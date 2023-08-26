package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.http.proxy.ReverseProxyImpl;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class ReverseProxy implements RuleAction {
  protected String target;
  protected boolean doForwardIP = true;

  protected int maxRetries = 0;
  protected int retriesEveryMs = 0;

  private ReverseProxyImpl proxy;

  @Override
  public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
    proxy.run(request, response);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    proxy = new ReverseProxyImpl(this);
    proxy.initialize(config);
  }

  @Override
  public void stop() {
    proxy.stop();
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public boolean isDoForwardIP() {
    return doForwardIP;
  }

  public void setDoForwardIP(boolean doForwardIP) {
    this.doForwardIP = doForwardIP;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public int getRetriesEveryMs() {
    return retriesEveryMs;
  }

  public void setRetriesEveryMs(int retriesEveryMs) {
    this.retriesEveryMs = retriesEveryMs;
  }
}
