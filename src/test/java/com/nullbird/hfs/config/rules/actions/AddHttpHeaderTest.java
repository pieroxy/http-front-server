package com.nullbird.hfs.config.rules.actions;

import com.nullbird.hfs.utils.errors.ConfigurationException;
import org.junit.jupiter.api.Test;
import utils.TestResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AddHttpHeaderTest {

  @Test
  public void testSimple() throws Exception {
    var action = new AddHttpHeader();
    action.setName("Name");
    action.setValue("Value");
    action.initialize(null);

    TestResponse response;
    action.run(null, response = new TestResponse(), null);
    assertEquals("Value", response.getHeaders().get("Name"), "Header value not found");
  }

  @Test
  public void testConfigurationMissingName() {
    var action = new AddHttpHeader();
    action.setValue("Value");
    assertThrows(ConfigurationException.class, () -> action.initialize(null));
  }
  @Test
  public void testConfigurationMissingValue() {
    var action = new AddHttpHeader();
    action.setName("Value");
    assertThrows(ConfigurationException.class, () -> action.initialize(null));
  }
}
