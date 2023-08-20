package com.nullbird.hfs.http;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletHttpResponse implements HttpResponse {
  private final static Logger LOGGER = Logger.getLogger(ServletHttpResponse.class.getName());
  private final HttpServletResponse response;
  private boolean consumed = false;

  public ServletHttpResponse(HttpServletResponse response) {
    this.response = response;
  }

  public void sendRedirect(int statusCode, String targetUrl) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("sendRedirect("+statusCode+","+targetUrl+")");
    response.setStatus(statusCode);
    response.addHeader("Location", targetUrl);
    consumed = true;
  }

  public void respond(int statusCode, ContentType contentType, String content) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("respond("+statusCode+","+contentType+","+content.length()+"chars)");
    response.setStatus(statusCode);
    response.setContentType(contentType.toString());
    response.setCharacterEncoding("UTF-8");
    try {
      response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    consumed = true;
  }

  @Override
  public void setStatus(int statusCode) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("setStatus("+statusCode+")");
    response.setStatus(statusCode);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getOutputStream()");
    consumed = true;
    return response.getOutputStream();
  }

  @Override
  public void flush() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("flush()");
    try {
      response.flushBuffer();
    } catch (Exception e) {
      // What can we do really ?
    }
  }

  @Override
  public void consume() {
    consumed = true;
  }

  public boolean isConsumed() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("isConsumed() :: " + consumed);
    return consumed;
  }

  @Override
  public void addCookie(Cookie cookie) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("addCookie("+cookie+")");
    response.addCookie(cookie);
  }

  @Override
  public void addHeader(String name, String value) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("addHeader("+name+","+value+")");
    response.addHeader(name, value);
  }
}