package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.proxy.ReverseProxyImpl;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.List;

/**
 * This action will replay the http request to another backend host.
 * <br>A header <code>X-Forwarded-Proto</code> is added to the request, indicating the backend whether the
 * connection to HFS was secure (<code>https</code>) or not (<code>http</code>).
 */
public class ReverseProxy implements RuleAction {
  /**
   * This "URL" represents the http backend host this action should forward requests to. It looks like an URL but
   * may only contain a protocol, host and port.
   * <br>Example of valid values: <code>http://1.2.3.4</code>, <code>https://google.com</code>,
   * <code>http://localhost:8080</code>
   * <br>Example of incorrect values: <code>http://1.2.3.4/</code>, <code>https://google.com/?q=32</code>,
   * <code>http://localhost:8080/index.html</code>
   * <br>This attribute is mandatory.
   */
  protected String target;

  /**
   * Determines if a <code>X-Forwarded-For</code> header is added (or completed) with the IP address
   * of the original request.
   * <br>Default value is <b>true</b>
   */
  protected boolean doForwardIP = true;

  /**
   * Determines the time the reverse proxy will wait for the backend to respond. A timeout error will be
   * thrown if the timeout is reached and a http error 500 will be produced.
   * <br>Default value is <b>0</b>, meaning no timeout.
   */
  protected long connectionTimeoutMs = 0;

  /**
   * If the backend host cannot be connected, this action can retry the request at a later time. This value indicate
   * how many retries should the action attempt before giving up. {@link #retriesEveryMs} must also be greater than
   * zero for this to have any effect.
   * <br>Default value is <b>0</b>, meaning an unreachable backend will lead to a 500 error.
   */
  protected int maxRetries = 0;
  /**
   * If the backend host cannot be connected, this action can retry the request at a later time. This value indicate
   * how much time must be waited between two retries. {@link #maxRetries} must also be greater than
   * zero for this to have any effect.
   * <br>Default value is <b>0</b>, meaning an unreachable backend will lead to a 500 error.
   */
  protected int retriesEveryMs = 0;

  /**
   * All http header names in this property will be wiped from the response.
   */
  protected List<String> ignoreResponseHeaders;

  private ReverseProxyImpl proxy;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
    proxy.run(request, response);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    proxy = new ReverseProxyImpl(this);
    proxy.initialize(config);
  }

  @Override
  public void stop() {
    proxy.stop();
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public boolean isDoForwardIP() {
    return doForwardIP;
  }

  public void setDoForwardIP(boolean doForwardIP) {
    this.doForwardIP = doForwardIP;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public int getRetriesEveryMs() {
    return retriesEveryMs;
  }

  public void setRetriesEveryMs(int retriesEveryMs) {
    this.retriesEveryMs = retriesEveryMs;
  }

  public List<String> getIgnoreResponseHeaders() {
    return ignoreResponseHeaders;
  }

  public void setIgnoreResponseHeaders(List<String> ignoreResponseHeaders) {
    this.ignoreResponseHeaders = ignoreResponseHeaders;
  }

  public long getConnectionTimeoutMs() {
    return connectionTimeoutMs;
  }
}
