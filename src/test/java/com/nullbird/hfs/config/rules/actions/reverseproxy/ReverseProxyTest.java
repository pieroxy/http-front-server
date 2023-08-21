package com.nullbird.hfs.config.rules.actions.reverseproxy;

import com.nullbird.hfs.config.rules.actions.proxy.ReverseProxy;
import com.nullbird.hfs.config.rules.actions.proxy.ReverseProxyResponseConsumer;
import com.nullbird.hfs.utils.HashTools;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.TestRequest;
import utils.testTomcat.TestServlet;
import utils.testTomcat.TestTomcat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({BeforeAllTests.class})
public class ReverseProxyTest {
  private TestTomcat tomcat;

  @BeforeAll
  void startTomcat() throws Exception {
    this.tomcat = new TestTomcat(12345, new TestServlet());
  }
  @Test
  public void simpleGetTest() throws Exception {
    var action = new ReverseProxy();
    action.setTarget("http://localhost:12345");
    action.initialize(null);
    var request = TestRequest.fromUrl("http://iii");
    request.getResponse().setToNotifyOnCompletion(this);
    action.run(request, request.getResponse(), null);
    waitOnResponse(request);
    assertEquals(200, request.getResponse().getStatus());
    List<String> lines = Stream.of(new String(request.getResponse().getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
            .map(String::toLowerCase).toList();
    assertEquals("ok", lines.get(0));
    assertTrue(lines.contains("header::host: iii"));
    assertEquals("method:get", lines.get(1));
    assertEquals("url:http://iii/", lines.get(3));
  }

  private void waitOnResponse(TestRequest request) {
    synchronized(this) {
      while (true) {
        try {
          this.wait(1000);
        } catch (Exception e) {
        }
        if (request.getResponse().isProcessingCompleted()) break;
      }
    }
  }

  @Test
  public void uriGetTest() throws Exception {
    var action = new ReverseProxy();
    action.setTarget("http://localhost:12345");
    action.initialize(null);
    var request = TestRequest.fromUrl("http://iii/this/is/the/uri?toto=2");
    action.run(request, request.getResponse(), null);
    waitOnResponse(request);
    assertEquals(200, request.getResponse().getStatus());
    List<String> lines = Stream.of(new String(request.getResponse().getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
            .map(String::toLowerCase).toList();
    assertEquals("ok", lines.get(0));
    assertTrue(lines.contains("header::host: iii"));
    assertEquals("method:get", lines.get(1));
    assertEquals("url:http://iii/this/is/the/uri", lines.get(3));
    assertEquals("qs:toto=2", lines.get(4));
  }
  @Test
  public void simplePostTest() throws Exception {
    var action = new ReverseProxy();
    action.setTarget("http://localhost:12345");
    action.initialize(null);
    var req = TestRequest.fromUrl("http://test.domain.com");
    req.setMethod("POST");
    req.setBodyAsByteArray("This is the body of the request.".getBytes(StandardCharsets.UTF_8));
    action.run(req, req.getResponse(), null);
    waitOnResponse(req);
    assertEquals(200, req.getResponse().getStatus());
    List<String> lines = Stream.of(new String(req.getResponse().getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
            .map(String::toLowerCase).toList();
    //lines.forEach(System.out::println);
    assertEquals("ok", lines.get(0));
    assertTrue(lines.contains("header::host: test.domain.com"));
    assertTrue(
            lines.contains("header::content-length: " + req.getBodyAsByteArray().length) ||
                    lines.contains("header::transfer-encoding: chunked")
    );
    assertEquals("method:post", lines.get(1));
    assertEquals("body:this is the body of the request.", lines.get(2));
  }

  @Test
  public void simpleBadHostTest() throws Exception {
    var action = new ReverseProxy();
    action.setTarget("http://A_" + HashTools.getRandomSequence(100));
    action.initialize(null);
    var req = TestRequest.fromUrl("http://test.domain.com");
    action.run(req, req.getResponse(), null);
    waitOnResponse(req);

    assertEquals(HttpServletResponse.SC_BAD_GATEWAY, req.getResponse().getStatus());
    List<String> lines = Stream.of(new String(req.getResponse().getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
            .map(String::toLowerCase).toList();
    assertTrue(lines.get(0).contains(ReverseProxyResponseConsumer.MSG_BAD_GATEWAY.toLowerCase()));
  }

  @AfterAll
  void end() {
    tomcat.shutdown();
  }
}

