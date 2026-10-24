package utils.e2e;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A client built on a raw {@link Socket}: the request bytes are sent exactly as written by the test (no URL
 * normalization), and the response is parsed strictly enough to tell a complete response from a truncated one.
 * <br>Each request uses its own connection.
 */
public class RawClient {
  public static final int READ_TIMEOUT_MS = 10_000;

  public static class Response {
    public int status;
    public RawHttp.Headers headers;
    public byte[] body = new byte[0];
    /**
     * True if the response was fully received, as delimited by its Content-Length or chunked encoding.
     */
    public boolean complete;
    /**
     * True if the proxy stopped sending anything for {@link #READ_TIMEOUT_MS}.
     */
    public boolean timedOut;
    public String failure;

    public String bodyAsString() {
      return new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
      return "status=" + status + " complete=" + complete + " timedOut=" + timedOut + " failure=" + failure +
          "\n" + headers + "\n" + bodyAsString();
    }
  }

  private final int port;

  public RawClient(int port) {
    this.port = port;
  }

  /**
   * Sends <code>METHOD target HTTP/1.1</code> with a <code>Host</code> header, <code>Connection: close</code> and
   * the provided extra header lines.
   */
  public Response send(String method, String target, String host, List<String> headers, byte[] body) throws IOException {
    var head = new StringBuilder();
    head.append(method).append(" ").append(target).append(" HTTP/1.1\r\n");
    head.append("Host: ").append(host).append("\r\n");
    head.append("Connection: close\r\n");
    headers.forEach(h -> head.append(h).append("\r\n"));
    head.append("\r\n");
    return sendRaw(head.toString().getBytes(StandardCharsets.ISO_8859_1), body, method.equals("HEAD"));
  }

  public Response get(String target, List<String> headers) throws IOException {
    return send("GET", target, "test.local", headers, null);
  }

  public Response get(String target) throws IOException {
    return get(target, List.of());
  }

  public Response sendRaw(byte[] head, byte[] body, boolean isHead) throws IOException {
    var response = new Response();
    try (var socket = new Socket("localhost", port)) {
      socket.setSoTimeout(READ_TIMEOUT_MS);
      OutputStream out = socket.getOutputStream();
      out.write(head);
      if (body != null) out.write(body);
      out.flush();

      InputStream in = new BufferedInputStream(socket.getInputStream());
      try {
        String statusLine = RawHttp.readLine(in);
        if (statusLine == null) throw new EOFException("Connection closed before any response");
        response.status = Integer.parseInt(statusLine.split(" ", 3)[1]);
        response.headers = RawHttp.readHeaders(in);
        if (isHead || response.status == 204 || response.status == 304 || response.status < 200) {
          response.complete = true;
        } else if (RawHttp.isChunked(response.headers)) {
          response.body = RawHttp.readChunked(in);
          response.complete = true;
        } else if (response.headers.has("Content-Length")) {
          response.body = RawHttp.readFixed(in, Long.parseLong(response.headers.get("Content-Length")));
          response.complete = true;
        } else {
          // Close-delimited body
          response.body = in.readAllBytes();
          response.complete = true;
        }
      } catch (SocketTimeoutException e) {
        response.timedOut = true;
        response.failure = e.toString();
      } catch (IOException e) {
        response.failure = e.toString();
      }
    }
    return response;
  }
}
