package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Matches the HTTP header <b>Host</b> value to a list of hosts. This is an exact match, case-insensitive.
 */
public class HostIsIn implements RuleMatcher {

  /**
   * The hosts this matcher is supposed to match. Note that the match is case-insensitive.
   * <br>This parameter is mandatory.
   */
  protected Set<String> hosts;

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
    if (value==null) value = "";

    if (excludePort && value.contains(":")) {
      value = value.substring(0, value.indexOf(":"));
    }

    return hosts.contains(value.toLowerCase(Locale.ROOT));
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (hosts==null || hosts.isEmpty()) throw new ConfigurationException("HostIs matcher definition must include a non empty 'hosts' attribute");
    Collection<String> allHosts = new ArrayList<>(hosts);
    hosts.clear();
    allHosts.forEach(h->hosts.add(h.toLowerCase(Locale.ROOT)));
  }

  @Override
  public void stop() {
  }

  /**
   * See {@link #hosts}
   */
  public Set<String> getHost() {
    return hosts;
  }

  /**
   * See {@link #hosts}
   */
  public void setHost(Set<String> hosts) {
    this.hosts = hosts;
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
