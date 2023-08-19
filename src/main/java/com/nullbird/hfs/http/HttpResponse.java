package com.nullbird.hfs.http;


import jakarta.servlet.http.Cookie;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.OutputStream;

public interface HttpResponse {
  void sendRedirect(int statusCode, String targetUrl);
  void respond(int statusCode, ContentType contentType, String content);
  boolean isConsumed();

  void addCookie(Cookie cookie);

  void addHeader(String name, String value);

  void setStatus(int statusCode);

  OutputStream getOutputStream() throws IOException;

  void flush();

  void consume();
}
