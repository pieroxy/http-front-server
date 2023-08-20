package com.nullbird.hfs.config;

import com.nullbird.hfs.config.rules.Rule;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public class Config {

  /**
   * Default status for redirections (See {@link com.nullbird.hfs.config.rules.actions.HttpRedirect}). This status can be
   * overridden at the action level. If not specified, the default value is <b>302</b>
   */
  protected int defaultRedirectStatus = HttpServletResponse.SC_FOUND;

  /**
   * Port this server should listen to for http requests. If not specified, the default value is <b>8080</b>
   */
  protected int httpPort = 8080;

  /**
   * Port this server should listen to for https requests. If not specified, the default value is <b>8443</b>
   */
  protected int httpsPort = 8443;

  /**
   * Address the server should listen to. If not specified, the server will listen on all local addresses.
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat documentation</a>
   */
  protected String address;

  /**
   * The level at which all loggers will be set. Defaults to <b>INFO</b> if not specified.
   * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/Level.html">java.util.logging documentation</a>
   */
  protected String defaultLoggingLevel;

  /**
   * The level at which specific loggers will be set. Defaults to {@link Config#defaultLoggingLevel} if not specified.
   * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.logging/java/util/logging/Level.html">java.util.logging documentation</a>
   * For example:
   * <pre>
   *   <code>
   *     {
   *       "com.nullbird.hfs.config.rules.actions.HttpRedirect":"FINE",
   *       "com.nullbird.hfs.config.rules.actions.AddHttpHeader":"WARNING"
   *     }
   *   </code>
   * </pre>
   */
  protected Map<String, String> loggersLevel;

  /**
   * The rules that will be run for each http request.
   */
  protected List<Rule> rules;

  public List<Rule> getRules() {
    return rules;
  }

  public void setRules(List<Rule> rules) {
    this.rules = rules;
  }

  public int getDefaultRedirectStatus() {
    return defaultRedirectStatus;
  }

  public void setDefaultRedirectStatus(int defaultRedirectStatus) {
    this.defaultRedirectStatus = defaultRedirectStatus;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public int getHttpsPort() {
    return httpsPort;
  }

  public void setHttpsPort(int httpsPort) {
    this.httpsPort = httpsPort;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getDefaultLoggingLevel() {
    return defaultLoggingLevel;
  }

  public void setDefaultLoggingLevel(String defaultLoggingLevel) {
    this.defaultLoggingLevel = defaultLoggingLevel;
  }

  public Map<String, String> getLoggersLevel() {
    return loggersLevel;
  }

  public void setLoggersLevel(Map<String, String> loggersLevel) {
    this.loggersLevel = loggersLevel;
  }
}
