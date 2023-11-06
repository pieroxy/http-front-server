package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * Matches the HTTP header <b>Host</b> value. This is an exact match, case-insensitive.
 */
public class HostIs implements RuleMatcher {

  /**
   * The host this matcher is supposed to match. Note that the match is case-insensitive.
   * <br>This parameter is mandatory.
   */
  protected String host;

  /**
   * Ignore the port part in the header value, if present.
   * <br>The <b>Host</b> http header will contain the port if specified by the url (ex: <i>https://pieroxy.net:443/</i> will yield
   * a <b>Host</b> value of <i>pieroxy.net:80</i>).
   * If this property is set to <b>true</b>, this part will be ignored, ie, <i>pieroxy.net:80</i> will be
   * considered as <i>pieroxy.net</i>.
   * <br>The default value is <b>true</b>.
   */
  protected boolean excludePort = true;

  @Override
  public boolean match(HttpRequest request) {
    String value = request.getHeader("Host");
    if (value == null) value = "";

    if (excludePort && value.contains(":")) {
      value = value.substring(0, value.indexOf(":"));
    }

    return value.equalsIgnoreCase(host);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (host==null) throw new ConfigurationException("HostIs matcher definition must include a non null 'host' attribute");
  }

  @Override
  public void stop() {
  }

  /**
   * See {@link #host}
   */
  public String getHost() {
    return host;
  }

  /**
   * See {@link #host}
   */
  public void setHost(String host) {
    this.host = host;
  }

  /**
   * See {@link #excludePort}
   */
  public boolean isExcludePort() {
    return excludePort;
  }

  /**
   * See {@link #excludePort}
   */
  public void setExcludePort(boolean excludePort) {
    this.excludePort = excludePort;
  }
}
