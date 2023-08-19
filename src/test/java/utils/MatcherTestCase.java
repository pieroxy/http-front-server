package utils;

import com.nullbird.hfs.config.rules.RuleMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatcherTestCase {
  protected void fromUrlHelper(RuleMatcher matcher, String url, boolean shouldMatch) {
    assertEquals(shouldMatch, matcher.match(TestRequest.fromUrl(url)), "For url " + url);
  }

  /* This method exists so that JUnit is happy */
  public void test() {}
}
