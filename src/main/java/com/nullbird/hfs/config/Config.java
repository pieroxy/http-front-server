package com.nullbird.hfs.config;

import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.utils.IpRange;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  /**
   * The IP addresses or CIDR ranges (<code>10.0.0.0/8</code>, <code>2001:db8::/32</code>) of the proxies HFS sits
   * behind, if any (a load balancer, a CDN...). Only for requests coming from them are the forwarding headers sent by
   * the client (<code>X-Forwarded-For</code>, <code>X-Forwarded-Proto</code>, <code>Forwarded</code>,
   * <code>X-Real-IP</code>...) passed on to the backends: they are removed for any other client.
   * <br>Defaults to none.
   */
  protected List<String> trustedProxies;
  private transient List<IpRange> trustedProxyRanges = List.of();
  private transient Set<String> authCookieNames = Set.of();

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

  public List<String> getTrustedProxies() {
    return trustedProxies;
  }

  /**
   * {@link #parseTrustedProxies()} must be called afterwards.
   */
  public void setTrustedProxies(List<String> trustedProxies) {
    this.trustedProxies = trustedProxies;
  }

  /**
   * Validates {@link #trustedProxies}, which {@link #isTrustedProxy} relies on.
   */
  public void parseTrustedProxies() throws ConfigurationException {
    var ranges = new ArrayList<IpRange>();
    if (trustedProxies != null) {
      for (String value : trustedProxies) {
        try {
          ranges.add(IpRange.parse(value));
        } catch (IllegalArgumentException e) {
          throw new ConfigurationException("trustedProxies: " + e.getMessage());
        }
      }
    }
    trustedProxyRanges = List.copyOf(ranges);
  }

  public boolean isTrustedProxy(String remoteAddr) {
    for (IpRange range : trustedProxyRanges) {
      if (range.contains(remoteAddr)) return true;
    }
    return false;
  }

  /**
   * The address of the client, as opposed to the one of a trusted proxy in front of HFS: when the peer is a trusted
   * proxy, <code>X-Forwarded-For</code> is walked from the right, skipping trusted proxies.
   * @param remoteAddr The address of the peer.
   * @param forwardedFor The values of the <code>X-Forwarded-For</code> headers, in order.
   * @return The first address that is not a trusted proxy. If an entry is not an IP address, the address of the
   * trusted proxy that added it. If all are trusted proxies, the leftmost one.
   */
  public String resolveClientAddr(String remoteAddr, List<String> forwardedFor) {
    String client = remoteAddr;
    if (!isTrustedProxy(client)) return client;
    List<String> entries = new ArrayList<>();
    for (String value : forwardedFor) {
      for (String entry : value.split(",")) {
        if (!entry.isBlank()) entries.add(entry.trim());
      }
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      String entry = entries.get(i);
      if (IpRange.parseAddress(entry) == null) return client;
      client = entry;
      if (!isTrustedProxy(client)) return client;
    }
    return client;
  }

  /**
   * @return The names of the cookies delivered by the {@link com.nullbird.hfs.config.rules.actions.BasicAuthenticate}
   * actions, which must not reach the backends.
   */
  public Set<String> getAuthCookieNames() {
    return authCookieNames;
  }

  public void setAuthCookieNames(Set<String> authCookieNames) {
    this.authCookieNames = Set.copyOf(authCookieNames);
  }

  public TomcatConfig getTomcatConfig() {
    return tomcatConfig;
  }

  public void setTomcatConfig(TomcatConfig tomcatConfig) {
    this.tomcatConfig = tomcatConfig;
  }
}
