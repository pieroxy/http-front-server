package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TestRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OverrideHostTest {
  Config basicConfig;
  @BeforeEach
  public void init() {
    basicConfig = new Config();
  }

  @Test
  public void simpleTest() throws ConfigurationException {
    var action = new OverrideHost();
    action.setHost("toto.com");
    action.initialize(basicConfig);

    TestRequest request = TestRequest.fromUrl("http://tito.fr/blu");
    assertEquals("tito.fr", request.getHeader(HttpHeaders.HOST));
    assertEquals("http://tito.fr/blu", request.getUrl());
    action.run(request, null, null);
    assertEquals("toto.com", request.getHeader(HttpHeaders.HOST));
    assertEquals("http://toto.com/blu", request.getUrl());
  }

  @Test
  public void NoSlashTest() throws ConfigurationException {
    var action = new OverrideHost();
    action.setHost("toto.com");
    action.initialize(basicConfig);

    TestRequest request = TestRequest.fromUrl("http://tito.fr");
    assertEquals("tito.fr", request.getHeader(HttpHeaders.HOST));
    assertEquals("http://tito.fr", request.getUrl());
    action.run(request, null, null);
    assertEquals("toto.com", request.getHeader(HttpHeaders.HOST));
    assertEquals("http://toto.com", request.getUrl());
  }
}
