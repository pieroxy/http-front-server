package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleAction;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class BasicAuthenticate implements RuleAction {
  public final String COOKIE_VALUE="OK";
  public final String AUTH_LOGIN=this.getClass().getName() + ".login";
  public final String AUTH_PASSWORD=this.getClass().getName() + ".password";
  public final String AUTH_REDIRECT=this.getClass().getName() + ".target";
  String credentialsFile;
  boolean secure = true;
  Map<String, String> credentials;
  String cookieName = this.getClass().getName();
  int cookieDurationInHours = 24*365;


  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    if (Objects.equals(request.getCookieValue(cookieName), COOKIE_VALUE)) return;
    String login = request.getParameter(AUTH_LOGIN);
    String password = request.getParameter(AUTH_PASSWORD);
    String target = request.getParameter(AUTH_REDIRECT);
    if (login!=null && password!=null && target!=null && request.getMethod().equals("POST")) {
      if (Objects.equals(credentials.get(login), password)) {
        // Authenticating the user
        authenticate(response);
        response.sendRedirect(HttpServletResponse.SC_FOUND, target);
      } else {
        failLogin(request, response);
      }
    } else {
      showLoginPrompt(request, response);
    }
  }

  private void failLogin(HttpRequest request, HttpResponse response) {
    sendLoginForm(response, request.getParameter(AUTH_REDIRECT), "Authentication failed");
  }

  private void showLoginPrompt(HttpRequest request, HttpResponse response) {
    sendLoginForm(response, request.getUrl(), "");
  }

  private void sendLoginForm(HttpResponse response, String redirect, String message) {
    String html = String.format("""
            <html>
              <head>
              </head>
              <body>
                <form>
                  <center>%s</center>
                  <label>Login:<input type="text" name="%s"></label>
                  <label>Password:<input type="password" name="%s"></label>
                  <input type="hidden" name="%s" value="%s">
                  <input type="submit" value="OK">
                </form>
              </body>
            </html>
            """, message, AUTH_LOGIN, AUTH_PASSWORD, AUTH_REDIRECT, redirect);
    response.respond(HttpServletResponse.SC_UNAUTHORIZED, ContentType.TEXT_HTML, html);
  }

  private void authenticate(HttpResponse response) {
    var cookie = new Cookie(cookieName, COOKIE_VALUE);
    cookie.setSecure(secure);
    cookie.setMaxAge(cookieDurationInHours*60*60);
    response.addCookie(cookie);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (credentials == null) credentials = new HashMap<>();
    if (StringUtils.containsNonWhitespace(credentialsFile)) {
      Properties properties = new Properties();
      try {
        properties.load(new FileReader(new File(credentialsFile)));
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
