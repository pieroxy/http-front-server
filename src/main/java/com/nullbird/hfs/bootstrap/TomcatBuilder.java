package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.StringUtils;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatBuilder {
  private final static Logger LOGGER = Logger.getLogger(TomcatBuilder.class.getName());

  Tomcat buildTomcat(Config config) {
    Tomcat tomcat = new Tomcat();

    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir!=null) {
      tomcat.setBaseDir(tempDir + File.separator + "nullbirdLbTomcat");
    }

    Connector ctr = new Connector();
    ctr.setPort(config.getTomcatConfig().getHttpPort());
    if (config.getTomcatConfig().getMaxThreads()>0) {
      LOGGER.info("Setting threads limit to " + config.getTomcatConfig().getMaxThreads());
      ctr.setProperty("maxThreads", String.valueOf(config.getTomcatConfig().getMaxThreads()));
    }
    tomcat.setConnector(ctr);
    ctr.addLifecycleListener(lifecycleEvent -> {
      if (lifecycleEvent.getType().equals(Lifecycle.AFTER_START_EVENT)) {
        LOGGER.log(Level.INFO,  StringUtils.formatNanos(System.nanoTime() - Runner.birth) + " nullbird-hfs started");
      }
    });
    if (StringUtils.containsNonWhitespace(config.getTomcatConfig().getAddress())) {
      ctr.setProperty("address", config.getTomcatConfig().getAddress());
    }
    ctr.setProperty("compression", "on");
    ctr.setProperty("compressionMinSize", "512");
    ctr.setProperty("compressibleMimeType", "text/html, text/css, application/javascript, image/svg+xml, application/json");

    addMainContext(tomcat);
    return tomcat;
  }

  private static void addMainContext(Tomcat tomcat) {
    String contextPath = "";
    StandardContext ctx = (StandardContext) tomcat.addContext(contextPath, null);
    ctx.setClearReferencesRmiTargets(false);
    ctx.setClearReferencesObjectStreamClassCaches(false);
    ctx.setClearReferencesThreadLocals(false);

    tomcat.addServlet(contextPath, "main", new MainServlet());
    ctx.addServletMappingDecoded("/*", "main");
    ctx.addLifecycleListener(new MainLifecycleListener());
  }
}
