package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import utils.MatcherTestCase;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContainsCookieTest extends MatcherTestCase {
  @Test
  public void testSimple() throws ConfigurationException {
    var hi = new ContainsCookie();
    hi.setName("testCookieName");
    hi.setValue("testCookieValue");
    hi.initialize(null);
    fromCookieHelper(hi, "testCookieName", "testCookieValue", true);
    fromCookieHelper(hi, "testCookieName2", "testCookieValue", false);
    fromCookieHelper(hi, "testCookieName", "2testCookieValue2", true);
    fromCookieHelper(hi, "testCookieName", "test2CookieValue", false);
    fromCookieHelper(hi, null, null, false);
  }

  @Test
  public void testRexp() throws ConfigurationException {
    var hi = new ContainsCookie();
    hi.setName("testCookieName");
    hi.setValue(".*foo.bar$");
    hi.initialize(null);
    fromCookieHelper(hi, "testCookieName", "testCookieValue", false);
    fromCookieHelper(hi, "testCookieName", "dddddfoo/bar ", false);
    fromCookieHelper(hi, "testCookieName", "dddddfoo/bar", true);
    fromCookieHelper(hi, "testCookieName", "foo/bar", true);
  }

  @Test
  public void testConfigurationMissing() throws ConfigurationException {
    var matcher = new ContainsCookie();
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
    matcher.setValue("");
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
    matcher.setValue(null);
    matcher.setName("");
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
    matcher.setValue("");
    matcher.initialize(null);
  }

}
