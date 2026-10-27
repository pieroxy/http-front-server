package com.nullbird.hfs.utils;

import com.nullbird.hfs.config.PathParametersHandling;
import com.nullbird.hfs.utils.errors.InvalidPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static com.nullbird.hfs.config.PathParametersHandling.*;
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
        {"/a%3Bb", "/a%3Bb"},
        {"/a%3bb", "/a%3Bb"},
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
      for (PathParametersHandling mode : PathParametersHandling.values()) {
        checks.add(() -> assertEquals(c[1], PathNormalizer.normalize(c[0], mode), "Normalizing " + c[0] + " with " + mode));
      }
    }
    assertAll(checks);
  }

  @Test
  public void keptPathParametersTest() {
    String[][] cases = {
        {"/a;jsessionid=X/b", "/a;jsessionid=X/b"},
        {"/a;x", "/a;x"},
        {"/;x/a", "/;x/a"},
        {"/a;x%3By", "/a;x%3By"},
    };
    List<Executable> checks = new ArrayList<>();
    for (String[] c : cases) {
      checks.add(() -> assertEquals(c[1], PathNormalizer.normalize(c[0], KEEP), "Normalizing " + c[0]));
    }
    assertAll(checks);
  }

  @Test
  public void strippedPathParametersTest() {
    String[][] cases = {
        {"/a;jsessionid=X/b", "/a/b"},
        {"/admin;x", "/admin"},
        {"/admin;x/", "/admin/"},
        {"/;x/a", "/a"},
        {"/a;x;y=z/b;c", "/a/b"},
        {"/a/..;x/b", "/b"},
        {"/a/.;x/b", "/a/b"},
        {"/a/%2e%2e;x/b", "/b"},
        {"/a;x%3By/b", "/a/b"},
    };
    List<Executable> checks = new ArrayList<>();
    for (String[] c : cases) {
      checks.add(() -> assertEquals(c[1], PathNormalizer.normalize(c[0], STRIP), "Normalizing " + c[0]));
    }
    assertAll(checks);
  }

  @Test
  public void rejectedPathParametersTest() {
    List<Executable> checks = new ArrayList<>();
    for (String path : List.of("/admin;x", "/a;jsessionid=X/b", "/;x/a", "/a;", "/a/..;x/b")) {
      checks.add(() -> assertThrows(InvalidPathException.class, () -> PathNormalizer.normalize(path, REJECT), path));
    }
    assertAll(checks);
  }

  @Test
  public void invalidPathsTest() {
    List<Executable> checks = new ArrayList<>();
    for (String path : List.of(
        "/..", "/../a", "/a/../..", "/%2e%2e/a",
        "/a%", "/a%4", "/a%G1", "/a%%41", "/a%٣٣")) {
      for (PathParametersHandling mode : PathParametersHandling.values()) {
        checks.add(() -> assertThrows(InvalidPathException.class, () -> PathNormalizer.normalize(path, mode), path + " with " + mode));
      }
    }
    // Dot-segments with parameters, when the backend would see them
    for (String path : List.of("/a/..;x/b", "/a/.;x/b", "/a/%2e%2e;x/b", "/..;/a")) {
      checks.add(() -> assertThrows(InvalidPathException.class, () -> PathNormalizer.normalize(path, KEEP), path));
    }
    // Going above the root once parameters are stripped
    checks.add(() -> assertThrows(InvalidPathException.class, () -> PathNormalizer.normalize("/..;/a", STRIP)));
    assertAll(checks);
  }
}
