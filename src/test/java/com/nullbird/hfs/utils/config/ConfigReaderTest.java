package com.nullbird.hfs.utils.config;

import com.google.gson.JsonParser;
import com.nullbird.hfs.config.PathParametersHandling;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import utils.BeforeAllTests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith({BeforeAllTests.class})
public class ConfigReaderTest {
  private static final String AUTH_RULE = """
      {"matcher":{"type":"All"},"action":{"type":"BasicAuthenticate","credentials":{"login":"password"}}}""";
  private static final String LITERAL_RULE = """
      {"matcher":{"type":"All"},"action":{"type":"RespondLiteral","contentType":"text/html","content":"<b>a=b</b>"}}""";

  private Path write(Path dir, String json) throws Exception {
    Path file = dir.resolve("config.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  public void secretIsGeneratedAndPersistedTest(@TempDir Path dir) throws Exception {
    Path file = write(dir, "{\"customField\":\"<kept=as-is>\",\"rules\":[" + AUTH_RULE + "," + LITERAL_RULE + "]}");
    ConfigReader.init(file.toString());
    String secret = ConfigReader.getConfig().getHfsAuthSecret();
    assertNotNull(secret);
    assertEquals(64, secret.length());

    String content = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(content.contains("\n  \"rules\""), "Config should be pretty printed");
    assertTrue(content.contains("\"<kept=as-is>\""), "No HTML escaping should happen");
    assertTrue(content.contains("\"<b>a=b</b>\""), "No HTML escaping should happen");
    var root = JsonParser.parseString(content).getAsJsonObject();
    assertEquals(secret, root.get("hfsAuthSecret").getAsString());
    assertEquals(2, root.getAsJsonArray("rules").size());

    // A second startup reuses the persisted secret and leaves the file alone
    ConfigReader.init(file.toString());
    assertEquals(secret, ConfigReader.getConfig().getHfsAuthSecret());
    assertEquals(content, Files.readString(file, StandardCharsets.UTF_8));
    try (var files = Files.list(dir)) {
      assertEquals(1, files.count(), "No temporary file should be left behind");
    }
  }

  @Test
  public void existingSecretIsKeptTest(@TempDir Path dir) throws Exception {
    String json = "{\"hfsAuthSecret\":\"mySecret\",\"rules\":[" + AUTH_RULE + "]}";
    Path file = write(dir, json);
    ConfigReader.init(file.toString());
    assertEquals("mySecret", ConfigReader.getConfig().getHfsAuthSecret());
    assertEquals(json, Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test
  public void pathParametersTest(@TempDir Path dir) throws Exception {
    ConfigReader.init(write(dir, "{\"rules\":[" + LITERAL_RULE + "]}").toString());
    assertEquals(PathParametersHandling.REJECT, ConfigReader.getConfig().getPathParameters(), "Default");
    ConfigReader.init(write(dir, "{\"pathParameters\":\"strip\",\"rules\":[" + LITERAL_RULE + "]}").toString());
    assertEquals(PathParametersHandling.STRIP, ConfigReader.getConfig().getPathParameters());
    ConfigReader.init(write(dir, "{\"pathParameters\":\"keep\",\"rules\":[" + LITERAL_RULE + "]}").toString());
    assertEquals(PathParametersHandling.KEEP, ConfigReader.getConfig().getPathParameters());
    assertThrows(ConfigurationException.class,
        () -> ConfigReader.init(write(dir, "{\"pathParameters\":\"Strip\",\"rules\":[" + LITERAL_RULE + "]}").toString()));
  }

  @Test
  public void trustedProxiesTest(@TempDir Path dir) throws Exception {
    ConfigReader.init(write(dir, "{\"trustedProxies\":[\"10.0.0.0/8\",\"::1\"],\"rules\":[" + LITERAL_RULE + "]}").toString());
    var config = ConfigReader.getConfig();
    assertTrue(config.isTrustedProxy("10.1.2.3"));
    assertTrue(config.isTrustedProxy("::1"));
    assertFalse(config.isTrustedProxy("127.0.0.1"));

    ConfigReader.init(write(dir, "{\"rules\":[" + LITERAL_RULE + "]}").toString());
    assertFalse(ConfigReader.getConfig().isTrustedProxy("127.0.0.1"), "No trusted proxy by default");

    assertThrows(ConfigurationException.class,
        () -> ConfigReader.init(write(dir, "{\"trustedProxies\":[\"my-lb.local\"],\"rules\":[" + LITERAL_RULE + "]}").toString()));
  }

  @Test
  public void noAuthNoRewriteTest(@TempDir Path dir) throws Exception {
    String json = "{\"rules\":[" + LITERAL_RULE + "]}";
    Path file = write(dir, json);
    ConfigReader.init(file.toString());
    assertNull(ConfigReader.getConfig().getHfsAuthSecret());
    assertEquals(json, Files.readString(file, StandardCharsets.UTF_8));
  }
}
