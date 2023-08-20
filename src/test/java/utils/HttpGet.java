package utils;

import com.nullbird.hfs.utils.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.nio.charset.StandardCharsets;

public class HttpGet {
  public static String run(String url) throws Exception {
    StringBuilder res = new StringBuilder();
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      ClassicHttpRequest httpGet = ClassicRequestBuilder.get(url)
              .build();
      httpclient.execute(httpGet, response -> {
        final HttpEntity entity1 = response.getEntity();
        res.append(StringUtils.getFromInputStream(entity1.getContent(), StandardCharsets.UTF_8));
        EntityUtils.consume(entity1);
        return null;
      });
    }
    return res.toString();
  }
}
