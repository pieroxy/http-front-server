package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TestRequest;
import utils.TestResponse;
import utils.TestSubstringRuleMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpRedirectTest {
  Config basicConfig;
  @BeforeEach
  public void init() {
    basicConfig = new Config();
    basicConfig.setDefaultRedirectStatus(311);
  }
  @Test
  public void simpleTest() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setStatus(302);
    action.setTarget("http://blu");
    action.initialize(null);

    TestResponse response;
    action.run(null, response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(302, response.getStatus(), "Status do not match");
  }

  @Test
  public void statusTest() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setStatus(301);
    action.setTarget("http://blu");
    action.initialize(null);

    TestResponse response;
    action.run(null, response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(301, response.getStatus(), "Status do not match");
  }
  @Test
  public void defaultStatusTest() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setTarget("http://blu");
    action.initialize(basicConfig);

    TestResponse response;
    action.run(null, response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");
  }
  @Test
  public void testWithSubstring() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setTarget("https://");
    action.setSubstring(true);
    action.initialize(basicConfig);

    TestResponse response;
    action.run(TestRequest.fromUrl("http://comeon.com/"), response = new TestResponse(), new TestSubstringRuleMatcher("http://"));
    assertEquals("https://comeon.com/", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");
  }
  @Test
  public void testBadconfig() {
    var action = new HttpRedirect();
    action.setSubstring(true);
    assertThrows(ConfigurationException.class, ()->action.initialize(basicConfig));
  }
  @Test
  public void testNoQueryString() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setTarget("http://blu");
    action.setIncludeQueryString(false);
    action.initialize(basicConfig);

    TestResponse response;

    action.run(TestRequest.fromUrl("http://comeon.com/"), response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");

    action.run(TestRequest.fromUrl("http://comeon.com/?queryString=nope"), response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");
  }
  @Test
  public void testQueryString() throws ConfigurationException {
    var action = new HttpRedirect();
    action.setTarget("http://blu");
    action.setIncludeQueryString(true);
    action.initialize(basicConfig);

    TestResponse response;

    action.run(TestRequest.fromUrl("http://comeon.com/"), response = new TestResponse(), null);
    assertEquals("http://blu", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");

    action.run(TestRequest.fromUrl("http://comeon.com/?queryString=nope"), response = new TestResponse(), null);
    assertEquals("http://blu?queryString=nope", response.getHeaders().get("Location"), "Header Location incorrect");
    assertEquals(311, response.getStatus(), "Status do not match");
  }
}
