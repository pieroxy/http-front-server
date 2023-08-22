package com.nullbird.hfs.config.rules.matchers;

import org.junit.jupiter.api.Test;
import utils.MatcherTestCase;
import utils.matchers.DumbOddMatcher;
import utils.matchers.FalseMatcher;
import utils.matchers.TrueMatcher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorsTest extends MatcherTestCase {
  @Test
  void notTest() {
    withDumbOddMatcher(new DumbOddMatcher(), 1, true);
    withDumbOddMatcher(new Not(new DumbOddMatcher()), 1, false);

    withDumbOddMatcher(new DumbOddMatcher(), 2, false);
    withDumbOddMatcher(new Not(new DumbOddMatcher()), 2, true);

    fromUrlHelper(new Not(new TrueMatcher()), "http://foo.com", false);
    fromUrlHelper(new TrueMatcher(), "http://foo.com", true);

    fromUrlHelper(new Not(new FalseMatcher()), "http://foo.com", true);
    fromUrlHelper(new FalseMatcher(), "http://foo.com", false);
  }

  @Test
  void andTest() {
    assertTrue(new And(List.of(new TrueMatcher())).match(null));
    assertTrue(new And(List.of(new TrueMatcher(),new TrueMatcher())).match(null));

    assertFalse(new And(List.of(new TrueMatcher(),new FalseMatcher())).match(null));
    assertFalse(new And(List.of(new FalseMatcher(),new TrueMatcher())).match(null));
    assertFalse(new And(List.of(new FalseMatcher(),new FalseMatcher())).match(null));
  }

  @Test
  void orTest() {
    assertTrue(new Or(List.of(new TrueMatcher())).match(null));
    assertTrue(new Or(List.of(new TrueMatcher(),new TrueMatcher())).match(null));

    assertTrue(new Or(List.of(new TrueMatcher(),new FalseMatcher())).match(null));
    assertTrue(new Or(List.of(new FalseMatcher(),new TrueMatcher())).match(null));
    assertFalse(new Or(List.of(new FalseMatcher(),new FalseMatcher())).match(null));
  }
}
