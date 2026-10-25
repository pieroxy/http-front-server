package com.nullbird.hfs.http.proxy;

import java.io.IOException;

/**
 * A failure reading from or writing to the client (and not the backend) during a proxied exchange.
 * <br>Most often the client went away, which is routine.
 */
public class ClientIOException extends IOException {
  public ClientIOException(IOException cause) {
    super(cause.getMessage(), cause);
  }
}
