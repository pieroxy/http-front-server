package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.SubstringRuleMatcher;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This action will respond with an http redirection.
 * This action does consume the response and interrupt the flow of rules processing.
 */
public class HttpRedirect implements RuleAction {
  /**
   * The URL to which user agents will be redirected to.
   * <br>This attribute is mandatory.
   */

  protected String target;
  /**
   * The http response code used for the redirection. This typically is 301 or 302. Be aware that browsers tend to
   * cache 301 responses, so if you want to change it at some later time, all user agents will not be affected by the
   * change.
   * <br>This attribute is optional. If not provided, the value of {@link Config#defaultRedirectStatus} will be used
   */
  protected int status;

  /**
   * If set to <b>true</b>, means the {@link #target} value will be used to replace the matching pattern of the matcher
   * in the incoming URL. The {@link RuleMatcher} used then needs to implement {@link SubstringRuleMatcher}, such as
   * {@link com.nullbird.hfs.config.rules.matchers.URLRegexpMatcher} or
   * {@link com.nullbird.hfs.config.rules.matchers.URLSubstringMatcher}
   * <br>If set to <b>false</b>, the {@link #target} attribute will be used as-is for the redirection.
   * <br>This attribute is optional. The default value is <b>false</b>
   */
  protected boolean substring;

  /**
   * If set to <b>true</b>, means the query string of the incoming request will be transferred to the redirection.
   * <br>This attribute is optional. The default value is <b>false</b>
   */
  protected boolean includeQueryString;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    String toAdd = "";
    if (includeQueryString) {
      toAdd = request.getQueryString();
      if (StringUtils.containsNonWhitespace(toAdd)) toAdd = "?" + toAdd;
    }
    if (substring) {
      if (!(matcher instanceof SubstringRuleMatcher)) {
        throw new RuntimeException("HttpRedirect with 'substring' attribute set must be used with a SubstringRuleMatcher");
      }
      response.sendRedirect(status, ((SubstringRuleMatcher)matcher).getMatchReplacedBy(request, target) + toAdd);
    } else {
      response.sendRedirect(status, target + toAdd);
    }
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (status<1) status = config.getDefaultRedirectStatus();
    if (target==null) throw new ConfigurationException("HttpRedirect action definition must include a non null 'target' attribute");
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

  public boolean isIncludeQueryString() {
    return includeQueryString;
  }

  public void setIncludeQueryString(boolean includeQueryString) {
    this.includeQueryString = includeQueryString;
  }
}
