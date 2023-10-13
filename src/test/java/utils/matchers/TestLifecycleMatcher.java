package utils.matchers;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.utils.config.RuleMatcher;
import com.nullbird.hfs.utils.errors.ConfigurationException;

public class TestLifecycleMatcher implements RuleMatcher {
  boolean initialized = false;
  boolean stopped = false;

  @Override
  public boolean match(HttpRequest request) {
    if (!initialized) throw new RuntimeException("Matcher not initialized");
    return false;
  }

  @Override
  public void initialize(Config config) throws ConfigurationException {
    initialized = true;
  }

  @Override
  public void stop() {
    if (!initialized) throw new RuntimeException("Matcher not initialized");
    stopped = true;
  }

  public boolean wasStopped() {
    return stopped;
  }
  public boolean wasInitialized() {
    return initialized;
  }
}
