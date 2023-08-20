package utils.testTomcat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface BasicServlet {
  void process(HttpServletRequest req, HttpServletResponse resp) throws Exception;
}
