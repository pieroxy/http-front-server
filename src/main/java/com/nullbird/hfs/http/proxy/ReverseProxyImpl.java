package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.actions.ReverseProxy;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import com.nullbird.hfs.utils.errors.ProxyException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ReverseProxyImpl  {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyImpl.class.getSimpleName());

  /** Context attribute telling {@link #stripInjectedUserAgent} that the client sent its own User-Agent. */
  private final static String CLIENT_SENT_USER_AGENT = "hfs.clientSentUserAgent";
  /** A pooled connection idle for longer than this is checked before being reused. */
  private final static TimeValue VALIDATE_AFTER_INACTIVITY = TimeValue.ofSeconds(1);
  /** Pooled connections idle for longer than this are closed. */
  private final static TimeValue EVICT_IDLE_AFTER = TimeValue.ofSeconds(30);

  private ReverseProxy conf;

  private transient AtomicInteger requestNumber;

  private transient HttpHost proxyHost;
  private transient CloseableHttpAsyncClient __proxyClient;
  /** Only created if retries are configured */
  private transient ScheduledExecutorService retryScheduler;

  public ReverseProxyImpl(ReverseProxy conf) {
    this.conf = conf;
  }

  public void run(HttpRequest request, HttpResponse response) throws ProxyException {
    try {
      int requestNumber=0;
      if (LOGGER.isLoggable(Level.FINE)) {
        if (this.requestNumber==null) this.requestNumber = new AtomicInteger();
        requestNumber = this.requestNumber.getAndIncrement();
      }
      AsyncRequestBuilder proxyRequestBuilder = buildProxyRequest(request);

      String transferEncoding = request.getHeader(HttpHeaders.TRANSFER_ENCODING);
      String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
      if (transferEncoding != null) {
        proxyRequestBuilder.setEntity(new ReverseProxyEntityProducer(null, request.getBodyStream(), -1));
      } else if (contentLength != null) {
        proxyRequestBuilder.setEntity(new ReverseProxyEntityProducer(null, request.getBodyStream(), Long.parseLong(contentLength.trim())));
      }

      copyRequestHeaders(request, proxyRequestBuilder);

      setXForwardedHeaders(request, proxyRequestBuilder);

      // Execute the request
      doExecute(request, response, proxyRequestBuilder, requestNumber);
    } catch (ProxyException e) {
      throw e;
    } catch (Exception e) {
      throw new ProxyException("Proxifying " + request.getUrl(), e);
    } finally {
      response.consume();
    }
  }

  private void doExecute(HttpRequest request, HttpResponse response, AsyncRequestBuilder proxyRequestBuilder, int requestNumber) throws ProxyException {
    var proxyRequest = proxyRequestBuilder.build();
    if (conf.isDumpAllRequests()) {
      String debugString = request.getUrl() + " >> " + proxyHost.toString();
      LOGGER.log(Level.INFO,"Request "+requestNumber+" running  " + debugString);
    }
    try {
      HttpResponse asyncResponse = request.getAsyncResponse(response);
      new ReverseProxyHttpRequest(
              proxyRequest,
              new ReverseProxyResponseConsumer(asyncResponse, conf, requestNumber),
              this, asyncResponse, request.getUrl(), proxyHost.toString(),
              request.getHeader(HttpHeaders.USER_AGENT) != null).run();
    } catch (Exception e) {
      throw new ProxyException(e);
    }
  }

  void tryExecute(ReverseProxyHttpRequest request) {
    ReverseProxyResponseConsumer consumer = request.getResponseConsumer();
    try {
      HttpClientContext context = HttpClientContext.create();
      context.setAttribute(CLIENT_SENT_USER_AGENT, request.isClientSentUserAgent());
      this.__proxyClient.execute(
              request.getRequestProducer(),
              consumer,
              context,
              new FutureCallback<>() {
                @Override
                public void completed(Void result) {
                  consumer.exchangeCompleted();
                }

                @Override
                public void failed(Exception ex) {
                  consumer.exchangeFailed(ex);
                }

                @Override
                public void cancelled() {
                  consumer.exchangeCancelled();
                }
              });
    } catch (Exception e) {
      consumer.exchangeFailed(e);
    }
  }

  /**
   * Schedules a new attempt of a request whose backend could not be connected, if the configuration allows it.
   * @return false if no retry was scheduled.
   */
  boolean scheduleRetry(ReverseProxyHttpRequest request) {
    if (retryScheduler == null || request.getAttemptNo() > conf.getMaxRetries()) return false;
    try {
      retryScheduler.schedule(request::run, conf.getRetriesEveryMs(), TimeUnit.MILLISECONDS);
      return true;
    } catch (RejectedExecutionException e) {
      // Stopping
      return false;
    }
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
    Set<String> connectionTokens = getConnectionTokens(Collections.list(request.getHeaders(HttpHeaders.CONNECTION)));
    // Get an Enumeration of all of the header names sent by the client
    Enumeration<String> enumerationOfHeaderNames = request.getHeaderNames();
    while (enumerationOfHeaderNames.hasMoreElements()) {
      String headerName = enumerationOfHeaderNames.nextElement();
      if (connectionTokens.contains(headerName.toLowerCase(Locale.ROOT))) continue;
      copyRequestHeader(request, proxyRequestBuilder, headerName);
    }
  }

  /**
   * The headers listed in <code>Connection</code> are hop-by-hop and must not be forwarded (RFC 9110, 7.6.1).
   * <code>Host</code> is never considered hop-by-hop, so that a client cannot make the backend see its own name.
   * @return The lowercase names of the headers listed in the provided <code>Connection</code> header values.
   */
  static Set<String> getConnectionTokens(List<String> connectionValues) {
    Set<String> tokens = new HashSet<>();
    for (String value : connectionValues) {
      for (String token : value.split(",")) {
        String name = token.trim().toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) tokens.add(name);
      }
    }
    tokens.remove("host");
    return tokens;
  }

  private void addHeaderImpl(String name, String value, AsyncRequestBuilder addTo) {
    if (conf.isDumpAllRequests()) {
      LOGGER.info("Header added: " + name + ": " + value);
    }
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
            "TE", "Trailers", "Transfer-Encoding", "Upgrade", "Expect",
            "X-Forwarded-For", "X-Forwarded-Proto" };
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
      if (conf.getMaxRetries() > 0 && conf.getRetriesEveryMs() > 0) {
        retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
          Thread thread = new Thread(r, "ReverseProxy-retries-" + conf.getTarget());
          thread.setDaemon(true);
          return thread;
        });
      }
    } catch (MalformedURLException e) {
      throw new ConfigurationException("Unable to parse provided target", e);
    }
  }

  /**
   * The client is configured as a transport only: none of the user-agent features of HttpClient (cookies,
   * redirects, retries, authentication, User-Agent injection) may alter what goes through.
   */
  protected CloseableHttpAsyncClient createAndStartHttpClient() {
    final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofMilliseconds(conf.getReadTimeoutMs()))
            .build();
    PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
            .setMaxConnPerRoute(500)
            .setMaxConnTotal(500)
            // Retries are disabled, so lower the odds of reusing a connection the backend just closed
            .setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY)
            .build();
    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(conf.getConnectTimeoutMs()))
            // Waiting for a free connection in the pool
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(conf.getPoolWaitTimeoutMs()))
            .setAuthenticationEnabled(false)
            .build();
    var client = HttpAsyncClients.custom()
            .setConnectionManager(connectionManager)
            .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
            .disableRedirectHandling()
            .disableCookieManagement()
            .disableAutomaticRetries()
            .disableAuthCaching()
            .evictExpiredConnections()
            .evictIdleConnections(EVICT_IDLE_AFTER)
            .addRequestInterceptorLast(ReverseProxyImpl::stripInjectedUserAgent)
            .setIOReactorConfig(ioReactorConfig)
            .setDefaultRequestConfig(requestConfig)
            .build();
    client.start();

    if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("New client created " + client);
    return client;
  }

  /**
   * HttpClient always adds its own User-Agent when the request has none, and cannot be told not to.
   */
  static void stripInjectedUserAgent(org.apache.hc.core5.http.HttpRequest request, EntityDetails entity, HttpContext context) {
    if (!Boolean.TRUE.equals(context.getAttribute(CLIENT_SENT_USER_AGENT))) {
      request.removeHeaders(HttpHeaders.USER_AGENT);
    }
  }

  public void stop() {
    LOGGER.info("Stopping http client");
    if (retryScheduler != null) retryScheduler.shutdownNow();
    __proxyClient.close(CloseMode.GRACEFUL);
  }

  public ReverseProxy getConf() {
    return conf;
  }
}

