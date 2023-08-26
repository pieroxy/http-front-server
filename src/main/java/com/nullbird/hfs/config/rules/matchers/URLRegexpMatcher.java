package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.utils.config.SubstringRuleMatcher;
import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This matcher will check http requests URLs with a regular expression.
 */
public class URLRegexpMatcher implements SubstringRuleMatcher {
  private transient Pattern pattern;
  /**
   * The regexp that will be matched to the requests URLs.
   */
  protected String regexp;


  @Override
  public boolean match(HttpRequest request) {
    return pattern.matcher(request.getUrl()).find();
  }

  @Override
  public String getMatchReplacedBy(HttpRequest request, String replaceWith) {
    Matcher match = pattern.matcher(request.getUrl());
    if (match.find()) {
      return match.replaceFirst(replaceWith);
    }
    throw new RuntimeException("Matcher did not match the request");
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (regexp==null) throw new ConfigurationException("URLRegexpMatcher definition must include a non null 'regexp' attribute");
    pattern = Pattern.compile(regexp);
  }

  @Override
  public void stop() {

  }


  /**
   * See {@link #regexp}
   */
  public String getRegexp() {
    return regexp;
  }

  /**
   * See {@link #regexp}
   */
  public void setRegexp(String regexp) {
    this.regexp = regexp;
  }

}
