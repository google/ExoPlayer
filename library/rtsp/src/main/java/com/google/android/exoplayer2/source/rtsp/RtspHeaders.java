/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.source.rtsp;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;

/**
 * RTSP message headers.
 *
 * <p>{@link Builder} must be used to construct an instance. Use {@link #get} to query header values
 * with case-insensitive header names. The extra spaces around header names and values are trimmed.
 * Contrary to HTTP, RTSP does not allow ambiguous/arbitrary header names (RFC 2326 Section 12).
 */
/* package */ final class RtspHeaders {

  public static final String ACCEPT = "accept";
  public static final String ALLOW = "allow";
  public static final String AUTHORIZATION = "authorization";
  public static final String BANDWIDTH = "bandwidth";
  public static final String BLOCKSIZE = "blocksize";
  public static final String CACHE_CONTROL = "cache-control";
  public static final String CONNECTION = "connection";
  public static final String CONTENT_BASE = "content-base";
  public static final String CONTENT_ENCODING = "content-encoding";
  public static final String CONTENT_LANGUAGE = "content-language";
  public static final String CONTENT_LENGTH = "content-length";
  public static final String CONTENT_LOCATION = "content-location";
  public static final String CONTENT_TYPE = "content-type";
  public static final String CSEQ = "cseq";
  public static final String DATE = "date";
  public static final String EXPIRES = "expires";
  public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
  public static final String PROXY_REQUIRE = "proxy-require";
  public static final String PUBLIC = "public";
  public static final String RANGE = "range";
  public static final String RTP_INFO = "rtp-info";
  public static final String RTCP_INTERVAL = "rtcp-interval";
  public static final String SCALE = "scale";
  public static final String SESSION = "session";
  public static final String SPEED = "speed";
  public static final String SUPPORTED = "supported";
  public static final String TIMESTAMP = "timestamp";
  public static final String TRANSPORT = "transport";
  public static final String USER_AGENT = "user-agent";
  public static final String VIA = "via";
  public static final String WWW_AUTHENTICATE = "www-authenticate";

  /** Builds {@link RtspHeaders} instances. */
  public static final class Builder {
    private final ImmutableListMultimap.Builder<String, String> namesAndValuesBuilder;

    /** Creates a new instance. */
    public Builder() {
      namesAndValuesBuilder = new ImmutableListMultimap.Builder<>();
    }

    /**
     * Adds a header name and header value pair.
     *
     * @param headerName The name of the header.
     * @param headerValue The value of the header.
     * @return This builder.
     */
    public Builder add(String headerName, String headerValue) {
      namesAndValuesBuilder.put(Ascii.toLowerCase(headerName.trim()), headerValue.trim());
      return this;
    }

    /**
     * Adds a list of headers.
     *
     * @param headers The list of headers, each item must following the format &lt;headerName&gt;:
     *     &lt;headerValue&gt;
     * @return This builder.
     */
    public Builder addAll(List<String> headers) {
      for (int i = 0; i < headers.size(); i++) {
        String[] header = Util.splitAtFirst(headers.get(i), ":\\s?");
        if (header.length == 2) {
          add(header[0], header[1]);
        }
      }
      return this;
    }

    /**
     * Adds multiple headers in a map.
     *
     * @param headers The map of headers, where the keys are the header names and the values are the
     *     header values.
     * @return This builder.
     */
    public Builder addAll(Map<String, String> headers) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        add(header.getKey(), header.getValue());
      }
      return this;
    }

    /**
     * Builds a new {@link RtspHeaders} instance.
     *
     * @return The newly built {@link RtspHeaders} instance.
     */
    public RtspHeaders build() {
      return new RtspHeaders(this);
    }
  }

  private final ImmutableListMultimap<String, String> namesAndValues;

  /**
   * Returns a map that associates header names to the list of values associated with the
   * corresponding header name.
   */
  public ImmutableListMultimap<String, String> asMultiMap() {
    return namesAndValues;
  }

  /**
   * Returns the most recent header value mapped to the argument, {@code null} if the header name is
   * not recorded.
   */
  @Nullable
  public String get(String headerName) {
    ImmutableList<String> headerValues = values(headerName);
    if (headerValues.isEmpty()) {
      return null;
    }
    return Iterables.getLast(headerValues);
  }

  /**
   * Returns a list of header values mapped to the argument, in the addition order. The returned
   * list is empty if the header name is not recorded.
   */
  public ImmutableList<String> values(String headerName) {
    return namesAndValues.get(Ascii.toLowerCase(headerName));
  }

  private RtspHeaders(Builder builder) {
    this.namesAndValues = builder.namesAndValuesBuilder.build();
  }
}
