package com.nullbird.hfs.http;

import com.nullbird.hfs.bootstrap.ClientAborterValve;
import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.BodyTooLargeException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServletHttpRequest implements HttpRequest {
  private final static Logger LOGGER = Logger.getLogger(ServletHttpRequest.class.getName());

  private final HttpServletRequest request;
  private ServletHttpResponse response;
  private final ClientAborter aborter;
  private String host;
  private final String path;
  private final Config config;
  private String clientAddr;
  private String url = null;

  private Map<String, String> postFormData;

  /**
   * @param path The normalized path of the request, see {@link com.nullbird.hfs.utils.PathNormalizer}
   * @param config Gives the trusted proxies.
   */
  public ServletHttpRequest(HttpServletRequest request, HttpServletResponse response, String path, Config config) {
    this.request = request;
    this.path = path;
    this.config = config;
    this.aborter = (ClientAborter) request.getAttribute(ClientAborterValve.ATTRIBUTE);
    this.response = new ServletHttpResponse(response, aborter);
    this.host = request.getHeader(HttpHeaders.HOST);
  }

  public String getMethod() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getMethod() :: " + request.getMethod());
    return request.getMethod();
  }

  /**
   * @return The part before the first ? or #, with the normalized path
   */
  @Override
  public String getUrl() {
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getUrl() :: " + request.getRequestURL());
    if (url == null) {
      // getRequestURL() is scheme://host:port followed by the raw getRequestURI()
      String requestUrl = request.getRequestURL().toString();
      url = requestUrl.substring(0, requestUrl.length() - request.getRequestURI().length()) + path;
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
  public String getClientAddr() {
    if (clientAddr == null) {
      clientAddr = config.resolveClientAddr(request.getRemoteAddr(), Collections.list(request.getHeaders("X-Forwarded-For")));
    }
    return clientAddr;
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
    if (LOGGER.isLoggable(Level.FINER)) LOGGER.finer("getPath() :: " + path + " ? " + request.getQueryString());
    if (request.getQueryString() == null) return path;
    return path + "?" + request.getQueryString();
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
  public String getQueryString() {
    return request.getQueryString();
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
    asyncContext.setTimeout(0); // The http client has a timeout already.
    return response = new ServletHttpResponse((HttpServletResponse) asyncContext.getResponse(), asyncContext, aborter);
  }


  public ServletHttpResponse getResponse() {
    return response;
  }

  @Override
  public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData(int maxBytes) throws IOException {
    if (postFormData == null) {
      if (request.getContentLengthLong() > maxBytes) {
        throw new BodyTooLargeException("Content-Length " + request.getContentLengthLong() + " is over " + maxBytes);
      }
      var data = new HashMap<String, String>();
      decodeInputStream(getBodyStream(), data, maxBytes);
      postFormData = data;
    }
    return postFormData;
  }

  /**
   * Malformed pairs (no <code>=</code>, invalid percent-encoding) are ignored.
   * @throws BodyTooLargeException if the stream holds more than <code>maxBytes</code> bytes. At most
   * <code>maxBytes + 1</code> bytes are read.
   */
  static void decodeInputStream(InputStream stream, Map<String, String> map, int maxBytes) throws IOException {
    byte[] bytes = stream.readNBytes(maxBytes + 1);
    if (bytes.length > maxBytes) throw new BodyTooLargeException("Body is over " + maxBytes + " bytes");
    String body = new String(bytes, StandardCharsets.UTF_8);
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      if (eq <= 0) continue;
      try {
        String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
        String value = URLDecoder.decode(pair.substring(eq + 1).trim(), StandardCharsets.UTF_8);
        map.put(key, value);
      } catch (IllegalArgumentException e) {
        if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Ignoring malformed x-www-form-urlencoded pair");
      }
    }
  }
}
