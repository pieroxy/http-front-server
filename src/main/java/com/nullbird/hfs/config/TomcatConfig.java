package com.nullbird.hfs.config;

public class TomcatConfig {
  /**
   * Port this server should listen to for http requests. If not specified, the default value is <b>8080</b>
   */
  protected int httpPort = 8080;

  /**
   * Port this server should listen to for https requests. If not specified, the default value is <b>8443</b>
   */
  protected int httpsPort = 8443;

  /**
   * Address the server should listen to. If not specified, the server will listen on all local addresses.
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat documentation</a>
   */
  protected String address;

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public int getHttpsPort() {
    return httpsPort;
  }

  public void setHttpsPort(int httpsPort) {
    this.httpsPort = httpsPort;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }
}