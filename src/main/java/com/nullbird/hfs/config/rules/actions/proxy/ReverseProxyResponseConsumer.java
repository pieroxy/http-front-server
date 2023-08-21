package com.nullbird.hfs.config.rules.actions.proxy;

import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.StatusLine;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReverseProxyResponseConsumer extends AbstractBinResponseConsumer<Void> {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyResponseConsumer.class.getName());

  public final static String MSG_INTERNAL_ERROR = "The proxy could not fulfill the request because of an internal failure.";
  public final static String MSG_BAD_GATEWAY = "The proxy could not reach the backend server. Logs will provide more details.";
  public final static String MSG_GATEWAY_TIMEOUT = "The backend server timed out.";
  private final HttpResponse response;
  private final String debugInfo;
  private WritableByteChannel __channel;

  private WritableByteChannel getChannel() throws IOException {
    if (__channel==null) __channel = Channels.newChannel(response.getOutputStream());
    return __channel;
  }

  public ReverseProxyResponseConsumer(HttpResponse response, String debugInfo) {
    this.response = response;
    this.debugInfo = debugInfo;
  }

  @Override
  protected void start(org.apache.hc.core5.http.HttpResponse httpResponse, ContentType contentType) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing started: " +debugInfo + " // First line: " + new StatusLine(httpResponse));
    Arrays.stream(httpResponse.getHeaders()).forEach(h -> LOGGER.finer(h.toString()));
    int statusCode = httpResponse.getCode();
    response.setStatus(statusCode);

    copyResponseHeaders(httpResponse, response);
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing of headers completed: " +debugInfo);
  }

  protected void copyResponseHeaders(org.apache.hc.core5.http.HttpResponse proxyResponse,
                                     HttpResponse response) {
    for (Header header : proxyResponse.getHeaders()) {
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
    if (ReverseProxy.hopByHopHeaders.containsHeader(headerName))
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
    while (data.hasRemaining()) {
      if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Transferring body: " + debugInfo + " Chunk of " + data.remaining() + "bytes");
      getChannel().write(data);
    }
  }

  @Override
  public void releaseResources() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Request " + debugInfo + " finished");
    if (response.getStatus() == 0) {
      new Thread(() -> {
        try {
          int loop = 0;
          while(response.getFuture() == null) {
            if (loop++>1000) break;
            synchronized (response) {
              try {
                response.wait(35);
              } catch (Exception e) {}
            }
          }
          if (response.getFuture() == null) {

            LOGGER.severe("No future set for the response.");
            response.respond(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_INTERNAL_ERROR));
          } else {
            response.getFuture().get(0, TimeUnit.MICROSECONDS);
          }
        } catch (ExecutionException e) {
          if (e.getCause() instanceof HttpHostConnectException ||
                  e.getCause() instanceof UnknownHostException) {
            LOGGER.warning("Host could not be reached (" + debugInfo + ")");
            response.respond(
                    HttpServletResponse.SC_BAD_GATEWAY,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_BAD_GATEWAY));
          } else if (e.getCause() instanceof SocketTimeoutException) {
            LOGGER.warning("Request timed out (" + debugInfo + ")");
            response.respond(
                    HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_GATEWAY_TIMEOUT));
          } else {
            LOGGER.log(Level.SEVERE, "Request errored out (" + debugInfo + ")", e);
            response.respond(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_INTERNAL_ERROR));
          }
        } catch (InterruptedException e) {
          LOGGER.log(Level.SEVERE, "WTF ???", e);
          response.respond(
                  HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                  ContentType.TEXT_HTML,
                  StringUtils.getHtmlErrorMessage(MSG_INTERNAL_ERROR));
        } catch (TimeoutException e) {
          LOGGER.log(Level.SEVERE, "The future set for the response did not resolve.", e);
          response.respond(
                  HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                  ContentType.TEXT_HTML,
                  StringUtils.getHtmlErrorMessage(MSG_INTERNAL_ERROR));
        }
      }).start();

    }
    try {
      response.doneProcessing();
    } catch (Exception e) {
      // What can we do, really ?
    }

    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing completed: " + debugInfo);
    // The two below statements do break havoc on the whole thing. I need to refresh my knowledge of
    // apache httpcient 5
    //ExceptionCatcher.flush(response);
    //ExceptionCatcher.close(__channel);
  }
}
