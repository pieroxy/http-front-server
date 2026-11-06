package com.nullbird.hfs.http.proxy;

import com.nullbird.hfs.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ReverseProxyHttpRequest {
  private final AsyncRequestProducer requestProducer;
  private final ReverseProxyResponseConsumer responseConsumer;
  private final ReverseProxyImpl proxy;
  private final HttpResponse asyncResponse;

  private final String sourceDebugString;
  private final String destinationDebugString;
  private final boolean clientSentUserAgent;

  private String debugInfos;
  private AtomicInteger attemptNo = new AtomicInteger();
  /** The exchange of the current attempt */
  private volatile Future<Void> exchange;

  public ReverseProxyHttpRequest(AsyncRequestProducer requestProducer, ReverseProxyResponseConsumer responseConsumer, ReverseProxyImpl proxy, HttpResponse asyncResponse, String sourceDebugString, String destinationDebugString, boolean clientSentUserAgent) {
    this.requestProducer = requestProducer;
    this.responseConsumer = responseConsumer;
    this.proxy = proxy;
    this.asyncResponse = asyncResponse;
    this.sourceDebugString = sourceDebugString;
    this.destinationDebugString = destinationDebugString;
    this.clientSentUserAgent = clientSentUserAgent;
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

  public boolean isClientSentUserAgent() {
    return clientSentUserAgent;
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

  void setExchange(Future<Void> exchange) {
    this.exchange = exchange;
  }

  /**
   * Cancels the exchange with the backend, if running: the backend connection is closed.
   */
  public void cancel() {
    var exchange = this.exchange;
    if (exchange != null) exchange.cancel(true);
  }
}
