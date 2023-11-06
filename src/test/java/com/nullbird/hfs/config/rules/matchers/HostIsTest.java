package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import utils.MatcherTestCase;
import utils.TestRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HostIsTest extends MatcherTestCase {
  @Test
  public void testSimple() {
    HostIs hi = new HostIs();
    hi.setHost("abc.com");
    fromUrlHelper(hi, "https://abc.com/toto", true);
    fromUrlHelper(hi, "https://ABC.com/toto", true);
    fromUrlHelper(hi, "https://abc.com:443/toto", true);
    fromUrlHelper(hi, "https://abcd.com/toto", false);
  }

  @Test
  public void testNoExcludePort() {
    HostIs hi = new HostIs();
    hi.setHost("abc.com");
    hi.setExcludePort(false);
    fromUrlHelper(hi, "https://abc.com/toto", true);
    fromUrlHelper(hi, "https://ABC.com/toto", true);
    fromUrlHelper(hi, "https://abc.com:443/toto", false);
    fromUrlHelper(hi, "https://abcd.com/toto", false);
  }

  @Test
  public void testNoHostInRequest() {
    HostIs hi = new HostIs();
    hi.setHost("abc.com");
    hi.setExcludePort(false);
    TestRequest req = TestRequest.fromUrl("https://abc.com/toto");
    req.setHost(null);
    assertEquals(false, hi.match(req));
  }

  @Test
  public void testConfigurationMissingHost() {
    var matcher = new HostIs();
    assertThrows(ConfigurationException.class, () -> matcher.initialize(null));
  }

}
