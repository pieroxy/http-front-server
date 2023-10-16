package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import utils.MatcherTestCase;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class HostIsInTest extends MatcherTestCase {
  @Test
  public void testSimple() {
    HostIsIn hi = new HostIsIn();
    hi.setHost(Set.of("abc.com", "def.com"));
    fromUrlHelper(hi, "https://abc.com/toto", true);
    fromUrlHelper(hi, "https://ABC.com/toto", true);
    fromUrlHelper(hi, "https://abc.com:443/toto", true);
    fromUrlHelper(hi, "https://def.com:443/toto", true);
    fromUrlHelper(hi, "https://ghi.com/toto", false);
  }

  @Test
  public void testNoExcludePort() {
    HostIsIn hi = new HostIsIn();
    hi.setHost(Set.of("abc.com"));
    hi.setExcludePort(false);
    fromUrlHelper(hi, "https://abc.com/toto", true);
    fromUrlHelper(hi, "https://ABC.com/toto", true);
    fromUrlHelper(hi, "https://abc.com:443/toto", false);
    fromUrlHelper(hi, "https://abcd.com/toto", false);
  }

  @Test
  public void testConfigurationMissingHost() {
    var matcher = new HostIs();
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
  }
}
