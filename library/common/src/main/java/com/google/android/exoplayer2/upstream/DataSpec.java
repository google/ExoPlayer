/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a region of data.
 */
public final class DataSpec {

  /** Builds {@link DataSpec} instances. */
  public static final class Builder {

    @Nullable private Uri uri;
    private long uriPositionOffset;
    @HttpMethod private int httpMethod;
    @Nullable private byte[] httpBody;
    private Map<String, String> httpRequestHeaders;
    private long position;
    private long length;
    @Nullable private String key;
    @Flags private int flags;

    /** Creates a new instance with default values. */
    public Builder() {
      httpMethod = HTTP_METHOD_GET;
      httpRequestHeaders = Collections.emptyMap();
      length = C.LENGTH_UNSET;
    }

    /**
     * Creates a new instance to build upon the provided {@link DataSpec}.
     *
     * @param dataSpec The {@link DataSpec} to build upon.
     */
    private Builder(DataSpec dataSpec) {
      uri = dataSpec.uri;
      uriPositionOffset = dataSpec.uriPositionOffset;
      httpMethod = dataSpec.httpMethod;
      httpBody = dataSpec.httpBody;
      httpRequestHeaders = dataSpec.httpRequestHeaders;
      position = dataSpec.position;
      length = dataSpec.length;
      key = dataSpec.key;
      flags = dataSpec.flags;
    }

    /**
     * Sets {@link DataSpec#uri}. Must be called before {@link #build()}.
     *
     * @param uri The {@link DataSpec#uri}.
     * @return The builder.
     */
    public Builder setUri(Uri uri) {
      this.uri = uri;
      return this;
    }

    /**
     * Sets the {@link DataSpec#uriPositionOffset}. The default value is 0.
     *
     * @param uriPositionOffset The {@link DataSpec#uriPositionOffset}.
     * @return The builder.
     */
    public Builder setUriPositionOffset(long uriPositionOffset) {
      this.uriPositionOffset = uriPositionOffset;
      return this;
    }

    /**
     * Sets {@link DataSpec#httpMethod}. The default value is {@link #HTTP_METHOD_GET}.
     *
     * @param httpMethod The {@link DataSpec#httpMethod}.
     * @return The builder.
     */
    public Builder setHttpMethod(@HttpMethod int httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    /**
     * Sets {@link DataSpec#httpBody}. The default value is {@code null}.
     *
     * @param httpBody The {@link DataSpec#httpBody}.
     * @return The builder.
     */
    public Builder setHttpBody(@Nullable byte[] httpBody) {
      this.httpBody = httpBody;
      return this;
    }

    /**
     * Sets the {@link DataSpec#httpRequestHeaders}. The default value is an empty map.
     *
     * @param httpRequestHeaders The {@link DataSpec#httpRequestHeaders}.
     * @return The builder.
     */
    public Builder setHttpRequestHeaders(Map<String, String> httpRequestHeaders) {
      this.httpRequestHeaders = httpRequestHeaders;
      return this;
    }

    /**
     * Sets the {@link DataSpec#position}. The default value is 0.
     *
     * @param position The {@link DataSpec#position}.
     * @return The builder.
     */
    public Builder setPosition(long position) {
      this.position = position;
      return this;
    }

    /**
     * Sets the {@link DataSpec#length}. The default value is {@link C#LENGTH_UNSET}.
     *
     * @param length The {@link DataSpec#length}.
     * @return The builder.
     */
    public Builder setLength(long length) {
      this.length = length;
      return this;
    }

    /**
     * Sets the {@link DataSpec#key}. The default value is {@code null}.
     *
     * @param key The {@link DataSpec#key}.
     * @return The builder.
     */
    public Builder setKey(@Nullable String key) {
      this.key = key;
      return this;
    }

    /**
     * Sets the {@link DataSpec#flags}. The default value is 0.
     *
     * @param flags The {@link DataSpec#flags}.
     * @return The builder.
     */
    public Builder setFlags(@Flags int flags) {
      this.flags = flags;
      return this;
    }

    /**
     * Builds a {@link DataSpec} with the builder's current values.
     *
     * @return The build {@link DataSpec}.
     * @throws IllegalStateException If {@link #setUri(Uri)} has not been called.
     */
    public DataSpec build() {
      Assertions.checkStateNotNull(uri, "The uri must be set.");
      return new DataSpec(
          uri,
          uriPositionOffset,
          httpMethod,
          httpBody,
          httpRequestHeaders,
          position,
          length,
          key,
          flags);
    }
  }

  /**
   * The flags that apply to any request for data. Possible flag values are {@link
   * #FLAG_ALLOW_GZIP}, {@link #FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN}, {@link
   * #FLAG_ALLOW_CACHE_FRAGMENTATION}, and {@link #FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {
        FLAG_ALLOW_GZIP,
        FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN,
        FLAG_ALLOW_CACHE_FRAGMENTATION,
        FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED
      })
  public @interface Flags {}
  /**
   * Allows an underlying network stack to request that the server use gzip compression.
   *
   * <p>Should not typically be set if the data being requested is already compressed (e.g. most
   * audio and video requests). May be set when requesting other data.
   *
   * <p>When a {@link DataSource} is used to request data with this flag set, and if the {@link
   * DataSource} does make a network request, then the value returned from {@link
   * DataSource#open(DataSpec)} will typically be {@link C#LENGTH_UNSET}. The data read from {@link
   * DataSource#read(byte[], int, int)} will be the decompressed data.
   */
  public static final int FLAG_ALLOW_GZIP = 1;
  /** Prevents caching if the length cannot be resolved when the {@link DataSource} is opened. */
  public static final int FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN = 1 << 1;
  /**
   * Allows fragmentation of this request into multiple cache files, meaning a cache eviction policy
   * will be able to evict individual fragments of the data. Depending on the cache implementation,
   * setting this flag may also enable more concurrent access to the data (e.g. reading one fragment
   * whilst writing another).
   */
  public static final int FLAG_ALLOW_CACHE_FRAGMENTATION = 1 << 2;
  /**
   * Indicates there are known external factors that might prevent the data from being loaded at
   * full network speed (e.g. server throttling or unfinished live media chunks).
   */
  public static final int FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED = 1 << 3;

  /**
   * HTTP methods supported by ExoPlayer {@link HttpDataSource}s. One of {@link #HTTP_METHOD_GET},
   * {@link #HTTP_METHOD_POST} or {@link #HTTP_METHOD_HEAD}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({HTTP_METHOD_GET, HTTP_METHOD_POST, HTTP_METHOD_HEAD})
  public @interface HttpMethod {}
  /** HTTP GET method. */
  public static final int HTTP_METHOD_GET = 1;
  /** HTTP POST method. */
  public static final int HTTP_METHOD_POST = 2;
  /** HTTP HEAD method. */
  public static final int HTTP_METHOD_HEAD = 3;

  /**
   * Returns an uppercase HTTP method name (e.g., "GET", "POST", "HEAD") corresponding to the given
   * {@link HttpMethod}.
   */
  public static String getStringForHttpMethod(@HttpMethod int httpMethod) {
    switch (httpMethod) {
      case HTTP_METHOD_GET:
        return "GET";
      case HTTP_METHOD_POST:
        return "POST";
      case HTTP_METHOD_HEAD:
        return "HEAD";
      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  /** The {@link Uri} from which data should be read. */
  public final Uri uri;

  /**
   * The offset of the data located at {@link #uri} within an original resource.
   *
   * <p>Equal to 0 unless {@link #uri} provides access to a subset of an original resource. As an
   * example, consider a resource that can be requested over the network and is 1000 bytes long. If
   * {@link #uri} points to a local file that contains just bytes [200-300], then this field will be
   * set to {@code 200}.
   *
   * <p>This field can be ignored except for in specific circumstances where the absolute position
   * in the original resource is required in a {@link DataSource} chain. One example is when a
   * {@link DataSource} needs to decrypt the content as it's read. In this case the absolute
   * position in the original resource is typically needed to correctly initialize the decryption
   * algorithm.
   */
  public final long uriPositionOffset;

  /**
   * The HTTP method to use when requesting the data. This value will be ignored by non-HTTP {@link
   * DataSource} implementations.
   */
  @HttpMethod public final int httpMethod;

  /**
   * The HTTP request body, null otherwise. If the body is non-null, then {@code httpBody.length}
   * will be non-zero.
   */
  @Nullable public final byte[] httpBody;

  /** Immutable map containing the headers to use in HTTP requests. */
  public final Map<String, String> httpRequestHeaders;

  /**
   * The absolute position of the data in the full stream.
   *
   * @deprecated Use {@link #position} except for specific use cases where the absolute position
   *     within the original resource is required within a {@link DataSource} chain. Where the
   *     absolute position is required, use {@code uriPositionOffset + position}.
   */
  @Deprecated public final long absoluteStreamPosition;

  /** The position of the data when read from {@link #uri}. */
  public final long position;

  /**
   * The length of the data, or {@link C#LENGTH_UNSET}.
   */
  public final long length;

  /**
   * A key that uniquely identifies the original stream. Used for cache indexing. May be null if the
   * data spec is not intended to be used in conjunction with a cache.
   */
  @Nullable public final String key;

  /** Request {@link Flags flags}. */
  @Flags public final int flags;

  /**
   * Constructs an instance.
   *
   * @param uri {@link #uri}.
   */
  public DataSpec(Uri uri) {
    this(uri, /* flags= */ 0);
  }

  /**
   * Constructs an instance.
   *
   * @param uri {@link #uri}.
   * @param flags {@link #flags}.
   */
  public DataSpec(Uri uri, @Flags int flags) {
    this(uri, /* position= */ 0, C.LENGTH_UNSET, /* key= */ null, flags);
  }

  /**
   * Constructs an instance.
   *
   * @param uri {@link #uri}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   */
  public DataSpec(Uri uri, long position, long length, @Nullable String key) {
    this(uri, position, position, length, key, /* flags= */ 0);
  }

  /**
   * Constructs an instance.
   *
   * @param uri {@link #uri}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   */
  public DataSpec(Uri uri, long position, long length, @Nullable String key, @Flags int flags) {
    this(uri, position, position, length, key, flags);
  }

  /**
   * Constructs an instance.
   *
   * @param uri {@link #uri}.
   * @param position {@link #position}, equal to {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   * @param httpRequestHeaders {@link #httpRequestHeaders}
   */
  public DataSpec(
      Uri uri,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags,
      Map<String, String> httpRequestHeaders) {
    this(
        uri,
        HTTP_METHOD_GET,
        /* httpBody= */ null,
        position,
        position,
        length,
        key,
        flags,
        httpRequestHeaders);
  }

  /**
   * Constructs an instance where {@link #uriPositionOffset} may be non-zero.
   *
   * @param uri {@link #uri}.
   * @param absoluteStreamPosition The sum of {@link #uriPositionOffset} and {@link #position}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   */
  public DataSpec(
      Uri uri,
      long absoluteStreamPosition,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags) {
    this(uri, /* postBody= */ null, absoluteStreamPosition, position, length, key, flags);
  }

  /**
   * Construct a instance where {@link #uriPositionOffset} may be non-zero. The {@link #httpMethod}
   * is inferred from {@code postBody}. If {@code postBody} is non-null then {@link #httpMethod} is
   * set to {@link #HTTP_METHOD_POST}. If {@code postBody} is null then {@link #httpMethod} is set
   * to {@link #HTTP_METHOD_GET}.
   *
   * @param uri {@link #uri}.
   * @param postBody {@link #httpBody} The body of the HTTP request, which is also used to infer the
   *     {@link #httpMethod}.
   * @param absoluteStreamPosition The sum of {@link #uriPositionOffset} and {@link #position}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   */
  public DataSpec(
      Uri uri,
      @Nullable byte[] postBody,
      long absoluteStreamPosition,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags) {
    this(
        uri,
        /* httpMethod= */ postBody != null ? HTTP_METHOD_POST : HTTP_METHOD_GET,
        /* httpBody= */ postBody,
        absoluteStreamPosition,
        position,
        length,
        key,
        flags);
  }

  /**
   * Construct a instance where {@link #uriPositionOffset} may be non-zero.
   *
   * @param uri {@link #uri}.
   * @param httpMethod {@link #httpMethod}.
   * @param httpBody {@link #httpBody}.
   * @param absoluteStreamPosition The sum of {@link #uriPositionOffset} and {@link #position}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   */
  public DataSpec(
      Uri uri,
      @HttpMethod int httpMethod,
      @Nullable byte[] httpBody,
      long absoluteStreamPosition,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags) {
    this(
        uri,
        httpMethod,
        httpBody,
        absoluteStreamPosition,
        position,
        length,
        key,
        flags,
        /* httpRequestHeaders= */ Collections.emptyMap());
  }

  /**
   * Construct a instance where {@link #uriPositionOffset} may be non-zero.
   *
   * @param uri {@link #uri}.
   * @param httpMethod {@link #httpMethod}.
   * @param httpBody {@link #httpBody}.
   * @param absoluteStreamPosition The sum of {@link #uriPositionOffset} and {@link #position}.
   * @param position {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param flags {@link #flags}.
   * @param httpRequestHeaders {@link #httpRequestHeaders}.
   */
  public DataSpec(
      Uri uri,
      @HttpMethod int httpMethod,
      @Nullable byte[] httpBody,
      long absoluteStreamPosition,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags,
      Map<String, String> httpRequestHeaders) {
    this(
        uri,
        /* uriPositionOffset= */ absoluteStreamPosition - position,
        httpMethod,
        httpBody,
        httpRequestHeaders,
        position,
        length,
        key,
        flags);
  }

  @SuppressWarnings("deprecation")
  private DataSpec(
      Uri uri,
      long uriPositionOffset,
      @HttpMethod int httpMethod,
      @Nullable byte[] httpBody,
      Map<String, String> httpRequestHeaders,
      long position,
      long length,
      @Nullable String key,
      @Flags int flags) {
    // TODO: Replace this assertion with a stricter one checking "uriPositionOffset >= 0", after
    // validating there are no violations in ExoPlayer and 1P apps.
    Assertions.checkArgument(uriPositionOffset + position >= 0);
    Assertions.checkArgument(position >= 0);
    Assertions.checkArgument(length > 0 || length == C.LENGTH_UNSET);
    this.uri = uri;
    this.uriPositionOffset = uriPositionOffset;
    this.httpMethod = httpMethod;
    this.httpBody = httpBody != null && httpBody.length != 0 ? httpBody : null;
    this.httpRequestHeaders = Collections.unmodifiableMap(new HashMap<>(httpRequestHeaders));
    this.position = position;
    this.absoluteStreamPosition = uriPositionOffset + position;
    this.length = length;
    this.key = key;
    this.flags = flags;
  }

  /**
   * Returns whether the given flag is set.
   *
   * @param flag Flag to be checked if it is set.
   */
  public boolean isFlagSet(@Flags int flag) {
    return (this.flags & flag) == flag;
  }

  /**
   * Returns the uppercase HTTP method name (e.g., "GET", "POST", "HEAD") corresponding to the
   * {@link #httpMethod}.
   */
  public final String getHttpMethodString() {
    return getStringForHttpMethod(httpMethod);
  }

  /** Returns a {@link DataSpec.Builder} initialized with the values of this instance. */
  public DataSpec.Builder buildUpon() {
    return new Builder(this);
  }

  /**
   * Returns a data spec that represents a subrange of the data defined by this DataSpec. The
   * subrange includes data from the offset up to the end of this DataSpec.
   *
   * @param offset The offset of the subrange.
   * @return A data spec that represents a subrange of the data defined by this DataSpec.
   */
  public DataSpec subrange(long offset) {
    return subrange(offset, length == C.LENGTH_UNSET ? C.LENGTH_UNSET : length - offset);
  }

  /**
   * Returns a data spec that represents a subrange of the data defined by this DataSpec.
   *
   * @param offset The offset of the subrange.
   * @param length The length of the subrange.
   * @return A data spec that represents a subrange of the data defined by this DataSpec.
   */
  public DataSpec subrange(long offset, long length) {
    if (offset == 0 && this.length == length) {
      return this;
    } else {
      return new DataSpec(
          uri,
          uriPositionOffset,
          httpMethod,
          httpBody,
          httpRequestHeaders,
          position + offset,
          length,
          key,
          flags);
    }
  }

  /**
   * Returns a copy of this data spec with the specified Uri.
   *
   * @param uri The new source {@link Uri}.
   * @return The copied data spec with the specified Uri.
   */
  public DataSpec withUri(Uri uri) {
    return new DataSpec(
        uri,
        uriPositionOffset,
        httpMethod,
        httpBody,
        httpRequestHeaders,
        position,
        length,
        key,
        flags);
  }

  /**
   * Returns a copy of this data spec with the specified HTTP request headers. Headers already in
   * the data spec are not copied to the new instance.
   *
   * @param httpRequestHeaders The HTTP request headers.
   * @return The copied data spec with the specified HTTP request headers.
   */
  public DataSpec withRequestHeaders(Map<String, String> httpRequestHeaders) {
    return new DataSpec(
        uri,
        uriPositionOffset,
        httpMethod,
        httpBody,
        httpRequestHeaders,
        position,
        length,
        key,
        flags);
  }

  /**
   * Returns a copy this data spec with additional HTTP request headers. Headers in {@code
   * additionalHttpRequestHeaders} will overwrite any headers already in the data spec that have the
   * same keys.
   *
   * @param additionalHttpRequestHeaders The additional HTTP request headers.
   * @return The copied data spec with the additional HTTP request headers.
   */
  public DataSpec withAdditionalHeaders(Map<String, String> additionalHttpRequestHeaders) {
    Map<String, String> httpRequestHeaders = new HashMap<>(this.httpRequestHeaders);
    httpRequestHeaders.putAll(additionalHttpRequestHeaders);
    return new DataSpec(
        uri,
        uriPositionOffset,
        httpMethod,
        httpBody,
        httpRequestHeaders,
        position,
        length,
        key,
        flags);
  }

  @Override
  public String toString() {
    return "DataSpec["
        + getHttpMethodString()
        + " "
        + uri
        + ", "
        + position
        + ", "
        + length
        + ", "
        + key
        + ", "
        + flags
        + "]";
  }
}
