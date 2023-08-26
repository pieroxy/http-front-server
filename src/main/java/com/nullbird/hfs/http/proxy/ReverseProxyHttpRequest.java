package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;

import java.util.concurrent.atomic.AtomicInteger;

public class ReverseProxyHttpRequest {
  private final AsyncRequestProducer requestProducer;
  private final ReverseProxyResponseConsumer responseConsumer;
  private final ReverseProxyImpl proxy;
  private final HttpResponse asyncResponse;

  private final String sourceDebugString;
  private final String destinationDebugString;

  private String debugInfos;
  private AtomicInteger attemptNo = new AtomicInteger();

  public ReverseProxyHttpRequest(AsyncRequestProducer requestProducer, ReverseProxyResponseConsumer responseConsumer, ReverseProxyImpl proxy, HttpResponse asyncResponse, String sourceDebugString, String destinationDebugString) {
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

  public ReverseProxyImpl getProxy() {
    return proxy;
  }

  public HttpResponse getAsyncResponse() {
    return asyncResponse;
  }

  public int getAttemptNo() {
    return attemptNo.get();
  }
  public int incAttemptNo() {
    return attemptNo.getAndIncrement();
  }

  public void run() {
    incAttemptNo();
    proxy.tryExecute(this);
  }
}
