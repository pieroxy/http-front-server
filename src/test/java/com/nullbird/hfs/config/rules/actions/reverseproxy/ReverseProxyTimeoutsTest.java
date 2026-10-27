package com.nullbird.hfs.config.rules.actions.reverseproxy;

import com.nullbird.hfs.config.rules.actions.ReverseProxy;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReverseProxyTimeoutsTest {

  @Test
  public void defaultsTest() {
    var action = new ReverseProxy();
    assertEquals(ReverseProxy.DEFAULT_CONNECT_TIMEOUT_MS, action.getConnectTimeoutMs());
    assertEquals(ReverseProxy.DEFAULT_READ_TIMEOUT_MS, action.getReadTimeoutMs());
    assertEquals(ReverseProxy.DEFAULT_CONNECT_TIMEOUT_MS, action.getPoolWaitTimeoutMs());
  }

  @Test
  public void oldSettingIsAFallbackTest() {
    var action = new ReverseProxy();
    action.setConnectionTimeoutMs(1234L);
    assertEquals(1234, action.getConnectTimeoutMs());
    assertEquals(1234, action.getReadTimeoutMs());

    action.setConnectTimeoutMs(10L);
    action.setReadTimeoutMs(20L);
    assertEquals(10, action.getConnectTimeoutMs());
    assertEquals(20, action.getReadTimeoutMs());
    assertEquals(10, action.getPoolWaitTimeoutMs());
  }

  @Test
  public void zeroMeansNoTimeoutButThePoolWaitIsBoundedTest() {
    var action = new ReverseProxy();
    action.setConnectionTimeoutMs(0L);
    assertEquals(0, action.getConnectTimeoutMs());
    assertEquals(0, action.getReadTimeoutMs());
    assertEquals(ReverseProxy.DEFAULT_POOL_WAIT_TIMEOUT_MS, action.getPoolWaitTimeoutMs());
  }

  @Test
  public void negativeTimeoutIsRejectedTest() {
    var connect = actionTo("http://localhost:1");
    connect.setConnectTimeoutMs(-1L);
    var read = actionTo("http://localhost:1");
    read.setReadTimeoutMs(-1L);
    var old = actionTo("http://localhost:1");
    old.setConnectionTimeoutMs(-1L);
    assertAll(
        () -> assertThrows(ConfigurationException.class, () -> connect.initialize(null)),
        () -> assertThrows(ConfigurationException.class, () -> read.initialize(null)),
        () -> assertThrows(ConfigurationException.class, () -> old.initialize(null)));
  }

  private static ReverseProxy actionTo(String target) {
    var action = new ReverseProxy();
    action.setTarget(target);
    return action;
  }
}
