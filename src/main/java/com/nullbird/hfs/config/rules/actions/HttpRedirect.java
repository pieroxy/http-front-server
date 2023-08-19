package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleAction;
import com.nullbird.hfs.config.rules.SubstringRuleMatcher;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class HttpRedirect implements RuleAction {
  private String target;
  private int status;
  private boolean substring;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    if (substring) {
      if (!(matcher instanceof SubstringRuleMatcher)) {
        throw new RuntimeException("HttpRedirect with 'substring' attribute set must be used with a SubstringRuleMatcher");
      }
      response.sendRedirect(status, ((SubstringRuleMatcher)matcher).getMatchReplacedBy(request, target));
    } else {
      response.sendRedirect(status, target);
    }
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (status<1) status = config.getDefaultRedirectStatus();
    if (target==null) throw new ConfigurationException("HttpRediect action definition must include a non null 'target' attribute");
  }

  @Override
  public void stop() {
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public boolean isSubstring() {
    return substring;
  }

  public void setSubstring(boolean substring) {
    this.substring = substring;
  }
}
