package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.Config;
import com.nullbird.hfs.config.rules.Rule;
import com.nullbird.hfs.http.HttpRequest;
import com.nullbird.hfs.http.HttpResponse;
import com.nullbird.hfs.utils.config.RuleAction;
import com.nullbird.hfs.utils.config.RuleMatcher;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.BeforeAllTests;
import utils.TestRequest;
import utils.matchers.TrueMatcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith({BeforeAllTests.class})
public class MainServletTest {

  /**
   * Records whether it was run, and does what the provided behavior says.
   */
  static class TestAction implements RuleAction {
    interface Behavior { void run(HttpResponse response) throws Exception; }
    final Behavior behavior;
    boolean ran;

    TestAction(Behavior behavior) {
      this.behavior = behavior;
    }

    @Override
    public void run(HttpRequest request, HttpResponse response, RuleMatcher matcher) throws Exception {
      ran = true;
      behavior.run(response);
    }

    @Override
    public void initialize(Config config) {
    }

    @Override
    public void stop() {
    }
  }

  private static Rule rule(RuleMatcher matcher, RuleAction action) {
    var rule = new Rule();
    rule.setMatcher(matcher);
    rule.setAction(action);
    return rule;
  }

  private static Config config(Rule... rules) {
    var config = new Config();
    config.setRules(new ArrayList<>(List.of(rules)));
    return config;
  }

  private static TestAction respondOk() {
    return new TestAction(response -> response.respond(200, org.apache.hc.core5.http.ContentType.TEXT_PLAIN, "ok"));
  }

  @Test
  public void failingActionDoesNotFallThroughTest() {
    var failing = new TestAction(response -> { throw new RuntimeException("boom"); });
    var next = respondOk();
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(new TrueMatcher(), failing), rule(new TrueMatcher(), next)));

    assertTrue(failing.ran);
    assertFalse(next.ran, "The rule after a failing action must not run");
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request.getResponse().getStatus());
    assertTrue(new String(request.getResponse().getBody(), StandardCharsets.UTF_8).contains(MainServlet.MSG_RULE_FAILED));
    assertTrue(request.getResponse().isProcessingCompleted());
  }

  @Test
  public void failingMatcherDoesNotFallThroughTest() {
    var failingMatcher = new TrueMatcher() {
      @Override
      public boolean match(HttpRequest request) {
        throw new RuntimeException("boom");
      }
    };
    var guarded = respondOk();
    var next = respondOk();
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(failingMatcher, guarded), rule(new TrueMatcher(), next)));

    assertFalse(guarded.ran);
    assertFalse(next.ran, "The rule after a failing matcher must not run");
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request.getResponse().getStatus());
    assertTrue(request.getResponse().isProcessingCompleted());
  }

  @Test
  public void failureAfterConsumingCompletesTheResponseTest() {
    // Mimics ReverseProxy failing after having started async processing
    var failing = new TestAction(response -> {
      response.consume();
      throw new RuntimeException("boom");
    });
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(new TrueMatcher(), failing)));

    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request.getResponse().getStatus());
    assertTrue(request.getResponse().isProcessingCompleted(), "The response must be completed, or the request hangs");
  }

  @Test
  public void failureAfterCommitOnlyCompletesTheResponseTest() {
    var failing = new TestAction(response -> {
      response.setStatus(200);
      response.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
      ((utils.TestResponse) response).setCommitted(true);
      throw new RuntimeException("boom");
    });
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(new TrueMatcher(), failing)));

    assertEquals(200, request.getResponse().getStatus());
    assertEquals("partial", new String(request.getResponse().getBody(), StandardCharsets.UTF_8));
    assertTrue(request.getResponse().isProcessingCompleted());
  }

  @Test
  public void nominalTest() {
    var skipped = new TestAction(response -> { });
    var ok = respondOk();
    var never = respondOk();
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(new TrueMatcher(), skipped), rule(new TrueMatcher(), ok), rule(new TrueMatcher(), never)));

    assertTrue(skipped.ran);
    assertTrue(ok.ran);
    assertFalse(never.ran);
    assertEquals(200, request.getResponse().getStatus());
  }

  @Test
  public void noRuleTest() {
    var request = TestRequest.fromUrl("http://anything/");
    MainServlet.process(request, config(rule(new TrueMatcher(), new TestAction(response -> { }))));
    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request.getResponse().getStatus());
  }
}
