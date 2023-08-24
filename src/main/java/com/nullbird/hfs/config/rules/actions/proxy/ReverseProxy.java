package com.nullbird.hfs.config.rules.actions.proxy;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleAction;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import com.nullbird.hfs.utils.errors.ProxyException;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.io.CloseMode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReverseProxy implements RuleAction {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxy.class.getSimpleName());

  private String target;
  private boolean doForwardIP = true;
  private boolean doSendUrlFragment = true;
  private int connectTimeout = -1;
  private int readTimeout = -1;
  private int connectionRequestTimeout = -1;
  private int maxConnections = -1;


  private transient HttpHost proxyHost;
  private transient CloseableHttpAsyncClient __proxyClient;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws ProxyException {
    try {
      AsyncRequestBuilder proxyRequestBuilder = buildProxyRequest(request);

      if (request.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
              request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
        proxyRequestBuilder.setEntity(new ReverseProxyEntityProducer(null, request.getBodyStream()));
      }

      copyRequestHeaders(request, proxyRequestBuilder);

      setXForwardedHeaders(request, proxyRequestBuilder);

      // Execute the request
      doExecute(request, response, proxyRequestBuilder);
    } catch (ProxyException e) {
      throw e;
    } catch (Exception e) {
      throw new ProxyException("Proxifying " + request.getUrl(), e);
    } finally {
      response.consume();
    }
  }

  private void doExecute(HttpRequest request, HttpResponse response, AsyncRequestBuilder proxyRequestBuilder) throws ProxyException {
    var proxyRequest = proxyRequestBuilder.build();
    String debugString = request.getUrl() + " >> " + proxyHost.toString();
    if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Executing request " + debugString);
    try {
      HttpResponse asyncResponse = request.getAsyncResponse(response);
      tryExecute(new ReverseProxyHttpRequest(
              proxyRequest,
              new ReverseProxyResponseConsumer(asyncResponse, debugString),
              this, asyncResponse));
    } catch (Exception e) {
      throw new ProxyException(e);
    }
  }

  private void tryExecute(ReverseProxyHttpRequest request) {
    final Future<Void> future = this.__proxyClient.execute(
            request.getRequestProducer(),
            request.getResponseConsumer(),
            null);
     request.getAsyncResponse().setFuture(future);
  }

  private void setXForwardedHeaders(HttpRequest request, AsyncRequestBuilder proxyRequestBuilder) {
    if (doForwardIP) {
      String forHeaderName = "X-Forwarded-For";
      String forHeader = request.getRemoteAddr();
      String existingForHeader = request.getHeader(forHeaderName);
      if (existingForHeader != null) {
        forHeader = existingForHeader + ", " + forHeader;
      }
      addHeaderImpl(forHeaderName, forHeader, proxyRequestBuilder);

    }
    String protoHeaderName = "X-Forwarded-Proto";
    String protoHeader = request.getScheme();
    addHeaderImpl(protoHeaderName, protoHeader, proxyRequestBuilder);
  }

  private void copyRequestHeaders(HttpRequest request, AsyncRequestBuilder proxyRequestBuilder) {
    // Get an Enumeration of all of the header names sent by the client
    Enumeration<String> enumerationOfHeaderNames = request.getHeaderNames();
    while (enumerationOfHeaderNames.hasMoreElements()) {
      String headerName = enumerationOfHeaderNames.nextElement();
      copyRequestHeader(request, proxyRequestBuilder, headerName);
    }
  }

  private void addHeaderImpl(String name, String value, AsyncRequestBuilder addTo) {
    LOGGER.finer("Header added: " + name + ": " + value);
    addTo.addHeader(name, value);
  }

  /** These are the "hop-by-hop" headers that should not be copied.
   * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
   * I use an HttpClient HeaderGroup class instead of Set&lt;String&gt; because this
   * approach does case insensitive lookup faster.
   */
  protected static final HeaderGroup hopByHopHeaders;
  static {
    hopByHopHeaders = new HeaderGroup();
    String[] headers = new String[] {
            "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
            "TE", "Trailers", "Transfer-Encoding", "Upgrade" };
    for (String header : headers) {
      hopByHopHeaders.addHeader(new BasicHeader(header, null));
    }
  }

  /**
   * Copy request headers from the servlet client to the proxy request.
   */
  protected void copyRequestHeader(HttpRequest servletRequest, AsyncRequestBuilder proxyRequest,
                                   String headerName) {
    if (hopByHopHeaders.containsHeader(headerName))
      return;
    // TODO This should be handled more efficiently
    // The EntityProducer seems to handle this.
    if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) return;

    Enumeration<String> headers = servletRequest.getHeaders(headerName);
    while (headers.hasMoreElements()) { //sometimes more than one value
      String headerValue = headers.nextElement();
      addHeaderImpl(headerName, headerValue, proxyRequest);
    }
  }

  private AsyncRequestBuilder buildProxyRequest(HttpRequest request) throws ProxyException {
    return AsyncRequestBuilder.create(request.getMethod())
            .setHttpHost(proxyHost)
            .setPath(request.getPath());
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (target==null) throw new ConfigurationException("ReverseProxy action must have a non null 'target' attribute");
    try {
      URL targetURL = new URL(target);
      if (StringUtils.containsNonWhitespace(targetURL.getFile()) ||
              targetURL.getRef()!=null ||
              StringUtils.containsNonWhitespace(targetURL.getPath()) ||
              targetURL.getQuery()!=null) {
        throw new ConfigurationException("ReverseProxy action target must only contain protocol, host and eventually port");
      }
      proxyHost = new HttpHost(targetURL.getProtocol(), targetURL.getHost(), targetURL.getPort());
      __proxyClient = createAndStartHttpClient();
    } catch (MalformedURLException e) {
      throw new ConfigurationException("Unable to parse provided target", e);
    }
  }

  protected CloseableHttpAsyncClient createAndStartHttpClient() {
    // TODO: Set timeouts and other configuration
    PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
            .setMaxConnPerRoute(500)
            .setMaxConnTotal(500)
            .build();
    var client = HttpAsyncClients.custom()
            .setConnectionManager(connectionManager)
            .disableRedirectHandling()
            .build();
    client.start();

    if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("New client created " + client);
    return client;
  }

  @Override
  public void stop() {
    LOGGER.info("Stopping http client");
    __proxyClient.close(CloseMode.GRACEFUL);
  }


  public String getTarget() {
    return target;
  }

  public void setTarget(String targetHost) {
    this.target = targetHost;
  }

  public boolean isDoForwardIP() {
    return doForwardIP;
  }

  public void setDoForwardIP(boolean doForwardIP) {
    this.doForwardIP = doForwardIP;
  }

  public boolean isDoSendUrlFragment() {
    return doSendUrlFragment;
  }

  public void setDoSendUrlFragment(boolean doSendUrlFragment) {
    this.doSendUrlFragment = doSendUrlFragment;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
  }

  public int getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  public void setConnectionRequestTimeout(int connectionRequestTimeout) {
    this.connectionRequestTimeout = connectionRequestTimeout;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }


}

