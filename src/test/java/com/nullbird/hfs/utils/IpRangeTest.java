package com.nullbird.hfs.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IpRangeTest {
  @Test
  public void containsTest() {
    String[][] matching = {
        {"10.0.0.1", "10.0.0.1"},
        {"10.0.0.0/8", "10.255.3.4"},
        {"10.0.0.0/8", "::ffff:10.1.2.3"},
        {"192.168.1.128/25", "192.168.1.200"},
        {"0.0.0.0/0", "8.8.8.8"},
        {"::1", "::1"},
        {"::1", "0:0:0:0:0:0:0:1"},
        {"2001:db8::/32", "2001:db8:1234::5"},
        {"2001:DB8::/32", "2001:db8::1"},
        {" 10.0.0.1 ", "10.0.0.1"},
    };
    String[][] notMatching = {
        {"10.0.0.1", "10.0.0.2"},
        {"10.0.0.0/8", "11.0.0.1"},
        {"192.168.1.128/25", "192.168.1.127"},
        {"0.0.0.0/0", "::1"},
        {"::/0", "10.0.0.1"},
        {"2001:db8::/32", "2001:db9::1"},
        {"127.0.0.1", "localhost"},
        {"127.0.0.1", "garbage"},
        {"127.0.0.1", ""},
        {"127.0.0.1", null},
    };
    List<Executable> checks = new ArrayList<>();
    for (String[] c : matching) {
      checks.add(() -> assertTrue(IpRange.parse(c[0]).contains(c[1]), c[0] + " should contain " + c[1]));
    }
    for (String[] c : notMatching) {
      checks.add(() -> assertFalse(IpRange.parse(c[0]).contains(c[1]), c[0] + " should not contain " + c[1]));
    }
    assertAll(checks);
  }

  @Test
  public void invalidRangesTest() {
    List<Executable> checks = new ArrayList<>();
    for (String value : List.of("", "localhost", "example.com", "1.2.3", "1.2.3.4.5", "256.0.0.1", "01.2.3.4",
        "1.2.3.4/33", "1.2.3.4/-1", "1.2.3.4/", "1.2.3.4/x", "::1/129", "2001:db8::g", "1.2.3.4/8/8")) {
      checks.add(() -> assertThrows(IllegalArgumentException.class, () -> IpRange.parse(value), value));
    }
    assertAll(checks);
  }
}
