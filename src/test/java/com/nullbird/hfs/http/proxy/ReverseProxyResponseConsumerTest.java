package com.nullbird.hfs.http.proxy;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.util.Deadline;
import org.apache.hc.core5.util.DeadlineTimeoutException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReverseProxyResponseConsumerTest {
  @Test
  public void errorStatusTest() {
    assertAll(
        () -> assertEquals(502, ReverseProxyResponseConsumer.getErrorStatus(new HttpHostConnectException("refused"))),
        () -> assertEquals(502, ReverseProxyResponseConsumer.getErrorStatus(new UnknownHostException("nope"))),
        () -> assertEquals(502, ReverseProxyResponseConsumer.getErrorStatus(new NoHttpResponseException("closed"))),
        () -> assertEquals(502, ReverseProxyResponseConsumer.getErrorStatus(new ConnectionClosedException("closed"))),
        () -> assertEquals(502, ReverseProxyResponseConsumer.getErrorStatus(new ProtocolException("malformed"))),
        () -> assertEquals(504, ReverseProxyResponseConsumer.getErrorStatus(new ConnectTimeoutException("connect"))),
        () -> assertEquals(504, ReverseProxyResponseConsumer.getErrorStatus(new SocketTimeoutException("read"))),
        () -> assertEquals(503, ReverseProxyResponseConsumer.getErrorStatus(
            DeadlineTimeoutException.from(Deadline.MIN_VALUE))),
        () -> assertEquals(500, ReverseProxyResponseConsumer.getErrorStatus(new IllegalStateException("bug"))));
  }
}
