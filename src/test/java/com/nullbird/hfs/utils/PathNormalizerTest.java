package com.nullbird.hfs.utils;

import com.nullbird.hfs.utils.errors.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PathNormalizerTest {
  @Test
  public void normalizationTest() {
    String[][] cases = {
        {"/", "/"},
        {"/a/b", "/a/b"},
        {"/a/", "/a/"},
        {"/a%20b", "/a%20b"},
        {"/caf%C3%A9", "/caf%C3%A9"},
        {"/caf%c3%a9", "/caf%C3%A9"},
        {"/%41%7e", "/A~"},
        {"/%2D%2E%5F%7E%30%7A", "/-._~0z"},
        {"/a@b:c=d,e~f!g$h&i'(k)*+l", "/a@b:c=d,e~f!g$h&i'(k)*+l"},
        {"/a+b", "/a+b"},
        {"/a;jsessionid=X/b", "/a;jsessionid=X/b"},
        {"/a;x", "/a;x"},
        {"/;x/a", "/;x/a"},
        {"/a%25b", "/a%25b"},
        {"/a%3Fb", "/a%3Fb"},
        {"/a%23b", "/a%23b"},
        {"/a%2Fb", "/a%2Fb"},
        {"/a%2fb", "/a%2Fb"},
        {"/a/./b", "/a/b"},
        {"/a/../b", "/b"},
        {"/a/%2e%2e/b", "/b"},
        {"/a/.%2E/b", "/b"},
        {"/a/b/..", "/a/"},
        {"/a/b/.", "/a/b/"},
        {"/a/..", "/"},
        {"/a/../", "/"},
        {"/a//b", "/a/b"},
        {"//a", "/a"},
        {"/a//", "/a/"},
        {"/...", "/..."},
        {"/..a", "/..a"},
        {"/a b", "/a%20b"},
        {"/café", "/caf%C3%A9"},
        {"*", "*"},
    };
    List<Executable> checks = new ArrayList<>();
    for (String[] c : cases) {
      checks.add(() -> assertEquals(c[1], PathNormalizer.normalize(c[0]), "Normalizing " + c[0]));
    }
    assertAll(checks);
  }

  @Test
  public void invalidPathsTest() {
    List<Executable> checks = new ArrayList<>();
    for (String path : List.of(
        "/..", "/../a", "/a/../..", "/%2e%2e/a",
        "/a/..;x/b", "/a/.;x/b", "/a/%2e%2e;x/b", "/..;/a",
        "/a%", "/a%4", "/a%G1", "/a%%41", "/a%٣٣")) {
      checks.add(() -> assertThrows(InvalidPathException.class, () -> PathNormalizer.normalize(path), path));
    }
    assertAll(checks);
  }
}
