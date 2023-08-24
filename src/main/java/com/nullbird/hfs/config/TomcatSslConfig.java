package com.nullbird.hfs.config;

public class TomcatSslConfig {
  /**
   * Port this server should listen to for https requests. If not specified, the default value is <b>8443</b>
   */
  protected int port = 8443;

  /**
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat's documentation.</a>
   */
  protected String protocols;
  /**
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat's documentation.</a>
   */
  protected String ciphers;
  /**
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat's documentation.</a>
   */
  protected String certificateFile;
  /**
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat's documentation.</a>
   */
  protected String certificateKeyFile;
  /**
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/http.html">Tomcat's documentation.</a>
   */
  protected String certificateChainFile;


  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getProtocols() {
    return protocols;
  }

  public void setProtocols(String protocols) {
    this.protocols = protocols;
  }

  public String getCiphers() {
    return ciphers;
  }

  public void setCiphers(String ciphers) {
    this.ciphers = ciphers;
  }

  public String getCertificateFile() {
    return certificateFile;
  }

  public void setCertificateFile(String certificateFile) {
    this.certificateFile = certificateFile;
  }

  public String getCertificateKeyFile() {
    return certificateKeyFile;
  }

  public void setCertificateKeyFile(String certificateKeyFile) {
    this.certificateKeyFile = certificateKeyFile;
  }

  public String getCertificateChainFile() {
    return certificateChainFile;
  }

  public void setCertificateChainFile(String certificateChainFile) {
    this.certificateChainFile = certificateChainFile;
  }
}
