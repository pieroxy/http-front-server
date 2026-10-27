package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.proxy.ReverseProxyImpl;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

import java.util.List;
import java.util.logging.Logger;

/**
 * This action will replay the http request to another backend host.
 * <br>A header <code>X-Forwarded-Proto</code> is added to the request, indicating the backend whether the
 * connection to HFS was secure (<code>https</code>) or not (<code>http</code>).
 * <br>The forwarding headers sent by the client (<code>X-Forwarded-For</code>, <code>X-Forwarded-Proto</code>,
 * <code>Forwarded</code>, <code>X-Real-IP</code>...) are removed, unless it is one of the
 * {@link Config#trustedProxies}: its <code>X-Forwarded-For</code> is then completed, and its
 * <code>X-Forwarded-Proto</code> and other forwarding headers are passed on.
 */
public class ReverseProxy implements RuleAction {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxy.class.getName());

  public static final long DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
  public static final long DEFAULT_READ_TIMEOUT_MS = 60_000;
  /** The wait for a free connection in the pool, when {@link #connectTimeoutMs} is disabled */
  public static final long DEFAULT_POOL_WAIT_TIMEOUT_MS = 60_000;

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
   * Determines if a <code>X-Forwarded-For</code> header is added with the IP address of the original request
   * (or completed, if the request comes from one of the {@link Config#trustedProxies}). If <b>false</b>, no
   * <code>X-Forwarded-For</code> is sent at all.
   * <br>Default value is <b>true</b>
   */
  protected boolean doForwardIP = true;

  /**
   * The time the reverse proxy waits for the connection to the backend to be established, and for a free
   * connection in the pool when all of them are busy. The client gets a 504 (connect) or a 503 (pool) when
   * it is reached.
   * <br>Default value is {@link #connectionTimeoutMs} if set, <b>10000</b> otherwise. <b>0</b> means no
   * timeout for the connection (the pool wait is then {@value #DEFAULT_POOL_WAIT_TIMEOUT_MS}ms).
   */
  protected Long connectTimeoutMs;

  /**
   * The longest time the backend may stay silent (no byte received) while the reverse proxy waits for, or reads,
   * its response. This is not a limit on the total duration: a response that keeps sending data, like a stream
   * of server-sent events, never reaches it. The client gets a 504 when it is reached before the response
   * started, the connection is aborted otherwise.
   * <br>Default value is {@link #connectionTimeoutMs} if set, <b>60000</b> otherwise. <b>0</b> means no timeout.
   */
  protected Long readTimeoutMs;

  /**
   * Older setting, used for both {@link #connectTimeoutMs} and {@link #readTimeoutMs} when they are not set.
   */
  protected Long connectionTimeoutMs;

  /**
   * If the backend host cannot be connected, this action can retry the request at a later time. This value indicate
   * how many retries should the action attempt before giving up. {@link #retriesEveryMs} must also be greater than
   * zero for this to have any effect.
   * <br>Default value is <b>0</b>, meaning an unreachable backend will lead to a 502 error.
   */
  protected int maxRetries = 0;
  /**
   * If the backend host cannot be connected, this action can retry the request at a later time. This value indicate
   * how much time must be waited between two retries. {@link #maxRetries} must also be greater than
   * zero for this to have any effect.
   * <br>Default value is <b>0</b>, meaning an unreachable backend will lead to a 502 error.
   */
  protected int retriesEveryMs = 0;

  /**
   *
   */
  protected boolean dumpAllRequests = false;

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
    checkTimeouts();
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

  private void checkTimeouts() throws ConfigurationException {
    for (Long timeout : new Long[] {connectTimeoutMs, readTimeoutMs, connectionTimeoutMs}) {
      if (timeout != null && timeout < 0) {
        throw new ConfigurationException("ReverseProxy action to " + target + ": timeouts cannot be negative");
      }
    }
    if (getConnectTimeoutMs() == 0) {
      LOGGER.warning("ReverseProxy action to " + target + ": no connect timeout, connecting to the backend may wait forever.");
    }
    if (getReadTimeoutMs() == 0) {
      LOGGER.warning("ReverseProxy action to " + target + ": no read timeout, a silent backend may hold requests forever.");
    }
  }

  /**
   * @return The effective connect timeout, see {@link #connectTimeoutMs}. 0 means no timeout.
   */
  public long getConnectTimeoutMs() {
    if (connectTimeoutMs != null) return connectTimeoutMs;
    if (connectionTimeoutMs != null) return connectionTimeoutMs;
    return DEFAULT_CONNECT_TIMEOUT_MS;
  }

  /**
   * @return The effective read timeout, see {@link #readTimeoutMs}. 0 means no timeout.
   */
  public long getReadTimeoutMs() {
    if (readTimeoutMs != null) return readTimeoutMs;
    if (connectionTimeoutMs != null) return connectionTimeoutMs;
    return DEFAULT_READ_TIMEOUT_MS;
  }

  /**
   * @return The time to wait for a free connection in the pool. Never 0, so that a saturated pool cannot hold
   * requests forever.
   */
  public long getPoolWaitTimeoutMs() {
    long connect = getConnectTimeoutMs();
    return connect > 0 ? connect : DEFAULT_POOL_WAIT_TIMEOUT_MS;
  }

  public void setConnectTimeoutMs(Long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public void setReadTimeoutMs(Long readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }

  public void setConnectionTimeoutMs(Long connectionTimeoutMs) {
    this.connectionTimeoutMs = connectionTimeoutMs;
  }

  public boolean isDumpAllRequests() {
    return dumpAllRequests;
  }

  public void setDumpAllRequests(boolean dumpAllRequests) {
    this.dumpAllRequests = dumpAllRequests;
  }
}
