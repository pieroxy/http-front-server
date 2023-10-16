package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This action is used to override the host http header in incoming requests.
 */
public class OverrideHost implements RuleAction {
  /**
   * The host that should be used.
   * <br>This attribute is mandatory.
   */
  protected String host;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    request.setHost(host);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (host==null) throw new ConfigurationException("RespondLiteral action definition must include a non null 'host' attribute");
  }

  @Override
  public void stop() {
  }

  public void setHost(String host) {
    this.host = host;
  }
}
