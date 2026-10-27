package com.nullbird.hfs.utils.errors;

import java.io.IOException;

/**
 * The body of the request is bigger than what the reader accepts. The rest of the body has not been read.
 */
public class BodyTooLargeException extends IOException {
  public BodyTooLargeException(String message) {
    super(message);
  }
}
