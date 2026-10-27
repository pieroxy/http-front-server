package com.nullbird.hfs.config;

import com.nullbird.hfs.config.rules.Rule;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * This class represents the configuration of the http front server.
 */
public class Config {

  /**
   * Default status for redirections (See {@link com.nullbird.hfs.config.rules.actions.HttpRedirect}). This status can be
   * overridden at the action level. If not specified, the default value is <b>302</b>
   */
  protected int defaultRedirectStatus = HttpServletResponse.SC_FOUND;

  /**
   * The configuration related to the http server.
   */
  protected TomcatConfig tomcatConfig = new TomcatConfig();

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

  /**
   * The secret used to sign the authentication cookies of {@link com.nullbird.hfs.config.rules.actions.BasicAuthenticate}.
   * <br>If the configuration contains at least one <code>BasicAuthenticate</code> action and this entry is missing,
   * a random secret is generated at startup and <b>written back into the configuration file</b>.
   * <br>Changing this value invalidates all authentication cookies.
   */
  protected String hfsAuthSecret;

  /**
   * What to do with path parameters (<code>/admin;x</code>): <code>reject</code> answers with a 400,
   * <code>strip</code> removes them (<code>/admin;x/a</code> becomes <code>/admin/a</code>) for the matchers as
   * well as for the backends, <code>keep</code> leaves them as-is.
   * <br>Many backends (Tomcat, Jetty, Spring...) ignore path parameters when routing, while the matchers do not:
   * with <code>keep</code>, <code>/admin;x</code> does not match a rule on <code>/admin</code> but is served as
   * <code>/admin</code> by such backends.
   * <br>Defaults to <b>reject</b>. An encoded semicolon (<code>%3B</code>) is not a path parameter and is always
   * accepted.
   */
  protected PathParametersHandling pathParameters = PathParametersHandling.REJECT;

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

  public String getHfsAuthSecret() {
    return hfsAuthSecret;
  }

  public void setHfsAuthSecret(String hfsAuthSecret) {
    this.hfsAuthSecret = hfsAuthSecret;
  }

  /**
   * @return null if the configuration file holds an unknown value.
   */
  public PathParametersHandling getPathParameters() {
    return pathParameters;
  }

  public void setPathParameters(PathParametersHandling pathParameters) {
    this.pathParameters = pathParameters;
  }

  public TomcatConfig getTomcatConfig() {
    return tomcatConfig;
  }

  public void setTomcatConfig(TomcatConfig tomcatConfig) {
    this.tomcatConfig = tomcatConfig;
  }
}
