package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

/**
 * This action is used to respond a small piece of text. <code>ads.txt</code>, <code>robots.txt</code>,
 * <code>apple-app-site-association</code>, etc, all these little files don't need to live on the filesystem
 * and have little to do with your domain application. Content is served with a http status of 200.
 */
public class RespondLiteral implements RuleAction {
  /**
   * The Content-Type header that will be used for the response.
   * <br>This attribute is mandatory.
   */
  protected String contentType;
  /**
   * The body of the http response.
   * <br>This attribute is mandatory.
   */
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
