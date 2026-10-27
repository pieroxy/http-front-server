package com.nullbird.hfs.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * At most {@code maxHits} hits per key within any sliding window of {@code milliseconds}.
 * Expired hits are dropped while hitting: the key's own on every hit, every other key's at most
 * once per window, so memory stays bounded by the keys hit during the last window, and by {@code maxKeys}.
 * <br>Copied from mom ({@code net.pieroxy.mom.utils.RateLimiter}), with a monotonic clock and a cap on the
 * number of keys.
 */
public class RateLimiter {
  private final long windowNanos;
  private final int maxHits;
  private final int maxKeys;
  private final LongSupplier nanoClock;

  private final Map<String, Deque<Long>> data = new HashMap<>();
  private long lastSweep;

  /**
   * @param maxKeys When this many keys have hits in the window, new keys are refused (by {@link #check}) and
   * ignored (by {@link #hit}) until the window frees some.
   */
  public RateLimiter(long milliseconds, int maxHits, int maxKeys) {
    this(milliseconds, maxHits, maxKeys, System::nanoTime);
  }

  /** Visible for tests: a fake clock. */
  RateLimiter(long milliseconds, int maxHits, int maxKeys, LongSupplier nanoClock) {
    this.windowNanos = TimeUnit.MILLISECONDS.toNanos(milliseconds);
    this.maxHits = maxHits;
    this.maxKeys = maxKeys;
    this.nanoClock = nanoClock;
    this.lastSweep = nanoClock.getAsLong();
  }

  /**
   * Keys are compared trimmed and case-insensitively.
   *
   * @throws RateExceededException if {@code key} already has {@code maxHits} hits in the window, or if it has none
   * and {@code maxKeys} keys do. Adds no hit.
   */
  public synchronized void check(String key) throws RateExceededException {
    long now = nanoClock.getAsLong();
    Deque<Long> hits = hitsOf(normalize(key), now);
    if (hits == null) {
      if (data.size() >= maxKeys) throw new RateExceededException(TimeUnit.NANOSECONDS.toMillis(lastSweep + windowNanos - now));
    } else if (hits.size() >= maxHits) {
      throw new RateExceededException(TimeUnit.NANOSECONDS.toMillis(hits.peekFirst() + windowNanos - now));
    }
  }

  public synchronized void hit(String key) {
    long now = nanoClock.getAsLong();
    String normalized = normalize(key);
    if (hitsOf(normalized, now) == null) {
      if (data.size() >= maxKeys) return;
      data.put(normalized, new ArrayDeque<>());
    }
    data.get(normalized).addLast(now);
  }

  public synchronized void reset(String key) {
    data.remove(normalize(key));
  }

  /** {@code key}'s hits still in the window, null if none; also sweeps every key at most once per window. */
  private Deque<Long> hitsOf(String key, long now) {
    long expired = now - windowNanos;
    if (now - lastSweep >= windowNanos) {
      gc(expired);
      lastSweep = now;
    }
    Deque<Long> hits = data.get(key);
    if (hits == null) return null;
    trim(hits, expired);
    if (hits.isEmpty()) {
      data.remove(key);
      return null;
    }
    return hits;
  }

  private static String normalize(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
  }

  public synchronized int getNbKeys() {
    return data.size();
  }

  public synchronized int getNbTimeStamps() {
    return data.values().stream().mapToInt(Deque::size).sum();
  }

  private void gc(long expired) {
    for (Iterator<Deque<Long>> it = data.values().iterator(); it.hasNext(); ) {
      Deque<Long> hits = it.next();
      trim(hits, expired);
      if (hits.isEmpty()) it.remove();
    }
  }

  /** Hits are appended in time order, so the expired ones are at the head. */
  private static void trim(Deque<Long> hits, long expired) {
    // Differences, not comparisons: nanoTime values can overflow
    while (!hits.isEmpty() && hits.peekFirst() - expired < 0) hits.removeFirst();
  }

  public static class RateExceededException extends RuntimeException {
    private final long retryAfterMillis;

    public RateExceededException(long retryAfterMillis) {
      super("Rate exceeded, retry in " + retryAfterMillis + " ms");
      this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    /** Until the oldest hit in the window expires, freeing one slot. */
    public long getRetryAfterMillis() {
      return retryAfterMillis;
    }
  }
}
