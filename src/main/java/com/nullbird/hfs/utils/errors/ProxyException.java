package com.nullbird.hfs.utils.errors;

public class ProxyException extends Exception {
  public ProxyException(String message) {
    super(message);
  }

  public ProxyException(String message, Throwable cause) {
    super(message, cause);
  }

  public ProxyException(Throwable cause) {
    super(cause);
  }
}
