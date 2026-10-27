package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.errors.BodyTooLargeException;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import utils.TestRequest;
import utils.TestResponse;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BasicAuthenticateTest {
  private static Config buildConfig(String secret) {
    var config = new Config();
    config.setHfsAuthSecret(secret);
    return config;
  }

  private BasicAuthenticate buildAction() {
    return buildAction("secret", Map.of("login", "password"), null);
  }

  private BasicAuthenticate buildAction(String secret, Map<String, String> credentials, String cookieName) {
    var action = new BasicAuthenticate();
    action.setCredentials(new HashMap<>(credentials));
    if (cookieName != null) action.setCookieName(cookieName);
    try {
      action.initialize(buildConfig(secret));
    } catch (ConfigurationException e) {
      throw new RuntimeException(e);
    }
    return action;
  }

  private TestResponse runWithCookie(BasicAuthenticate action, String cookieName, String cookieValue) {
    var response = new TestResponse();
    var request = TestRequest.fromUrl("http://anything/");
    request.getCookieValues().put(cookieName, cookieValue);
    action.run(request, response, null);
    return response;
  }

  private long inOneHour() {
    return System.currentTimeMillis() / 1000 + 3600;
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
  public void unauthenticatedTestNoCookies() {
    var actiom = buildAction();

    var response = new TestResponse();
    var req = TestRequest.fromUrl("http://anything/");
    req.setCookieValues(null);
    actiom.run(req, response, null);
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
    String token = action.buildToken("login", "password", inOneHour());

    var response = runWithCookie(action, action.getCookieName(), token);
    assertEquals(0, response.getStatus());
    assertFalse(response.isConsumed());

    response = runWithCookie(action, action.getCookieName(), token + "2");
    assertEquals(401, response.getStatus());
    assertTrue(response.isConsumed());

    response = runWithCookie(action, action.getCookieName() + "2", token);
    assertEquals(401, response.getStatus());
    assertTrue(response.isConsumed());
  }

  @Test
  public void forgedCookiesAreRejectedTest() {
    var action = buildAction();
    String valid = action.buildToken("login", "password", inOneHour());
    String[] parts = valid.split("\\.");
    for (String forged : List.of(
        "OK",
        "",
        "...",
        parts[0] + "." + parts[1],
        parts[0] + "." + parts[1] + ".",
        parts[0] + "." + (Long.parseLong(parts[1]) + 1) + "." + parts[2],
        parts[0] + "." + parts[1] + ".!!notbase64!!",
        parts[0] + "." + "notanumber" + "." + parts[2])) {
      var response = runWithCookie(action, action.getCookieName(), forged);
      assertEquals(401, response.getStatus(), "Forged cookie accepted: " + forged);
    }
  }

  @Test
  public void expiredCookieTest() {
    var action = buildAction();
    String token = action.buildToken("login", "password", System.currentTimeMillis() / 1000 - 1);
    assertEquals(401, runWithCookie(action, action.getCookieName(), token).getStatus());
  }

  @Test
  public void unknownUserTest() {
    var action = buildAction();
    String token = action.buildToken("someoneElse", "password", inOneHour());
    assertEquals(401, runWithCookie(action, action.getCookieName(), token).getStatus());
  }

  @Test
  public void passwordChangeInvalidatesCookieTest() {
    var before = buildAction("secret", Map.of("login", "password"), null);
    String token = before.buildToken("login", "password", inOneHour());
    var after = buildAction("secret", Map.of("login", "newPassword"), null);
    assertEquals(401, runWithCookie(after, after.getCookieName(), token).getStatus());
  }

  @Test
  public void secretChangeInvalidatesCookieTest() {
    var before = buildAction("secret", Map.of("login", "password"), null);
    String token = before.buildToken("login", "password", inOneHour());
    var after = buildAction("otherSecret", Map.of("login", "password"), null);
    assertEquals(401, runWithCookie(after, after.getCookieName(), token).getStatus());
  }

  @Test
  public void cookieNameScopesTheTokenTest() {
    var zoneA = buildAction("secret", Map.of("login", "password"), "zoneA");
    var zoneB = buildAction("secret", Map.of("login", "password"), "zoneB");
    String tokenA = zoneA.buildToken("login", "password", inOneHour());
    assertEquals(0, runWithCookie(zoneA, "zoneA", tokenA).getStatus());
    assertEquals(401, runWithCookie(zoneB, "zoneB", tokenA).getStatus());
  }

  @Test
  public void failingBodyDecodingShowsLoginPromptTest() {
    var action = buildAction();
    var request = new TestRequest() {
      @Override
      public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData(int maxBytes) throws IOException {
        throw new RuntimeException("Simulated decoding failure");
      }
    };
    request.setUrl("http://anything/");
    request.setMethod("POST");
    request.setHeaders(new HashMap<>(Map.of(HttpHeaders.CONTENT_TYPE, List.of("application/x-www-form-urlencoded"))));
    request.setCookieValues(new HashMap<>());
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(401, response.getStatus());
    assertTrue(response.isConsumed());
  }
  @Test
  public void authenticateTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    var request = TestRequest.fromUrl("http://anything/protected/page?a=1&b=2");
    request.setMethod("POST");
    request.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of("application/x-www-form-urlencoded"));
    request.getPostData().put(action.AUTH_LOGIN, "user");
    request.getPostData().put(action.AUTH_PASSWORD, "password");
    // Older forms had this field: it must be ignored
    request.getPostData().put(BasicAuthenticate.class.getName() + ".target", "https://evil.example/");
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
    assertNotNull(response.getCookie(action.cookieName));
    assertEquals("/protected/page?a=1&b=2", response.getHeaders().get(HttpHeaders.LOCATION), "Back to the page, relative");

    // The delivered cookie must authenticate the next request
    var next = runWithCookie(action, action.cookieName, response.getCookie(action.cookieName).getValue());
    assertEquals(0, next.getStatus());
    assertFalse(next.isConsumed());
  }

  @Test
  public void authenticateFailureTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    var request = TestRequest.fromUrl("http://anything/");
    request.setMethod("POST");
    request.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of("application/x-www-form-urlencoded"));
    request.getPostData().put(action.AUTH_LOGIN, "user");
    request.getPostData().put(action.AUTH_PASSWORD, "password2");
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    assertNull(response.getCookie(action.cookieName));
    String body = new String(response.getBody(), StandardCharsets.UTF_8);
    assertTrue(body.length() > 0);
    assertTrue(body.startsWith("<html>"));
    assertTrue(body.contains("<input "));
  }

  /**
   * A request whose body must never be read, and which holds valid credentials if it were.
   */
  private TestRequest untouchableBody(BasicAuthenticate action, String method, String contentType) {
    var request = new TestRequest() {
      @Override
      public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData(int maxBytes) {
        throw new AssertionError("The body must not be read");
      }
    };
    request.setUrl("http://anything/");
    request.setMethod(method);
    request.setCookieValues(new HashMap<>());
    request.setHeaders(new HashMap<>());
    if (contentType != null) request.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of(contentType));
    return request;
  }

  @Test
  public void onlyFormPostsAreReadTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    for (var request : List.of(
        untouchableBody(action, "GET", "application/x-www-form-urlencoded"),
        untouchableBody(action, "PUT", "application/x-www-form-urlencoded"),
        untouchableBody(action, "POST", null),
        untouchableBody(action, "POST", "application/json"),
        untouchableBody(action, "POST", "multipart/form-data; boundary=x"))) {
      var response = new TestResponse();
      action.run(request, response, null);
      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(), request.getMethod() + " " + request.getHeader(HttpHeaders.CONTENT_TYPE));
      assertNull(response.getCookie(action.cookieName));
    }
  }

  @Test
  public void formContentTypeWithParametersIsAcceptedTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    var request = TestRequest.fromUrl("http://anything/");
    request.setMethod("POST");
    request.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of(" Application/X-WWW-Form-Urlencoded ; charset=UTF-8"));
    request.getPostData().put(action.AUTH_LOGIN, "user");
    request.getPostData().put(action.AUTH_PASSWORD, "password");
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
  }

  @Test
  public void tooLargeBodyShowsLoginPromptTest() {
    var action = buildAction();
    var limits = new ArrayList<Integer>();
    var request = new TestRequest() {
      @Override
      public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData(int maxBytes) throws IOException {
        limits.add(maxBytes);
        throw new BodyTooLargeException("Simulated");
      }
    };
    request.setUrl("http://anything/");
    request.setMethod("POST");
    request.setCookieValues(new HashMap<>());
    request.setHeaders(new HashMap<>(Map.of(HttpHeaders.CONTENT_TYPE, List.of("application/x-www-form-urlencoded"))));
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    assertEquals(List.of(BasicAuthenticate.MAX_LOGIN_BODY_BYTES), limits);
  }

  private TestResponse login(BasicAuthenticate action, String clientAddr, String password) {
    var request = TestRequest.fromUrl("http://anything/");
    request.setRemoteAddr(clientAddr);
    request.setMethod("POST");
    request.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of("application/x-www-form-urlencoded"));
    request.getPostData().put(action.AUTH_LOGIN, "user");
    request.getPostData().put(action.AUTH_PASSWORD, password);
    var response = new TestResponse();
    action.run(request, response, null);
    return response;
  }

  @Test
  public void failedLoginsLockTheClientOutTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS; i++) {
      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, login(action, "1.2.3.4", "wrong").getStatus(), "Attempt " + i);
    }
    // Even the right password is refused now, without being checked
    var locked = login(action, "1.2.3.4", "password");
    assertEquals(429, locked.getStatus());
    assertNull(locked.getCookie(action.cookieName));
    long retryAfter = Long.parseLong(locked.getHeaders().get(HttpHeaders.RETRY_AFTER));
    assertTrue(retryAfter > 0 && retryAfter <= BasicAuthenticate.FAILED_LOGIN_WINDOW_MS / 1000, "Retry-After: " + retryAfter);

    // Other clients are not affected
    assertEquals(HttpServletResponse.SC_FOUND, login(action, "1.2.3.5", "password").getStatus());
  }

  @Test
  public void successfulLoginResetsTheCountTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS - 1; i++) login(action, "1.2.3.4", "wrong");
    assertEquals(HttpServletResponse.SC_FOUND, login(action, "1.2.3.4", "password").getStatus());
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS - 1; i++) {
      assertEquals(HttpServletResponse.SC_UNAUTHORIZED, login(action, "1.2.3.4", "wrong").getStatus());
    }
  }

  @Test
  public void lockedClientBodyIsNotReadTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS; i++) login(action, "1.2.3.4", "wrong");
    var request = untouchableBody(action, "POST", "application/x-www-form-urlencoded");
    request.setRemoteAddr("1.2.3.4");
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(429, response.getStatus());
  }

  @Test
  public void onlyLoginAttemptsAreLimitedTest() {
    var action = buildAction("secret", Map.of("user", "password"), null);
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS; i++) login(action, "1.2.3.4", "wrong");
    var request = TestRequest.fromUrl("http://anything/");
    request.setRemoteAddr("1.2.3.4");
    var response = new TestResponse();
    action.run(request, response, null);
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(), "The login form is still shown");
  }

  @Test
  public void ipv6ClientsAreCountedBy64Test() {
    assertEquals("2001:db8:1:2::/64", BasicAuthenticate.getRateLimiterKey("2001:db8:1:2:aaaa::1"));
    assertEquals("2001:db8:1:2::/64", BasicAuthenticate.getRateLimiterKey("2001:0DB8:0001:0002:ffff:ffff:ffff:ffff"));
    assertEquals("0:0:0:0::/64", BasicAuthenticate.getRateLimiterKey("::1"));
    assertEquals("1.2.3.4", BasicAuthenticate.getRateLimiterKey("1.2.3.4"));
    assertEquals("1.2.3.4", BasicAuthenticate.getRateLimiterKey("::ffff:1.2.3.4"), "IPv4-mapped is IPv4");

    var action = buildAction("secret", Map.of("user", "password"), null);
    for (int i = 0; i < BasicAuthenticate.MAX_FAILED_LOGINS; i++) login(action, "2001:db8:1:2::" + (i + 1), "wrong");
    assertEquals(429, login(action, "2001:db8:1:2::ff", "password").getStatus(), "Same /64");
    assertEquals(HttpServletResponse.SC_FOUND, login(action, "2001:db8:1:3::1", "password").getStatus(), "Other /64");
  }

  @Test
  public void badConfigTest() throws ConfigurationException {
    var action = new BasicAuthenticate();
    action.setCredentials(new HashMap<>());
    assertThrows(ConfigurationException.class,()->action.initialize(buildConfig("secret")));
  }

  @Test
  public void missingSecretTest() {
    var action = new BasicAuthenticate();
    action.setCredentials(new HashMap<>(Map.of("login", "password")));
    assertThrows(ConfigurationException.class,()->action.initialize(null));
    assertThrows(ConfigurationException.class,()->action.initialize(buildConfig(null)));
    assertThrows(ConfigurationException.class,()->action.initialize(buildConfig("  ")));
  }

  // TODO The credentials file test
}
