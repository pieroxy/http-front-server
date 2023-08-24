package com.nullbird.hfs.config;

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
}
