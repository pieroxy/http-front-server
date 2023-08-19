package com.nullbird.hfs.config.rules;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class Rule {
  private RuleMatcher matcher;
  private RuleAction action;

  public void init(Config config) throws ConfigurationException {
    matcher.initialize(config);
    action.initialize(config);
  }

  public RuleMatcher getMatcher() {
    return matcher;
  }

  public void setMatcher(RuleMatcher matcher) {
    this.matcher = matcher;
  }

  public RuleAction getAction() {
    return action;
  }

  public void setAction(RuleAction action) {
    this.action = action;
  }
}
