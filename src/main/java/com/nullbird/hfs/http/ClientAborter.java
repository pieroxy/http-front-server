package com.nullbird.hfs.http;

/**
 * Closes the client connection right away, without completing the response.
 * <br>Once a response is committed, this is the only way to tell the client that what it received is not the
 * whole response: completing it normally would make a truncated body look complete (for instance by sending
 * the final chunk of a chunked response).
 */
@FunctionalInterface
public interface ClientAborter {
  void abort(Throwable cause);
}
