package com.nullbird.hfs.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class StringUtils {

    public static void addPaddedString(StringBuilder sbf, String toAdd, int len) {
        if (toAdd.length() == len) sbf.append(toAdd);
        else if (toAdd.length()<len) {
            for (int i=0 ; i<len-toAdd.length() ; i++)
                sbf.append(" ");
            sbf.append(toAdd);
        } else {
            sbf.append(toAdd.substring(toAdd.length() - len));
        }
    }

    public static String formatNanos(long nanos) {
        if (nanos < 10000) return pad(nanos+"ns", ' ', 7);
        nanos /= 1000;
        if (nanos < 10000) return pad(nanos+"µs", ' ', 7);
        nanos /= 1000;
        if (nanos < 10000) return pad(nanos+"ms", ' ', 7);
        nanos /= 1000;
        if (nanos < 10000) return pad(nanos+"s ", ' ', 7);
        nanos /= 60;
        return pad(nanos+"mn", ' ', 7);
    }

    static String pad(String s, char fillWith, int len) {
        if (s.length()<len) {
            StringBuilder sb = new StringBuilder(len);
            while (s.length()+sb.length()<len) {
                sb.append(fillWith);
            }
            s = sb.append(s).toString();
        }
        return s;
    }

  public static boolean containsNonWhitespace(String s) {
      if (s==null) return false;
      return s.trim().length()>0;
  }

  public static String getFromInputStream(InputStream input, Charset charset) throws IOException {
      var out = new ByteArrayOutputStream();
      byte[]buf = new byte[1024];
      int read=-1;
      while ((read=input.read(buf))>-1) {
          out.write(buf,0,read);
      }
      return out.toString(charset);
  }

  public static String getHtmlErrorMessage(String message) {
        return "<html><body><h1>"+message+"</h1></body></html>\r\n";
  }
}
