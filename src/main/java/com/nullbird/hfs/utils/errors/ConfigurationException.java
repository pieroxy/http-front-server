package com.nullbird.hfs.utils.errors;

public class ConfigurationException extends Exception {
  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public String getMessage() {
    return "Failed to apply configuration: \r\n" + super.getMessage();
  }
}
