package utils;

import com.nullbird.hfs.http.HttpResponse;
import jakarta.servlet.http.Cookie;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TestResponse implements HttpResponse {
  int status;
  Map<String, String> headers = new HashMap<>();
  Map<String, Cookie> cookies = new HashMap<>();
  boolean consumed;
  ByteArrayOutputStream body = new ByteArrayOutputStream();
  Object toNotifyOnCompletion=null;
  boolean processingCompleted;

  @Override
  public void sendRedirect(int statusCode, String targetUrl) {
    status = statusCode;
    headers.put(HttpHeaders.LOCATION, targetUrl);
    consumed = true;
  }

  @Override
  public void respond(int statusCode, ContentType contentType, String content) {
    status = statusCode;
    headers.put(HttpHeaders.CONTENT_TYPE, contentType.toString());
    try {
      body.write(content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    consumed = true;
  }

  @Override
  public boolean isConsumed() {
    return consumed;
  }

  @Override
  public void addCookie(Cookie cookie) {
    cookies.put(cookie.getName(), cookie);
  }

  @Override
  public void addHeader(String name, String value) {
    headers.put(name, value);
  }

  @Override
  public void setStatus(int statusCode) {
    status = statusCode;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return body;
  }

  @Override
  public void doneProcessing() {
    processingCompleted = true;
    if (toNotifyOnCompletion!=null) {
      synchronized (toNotifyOnCompletion) {
        toNotifyOnCompletion.notifyAll();
      }
    }
  }

  @Override
  public void consume() {
    consumed = true;
  }

  public int getStatus() {
    return status;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public byte[] getBody() {
    return body.toByteArray();
  }

  public Map<String, Cookie> getCookies() {
    return cookies;
  }
  public Cookie getCookie(String name) {
    return cookies.get(name);
  }

  public void setToNotifyOnCompletion(Object toNotifyOnCompletion) {
    this.toNotifyOnCompletion = toNotifyOnCompletion;
  }

  public boolean isProcessingCompleted() {
    return processingCompleted;
  }
}
