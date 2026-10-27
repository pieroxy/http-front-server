package com.nullbird.hfs.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterTest {
  /** Starts near the overflow of nanoTime values, which must not matter */
  private long now = Long.MAX_VALUE - TimeUnit.SECONDS.toNanos(30);

  private RateLimiter limiter(long milliseconds, int maxHits, int maxKeys) {
    return new RateLimiter(milliseconds, maxHits, maxKeys, () -> now);
  }

  private void advance(long millis) {
    now += TimeUnit.MILLISECONDS.toNanos(millis);
  }

  @Test
  public void checkRefusesOnceTheKeyHasMaxHitsCaseInsensitivelyTest() {
    var limiter = limiter(60_000, 2, 100);
    assertTrue(limiter.hit("admin").allowed());
    assertTrue(limiter.check("admin").allowed());
    advance(1000);
    var afterHit = limiter.hit(" Admin ");
    assertFalse(afterHit.allowed(), "hit returns the status after the hit");
    var status = limiter.check("ADMIN");
    assertFalse(status.allowed());
    assertEquals(59_000, status.retryAfterMillis(), "Retry once the oldest hit expires");
    assertEquals(status, afterHit);
    assertTrue(limiter.check("other").allowed());
  }

  @Test
  public void checkAddsNoHitTest() {
    var limiter = limiter(60_000, 1, 100);
    for (int i = 0; i < 10; i++) assertTrue(limiter.check("admin").allowed());
    assertEquals(0, limiter.getNbTimeStamps());
  }

  @Test
  public void resetClearsTheKeyTest() {
    var limiter = limiter(60_000, 1, 100);
    limiter.hit("admin");
    assertFalse(limiter.check("admin").allowed());
    limiter.reset("ADMIN");
    assertTrue(limiter.check("admin").allowed());
  }

  @Test
  public void allowsAgainOnceTheWindowHasPassedTest() {
    var limiter = limiter(50, 1, 100);
    limiter.hit("admin");
    assertFalse(limiter.check("admin").allowed());
    advance(80);
    assertTrue(limiter.check("admin").allowed());
  }

  @Test
  public void dropsKeysWithNoHitLeftInTheWindowTest() {
    var limiter = limiter(50, 5, 100);
    limiter.hit("a");
    limiter.hit("b");
    assertEquals(2, limiter.getNbKeys());
    advance(80);
    limiter.hit("c");
    assertEquals(1, limiter.getNbKeys());
    assertEquals(1, limiter.getNbTimeStamps());
  }

  @Test
  public void newKeysAreRefusedWhenFullTest() {
    var limiter = limiter(60_000, 5, 2);
    limiter.hit("a");
    advance(1000);
    limiter.hit("b");
    var status = limiter.check("c");
    assertFalse(status.allowed());
    assertEquals(59_000, status.retryAfterMillis(), "Until the next sweep");
    assertFalse(limiter.hit("c").allowed());
    assertEquals(2, limiter.getNbKeys(), "A new key is ignored when full");
    // Known keys are still served
    assertTrue(limiter.check("a").allowed());
    assertTrue(limiter.hit("a").allowed());
    assertEquals(3, limiter.getNbTimeStamps());

    advance(61_000);
    assertTrue(limiter.check("c").allowed());
    assertTrue(limiter.hit("c").allowed());
    assertEquals(1, limiter.getNbKeys());
  }
}
