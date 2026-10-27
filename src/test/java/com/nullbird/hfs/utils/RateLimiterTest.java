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
    limiter.hit("admin");
    limiter.check("admin");
    advance(1000);
    limiter.hit(" Admin ");
    var e = assertThrows(RateLimiter.RateExceededException.class, () -> limiter.check("ADMIN"));
    assertEquals(59_000, e.getRetryAfterMillis(), "Retry once the oldest hit expires");
    limiter.check("other");
  }

  @Test
  public void checkAddsNoHitTest() {
    var limiter = limiter(60_000, 1, 100);
    for (int i = 0; i < 10; i++) limiter.check("admin");
    assertEquals(0, limiter.getNbTimeStamps());
  }

  @Test
  public void resetClearsTheKeyTest() {
    var limiter = limiter(60_000, 1, 100);
    limiter.hit("admin");
    assertThrows(RateLimiter.RateExceededException.class, () -> limiter.check("admin"));
    limiter.reset("ADMIN");
    limiter.check("admin");
  }

  @Test
  public void allowsAgainOnceTheWindowHasPassedTest() {
    var limiter = limiter(50, 1, 100);
    limiter.hit("admin");
    assertThrows(RateLimiter.RateExceededException.class, () -> limiter.check("admin"));
    advance(80);
    limiter.check("admin");
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
    limiter.hit("b");
    assertThrows(RateLimiter.RateExceededException.class, () -> limiter.check("c"));
    limiter.hit("c");
    assertEquals(2, limiter.getNbKeys(), "A new key is ignored when full");
    // Known keys are still served
    limiter.check("a");
    limiter.hit("a");
    assertEquals(3, limiter.getNbTimeStamps());

    advance(61_000);
    limiter.check("c");
    limiter.hit("c");
    assertEquals(1, limiter.getNbKeys());
  }
}
