package com.nullbird.hfs.e2e;

import com.nullbird.hfs.bootstrap.HfsTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.e2e.RawClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class ServeStaticE2ETest {
  private static final String INDEX = "<html>index</html>";
  private static final String CSP = "default-src 'self'";
  /** Long enough to be compressed by the connector */
  private static final String LARGE_TEXT = "0123456789".repeat(200);
  private Path root;
  private HfsTestServer hfs;
  private RawClient client;

  @BeforeAll
  void start() throws Exception {
    root = Files.createTempDirectory("hfs-static-");
    Files.writeString(root.resolve("index.html"), INDEX);
    Files.writeString(root.resolve("app.js"), "console.log(1);");
    Files.writeString(root.resolve("large.txt"), LARGE_TEXT);
    Files.writeString(root.resolve("café.txt"), "café");
    Files.writeString(root.resolve("site.webmanifest"), "{}");
    Files.createDirectory(root.resolve("sub"));
    Files.writeString(root.resolve("sub/index.html"), "sub index");
    Files.createDirectory(root.resolve("noindex"));

    hfs = new HfsTestServer("""
        {
          "rules": [
            {
              "matcher": {"type": "All"},
              "action": {"type": "AddHttpHeader", "name": "Content-Security-Policy", "value": "%s"}
            },
            {
              "matcher": {"type": "All"},
              "action": {
                "type": "ServeStatic",
                "root": "%s",
                "spaFallback": "/index.html",
                "mimeTypes": {"webmanifest": "application/manifest+json"}
              }
            }
          ]
        }
        """.formatted(CSP, root.toString().replace("\\", "\\\\")));
    client = new RawClient(hfs.getPort());
  }

  @AfterAll
  void stop() throws Exception {
    hfs.close();
    try (Stream<Path> files = Files.walk(root)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  public void servesFilesWithTheirMimeTypeTest() throws Exception {
    var response = client.get("/app.js?v=3");
    assertEquals(200, response.status, response.toString());
    assertEquals("console.log(1);", response.bodyAsString());
    assertEquals("text/javascript;charset=UTF-8", response.headers.get("Content-Type"));
    assertEquals("bytes", response.headers.get("Accept-Ranges"));

    assertEquals("application/manifest+json", client.get("/site.webmanifest").headers.get("Content-Type"), "Configured MIME type");
  }

  @Test
  public void headersOfPreviousRulesAreKeptTest() throws Exception {
    assertEquals(CSP, client.get("/app.js").headers.get("Content-Security-Policy"));
  }

  @Test
  public void indexTest() throws Exception {
    var response = client.get("/");
    assertEquals(200, response.status, response.toString());
    assertEquals(INDEX, response.bodyAsString());
    assertEquals("text/html;charset=UTF-8", response.headers.get("Content-Type"));
    assertEquals("sub index", client.get("/sub/").bodyAsString());

    var redirect = client.get("/sub?a=b");
    assertEquals(302, redirect.status, redirect.toString());
    assertEquals("/sub/?a=b", redirect.headers.get("Location"));
  }

  @Test
  public void spaFallbackTest() throws Exception {
    var response = client.get("/page/login?next=/");
    assertEquals(200, response.status, response.toString());
    assertEquals(INDEX, response.bodyAsString());
    assertEquals(INDEX, client.get("/page/").bodyAsString());
    assertEquals(404, client.get("/missing.js").status, "No fallback for files with an extension");
    assertEquals(404, client.get("/noindex/").status, "Existing directory without index");
  }

  @Test
  public void encodedPathsTest() throws Exception {
    var response = client.get("/caf%C3%A9.txt");
    assertEquals(200, response.status, response.toString());
    assertEquals("café", response.bodyAsString());
    assertEquals(400, client.get("/sub%2Findex.html").status, "Encoded slash, rejected by Tomcat");
    assertEquals(400, client.get("/sub/%2e%2e/%2e%2e/etc/passwd").status, "Above the root");
  }

  @Test
  public void rangeTest() throws Exception {
    var response = client.get("/large.txt", List.of("Range: bytes=5-14"));
    assertEquals(206, response.status, response.toString());
    assertEquals("5678901234", response.bodyAsString());
    assertEquals("bytes 5-14/" + LARGE_TEXT.length(), response.headers.get("Content-Range"));

    response = client.get("/large.txt", List.of("Range: bytes=5-14", "Accept-Encoding: gzip"));
    assertEquals(206, response.status, response.toString());
    assertEquals("5678901234", response.bodyAsString(), "Partial responses are not compressed");

    assertEquals(416, client.get("/large.txt", List.of("Range: bytes=99999-")).status);
  }

  @Test
  public void fullResponseIsCompressedTest() throws Exception {
    var response = client.get("/large.txt", List.of("Accept-Encoding: gzip"));
    assertEquals(200, response.status, response.toString());
    assertEquals("gzip", response.headers.get("Content-Encoding"));
  }

  @Test
  public void conditionalRequestTest() throws Exception {
    var etag = client.get("/app.js").headers.get("ETag");
    assertNotNull(etag);
    var response = client.get("/app.js", List.of("If-None-Match: " + etag));
    assertEquals(304, response.status, response.toString());
    assertEquals(0, response.body.length);
  }

  @Test
  public void methodsTest() throws Exception {
    var head = client.send("HEAD", "/app.js", "test.local", List.of(), null);
    assertEquals(200, head.status, head.toString());
    assertEquals("15", head.headers.get("Content-Length"));
    assertEquals(0, head.body.length);

    byte[] body = "a=b".getBytes(StandardCharsets.UTF_8);
    var post = client.send("POST", "/app.js", "test.local",
        List.of("Content-Type: application/x-www-form-urlencoded", "Content-Length: " + body.length), body);
    assertEquals(405, post.status, post.toString());
    assertEquals(405, client.send("DELETE", "/app.js", "test.local", List.of(), null).status);
  }
}
