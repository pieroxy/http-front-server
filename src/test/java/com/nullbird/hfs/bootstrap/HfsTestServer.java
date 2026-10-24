package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.utils.config.ConfigReader;
import org.apache.catalina.startup.Tomcat;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts a real HFS (Tomcat + MainServlet) from a JSON configuration, on an ephemeral port.
 * <br>The configuration is global to the JVM ({@link ConfigReader}), so only one instance may run at a time.
 */
public class HfsTestServer implements Closeable {
  private final Path configFile;
  private final Config config;
  private final Tomcat tomcat;

  public HfsTestServer(String configJson) throws Exception {
    configFile = Files.createTempFile("hfs-e2e-", ".json");
    Files.writeString(configFile, configJson, StandardCharsets.UTF_8);
    ConfigReader.init(configFile.toString());
    config = ConfigReader.getConfig();
    config.getTomcatConfig().setHttpPort(0);
    tomcat = new TomcatBuilder().buildTomcat(config);
    tomcat.start();
  }

  public int getPort() {
    return tomcat.getConnector().getLocalPort();
  }

  @Override
  public void close() {
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (Exception e) {
      e.printStackTrace();
    }
    config.getRules().forEach(Rule::close);
    try {
      Files.deleteIfExists(configFile);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
