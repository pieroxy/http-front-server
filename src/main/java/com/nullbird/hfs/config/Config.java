package com.nullbird.hfs.config;

import com.nullbird.hfs.config.rules.Rule;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public class Config {
  private int defaultRedirectStatus = HttpServletResponse.SC_FOUND;
  private int httpPort = 8080;
  private int httpsPort = 8443;
  private String address;
  private String defaultLoggingLevel;
  private Map<String, String> loggersLevel;

  private List<Rule> rules;

  public List<Rule> getRules() {
    return rules;
  }

  public void setRules(List<Rule> rules) {
    this.rules = rules;
  }

  public int getDefaultRedirectStatus() {
    return defaultRedirectStatus;
  }

  public void setDefaultRedirectStatus(int defaultRedirectStatus) {
    this.defaultRedirectStatus = defaultRedirectStatus;
  }

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

  public String getDefaultLoggingLevel() {
    return defaultLoggingLevel;
  }

  public void setDefaultLoggingLevel(String defaultLoggingLevel) {
    this.defaultLoggingLevel = defaultLoggingLevel;
  }

  public Map<String, String> getLoggersLevel() {
    return loggersLevel;
  }

  public void setLoggersLevel(Map<String, String> loggersLevel) {
    this.loggersLevel = loggersLevel;
  }
}
