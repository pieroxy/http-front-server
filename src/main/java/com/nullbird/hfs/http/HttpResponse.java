package com.nullbird.hfs.http;


import jakarta.servlet.http.Cookie;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Future;

public interface HttpResponse {
  void sendRedirect(int statusCode, String targetUrl);
  void respond(int statusCode, ContentType contentType, String content);
  boolean isConsumed();

  void addCookie(Cookie cookie);

  void addHeader(String name, String value);

  void setStatus(int statusCode);
  int getStatus();

  OutputStream getOutputStream() throws IOException;

  void doneProcessing();

  void consume();

  void setFuture(Future<Void> future);
  Future<Void> getFuture();
}
