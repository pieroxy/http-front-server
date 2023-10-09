package utils;

import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.net.URIBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class TestRequest implements HttpRequest {
  String method;
  String url;
  String path;
  Map<String, List<String>> headers;
  Map<String, List<String>> parameters;
  Map<String, String> cookieValues;
  Map<String, String> postData = new HashMap<>();
  String remoteAddr = "127.0.0.1";
  String scheme;
  byte[]bodyAsByteArray;
  TestResponse response;

  public TestRequest() {
    response = new TestResponse();
  }

  public static TestRequest fromUrl(String url) {
    var res = new TestRequest();
    res.setUrl(url);
    res.setHeaders(new HashMap<>());
    res.setParameters(new HashMap<>());
    res.setCookieValues(new HashMap<>());
    try {
      var obj = new URL(url);
      res.path = obj.getPath() + (obj.getQuery()==null ? "" : "?" + obj.getQuery());
      res.getHeaders().put(HttpHeaders.HOST, List.of(obj.getAuthority()));
      res.getHeaders().put(HttpHeaders.USER_AGENT, List.of("UnitTestV1.0"));
      res.getHeaders().put(HttpHeaders.ACCEPT, List.of("*/*"));
      res.setScheme(obj.getProtocol());
      res.setMethod("GET");
      var qs = obj.getQuery();
      if (qs!=null) new URIBuilder(url).getQueryParams().forEach(nvp -> {
        if (!res.parameters.containsKey(nvp.getName())) res.parameters.put(nvp.getName(), new ArrayList<>());
        res.parameters.get(nvp.getName()).add(nvp.getValue());
      });
    } catch (MalformedURLException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return res;
  }

  @Override
  public String getHeader(String name) {
    List<String> values = headers.get(name);
    if (values!=null && values.size()>0) return values.get(0);
    return null;
  }

  @Override
  public String getParameter(String name) {
    List<String> values = parameters.get(name);
    if (values!=null && values.size()>0) return values.get(0);
    return null;
  }

  @Override
  public List<String> getCookieValues(String name) {
    var res = new ArrayList<String>();
    if (cookieValues.containsKey(name)) res.add(cookieValues.get(name));
    return res;
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return Collections.enumeration(headers.keySet());
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    List<String> values = headers.get(name);
    if (values!=null) return Collections.enumeration(values);
    return null;
  }

  @Override
  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, List<String>> headers) {
    this.headers = headers;
  }

  public Map<String, List<String>> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, List<String>> parameters) {
    this.parameters = parameters;
  }

  public Map<String, String> getCookieValues() {
    return cookieValues;
  }

  public void setCookieValues(Map<String, String> cookieValues) {
    this.cookieValues = cookieValues;
  }

  @Override
  public String getRemoteAddr() {
    return remoteAddr;
  }

  public void setRemoteAddr(String remoteAddr) {
    this.remoteAddr = remoteAddr;
  }

  @Override
  public String getScheme() {
    return scheme;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public InputStream getBodyStream() throws IOException {
    return new ByteArrayInputStream(bodyAsByteArray);
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  @Override
  public byte[] getBodyAsByteArray() {
    return bodyAsByteArray;
  }

  public void setBodyAsByteArray(byte[] bodyAsByteArray) {
    this.bodyAsByteArray = bodyAsByteArray;
    headers.put(HttpHeaders.CONTENT_LENGTH, List.of(String.valueOf(bodyAsByteArray.length)));
  }

  @Override
  public HttpResponse getAsyncResponse(HttpResponse syncResponse) {
    return syncResponse;
  }

  @Override
  public TestResponse getResponse() {
    return response;
  }

  @Override
  public Map<String, String> decodeSimpleXWWWFormUrlEncodedPostData() throws IOException {
    return postData;
  }

  public Map<String, String> getPostData() {
    return postData;
  }

  public void setPostData(Map<String, String> postData) {
    this.postData = postData;
  }
}