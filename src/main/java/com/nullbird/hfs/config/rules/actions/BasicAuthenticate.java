package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This action protects the requests matched with a simple login/password authentication. IT is not meant to be
 * state-of-the-art security but adds a layer nevertheless. You can typically use this to protect
 * endpoints that are commonly targeted by hackers and bots, such as wordpress admin endpoints, or your test instance
 * that needs to be accessed through the Internet but you don't want your users to confuse it with the production
 * services.
 * <br>The authentication cookie contains the login, an expiry date and an HMAC signature computed with
 * {@link Config#hfsAuthSecret}, the cookie name and the user's password. Changing the password or removing the user
 * invalidates the cookies previously delivered to this user.
 */
public class BasicAuthenticate implements RuleAction {
  private static final Logger LOGGER = Logger.getLogger(BasicAuthenticate.class.getName());
  private static final Level DEBUG_LEVEL = Level.FINE;
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  public final String AUTH_LOGIN=this.getClass().getName() + ".login";
  public final String AUTH_PASSWORD=this.getClass().getName() + ".password";
  public final String AUTH_REDIRECT=this.getClass().getName() + ".target";

  /**
   * The file name for the credentials. The format is <a href="https://en.wikipedia.org/wiki/.properties">properties</a>
   * in the format <code>login=password</code>.
   * <br>This parameter or {@link #credentials} must be defined.
   */
  String credentialsFile;
  /**
   * If set to <b>true</b> the cookie used will have the <code>secure</code> flag.
   * <br>Defaults to <b>true</b>.
   */
  boolean secure = true;
  /**
   * The list of login/passwords to authenticate users.
   * <br>This parameter or {@link #credentialsFile} must be defined.
   */
  Map<String, String> credentials;
  /**
   * The name of the cookie to drop.
   * <br>Defaults to <b>com.nullbird.hfs.config.rules.actions.BasicAuthenticate</b>.
   */
  String cookieName = this.getClass().getName();
  /**
   * The duration of the authentication, in hours.
   * <br>Defaults to <b>365 days</b>.
   */
  int cookieDurationInHours = 24*365;

  private byte[] cookieKey;


  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    var cookieValues = request.getCookieValues(cookieName);
    if (cookieValues!=null && cookieValues.stream().anyMatch(this::isValidToken)) {
      if (LOGGER.isLoggable(DEBUG_LEVEL)) {
        LOGGER.log(DEBUG_LEVEL, "Letting request go through - " + request.getUrl());
      }
      return;
    }
    try {
      Map<String, String> postData = request.decodeSimpleXWWWFormUrlEncodedPostData();
      String login = postData.get(AUTH_LOGIN);
      String password = postData.get(AUTH_PASSWORD);
      String target = postData.get(AUTH_REDIRECT);

      if (LOGGER.isLoggable(Level.FINER)) {
        LOGGER.finer("login " + login);
        LOGGER.finer("password null " + (password == null));
        LOGGER.finer("target " + target);
        LOGGER.finer("method " + request.getMethod());
      }

      if (login != null && password != null && target != null && request.getMethod().equals("POST")) {
        if (LOGGER.isLoggable(DEBUG_LEVEL)) LOGGER.log(DEBUG_LEVEL, "Authenticating the request - " + request.getUrl());
        if (Objects.equals(credentials.get(login), password)) {
          if (LOGGER.isLoggable(DEBUG_LEVEL)) LOGGER.log(DEBUG_LEVEL, "Authentication successful - " + request.getUrl());
          // Authenticating the user
          authenticate(login, password, response);
          response.sendRedirect(HttpServletResponse.SC_FOUND, target);
        } else {
          if (LOGGER.isLoggable(Level.INFO)) LOGGER.info("Authentication failed - " + request.getUrl());
          failLogin(request, target, response);
        }
      } else {
        if (LOGGER.isLoggable(DEBUG_LEVEL)) LOGGER.log(DEBUG_LEVEL, "Request needs authentication - " + request.getUrl());
        showLoginPrompt(request, response);
      }
    } catch (Exception e) {
      // Whatever happens, the request must not go through unauthenticated.
      if (LOGGER.isLoggable(Level.INFO)) LOGGER.log(Level.INFO, "Request failed decoding and thus needs authentication - " + request.getUrl(), e);
      showLoginPrompt(request, response);
    }
  }

  private void failLogin(HttpRequest request, String target, HttpResponse response) {
    sendLoginForm(response, target, "Authentication failed");
  }

  private void showLoginPrompt(HttpRequest request, HttpResponse response) {
    sendLoginForm(response, request.getUrl(), "You need to authenticate to move forward.");
  }

  private void sendLoginForm(HttpResponse response, String redirect, String message) {
    String html = String.format("""
            <html>
              <head>
              <style>
              body {
                text-align: center;
              }
              
              form {
                border: 1px solid #eee;
                display: inline-block;
                margin-top: calc( 50vh - 7em );
                padding: 2em 1em;
                background-color: #f8f8f8;
                box-shadow: 3px 3px 10px rgba(0,0,0,0.1);
              }
              
              right {
                text-align:right;
                display: block;
              }
              
              input {
                margin-top:10px;
              }
              
              input[type=submit] {
                  padding: 3px 30px;
              }
              </style>
              </head>
              <body>
                <form method="post">
                  <center>%s</center>
                  <right>
                    <label>Login: <input type="text" name="%s"></label><br>
                    <label>Password: <input type="password" name="%s"></label><br>
                  </right>
                  <center>
                    <input type="hidden" name="%s" value="%s">
                    <input type="submit" value="OK">
                  </center>
                </form>
              </body>
            </html>
            """, message, AUTH_LOGIN, AUTH_PASSWORD, AUTH_REDIRECT, StringUtils.formatForHTML(redirect));
    response.respond(HttpServletResponse.SC_UNAUTHORIZED, ContentType.TEXT_HTML, html);
  }

  private void authenticate(String login, String password, HttpResponse response) {
    long expiry = System.currentTimeMillis() / 1000 + cookieDurationInHours * 3600L;
    var cookie = new Cookie(cookieName, buildToken(login, password, expiry));
    cookie.setSecure(secure);
    cookie.setMaxAge(cookieDurationInHours*60*60);
    cookie.setHttpOnly(true);
    response.addCookie(cookie);
  }

  /**
   * The token is <code>base64url(login).expiry.base64url(signature)</code>
   */
  String buildToken(String login, String password, long expiry) {
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(login.getBytes(StandardCharsets.UTF_8)) + "." + expiry;
    return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload, password));
  }

  private boolean isValidToken(String token) {
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 3) return false;
      long expiry = Long.parseLong(parts[1]);
      if (expiry < System.currentTimeMillis() / 1000) return false;
      String login = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      String password = credentials.get(login);
      if (password == null) return false;
      byte[] expected = sign(parts[0] + "." + parts[1], password);
      return MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]));
    } catch (Exception e) {
      if (LOGGER.isLoggable(DEBUG_LEVEL)) LOGGER.log(DEBUG_LEVEL, "Malformed authentication cookie", e);
      return false;
    }
  }

  private byte[] sign(String payload, String password) {
    return hmac(cookieKey, payload + "\n" + password);
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (config == null || !StringUtils.containsNonWhitespace(config.getHfsAuthSecret())) {
      throw new ConfigurationException("BasicAuthenticate requires a hfsAuthSecret in the configuration.");
    }
    // One key per cookie name, so that a cookie delivered for one cookie name is useless for another.
    cookieKey = hmac(config.getHfsAuthSecret().getBytes(StandardCharsets.UTF_8), "BasicAuthenticate.cookie:" + cookieName);
    if (credentials == null) credentials = new HashMap<>();
    if (StringUtils.containsNonWhitespace(credentialsFile)) {
      Properties properties = new Properties();
      try {
        properties.load(new FileReader(credentialsFile));
      } catch (IOException e) {
        throw new ConfigurationException("Cannot read from credentials file", e);
      }
      properties.entrySet().forEach(e-> credentials.put(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
    }
    if (credentials.isEmpty()) {
      throw new ConfigurationException("BasicAuthenticate do not have any credentials.");
    }
  }

  @Override
  public void stop() {
    credentials.clear();
    credentials = null;
  }

  public String getCredentialsFile() {
    return credentialsFile;
  }

  public void setCredentialsFile(String credentialsFile) {
    this.credentialsFile = credentialsFile;
  }

  public boolean isSecure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public Map<String, String> getCredentials() {
    return credentials;
  }

  public void setCredentials(Map<String, String> credentials) {
    this.credentials = credentials;
  }

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }

  public int getCookieDurationInHours() {
    return cookieDurationInHours;
  }

  public void setCookieDurationInHours(int cookieDurationInHours) {
    this.cookieDurationInHours = cookieDurationInHours;
  }
}
