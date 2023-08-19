package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import utils.MatcherTestCase;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class URLRegexpMatcherTest extends MatcherTestCase {
  @Test
  public void testSimple() throws ConfigurationException {
    var matcher = new URLRegexpMatcher();
    matcher.setRegexp("a.b");
    matcher.initialize(null);
    fromUrlHelper(matcher, "https://axbc.com/toto", true);
    fromUrlHelper(matcher, "https://abc.com/toto", false);
    fromUrlHelper(matcher, "https://abc.com:443/totoa2b", true);
    fromUrlHelper(matcher, "https://abcd.com/toto", false);
  }
  @Test
  public void testBegin() throws ConfigurationException {
    var matcher = new URLRegexpMatcher();
    matcher.setRegexp("http:");
    matcher.initialize(null);
    fromUrlHelper(matcher, "http://abc.com/toto", true);
    fromUrlHelper(matcher, "https://abc.com/toto", false);
    fromUrlHelper(matcher, "https://abc.com.http:8080/toto", true);
    matcher = new URLRegexpMatcher();
    matcher.setRegexp("^http:");
    matcher.initialize(null);
    fromUrlHelper(matcher, "http://abc.com/toto", true);
    fromUrlHelper(matcher, "https://abc.com/toto", false);
    fromUrlHelper(matcher, "https://abc.com.http:8080/toto", false);
  }

  @Test
  public void testConfigurationMissingRegexp() {
    var matcher = new URLRegexpMatcher();
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
  }
}
