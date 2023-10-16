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

  /**
   *
   * @param name
   * @return never null
   */
  List<String> getCookieValues(String name);

  byte[] getBodyAsByteArray() throws IOException;

  Enumeration<String> getHeaderNames();
  Enumeration<String> getHeaders(String name);

  String getRemoteAddr();
  void setHost(String host);

  String getScheme();
  String getPath();

  InputStream getBodyStream() throws IOException;

  HttpResponse getAsyncResponse(HttpResponse syncResponse);

  HttpResponse getResponse();

  Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData() throws IOException;
}
