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
  /**
   * The state of a key: allowed, or refused for <code>retryAfterMillis</code>.
   */
  public record Status(boolean allowed, long retryAfterMillis) {
    static final Status ALLOWED = new Status(true, 0);

    static Status refusedFor(long retryAfterNanos) {
      return new Status(false, Math.max(0, TimeUnit.NANOSECONDS.toMillis(retryAfterNanos)));
    }
  }

  private final long windowNanos;
  private final int maxHits;
  private final int maxKeys;
  private final LongSupplier nanoClock;

  private final Map<String, Deque<Long>> data = new HashMap<>();
  private long lastSweep;

  /**
   * @param maxKeys When this many keys have hits in the window, new keys are refused and their hits ignored, until
   * the window frees some.
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
   * Keys are compared trimmed and case-insensitively. Adds no hit.
   *
   * @return Refused if {@code key} already has {@code maxHits} hits in the window, or if it has none and
   * {@code maxKeys} keys do.
   */
  public synchronized Status check(String key) {
    long now = nanoClock.getAsLong();
    return statusOf(hitsOf(normalize(key), now), now);
  }

  /**
   * Adds a hit to {@code key}, unless it is new and {@code maxKeys} keys already have hits in the window.
   *
   * @return The status of {@code key} after the hit, see {@link #check}.
   */
  public synchronized Status hit(String key) {
    long now = nanoClock.getAsLong();
    String normalized = normalize(key);
    Deque<Long> hits = hitsOf(normalized, now);
    if (hits == null) {
      if (data.size() >= maxKeys) return statusOf(null, now);
      hits = new ArrayDeque<>();
      data.put(normalized, hits);
    }
    hits.addLast(now);
    return statusOf(hits, now);
  }

  private Status statusOf(Deque<Long> hits, long now) {
    if (hits == null) {
      return data.size() >= maxKeys ? Status.refusedFor(lastSweep + windowNanos - now) : Status.ALLOWED;
    }
    return hits.size() >= maxHits ? Status.refusedFor(hits.peekFirst() + windowNanos - now) : Status.ALLOWED;
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
}
