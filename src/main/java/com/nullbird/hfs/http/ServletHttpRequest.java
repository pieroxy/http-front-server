package com.nullbird.hfs.http;

import com.nullbird.hfs.utils.StreamUtils;
import com.nullbird.hfs.utils.UtilityCollectors;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletHttpRequest implements HttpRequest {
  private final static Logger LOGGER = Logger.getLogger(ServletHttpRequest.class.getName());

  private final HttpServletRequest request;
  private String url = null;

  public ServletHttpRequest(HttpServletRequest request) {
    this.request = request;
  }

  public String getMethod() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getMethod() :: " + request.getMethod());
    return request.getMethod();
  }

  @Override
  public byte[] getBodyAsByteArray() throws IOException {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getBodyAsByteArray() :: not computing");
    // TODO This needs to go away
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    InputStream inputStream = request.getInputStream();
    StreamUtils.copyStream(inputStream, outputStream);
    return outputStream.toByteArray();
  }

  /**
   * @return The part before the first ? or #
   */
  @Override
  public String getUrl() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getUrl() :: " + request.getRequestURL());
    if (url == null) url = request.getRequestURL().toString();
    return url;
  }

  @Override
  public String getHeader(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getHeader("+name+") :: " + request.getHeader(name));
    return request.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getHeaderNames() :: not computing");
    return request.getHeaderNames();
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getHeaders() :: not computing");
    return request.getHeaders(name);
  }

  @Override
  public String getRemoteAddr() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getRemoteAddr() :: " + request.getRemoteAddr());
    return request.getRemoteAddr();
  }

  @Override
  public String getScheme() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getScheme() :: " + request.getScheme());
    return request.getScheme();
  }

  @Override
  public String getPath() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getPath() :: " + request.getPathInfo() + " ? " + request.getQueryString());
    String res = request.getPathInfo();
    if (request.getQueryString() == null) return res;
    return res + "?" + request.getQueryString();
  }

  @Override
  public InputStream getBodyStream() throws IOException {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getBodyStream() :: not computing");
    return request.getInputStream();
  }

  @Override
  public String getParameter(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getParameter("+name+") :: " + request.getParameter(name));
    return request.getParameter(name);
  }

  @Override
  public String getCookieValue(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getCookieValue("+name+") :: not computing");
    Cookie c = Arrays.stream(request.getCookies())
            .filter(cookie -> Objects.equals(cookie.getName(), name))
            .collect(UtilityCollectors.getOneItemOrNull());
    return c == null ? null : c.getValue();
  }
}
