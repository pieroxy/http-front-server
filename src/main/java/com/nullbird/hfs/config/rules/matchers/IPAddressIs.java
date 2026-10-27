package com.nullbird.hfs.config.rules.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This matcher will check the IP address from which the http request was launched for equality. When HFS is behind
 * one of the {@link com.nullbird.hfs.config.Config#trustedProxies}, this is the client address it forwarded in
 * <code>X-Forwarded-For</code>. Otherwise, <code>X-Forwarded-For</code> and other headers alike are ignored.
 */
public class IPAddressIs implements RuleMatcher {
  private final static Logger LOGGER = Logger.getLogger(IPAddressIs.class.getName());
  /**
   * The IP address in textual representation (eg: 192.168.1.2)
   */
  protected String ipAddress;

  @Override
  public boolean match(HttpRequest request) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Matching " + request.getClientAddr());
    return request.getClientAddr().equals(ipAddress);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (ipAddress==null) throw new ConfigurationException("IPAddressIs matcher definition must include a non null 'ipAddress' attribute");
  }

  @Override
  public void stop() {
  }
}
