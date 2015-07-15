/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.util.Predicate;
import com.google.android.exoplayer.util.Util;

import android.text.TextUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An HTTP specific extension to {@link UriDataSource}.
 */
public interface HttpDataSource extends UriDataSource {

  /**
   * A {@link Predicate} that rejects content types often used for pay-walls.
   */
  public static final Predicate<String> REJECT_PAYWALL_TYPES = new Predicate<String>() {

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
  public static class HttpDataSourceException extends IOException {

    /*
     * The {@link DataSpec} associated with the current connection.
     */
    public final DataSpec dataSpec;

    public HttpDataSourceException(DataSpec dataSpec) {
      super();
      this.dataSpec = dataSpec;
    }

    public HttpDataSourceException(String message, DataSpec dataSpec) {
      super(message);
      this.dataSpec = dataSpec;
    }

    public HttpDataSourceException(IOException cause, DataSpec dataSpec) {
      super(cause);
      this.dataSpec = dataSpec;
    }

    public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec) {
      super(message, cause);
      this.dataSpec = dataSpec;
    }

  }

  /**
   * Thrown when the content type is invalid.
   */
  public static final class InvalidContentTypeException extends HttpDataSourceException {

    public final String contentType;

    public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
      super("Invalid content type: " + contentType, dataSpec);
      this.contentType = contentType;
    }

  }

  /**
   * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
   */
  public static final class InvalidResponseCodeException extends HttpDataSourceException {

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
      super("Response code: " + responseCode, dataSpec);
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

}
