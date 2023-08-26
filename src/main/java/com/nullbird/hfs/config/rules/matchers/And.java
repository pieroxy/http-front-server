package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.List;

public class And implements RuleMatcher {
  /**
   * The instances of {@link RuleMatcher}s this matcher will AND the output of.
   * <br>This parameter is mandatory.
   */
  protected List<RuleMatcher> matchers;

  public And() {
  }

  public And(List<RuleMatcher> matchers) {
    this.matchers = matchers;
  }

  /**
   * @param request The {@link HttpRequest} that should be tried for matching.
   * @return true if and only if all of the {@link #matchers} returns <b>true</b> for the provided request.
   * Processing stops at the first matcher that returns <b>false</b>.
   */
  @Override
  public boolean match(HttpRequest request) {
    for (var matcher : matchers) {
      if (!matcher.match(request)) return false;
    }
    return true;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (matchers==null) throw new ConfigurationException("And matcher definition must include a non null 'matchers' attribute");
  }

  @Override
  public void stop() {
    for (var matcher : matchers) {
      matcher.stop();
    }
  }

  public List<RuleMatcher> getMatcher() {
    return matchers;
  }

  public void setMatcher(List<RuleMatcher> matchers) {
    this.matchers = matchers;
  }
}
