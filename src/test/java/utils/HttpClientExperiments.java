package utils;

import com.nullbird.hfs.utils.errors.ProxyException;
import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is experiments I do to test the HttpClient
 */
public class HttpClientExperiments {
  private final Logger LOGGER = Logger.getLogger(this.getClass().getName());

  @Test
  void run() throws Exception {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      ClassicHttpRequest httpGet = ClassicRequestBuilder.get("http://google.fr")
              .build();
      // The underlying HTTP connection is still held by the response object
      // to allow the response content to be streamed directly from the network socket.
      // In order to ensure correct deallocation of system resources
      // the user MUST call CloseableHttpResponse#close() from a finally clause.
      // Please note that if response content is not fully consumed the underlying
      // connection cannot be safely re-used and will be shut down and discarded
      // by the connection manager.
      httpclient.execute(httpGet, response -> {
        System.out.println(response.getCode() + " " + response.getReasonPhrase());
        final HttpEntity entity1 = response.getEntity();
        // do something useful with the response body
        // and ensure it is fully consumed
        EntityUtils.consume(entity1);
        return null;
      });
    }
  }

  @Test
  void run2() throws Exception {
    SimpleRequestBuilder proxyRequestBuilder = SimpleRequestBuilder.get()
            .setHttpHost(new HttpHost("https", "google.fr"))
            .setPath("/");
    var proxyRequest = proxyRequestBuilder.build();

    try (CloseableHttpAsyncClient proxyClient = HttpAsyncClients.createDefault()) {
      final Future<SimpleHttpResponse> future = proxyClient.execute(
              SimpleRequestProducer.create(proxyRequest),
              SimpleResponseConsumer.create(),
              new FutureCallback<>() {

                @Override
                public void completed(final SimpleHttpResponse proxyResponse) {
                  // Process the response:
                  try {
                    int statusCode = proxyResponse.getCode();
                    System.out.println("Status == " + statusCode);
                  } catch (Exception t) {
                    LOGGER.log(Level.SEVERE, "Receiving response", t);
                  }
                }

                @Override
                public void failed(final Exception ex) {
                  try {
                    LOGGER.log(Level.WARNING, "Request failed ", ex);
                  } catch (Exception e1) {
                    try {
                      LOGGER.log(Level.SEVERE, "", e1);
                    } catch (Throwable e2) {
                      // Any exception in here will crash the http client
                    }
                  }
                }

                @Override
                public void cancelled() {
                  LOGGER.warning("Request cancelled");
                }
              });
      future.get();

    } catch (IOException e) {
      throw new ProxyException(e);
    }
  }
  @Test
  void run3asyncExample() throws Exception {
    final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofSeconds(5))
            .build();

    try (CloseableHttpAsyncClient client = HttpAsyncClients.custom()
            .setIOReactorConfig(ioReactorConfig)
            .build()) {
      client.start();

      final HttpHost target = new HttpHost("google.fr");
      final String[] requestUris = new String[] {"/", "/ip"};

      for (final String requestUri: requestUris) {
        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath(requestUri)
                .build();

        System.out.println("Executing request " + request);
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestProducer.create(request),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {

                  @Override
                  public void completed(final SimpleHttpResponse response) {
                    System.out.println(request + "->" + new StatusLine(response));
                    System.out.println(response.getBody());
                    System.out.println(response.getBody().isText());
                    System.out.println(response.getBody().getBodyText());
                  }

                  @Override
                  public void failed(final Exception ex) {
                    System.out.println(request + "->" + ex);
                  }

                  @Override
                  public void cancelled() {
                    System.out.println(request + " cancelled");
                  }

                });
        future.get();
      }

      System.out.println("Shutting down");
      client.close(CloseMode.GRACEFUL);

    }

  }
  @Test
  void run4asyncExamplePlusStreamingBodyResponse() throws Exception {
    final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofSeconds(5))
            .build();

    try (CloseableHttpAsyncClient client = HttpAsyncClients.custom()
            .disableRedirectHandling()
            .setIOReactorConfig(ioReactorConfig)
            .build()) {
      client.start();

      final HttpHost target = new HttpHost("https","pieroxy.net");
      final String[] requestUris = new String[] {"/", "/blog/dwr/interface/Pebble.js"};

      for (final String requestUri: requestUris) {
        var request = AsyncRequestBuilder.get()
                .setHttpHost(target)
                .setPath(requestUri)
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(out);

        System.out.println("Executing request " + requestUri);
        final Future<Void> future = client.execute(
                request,
                new AbstractBinResponseConsumer<Void>() {
                  @Override
                  protected void start(HttpResponse httpResponse, ContentType contentType) throws HttpException, IOException {
                    System.out.println(requestUri + " Start -> " + new StatusLine(httpResponse));
                    Arrays.stream(httpResponse.getHeaders()).forEach(System.out::println);
                  }

                  @Override
                  protected Void buildResult() {
                    return null;
                  }

                  @Override
                  protected int capacityIncrement() {
                    return Integer.MAX_VALUE;
                  }

                  @Override
                  protected void data(ByteBuffer data, boolean endOfStream) throws IOException {
                    while (data.hasRemaining()) {
                      System.out.println(requestUri + " Chunk of " + data.remaining() + "bytes");
                      channel.write(data);
                    }
                    if (endOfStream) {
                      System.out.println("---------------------------");
                      System.out.println(out.toString(StandardCharsets.UTF_8));
                      System.out.println("---------------------------");
                      System.out.println(requestUri + " <EOS> ");
                    }

                  }

                  @Override
                  public void releaseResources() {
                    System.out.println(requestUri + " Release ");
                  }
                },null);
        future.get();
      }

      System.out.println("Shutting down");
      client.close(CloseMode.GRACEFUL);

    }
  }
}

