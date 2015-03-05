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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Predicate;

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
 * A {@link HttpDataSource} that uses Android's {@link HttpURLConnection}.
 */
public class DefaultHttpDataSource implements HttpDataSource {

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
   *     rejected by the predicate then a {@link HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   */
  public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate) {
    this(userAgent, contentTypePredicate, null);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is
   *     rejected by the predicate then a {@link HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   * @param listener An optional listener.
   */
  public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
      TransferListener listener) {
    this(userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /**
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is
   *     rejected by the predicate then a {@link HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
   *     interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted
   *     as an infinite timeout.
   */
  public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
      TransferListener listener, int connectTimeoutMillis, int readTimeoutMillis) {
    this.userAgent = Assertions.checkNotEmpty(userAgent);
    this.contentTypePredicate = contentTypePredicate;
    this.listener = listener;
    this.requestProperties = new HashMap<String, String>();
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public String getUrl() {
    return connection == null ? null : connection.getURL().toString();
  }

  @Override
  public void setRequestProperty(String name, String value) {
    Assertions.checkNotNull(name);
    Assertions.checkNotNull(value);
    synchronized (requestProperties) {
      requestProperties.put(name, value);
    }
  }

  @Override
  public void clearRequestProperty(String name) {
    Assertions.checkNotNull(name);
    synchronized (requestProperties) {
      requestProperties.remove(name);
    }
  }

  @Override
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

    if ((dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) == 0) {
      long contentLength = getContentLength(connection);
      dataLength = dataSpec.length == C.LENGTH_UNBOUNDED ? contentLength : dataSpec.length;
      if (dataSpec.length != C.LENGTH_UNBOUNDED && contentLength != C.LENGTH_UNBOUNDED
          && contentLength != dataSpec.length) {
        // The DataSpec specified a length and we resolved a length from the response headers, but
        // the two lengths do not match.
        closeConnection();
        throw new HttpDataSourceException(
            new UnexpectedLengthException(dataSpec.length, contentLength), dataSpec);
      }
    } else {
      // Gzip is enabled. If the server opts to use gzip then the content length in the response
      // will be that of the compressed data, which isn't what we want. Furthermore, there isn't a
      // reliable way to determine whether the gzip was used or not. Hence we always treat the
      // length as unknown.
      dataLength = C.LENGTH_UNBOUNDED;
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
    } else if (dataLength != C.LENGTH_UNBOUNDED && dataLength != bytesRead) {
      // Check for cases where the server closed the connection having not sent the correct amount
      // of data. We can only do this if we know the length of the data we were expecting.
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
   * Returns the number of bytes that are still to be read for the current {@link DataSpec}.
   * <p>
   * If the total length of the data being read is known, then this length minus {@code bytesRead()}
   * is returned. If the total length is unknown, {@link C#LENGTH_UNBOUNDED} is returned.
   *
   * @return The remaining length, or {@link C#LENGTH_UNBOUNDED}.
   */
  protected final long bytesRemaining() {
    return dataLength == C.LENGTH_UNBOUNDED ? dataLength : dataLength - bytesRead;
  }

  private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
    URL url = new URL(dataSpec.uri.toString());
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(connectTimeoutMillis);
    connection.setReadTimeout(readTimeoutMillis);
    connection.setDoOutput(false);
    synchronized (requestProperties) {
      for (Map.Entry<String, String> property : requestProperties.entrySet()) {
        connection.setRequestProperty(property.getKey(), property.getValue());
      }
    }
    setRangeHeader(connection, dataSpec);
    connection.setRequestProperty("User-Agent", userAgent);
    if ((dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) == 0) {
      connection.setRequestProperty("Accept-Encoding", "identity");
    }
    connection.connect();
    return connection;
  }

  private void setRangeHeader(HttpURLConnection connection, DataSpec dataSpec) {
    if (dataSpec.position == 0 && dataSpec.length == C.LENGTH_UNBOUNDED) {
      // Not required.
      return;
    }
    String rangeRequest = "bytes=" + dataSpec.position + "-";
    if (dataSpec.length != C.LENGTH_UNBOUNDED) {
      rangeRequest += (dataSpec.position + dataSpec.length - 1);
    }
    connection.setRequestProperty("Range", rangeRequest);
  }

  private long getContentLength(HttpURLConnection connection) {
    long contentLength = C.LENGTH_UNBOUNDED;
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
            Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader
                + "]");
            contentLength = Math.max(contentLength, contentLengthFromRange);
          }
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
        }
      }
    }
    return contentLength;
  }

}
