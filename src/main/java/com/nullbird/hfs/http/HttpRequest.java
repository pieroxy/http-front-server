package com.nullbird.hfs.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public interface HttpRequest {
  String getMethod();
  String getUrl();
  String getHeader(String name);
  String getParameter(String name);
  String getQueryString();

  /**
   *
   * @param name
   * @return never null
   */
  List<String> getCookieValues(String name);

  Enumeration<String> getHeaderNames();
  Enumeration<String> getHeaders(String name);

  String getRemoteAddr();
  void setHost(String host);

  String getScheme();
  String getPath();

  InputStream getBodyStream() throws IOException;

  HttpResponse getAsyncResponse(HttpResponse syncResponse);

  HttpResponse getResponse();

  /**
   * Reads the body and decodes it as <code>application/x-www-form-urlencoded</code>, without checking the method
   * or the <code>Content-Type</code>.
   * @param maxBytes The body is not read beyond this size.
   * @throws com.nullbird.hfs.utils.errors.BodyTooLargeException if the body, or its announced
   * <code>Content-Length</code>, is bigger than <code>maxBytes</code>.
   */
  Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData(int maxBytes) throws IOException;
}
