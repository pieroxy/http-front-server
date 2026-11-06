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
import utils.e2e.RawHttp;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A backend reading request bodies slower than the client sends them: the proxy must hold the data back, not lose it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class SlowBackendE2ETest {
  /** Well over what the kernel buffers on both sides of the backend connection can absorb */
  private static final int BODY_SIZE = 32 * 1024 * 1024;

  private RawBackend backend;
  private HfsTestServer hfs;
  private RawClient client;

  @BeforeAll
  void start() throws Exception {
    backend = new RawBackend(16 * 1024);
    hfs = new HfsTestServer("""
        {
          "rules": [
            {
              "matcher": {"type": "All"},
              "action": {"type": "ReverseProxy", "target": "http://localhost:%d"}
            }
          ]
        }
        """.formatted(backend.getPort()));
    client = new RawClient(hfs.getPort());
  }

  @BeforeEach
  void resetBackend() {
    backend.reset();
    backend.setReadThrottle(16 * 1024, 1);
  }

  @AfterAll
  void stop() throws Exception {
    hfs.close();
    backend.close();
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(size).nextBytes(bytes);
    return bytes;
  }

  @Test
  public void contentLengthBodyIsPreservedTest() throws Exception {
    byte[] body = randomBytes(BODY_SIZE);
    var response = client.send("POST", "/upload", "test.local",
        List.of("Content-Type: application/octet-stream", "Content-Length: " + body.length), body);
    assertEquals(200, response.status, response.toString());
    assertArrayEquals(body, backend.getSingleReceived().body());
  }

  @Test
  public void chunkedBodyIsPreservedTest() throws Exception {
    byte[] body = randomBytes(BODY_SIZE);
    var response = client.send("POST", "/upload", "test.local",
        List.of("Content-Type: application/octet-stream", "Transfer-Encoding: chunked"),
        RawHttp.chunkedBytes(body, 7000));
    assertEquals(200, response.status, response.toString());
    assertArrayEquals(body, backend.getSingleReceived().body());
  }
}
