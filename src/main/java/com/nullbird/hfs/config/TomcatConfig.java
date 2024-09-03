package com.nullbird.hfs.config;

/**
 * The configuration for Tomcat
 */
public class TomcatConfig {
  /**
   * Port this server should listen to for http requests. If not specified, the default value is <b>8080</b>
   */
  protected int httpPort = 8080;

  /**
   * Address the server should listen to. If not specified, the server will listen on all local addresses.
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat documentation</a>
   */
  protected String address;

  /**
   * The http log configuration. If not set, no http access logs will be produced.
   */
  protected TomcatHttpLogConfig httpLogConfig;

  /**
   * Used for testing purposes, you should not try to play with this unless you know what you're doing.
   */
  protected int maxThreads = 0;

  /**
   * The https configuration. Mandatory if you want https configured.
   */
  protected TomcatSslConfig sslConfig;

  /**
   * The 404 error page. If not defined, the default Tomcat page will be shown.
   */
  protected String errorPage404;

  /**
   * The 400 error page. If not defined, the default Tomcat page will be shown.
   */
  protected String errorPage400;

  /**
   * The 500 error page. If not defined, the default Tomcat page will be shown.
   */
  protected String errorPage500;

  /**
   * The catch-all error page. On any error or exception, if defined, this page will be shown.
   */
  protected String errorPageAll;

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }


  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public int getMaxThreads() {
    return maxThreads;
  }

  public void setMaxThreads(int maxThreads) {
    this.maxThreads = maxThreads;
  }

  public TomcatSslConfig getSslConfig() {
    return sslConfig;
  }

  public void setSslConfig(TomcatSslConfig sslConfig) {
    this.sslConfig = sslConfig;
  }

  public TomcatHttpLogConfig getHttpLogConfig() {
    return httpLogConfig;
  }

  public void setHttpLogConfig(TomcatHttpLogConfig httpLogConfig) {
    this.httpLogConfig = httpLogConfig;
  }

  public String getErrorPage404() {
    return errorPage404;
  }

  public String getErrorPage400() {
    return errorPage400;
  }

  public String getErrorPage500() {
    return errorPage500;
  }

  public String getErrorPageAll() {
    return errorPageAll;
  }
}
