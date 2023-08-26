package com.nullbird.hfs.utils.config;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public interface RuleMatcher {
  /**
   *
   * @param request The {@link HttpRequest} that should be tried for matching.
   * @return true if this matcher actually matches the {@see HttpRequest} provided.
   */
  boolean match(HttpRequest request);

  /**
   * This method should throw a {@link com.nullbird.hfs.utils.errors.ConfigurationException} if the configuration is incorrect.
   * @param config The configuration, to be used for eventual default values.
   */
  void initialize(Config config) throws ConfigurationException;

  /**
   * The place for this Matcher to free any resource before being thrown away.
   */
  void stop();
}
