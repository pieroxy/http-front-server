package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches a cookie in the HTTP request. The value of the cookie must match a regexp.
 */
public class ContainsCookie  implements RuleMatcher {
  /**
   * The name of the cookie this matcher is supposed to check.
   * <br>This parameter is mandatory.
   */
  protected String name;

  /**
   * The regexp matching the cookie value, if it exists.
   * <br>This parameter is mandatory.
   */
  protected String value;

  private transient Pattern pattern;

  /**
   * @param request The {@link HttpRequest} that should be tried for matching.
   * @return true if the cookie is found <b>and</b> if the value of the cookie matches the {@link #value} regular expression.
   */
  @Override
  public boolean match(HttpRequest request) {
    List<String> cookieValues = request.getCookieValues(this.name);
    for (var cookieValue : cookieValues) {
      if (pattern.matcher(cookieValue).find()) return true;
    }
    return false;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (name==null) throw new ConfigurationException("ContainsCookie matcher definition must include a non null 'name' attribute");
    if (value==null) throw new ConfigurationException("ContainsCookie matcher definition must include a non null 'value' attribute");
    pattern = Pattern.compile(value);

  }

  @Override
  public void stop() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
