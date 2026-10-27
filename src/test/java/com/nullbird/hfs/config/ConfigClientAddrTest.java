package com.nullbird.hfs.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigClientAddrTest {
  private static Config withTrustedProxies(String... proxies) throws Exception {
    var config = new Config();
    config.setTrustedProxies(List.of(proxies));
    config.parseTrustedProxies();
    return config;
  }

  @Test
  public void untrustedPeerIsTheClientTest() throws Exception {
    var config = withTrustedProxies("10.0.0.0/8");
    assertEquals("8.8.8.8", config.resolveClientAddr("8.8.8.8", List.of("6.6.6.6")));
    assertEquals("8.8.8.8", new Config().resolveClientAddr("8.8.8.8", List.of("6.6.6.6")), "No trusted proxy by default");
  }

  @Test
  public void trustedProxiesAreSkippedFromTheRightTest() throws Exception {
    var config = withTrustedProxies("10.0.0.0/8", "::1");
    assertAll(
        () -> assertEquals("6.6.6.6", config.resolveClientAddr("10.0.0.1", List.of("6.6.6.6"))),
        // The client may send anything on the left: only what the trusted proxies appended counts
        () -> assertEquals("7.7.7.7", config.resolveClientAddr("10.0.0.1", List.of("1.1.1.1, 7.7.7.7"))),
        () -> assertEquals("7.7.7.7", config.resolveClientAddr("10.0.0.1", List.of("1.1.1.1, 7.7.7.7, 10.0.0.2"))),
        () -> assertEquals("7.7.7.7", config.resolveClientAddr("::1", List.of("1.1.1.1", " 7.7.7.7 ,10.0.0.2"))),
        () -> assertEquals("2001:db8::1", config.resolveClientAddr("10.0.0.1", List.of("2001:db8::1"))));
  }

  @Test
  public void edgeCasesTest() throws Exception {
    var config = withTrustedProxies("10.0.0.0/8");
    assertAll(
        () -> assertEquals("10.0.0.1", config.resolveClientAddr("10.0.0.1", List.of()), "No X-Forwarded-For"),
        () -> assertEquals("10.0.0.3", config.resolveClientAddr("10.0.0.1", List.of("10.0.0.3, 10.0.0.2")), "Only trusted proxies: the leftmost"),
        () -> assertEquals("10.0.0.2", config.resolveClientAddr("10.0.0.1", List.of("6.6.6.6, unknown, 10.0.0.2")), "Garbage: the proxy that added it"),
        () -> assertEquals("10.0.0.1", config.resolveClientAddr("10.0.0.1", List.of("my-host")), "Host names are not addresses"),
        () -> assertEquals("6.6.6.6", config.resolveClientAddr("10.0.0.1", List.of(" , 6.6.6.6,")), "Empty entries are ignored"));
  }
}
