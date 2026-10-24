package com.nullbird.hfs.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServletHttpRequestTest {
  private Map<String, String> decode(String body) throws IOException {
    var map = new HashMap<String, String>();
    ServletHttpRequest.decodeInputStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), map);
    return map;
  }

  @Test
  public void wellFormedTest() throws IOException {
    assertEquals(Map.of("a", "1", "b", "hello world", "c", "é"), decode("a=1&b=hello+world&c=%C3%A9"));
  }

  @Test
  public void keysAreDecodedTest() throws IOException {
    assertEquals(Map.of("a.b", "1", "c d", "2"), decode("a%2Eb=1&c+d=2"));
  }

  @Test
  public void malformedPairsAreIgnoredTest() throws IOException {
    assertEquals(Map.of(), decode(""));
    assertEquals(Map.of(), decode("x"));
    assertEquals(Map.of(), decode("&&&"));
    assertEquals(Map.of(), decode("=value"));
    assertEquals(Map.of("a", ""), decode("a="));
    assertEquals(Map.of("b", "2"), decode("x&b=2&bad=%G1&%=3"));
    assertEquals(Map.of("a", "b=c"), decode("a=b=c"));
  }
}
