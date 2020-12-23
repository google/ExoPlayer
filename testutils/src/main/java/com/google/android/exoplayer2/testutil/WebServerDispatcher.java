/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link Dispatcher} for {@link okhttp3.mockwebserver.MockWebServer} that allows per-path
 * customisation of the static data served.
 */
public class WebServerDispatcher extends Dispatcher {

  /** A resource served by {@link WebServerDispatcher}. */
  public static class Resource {

    /** Builder for {@link Resource}. */
    public static class Builder {
      private @MonotonicNonNull String path;
      private byte @MonotonicNonNull [] data;
      private boolean supportsRangeRequests;
      private boolean resolvesToUnknownLength;

      /**
       * Sets the path this data should be served at. This is required.
       *
       * @return this builder, for convenience.
       */
      public Builder setPath(String path) {
        this.path = path.startsWith("/") ? path : "/" + path;
        return this;
      }

      /**
       * Sets the data served by this resource. This is required.
       *
       * @return this builder, for convenience.
       */
      public Builder setData(byte[] data) {
        this.data = data;
        return this;
      }

      /**
       * Sets if RFC 7233 range requests should be supported for this resource. Defaults to false.
       *
       * @return this builder, for convenience.
       */
      public Builder supportsRangeRequests(boolean supportsRangeRequests) {
        this.supportsRangeRequests = supportsRangeRequests;
        return this;
      }

      /**
       * Sets if the resource should resolve to an unknown length. Defaults to false.
       *
       * @return this builder, for convenience.
       */
      public Builder resolvesToUnknownLength(boolean resolvesToUnknownLength) {
        this.resolvesToUnknownLength = resolvesToUnknownLength;
        return this;
      }

      /** Builds the {@link Resource}. */
      public Resource build() {
        return new Resource(
            checkNotNull(path), checkNotNull(data), supportsRangeRequests, resolvesToUnknownLength);
      }
    }

    private final String path;
    private final byte[] data;
    private final boolean supportsRangeRequests;
    private final boolean resolvesToUnknownLength;

    private Resource(
        String path, byte[] data, boolean supportsRangeRequests, boolean resolvesToUnknownLength) {
      this.path = path;
      this.data = data;
      this.supportsRangeRequests = supportsRangeRequests;
      this.resolvesToUnknownLength = resolvesToUnknownLength;
    }

    /** Returns the path this resource is available at. */
    public String getPath() {
      return path;
    }

    /** Returns the data served by this resource. */
    public byte[] getData() {
      return data.clone();
    }

    /** Returns true if RFC 7233 range requests should be supported for this resource. */
    public boolean supportsRangeRequests() {
      return supportsRangeRequests;
    }

    /**
     * Returns true if the server shouldn't include the resource length in header responses.
     *
     * <p>Responses to unbound requests won't include a Content-Length header, and Content-Range
     * headers won't include the total resource length.
     */
    public boolean resolvesToUnknownLength() {
      return resolvesToUnknownLength;
    }
  }

  private final ImmutableMap<String, Resource> resourcesByPath;

  /**
   * Constructs a dispatcher that handles requests based the provided {@link Resource} instances.
   */
  public static WebServerDispatcher forResources(Iterable<Resource> resources) {
    return new WebServerDispatcher(Maps.uniqueIndex(resources, Resource::getPath));
  }

  private WebServerDispatcher(ImmutableMap<String, Resource> resourcesByPath) {
    this.resourcesByPath = resourcesByPath;
  }

  @Override
  public MockResponse dispatch(RecordedRequest request) {
    MockResponse response = new MockResponse();
    if (!resourcesByPath.containsKey(request.getPath())) {
      return response.setResponseCode(404);
    }
    Resource resource = checkNotNull(resourcesByPath.get(request.getPath()));
    byte[] resourceData = resource.getData();
    if (resource.supportsRangeRequests()) {
      response.setHeader("Accept-ranges", "bytes");
    }
    String rangeHeader = request.getHeader("Range");
    if (!resource.supportsRangeRequests() || rangeHeader == null) {
      response.setBody(new Buffer().write(resourceData));
      if (resource.resolvesToUnknownLength()) {
        response.setHeader("Content-Length", "");
      }
      return response;
    }

    @Nullable
    Pair<@NullableType Integer, @NullableType Integer> range = parseRangeHeader(rangeHeader);

    if (range == null || (range.first != null && range.first >= resourceData.length)) {
      return response
          .setResponseCode(416)
          .setHeader("Content-Range", "bytes */" + resourceData.length);
    }

    if (range.first == null || range.second == null) {
      int start;
      if (range.first == null) {
        // We're handling a suffix range
        if (resource.resolvesToUnknownLength()) {
          // Can't return the suffix of an unknown-length resource.
          return response
              .setResponseCode(416)
              .setHeader("Content-Range", "bytes */" + resourceData.length);
        }
        start = max(0, resourceData.length - checkNotNull(range.second));
      } else {
        // We're handling an unbounded range
        start = checkNotNull(range.first);
      }
      response
          .setResponseCode(206)
          .setHeader(
              "Content-Range",
              "bytes "
                  + start
                  + "-"
                  + (resourceData.length - 1)
                  + "/"
                  + (resource.resolvesToUnknownLength() ? "*" : resourceData.length))
          .setBody(new Buffer().write(resourceData, start, resourceData.length - start));
      if (resource.resolvesToUnknownLength()) {
        response.setHeader("Content-Length", "");
      }
      return response;
    }

    // range.first and range.second are both non-null, so the range is bounded.

    if (range.second < range.first) {
      return response
          .setResponseCode(416)
          .setHeader("Content-Range", "bytes */" + resourceData.length);
    }

    int end = min(range.second + 1, resourceData.length);
    return response
        .setResponseCode(206)
        .setHeader(
            "Content-Range",
            "bytes "
                + range.first
                + "-"
                + (end - 1)
                + "/"
                + (resource.resolvesToUnknownLength() ? "*" : resourceData.length))
        .setBody(new Buffer().write(resourceData, range.first, end - range.first));
  }

  /**
   * Parses an RFC 7233 Range header to its component parts. Returns null if the Range is invalid.
   */
  @Nullable
  private static Pair<@NullableType Integer, @NullableType Integer> parseRangeHeader(
      String rangeHeader) {
    Pattern rangePattern = Pattern.compile("bytes=(\\d*)-(\\d*)");
    Matcher rangeMatcher = rangePattern.matcher(rangeHeader);
    if (!rangeMatcher.matches() || rangeHeader.contains(",")) {
      // This implementation only supports byte ranges and doesn't support multiple ranges.
      return null;
    }
    String first = checkNotNull(rangeMatcher.group(1));
    String second = checkNotNull(rangeMatcher.group(2));

    Pair<@NullableType Integer, @NullableType Integer> result =
        Pair.create(
            first.isEmpty() ? null : Integer.parseInt(first),
            second.isEmpty() ? null : Integer.parseInt(second));
    if (result.first != null && result.second != null && result.second < result.first) {
      return null;
    }
    return result;
  }
}
