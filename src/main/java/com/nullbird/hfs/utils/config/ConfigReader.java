package com.nullbird.hfs.utils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.PathParametersHandling;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.config.rules.actions.BasicAuthenticate;
import com.nullbird.hfs.utils.HashTools;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import com.nullbird.hfs.utils.parsing.RuleActionDeserializer;
import com.nullbird.hfs.utils.parsing.RuleMatcherDeserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class ConfigReader {
  private final static Logger LOGGER = Logger.getLogger(ConfigReader.class.getName());

  private static File configFile;
  private static Config runningConfig;

  public static void init(String configFile) throws ConfigurationException {
    ConfigReader.configFile = new File(configFile);
    preloadConfig();
  }

  public static Config getConfig() {
    return runningConfig;
  }

  public static void preloadConfig() throws ConfigurationException {
    runningConfig = loadConfig();
  }

  public synchronized static Config loadConfig() throws ConfigurationException {
    long nanos = System.nanoTime();
    Config config = null;
    if (!configFile.exists()) {
      throw new ConfigurationException("Config file not found: " + configFile.getAbsolutePath());
    }
    try (InputStream is = new FileInputStream(configFile)) {
      config = getGson().fromJson(new InputStreamReader(is), Config.class);
    } catch (Exception e) {
      throw new ConfigurationException("Unable to parse config file " + configFile.getAbsolutePath() + ": " + e.getMessage(), e);
    }

    if (config.getRules()==null || config.getRules().size()==0) throw new ConfigurationException("Config file must have at least one rule");
    // Gson sets unknown enum values to null
    if (config.getPathParameters()==null) throw new ConfigurationException("pathParameters must be one of: reject, strip, keep");
    if (config.getPathParameters()==PathParametersHandling.KEEP) {
      LOGGER.warning("pathParameters is 'keep': rules on URLs can be bypassed with path parameters (/admin;x) " +
          "for backends that ignore them.");
    }
    ensureAuthSecret(config);
    for (Rule rule : config.getRules()) {
      rule.init(config);
    }

    LOGGER.info(StringUtils.formatNanos(System.nanoTime() - nanos) + " Reloaded config");
    return config;
  }

  /**
   * If a {@link BasicAuthenticate} action is configured without any <code>hfsAuthSecret</code>, generates one
   * and writes it back into the configuration file.
   */
  private static void ensureAuthSecret(Config config) throws ConfigurationException {
    if (StringUtils.containsNonWhitespace(config.getHfsAuthSecret())) return;
    boolean needed = config.getRules().stream().anyMatch(rule -> rule.getAction() instanceof BasicAuthenticate);
    if (!needed) return;

    String secret = HashTools.getRandomSequence(32);
    LOGGER.warning("No hfsAuthSecret found in the configuration. Generating one and rewriting " + configFile.getAbsolutePath());
    try {
      JsonObject root;
      try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
        root = JsonParser.parseReader(reader).getAsJsonObject();
      }
      root.addProperty("hfsAuthSecret", secret);
      String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);

      Path target = configFile.getAbsoluteFile().toPath();
      Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
      try {
        Files.writeString(temp, json + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (Exception e) {
      throw new ConfigurationException("Unable to write the generated hfsAuthSecret into " + configFile.getAbsolutePath() + ": " + e.getMessage(), e);
    }
    config.setHfsAuthSecret(secret);
  }

  private static Gson getGson() {
    var builder = new GsonBuilder();
    builder.registerTypeAdapter(RuleAction.class, new RuleActionDeserializer());
    builder.registerTypeAdapter(RuleMatcher.class, new RuleMatcherDeserializer());
    return builder.create();
  }
}
