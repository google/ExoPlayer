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

import static com.google.android.exoplayer2.testutil.WebServerDispatcher.Resource.GZIP_SUPPORT_DISABLED;
import static com.google.android.exoplayer2.testutil.WebServerDispatcher.Resource.GZIP_SUPPORT_FORCED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.Pair;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
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

    /**
     * The level of gzip support offered by the server for a resource.
     *
     * <p>One of:
     *
     * <ul>
     *   <li>{@link #GZIP_SUPPORT_DISABLED}
     *   <li>{@link #GZIP_SUPPORT_ENABLED}
     *   <li>{@link #GZIP_SUPPORT_FORCED}
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({GZIP_SUPPORT_DISABLED, GZIP_SUPPORT_ENABLED, GZIP_SUPPORT_FORCED})
    private @interface GzipSupport {}

    /** The server doesn't support gzip. */
    public static final int GZIP_SUPPORT_DISABLED = 1;

    /**
     * The server supports gzip. Responses are only compressed if the request signals "gzip" is an
     * acceptable content-coding using an {@code Accept-Encoding} header.
     */
    public static final int GZIP_SUPPORT_ENABLED = 2;

    /**
     * The server supports gzip. Responses are compressed if the request contains no {@code
     * Accept-Encoding} header or one that accepts {@code "gzip"}.
     *
     * <p>RFC 2616 14.3 recommends a server use {@code "identity"} content-coding if no {@code
     * Accept-Encoding} is present, but some servers will still compress responses in this case.
     * This option mimics that behaviour.
     */
    public static final int GZIP_SUPPORT_FORCED = 3;

    /** Builder for {@link Resource}. */
    public static class Builder {
      private @MonotonicNonNull String path;
      private byte @MonotonicNonNull [] data;
      private boolean supportsRangeRequests;
      private boolean resolvesToUnknownLength;
      @GzipSupport private int gzipSupport;

      /** Constructs an instance. */
      public Builder() {
        this.gzipSupport = GZIP_SUPPORT_DISABLED;
      }

      private Builder(Resource resource) {
        this.path = resource.getPath();
        this.data = resource.getData();
        this.supportsRangeRequests = resource.supportsRangeRequests();
        this.resolvesToUnknownLength = resource.resolvesToUnknownLength();
        this.gzipSupport = resource.getGzipSupport();
      }

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
       * Sets if the server shouldn't include the resource length in header responses.
       *
       * <p>If true, responses to unbound requests won't include a Content-Length header and
       * Content-Range headers won't include the total resource length.
       *
       * @return this builder, for convenience.
       */
      public Builder resolvesToUnknownLength(boolean resolvesToUnknownLength) {
        this.resolvesToUnknownLength = resolvesToUnknownLength;
        return this;
      }

      /**
       * Sets the level of gzip support for this resource. Defaults to {@link
       * #GZIP_SUPPORT_DISABLED}.
       *
       * @return this builder, for convenience.
       */
      public Builder setGzipSupport(@GzipSupport int gzipSupport) {
        this.gzipSupport = gzipSupport;
        return this;
      }

      /** Builds the {@link Resource}. */
      public Resource build() {
        if (gzipSupport != GZIP_SUPPORT_DISABLED) {
          checkState(!supportsRangeRequests, "Can't enable compression & range requests.");
          checkState(!resolvesToUnknownLength, "Can't enable compression if length isn't known.");
        }
        return new Resource(
            checkNotNull(path),
            checkNotNull(data),
            supportsRangeRequests,
            resolvesToUnknownLength,
            gzipSupport);
      }
    }

    private final String path;
    private final byte[] data;
    private final boolean supportsRangeRequests;
    private final boolean resolvesToUnknownLength;
    @GzipSupport private final int gzipSupport;

    private Resource(
        String path,
        byte[] data,
        boolean supportsRangeRequests,
        boolean resolvesToUnknownLength,
        @GzipSupport int gzipSupport) {
      this.path = path;
      this.data = data;
      this.supportsRangeRequests = supportsRangeRequests;
      this.resolvesToUnknownLength = resolvesToUnknownLength;
      this.gzipSupport = gzipSupport;
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

    /** Returns true if the resource should resolve to an unknown length. */
    public boolean resolvesToUnknownLength() {
      return resolvesToUnknownLength;
    }

    /** Returns the level of gzip support the server should provide for this resource. */
    @GzipSupport
    public int getGzipSupport() {
      return gzipSupport;
    }

    /** Returns a new {@link Builder} initialized with the values from this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }
  }

  /** Matches an Accept-Encoding header value (format defined in RFC 2616 section 14.3). */
  private static final Pattern ACCEPT_ENCODING_PATTERN =
      Pattern.compile("\\W*(\\w+|\\*)(?:;q=(\\d+\\.?\\d*))?\\W*");

  private final ImmutableMap<String, Resource> resourcesByPath;

  /** Returns the path for a given {@link RecordedRequest}, stripping any query parameters. */
  public static String getRequestPath(RecordedRequest request) {
    return Util.splitAtFirst(request.getPath(), "\\?")[0];
  }

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
    String requestPath = getRequestPath(request);
    MockResponse response = new MockResponse();
    if (!resourcesByPath.containsKey(requestPath)) {
      return response.setResponseCode(404);
    }
    Resource resource = checkNotNull(resourcesByPath.get(requestPath));
    byte[] resourceData = resource.getData();
    if (resource.supportsRangeRequests()) {
      response.setHeader("Accept-ranges", "bytes");
    }
    @Nullable ImmutableMap<String, Float> acceptEncodingHeader = getAcceptEncodingHeader(request);
    @Nullable String preferredContentCoding;
    if (resource.getGzipSupport() == GZIP_SUPPORT_FORCED && acceptEncodingHeader == null) {
      preferredContentCoding = "gzip";
    } else {
      ImmutableList<String> supportedContentCodings =
          resource.getGzipSupport() == GZIP_SUPPORT_DISABLED
              ? ImmutableList.of("identity")
              : ImmutableList.of("gzip", "identity");
      preferredContentCoding =
          getPreferredContentCoding(acceptEncodingHeader, supportedContentCodings);
    }
    if (preferredContentCoding == null) {
      // None of the supported encodings are accepted by the client.
      return response.setResponseCode(406);
    }

    @Nullable String rangeHeader = request.getHeader("Range");
    if (!resource.supportsRangeRequests() || rangeHeader == null) {
      switch (preferredContentCoding) {
        case "gzip":
          setResponseBody(
              response, Util.gzip(resourceData), /* chunked= */ resource.resolvesToUnknownLength);
          response
              .setHeader("Content-Encoding", "gzip");
          break;
        case "identity":
          setResponseBody(response, resourceData, /* chunked= */ resource.resolvesToUnknownLength);
          response
              .setHeader("Content-Encoding", "identity");
          break;
        default:
          throw new IllegalStateException("Unexpected content coding: " + preferredContentCoding);
      }
      return response;
    }

    @Nullable
    Pair<@NullableType Integer, @NullableType Integer> range = getRangeHeader(rangeHeader);

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
                  + (resource.resolvesToUnknownLength() ? "*" : resourceData.length));
      setResponseBody(
          response,
          Arrays.copyOfRange(resourceData, start, resourceData.length),
          /* chunked= */ resource.resolvesToUnknownLength);
      return response;
    }

    // range.first and range.second are both non-null, so the range is bounded.

    if (range.second < range.first) {
      return response
          .setResponseCode(416)
          .setHeader("Content-Range", "bytes */" + resourceData.length);
    }

    int end = min(range.second + 1, resourceData.length);
    response
        .setResponseCode(206)
        .setHeader(
            "Content-Range",
            "bytes "
                + range.first
                + "-"
                + (end - 1)
                + "/"
                + (resource.resolvesToUnknownLength() ? "*" : resourceData.length));
    setResponseBody(
        response, Arrays.copyOfRange(resourceData, range.first, end), /* chunked= */ false);
    return response;
  }

  /**
   * Populates a response with the specified body.
   *
   * @param response The response whose body should be populated.
   * @param body The body data.
   * @param chunked Whether to use chunked transfer encoding. Note that if set to {@code true}, the
   *     "Content-Length" header will not be set.
   */
  private static void setResponseBody(MockResponse response, byte[] body, boolean chunked) {
    if (chunked) {
      response.setChunkedBody(new Buffer().write(body), /* maxChunkSize= */ Integer.MAX_VALUE);
    } else {
      response.setBody(new Buffer().write(body));
    }
  }

  /**
   * Parses an RFC 2616 14.3 Accept-Encoding header into a map from content-coding to qvalue.
   *
   * <p>Returns null if the header is not present.
   *
   * <p>Missing qvalues are stored in the map as -1.
   */
  @Nullable
  private static ImmutableMap<String, Float> getAcceptEncodingHeader(RecordedRequest request) {
    @Nullable List<String> headers = request.getHeaders().toMultimap().get("Accept-Encoding");
    if (headers == null) {
      return null;
    }
    String header = Joiner.on(",").join(headers);
    String[] encodings = Util.split(header, ",");
    ImmutableMap.Builder<String, Float> parsedEncodings = ImmutableMap.builder();
    for (String encoding : encodings) {
      Matcher matcher = ACCEPT_ENCODING_PATTERN.matcher(encoding);
      if (!matcher.matches()) {
        continue;
      }
      String contentCoding = checkNotNull(matcher.group(1));
      @Nullable String qvalue = matcher.group(2);
      parsedEncodings.put(contentCoding, qvalue == null ? -1f : Float.parseFloat(qvalue));
    }
    return parsedEncodings.build();
  }

  /**
   * Returns the preferred content-coding based on the (optional) Accept-Encoding header, or null if
   * none of {@code supportedContentCodings} are accepted by the client.
   *
   * <p>The selection algorithm is described in RFC 2616 section 14.3.
   *
   * @param acceptEncodingHeader The Accept-Encoding header parsed into a map from content-coding to
   *     qvalue (absent qvalues are represented by -1), or null if the header isn't present.
   * @param supportedContentCodings A list of content-codings supported by the server in order of
   *     preference.
   */
  @Nullable
  private static String getPreferredContentCoding(
      @Nullable ImmutableMap<String, Float> acceptEncodingHeader,
      List<String> supportedContentCodings) {
    if (acceptEncodingHeader == null) {
      return "identity";
    }
    if (!acceptEncodingHeader.containsKey("identity") && !acceptEncodingHeader.containsKey("*")) {
      acceptEncodingHeader =
          ImmutableMap.<String, Float>builder()
              .putAll(acceptEncodingHeader)
              .put("identity", -1f)
              .build();
    }
    float asteriskQvalue = acceptEncodingHeader.getOrDefault("*", 0f);
    @Nullable String preferredContentCoding = null;
    float preferredQvalue = Integer.MIN_VALUE;
    for (String supportedContentCoding : supportedContentCodings) {
      float qvalue = acceptEncodingHeader.getOrDefault(supportedContentCoding, 0f);
      if (!acceptEncodingHeader.containsKey(supportedContentCoding)
          && asteriskQvalue != 0
          && asteriskQvalue > preferredQvalue) {
        preferredContentCoding = supportedContentCoding;
        preferredQvalue = asteriskQvalue;
      } else if (qvalue != 0 && qvalue > preferredQvalue) {
        preferredContentCoding = supportedContentCoding;
        preferredQvalue = qvalue;
      }
    }

    return preferredContentCoding;
  }

  /**
   * Parses an RFC 7233 Range header to its component parts. Returns null if the Range is invalid.
   */
  @Nullable
  private static Pair<@NullableType Integer, @NullableType Integer> getRangeHeader(
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
