package utils.testTomcat;

import org.junit.jupiter.api.Test;
import utils.testTomcat.TestServlet;
import utils.testTomcat.TestTomcat;

public class StandaloneTestTomcat {
  @Test
  public void run() throws Exception {
    var tomcat = new TestTomcat(12345, new TestServlet());
    tomcat.await();
  }
}
