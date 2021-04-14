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

import static com.google.android.exoplayer2.upstream.HttpUtil.buildRangeRequestHeader;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.min;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec.HttpMethod;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * An {@link HttpDataSource} that uses Android's {@link HttpURLConnection}.
 *
 * <p>By default this implementation will not follow cross-protocol redirects (i.e. redirects from
 * HTTP to HTTPS or vice versa). Cross-protocol redirects can be enabled by passing {@code true} to
 * {@link DefaultHttpDataSource.Factory#setAllowCrossProtocolRedirects(boolean)}.
 *
 * <p>Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the {@code dataSpec}, {@link #setRequestProperty} and the default properties that can
 * be passed to {@link HttpDataSource.Factory#setDefaultRequestProperties(Map)}.
 */
public class DefaultHttpDataSource extends BaseDataSource implements HttpDataSource {

  /** {@link DataSource.Factory} for {@link DefaultHttpDataSource} instances. */
  public static final class Factory implements HttpDataSource.Factory {

    private final RequestProperties defaultRequestProperties;

    @Nullable private TransferListener transferListener;
    @Nullable private Predicate<String> contentTypePredicate;
    @Nullable private String userAgent;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private boolean allowCrossProtocolRedirects;

    /** Creates an instance. */
    public Factory() {
      defaultRequestProperties = new RequestProperties();
      connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
    }

    /** @deprecated Use {@link #setDefaultRequestProperties(Map)} instead. */
    @Deprecated
    @Override
    public final RequestProperties getDefaultRequestProperties() {
      return defaultRequestProperties;
    }

    @Override
    public final Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
      this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
      return this;
    }

    /**
     * Sets the user agent that will be used.
     *
     * <p>The default is {@code null}, which causes the default user agent of the underlying
     * platform to be used.
     *
     * @param userAgent The user agent that will be used, or {@code null} to use the default user
     *     agent of the underlying platform.
     * @return This factory.
     */
    public Factory setUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Sets the connect timeout, in milliseconds.
     *
     * <p>The default is {@link DefaultHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
     *
     * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    public Factory setConnectTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
      return this;
    }

    /**
     * Sets the read timeout, in milliseconds.
     *
     * <p>The default is {@link DefaultHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
     *
     * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    public Factory setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
      return this;
    }

    /**
     * Sets whether to allow cross protocol redirects.
     *
     * <p>The default is {@code false}.
     *
     * @param allowCrossProtocolRedirects Whether to allow cross protocol redirects.
     * @return This factory.
     */
    public Factory setAllowCrossProtocolRedirects(boolean allowCrossProtocolRedirects) {
      this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
      return this;
    }

    /**
     * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
     * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
     * DefaultHttpDataSource#open(DataSpec)}.
     *
     * <p>The default is {@code null}.
     *
     * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
     *     predicate that was previously set.
     * @return This factory.
     */
    public Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
      this.contentTypePredicate = contentTypePredicate;
      return this;
    }

    /**
     * Sets the {@link TransferListener} that will be used.
     *
     * <p>The default is {@code null}.
     *
     * <p>See {@link DataSource#addTransferListener(TransferListener)}.
     *
     * @param transferListener The listener that will be used.
     * @return This factory.
     */
    public Factory setTransferListener(@Nullable TransferListener transferListener) {
      this.transferListener = transferListener;
      return this;
    }

    @Override
    public DefaultHttpDataSource createDataSource() {
      DefaultHttpDataSource dataSource =
          new DefaultHttpDataSource(
              userAgent,
              connectTimeoutMs,
              readTimeoutMs,
              allowCrossProtocolRedirects,
              defaultRequestProperties,
              contentTypePredicate);
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return dataSource;
    }
  }

  /** The default connection timeout, in milliseconds. */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private static final String TAG = "DefaultHttpDataSource";
  private static final int MAX_REDIRECTS = 20; // Same limit as okhttp.
  private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
  private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;
  private static final long MAX_BYTES_TO_DRAIN = 2048;

  private final boolean allowCrossProtocolRedirects;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  @Nullable private final String userAgent;
  @Nullable private final RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;

  @Nullable private Predicate<String> contentTypePredicate;
  @Nullable private DataSpec dataSpec;
  @Nullable private HttpURLConnection connection;
  @Nullable private InputStream inputStream;
  private boolean opened;
  private int responseCode;
  private long bytesToRead;
  private long bytesRead;

  /** @deprecated Use {@link DefaultHttpDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public DefaultHttpDataSource() {
    this(/* userAgent= */ null, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /** @deprecated Use {@link DefaultHttpDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public DefaultHttpDataSource(@Nullable String userAgent) {
    this(userAgent, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS);
  }

  /** @deprecated Use {@link DefaultHttpDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public DefaultHttpDataSource(
      @Nullable String userAgent, int connectTimeoutMillis, int readTimeoutMillis) {
    this(
        userAgent,
        connectTimeoutMillis,
        readTimeoutMillis,
        /* allowCrossProtocolRedirects= */ false,
        /* defaultRequestProperties= */ null);
  }

  /** @deprecated Use {@link DefaultHttpDataSource.Factory} instead. */
  @Deprecated
  public DefaultHttpDataSource(
      @Nullable String userAgent,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects,
      @Nullable RequestProperties defaultRequestProperties) {
    this(
        userAgent,
        connectTimeoutMillis,
        readTimeoutMillis,
        allowCrossProtocolRedirects,
        defaultRequestProperties,
        /* contentTypePredicate= */ null);
  }

  private DefaultHttpDataSource(
      @Nullable String userAgent,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects,
      @Nullable RequestProperties defaultRequestProperties,
      @Nullable Predicate<String> contentTypePredicate) {
    super(/* isNetwork= */ true);
    this.userAgent = userAgent;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    this.defaultRequestProperties = defaultRequestProperties;
    this.contentTypePredicate = contentTypePredicate;
    this.requestProperties = new RequestProperties();
  }

  /**
   * @deprecated Use {@link DefaultHttpDataSource.Factory#setContentTypePredicate(Predicate)}
   *     instead.
   */
  @Deprecated
  public void setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
    this.contentTypePredicate = contentTypePredicate;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return connection == null ? null : Uri.parse(connection.getURL().toString());
  }

  @Override
  public int getResponseCode() {
    return connection == null || responseCode <= 0 ? -1 : responseCode;
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return connection == null ? Collections.emptyMap() : connection.getHeaderFields();
  }

  @Override
  public void setRequestProperty(String name, String value) {
    checkNotNull(name);
    checkNotNull(value);
    requestProperties.set(name, value);
  }

  @Override
  public void clearRequestProperty(String name) {
    checkNotNull(name);
    requestProperties.remove(name);
  }

  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  /**
   * Opens the source to read the specified data.
   */
  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    this.dataSpec = dataSpec;
    bytesRead = 0;
    bytesToRead = 0;
    transferInitializing(dataSpec);

    try {
      connection = makeConnection(dataSpec);
    } catch (IOException e) {
      @Nullable String message = e.getMessage();
      if (message != null
          && Ascii.toLowerCase(message).matches("cleartext http traffic.*not permitted.*")) {
        throw new CleartextNotPermittedException(e, dataSpec);
      }
      throw new HttpDataSourceException(
          "Unable to connect", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    HttpURLConnection connection = this.connection;
    String responseMessage;
    try {
      responseCode = connection.getResponseCode();
      responseMessage = connection.getResponseMessage();
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException(
          "Unable to connect", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    // Check for a valid response code.
    if (responseCode < 200 || responseCode > 299) {
      Map<String, List<String>> headers = connection.getHeaderFields();
      if (responseCode == 416) {
        long documentSize =
            HttpUtil.getDocumentSize(connection.getHeaderField(HttpHeaders.CONTENT_RANGE));
        if (dataSpec.position == documentSize) {
          opened = true;
          transferStarted(dataSpec);
          return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : 0;
        }
      }

      @Nullable InputStream errorStream = connection.getErrorStream();
      byte[] errorResponseBody;
      try {
        errorResponseBody =
            errorStream != null ? Util.toByteArray(errorStream) : Util.EMPTY_BYTE_ARRAY;
      } catch (IOException e) {
        errorResponseBody = Util.EMPTY_BYTE_ARRAY;
      }
      closeConnectionQuietly();
      InvalidResponseCodeException exception =
          new InvalidResponseCodeException(
              responseCode, responseMessage, headers, dataSpec, errorResponseBody);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }

    // Check for a valid content type.
    String contentType = connection.getContentType();
    if (contentTypePredicate != null && !contentTypePredicate.apply(contentType)) {
      closeConnectionQuietly();
      throw new InvalidContentTypeException(contentType, dataSpec);
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    long bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Determine the length of the data to be read, after skipping.
    boolean isCompressed = isCompressed(connection);
    if (!isCompressed) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesToRead = dataSpec.length;
      } else {
        long contentLength =
            HttpUtil.getContentLength(
                connection.getHeaderField(HttpHeaders.CONTENT_LENGTH),
                connection.getHeaderField(HttpHeaders.CONTENT_RANGE));
        bytesToRead = contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip)
            : C.LENGTH_UNSET;
      }
    } else {
      // Gzip is enabled. If the server opts to use gzip then the content length in the response
      // will be that of the compressed data, which isn't what we want. Always use the dataSpec
      // length in this case.
      bytesToRead = dataSpec.length;
    }

    try {
      inputStream = connection.getInputStream();
      if (isCompressed) {
        inputStream = new GZIPInputStream(inputStream);
      }
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    opened = true;
    transferStarted(dataSpec);

    try {
      if (!skipFully(bytesToSkip)) {
        throw new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE);
      }
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    return bytesToRead;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    try {
      return readInternal(buffer, offset, readLength);
    } catch (IOException e) {
      throw new HttpDataSourceException(
          e, castNonNull(dataSpec), HttpDataSourceException.TYPE_READ);
    }
  }

  @Override
  public void close() throws HttpDataSourceException {
    try {
      @Nullable InputStream inputStream = this.inputStream;
      if (inputStream != null) {
        long bytesRemaining =
            bytesToRead == C.LENGTH_UNSET ? C.LENGTH_UNSET : bytesToRead - bytesRead;
        maybeTerminateInputStream(connection, bytesRemaining);
        try {
          inputStream.close();
        } catch (IOException e) {
          throw new HttpDataSourceException(
              e, castNonNull(dataSpec), HttpDataSourceException.TYPE_CLOSE);
        }
      }
    } finally {
      inputStream = null;
      closeConnectionQuietly();
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

  /**
   * Establishes a connection, following redirects to do so where permitted.
   */
  private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
    URL url = new URL(dataSpec.uri.toString());
    @HttpMethod int httpMethod = dataSpec.httpMethod;
    @Nullable byte[] httpBody = dataSpec.httpBody;
    long position = dataSpec.position;
    long length = dataSpec.length;
    boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

    if (!allowCrossProtocolRedirects) {
      // HttpURLConnection disallows cross-protocol redirects, but otherwise performs redirection
      // automatically. This is the behavior we want, so use it.
      return makeConnection(
          url,
          httpMethod,
          httpBody,
          position,
          length,
          allowGzip,
          /* followRedirects= */ true,
          dataSpec.httpRequestHeaders);
    }

    // We need to handle redirects ourselves to allow cross-protocol redirects.
    int redirectCount = 0;
    while (redirectCount++ <= MAX_REDIRECTS) {
      HttpURLConnection connection =
          makeConnection(
              url,
              httpMethod,
              httpBody,
              position,
              length,
              allowGzip,
              /* followRedirects= */ false,
              dataSpec.httpRequestHeaders);
      int responseCode = connection.getResponseCode();
      String location = connection.getHeaderField("Location");
      if ((httpMethod == DataSpec.HTTP_METHOD_GET || httpMethod == DataSpec.HTTP_METHOD_HEAD)
          && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
              || responseCode == HttpURLConnection.HTTP_MOVED_PERM
              || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
              || responseCode == HttpURLConnection.HTTP_SEE_OTHER
              || responseCode == HTTP_STATUS_TEMPORARY_REDIRECT
              || responseCode == HTTP_STATUS_PERMANENT_REDIRECT)) {
        connection.disconnect();
        url = handleRedirect(url, location);
      } else if (httpMethod == DataSpec.HTTP_METHOD_POST
          && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
              || responseCode == HttpURLConnection.HTTP_MOVED_PERM
              || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
              || responseCode == HttpURLConnection.HTTP_SEE_OTHER)) {
        // POST request follows the redirect and is transformed into a GET request.
        connection.disconnect();
        httpMethod = DataSpec.HTTP_METHOD_GET;
        httpBody = null;
        url = handleRedirect(url, location);
      } else {
        return connection;
      }
    }

    // If we get here we've been redirected more times than are permitted.
    throw new NoRouteToHostException("Too many redirects: " + redirectCount);
  }

  /**
   * Configures a connection and opens it.
   *
   * @param url The url to connect to.
   * @param httpMethod The http method.
   * @param httpBody The body data, or {@code null} if not required.
   * @param position The byte offset of the requested data.
   * @param length The length of the requested data, or {@link C#LENGTH_UNSET}.
   * @param allowGzip Whether to allow the use of gzip.
   * @param followRedirects Whether to follow redirects.
   * @param requestParameters parameters (HTTP headers) to include in request.
   */
  private HttpURLConnection makeConnection(
      URL url,
      @HttpMethod int httpMethod,
      @Nullable byte[] httpBody,
      long position,
      long length,
      boolean allowGzip,
      boolean followRedirects,
      Map<String, String> requestParameters)
      throws IOException {
    HttpURLConnection connection = openConnection(url);
    connection.setConnectTimeout(connectTimeoutMillis);
    connection.setReadTimeout(readTimeoutMillis);

    Map<String, String> requestHeaders = new HashMap<>();
    if (defaultRequestProperties != null) {
      requestHeaders.putAll(defaultRequestProperties.getSnapshot());
    }
    requestHeaders.putAll(requestProperties.getSnapshot());
    requestHeaders.putAll(requestParameters);

    for (Map.Entry<String, String> property : requestHeaders.entrySet()) {
      connection.setRequestProperty(property.getKey(), property.getValue());
    }

    @Nullable String rangeHeader = buildRangeRequestHeader(position, length);
    if (rangeHeader != null) {
      connection.setRequestProperty(HttpHeaders.RANGE, rangeHeader);
    }
    if (userAgent != null) {
      connection.setRequestProperty(HttpHeaders.USER_AGENT, userAgent);
    }
    connection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, allowGzip ? "gzip" : "identity");
    connection.setInstanceFollowRedirects(followRedirects);
    connection.setDoOutput(httpBody != null);
    connection.setRequestMethod(DataSpec.getStringForHttpMethod(httpMethod));

    if (httpBody != null) {
      connection.setFixedLengthStreamingMode(httpBody.length);
      connection.connect();
      OutputStream os = connection.getOutputStream();
      os.write(httpBody);
      os.close();
    } else {
      connection.connect();
    }
    return connection;
  }

  /** Creates an {@link HttpURLConnection} that is connected with the {@code url}. */
  @VisibleForTesting
  /* package */ HttpURLConnection openConnection(URL url) throws IOException {
    return (HttpURLConnection) url.openConnection();
  }

  /**
   * Handles a redirect.
   *
   * @param originalUrl The original URL.
   * @param location The Location header in the response. May be {@code null}.
   * @return The next URL.
   * @throws IOException If redirection isn't possible.
   */
  private static URL handleRedirect(URL originalUrl, @Nullable String location) throws IOException {
    if (location == null) {
      throw new ProtocolException("Null location redirect");
    }
    // Form the new url.
    URL url = new URL(originalUrl, location);
    // Check that the protocol of the new url is supported.
    String protocol = url.getProtocol();
    if (!"https".equals(protocol) && !"http".equals(protocol)) {
      throw new ProtocolException("Unsupported protocol redirect: " + protocol);
    }
    // Currently this method is only called if allowCrossProtocolRedirects is true, and so the code
    // below isn't required. If we ever decide to handle redirects ourselves when cross-protocol
    // redirects are disabled, we'll need to uncomment this block of code.
    // if (!allowCrossProtocolRedirects && !protocol.equals(originalUrl.getProtocol())) {
    //   throw new ProtocolException("Disallowed cross-protocol redirect ("
    //       + originalUrl.getProtocol() + " to " + protocol + ")");
    // }
    return url;
  }

  /**
   * Attempts to skip the specified number of bytes in full.
   *
   * @param bytesToSkip The number of bytes to skip.
   * @throws InterruptedIOException If the thread is interrupted during the operation.
   * @throws IOException If an error occurs reading from the source.
   * @return Whether the bytes were skipped in full. If {@code false} then the data ended before the
   *     specified number of bytes were skipped. Always {@code true} if {@code bytesToSkip == 0}.
   */
  private boolean skipFully(long bytesToSkip) throws IOException {
    if (bytesToSkip == 0) {
      return true;
    }
    byte[] skipBuffer = new byte[4096];
    while (bytesToSkip > 0) {
      int readLength = (int) min(bytesToSkip, skipBuffer.length);
      int read = castNonNull(inputStream).read(skipBuffer, 0, readLength);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedIOException();
      }
      if (read == -1) {
        return false;
      }
      bytesToSkip -= read;
      bytesTransferred(read);
    }
    return true;
  }

  /**
   * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
   * index {@code offset}.
   * <p>
   * This method blocks until at least one byte of data can be read, the end of the opened range is
   * detected, or an exception is thrown.
   *
   * @param buffer The buffer into which the read data should be stored.
   * @param offset The start offset into {@code buffer} at which data should be written.
   * @param readLength The maximum number of bytes to read.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
   *     range is reached.
   * @throws IOException If an error occurs reading from the source.
   */
  private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    }
    if (bytesToRead != C.LENGTH_UNSET) {
      long bytesRemaining = bytesToRead - bytesRead;
      if (bytesRemaining == 0) {
        return C.RESULT_END_OF_INPUT;
      }
      readLength = (int) min(readLength, bytesRemaining);
    }

    int read = castNonNull(inputStream).read(buffer, offset, readLength);
    if (read == -1) {
      return C.RESULT_END_OF_INPUT;
    }

    bytesRead += read;
    bytesTransferred(read);
    return read;
  }

  /**
   * On platform API levels 19 and 20, okhttp's implementation of {@link InputStream#close} can
   * block for a long time if the stream has a lot of data remaining. Call this method before
   * closing the input stream to make a best effort to cause the input stream to encounter an
   * unexpected end of input, working around this issue. On other platform API levels, the method
   * does nothing.
   *
   * @param connection The connection whose {@link InputStream} should be terminated.
   * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
   *     length is known. {@link C#LENGTH_UNSET} otherwise.
   */
  private static void maybeTerminateInputStream(
      @Nullable HttpURLConnection connection, long bytesRemaining) {
    if (connection == null || Util.SDK_INT < 19 || Util.SDK_INT > 20) {
      return;
    }

    try {
      InputStream inputStream = connection.getInputStream();
      if (bytesRemaining == C.LENGTH_UNSET) {
        // If the input stream has already ended, do nothing. The socket may be re-used.
        if (inputStream.read() == -1) {
          return;
        }
      } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
        // There isn't much data left. Prefer to allow it to drain, which may allow the socket to be
        // re-used.
        return;
      }
      String className = inputStream.getClass().getName();
      if ("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream".equals(className)
          || "com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream"
              .equals(className)) {
        Class<?> superclass = inputStream.getClass().getSuperclass();
        Method unexpectedEndOfInput =
            checkNotNull(superclass).getDeclaredMethod("unexpectedEndOfInput");
        unexpectedEndOfInput.setAccessible(true);
        unexpectedEndOfInput.invoke(inputStream);
      }
    } catch (Exception e) {
      // If an IOException then the connection didn't ever have an input stream, or it was closed
      // already. If another type of exception then something went wrong, most likely the device
      // isn't using okhttp.
    }
  }

  /**
   * Closes the current connection quietly, if there is one.
   */
  private void closeConnectionQuietly() {
    if (connection != null) {
      try {
        connection.disconnect();
      } catch (Exception e) {
        Log.e(TAG, "Unexpected error while disconnecting", e);
      }
      connection = null;
    }
  }

  private static boolean isCompressed(HttpURLConnection connection) {
    String contentEncoding = connection.getHeaderField("Content-Encoding");
    return "gzip".equalsIgnoreCase(contentEncoding);
  }
}
