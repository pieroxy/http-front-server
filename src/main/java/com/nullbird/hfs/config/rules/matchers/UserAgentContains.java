package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This matcher will check if the <code>User-Agent</code> http header contains a specific string.
 */
public class UserAgentContains implements RuleMatcher {
  /**
   * The String this matcher is supposed to look for in the <code>User-Agent</code> http header of all requests.
   */
  protected String substring;

  @Override
  public boolean match(HttpRequest request) {
    String ua = request.getHeader("User-Agent");
    if (ua == null) return false;
    return ua.contains(substring);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (substring==null) throw new ConfigurationException("UserAgentContains matcher definition must include a non null 'substring' attribute");
  }

  @Override
  public void stop() {

  }
}
