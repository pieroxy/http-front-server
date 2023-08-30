package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This matcher encapsulates one matcher and will match if the matcher doesn't match the request.
 * For example, if it encapsulates the {@link ContainsCookie} matcher, it will match if the request
 * doesn't contain the cookie.
 */

public class Not implements RuleMatcher {
  /**
   * The instance of {@link RuleMatcher} this matcher will negate the output of.
   * <br>This parameter is mandatory.
   */
  protected RuleMatcher matcher;

  public Not() {
  }

  public Not(RuleMatcher matcher) {
    this.matcher = matcher;
  }

  /**
   * @param request The {@link HttpRequest} that should be tried for matching.
   * @return true if and only if the {@link #matcher} returns <b>false</b> to the same request.
   */
  @Override
  public boolean match(HttpRequest request) {
    return ! matcher.match(request);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (matcher==null) throw new ConfigurationException("Not matcher definition must include a non null 'matcher' attribute");
  }

  @Override
  public void stop() {
    matcher.stop();
  }

  public RuleMatcher getMatcher() {
    return matcher;
  }

  public void setMatcher(RuleMatcher matcher) {
    this.matcher = matcher;
  }
}
