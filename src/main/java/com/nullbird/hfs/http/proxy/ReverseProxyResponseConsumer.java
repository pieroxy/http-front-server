package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.config.rules.actions.ReverseProxy;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.DeadlineTimeoutException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReverseProxyResponseConsumer extends AbstractBinResponseConsumer<Void> {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyResponseConsumer.class.getName());

  public final static String MSG_INTERNAL_ERROR = "The proxy could not fulfill the request because of an internal failure.";
  public final static String MSG_BAD_GATEWAY = "The proxy could not reach the backend server. Logs will provide more details.";
  public final static String MSG_GATEWAY_TIMEOUT = "The backend server timed out.";
  public final static String MSG_PROXY_SATURATED = "The proxy has too many requests in progress to this backend server.";
  private final HttpResponse response;
  private final int requestNumber;
  private WritableByteChannel __channel;

  private ReverseProxyHttpRequest requestData;
  private HeaderGroup headersToIgnore = new HeaderGroup();

  private final ReverseProxy conf;
  /** The backend response has started: from now on, a failure can only abort the client connection. */
  private volatile boolean responseStarted;

  private WritableByteChannel getChannel() throws IOException {
    if (__channel==null) __channel = Channels.newChannel(response.getOutputStream());
    return __channel;
  }

  public ReverseProxyResponseConsumer(HttpResponse response, ReverseProxy conf, int requestNumber) {
    this.response = response;
    this.conf = conf;
    if (conf.getIgnoreResponseHeaders()!=null)
      conf.getIgnoreResponseHeaders().forEach(h -> headersToIgnore.addHeader(new BasicHeader(h, null)));
    this.requestNumber = requestNumber;
  }



  @Override
  protected void start(org.apache.hc.core5.http.HttpResponse httpResponse, ContentType contentType) throws IOException {
    responseStarted = true;
    if (LOGGER.isLoggable(Level.FINER)) {
      LOGGER.finer("Processing started: " + requestData.getDebugInfos() + " // First line: " + new StatusLine(httpResponse));
      Arrays.stream(httpResponse.getHeaders()).forEach(h -> LOGGER.finer(h.toString()));
    }
    int statusCode = httpResponse.getCode();
    response.setStatus(statusCode);

    copyResponseHeaders(httpResponse, response);
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing of headers completed: " +requestData.getDebugInfos());
    try {
      // The client gets the headers as soon as the backend sends them
      response.flush();
    } catch (IOException e) {
      throw new ClientIOException(e);
    }
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
  protected Void buildResult() {
    return null;
  }

  @Override
  protected int capacityIncrement() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void data(ByteBuffer data, boolean endOfStream) throws IOException {
    try {
      while (data.hasRemaining()) {
        if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Transferring body: " + requestData.getDebugInfos() + " Chunk of " + data.remaining() + "bytes");
        getChannel().write(data);
      }
      // Streamed responses (SSE, long polling...) must not wait in Tomcat's buffer
      response.flush();
    } catch (IOException e) {
      throw new ClientIOException(e);
    }
  }

  @Override
  public void releaseResources() {
    // The outcome of the exchange is handled in exchangeCompleted/exchangeFailed/exchangeCancelled
  }

  /**
   * The backend response was fully transferred to the client.
   */
  void exchangeCompleted() {
    if (LOGGER.isLoggable(Level.FINE)) LOGGER.log(Level.FINE,"Request "+requestNumber+" finished with status " + response.getStatus());
    markResponseComplete();
  }

  void exchangeCancelled() {
    LOGGER.warning("Request cancelled (" + requestData.getDebugInfos() + ")");
    exchangeFailed(new CancellationException("Request cancelled"));
  }

  /**
   * The exchange failed, on either side. Once the backend response has started, the client connection is aborted.
   * Before, the client gets an error status, or the request is retried if the backend could not be connected.
   */
  void exchangeFailed(Exception e) {
    try {
      if (e instanceof ClientIOException) {
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Client failure (" + requestData.getDebugInfos() + "): " + e);
        response.abort(e);
      } else if (responseStarted) {
        LOGGER.warning("Backend failed during the response, aborting the client (" + requestData.getDebugInfos() + "): " + e);
        response.abort(e);
      } else if (e instanceof HttpHostConnectException && requestData.getProxy().scheduleRetry(requestData)) {
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Host could not be reached, retrying (" + requestData.getDebugInfos() + ")");
        return;
      } else {
        int status = getErrorStatus(e);
        if (status == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
          LOGGER.log(Level.SEVERE, "Request errored out (" + requestData.getDebugInfos() + ")", e);
        } else {
          LOGGER.warning("Backend failure, responding " + status + " (" + requestData.getDebugInfos() + "): " + e);
        }
        response.respond(status, ContentType.TEXT_HTML, StringUtils.getHtmlErrorMessage(getErrorMessage(status)));
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
