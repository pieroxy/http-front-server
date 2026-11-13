package com.nullbird.hfs.http;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Globals;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.servlets.DefaultServlet;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tomcat's {@link DefaultServlet} (ranges, conditional requests, MIME types, sendfile), running outside any servlet
 * context: it serves the files of the {@link WebResourceRoot} it is given, at the path given to {@link #serve}.
 * <br>The {@link ServletContext} it sees only provides the resources, the MIME types and logging.
 */
public class StaticFileServlet extends DefaultServlet implements ServletConfig {
  private final static Logger LOGGER = Logger.getLogger(StaticFileServlet.class.getName());
  private static final String PATH_ATTRIBUTE = StaticFileServlet.class.getName() + ".path";
  private static final Map<String, String> INIT_PARAMETERS = Map.of(
      "listings", "false",
      "readonly", "true",
      "allowPostAsGet", "false",
      "showServerInfo", "false",
      "fileEncoding", "UTF-8");

  private final transient ServletContext servletContext;

  public StaticFileServlet(WebResourceRoot resources) throws ServletException {
    servletContext = (ServletContext) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class<?>[]{ServletContext.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getAttribute" -> Globals.RESOURCES_ATTR.equals(args[0]) ? resources : null;
          case "getMimeType" -> {
            String file = (String) args[0];
            int dot = file.lastIndexOf('.');
            yield dot < 0 ? null : resources.getContext().findMimeMapping(file.substring(dot + 1));
          }
          case "log" -> {
            LOGGER.log(Level.WARNING, (String) args[0], args.length > 1 && args[1] instanceof Throwable t ? t : null);
            yield null;
          }
          case "getResourceAsStream", "getInitParameter" -> null;
          case "getInitParameterNames", "getAttributeNames" -> Collections.emptyEnumeration();
          default -> throw new UnsupportedOperationException("ServletContext." + method.getName());
        });
    init();
  }

  /**
   * @param path The decoded path of the file, relative to the resources root. Directories are redirected to the
   * same path with a trailing slash, or get a 404 if they already have one.
   */
  public void serve(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException, IOException {
    String mimeType = resources.getResource(path).getMimeType();
    // Matches the fileEncoding, so DefaultServlet doesn't convert the content
    if (mimeType != null && mimeType.startsWith("text/")) response.setCharacterEncoding("UTF-8");
    request.setAttribute(PATH_ATTRIBUTE, path);
    try {
      service(request, response);
    } finally {
      request.removeAttribute(PATH_ATTRIBUTE);
    }
  }

  @Override
  protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
    return (String) request.getAttribute(PATH_ATTRIBUTE);
  }

  @Override
  public ServletConfig getServletConfig() {
    return this;
  }

  @Override
  public ServletContext getServletContext() {
    return servletContext;
  }

  @Override
  public String getServletName() {
    return getClass().getSimpleName();
  }

  @Override
  public String getInitParameter(String name) {
    return INIT_PARAMETERS.get(name);
  }

  @Override
  public Enumeration<String> getInitParameterNames() {
    return Collections.enumeration(INIT_PARAMETERS.keySet());
  }
}
