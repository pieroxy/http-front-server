package com.nullbird.hfs;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.ConfigReader;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.ServletHttpRequest;
import com.nullbird.hfs.http.ServletHttpResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainServlet extends HttpServlet {
  private static final Logger LOGGER = Logger.getLogger(MainServlet.class.getSimpleName());
  private static final ContentType HTML_UTF8 = ContentType.create("text/html", StandardCharsets.UTF_8);
  private final AtomicLong nbRequestsInFlight = new AtomicLong();

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {

    // The following will prevent any matcher or action to "accidentally"
    // read the body through a req.getParameter family of methods.
    req.getInputStream();

    HttpRequest request = new ServletHttpRequest(req);
    HttpResponse response = new ServletHttpResponse(res);
    Config config = ConfigReader.getConfig();
    for (Rule rule : config.getRules()) {
      if (rule.getMatcher().match(request)) {
        try {
          rule.getAction().run(request, response, rule.getMatcher());
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, request.getUrl(), e);
        }
      }
      if (response.isConsumed()) return;
    }
    LOGGER.log(Level.SEVERE, "The configuration doesn't have a rule for this request: " + req.getRequestURL());
    response.respond(
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            HTML_UTF8,
            "<html><body><h1>The configuration doesn't have a rule for this request.</h1></body></html>\r\n"
    );
  }
}
