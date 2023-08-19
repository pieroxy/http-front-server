package utils;

import com.nullbird.hfs.Runner;
import com.nullbird.hfs.config.Config;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.logging.Level;

public class BeforeAllTests implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

  private static boolean started = false;

  @Override
  public void beforeAll(ExtensionContext context) {
    if (!started) {
      started = true;
      var config = new Config();
      config.setDefaultLoggingLevel(Level.WARNING.getName());
      Runner.initLogging(config);
    }
  }

  @Override
  public void close() {
    // Your "after all tests" logic goes here
  }
}
