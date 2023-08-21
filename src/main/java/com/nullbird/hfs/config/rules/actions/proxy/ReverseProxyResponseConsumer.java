package com.nullbird.hfs.config.rules.actions.proxy;

import com.nullbird.hfs.http.HttpResponse;
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.StatusLine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

class ReverseProxyResponseConsumer extends AbstractBinResponseConsumer<Void> {
  private final static Logger LOGGER = Logger.getLogger(ReverseProxyResponseConsumer.class.getName());
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
