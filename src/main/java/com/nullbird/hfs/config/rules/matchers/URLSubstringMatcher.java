package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.SubstringRuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This matcher looks for a substring to be present in the URL provided.
 * <br>Note that everything this matcher does can be achieved with {@link URLRegexpMatcher}, but this one is
 * faster because it doesn't deal with regular expressions.
 * <br>The matching is case-sensitive.
 */
public class URLSubstringMatcher implements SubstringRuleMatcher {
  /**
   * The pattern to look for.
   * <br>The parameter is mandatory.
   */
  protected String pattern;
  /**
   * If <b>true</b>, the pattern must be at the beginning of the URL to match.
   * <br>Default value is <b>false</b>.
   */
  protected boolean startsWith;

  @Override
  public boolean match(HttpRequest request) {
    int pos = request.getUrl().indexOf(pattern);
    if (pos>-1) {
      if (startsWith) return pos==0;
      return true;
    }
    return false;
  }

  @Override
  public String getMatchReplacedBy(HttpRequest request, String replaceWith) {
    return request.getUrl().replace(pattern, replaceWith);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (pattern==null) throw new ConfigurationException("URLSubstringMatcher definition must include a non null 'pattern' attribute");

  }

  @Override
  public void stop() {

  }

  /**
   * See {@link #pattern}
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * See {@link #pattern}
   */
  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  /**
   * See {@link #startsWith}
   */
  public boolean isStartsWith() {
    return startsWith;
  }

  /**
   * See {@link #startsWith}
   */
  public void setStartsWith(boolean startsWith) {
    this.startsWith = startsWith;
  }
}
