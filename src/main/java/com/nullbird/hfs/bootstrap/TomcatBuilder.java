package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.TomcatConfig;
import com.nullbird.hfs.config.TomcatSslConfig;
import com.nullbird.hfs.utils.StringUtils;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatBuilder {
  private final static Logger LOGGER = Logger.getLogger(TomcatBuilder.class.getName());

  private final Tomcat tomcat = new Tomcat();

  Tomcat buildTomcat(Config config) {
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir!=null) {
      tomcat.setBaseDir(tempDir + File.separator + "nullbirdLbTomcat");
    }
    tomcat.getServer().addLifecycleListener(lifecycleEvent -> {
      if (lifecycleEvent.getType().equals(Lifecycle.AFTER_START_EVENT)) {
        LOGGER.log(Level.INFO,  StringUtils.formatNanos(System.nanoTime() - Runner.birth) + " nullbird-hfs started");
      }
    });

    addHttpConnector(config);
    addHttpsConnector(config);
    addMainContext(config.getTomcatConfig());
    return tomcat;
  }

  void addHttpConnector(Config config) {
    Connector ctr = new Connector();
    ctr.setPort(config.getTomcatConfig().getHttpPort());
    setCommonConnectorAttributes(ctr, config);
    tomcat.setConnector(ctr);
  }

  private void setCommonConnectorAttributes(Connector ctr, Config config) {
    if (config.getTomcatConfig().getMaxThreads()>0) {
      LOGGER.warning("Setting threads limit to " + config.getTomcatConfig().getMaxThreads());
      ctr.setProperty("maxThreads", String.valueOf(config.getTomcatConfig().getMaxThreads()));
    }
    if (StringUtils.containsNonWhitespace(config.getTomcatConfig().getAddress())) {
      ctr.setProperty("address", config.getTomcatConfig().getAddress());
    }
    ctr.setProperty("compression", "on");
    ctr.setProperty("maxConnections", "1000");
    ctr.setProperty("compressionMinSize", "512");
    ctr.setProperty("compressibleMimeType", "text/html, text/css, application/javascript, image/svg+xml, application/json");
  }

  Connector sslConnector;
  void addHttpsConnector(Config config) {
    TomcatSslConfig hfsSslConf = config.getTomcatConfig().getSslConfig();
    if (hfsSslConf!=null && hfsSslConf.getPort()>0) {
      sslConnector = buildSslConnector(config);
      tomcat.setConnector(sslConnector);
      scheduleCertListener(tomcat, config);
    }
  }

  private Connector buildSslConnector(Config config) {
    TomcatSslConfig hfsSslConf = config.getTomcatConfig().getSslConfig();
    Connector ctr = new Connector();
    ctr.setPort(hfsSslConf.getPort());
    ctr.setSecure(true);
    ctr.setScheme("https");
    ctr.setProperty("SSLEnabled", "true");
    setCommonConnectorAttributes(ctr, config);

    SSLHostConfig sslConfig = new SSLHostConfig();
    sslConfig.setCiphers(hfsSslConf.getCiphers());
    sslConfig.setProtocols(hfsSslConf.getProtocols());

    SSLHostConfigCertificate certConfig = new SSLHostConfigCertificate(sslConfig, SSLHostConfigCertificate.Type.RSA);
    certConfig.setCertificateFile(hfsSslConf.getCertificateFile());
    certConfig.setCertificateKeyFile(hfsSslConf.getCertificateKeyFile());
    certConfig.setCertificateChainFile(hfsSslConf.getCertificateChainFile());
    sslConfig.addCertificate(certConfig);

    ctr.addSslHostConfig(sslConfig);
    return ctr;
  }

  private void scheduleCertListener(Tomcat tomcat, Config config) {
    TomcatSslConfig hfsSslConf = config.getTomcatConfig().getSslConfig();
    Timer timer = new Timer();
    File [] files = new File[] {
            new File(hfsSslConf.getCertificateFile()),
            new File(hfsSslConf.getCertificateKeyFile()),
            new File(hfsSslConf.getCertificateChainFile())
    };
    long []timestamps = new long[] {
            files[0].lastModified(),
            files[1].lastModified(),
            files[2].lastModified()
    };
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        try {
          if (files[0].lastModified() != timestamps[0] ||
                  files[1].lastModified() != timestamps[1] ||
                  files[2].lastModified() != timestamps[2]) {
            LOGGER.warning("Reloading SSL certificate");
            try {
              Thread.sleep(1000); // Feeble attempt to leave some time to whatever process to copy all the files.
              tomcat.getEngine().getService().removeConnector(sslConnector);
              Connector ctr = buildSslConnector(config);
              sslConnector.destroy();
              tomcat.setConnector(ctr);
              sslConnector = ctr;
              timestamps[0] = files[0].lastModified();
              timestamps[1] = files[1].lastModified();
              timestamps[2] = files[2].lastModified();
            } catch (Exception e) {
              tomcat.setConnector(sslConnector);
              LOGGER.log(Level.SEVERE, "Trying to apply the new certificates", e);
            }
          }
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, "Watching for new certs", e);
        }
      }
    }, 5000, 5000);
  }

  private void addMainContext(TomcatConfig tomcatConfig) {
    String contextPath = "";
    StandardContext ctx = (StandardContext) tomcat.addContext(contextPath, null);
    ctx.setClearReferencesRmiTargets(false);
    ctx.setClearReferencesObjectStreamClassCaches(false);
    ctx.setClearReferencesThreadLocals(false);

    tomcat.addServlet(contextPath, "main", new MainServlet());
    ctx.addServletMappingDecoded("/*", "main");
    ctx.addLifecycleListener(new MainLifecycleListener());
    if (tomcatConfig!=null && tomcatConfig.getHttpLogConfig()!=null) {
      var httpLog = tomcatConfig.getHttpLogConfig();
      AccessLogValve accessLogValve = new AccessLogValve();
      accessLogValve.setDirectory(httpLog.getDirectory());
      accessLogValve.setPattern(httpLog.getPattern());
      accessLogValve.setSuffix(httpLog.getSuffix());
      accessLogValve.setPrefix(httpLog.getPrefix());
      ctx.addValve(accessLogValve);
    }
  }
}
