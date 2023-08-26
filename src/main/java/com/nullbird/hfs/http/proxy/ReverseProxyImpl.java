package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.actions.ReverseProxy;
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

public class ReverseProxyImpl  {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyImpl.class.getSimpleName());

  private ReverseProxy conf;

  private transient HttpHost proxyHost;
  private transient CloseableHttpAsyncClient __proxyClient;

  public ReverseProxyImpl(ReverseProxy conf) {
    this.conf = conf;
  }

  public void run(HttpRequest request, HttpResponse response) throws ProxyException {
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
    if (LOGGER.isLoggable(Level.FINE)) {
      String debugString = request.getUrl() + " >> " + proxyHost.toString();
      LOGGER.fine("Executing request " + debugString);
    }
    try {
      HttpResponse asyncResponse = request.getAsyncResponse(response);
      new ReverseProxyHttpRequest(
              proxyRequest,
              new ReverseProxyResponseConsumer(asyncResponse),
              this, asyncResponse, request.getUrl(), proxyHost.toString()).run();
    } catch (Exception e) {
      throw new ProxyException(e);
    }
  }

  void tryExecute(ReverseProxyHttpRequest request) {
    final Future<Void> future = this.__proxyClient.execute(
            request.getRequestProducer(),
            request.getResponseConsumer(),
            null);
     request.getAsyncResponse().setFuture(future);
  }

  private void setXForwardedHeaders(HttpRequest request, AsyncRequestBuilder proxyRequestBuilder) {
    if (conf.isDoForwardIP()) {
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

  public void initialize(Config config) throws ConfigurationException {
    if (conf.getTarget()==null) throw new ConfigurationException("ReverseProxy action must have a non null 'target' attribute");
    try {
      URL targetURL = new URL(conf.getTarget());
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

  public void stop() {
    LOGGER.info("Stopping http client");
    __proxyClient.close(CloseMode.GRACEFUL);
  }

  public ReverseProxy getConf() {
    return conf;
  }
}

