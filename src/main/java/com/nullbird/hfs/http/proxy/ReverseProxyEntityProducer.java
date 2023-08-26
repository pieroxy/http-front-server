package com.nullbird.hfs.http.proxy;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityProducer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReverseProxyEntityProducer extends AbstractBinAsyncEntityProducer {
  public static final int FRAGMENT_SIZE_HINT = 8192;
  private final InputStream input;
  private final AtomicBoolean available = new AtomicBoolean(true);
  final byte[] buffer = new byte[FRAGMENT_SIZE_HINT];

  public ReverseProxyEntityProducer(ContentType contentType, InputStream input) {
    super(FRAGMENT_SIZE_HINT, contentType);
    this.input = input;
  }

  @Override
  protected int availableData() {
    return available.get() ? FRAGMENT_SIZE_HINT : 0;
  }

  @Override
  protected void produceData(StreamChannel<ByteBuffer> streamChannel) throws IOException {
    assert(available.get());
    final int read = input.read(buffer);
    if (read == -1) {
      streamChannel.endStream();
      available.set(false);
      return;
    }
    final int n1 = streamChannel.write(ByteBuffer.wrap(buffer, 0, read));
    assert(n1 == read);
  }

  @Override
  public boolean isRepeatable() {
    return false;
  }

  @Override
  public void failed(Exception e) {
    //TODO Something
  }
}
