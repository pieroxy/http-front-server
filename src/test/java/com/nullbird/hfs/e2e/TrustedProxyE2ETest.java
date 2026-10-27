package com.nullbird.hfs.e2e;

import com.nullbird.hfs.bootstrap.HfsTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.e2e.RawBackend;
import utils.e2e.RawClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HFS behind a trusted proxy: the test client (127.0.0.1) plays the proxy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class TrustedProxyE2ETest {
  /** Not the address of SPOOFED_FORWARDING_HEADERS, whose requests must reach the backend */
  private static final String IP_MATCHED = "matched 9.9.9.9";
  private RawBackend backend;
  private HfsTestServer hfs;
  private RawClient client;

  @BeforeAll
  void start() throws Exception {
    backend = new RawBackend();
    hfs = new HfsTestServer("""
        {
          "trustedProxies": ["127.0.0.0/8", "::1"],
          "rules": [
            {
              "matcher": {"type": "IPAddressIs", "ipAddress": "9.9.9.9"},
              "action": {"type": "RespondLiteral", "contentType": "text/plain", "content": "%s"}
            },
            {
              "matcher": {"type": "All"},
              "action": {"type": "ReverseProxy", "target": "http://localhost:%d"}
            }
          ]
        }
        """.formatted(IP_MATCHED, backend.getPort()));
    client = new RawClient(hfs.getPort());
  }

  @BeforeEach
  void resetBackend() {
    backend.reset();
  }

  @AfterAll
  void stop() throws Exception {
    hfs.close();
    backend.close();
  }

  @Test
  public void forwardingHeadersArePassedOnTest() throws Exception {
    var response = client.get("/", ReverseProxyE2ETest.SPOOFED_FORWARDING_HEADERS);
    assertEquals(200, response.status, response.toString());
    var headers = backend.getSingleReceived().headers();
    List<String> checks = ReverseProxyE2ETest.SPOOFED_FORWARDING_HEADERS.stream()
        .filter(h -> !h.startsWith("X-Forwarded-For:"))
        .toList();
    assertAll(
        () -> assertEquals(List.of("6.6.6.6, 127.0.0.1"), headers.getAll("X-Forwarded-For"), "Completed with the proxy's address"),
        () -> assertAll(checks.stream().map(h -> () -> {
          String name = h.substring(0, h.indexOf(':'));
          assertEquals(List.of(h.substring(h.indexOf(':') + 1).trim()), headers.getAll(name), name);
        })));
  }

  @Test
  public void ipAddressIsSeesTheForwardedClientTest() throws Exception {
    assertEquals(IP_MATCHED, client.get("/", List.of("X-Forwarded-For: 9.9.9.9")).bodyAsString());
    // Appended by a trusted proxy (127.0.0.2) in front of the test client
    assertEquals(IP_MATCHED, client.get("/", List.of("X-Forwarded-For: 1.1.1.1, 9.9.9.9, 127.0.0.2")).bodyAsString());
    // Spoofed on the left by the client itself
    assertNotEquals(IP_MATCHED, client.get("/", List.of("X-Forwarded-For: 9.9.9.9, 1.1.1.1")).bodyAsString());
  }

  @Test
  public void severalForwardedForHeadersAreJoinedTest() throws Exception {
    var response = client.get("/", List.of("X-Forwarded-For: 1.1.1.1", "X-Forwarded-For: 2.2.2.2, 3.3.3.3"));
    assertEquals(200, response.status, response.toString());
    assertEquals(List.of("1.1.1.1, 2.2.2.2, 3.3.3.3, 127.0.0.1"), backend.getSingleReceived().headers().getAll("X-Forwarded-For"));
  }

  @Test
  public void missingForwardingHeadersAreSetByHfsTest() throws Exception {
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    var headers = backend.getSingleReceived().headers();
    assertEquals(List.of("127.0.0.1"), headers.getAll("X-Forwarded-For"));
    assertEquals(List.of("http"), headers.getAll("X-Forwarded-Proto"));
  }
}
