package com.nullbird.hfs.config.rules.actions.reverseproxy;

import com.nullbird.hfs.config.rules.actions.proxy.ReverseProxy;
import com.nullbird.hfs.utils.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.TestRequest;
import utils.TestResponse;
import utils.testTomcat.TestServlet;
import utils.testTomcat.TestTomcat;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
    var response = new TestResponse();
    action.run(TestRequest.fromUrl("http://iii"), response, null);
    assertEquals(200, response.getStatus());
    List<String> lines = Stream.of(new String(response.getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
            .map(String::toLowerCase).toList();
    assertEquals("ok", lines.get(0));
    assertTrue(lines.contains("header::host: iii"));
    assertEquals("method:get", lines.get(1));
    assertEquals("url:http://iii/", lines.get(3));
  }
  @Test
  public void uriGetTest() throws Exception {
    var action = new ReverseProxy();
    action.setTarget("http://localhost:12345");
    action.initialize(null);
    var response = new TestResponse();
    action.run(TestRequest.fromUrl("http://iii/this/is/the/uri?toto=2"), response, null);
    assertEquals(200, response.getStatus());
    List<String> lines = Stream.of(new String(response.getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
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
    var response = new TestResponse();
    var req = TestRequest.fromUrl("http://test.domain.com");
    req.setMethod("POST");
    req.setBodyAsByteArray("This is the body of the request.".getBytes(StandardCharsets.UTF_8));
    action.run(req, response, null);
    assertEquals(200, response.getStatus());
    List<String> lines = Stream.of(new String(response.getBody(), StandardCharsets.UTF_8).split("\\r?\\n"))
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

  @AfterAll
  void end() {
    tomcat.shutdown();
  }
}

