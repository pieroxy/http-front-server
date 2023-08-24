package com.nullbird.hfs.config.rules.actions.proxy;

import com.nullbird.hfs.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;

public class ReverseProxyHttpRequest {
  private final AsyncRequestProducer requestProducer;
  private final ReverseProxyResponseConsumer responseConsumer;
  private final ReverseProxy proxy;
  private final HttpResponse asyncResponse;

  private final String sourceDebugString;
  private final String destinationDebugString;

  private String debugInfos;

  public ReverseProxyHttpRequest(AsyncRequestProducer requestProducer, ReverseProxyResponseConsumer responseConsumer, ReverseProxy proxy, HttpResponse asyncResponse, String sourceDebugString, String destinationDebugString) {
    this.requestProducer = requestProducer;
    this.responseConsumer = responseConsumer;
    this.proxy = proxy;
    this.asyncResponse = asyncResponse;
    this.sourceDebugString = sourceDebugString;
    this.destinationDebugString = destinationDebugString;
    responseConsumer.setRequestData(this);
  }

  public String getDebugInfos() {
    if (debugInfos == null) {
      debugInfos = sourceDebugString + " >> " + destinationDebugString;
    }
    return debugInfos;
  }

  public AsyncRequestProducer getRequestProducer() {
    return requestProducer;
  }

  public ReverseProxyResponseConsumer getResponseConsumer() {
    return responseConsumer;
  }

  public ReverseProxy getProxy() {
    return proxy;
  }

  public HttpResponse getAsyncResponse() {
    return asyncResponse;
  }
}
