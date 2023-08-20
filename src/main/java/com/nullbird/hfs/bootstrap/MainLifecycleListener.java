package com.nullbird.hfs.bootstrap;

import com.nullbird.hfs.config.ConfigReader;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;

public class MainLifecycleListener implements LifecycleListener {

  @Override
  public void lifecycleEvent(LifecycleEvent lifecycleEvent) {
    if (lifecycleEvent.getType().equals(Lifecycle.AFTER_DESTROY_EVENT))
      ConfigReader.getConfig().getRules().forEach(rule->rule.close());
  }
}
