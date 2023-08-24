package com.nullbird.hfs.http;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletHttpResponse implements HttpResponse {
  private final static Logger LOGGER = Logger.getLogger(ServletHttpResponse.class.getName());
  private final HttpServletResponse response;
  private boolean consumed = false;
  private final AsyncContext asyncContext;
  private int status = 0;
  private Future<Void> future;

  protected ServletHttpResponse(HttpServletResponse response) {
    this.response = response;
    asyncContext = null;
  }

  public ServletHttpResponse(HttpServletResponse response, AsyncContext asyncContext) {
    this.response = response;
    this.asyncContext = asyncContext;
    this.consumed = true; // AsyncContext means we're in the game.
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
    response.setStatus(this.status = statusCode);
  }

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getOutputStream()");
    consumed = true;
    return response.getOutputStream();
  }

  @Override
  public void doneProcessing() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("doneProcessing()");
    try {
      //response.flushBuffer();
    } catch (Exception e) {
      // What can we do really ?
    }
    try {
      if (asyncContext!=null)
        asyncContext.complete();
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

  HttpServletResponse getHttpServletResponse() {
    return response;
  }

  @Override
  public Future<Void> getFuture() {
    return future;
  }

  @Override
  public void setFuture(Future<Void> future) {
    this.future = future;
    synchronized (this) {
      this.notifyAll();
    }
  }

  public boolean hasAsyncContext() {
    return asyncContext!=null;
  }
}
