package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

public class RespondLiteral implements RuleAction {
  protected String contentType;
  protected String content;


  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
    response.respond(HttpServletResponse.SC_OK, ContentType.create(contentType), content);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    if (contentType==null) throw new ConfigurationException("RespondLiteral action definition must include a non null 'contentType' attribute");
    if (content==null) throw new ConfigurationException("RespondLiteral action definition must include a non null 'content' attribute");

  }

  @Override
  public void stop() {
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
