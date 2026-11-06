package com.nullbird.hfs.http.proxy;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Streams the body of the client request to the backend, without ever blocking the I/O thread of the proxy client:
 * the client is only read when it has data ready (servlet non-blocking I/O), and only as fast as the backend accepts
 * it.
 * <br>All reads happen in {@link #produce}, on the I/O thread. The {@link ReadListener} callbacks, on Tomcat's
 * threads, only wake it up.
 */
public class ReverseProxyEntityProducer implements AsyncEntityProducer, ReadListener {
  public static final int FRAGMENT_SIZE = 8192;
  private final ServletInputStream input;
  private final long contentLength;
  private final byte[] buffer = new byte[FRAGMENT_SIZE];
  /** Read from the client, not yet accepted by the backend connection */
  private ByteBuffer pending;
  private boolean ended;
  /** True while produce() waits for Tomcat to call back: no data must be announced, or the I/O thread would spin. */
  private volatile boolean waitingForClient;
  private volatile Throwable clientFailure;
  private volatile DataStreamChannel channel;

  /**
   * The request must be in async mode.
   * @param contentLength The length of the body, or -1 if unknown (the body is then sent chunked).
   */
  public ReverseProxyEntityProducer(ServletInputStream input, long contentLength) {
    this.input = input;
    this.contentLength = contentLength;
    input.setReadListener(this);
  }

  @Override
  public int available() {
    if (clientFailure != null) return 1;
    if (pending != null) return pending.remaining();
    return waitingForClient ? 0 : FRAGMENT_SIZE;
  }

  /**
   * Sends at most one fragment per call: HttpCore calls again as long as {@link #available()} is positive.
   */
  @Override
  public void produce(DataStreamChannel channel) throws IOException {
    this.channel = channel;
    Throwable failure = clientFailure;
    if (failure != null) {
      throw new ClientIOException(failure instanceof IOException e ? e : new IOException(failure));
    }
    if (pending == null && !ended) {
      // Set before checking, so that a callback arriving in between is not missed
      waitingForClient = true;
      if (input.isFinished()) {
        end(channel);
        return;
      }
      if (!input.isReady()) return;
      waitingForClient = false;
      final int read;
      try {
        read = input.read(buffer);
      } catch (IOException e) {
        throw new ClientIOException(e);
      }
      if (read == -1) {
        end(channel);
        return;
      }
      pending = ByteBuffer.wrap(buffer, 0, read);
    }
    if (pending != null) {
      channel.write(pending);
      // Otherwise, HttpCore calls again when the connection can take more
      if (!pending.hasRemaining()) pending = null;
    }
  }

  private void end(DataStreamChannel channel) throws IOException {
    ended = true;
    waitingForClient = true;
    channel.endStream();
  }

  private void wakeUp() {
    waitingForClient = false;
    DataStreamChannel channel = this.channel;
    // Before the first produce() call, there is nothing to wake up
    if (channel != null) channel.requestOutput();
  }

  @Override
  public void onDataAvailable() {
    wakeUp();
  }

  @Override
  public void onAllDataRead() {
    wakeUp();
  }

  @Override
  public void onError(Throwable t) {
    clientFailure = t;
    wakeUp();
  }

  @Override
  public long getContentLength() {
    return contentLength;
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public String getContentEncoding() {
    return null;
  }

  @Override
  public boolean isChunked() {
    return contentLength < 0;
  }

  @Override
  public Set<String> getTrailerNames() {
    return null;
  }

  @Override
  public boolean isRepeatable() {
    return false;
  }

  @Override
  public void failed(Exception cause) {
    // The exchange reports the failure
  }

  @Override
  public void releaseResources() {
  }
}
