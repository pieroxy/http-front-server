package com.nullbird.hfs.config.rules;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * A rule contains a {@link #matcher} and an {@link #action}. When executed, the engine will check if the rule matches
 * the current request being processed, by calling the matcher. If so, the action will be run. If not, the following
 * rule will then be checked.
 */
public class Rule {
  /**
   * The {@link RuleMatcher} that will decide if the {@link #action} should be run.
   */
  protected RuleMatcher matcher;
  /**
   * The {@link RuleAction} that will be run if the  {@link #matcher} matches the request.
   */
  protected RuleAction action;

  /**
   * Initializes the {@link #matcher} and the {@link #action} configured.
   * @param config The global configuration object
   * @throws ConfigurationException if some parameters are missing, defined to incorrect values, incompatibles, etc.
   */

  public void init(Config config) throws ConfigurationException {
    matcher.initialize(config);
    action.initialize(config);
  }

  /**
   * Closes the {@link #matcher} and the {@link #action} configured.
   */
  public void close() {
    try {
      matcher.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      action.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** */
  public RuleMatcher getMatcher() {
    return matcher;
  }

  /** */
  public void setMatcher(RuleMatcher matcher) {
    this.matcher = matcher;
  }

  /** */
  public RuleAction getAction() {
    return action;
  }

  /** */
  public void setAction(RuleAction action) {
    this.action = action;
  }
}
