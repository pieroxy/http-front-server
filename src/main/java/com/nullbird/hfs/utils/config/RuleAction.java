package com.nullbird.hfs.utils.config;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public interface RuleAction {
  void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception;
  void initialize(Config config) throws ConfigurationException;
  void stop();
}
