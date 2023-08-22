package utils.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class DumbOddMatcher implements RuleMatcher {
  public static final String MATCHER_VALUE = "matcherValue";
  @Override
  public boolean match(HttpRequest request) {
    return Integer.parseInt(request.getHeader(MATCHER_VALUE))%2 == 1;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
  }

  @Override
  public void stop() {
  }
}
