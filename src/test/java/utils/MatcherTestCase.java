package utils;

import com.nullbird.hfs.utils.config.RuleMatcher;
import utils.matchers.DumbOddMatcher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatcherTestCase {
  protected void fromUrlHelper(RuleMatcher matcher, String url, boolean shouldMatch) {
    assertEquals(shouldMatch, matcher.match(TestRequest.fromUrl(url)), "For url " + url);
  }

  protected void fromCookieHelper(RuleMatcher matcher, String cookieName, String cookieValue, boolean shouldMatch) {
    var req = TestRequest.fromUrl("https://toto.com");
    if (cookieName == null) {
      req.setCookieValues(null);
    } else {
      req.getCookieValues().put(cookieName, cookieValue);
    }
    assertEquals(shouldMatch, matcher.match(req), "For cookie " + cookieName + ":" + cookieValue);
  }

  protected void withDumbOddMatcher(RuleMatcher matcher, int value, boolean shouldMatch) {
    var req = TestRequest.fromUrl("https://toto.com");
    req.getHeaders().put(DumbOddMatcher.MATCHER_VALUE, List.of(String.valueOf(value)));
    assertEquals(shouldMatch, matcher.match(req), "For value " + value);
  }

  /* This method exists so that JUnit is happy */
  public void test() {}
}
