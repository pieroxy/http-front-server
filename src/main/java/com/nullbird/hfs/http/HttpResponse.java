package com.nullbird.hfs.http;


import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;

public interface HttpResponse {
  void sendRedirect(int statusCode, String targetUrl);
  void respond(int statusCode, ContentType contentType, String content);
  boolean isConsumed();

  /**
   * @return true if the status and headers have already been sent to the client
   */
  boolean isCommitted();

  void addCookie(Cookie cookie);

  void addHeader(String name, String value);

  void setStatus(int statusCode);
  int getStatus();

  /**
   * Writable in blocking mode, or in non-blocking mode once in async mode (see {@link HttpRequest#getAsyncResponse}).
   */
  ServletOutputStream getOutputStream() throws IOException;

  /**
   * Closes the client connection without completing the response, so that the client sees it as failed.
   * {@link #doneProcessing()} must still be called afterwards.
   */
  void abort(Throwable cause);

  void doneProcessing();

  void consume();
}
