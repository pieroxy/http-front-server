package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.ConfigReader;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.ServletHttpRequest;
import com.nullbird.hfs.utils.PathNormalizer;
import com.nullbird.hfs.utils.StringUtils;
import com.nullbird.hfs.utils.errors.InvalidPathException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(urlPatterns = "/*", asyncSupported = true)
public class MainServlet extends HttpServlet {
  private static final Logger LOGGER = Logger.getLogger(MainServlet.class.getSimpleName());
  private static final Logger CONCURRENT_REQUESTS_LOGGER = Logger.getLogger("concurrentRequests");
  private static final ContentType HTML_UTF8 = ContentType.create("text/html", StandardCharsets.UTF_8);
  static final String MSG_INVALID_PATH = "The path of the request is invalid.";
  static final String MSG_RULE_FAILED = "The server could not process the request because of an internal failure.";
  private final AtomicLong nbRequestsInFlight = new AtomicLong();

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    long cr = nbRequestsInFlight.incrementAndGet();
    try {
      if (CONCURRENT_REQUESTS_LOGGER.isLoggable(Level.FINE)) {
        CONCURRENT_REQUESTS_LOGGER.fine("Concurrent requests: " + cr);
      }
      // The following will prevent any matcher or action to "accidentally"
      // read the body through a req.getParameter family of methods.
      req.getInputStream();

      String path;
      try {
        path = PathNormalizer.normalize(req.getRequestURI());
      } catch (InvalidPathException e) {
        LOGGER.info("Rejected: " + e.getMessage());
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        res.setContentType(HTML_UTF8.toString());
        res.getOutputStream().write(StringUtils.getHtmlErrorMessage(MSG_INVALID_PATH).getBytes(StandardCharsets.UTF_8));
        return;
      }
      process(new ServletHttpRequest(req, res, path), ConfigReader.getConfig());
    } finally {
      nbRequestsInFlight.decrementAndGet();
    }
  }

  static void process(HttpRequest request, Config config) {
    try {
      for (Rule rule : config.getRules()) {
        if (rule.getMatcher().match(request)) {
          rule.getAction().run(request, request.getResponse(), rule.getMatcher());
        }
        if (request.getResponse().isConsumed()) return;
      }
    } catch (Exception e) {
      // Never let the request fall through to the next rules: a failing rule could be protecting them.
      LOGGER.log(Level.SEVERE, request.getUrl(), e);
      respondInternalError(request.getResponse(), MSG_RULE_FAILED, e);
      return;
    }
    LOGGER.log(Level.SEVERE, "The configuration doesn't have a rule for this request: " + request.getUrl());
    request.getResponse().respond(
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            HTML_UTF8,
            StringUtils.getHtmlErrorMessage("The configuration doesn't have a rule for this request."));
  }

  /**
   * Responds with a 500 if nothing was sent to the client yet, or aborts the client connection otherwise, so that a
   * truncated response does not look complete. In all cases, completes the response, which matters if an action
   * started async processing before failing.
   */
  private static void respondInternalError(HttpResponse response, String message, Exception cause) {
    try {
      if (response.isCommitted()) {
        LOGGER.severe("The response was already committed, aborting the client connection.");
        response.abort(cause);
      } else {
        response.respond(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, HTML_UTF8, StringUtils.getHtmlErrorMessage(message));
      }
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Could not send the error response", e);
    } finally {
      response.consume();
      response.doneProcessing();
    }
  }
}
