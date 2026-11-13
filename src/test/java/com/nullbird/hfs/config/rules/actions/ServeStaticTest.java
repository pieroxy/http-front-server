package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ServeStaticTest {
  private Path root;
  private ServeStatic action;

  @BeforeEach
  void createRoot() throws Exception {
    root = Files.createTempDirectory("hfs-static-");
    Files.writeString(root.resolve("index.html"), "index");
    Files.createDirectory(root.resolve("dir"));
    action = new ServeStatic();
    action.setRoot(root.toString());
  }

  @AfterEach
  void deleteRoot() throws Exception {
    action.stop();
    try (Stream<Path> files = Files.walk(root)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  public void invalidConfigurationTest() throws Exception {
    assertThrows(ConfigurationException.class, () -> new ServeStatic().initialize(new Config()), "No root");

    var missing = new ServeStatic();
    missing.setRoot(root.resolve("missing").toString());
    assertThrows(ConfigurationException.class, () -> missing.initialize(new Config()), "Missing root");

    var file = new ServeStatic();
    file.setRoot(root.resolve("index.html").toString());
    assertThrows(ConfigurationException.class, () -> file.initialize(new Config()), "Root is a file");

    action.setSpaFallback("index.html");
    assertThrows(ConfigurationException.class, () -> action.initialize(new Config()), "Relative fallback");
  }

  @Test
  public void resolveTest() throws Exception {
    action.initialize(new Config());
    assertEquals("/index.html", action.resolve("/"));
    assertEquals("/index.html", action.resolve("/?a=b"));
    assertEquals("/dir/", action.resolve("/dir/"), "No index in the directory");
    assertEquals("/dir", action.resolve("/dir"));
    assertEquals("/a b", action.resolve("/a%20b"));
    assertEquals("/a+b", action.resolve("/a+b"));
    assertEquals("/page", action.resolve("/page"), "No fallback");
    assertNull(action.resolve("/a%2Fb"));
    assertNull(action.resolve("/a%5Cb"));
  }

  @Test
  public void spaFallbackTest() throws Exception {
    action.setSpaFallback("/index.html");
    action.initialize(new Config());
    assertEquals("/index.html", action.resolve("/page/login?next=/"));
    assertEquals("/index.html", action.resolve("/page/"));
    assertEquals("/dir", action.resolve("/dir"), "Existing directory");
    assertEquals("/app.js", action.resolve("/app.js"));
    assertEquals("/index.html", action.resolve("/v1.2/app"), "Only the last segment matters");
  }
}
