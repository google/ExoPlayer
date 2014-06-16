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

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Predicate;
import com.google.android.exoplayer.util.Util;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An http {@link DataSource}.
 */
public class HttpDataSource implements DataSource {

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
   * Thrown when an error is encountered when trying to read from HTTP data source.
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

  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private static final String TAG = "HttpDataSource";
  private static final Pattern CONTENT_RANGE_HEADER =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");

  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final String userAgent;
  private final Predicate<String> contentTypePredicate;
  private final HashMap<String, String> requestProperties;
  private final TransferListener listener;

  private DataSpec dataSpec;
  private HttpURLConnection connection;
  private InputStream inputStream;
  private boolean opened;

  private long dataLength;
  private long bytesRead;

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is
   *     rejected by the predicate then a {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   */
  public HttpDataSource(String userAgent, Predicate<String> contentTypePredicate) {
    this(userAgent, contentTypePredicate, null);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is
   *     rejected by the predicate then a {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param listener An optional listener.
   */
  public HttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
      TransferListener listener) {
    this(userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is
   *     rejected by the predicate then a {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted
   *     as an infinite timeout.
   */
  public HttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
      TransferListener listener, int connectTimeoutMillis, int readTimeoutMillis) {
    this.userAgent = Assertions.checkNotEmpty(userAgent);
    this.contentTypePredicate = contentTypePredicate;
    this.listener = listener;
    this.requestProperties = new HashMap<String, String>();
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  /**
   * Sets the value of a request header field. The value will be used for subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  public void setRequestProperty(String name, String value) {
    Assertions.checkNotNull(name);
    Assertions.checkNotNull(value);
    synchronized (requestProperties) {
      requestProperties.put(name, value);
    }
  }

  /**
   * Clears the value of a request header field. The change will apply to subsequent connections
   * established by the source.
   *
   * @param name The name of the header field.
   */
  public void clearRequestProperty(String name) {
    Assertions.checkNotNull(name);
    synchronized (requestProperties) {
      requestProperties.remove(name);
    }
  }

  /**
   * Clears all request header fields that were set by {@link #setRequestProperty(String, String)}.
   */
  public void clearAllRequestProperties() {
    synchronized (requestProperties) {
      requestProperties.clear();
    }
  }

  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    this.dataSpec = dataSpec;
    this.bytesRead = 0;
    try {
      connection = makeConnection(dataSpec);
    } catch (IOException e) {
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec);
    }

    // Check for a valid response code.
    int responseCode;
    try {
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec);
    }
    if (responseCode < 200 || responseCode > 299) {
      Map<String, List<String>> headers = connection.getHeaderFields();
      closeConnection();
      throw new InvalidResponseCodeException(responseCode, headers, dataSpec);
    }

    // Check for a valid content type.
    String contentType = connection.getContentType();
    if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
      closeConnection();
      throw new InvalidContentTypeException(contentType, dataSpec);
    }

    long contentLength = getContentLength(connection);
    dataLength = dataSpec.length == DataSpec.LENGTH_UNBOUNDED ? contentLength : dataSpec.length;
    if (dataLength == DataSpec.LENGTH_UNBOUNDED) {
      // The DataSpec specified unbounded length and we failed to resolve a length from the
      // response headers.
      throw new HttpDataSourceException(
          new UnexpectedLengthException(DataSpec.LENGTH_UNBOUNDED, DataSpec.LENGTH_UNBOUNDED),
          dataSpec);
    }

    if (dataSpec.length != DataSpec.LENGTH_UNBOUNDED && contentLength != DataSpec.LENGTH_UNBOUNDED
        && contentLength != dataSpec.length) {
      // The DataSpec specified a length and we resolved a length from the response headers, but
      // the two lengths do not match.
      closeConnection();
      throw new HttpDataSourceException(
          new UnexpectedLengthException(dataSpec.length, contentLength), dataSpec);
    }

    try {
      inputStream = connection.getInputStream();
    } catch (IOException e) {
      closeConnection();
      throw new HttpDataSourceException(e, dataSpec);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }

    return dataLength;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    int read = 0;
    try {
      read = inputStream.read(buffer, offset, readLength);
    } catch (IOException e) {
      throw new HttpDataSourceException(e, dataSpec);
    }

    if (read > 0) {
      bytesRead += read;
      if (listener != null) {
        listener.onBytesTransferred(read);
      }
    } else if (dataLength != bytesRead) {
      // Check for cases where the server closed the connection having not sent the correct amount
      // of data.
      throw new HttpDataSourceException(new UnexpectedLengthException(dataLength, bytesRead),
          dataSpec);
    }

    return read;
  }

  @Override
  public void close() throws HttpDataSourceException {
    try {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          throw new HttpDataSourceException(e, dataSpec);
        }
        inputStream = null;
      }
    } finally {
      if (opened) {
        opened = false;
        if (listener != null) {
          listener.onTransferEnd();
        }
        closeConnection();
      }
    }
  }

  private void closeConnection() {
    if (connection != null) {
      connection.disconnect();
      connection = null;
    }
  }

  /**
   * Returns the current connection, or null if the source is not currently opened.
   *
   * @return The current open connection, or null.
   */
  protected final HttpURLConnection getConnection() {
    return connection;
  }

  /**
   * Returns the number of bytes that have been read since the most recent call to
   * {@link #open(DataSpec)}.
   *
   * @return The number of bytes read.
   */
  protected final long bytesRead() {
    return bytesRead;
  }

  /**
   * Returns the number of bytes that are still to be read for the current {@link DataSpec}. This
   * value is equivalent to {@code dataSpec.length - bytesRead()}, where dataSpec is the
   * {@link DataSpec} that was passed to the most recent call of {@link #open(DataSpec)}.
   *
   * @return The number of bytes remaining.
   */
  protected final long bytesRemaining() {
    return dataLength - bytesRead;
  }

  private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
    URL url = new URL(dataSpec.uri.toString());
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(connectTimeoutMillis);
    connection.setReadTimeout(readTimeoutMillis);
    connection.setDoOutput(false);
    synchronized (requestProperties) {
      for (HashMap.Entry<String, String> property : requestProperties.entrySet()) {
        connection.setRequestProperty(property.getKey(), property.getValue());
      }
    }
    connection.setRequestProperty("Accept-Encoding", "deflate");
    connection.setRequestProperty("User-Agent", userAgent);
    connection.setRequestProperty("Range", buildRangeHeader(dataSpec));
    connection.connect();
    return connection;
  }

  private String buildRangeHeader(DataSpec dataSpec) {
    String rangeRequest = "bytes=" + dataSpec.position + "-";
    if (dataSpec.length != DataSpec.LENGTH_UNBOUNDED) {
      rangeRequest += (dataSpec.position + dataSpec.length - 1);
    }
    return rangeRequest;
  }

  private long getContentLength(HttpURLConnection connection) {
    long contentLength = DataSpec.LENGTH_UNBOUNDED;
    String contentLengthHeader = connection.getHeaderField("Content-Length");
    if (!TextUtils.isEmpty(contentLengthHeader)) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
      }
    }
    String contentRangeHeader = connection.getHeaderField("Content-Range");
    if (!TextUtils.isEmpty(contentRangeHeader)) {
      Matcher matcher = CONTENT_RANGE_HEADER.matcher(contentRangeHeader);
      if (matcher.find()) {
        try {
          long contentLengthFromRange =
              Long.parseLong(matcher.group(2)) - Long.parseLong(matcher.group(1)) + 1;
          if (contentLength < 0) {
            // Some proxy servers strip the Content-Length header. Fall back to the length
            // calculated here in this case.
            contentLength = contentLengthFromRange;
          } else if (contentLength != contentLengthFromRange) {
            // If there is a discrepancy between the Content-Length and Content-Range headers,
            // assume the one with the larger value is correct. We have seen cases where carrier
            // change one of them to reduce the size of a request, but it is unlikely anybody would
            // increase it.
            Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader +
                "]");
            contentLength = Math.max(contentLength, contentLengthFromRange);
          }
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
        }
      }
    }
    if (contentLength == DataSpec.LENGTH_UNBOUNDED) {
      Log.w(TAG, "Unable to parse content length [" + contentLengthHeader + "] [" +
          contentRangeHeader + "]");
    }
    return contentLength;
  }

}
