package utils.e2e;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal HTTP/1.1 parsing helpers, working on raw bytes so that tests see exactly what was on the wire.
 */
public class RawHttp {

  /**
   * An ordered list of headers, names and values kept exactly as received.
   */
  public static class Headers {
    private final List<String[]> list = new ArrayList<>();

    public void add(String name, String value) {
      list.add(new String[]{name, value});
    }

    public String get(String name) {
      var values = getAll(name);
      return values.isEmpty() ? null : values.get(0);
    }

    public List<String> getAll(String name) {
      return list.stream().filter(h -> h[0].equalsIgnoreCase(name)).map(h -> h[1]).toList();
    }

    public List<String> names() {
      return list.stream().map(h -> h[0]).toList();
    }

    public boolean has(String name) {
      return !getAll(name).isEmpty();
    }

    @Override
    public String toString() {
      var sb = new StringBuilder();
      list.forEach(h -> sb.append(h[0]).append(": ").append(h[1]).append("\n"));
      return sb.toString();
    }
  }

  /**
   * @return the line without its CRLF, or null if the stream ended before any byte was read.
   */
  static String readLine(InputStream in) throws IOException {
    var buffer = new ByteArrayOutputStream();
    int previous = -1;
    while (true) {
      int b = in.read();
      if (b == -1) {
        if (buffer.size() == 0 && previous == -1) return null;
        throw new EOFException("Stream ended in the middle of a line");
      }
      if (previous == '\r' && b == '\n') {
        byte[] bytes = buffer.toByteArray();
        return new String(bytes, 0, bytes.length - 1, StandardCharsets.ISO_8859_1);
      }
      buffer.write(b);
      previous = b;
    }
  }

  static Headers readHeaders(InputStream in) throws IOException {
    var headers = new Headers();
    while (true) {
      String line = readLine(in);
      if (line == null) throw new EOFException("Stream ended in the headers");
      if (line.isEmpty()) return headers;
      int colon = line.indexOf(':');
      headers.add(line.substring(0, colon), line.substring(colon + 1).trim());
    }
  }

  static boolean isChunked(Headers headers) {
    String te = headers.get("Transfer-Encoding");
    return te != null && te.toLowerCase(Locale.ROOT).contains("chunked");
  }

  /**
   * Reads a chunked body. Throws an {@link EOFException} if the terminating chunk is missing.
   */
  static byte[] readChunked(InputStream in) throws IOException {
    var body = new ByteArrayOutputStream();
    while (true) {
      String sizeLine = readLine(in);
      if (sizeLine == null) throw new EOFException("Stream ended before the terminating chunk");
      int semicolon = sizeLine.indexOf(';');
      int size = Integer.parseInt((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
      if (size == 0) {
        // Trailers, if any
        readHeaders(in);
        return body.toByteArray();
      }
      byte[] chunk = in.readNBytes(size);
      body.write(chunk);
      if (chunk.length < size) throw new EOFException("Stream ended in the middle of a chunk");
      readLine(in);
    }
  }

  /**
   * Reads exactly <code>length</code> bytes. Throws an {@link EOFException} if the stream ends before.
   */
  static byte[] readFixed(InputStream in, long length) throws IOException {
    byte[] body = in.readNBytes((int) length);
    if (body.length < length) throw new EOFException("Got " + body.length + " bytes out of " + length);
    return body;
  }

  /**
   * Encodes a body as a complete chunked stream (including the terminating chunk).
   */
  public static byte[] chunkedBytes(byte[] body, int chunkSize) {
    var out = new ByteArrayOutputStream();
    for (int i = 0; i < body.length; i += chunkSize) {
      int len = Math.min(chunkSize, body.length - i);
      out.writeBytes((Integer.toHexString(len) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
      out.write(body, i, len);
      out.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
    }
    out.writeBytes("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
    return out.toByteArray();
  }
}
