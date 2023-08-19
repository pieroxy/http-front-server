package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import utils.TestRequest;
import utils.TestResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BasicAuthenticateTest {
  private BasicAuthenticate buildAction() {
    var action = new BasicAuthenticate();
    action.setCredentials(new HashMap<>());
    action.getCredentials().put("login", "password");
    return action;
  }
  @Test
  public void unauthenticatedTest() {
    var actiom = buildAction();

    var response = new TestResponse();
    actiom.run(TestRequest.fromUrl("http://anything/"), response, null);
    assertEquals(401, response.getStatus());
    assertNotNull(response.getBody());
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertTrue(body.length() > 0);
    assertTrue(body.startsWith("<html>"));
    assertTrue(body.contains("<input "));
  }

  @Test
  public void authenticatedTest() {
    var action = buildAction();
    var response = new TestResponse();
    var request = TestRequest.fromUrl("http://anything/");
    request.getCookieValues().put(action.getCookieName(), action.COOKIE_VALUE);
    action.run(request, response, null);
    assertEquals(0, response.getStatus());
    assertFalse(response.isConsumed());

    request.getCookieValues().clear();
    request.getCookieValues().put(action.getCookieName(), action.COOKIE_VALUE + "2");
    action.run(request, response, null);
    assertEquals(401, response.getStatus());
    assertTrue(response.isConsumed());

    request.getCookieValues().clear();
    request.getCookieValues().put(action.getCookieName()+"2", action.COOKIE_VALUE);
    action.run(request, response, null);
    assertEquals(401, response.getStatus());
    assertTrue(response.isConsumed());
  }
  @Test
  public void authenticateTest() {
    var action = buildAction();
    action.setCredentials(Map.of("user", "password"));
    var request = TestRequest.fromUrl("http://anything/");
    request.setMethod("POST");
    request.getParameters().put(action.AUTH_LOGIN, List.of("user"));
    request.getParameters().put(action.AUTH_PASSWORD, List.of("password"));
    request.getParameters().put(action.AUTH_REDIRECT, List.of("http://success"));
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
    assertNotNull(response.getCookie(action.cookieName));
    assertEquals(action.COOKIE_VALUE, response.getCookie(action.cookieName).getValue());
    assertEquals("http://success", response.getHeaders().get(HttpHeaders.LOCATION));
  }

  @Test
  public void authenticateFailureTest() {
    var action = buildAction();
    action.setCredentials(Map.of("user", "password"));
    var request = TestRequest.fromUrl("http://anything/");
    request.setMethod("POST");
    request.getParameters().put(action.AUTH_LOGIN, List.of("user"));
    request.getParameters().put(action.AUTH_PASSWORD, List.of("password2"));
    request.getParameters().put(action.AUTH_REDIRECT, List.of("http://success"));
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    assertNull(response.getCookie(action.cookieName));
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertTrue(body.length() > 0);
    assertTrue(body.startsWith("<html>"));
    assertTrue(body.contains("<input "));
  }

  @Test
  public void badConfigTest() throws ConfigurationException {
    var action = new BasicAuthenticate();
    action.setCredentials(new HashMap<>());
    assertThrows(ConfigurationException.class,()->action.initialize(null));
  }

  // TODO The credentials file test
}
