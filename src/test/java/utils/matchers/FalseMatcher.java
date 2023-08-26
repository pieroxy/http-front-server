package utils.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class FalseMatcher implements RuleMatcher {
  @Override
  public boolean match(HttpRequest request) {
    return false;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {

  }

  @Override
  public void stop() {

  }
}
