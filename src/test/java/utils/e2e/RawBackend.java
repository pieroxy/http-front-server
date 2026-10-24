package utils.e2e;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A backend built on a raw {@link ServerSocket}: it records every request exactly as received on the wire, and lets
 * each test script the raw bytes of the response (or misbehave: close the connection, hang...).
 * <br>Connections are kept alive, as the reverse proxy pools them.
 */
public class RawBackend implements Closeable {

  public record ReceivedRequest(String method, String target, RawHttp.Headers headers, byte[] body, boolean chunked) {
    public String bodyAsString() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  public interface Handler {
    /**
     * Writes the response to <code>out</code>. Closing the socket is allowed, to simulate failures.
     */
    void handle(ReceivedRequest request, Socket socket, OutputStream out) throws Exception;
  }

  private final ServerSocket server;
  private final List<ReceivedRequest> received = Collections.synchronizedList(new ArrayList<>());
  private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
  private volatile Handler handler = RawBackend::respondOk;
  private volatile boolean closed;

  public RawBackend() throws IOException {
    server = new ServerSocket(0);
    var acceptor = new Thread(this::acceptLoop, "RawBackend-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  public int getPort() {
    return server.getLocalPort();
  }

  public void setHandler(Handler handler) {
    this.handler = handler;
  }

  /**
   * Restores the default handler and forgets the requests received so far.
   */
  public void reset() {
    handler = RawBackend::respondOk;
    received.clear();
  }

  public List<ReceivedRequest> getReceived() {
    synchronized (received) {
      return new ArrayList<>(received);
    }
  }

  public ReceivedRequest getSingleReceived() {
    var list = getReceived();
    if (list.size() != 1) throw new AssertionError("Expected exactly one request on the backend, got " + list.size());
    return list.get(0);
  }

  private void acceptLoop() {
    while (!closed) {
      try {
        Socket socket = server.accept();
        sockets.add(socket);
        var thread = new Thread(() -> serve(socket), "RawBackend-connection");
        thread.setDaemon(true);
        thread.start();
      } catch (IOException e) {
        if (!closed) e.printStackTrace();
      }
    }
  }

  private void serve(Socket socket) {
    try (socket) {
      InputStream in = new BufferedInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();
      while (!socket.isClosed()) {
        String requestLine = RawHttp.readLine(in);
        if (requestLine == null) return;
        String[] parts = requestLine.split(" ", 3);
        RawHttp.Headers headers = RawHttp.readHeaders(in);
        boolean chunked = RawHttp.isChunked(headers);
        byte[] body;
        if (chunked) {
          body = RawHttp.readChunked(in);
        } else if (headers.has("Content-Length")) {
          body = RawHttp.readFixed(in, Long.parseLong(headers.get("Content-Length")));
        } else {
          body = new byte[0];
        }
        var request = new ReceivedRequest(parts[0], parts[1], headers, body, chunked);
        received.add(request);
        handler.handle(request, socket, out);
        out.flush();
      }
    } catch (Exception e) {
      // Connection closed by the proxy, or by a handler simulating a failure
    } finally {
      sockets.remove(socket);
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    server.close();
    for (Socket socket : sockets) {
      try {
        socket.close();
      } catch (IOException e) {
        // Nothing to do
      }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Response helpers

  public static void respondOk(ReceivedRequest request, Socket socket, OutputStream out) throws IOException {
    respond(out, "200 OK", List.of(), "ok".getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Writes a response with a <code>Content-Length</code>. <code>headers</code> are "Name: value" lines.
   */
  public static void respond(OutputStream out, String status, List<String> headers, byte[] body) throws IOException {
    var head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
    headers.forEach(h -> head.append(h).append("\r\n"));
    head.append("Content-Length: ").append(body.length).append("\r\n\r\n");
    out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
    out.write(body);
  }

  /**
   * Writes a response with no body and no <code>Content-Length</code> (for 204, 304...).
   */
  public static void respondNoBody(OutputStream out, String status, List<String> headers) throws IOException {
    var head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
    headers.forEach(h -> head.append(h).append("\r\n"));
    head.append("\r\n");
    out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
  }
}
