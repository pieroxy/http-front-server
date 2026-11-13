package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.ServletHttpRequest;
import com.nullbird.hfs.http.ServletHttpResponse;
import com.nullbird.hfs.http.StaticFileServlet;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.WebResource;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.apache.tomcat.util.buf.UDecoder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * This action serves the files of a directory, the request path being the path of the file relative to {@link #root}.
 * It supports range and conditional (<code>ETag</code>, <code>Last-Modified</code>) requests. Only <code>GET</code>,
 * <code>HEAD</code> and <code>OPTIONS</code> are allowed. The response is always consumed: a missing file is a 404.
 * <br>Headers such as <code>Content-Security-Policy</code> or <code>Cache-Control</code> can be set by
 * {@link AddHttpHeader} rules placed before this one.
 * <br>Text files (<code>text/*</code>) are served as UTF-8.
 * <br>Files changed on disk may be served from cache for up to 5 seconds. Symbolic links inside {@link #root} are
 * not followed.
 */
public class ServeStatic implements RuleAction {
  private final static Logger LOGGER = Logger.getLogger(ServeStatic.class.getSimpleName());
  private static final AtomicInteger INSTANCES = new AtomicInteger();

  /**
   * The directory holding the files to serve.
   * <br>This attribute is mandatory.
   */
  protected String root;
  /**
   * The file served for a path ending with <code>/</code>, if it exists in that directory.
   * <br>Defaults to <code>index.html</code>.
   */
  protected String index = "index.html";
  /**
   * For single page applications: the path, relative to {@link #root}, of the file served when the requested file
   * doesn't exist and the last segment of the path has no extension. For example, with <code>/index.html</code>,
   * <code>/page/login</code> serves <code>/index.html</code> while a missing <code>/app.js</code> is still a 404.
   * <br>This attribute is optional.
   */
  protected String spaFallback;
  /**
   * Extra MIME types, by file extension (without the dot). They override Tomcat's defaults.
   * <br>This attribute is optional.
   */
  protected Map<String, String> mimeTypes;

  private transient StandardRoot resources;
  private transient StaticFileServlet servlet;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
    if (!(request instanceof ServletHttpRequest servletRequest) || !(response instanceof ServletHttpResponse servletResponse)) {
      throw new IllegalStateException("ServeStatic needs a servlet request and response");
    }
    response.consume();
    String path = resolve(request.getPath());
    if (path == null) {
      servletResponse.getHttpServletResponse().sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    servlet.serve(servletRequest.getHttpServletRequest(), servletResponse.getHttpServletResponse(), path);
  }

  /**
   * @param requestPath The normalized path, with its query string if any.
   * @return The decoded path of the file to serve, relative to {@link #root}, or null if it cannot map to a file.
   */
  String resolve(String requestPath) {
    int query = requestPath.indexOf('?');
    try {
      String path = UDecoder.URLDecode(query < 0 ? requestPath : requestPath.substring(0, query), StandardCharsets.UTF_8,
          EncodedSolidusHandling.REJECT, EncodedSolidusHandling.REJECT);
      if (!path.startsWith("/")) return null;
      if (path.endsWith("/") && index != null && resources.getResource(path + index).isFile()) {
        return path + index;
      }
      WebResource resource = resources.getResource(path);
      if (resource.exists() || spaFallback == null) return path;
      String lastSegment = path.substring(path.lastIndexOf('/') + 1);
      return lastSegment.contains(".") ? path : spaFallback;
    } catch (IllegalArgumentException e) {
      // Encoded slashes, or a path the resources consider invalid
      return null;
    }
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (root == null) throw new ConfigurationException("ServeStatic action definition must include a non null 'root' attribute");
    File rootFolder = new File(root);
    if (!rootFolder.isDirectory() || !rootFolder.canRead()) {
      throw new ConfigurationException("ServeStatic 'root' must be a readable directory: " + rootFolder.getAbsolutePath());
    }
    if (spaFallback != null && !spaFallback.startsWith("/")) {
      throw new ConfigurationException("ServeStatic 'spaFallback' must start with '/'");
    }
    try {
      // Only holds the docBase and the MIME types for the resources, it is never started.
      StandardContext context = new StandardContext();
      String name = "/hfs-static-" + INSTANCES.incrementAndGet();
      context.setName(name);
      context.setPath(name);
      context.setDocBase(rootFolder.getCanonicalPath());
      Tomcat.addDefaultMimeTypeMappings(context);
      if (mimeTypes != null) mimeTypes.forEach(context::addMimeMapping);

      resources = new StandardRoot(context);
      resources.start();
      servlet = new StaticFileServlet(resources);
    } catch (Exception e) {
      stop();
      throw new ConfigurationException("ServeStatic could not serve " + rootFolder.getAbsolutePath() + ": " + e.getMessage(), e);
    }
    if (spaFallback != null && !resources.getResource(spaFallback).isFile()) {
      LOGGER.warning("ServeStatic 'spaFallback' " + spaFallback + " is not a file in " + rootFolder.getAbsolutePath());
    }
  }

  @Override
  public void stop() {
    if (servlet != null) servlet.destroy();
    servlet = null;
    if (resources != null) {
      try {
        resources.stop();
        resources.destroy();
      } catch (Exception e) {
        LOGGER.warning("Could not stop the resources of " + root + ": " + e);
      }
    }
    resources = null;
  }

  public void setRoot(String root) {
    this.root = root;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public void setSpaFallback(String spaFallback) {
    this.spaFallback = spaFallback;
  }

  public void setMimeTypes(Map<String, String> mimeTypes) {
    this.mimeTypes = mimeTypes;
  }
}
