package com.nullbird.hfs.config.rules;

import com.nullbird.hfs.http.HttpRequest;

public interface SubstringRuleMatcher extends RuleMatcher {
  String getMatchReplacedBy(HttpRequest request, String replaceWith);
}
