package com.nullbird.hfs.e2e;

import com.nullbird.hfs.bootstrap.HfsTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.e2e.RawBackend;
import utils.e2e.RawClient;
import utils.e2e.RawHttp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clients that send their request body, or read the response, slower than the backend: they must not hold up the
 * requests of the other clients.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class SlowClientE2ETest {
  /** More than the I/O threads of the proxy client, which default to the number of CPUs */
  private static final int SLOW_CLIENTS = 2 * Runtime.getRuntime().availableProcessors();
  private static final long MAX_RESPONSE_TIME_MS = 3000;
  private static final String HOST_SHORT_TIMEOUT = "short-timeout.local";
  private static final int SHORT_TIMEOUT_MS = 1000;
  private static final byte[] CHUNK = chunk(64 * 1024);

  private RawBackend backend;
  private HfsTestServer hfs;
  private RawClient client;
  private final List<Socket> slowClients = new ArrayList<>();

  @BeforeAll
  void start() throws Exception {
    backend = new RawBackend();
    hfs = new HfsTestServer("""
        {
          "rules": [
            {
              "matcher": {"type": "HostIs", "host": "%s"},
              "action": {"type": "ReverseProxy", "target": "http://localhost:%d", "readTimeoutMs": %d}
            },
            {
              "matcher": {"type": "All"},
              "action": {"type": "ReverseProxy", "target": "http://localhost:%d"}
            }
          ]
        }
        """.formatted(HOST_SHORT_TIMEOUT, backend.getPort(), SHORT_TIMEOUT_MS, backend.getPort()));
    client = new RawClient(hfs.getPort());
  }

  @BeforeEach
  void resetBackend() {
    backend.reset();
  }

  @AfterEach
  void closeSlowClients() throws IOException {
    for (Socket socket : slowClients) socket.close();
    slowClients.clear();
  }

  @AfterAll
  void stop() throws Exception {
    hfs.close();
    backend.close();
  }

  /** One chunk of a chunked body, holding <code>size</code> bytes */
  private static byte[] chunk(int size) {
    byte[] head = (Integer.toHexString(size) + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
    byte[] chunk = new byte[head.length + size + 2];
    System.arraycopy(head, 0, chunk, 0, head.length);
    chunk[chunk.length - 2] = '\r';
    chunk[chunk.length - 1] = '\n';
    return chunk;
  }

  private Socket openSlowClient(int receiveBufferSize, String request) throws IOException {
    var socket = new Socket();
    slowClients.add(socket);
    // Must be set before connecting to be taken into account
    if (receiveBufferSize > 0) socket.setReceiveBufferSize(receiveBufferSize);
    socket.connect(new InetSocketAddress("localhost", hfs.getPort()));
    socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
    socket.getOutputStream().flush();
    return socket;
  }

  private void waitForBackendRequests(int count) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (backend.getReceived().size() < count) {
      if (System.currentTimeMillis() > deadline) {
        fail("The backend got " + backend.getReceived().size() + " requests out of " + count);
      }
      Thread.sleep(10);
    }
  }

  private void assertOtherClientsAreServed() throws IOException {
    long start = System.currentTimeMillis();
    var response = client.get("/other");
    long elapsed = System.currentTimeMillis() - start;
    assertEquals(200, response.status, response.toString());
    assertTrue(elapsed < MAX_RESPONSE_TIME_MS, "The request took " + elapsed + "ms");
  }

  @Test
  public void clientsNotReadingTheResponseDoNotHoldUpOthersTest() throws Exception {
    backend.setHandler((request, socket, out) -> {
      if (!request.target().equals("/endless")) {
        RawBackend.respondOk(request, socket, out);
        return;
      }
      RawBackend.respondNoBody(out, "200 OK", List.of("Content-Type: application/octet-stream", "Transfer-Encoding: chunked"));
      // Until the proxy closes the connection
      while (true) out.write(CHUNK);
    });
    for (int i = 0; i < SLOW_CLIENTS; i++) {
      // Never read: the small receive buffer is filled at once
      openSlowClient(4096, "GET /endless HTTP/1.1\r\nHost: test.local\r\n\r\n");
    }
    waitForBackendRequests(SLOW_CLIENTS);
    // Lets the buffers between the proxy and the clients fill up
    Thread.sleep(1000);
    assertOtherClientsAreServed();
  }

  @Test
  public void clientsSendingTheBodySlowlyDoNotHoldUpOthersTest() throws Exception {
    for (int i = 0; i < SLOW_CLIENTS; i++) {
      // Announces a body it never sends
      openSlowClient(0, "POST /upload HTTP/1.1\r\nHost: test.local\r\nContent-Type: application/octet-stream\r\n" +
          "Content-Length: 1000000\r\n\r\nstarted");
    }
    // The backend only records requests once their body is received: give the proxy the time to start them all
    Thread.sleep(1000);
    assertOtherClientsAreServed();
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(size).nextBytes(bytes);
    return bytes;
  }

  /** A backend handler streaming a chunked body until the proxy closes the connection, which counts down the latch */
  private static RawBackend.Handler endless(CountDownLatch closed) {
    return (request, socket, out) -> {
      RawBackend.respondNoBody(out, "200 OK", List.of("Content-Type: application/octet-stream", "Transfer-Encoding: chunked"));
      try {
        while (true) out.write(CHUNK);
      } catch (IOException e) {
        closed.countDown();
        throw e;
      }
    };
  }

  /** The CPU time used by the whole JVM */
  private static long cpuTimeNanos() {
    var threads = ManagementFactory.getThreadMXBean();
    long total = 0;
    for (long id : threads.getAllThreadIds()) {
      long time = threads.getThreadCpuTime(id);
      if (time > 0) total += time;
    }
    return total;
  }

  @Test
  public void slowReadingClientGetsTheWholeResponseTest() throws Exception {
    byte[] body = randomBytes(4 * 1024 * 1024);
    backend.setHandler((request, socket, out) ->
        RawBackend.respond(out, "200 OK", List.of("Content-Type: application/octet-stream"), body));
    var socket = openSlowClient(4096, "GET / HTTP/1.1\r\nHost: test.local\r\nConnection: close\r\n\r\n");
    socket.setSoTimeout(RawClient.READ_TIMEOUT_MS);
    InputStream in = new BufferedInputStream(socket.getInputStream());
    assertEquals("HTTP/1.1 200 ", RawHttp.readLine(in).substring(0, 13));
    var headers = RawHttp.readHeaders(in);
    assertEquals(String.valueOf(body.length), headers.get("Content-Length"));
    byte[] received = new byte[body.length];
    int total = 0;
    while (total < body.length) {
      int read = in.read(received, total, Math.min(16 * 1024, body.length - total));
      if (read < 0) break;
      total += read;
      Thread.sleep(1);
    }
    assertEquals(body.length, total);
    assertArrayEquals(body, received);
  }

  @Test
  public void slowSendingClientBodyIsPreservedTest() throws Exception {
    byte[] body = randomBytes(4 * 1024 * 1024);
    var socket = openSlowClient(0, "POST /upload HTTP/1.1\r\nHost: test.local\r\nConnection: close\r\n" +
        "Content-Type: application/octet-stream\r\nContent-Length: " + body.length + "\r\n\r\n");
    socket.setSoTimeout(RawClient.READ_TIMEOUT_MS);
    OutputStream out = socket.getOutputStream();
    for (int offset = 0; offset < body.length; offset += 16 * 1024) {
      out.write(body, offset, Math.min(16 * 1024, body.length - offset));
      out.flush();
      Thread.sleep(1);
    }
    InputStream in = new BufferedInputStream(socket.getInputStream());
    assertEquals("HTTP/1.1 200 ", RawHttp.readLine(in).substring(0, 13));
    assertArrayEquals(body, backend.getSingleReceived().body());
  }

  @Test
  public void clientLeavingEndsTheBackendExchangeTest() throws Exception {
    var closed = new CountDownLatch(1);
    backend.setHandler(endless(closed));
    var socket = openSlowClient(0, "GET /endless HTTP/1.1\r\nHost: test.local\r\n\r\n");
    socket.setSoTimeout(RawClient.READ_TIMEOUT_MS);
    InputStream in = socket.getInputStream();
    in.readNBytes(100_000);
    socket.close();
    assertTrue(closed.await(5, TimeUnit.SECONDS), "The backend connection must be closed");
  }

  @Test
  public void clientNotReadingIsCutOffByTheReadTimeoutTest() throws Exception {
    var closed = new CountDownLatch(1);
    backend.setHandler(endless(closed));
    openSlowClient(4096, "GET /endless HTTP/1.1\r\nHost: " + HOST_SHORT_TIMEOUT + "\r\n\r\n");
    assertTrue(closed.await(5 * SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "The backend connection must be closed");
  }

  @Test
  public void stalledUploadIsCutOffByTheReadTimeoutTest() throws Exception {
    var socket = openSlowClient(0, "POST /upload HTTP/1.1\r\nHost: " + HOST_SHORT_TIMEOUT + "\r\n" +
        "Content-Type: application/octet-stream\r\nContent-Length: 1000000\r\n\r\nstarted");
    socket.setSoTimeout(5 * SHORT_TIMEOUT_MS);
    // Tomcat keeps the connection open while it waits for the rest of the body, but the client gets its answer
    InputStream in = new BufferedInputStream(socket.getInputStream());
    assertEquals("HTTP/1.1 504 ", RawHttp.readLine(in).substring(0, 13));
    var headers = RawHttp.readHeaders(in);
    int length = Integer.parseInt(headers.get("Content-Length"));
    assertEquals(length, in.readNBytes(length).length);
  }

  @Test
  public void stalledClientsDoNotMakeTheProxySpinTest() throws Exception {
    var closed = new CountDownLatch(SLOW_CLIENTS);
    backend.setHandler((request, socket, out) -> {
      if (request.target().equals("/endless")) endless(closed).handle(request, socket, out);
      else RawBackend.respondOk(request, socket, out);
    });
    for (int i = 0; i < SLOW_CLIENTS; i++) {
      openSlowClient(4096, "GET /endless HTTP/1.1\r\nHost: test.local\r\n\r\n");
      openSlowClient(0, "POST /upload HTTP/1.1\r\nHost: test.local\r\nContent-Type: application/octet-stream\r\n" +
          "Content-Length: 1000000\r\n\r\nstarted");
    }
    waitForBackendRequests(SLOW_CLIENTS);
    Thread.sleep(1000);
    long cpu = cpuTimeNanos();
    Thread.sleep(2000);
    long used = TimeUnit.NANOSECONDS.toMillis(cpuTimeNanos() - cpu);
    assertTrue(used < 500, "The JVM used " + used + "ms of CPU in 2s while all clients were stalled");
  }
}
