package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.config.rules.actions.ReverseProxy;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.DeadlineTimeoutException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streams the backend response to the client, without ever blocking the I/O thread of the proxy client: the client
 * is written to only when it can take more (servlet non-blocking I/O), and the backend is read only as fast as the
 * client reads.
 * <br>The data received from the backend is queued, then written to the client by {@link #drain()}, either on the
 * I/O thread or on a Tomcat thread ({@link #onWritePossible()}), never on both at the same time. HttpCore reads
 * from the backend while its capacity window (64KB by default) is open, and the window is only given back once the
 * data reached the client socket: this bounds what is queued for a slow client.
 */
public class ReverseProxyResponseConsumer implements AsyncResponseConsumer<Void>, WriteListener {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyResponseConsumer.class.getName());

  public final static String MSG_INTERNAL_ERROR = "The proxy could not fulfill the request because of an internal failure.";
  public final static String MSG_BAD_GATEWAY = "The proxy could not reach the backend server. Logs will provide more details.";
  public final static String MSG_GATEWAY_TIMEOUT = "The backend server timed out.";
  public final static String MSG_PROXY_SATURATED = "The proxy has too many requests in progress to this backend server.";
  private final HttpResponse response;
  private final int requestNumber;

  private ReverseProxyHttpRequest requestData;
  private HeaderGroup headersToIgnore = new HeaderGroup();

  private final ReverseProxy conf;
  /** The backend response has started: from now on, a failure can only abort the client connection. */
  private volatile boolean responseStarted;

  private volatile ServletOutputStream out;
  private final Queue<byte[]> queue = new ConcurrentLinkedQueue<>();
  /** The number of threads that asked for a drain: only the one that brought it from 0 drains. */
  private final AtomicInteger drainRequests = new AtomicInteger();
  /** Data written since the last flush */
  private volatile boolean flushPending;
  /** Bytes received from the backend, not yet given back to the capacity window */
  private final AtomicInteger credit = new AtomicInteger();
  private volatile CapacityChannel capacityChannel;
  /** Completes the exchange, which releases the backend connection */
  private volatile FutureCallback<Void> resultCallback;
  private volatile boolean backendEnded;
  /** The client response is complete, or aborted. */
  private final AtomicBoolean finished = new AtomicBoolean();

  public ReverseProxyResponseConsumer(HttpResponse response, ReverseProxy conf, int requestNumber) {
    this.response = response;
    this.conf = conf;
    if (conf.getIgnoreResponseHeaders()!=null)
      conf.getIgnoreResponseHeaders().forEach(h -> headersToIgnore.addHeader(new BasicHeader(h, null)));
    this.requestNumber = requestNumber;
  }

  @Override
  public void consumeResponse(org.apache.hc.core5.http.HttpResponse httpResponse, EntityDetails entityDetails,
                              HttpContext context, FutureCallback<Void> resultCallback) throws IOException {
    if (finished.get()) throw new ClientIOException(new IOException("The client is gone"));
    responseStarted = true;
    if (LOGGER.isLoggable(Level.FINER)) {
      LOGGER.finer("Processing started: " + requestData.getDebugInfos() + " // First line: " + new StatusLine(httpResponse));
      Arrays.stream(httpResponse.getHeaders()).forEach(h -> LOGGER.finer(h.toString()));
    }
    response.setStatus(httpResponse.getCode());
    copyResponseHeaders(httpResponse, response);
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing of headers completed: " +requestData.getDebugInfos());

    // The client gets the headers as soon as the backend sends them
    flushPending = true;
    if (entityDetails == null) {
      backendEnded = true;
      resultCallback.completed(null);
    } else {
      this.resultCallback = resultCallback;
    }
    try {
      out = response.getOutputStream();
    } catch (IOException e) {
      throw new ClientIOException(e);
    }
    // Tomcat calls onWritePossible() as soon as the client can be written to
    out.setWriteListener(this);
  }

  protected void copyResponseHeaders(org.apache.hc.core5.http.HttpResponse proxyResponse,
                                     HttpResponse response) {
    Set<String> connectionTokens = ReverseProxyImpl.getConnectionTokens(
            Arrays.stream(proxyResponse.getHeaders(HttpHeaders.CONNECTION)).map(Header::getValue).toList());
    for (Header header : proxyResponse.getHeaders()) {
      if (connectionTokens.contains(header.getName().toLowerCase(Locale.ROOT))) continue;
      copyResponseHeader(proxyResponse, response, header);
    }
  }

  /**
   * Copy a proxied response header back to the servlet client.
   */
  protected void copyResponseHeader(org.apache.hc.core5.http.HttpResponse proxyResponse,
                                    HttpResponse response,
                                    Header header) {
    String headerName = header.getName();
    if (ReverseProxyImpl.hopByHopHeaders.containsHeader(headerName) || headersToIgnore.containsHeader(headerName))
      return;
    String headerValue = header.getValue();
    response.addHeader(headerName, headerValue);
  }

  @Override
  public void informationResponse(org.apache.hc.core5.http.HttpResponse response, HttpContext context) {
    // 1xx responses are not passed on
  }

  /**
   * Called by HttpCore when the capacity window is exhausted.
   */
  @Override
  public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
    this.capacityChannel = capacityChannel;
    if (finished.get()) {
      reopenWindow();
      return;
    }
    requestDrain();
  }

  /**
   * @throws ClientIOException once the client response is aborted: failing the exchange is what makes HttpCore close
   * the backend connection.
   */
  @Override
  public void consume(ByteBuffer src) throws IOException {
    if (finished.get()) throw new ClientIOException(new IOException("The client response is aborted"));
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Transferring body: " + requestData.getDebugInfos() + " Chunk of " + src.remaining() + "bytes");
    byte[] data = new byte[src.remaining()];
    src.get(data);
    credit.addAndGet(data.length);
    queue.add(data);
    requestDrain();
  }

  @Override
  public void streamEnd(List<? extends Header> trailers) {
    backendEnded = true;
    // Releases the backend connection, even if the client did not get everything yet
    var callback = resultCallback;
    if (callback != null) callback.completed(null);
    requestDrain();
  }

  @Override
  public void onWritePossible() {
    requestDrain();
  }

  /**
   * The client connection failed, most often because the client went away.
   */
  @Override
  public void onError(Throwable t) {
    clientFailed(t);
  }

  @Override
  public void failed(Exception cause) {
    // Reported to the FutureCallback of the exchange as well, see exchangeFailed()
  }

  @Override
  public void releaseResources() {
    // The outcome of the exchange is handled in exchangeCompleted/exchangeFailed/exchangeCancelled
  }

  private void requestDrain() {
    if (drainRequests.getAndIncrement() != 0) return;
    int requests = 1;
    do {
      try {
        drain();
      } catch (IOException | RuntimeException e) {
        clientFailed(e);
      }
      requests = drainRequests.addAndGet(-requests);
    } while (requests != 0);
  }

  /**
   * Writes to the client as long as it can take more: when it cannot, Tomcat calls {@link #onWritePossible()} later.
   */
  private void drain() throws IOException {
    if (finished.get()) {
      queue.clear();
      return;
    }
    ServletOutputStream out = this.out;
    if (out == null) return;
    while (out.isReady()) {
      byte[] data = queue.poll();
      if (data != null) {
        out.write(data);
        flushPending = true;
      } else if (flushPending) {
        // Streamed responses (SSE, long polling...) must not wait in Tomcat's buffer
        flushPending = false;
        out.flush();
      } else {
        // Everything reached the client socket
        giveBackCredit();
        if (backendEnded) complete();
        return;
      }
    }
  }

  private void giveBackCredit() throws IOException {
    CapacityChannel channel = capacityChannel;
    // Until HttpCore asks, the window is still open
    if (channel == null) return;
    int bytes = credit.getAndSet(0);
    if (bytes > 0) channel.update(bytes);
  }

  private void complete() {
    if (finished.compareAndSet(false, true)) {
      if (LOGGER.isLoggable(Level.FINE)) LOGGER.log(Level.FINE,"Request "+requestNumber+" finished with status " + response.getStatus());
      markResponseComplete();
    }
  }

  /**
   * Aborts the client connection, and the backend exchange if still running.
   */
  private void clientFailed(Throwable t) {
    if (!finished.compareAndSet(false, true)) return;
    if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Client failure (" + requestData.getDebugInfos() + "): " + t);
    queue.clear();
    // Cancelling the exchange only works until the response starts. After that, HttpCore is made to read again,
    // so that consume() fails the exchange.
    requestData.cancel();
    try {
      reopenWindow();
    } catch (IOException e) {
      // The backend connection is gone already
    }
    response.abort(t);
    markResponseComplete();
  }

  private void reopenWindow() throws IOException {
    CapacityChannel channel = capacityChannel;
    // Until HttpCore asks, the window is still open
    if (channel != null) channel.update(Integer.MAX_VALUE);
  }

  /**
   * The backend exchange completed. The client response completes once it got all the data, see {@link #drain()}.
   */
  void exchangeCompleted() {
    if (LOGGER.isLoggable(Level.FINE)) LOGGER.log(Level.FINE,"Request "+requestNumber+" received from the backend");
  }

  void exchangeCancelled() {
    if (finished.get()) return;
    LOGGER.warning("Request cancelled (" + requestData.getDebugInfos() + ")");
    exchangeFailed(new CancellationException("Request cancelled"));
  }

  /**
   * The exchange failed, on either side. Once the backend response has started, the client connection is aborted.
   * Before, the client gets an error status, or the request is retried if the backend could not be connected.
   */
  void exchangeFailed(Exception e) {
    // Cancelled by clientFailed(), or failed while completing
    if (finished.get()) return;
    if (e instanceof ClientIOException) {
      clientFailed(e);
      return;
    }
    if (!responseStarted && e instanceof HttpHostConnectException && requestData.getProxy().scheduleRetry(requestData)) {
      if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Host could not be reached, retrying (" + requestData.getDebugInfos() + ")");
      return;
    }
    if (!finished.compareAndSet(false, true)) return;
    queue.clear();
    try {
      if (responseStarted) {
        LOGGER.warning("Backend failed during the response, aborting the client (" + requestData.getDebugInfos() + "): " + e);
        response.abort(e);
      } else {
        int status = getErrorStatus(e);
        if (status == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
          LOGGER.log(Level.SEVERE, "Request errored out (" + requestData.getDebugInfos() + ")", e);
        } else {
          LOGGER.warning("Backend failure, responding " + status + " (" + requestData.getDebugInfos() + "): " + e);
        }
        response.respond(status, ContentType.TEXT_HTML, StringUtils.getHtmlErrorMessage(getErrorMessage(status)));
        // Otherwise, Tomcat only sends it once it has read the rest of the request body, which may never come
        response.getOutputStream().flush();
      }
    } catch (Exception e2) {
      LOGGER.log(Level.SEVERE, "Handling the failure of the request (" + requestData.getDebugInfos() + ")", e2);
      response.abort(e2);
    }
    markResponseComplete();
  }

  /**
   * @return The status to send to the client when the exchange failed before the backend response started.
   */
  static int getErrorStatus(Exception e) {
    if (e instanceof DeadlineTimeoutException) return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
    // Connect timeouts (ConnectTimeoutException) included
    if (e instanceof SocketTimeoutException) return HttpServletResponse.SC_GATEWAY_TIMEOUT;
    // Connection refused, unknown host, closed connection, malformed response...
    if (e instanceof IOException || e instanceof HttpException) return HttpServletResponse.SC_BAD_GATEWAY;
    return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
  }

  private static String getErrorMessage(int status) {
    return switch (status) {
      case HttpServletResponse.SC_SERVICE_UNAVAILABLE -> MSG_PROXY_SATURATED;
      case HttpServletResponse.SC_GATEWAY_TIMEOUT -> MSG_GATEWAY_TIMEOUT;
      case HttpServletResponse.SC_BAD_GATEWAY -> MSG_BAD_GATEWAY;
      default -> MSG_INTERNAL_ERROR;
    };
  }

  private void markResponseComplete() {
    try {
      response.doneProcessing();
    } catch (Exception e) {
      // What can we do, really ?
    }

    if (LOGGER.isLoggable(Level.FINE)) LOGGER.log(Level.FINE,"Request "+requestNumber+" complete ");
  }

  public ReverseProxyHttpRequest getRequestData() {
    return requestData;
  }

  public void setRequestData(ReverseProxyHttpRequest requestData) {
    this.requestData = requestData;
  }
}
