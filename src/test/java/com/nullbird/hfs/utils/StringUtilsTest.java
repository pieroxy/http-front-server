package com.nullbird.hfs.utils;

import com.nullbird.hfs.config.rules.matchers.All;
import org.junit.jupiter.api.Test;
import utils.TestRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilsTest {
  @Test
  public void testEncodePartsBetweenSlashes() {
    assertEquals("toto", StringUtils.encodePartsBetweenSlashes("toto"));
    assertEquals("to/to", StringUtils.encodePartsBetweenSlashes("to/to"));
    assertEquals("/toto", StringUtils.encodePartsBetweenSlashes("/toto"));
    assertEquals("to//to", StringUtils.encodePartsBetweenSlashes("to//to"));
    assertEquals("to%20to", StringUtils.encodePartsBetweenSlashes("to to"));
    assertEquals("t%C3%A9to", StringUtils.encodePartsBetweenSlashes("této"));
    assertEquals("/toto/", StringUtils.encodePartsBetweenSlashes("/toto/"));
  }

}
