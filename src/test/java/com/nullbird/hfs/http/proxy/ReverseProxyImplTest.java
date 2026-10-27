package com.nullbird.hfs.http.proxy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ReverseProxyImplTest {
  private static final Set<String> AUTH = Set.of("auth", "auth2");

  @Test
  public void removeCookiesTest() {
    assertAll(
        () -> assertEquals("a=1; b=2", ReverseProxyImpl.removeCookies("a=1; auth=xyz; b=2", AUTH)),
        () -> assertEquals("a=1", ReverseProxyImpl.removeCookies("auth=x;a=1;auth2=y", AUTH)),
        () -> assertEquals("a=1; b=x=y", ReverseProxyImpl.removeCookies(" a=1 ;auth=;  b=x=y ", AUTH)),
        () -> assertNull(ReverseProxyImpl.removeCookies("auth=xyz", AUTH)),
        () -> assertNull(ReverseProxyImpl.removeCookies("auth=xyz; auth2=abc", AUTH)),
        () -> assertNull(ReverseProxyImpl.removeCookies(" auth = xyz ", AUTH), "Names are trimmed"),
        () -> assertEquals("Auth=1; myauth=2; auth-x=3", ReverseProxyImpl.removeCookies("Auth=1; myauth=2; auth-x=3", AUTH),
            "Names are compared exactly"),
        () -> assertEquals("a=1;b=2", ReverseProxyImpl.removeCookies("a=1;b=2", AUTH), "Unchanged when nothing is removed"));
  }
}
