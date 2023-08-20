package utils.testTomcat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class TestTomcat {
  static int counter;
  Tomcat tomcat;

  public TestTomcat(int port, BasicServlet handler) throws LifecycleException {

    tomcat = new Tomcat();

    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir != null) {
      tomcat.setBaseDir(tempDir + File.separator + "nullbirdLbTomcatTests" + counter++);
    }

    Connector ctr = new Connector();
    ctr.setPort(port);
    tomcat.setConnector(ctr);
    tomcat.getEngine().setDefaultHost("localhost");
    String contextPath = "";
    StandardContext ctx = (StandardContext) tomcat.addContext(contextPath, null);
    ctx.setClearReferencesRmiTargets(false);
    ctx.setClearReferencesObjectStreamClassCaches(false);
    ctx.setClearReferencesThreadLocals(false);

    tomcat.addServlet(contextPath, "main", new HttpServlet() {
      @Override
      protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
          handler.process(req, resp);
        } catch (Exception e) {
          throw new ServletException(e);
        }
      }
    });
    ctx.addServletMappingDecoded("/*", "main");
    tomcat.start();
  }

  public void shutdown() {
    if (tomcat == null) return;
    try {
      tomcat.stop();
    } catch (LifecycleException e) {
    }
    try {
      tomcat.destroy();
    } catch (LifecycleException e) {
    }
  }
}
