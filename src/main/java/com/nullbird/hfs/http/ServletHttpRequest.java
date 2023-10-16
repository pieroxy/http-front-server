package com.nullbird.hfs.http;

import com.nullbird.hfs.utils.StreamUtils;
import com.nullbird.hfs.utils.StringUtils;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServletHttpRequest implements HttpRequest {
  private final static Logger LOGGER = Logger.getLogger(ServletHttpRequest.class.getName());

  private final HttpServletRequest request;
  private ServletHttpResponse response;
  private String host;
  private String url = null;

  private Map<String, String> postFormData;

  public ServletHttpRequest(HttpServletRequest request, HttpServletResponse response) {
    this.request = request;
    this.response = new ServletHttpResponse(response);
    this.host = request.getHeader(HttpHeaders.HOST);
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
    if (url == null) {
      url = request.getRequestURL().toString();
      if (StringUtils.containsNonWhitespace(this.host)) {
        url = StringUtils.replaceHost(url, this.host);
      }
    }
    return url;
  }

  @Override
  public String getHeader(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getHeader("+name+") :: " + request.getHeader(name));
    if (name.equalsIgnoreCase(HttpHeaders.HOST)) return host;
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
    if (name.equalsIgnoreCase(HttpHeaders.HOST)) return Collections.enumeration(List.of(host));
    return request.getHeaders(name);
  }

  @Override
  public String getRemoteAddr() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getRemoteAddr() :: " + request.getRemoteAddr());
    return request.getRemoteAddr();
  }

  @Override
  public void setHost(String host) {
    this.host = host;
    this.url = null;
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
  public List<String> getCookieValues(String name) {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getCookieValue("+name+") :: not computing");
    var cookies = request.getCookies();
    if (cookies == null) return null;
    return Arrays.stream(cookies)
            .filter(cookie -> Objects.equals(cookie.getName(), name))
            .map(Cookie::getValue)
            .collect(Collectors.toList());
  }

  @Override
  public HttpResponse getAsyncResponse(HttpResponse syncResponse) {
    if (response.hasAsyncContext()) return response;
    final AsyncContext asyncContext = request.startAsync(request, ((ServletHttpResponse)syncResponse).getHttpServletResponse());
    return response = new ServletHttpResponse((HttpServletResponse) asyncContext.getResponse(), asyncContext);
  }


  public ServletHttpResponse getResponse() {
    return response;
  }

  @Override
  public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData() throws IOException {
    if (postFormData == null) {
       postFormData = new HashMap<>();
       decodeInputStream(getBodyStream(), postFormData);
    }
    return postFormData;
  }

  static void decodeInputStream(InputStream stream, Map<String, String> map) {
    Scanner s = new Scanner(stream).useDelimiter("\\&");

    s.forEachRemaining((pair) -> {
      Scanner s2 = new Scanner(pair).useDelimiter("=");
      String key = s2.next();
      try {
        String value = URLDecoder.decode(s2.next().trim(), "UTF-8");
        map.put(key, value);
      } catch (UnsupportedEncodingException
               | NoSuchElementException ex) {
        throw new RuntimeException("Could not decode x-www-form-urlencoded data", ex);
      }
    });
  }
}
