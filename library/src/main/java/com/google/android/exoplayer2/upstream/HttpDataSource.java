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

import android.support.annotation.IntDef;
import android.text.TextUtils;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An HTTP {@link DataSource}.
 */
public interface HttpDataSource extends DataSource {

  /**
   * A factory for {@link HttpDataSource} instances.
   */
  interface Factory extends DataSource.Factory {

    @Override
    HttpDataSource createDataSource();

    /**
     * Sets a default request header field for {@link HttpDataSource} instances subsequently
     * created by the factory. Previously created instances are not affected.
     *
     * @param name The name of the header field.
     * @param value The value of the field.
     */
    void setDefaultRequestProperty(String name, String value);

    /**
     * Clears a default request header field for {@link HttpDataSource} instances subsequently
     * created by the factory. Previously created instances are not affected.
     *
     * @param name The name of the header field.
     */
    void clearDefaultRequestProperty(String name);

    /**
     * Clears all default request header fields for all {@link HttpDataSource} instances
     * subsequently created by the factory.  Previously created instances are not affected.
     */
    void clearAllDefaultRequestProperties();

  }

  /**
   * Base implementation of {@link Factory} that sets default request properties.
   */
  abstract class BaseFactory implements Factory {

    private final HashMap<String, String> requestProperties;

    public BaseFactory() {
      requestProperties = new HashMap<>();
    }

    @Override
    public final HttpDataSource createDataSource() {
      HttpDataSource dataSource = createDataSourceInternal();
      synchronized (requestProperties) {
        for (Map.Entry<String, String> property : requestProperties.entrySet()) {
          dataSource.setRequestProperty(property.getKey(), property.getValue());
        }
      }
      return dataSource;
    }

    @Override
    public final void setDefaultRequestProperty(String name, String value) {
      Assertions.checkNotNull(name);
      Assertions.checkNotNull(value);
      synchronized (requestProperties) {
        requestProperties.put(name, value);
      }
    }

    @Override
    public final void clearDefaultRequestProperty(String name) {
      Assertions.checkNotNull(name);
      synchronized (requestProperties) {
        requestProperties.remove(name);
      }
    }

    @Override
    public final void clearAllDefaultRequestProperties() {
      synchronized (requestProperties) {
        requestProperties.clear();
      }
    }

    /**
     * Called by {@link #createDataSource()} to create a {@link HttpDataSource} instance without
     * default request properties set. Default request properties will be set by
     * {@link #createDataSource()} before the instance is returned.
     *
     * @return A {@link HttpDataSource} instance without default request properties set.
     */
    protected abstract HttpDataSource createDataSourceInternal();

  }

  /**
   * A {@link Predicate} that rejects content types often used for pay-walls.
   */
  Predicate<String> REJECT_PAYWALL_TYPES = new Predicate<String>() {

    @Override
    public boolean evaluate(String contentType) {
      contentType = Util.toLowerInvariant(contentType);
      return !TextUtils.isEmpty(contentType)
          && (!contentType.contains("text") || contentType.contains("text/vtt"))
          && !contentType.contains("html") && !contentType.contains("xml");
    }

  };

  /**
   * Thrown when an error is encountered when trying to read from a {@link HttpDataSource}.
   */
  class HttpDataSourceException extends IOException {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
    public @interface Type {}
    public static final int TYPE_OPEN = 1;
    public static final int TYPE_READ = 2;
    public static final int TYPE_CLOSE = 3;

    @Type
    public final int type;

    /**
     * The {@link DataSpec} associated with the current connection.
     */
    public final DataSpec dataSpec;

    public HttpDataSourceException(DataSpec dataSpec, @Type int type) {
      super();
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, DataSpec dataSpec, @Type int type) {
      super(message);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(IOException cause, DataSpec dataSpec, @Type int type) {
      super(cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

    public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec,
        @Type int type) {
      super(message, cause);
      this.dataSpec = dataSpec;
      this.type = type;
    }

  }

  /**
   * Thrown when the content type is invalid.
   */
  final class InvalidContentTypeException extends HttpDataSourceException {

    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super("Invalid content type: " + contentType, dataSpec, TYPE_OPEN);
      this.contentType = contentType;
    }

  }

  /**
   * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
   */
  final class InvalidResponseCodeException extends HttpDataSourceException {

    /**
     * The response code that was outside of the 2xx range.
     */
    public final int responseCode;

    /**
     * An unmodifiable map of the response header fields and values.
     */
    public final Map<String, List<String>> headerFields;

    public InvalidResponseCodeException(int responseCode, Map<String, List<String>> headerFields,
        DataSpec dataSpec) {
      super("Response code: " + responseCode, dataSpec, TYPE_OPEN);
      this.responseCode = responseCode;
      this.headerFields = headerFields;
    }

  }

  @Override
  long open(DataSpec dataSpec) throws HttpDataSourceException;

  @Override
  void close() throws HttpDataSourceException;

  @Override
  int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException;

  /**
   * Sets the value of a request header field. The value will be used for subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  void setRequestProperty(String name, String value);

  /**
   * Clears the value of a request header field. The change will apply to subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   */
  void clearRequestProperty(String name);

  /**
   * Clears all request header fields that were set by {@link #setRequestProperty(String, String)}.
   */
  void clearAllRequestProperties();

  /**
   * Returns the headers provided in the response, or {@code null} if response headers are
   * unavailable.
   */
  Map<String, List<String>> getResponseHeaders();

}
