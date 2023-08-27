package com.nullbird.hfs.config;

/**
 * This is the configuration for the access logs. The log file will be named
 * <code>{directory}/{prefix}.{date}{suffix}</code> where date is like <i>2023-08-23</i> for example.
 */
public class TomcatHttpLogConfig {
  /**
   * The prefix for the log file name
   */
  protected String prefix;
  /**
   * The suffix for the log file name
   */
  protected String suffix;
  /**
   * The directory in which log files will be written
   */
  protected String directory;
  /**
   * The format of lines in the access log file.
   * @see <a href="https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Access_Log_Valve/Attributes">Tomcat documentation</a>
   */
  protected String pattern;


  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getSuffix() {
    return suffix;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }
}
