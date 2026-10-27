package com.nullbird.hfs.config;

import com.google.gson.annotations.SerializedName;

/**
 * What to do with path parameters (<code>/a;x/b</code>) in incoming requests, see {@link Config#pathParameters}.
 */
public enum PathParametersHandling {
  /** The request is answered with a 400 */
  @SerializedName("reject") REJECT,
  /** Path parameters are removed, for the matchers as well as for the backends */
  @SerializedName("strip") STRIP,
  /** Path parameters are kept as-is */
  @SerializedName("keep") KEEP
}
