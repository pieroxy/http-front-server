package utils;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.SubstringRuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class TestSubstringRuleMatcher implements SubstringRuleMatcher {
  private String replace;

  public TestSubstringRuleMatcher(String replace) {
    this.replace = replace;
  }

  @Override
  public boolean match(HttpRequest request) {
    return request.getUrl().contains(replace);
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
  }

  @Override
  public void stop() {
  }

  @Override
  public String getMatchReplacedBy(HttpRequest request, String replaceWith) {
    return request.getUrl().replace(replace, replaceWith);
  }
}
