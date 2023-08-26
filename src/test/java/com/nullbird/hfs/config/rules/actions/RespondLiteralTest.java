package com.nullbird.hfs.config.rules.actions;

import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import utils.TestResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RespondLiteralTest {
  @Test
  public void simpleTest() throws Exception {
    var action = new RespondLiteral();
    action.setContent("This is the content");
    action.setContentType("This is the content type");
    TestResponse response;
    action.run(null, response = new TestResponse(), null);
    assertEquals("This is the content", new String(response.getBody()), "Body incorrect");
    assertEquals("This is the content type".toLowerCase(), response.getHeaders().get(HttpHeaders.CONTENT_TYPE), "Body incorrect");
    assertEquals(200, response.getStatus(), "Status incorrect");
  }
}
