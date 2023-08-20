package com.nullbird.hfs;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.ConfigReader;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.logging.GcLogging;
import com.nullbird.hfs.utils.logging.SingleLineFormatter;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Runner {
  private final static Logger LOGGER = Logger.getLogger(Runner.class.getName());
  private final static String GIT_REV;
  private final static String MVN_VER;
  private final static String GIT_DEPTH;
  public static final long birth;
  private static final Level DEFAULT_LEVEL = Level.INFO;

  static {
    birth = System.nanoTime();
    GIT_REV = readResourceFileAsString("GIT_REV");
    MVN_VER = readResourceFileAsString("MVN_VER");
    GIT_DEPTH = readResourceFileAsString("GIT_DEPTH");
  }

  private static String readResourceFileAsString(String filename) {
    try {
      InputStream is = Runner.class.getClassLoader().getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is)).lines().findFirst().get();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Could not read " + filename, e);
      return filename + "_" + Math.random();
    }
  }

  private static Tomcat tomcat;

  public static void main(String[] args) {
    initLogging(null);
    LOGGER.info("Starting.");
    try {
      String dataFolder = args[0];
      ConfigReader.init(dataFolder);
      run();
    } catch (Throwable e) {
      LOGGER.log(Level.SEVERE, "Something went very wrong", e);
      System.exit(1);
    }
    LOGGER.info("Done.");
  }

  private static boolean portAlreadyTaken(int port) throws IOException {
    URL u = new URL("http://localhost:" + port + "/");
    HttpURLConnection c = (HttpURLConnection)u.openConnection();
    try {
      c.connect();
    } catch (ConnectException e) {
      return false;
    } catch (Exception e) {
    }
    return true;
  }

  private static void run() throws Exception {
    Config config = ConfigReader.getConfig();
    initLogging(config);
    LOGGER.log(Level.INFO, String.format("Starting version %s (build %s, #commit %s)", MVN_VER, GIT_REV, GIT_DEPTH));
    if (portAlreadyTaken(config.getTomcatConfig().getHttpPort())) {
      LOGGER.severe("The port " + config.getTomcatConfig().getHttpPort() + " is already taken.");
      return;
    }

    if (config.getTomcatConfig().getHttpPort() <= 0)
      throw new Exception("httpPort configured to an invalid value of " + config.getTomcatConfig().getHttpPort());

    LOGGER.info("Starting Tomcat");

    tomcat = buildTomcat(config);
    tomcat.start();
    tomcat.getServer().await();
  }

  static void debugLoggerFormatters() {
    Collections.list(LogManager.getLogManager().getLoggerNames()).forEach(
            loggerName -> {
              var logger = Logger.getLogger(loggerName);
              List<Handler> handlers = Arrays.asList(logger.getHandlers());
              handlers.forEach(handler -> {
                LOGGER.info(loggerName + " -- " + handler.getFormatter().getClass().getName());
              });
            }
    );
  }

  public static Level getDefaultLevel(Config config) {
    String defaultLevelName = config == null ? null : config.getDefaultLoggingLevel();
    Level defaultLevel = null;
    try {
      if (defaultLevelName!=null) defaultLevel = Level.parse(defaultLevelName);
    } catch (IllegalArgumentException e) {
      LOGGER.warning(String.format("The default level specified (%s) is not valid. Using %s instead.", defaultLevelName, DEFAULT_LEVEL.getName()));
    }
    if (defaultLevel == null) defaultLevel = DEFAULT_LEVEL;

    return defaultLevel;
  }

  public static void initLogging(Config config) {
    // This is for Tomcat to not override the default Formatter
    System.setProperty("java.util.logging.config.file", "none");

    var defaultLevel = getDefaultLevel(config);
    GcLogging.installGCMonitoring();
    Properties p = new Properties();
    p.put("java.util.logging.ConsoleHandler.formatter", SingleLineFormatter.class.getName());
    p.put("handlers", "java.util.logging.ConsoleHandler");
    p.put(".level", defaultLevel.getName());
    p.put("java.util.logging.ConsoleHandler.level", "ALL");
    Map<String, String> levels = new HashMap<>();
    if (config!=null && config.getLoggersLevel()!=null) {
      levels.putAll(config.getLoggersLevel());
    }
    levels.entrySet().forEach(e -> {
      p.put(e.getKey()+".level", e.getValue());
    });

    var formatter = new SingleLineFormatter();

    try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      p.store(out, "");
      LogManager.getLogManager().updateConfiguration(
              new ByteArrayInputStream(out.toByteArray()),
              (key)->(oldValue,newValue)-> newValue);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }

    Collections.list(LogManager.getLogManager().getLoggerNames()).forEach(
      loggerName -> {
        var logger = Logger.getLogger(loggerName);
        if (logger.getUseParentHandlers()) logger = logger.getParent();
        if (logger == null) return;
        List<Handler> handlers = Arrays.asList(logger.getHandlers());
        handlers.forEach(handler -> handler.setFormatter(formatter));
      }
    );
  }

  private static void shutdown() {
    if (tomcat == null) return;
    LOGGER.log(Level.INFO, "Shutting Down.");
    try {
      tomcat.stop();
    } catch (LifecycleException e) {
      LOGGER.log(Level.SEVERE, "Stopping Tomcat " + e);
    }
    try {
      tomcat.destroy();
    } catch (LifecycleException e) {
      LOGGER.log(Level.SEVERE, "Destroying Tomcat" + e);
    }
    LOGGER.log(Level.INFO, "Shutdown complete.");
  }

  private static Tomcat buildTomcat(Config config) {
    Tomcat tomcat = new Tomcat();

    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir!=null) {
      tomcat.setBaseDir(tempDir + File.separator + "nullbirdLbTomcat");
    }

    Connector ctr = new Connector();
    ctr.setPort(config.getTomcatConfig().getHttpPort());
    tomcat.setConnector(ctr);
    ctr.addLifecycleListener(lifecycleEvent -> {
      if (lifecycleEvent.getType().equals(Lifecycle.AFTER_START_EVENT)) {
        LOGGER.log(Level.INFO,  StringUtils.formatNanos(System.nanoTime() - Runner.birth) + " LB started");
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

    tomcat.addServlet(contextPath, "main", new MainServlet());
    ctx.addServletMappingDecoded("/*", "main");
  }
}
