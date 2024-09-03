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
      return """
          <html>
              <head>
                  <style>
                      body {
                          text-align: center;
                      }
                      h1 {
                          border: 1px solid #eee;
                          display: inline-block;
                          margin-top: calc( 50vh - 7em );
                          padding: 2em 1em;
                          background-color: #f8f8f8;
                          box-shadow: 3px 3px 10px rgba(0,0,0,0.1);
                          font-family: Arial, Helvetica, sans-serif;
                          max-width: 50%;
                      }
                  </style>
              </head>
              <body>
                  <h1>
                  """ + message + """
                    </h1>
                </body>
            </html>\r\n""";
  }

  public static String replaceHost(String url, String host) {
      int first = url.indexOf("://");
      int second = url.indexOf("/", first + 4);
      url = url.substring(0, first+3) + host + (second == -1 ? "" : url.substring(second));
      return url;
  }

    public static String formatForHTML(String arg) {
        if (arg == null)
            return "";

        return replace(replace(
                replace(replace(arg, "&", "&amp;"), "\"", "&quot;"), ">",
                "&gt;"), "<", "&lt;");
    }

    public static String replace(String str, String oldValue, String newValue) {
        int index = str.indexOf(oldValue);

        if (index == -1)
            return str;
        else {
            int oldValueLen = oldValue.length();
            int indexRemaining = 0;
            StringBuilder result = new StringBuilder();

            while (index != -1) {
                result.append(str.substring(indexRemaining, index));
                result.append(newValue);
                index = str.indexOf(oldValue, indexRemaining = index + oldValueLen);
            }
            result.append(str.substring(indexRemaining));
            return result.toString();
        }
    }


}
