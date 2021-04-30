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
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

  public static final String ACCEPT = "Accept";
  public static final String ALLOW = "Allow";
  public static final String AUTHORIZATION = "Authorization";
  public static final String BANDWIDTH = "Bandwidth";
  public static final String BLOCKSIZE = "Blocksize";
  public static final String CACHE_CONTROL = "Cache-Control";
  public static final String CONNECTION = "Connection";
  public static final String CONTENT_BASE = "Content-Base";
  public static final String CONTENT_ENCODING = "Content-Encoding";
  public static final String CONTENT_LANGUAGE = "Content-Language";
  public static final String CONTENT_LENGTH = "Content-Length";
  public static final String CONTENT_LOCATION = "Content-Location";
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String CSEQ = "CSeq";
  public static final String DATE = "Date";
  public static final String EXPIRES = "Expires";
  public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
  public static final String PROXY_REQUIRE = "Proxy-Require";
  public static final String PUBLIC = "Public";
  public static final String RANGE = "Range";
  public static final String RTP_INFO = "RTP-Info";
  public static final String RTCP_INTERVAL = "RTCP-Interval";
  public static final String SCALE = "Scale";
  public static final String SESSION = "Session";
  public static final String SPEED = "Speed";
  public static final String SUPPORTED = "Supported";
  public static final String TIMESTAMP = "Timestamp";
  public static final String TRANSPORT = "Transport";
  public static final String USER_AGENT = "User-Agent";
  public static final String VIA = "Via";
  public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

  /** Builds {@link RtspHeaders} instances. */
  public static final class Builder {
    private final List<String> namesAndValues;

    /** Creates a new instance. */
    public Builder() {
      namesAndValues = new ArrayList<>();
    }

    /**
     * Adds a header name and header value pair.
     *
     * @param headerName The name of the header.
     * @param headerValue The value of the header.
     * @return This builder.
     */
    public Builder add(String headerName, String headerValue) {
      namesAndValues.add(headerName.trim());
      namesAndValues.add(headerValue.trim());
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

  private final ImmutableList<String> namesAndValues;

  /**
   * Gets the headers as a map, where the keys are the header names and values are the header
   * values.
   *
   * @return The headers as a map. The keys of the map have follows those that are used to build
   *     this {@link RtspHeaders} instance.
   */
  public ImmutableMap<String, String> asMap() {
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.size(); i += 2) {
      headers.put(namesAndValues.get(i), namesAndValues.get(i + 1));
    }
    return ImmutableMap.copyOf(headers);
  }

  /**
   * Returns a header value mapped to the argument, {@code null} if the header name is not recorded.
   */
  @Nullable
  public String get(String headerName) {
    for (int i = namesAndValues.size() - 2; i >= 0; i -= 2) {
      if (Ascii.equalsIgnoreCase(headerName, namesAndValues.get(i))) {
        return namesAndValues.get(i + 1);
      }
    }
    return null;
  }

  private RtspHeaders(Builder builder) {
    this.namesAndValues = ImmutableList.copyOf(builder.namesAndValues);
  }
}
