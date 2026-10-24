package com.nullbird.hfs.utils;

import com.nullbird.hfs.utils.errors.InvalidPathException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Normalizes the raw (undecoded) path of a request, following RFC 3986:
 * <ul>
 *   <li>Percent-encoded unreserved characters are decoded (<code>%41</code> becomes <code>A</code>)</li>
 *   <li>Other percent-encodings are kept, with uppercase hex digits (<code>%c3</code> becomes <code>%C3</code>)</li>
 *   <li>Dot-segments are resolved (<code>/a/../b</code> becomes <code>/b</code>)</li>
 *   <li>Empty segments are merged (<code>/a//b</code> becomes <code>/a/b</code>), the trailing slash is kept</li>
 *   <li>Everything else, path parameters included, is kept as sent</li>
 * </ul>
 * The result is both what the matchers see and what is sent to the backends, so they cannot disagree.
 */
public class PathNormalizer {
  private static final String HEX = "0123456789ABCDEF";
  private static final String SUB_DELIMS_COLON_AT = "!$&'()*+,;=:@";

  /**
   * @param rawPath The path as sent by the client, without the query string.
   * @return The normalized path. A path not starting with <code>/</code> (ex: <code>*</code>) is returned as-is.
   * @throws InvalidPathException if the path goes above the root, has a malformed percent-encoding, or has path
   * parameters on a dot-segment (<code>/..;x/</code>), which some backends would resolve as a dot-segment.
   */
  public static String normalize(String rawPath) throws InvalidPathException {
    if (rawPath == null || !rawPath.startsWith("/")) return rawPath;

    String[] segments = rawPath.substring(1).split("/", -1);
    Deque<String> output = new ArrayDeque<>();
    boolean trailingSlash = false;
    for (int i = 0; i < segments.length; i++) {
      boolean last = i == segments.length - 1;
      String segment = normalizeEncoding(segments[i], rawPath);
      int semicolon = segment.indexOf(';');
      String name = semicolon < 0 ? segment : segment.substring(0, semicolon);

      if (name.equals(".") || name.equals("..")) {
        if (semicolon >= 0) throw new InvalidPathException("Path parameters on a dot-segment: " + rawPath);
        if (name.equals("..")) {
          if (output.isEmpty()) throw new InvalidPathException("Path goes above the root: " + rawPath);
          output.removeLast();
        }
        trailingSlash = last;
      } else if (segment.isEmpty()) {
        trailingSlash = last;
      } else {
        output.addLast(segment);
        trailingSlash = false;
      }
    }
    if (output.isEmpty()) return "/";
    return "/" + String.join("/", output) + (trailingSlash ? "/" : "");
  }

  private static String normalizeEncoding(String segment, String rawPath) throws InvalidPathException {
    var sb = new StringBuilder(segment.length());
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (c == '%') {
        if (i + 2 >= segment.length()) {
          throw new InvalidPathException("Truncated percent-encoding: " + rawPath);
        }
        int hi = hexValue(segment.charAt(i + 1));
        int lo = hexValue(segment.charAt(i + 2));
        if (hi < 0 || lo < 0) throw new InvalidPathException("Malformed percent-encoding: " + rawPath);
        char decoded = (char) (hi * 16 + lo);
        if (isUnreserved(decoded)) {
          sb.append(decoded);
        } else {
          sb.append('%').append(HEX.charAt(hi)).append(HEX.charAt(lo));
        }
        i += 2;
      } else if (isUnreserved(c) || SUB_DELIMS_COLON_AT.indexOf(c) >= 0) {
        sb.append(c);
      } else {
        // Not allowed in a path. Tomcat rejects those before we get here, but let's not forward them raw.
        int codePoint = segment.codePointAt(i);
        if (Character.charCount(codePoint) == 2) i++;
        for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
          sb.append('%').append(HEX.charAt((b >> 4) & 0xF)).append(HEX.charAt(b & 0xF));
        }
      }
    }
    return sb.toString();
  }

  private static int hexValue(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
        c == '-' || c == '.' || c == '_' || c == '~';
  }
}
