package com.nullbird.hfs.e2e;

import com.nullbird.hfs.bootstrap.HfsTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import utils.BeforeAllTests;
import utils.e2e.RawBackend;
import utils.e2e.RawClient;
import utils.e2e.RawHttp;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests of the reverse proxy: a raw socket client talks to a real HFS, which proxies to a raw socket
 * backend. Both ends see exactly the bytes on the wire.
 * <br>Some of these tests are expected to fail until the proxy is fixed: they describe the expected behavior.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class ReverseProxyE2ETest {
  private static final String HOST_TIMEOUT = "timeout.local";
  private static final String HOST_UNREACHABLE = "unreachable.local";
  private static final int TIMEOUT_MS = 1000;

  private RawBackend backend;
  private HfsTestServer hfs;
  private RawClient client;

  @BeforeAll
  void start() throws Exception {
    backend = new RawBackend();
    int closedPort;
    try (var socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    String target = "http://localhost:" + backend.getPort();
    hfs = new HfsTestServer("""
        {
          "rules": [
            {
              "matcher": {"type": "HostIs", "host": "%s"},
              "action": {"type": "ReverseProxy", "target": "%s", "connectionTimeoutMs": %d}
            },
            {
              "matcher": {"type": "HostIs", "host": "%s"},
              "action": {"type": "ReverseProxy", "target": "http://localhost:%d"}
            },
            {
              "matcher": {"type": "All"},
              "action": {"type": "ReverseProxy", "target": "%s"}
            }
          ]
        }
        """.formatted(HOST_TIMEOUT, target, TIMEOUT_MS, HOST_UNREACHABLE, closedPort, target));
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

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(size).nextBytes(bytes);
    return bytes;
  }

  private static Set<String> lowerCase(List<String> names) {
    return names.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }

  // =================================================================================================================
  // Isolation between clients

  @Test
  public void backendCookiesAreNotReplayedToOtherClientsTest() throws Exception {
    backend.setHandler((request, socket, out) ->
        RawBackend.respond(out, "200 OK", List.of("Set-Cookie: session=secret-of-A; Path=/"), new byte[0]));
    var a = client.get("/login");
    assertEquals(200, a.status, a.toString());
    assertEquals(List.of("session=secret-of-A; Path=/"), a.headers.getAll("Set-Cookie"));

    backend.reset();
    var b = client.get("/");
    assertEquals(200, b.status, b.toString());
    assertFalse(backend.getSingleReceived().headers().has("Cookie"), "Client B received the cookies of client A");
  }

  @Test
  public void parallelClientsOnlyCarryTheirOwnCredentialsTest() throws Exception {
    int nbClients = 30;
    // The backend answers with the client id, and tries to give each client its own cookie
    backend.setHandler((request, socket, out) -> {
      String id = request.headers().get("X-Client");
      RawBackend.respond(out, "200 OK", List.of("Set-Cookie: from-backend=" + id), id.getBytes(StandardCharsets.UTF_8));
    });

    ExecutorService executor = Executors.newFixedThreadPool(nbClients);
    try {
      // First wave: each client sends its own credentials
      List<Future<RawClient.Response>> wave1 = new ArrayList<>();
      for (int i = 0; i < nbClients; i++) {
        String id = String.valueOf(i);
        wave1.add(executor.submit(() -> client.get("/c/" + id,
            List.of("X-Client: " + id, "Cookie: id=" + id, "Authorization: Bearer token-" + id))));
      }
      for (int i = 0; i < nbClients; i++) {
        var response = wave1.get(i).get();
        assertEquals(200, response.status, response.toString());
        assertEquals(String.valueOf(i), response.bodyAsString(), "Client " + i + " got someone else's response");
        assertEquals(List.of("from-backend=" + i), response.headers.getAll("Set-Cookie"));
      }
      var received = backend.getReceived();
      assertEquals(nbClients, received.size());
      for (var request : received) {
        String id = request.headers().get("X-Client");
        assertEquals(List.of("id=" + id), request.headers().getAll("Cookie"), "Cookies of request " + id);
        assertEquals(List.of("Bearer token-" + id), request.headers().getAll("Authorization"), "Authorization of request " + id);
      }

      // Second wave: no credentials at all. Nothing from the first wave may leak into these requests.
      backend.reset();
      backend.setHandler((request, socket, out) ->
          RawBackend.respond(out, "200 OK", List.of(), request.headers().get("X-Client").getBytes(StandardCharsets.UTF_8)));
      List<Callable<RawClient.Response>> wave2 = new ArrayList<>();
      for (int i = 0; i < nbClients; i++) {
        String id = String.valueOf(i);
        wave2.add(() -> client.get("/c/" + id, List.of("X-Client: " + id)));
      }
      for (var future : executor.invokeAll(wave2)) {
        assertEquals(200, future.get().status, future.get().toString());
      }
      for (var request : backend.getReceived()) {
        assertFalse(request.headers().has("Cookie"), "Leaked cookie: " + request.headers().getAll("Cookie"));
        assertFalse(request.headers().has("Authorization"), "Leaked authorization: " + request.headers().getAll("Authorization"));
      }
    } finally {
      executor.shutdownNow();
    }
  }

  // =================================================================================================================
  // Request pass-through

  @Test
  public void pathNormalizationTest() {
    String[][] cases = {
        {"/a/b", "/a/b"},
        {"/a/", "/a/"},
        {"/a%20b", "/a%20b"},
        {"/caf%C3%A9", "/caf%C3%A9"},
        {"/caf%c3%a9", "/caf%C3%A9"},
        {"/%41%7e", "/A~"},
        {"/a@b:c=d,e~f!g$h&i'(k)*+l", "/a@b:c=d,e~f!g$h&i'(k)*+l"},
        {"/a+b", "/a+b"},
        {"/a;jsessionid=X/b", "/a;jsessionid=X/b"},
        {"/a%25b", "/a%25b"},
        {"/a%3Fb", "/a%3Fb"},
        {"/a%23b", "/a%23b"},
        {"/a/./b", "/a/b"},
        {"/a/../b", "/b"},
        {"/a/%2e%2e/b", "/b"},
        {"/a//b", "/a/b"},
    };
    List<Executable> checks = new ArrayList<>();
    for (String[] c : cases) {
      checks.add(() -> {
        backend.reset();
        var response = client.get(c[0]);
        assertEquals(200, response.status, c[0] + " -> " + response);
        assertEquals(c[1], backend.getSingleReceived().target(), "Path sent: " + c[0]);
      });
    }
    assertAll(checks);
  }

  @Test
  public void invalidPathsAreRejectedTest() {
    List<Executable> checks = new ArrayList<>();
    for (String path : List.of("/../a", "/a%2Fb")) {
      checks.add(() -> {
        backend.reset();
        var response = client.get(path);
        assertEquals(400, response.status, path + " -> " + response);
        assertEquals(0, backend.getReceived().size(), path + " must not reach the backend");
      });
    }
    assertAll(checks);
  }

  @Test
  public void queryStringIsVerbatimTest() throws Exception {
    String target = "/q?x=a%20b&y=+&z&%41&w=%c3%a9&v=a/b?c";
    var response = client.get(target);
    assertEquals(200, response.status, response.toString());
    assertEquals(target, backend.getSingleReceived().target());
  }

  @Test
  public void multiValuedHeadersArePreservedTest() throws Exception {
    var response = client.get("/", List.of("X-Multi: a", "X-Multi: b, c", "Accept: text/html", "Accept: */*"));
    assertEquals(200, response.status, response.toString());
    var headers = backend.getSingleReceived().headers();
    assertEquals(List.of("a", "b, c"), headers.getAll("X-Multi"));
    assertEquals(List.of("text/html", "*/*"), headers.getAll("Accept"));
  }

  @Test
  public void hopByHopHeadersAreStrippedTest() throws Exception {
    var response = client.get("/", List.of(
        "Connection: X-Hop",
        "X-Hop: must-not-be-forwarded",
        "Keep-Alive: timeout=5",
        "TE: trailers",
        "Proxy-Authorization: Basic Zm9vOmJhcg==",
        "X-End-To-End: kept"));
    assertEquals(200, response.status, response.toString());
    var headers = backend.getSingleReceived().headers();
    assertAll(
        () -> assertFalse(headers.has("X-Hop"), "Header listed in Connection must be stripped"),
        () -> assertFalse(headers.has("TE"), "TE must be stripped"),
        () -> assertFalse(headers.has("Proxy-Authorization"), "Proxy-Authorization must be stripped"),
        () -> assertFalse(headers.getAll("Keep-Alive").contains("timeout=5"), "The client's Keep-Alive must not be forwarded"),
        () -> assertFalse(headers.getAll("Connection").stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains("x-hop")),
            "The client's Connection must not be forwarded"),
        () -> assertEquals("kept", headers.get("X-End-To-End")));
  }

  @Test
  public void noHeaderIsInjectedTest() throws Exception {
    // The client only sends Host (and Connection, which is hop-by-hop)
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    var headers = backend.getSingleReceived().headers();
    var names = lowerCase(headers.names());
    // Connection management between HFS and the backend is HFS's own business
    names.remove("connection");
    names.remove("keep-alive");
    assertEquals(Set.of("host", "x-forwarded-for", "x-forwarded-proto"), names, headers.toString());
    assertEquals("test.local", headers.get("Host"));
  }

  @Test
  public void contentLengthBodyIsPreservedTest() throws Exception {
    byte[] body = randomBytes(1024 * 1024);
    var response = client.send("POST", "/upload", "test.local",
        List.of("Content-Type: application/octet-stream", "Content-Length: " + body.length), body);
    assertEquals(200, response.status, response.toString());
    var received = backend.getSingleReceived();
    assertFalse(received.chunked(), "The body must not be re-encoded as chunked");
    assertEquals(String.valueOf(body.length), received.headers().get("Content-Length"));
    assertArrayEquals(body, received.body());
  }

  @Test
  public void chunkedBodyIsPreservedTest() throws Exception {
    byte[] body = randomBytes(300 * 1024);
    var response = client.send("POST", "/upload", "test.local",
        List.of("Content-Type: application/octet-stream", "Transfer-Encoding: chunked"),
        RawHttp.chunkedBytes(body, 7000));
    assertEquals(200, response.status, response.toString());
    assertArrayEquals(body, backend.getSingleReceived().body());
  }

  // =================================================================================================================
  // Response pass-through

  @Test
  public void statusCodesArePreservedTest() {
    List<Executable> checks = new ArrayList<>();
    for (int status : List.of(200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 500, 502, 503)) {
      checks.add(() -> {
        backend.reset();
        boolean noBody = status == 204 || status == 304;
        String body = "status " + status;
        backend.setHandler((request, socket, out) -> {
          if (noBody) {
            RawBackend.respondNoBody(out, status + " Whatever", List.of());
          } else {
            // Location points to the backend itself, so a followed redirect would show as a 2nd request
            RawBackend.respond(out, status + " Whatever",
                List.of("Location: http://localhost:" + backend.getPort() + "/elsewhere"),
                body.getBytes(StandardCharsets.UTF_8));
          }
        });
        var response = client.get("/");
        assertEquals(status, response.status, response.toString());
        assertTrue(response.complete, response.toString());
        if (!noBody) assertEquals(body, response.bodyAsString(), "Body of a " + status);
        assertEquals(1, backend.getReceived().size(), "Requests on the backend for a " + status);
      });
    }
    assertAll(checks);
  }

  @Test
  public void redirectsAreNotFollowedTest() throws Exception {
    String location = "http://localhost:" + backend.getPort() + "/elsewhere";
    backend.setHandler((request, socket, out) ->
        RawBackend.respond(out, "302 Found", List.of("Location: " + location), new byte[0]));
    var response = client.get("/");
    assertEquals(302, response.status, response.toString());
    assertEquals(location, response.headers.get("Location"));
    assertEquals(1, backend.getReceived().size());
  }

  @Test
  public void authChallengesAreNotHandledTest() throws Exception {
    backend.setHandler((request, socket, out) ->
        RawBackend.respond(out, "401 Unauthorized", List.of("WWW-Authenticate: Basic realm=\"test\""), new byte[0]));
    var response = client.get("/");
    assertEquals(401, response.status, response.toString());
    assertEquals("Basic realm=\"test\"", response.headers.get("WWW-Authenticate"));
    assertEquals(1, backend.getReceived().size());
  }

  @Test
  public void multipleSetCookieArePreservedTest() throws Exception {
    var cookies = List.of(
        "a=1; Path=/",
        "b=2; Expires=Wed, 21 Oct 2026 07:28:00 GMT; HttpOnly",
        "c=3; Domain=test.local; Secure; SameSite=Strict");
    backend.setHandler((request, socket, out) -> RawBackend.respond(out, "200 OK",
        cookies.stream().map(c -> "Set-Cookie: " + c).toList(), new byte[0]));
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    assertEquals(cookies, response.headers.getAll("Set-Cookie"));
  }

  @Test
  public void responseHeadersArePreservedTest() throws Exception {
    backend.setHandler((request, socket, out) -> RawBackend.respond(out, "200 OK", List.of(
        "X-Custom: Some Value",
        "X-Multi: a",
        "X-Multi: b",
        "Cache-Control: no-cache, private",
        "ETag: \"abc\"",
        "Content-Type: application/json"), "{}".getBytes(StandardCharsets.UTF_8)));
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    assertEquals("Some Value", response.headers.get("X-Custom"));
    assertEquals(List.of("a", "b"), response.headers.getAll("X-Multi"));
    assertEquals("no-cache, private", response.headers.get("Cache-Control"));
    assertEquals("\"abc\"", response.headers.get("ETag"));
    assertEquals("application/json", response.headers.get("Content-Type"));
    assertEquals("{}", response.bodyAsString());
  }

  @Test
  public void largeBinaryResponseIsPreservedTest() throws Exception {
    byte[] body = randomBytes(5 * 1024 * 1024);
    backend.setHandler((request, socket, out) ->
        RawBackend.respond(out, "200 OK", List.of("Content-Type: application/octet-stream"), body));
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    assertTrue(response.complete, response.failure);
    assertArrayEquals(body, response.body);
  }

  @Test
  public void chunkedResponseIsPreservedTest() throws Exception {
    byte[] body = randomBytes(1024 * 1024);
    backend.setHandler((request, socket, out) -> {
      RawBackend.respondNoBody(out, "200 OK", List.of("Content-Type: application/octet-stream", "Transfer-Encoding: chunked"));
      out.write(RawHttp.chunkedBytes(body, 10_000));
    });
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    assertTrue(response.complete, response.failure);
    assertArrayEquals(body, response.body);
  }

  // =================================================================================================================
  // Backend failures

  @Test
  public void unreachableBackendTest() throws Exception {
    var response = client.send("GET", "/", HOST_UNREACHABLE, List.of(), null);
    assertEquals(502, response.status, response.toString());
  }

  @Test
  public void backendClosingWithoutResponseIsNotRetriedTest() {
    List<Executable> checks = new ArrayList<>();
    for (String method : List.of("GET", "POST")) {
      checks.add(() -> {
        backend.reset();
        backend.setHandler((request, socket, out) -> socket.close());
        byte[] body = method.equals("POST") ? "data".getBytes(StandardCharsets.UTF_8) : null;
        var headers = body == null ? List.<String>of() : List.of("Content-Length: " + body.length);
        var response = client.send(method, "/", "test.local", headers, body);
        assertAll(
            () -> assertEquals(502, response.status, method + " -> " + response),
            () -> assertEquals(1, backend.getReceived().size(), method + " must reach the backend exactly once"));
      });
    }
    assertAll(checks);
  }

  @Test
  public void backendDyingMidBodyWithContentLengthAbortsTheClientTest() throws Exception {
    backend.setHandler((request, socket, out) -> {
      RawBackend.respondNoBody(out, "200 OK", List.of("Content-Length: 100000"));
      out.write(randomBytes(1000));
      out.flush();
      socket.close();
    });
    var response = client.get("/");
    assertFalse(response.timedOut, "HFS must not leave the client hanging");
    assertFalse(response.complete, "The client must see the response as failed, not as a complete response: " + response.status);
  }

  @Test
  public void backendDyingMidChunkedBodyAbortsTheClientTest() throws Exception {
    backend.setHandler((request, socket, out) -> {
      RawBackend.respondNoBody(out, "200 OK", List.of("Transfer-Encoding: chunked"));
      out.write("3e8\r\n".getBytes(StandardCharsets.ISO_8859_1));
      out.write(randomBytes(1000));
      out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
      socket.close();
    });
    var response = client.get("/");
    assertFalse(response.timedOut, "HFS must not leave the client hanging");
    assertFalse(response.complete, "The client must see the response as failed, not as a complete response: " + response.status);
  }

  @Test
  public void hangingBackendTimesOutTest() throws Exception {
    backend.setHandler((request, socket, out) -> Thread.sleep(30_000));
    long start = System.currentTimeMillis();
    var response = client.send("GET", "/", HOST_TIMEOUT, List.of(), null);
    long elapsed = System.currentTimeMillis() - start;
    assertEquals(504, response.status, response.toString());
    assertTrue(elapsed < TIMEOUT_MS * 5L, "Took " + elapsed + "ms with a timeout of " + TIMEOUT_MS + "ms");
  }
}
