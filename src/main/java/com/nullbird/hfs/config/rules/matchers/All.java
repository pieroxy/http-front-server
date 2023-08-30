package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This matcher will match all requests.
 */
public class All implements RuleMatcher {

  /**
   * @param request Not used, may be null.
   * @return <b>true</b>
   */
  @Override
  public boolean match(HttpRequest request) {
    return true;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
  }

  @Override
  public void stop() {
  }
}
