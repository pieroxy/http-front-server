package com.nullbird.hfs.http.proxy;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReverseProxyResponseConsumer extends AbstractBinResponseConsumer<Void> {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyResponseConsumer.class.getName());

  public final static String MSG_INTERNAL_ERROR = "The proxy could not fulfill the request because of an internal failure.";
  public final static String MSG_BAD_GATEWAY = "The proxy could not reach the backend server. Logs will provide more details.";
  public final static String MSG_GATEWAY_TIMEOUT = "The backend server timed out.";
  private final HttpResponse response;
  private WritableByteChannel __channel;

  private ReverseProxyHttpRequest requestData;

  private WritableByteChannel getChannel() throws IOException {
    if (__channel==null) __channel = Channels.newChannel(response.getOutputStream());
    return __channel;
  }

  public ReverseProxyResponseConsumer(HttpResponse response) {
    this.response = response;
  }



  @Override
  protected void start(org.apache.hc.core5.http.HttpResponse httpResponse, ContentType contentType) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing started: " +requestData.getDebugInfos() + " // First line: " + new StatusLine(httpResponse));
    Arrays.stream(httpResponse.getHeaders()).forEach(h -> LOGGER.finer(h.toString()));
    int statusCode = httpResponse.getCode();
    response.setStatus(statusCode);

    copyResponseHeaders(httpResponse, response);
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing of headers completed: " +requestData.getDebugInfos());
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
    if (ReverseProxyImpl.hopByHopHeaders.containsHeader(headerName))
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
      if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Transferring body: " + requestData.getDebugInfos() + " Chunk of " + data.remaining() + "bytes");
      getChannel().write(data);
    }
  }

  private void markResponseComplete() {
    try {
      response.doneProcessing();
    } catch (Exception e) {
      // What can we do, really ?
    }

    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Processing completed: " + requestData.getDebugInfos());

  }
  @Override
  public void releaseResources() {
    AtomicBoolean initiatedRetry = new AtomicBoolean(false);
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("Request " + requestData.getDebugInfos() + " finished with status " + response.getStatus());
    if (response.getStatus() == 0) {
      new Thread(() -> {
        try {
          int loop = 0;
          while (response.getFuture() == null) {
            if (loop++ > 1000) break;
            synchronized (response) {
              try {
                response.wait(35);
              } catch (Exception e) {
              }
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
          if (e.getCause() instanceof HttpHostConnectException) {
            if (requestData.getProxy().getConf().getMaxRetries() > 0 &&
                    requestData.getProxy().getConf().getRetriesEveryMs() > 0) {
              if (requestData.getProxy().getConf().getMaxRetries() < requestData.getAttemptNo()) {
                LOGGER.warning("Host could not be reached after retries (" + requestData.getDebugInfos() + ")");
                response.respond(
                        HttpServletResponse.SC_BAD_GATEWAY,
                        ContentType.TEXT_HTML,
                        StringUtils.getHtmlErrorMessage(MSG_BAD_GATEWAY));

              } else {
                final var timer = new Timer();
                timer.schedule(
                        new TimerTask() {
                          @Override
                          public void run() {
                            requestData.run();
                            timer.cancel();
                          }
                        },
                        requestData.getProxy().getConf().getRetriesEveryMs()
                );
                initiatedRetry.set(true);
                return;
              }


            } else {
              LOGGER.warning("Host could not be reached (" + requestData.getDebugInfos() + ")");
              response.respond(
                      HttpServletResponse.SC_BAD_GATEWAY,
                      ContentType.TEXT_HTML,
                      StringUtils.getHtmlErrorMessage(MSG_BAD_GATEWAY));
            }
          } else if (e.getCause() instanceof UnknownHostException) {
            LOGGER.log(Level.WARNING, "Host could not be reached (" + requestData.getDebugInfos() + "): "+ e.getMessage());
            response.respond(
                    HttpServletResponse.SC_BAD_GATEWAY,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_BAD_GATEWAY));
          } else if (e.getCause() instanceof SocketTimeoutException) {
            LOGGER.warning("Request timed out (" + requestData.getDebugInfos() + ")");
            response.respond(
                    HttpServletResponse.SC_GATEWAY_TIMEOUT,
                    ContentType.TEXT_HTML,
                    StringUtils.getHtmlErrorMessage(MSG_GATEWAY_TIMEOUT));
          } else {
            LOGGER.log(Level.SEVERE, "Request errored out (" + requestData.getDebugInfos() + ")", e);
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
        } finally {
          if (!initiatedRetry.get()) markResponseComplete();
        }
      }).start();
    } else {
      markResponseComplete();
    }
    // The two below statements do break havoc on the whole thing. I need to refresh my knowledge of
    // apache httpcient 5
    //ExceptionCatcher.flush(response);
    //ExceptionCatcher.close(__channel);
  }

  public ReverseProxyHttpRequest getRequestData() {
    return requestData;
  }

  public void setRequestData(ReverseProxyHttpRequest requestData) {
    this.requestData = requestData;
  }
}
