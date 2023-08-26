package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleAction;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;

/**
 * This action will add an http header to the response. It does not consume the response and allow the
 * flow to continue.
 */
public class AddHttpHeader implements RuleAction {
  /**
   * The name of the http header to be added.
   * <br>This attribute is mandatory.
   */
  protected String name;
  /**
   * The value of the http header to be added.
   * <br>This attribute is mandatory.
   */
  protected String value;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) {
    response.addHeader(name, value);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (name==null) throw new ConfigurationException("AddHttpHeader action definition must include a non null 'name' attribute");
    if (value==null) throw new ConfigurationException("AddHttpHeader action definition must include a non null 'value' attribute");
  }

  @Override
  public void stop() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
