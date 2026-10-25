package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.http.ClientAborter;
import jakarta.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;

import java.io.IOException;

/**
 * Gives the servlet a {@link ClientAborter} for the current request, as the request attribute
 * {@link #ATTRIBUTE}. The servlet API has no way to abort a response, so this relies on Tomcat's
 * {@link ActionCode#CLOSE_NOW}, which is what Tomcat itself does when an error occurs on a committed response.
 */
public class ClientAborterValve extends ValveBase {
  public static final String ATTRIBUTE = ClientAborterValve.class.getName();

  public ClientAborterValve() {
    super(true);
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    request.setAttribute(ATTRIBUTE, (ClientAborter) cause -> response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, cause));
    getNext().invoke(request, response);
  }
}
