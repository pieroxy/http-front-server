package com.nullbird.hfs.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {
  public static void copyStream(InputStream in, OutputStream os) throws IOException {
    copyStream(in, os, 1024 * 10); // Default buffer = 10KB
  }

  public static void copyStream(InputStream in, OutputStream os, int bufferSize) throws IOException {
    byte[] buffer = new byte[bufferSize];
    while (true) {
      int read = in.read(buffer);
      if (read == -1) return;
      os.write(buffer, 0, read);
    }
  }

}
