package utils.testTomcat;

import com.nullbird.hfs.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class TestServlet implements BasicServlet {
  @Override
  public void process(HttpServletRequest req, HttpServletResponse res) throws Exception {
    PrintWriter writer = res.getWriter();
    String wait = req.getParameter("wait");
    if (StringUtils.containsNonWhitespace(wait)) {
      String loopsString = req.getParameter("loops");
      int loops = 1;
      if (StringUtils.containsNonWhitespace(loopsString)) {
        loops = Integer.parseInt(loopsString);
      }
      for (int i=0 ; i<loops ; i++) {
        Thread.sleep(Integer.parseInt(wait));
        writer.println("Slept once");
        writer.flush();
      }
    }
    writer.println("OK");
    writer.println("Method:" + req.getMethod());
    writer.println("Body:" + StringUtils.getFromInputStream(req.getInputStream(), StandardCharsets.UTF_8));
    writer.println("URL:" + req.getRequestURL());
    writer.println("QS:" + req.getQueryString());
    Collections.list(req.getHeaderNames()).stream().forEach(name -> {
      Collections.list(req.getHeaders(name)).stream().forEach(value -> {
        writer.println("Header::" + name + ": " + value);
      });
    });

  }
}
