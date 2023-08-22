package utils.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.RuleMatcher;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class TrueMatcher implements RuleMatcher {
  @Override
  public boolean match(HttpRequest request) {
    return true;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {

  }

  @Override
  public void stop() {

  }
}
