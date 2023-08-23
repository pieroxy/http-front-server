package com.nullbird.hfs.config.rules.matchers;

import org.junit.jupiter.api.Test;
import utils.TestRequest;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AllTest {
  @Test
  public void testAll() {
    assertTrue(new All().match(null));
    assertTrue(new All().match(new TestRequest()));
  }
}
