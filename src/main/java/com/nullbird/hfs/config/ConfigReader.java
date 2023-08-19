package com.nullbird.hfs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.config.rules.RuleAction;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import com.nullbird.hfs.utils.parsing.RuleActionDeserializer;
import com.nullbird.hfs.utils.parsing.RuleMatcherDeserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    for (Rule rule : config.getRules()) {
      rule.init(config);
    }

    LOGGER.info(StringUtils.formatNanos(System.nanoTime() - nanos) + " Reloaded config");
    return config;
  }

  private static Gson getGson() {
    var builder = new GsonBuilder();
    builder.registerTypeAdapter(RuleAction.class, new RuleActionDeserializer());
    builder.registerTypeAdapter(RuleMatcher.class, new RuleMatcherDeserializer());
    return builder.create();
  }
}
