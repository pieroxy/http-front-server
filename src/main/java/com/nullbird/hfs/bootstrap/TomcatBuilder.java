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
    ctr.setProperty("compressionMinSize", "512");
    ctr.setProperty("compressibleMimeType", "text/html, text/css, application/javascript, image/svg+xml, application/json");

  }

  void addHttpsConnector(Config config) {
    TomcatSslConfig hfsSslConf = config.getTomcatConfig().getSslConfig();
    if (hfsSslConf!=null && hfsSslConf.getPort()>0) {
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
      tomcat.setConnector(ctr);
    }
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
