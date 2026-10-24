package com.nullbird.hfs.utils.config;

import com.google.gson.JsonParser;
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
  public void noAuthNoRewriteTest(@TempDir Path dir) throws Exception {
    String json = "{\"rules\":[" + LITERAL_RULE + "]}";
    Path file = write(dir, json);
    ConfigReader.init(file.toString());
    assertNull(ConfigReader.getConfig().getHfsAuthSecret());
    assertEquals(json, Files.readString(file, StandardCharsets.UTF_8));
  }
}
