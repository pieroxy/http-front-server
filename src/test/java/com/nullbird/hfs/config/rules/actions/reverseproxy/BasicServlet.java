package com.nullbird.hfs.config.rules.actions.reverseproxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

interface BasicServlet {
  void process(HttpServletRequest req, HttpServletResponse resp) throws Exception;
}
