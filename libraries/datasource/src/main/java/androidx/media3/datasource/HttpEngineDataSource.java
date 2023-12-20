/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.datasource;

import static android.net.http.UrlRequest.REQUEST_PRIORITY_MEDIUM;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.datasource.HttpUtil.buildRangeRequestHeader;

import android.net.Uri;
import android.net.http.HttpEngine;
import android.net.http.HttpException;
import android.net.http.NetworkException;
import android.net.http.UrlRequest;
import android.net.http.UrlRequest.Status;
import android.net.http.UrlResponseInfo;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.HttpDataSource.CleartextNotPermittedException;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException;
import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Longs;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

/**
 * DataSource without intermediate buffer based on {@link HttpEngine} set using {@link UrlRequest}.
 *
 * <p>Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the {@code dataSpec}, {@link #setRequestProperty} and the default parameters used to
 * construct the instance.
 */
@RequiresApi(34)
@UnstableApi
public final class HttpEngineDataSource extends BaseDataSource implements HttpDataSource {

  static {
    MediaLibraryInfo.registerModule("media3.datasource.httpengine");
  }

  /** {@link DataSource.Factory} for {@link HttpEngineDataSource} instances. */
  public static final class Factory implements HttpDataSource.Factory {

    private final HttpEngine httpEngine;
    private final Executor executor;
    private final RequestProperties defaultRequestProperties;

    @Nullable private Predicate<String> contentTypePredicate;
    @Nullable private TransferListener transferListener;
    @Nullable private String userAgent;
    private int requestPriority;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private boolean resetTimeoutOnRedirects;
    private boolean handleSetCookieRequests;
    private boolean keepPostFor302Redirects;

    /**
     * Creates an instance.
     *
     * @param httpEngine An {@link HttpEngine} to make the requests.
     * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This
     *     may be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a
     *     thread hop from HttpEngine's internal network thread to the response handling thread.
     *     However, to avoid slowing down overall network performance, care must be taken to make
     *     sure response handling is a fast operation when using a direct executor.
     */
    public Factory(HttpEngine httpEngine, Executor executor) {
      this.httpEngine = Assertions.checkNotNull(httpEngine);
      this.executor = executor;
      defaultRequestProperties = new RequestProperties();
      requestPriority = REQUEST_PRIORITY_MEDIUM;
      connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
    }

    @CanIgnoreReturnValue
    @UnstableApi
    @Override
    public final Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
      this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
      return this;
    }

    /**
     * Sets the user agent that will be used.
     *
     * <p>The default is {@code null}, which causes the default user agent of the underlying {@link
     * HttpEngine} to be used.
     *
     * @param userAgent The user agent that will be used, or {@code null} to use the default user
     *     agent of the underlying {@link HttpEngine}.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Sets the priority of requests made by {@link HttpEngineDataSource} instances created by this
     * factory.
     *
     * <p>The default is {@link UrlRequest#REQUEST_PRIORITY_MEDIUM}.
     *
     * @param requestPriority The request priority, which should be one of HttpEngine's {@code
     *     UrlRequest#REQUEST_PRIORITY_*} constants.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setRequestPriority(int requestPriority) {
      this.requestPriority = requestPriority;
      return this;
    }

    /**
     * Sets the connect timeout, in milliseconds.
     *
     * <p>The default is {@link HttpEngineDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
     *
     * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setConnectionTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
      return this;
    }

    /**
     * Sets whether the connect timeout is reset when a redirect occurs.
     *
     * <p>The default is {@code false}.
     *
     * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setResetTimeoutOnRedirects(boolean resetTimeoutOnRedirects) {
      this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
      return this;
    }

    /**
     * Sets whether "Set-Cookie" requests on redirect should be forwarded to the redirect url in the
     * "Cookie" header.
     *
     * <p>The default is {@code false}.
     *
     * @param handleSetCookieRequests Whether "Set-Cookie" requests on redirect should be forwarded
     *     to the redirect url in the "Cookie" header.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setHandleSetCookieRequests(boolean handleSetCookieRequests) {
      this.handleSetCookieRequests = handleSetCookieRequests;
      return this;
    }

    /**
     * Sets the read timeout, in milliseconds.
     *
     * <p>The default is {@link HttpEngineDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
     *
     * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
      return this;
    }

    /**
     * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
     * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link #open(DataSpec)}.
     *
     * <p>The default is {@code null}.
     *
     * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
     *     predicate that was previously set.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
      this.contentTypePredicate = contentTypePredicate;
      return this;
    }

    /**
     * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for a
     * POST request.
     */
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setKeepPostFor302Redirects(boolean keepPostFor302Redirects) {
      this.keepPostFor302Redirects = keepPostFor302Redirects;
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
    @CanIgnoreReturnValue
    @UnstableApi
    public Factory setTransferListener(@Nullable TransferListener transferListener) {
      this.transferListener = transferListener;
      return this;
    }

    @UnstableApi
    @Override
    public HttpDataSource createDataSource() {
      HttpEngineDataSource dataSource =
          new HttpEngineDataSource(
              httpEngine,
              executor,
              requestPriority,
              connectTimeoutMs,
              readTimeoutMs,
              resetTimeoutOnRedirects,
              handleSetCookieRequests,
              userAgent,
              defaultRequestProperties,
              contentTypePredicate,
              keepPostFor302Redirects);
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return dataSource;
    }
  }

  /** Thrown when an error is encountered when trying to open a {@link HttpEngineDataSource}. */
  @UnstableApi
  public static final class OpenException extends HttpDataSourceException {

    /**
     * Returns the status of the connection establishment at the moment when the error occurred, as
     * defined by {@link UrlRequest.Status}.
     */
    public final int httpEngineConnectionStatus;

    public OpenException(
        IOException cause,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        int httpEngineConnectionStatus) {
      super(cause, dataSpec, errorCode, TYPE_OPEN);
      this.httpEngineConnectionStatus = httpEngineConnectionStatus;
    }

    public OpenException(
        String errorMessage,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        int httpEngineConnectionStatus) {
      super(errorMessage, dataSpec, errorCode, TYPE_OPEN);
      this.httpEngineConnectionStatus = httpEngineConnectionStatus;
    }

    public OpenException(
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        int httpEngineConnectionStatus) {
      super(dataSpec, errorCode, TYPE_OPEN);
      this.httpEngineConnectionStatus = httpEngineConnectionStatus;
    }
  }

  /** The default connection timeout, in milliseconds. */
  @UnstableApi public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;

  /** The default read timeout, in milliseconds. */
  @UnstableApi public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  // The size of read buffer passed to cronet UrlRequest.read().
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  private final HttpEngine httpEngine;
  private final Executor executor;
  private final int requestPriority;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final boolean handleSetCookieRequests;
  @Nullable private final String userAgent;
  @Nullable private final RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;
  private final ConditionVariable operation;
  private final Clock clock;

  @Nullable private Predicate<String> contentTypePredicate;
  private final boolean keepPostFor302Redirects;

  // Accessed by the calling thread only.
  private boolean opened;
  private long bytesRemaining;

  @Nullable private DataSpec currentDataSpec;
  @Nullable private UrlRequestWrapper currentUrlRequestWrapper;

  // Reference written and read by calling thread only. Passed to HttpEngine thread as a local
  // variable.
  // operation.open() calls ensure writes into the buffer are visible to reads made by the calling
  // thread.
  @Nullable private ByteBuffer readBuffer;

  // Written from the HttpEngine thread only. operation.open() calls ensure writes are visible to
  // reads
  // made by the calling thread.
  @Nullable private UrlResponseInfo responseInfo;
  @Nullable private IOException exception;
  private boolean finished;

  private volatile long currentConnectTimeoutMs;

  @UnstableApi
  /* package */ HttpEngineDataSource(
      HttpEngine httpEngine,
      Executor executor,
      int requestPriority,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      boolean handleSetCookieRequests,
      @Nullable String userAgent,
      @Nullable RequestProperties defaultRequestProperties,
      @Nullable Predicate<String> contentTypePredicate,
      boolean keepPostFor302Redirects) {
    super(/* isNetwork= */ true);
    this.httpEngine = Assertions.checkNotNull(httpEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.requestPriority = requestPriority;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.handleSetCookieRequests = handleSetCookieRequests;
    this.userAgent = userAgent;
    this.defaultRequestProperties = defaultRequestProperties;
    this.contentTypePredicate = contentTypePredicate;
    this.keepPostFor302Redirects = keepPostFor302Redirects;
    clock = Clock.DEFAULT;
    requestProperties = new RequestProperties();
    operation = new ConditionVariable();
  }

  // HttpDataSource implementation.

  @UnstableApi
  @Override
  public void setRequestProperty(String name, String value) {
    requestProperties.set(name, value);
  }

  @UnstableApi
  @Override
  public void clearRequestProperty(String name) {
    requestProperties.remove(name);
  }

  @UnstableApi
  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  @UnstableApi
  @Override
  public int getResponseCode() {
    return responseInfo == null || responseInfo.getHttpStatusCode() <= 0
        ? -1
        : responseInfo.getHttpStatusCode();
  }

  @UnstableApi
  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return responseInfo == null ? Collections.emptyMap() : responseInfo.getHeaders().getAsMap();
  }

  @UnstableApi
  @Override
  @Nullable
  public Uri getUri() {
    return responseInfo == null ? null : Uri.parse(responseInfo.getUrl());
  }

  @UnstableApi
  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    Assertions.checkNotNull(dataSpec);
    Assertions.checkState(!opened);

    operation.close();
    resetConnectTimeout();
    currentDataSpec = dataSpec;
    UrlRequestWrapper urlRequestWrapper;
    try {
      urlRequestWrapper = buildRequestWrapper(dataSpec);
      currentUrlRequestWrapper = urlRequestWrapper;
    } catch (IOException e) {
      if (e instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) e;
      } else {
        throw new OpenException(
            e, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, Status.IDLE);
      }
    }
    urlRequestWrapper.start();

    transferInitializing(dataSpec);
    try {
      boolean connectionOpened = blockUntilConnectTimeout();
      @Nullable IOException connectionOpenException = exception;
      if (connectionOpenException != null) {
        @Nullable String message = connectionOpenException.getMessage();
        if (message != null && Ascii.toLowerCase(message).contains("err_cleartext_not_permitted")) {
          throw new CleartextNotPermittedException(connectionOpenException, dataSpec);
        }
        throw new OpenException(
            connectionOpenException,
            dataSpec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            urlRequestWrapper.getStatus());
      } else if (!connectionOpened) {
        // The timeout was reached before the connection was opened.
        throw new OpenException(
            new SocketTimeoutException(),
            dataSpec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            urlRequestWrapper.getStatus());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // An interruption means the operation is being cancelled, in which case this exception should
      // not cause the player to fail. If it does, it likely means that the owner of the operation
      // is failing to swallow the interruption, which makes us enter an invalid state.
      throw new OpenException(
          new InterruptedIOException(),
          dataSpec,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
          Status.INVALID);
    }

    // Check for a valid response code.
    UrlResponseInfo responseInfo = Assertions.checkNotNull(this.responseInfo);
    int responseCode = responseInfo.getHttpStatusCode();
    Map<String, List<String>> responseHeaders = responseInfo.getHeaders().getAsMap();
    if (responseCode < 200 || responseCode > 299) {
      if (responseCode == 416) {
        long documentSize =
            HttpUtil.getDocumentSize(getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE));
        if (dataSpec.position == documentSize) {
          opened = true;
          transferStarted(dataSpec);
          return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : 0;
        }
      }

      byte[] responseBody;
      try {
        responseBody = readResponseBody();
      } catch (IOException e) {
        responseBody = Util.EMPTY_BYTE_ARRAY;
      }

      @Nullable
      IOException cause =
          responseCode == 416
              ? new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
              : null;
      throw new InvalidResponseCodeException(
          responseCode,
          responseInfo.getHttpStatusText(),
          cause,
          responseHeaders,
          dataSpec,
          responseBody);
    }

    // Check for a valid content type.
    Predicate<String> contentTypePredicate = this.contentTypePredicate;
    if (contentTypePredicate != null) {
      @Nullable String contentType = getFirstHeader(responseHeaders, HttpHeaders.CONTENT_TYPE);
      if (contentType != null && !contentTypePredicate.apply(contentType)) {
        throw new InvalidContentTypeException(contentType, dataSpec);
      }
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    long bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Calculate the content length.
    if (!isCompressed(responseInfo)) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        long contentLength =
            HttpUtil.getContentLength(
                getFirstHeader(responseHeaders, HttpHeaders.CONTENT_LENGTH),
                getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE));
        bytesRemaining =
            contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip) : C.LENGTH_UNSET;
      }
    } else {
      // If the response is compressed then the content length will be that of the compressed data
      // which isn't what we want. Always use the dataSpec length in this case.
      bytesRemaining = dataSpec.length;
    }

    opened = true;
    transferStarted(dataSpec);

    skipFully(bytesToSkip, dataSpec);
    return bytesRemaining;
  }

  @UnstableApi
  @Override
  public int read(byte[] buffer, int offset, int length) throws HttpDataSourceException {
    Assertions.checkState(opened);

    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    ByteBuffer readBuffer = getOrCreateReadBuffer();
    if (!readBuffer.hasRemaining()) {
      // Fill readBuffer with more data from HttpEngine.
      operation.close();
      readBuffer.clear();

      readInternal(readBuffer, castNonNull(currentDataSpec));

      if (finished) {
        bytesRemaining = 0;
        return C.RESULT_END_OF_INPUT;
      }

      // The operation didn't time out, fail or finish, and therefore data must have been read.
      readBuffer.flip();
      Assertions.checkState(readBuffer.hasRemaining());
    }

    // Ensure we read up to bytesRemaining, in case this was a Range request with finite end, but
    // the server does not support Range requests and transmitted the entire resource.
    int bytesRead =
        (int)
            Longs.min(
                bytesRemaining != C.LENGTH_UNSET ? bytesRemaining : Long.MAX_VALUE,
                readBuffer.remaining(),
                length);

    readBuffer.get(buffer, offset, bytesRead);

    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  /**
   * Reads up to {@code buffer.remaining()} bytes of data and stores them into {@code buffer},
   * starting at {@code buffer.position()}. Advances the position of the buffer by the number of
   * bytes read and returns this length.
   *
   * <p>If there is an error, a {@link HttpDataSourceException} is thrown and the contents of {@code
   * buffer} should be ignored. If the exception has error code {@code
   * HttpDataSourceException.TYPE_READ}, note that HttpEngine may continue writing into {@code
   * buffer} after the method has returned. Thus the caller should not attempt to reuse the buffer.
   *
   * <p>If {@code buffer.remaining()} is zero then 0 is returned. Otherwise, if no data is available
   * because the end of the opened range has been reached, then {@link C#RESULT_END_OF_INPUT} is
   * returned. Otherwise, the call will block until at least one byte of data has been read and the
   * number of bytes read is returned.
   *
   * <p>Passed buffer must be direct ByteBuffer. If you have a non-direct ByteBuffer, consider the
   * alternative read method with its backed array.
   *
   * @param buffer The ByteBuffer into which the read data should be stored. Must be a direct
   *     ByteBuffer.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if no data is available
   *     because the end of the opened range has been reached.
   * @throws HttpDataSourceException If an error occurs reading from the source.
   * @throws IllegalArgumentException If {@code buffer} is not a direct ByteBuffer.
   */
  @UnstableApi
  public int read(ByteBuffer buffer) throws HttpDataSourceException {
    Assertions.checkState(opened);

    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("Passed buffer is not a direct ByteBuffer");
    }
    if (!buffer.hasRemaining()) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    int readLength = buffer.remaining();

    if (readBuffer != null) {
      // If there is existing data in the readBuffer, read as much as possible. Return if any read.
      int copyBytes = copyByteBuffer(/* src= */ readBuffer, /* dst= */ buffer);
      if (copyBytes != 0) {
        if (bytesRemaining != C.LENGTH_UNSET) {
          bytesRemaining -= copyBytes;
        }
        bytesTransferred(copyBytes);
        return copyBytes;
      }
    }

    // Fill buffer with more data from HttpEngine.
    operation.close();
    readInternal(buffer, castNonNull(currentDataSpec));

    if (finished) {
      bytesRemaining = 0;
      return C.RESULT_END_OF_INPUT;
    }

    // The operation didn't time out, fail or finish, and therefore data must have been read.
    Assertions.checkState(readLength > buffer.remaining());
    int bytesRead = readLength - buffer.remaining();
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @UnstableApi
  @Override
  public synchronized void close() {
    if (currentUrlRequestWrapper != null) {
      currentUrlRequestWrapper.close();
      currentUrlRequestWrapper = null;
    }
    if (readBuffer != null) {
      readBuffer.limit(0);
    }
    currentDataSpec = null;
    responseInfo = null;
    exception = null;
    finished = false;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }

  /** Returns current {@link UrlRequest.Callback}. May be null if the data source is not opened. */
  @UnstableApi
  @VisibleForTesting
  @Nullable
  UrlRequest.Callback getCurrentUrlRequestCallback() {
    return currentUrlRequestWrapper == null
        ? null
        : currentUrlRequestWrapper.getUrlRequestCallback();
  }

  private UrlRequestWrapper buildRequestWrapper(DataSpec dataSpec) throws IOException {
    UrlRequestCallback callback = new UrlRequestCallback();
    return new UrlRequestWrapper(buildRequestBuilder(dataSpec, callback).build(), callback);
  }

  private UrlRequest.Builder buildRequestBuilder(
      DataSpec dataSpec, UrlRequest.Callback urlRequestCallback) throws IOException {
    UrlRequest.Builder requestBuilder =
        httpEngine
            .newUrlRequestBuilder(dataSpec.uri.toString(), executor, urlRequestCallback)
            .setPriority(requestPriority)
            .setDirectExecutorAllowed(true);

    // Set the headers.
    Map<String, String> requestHeaders = new HashMap<>();
    if (defaultRequestProperties != null) {
      requestHeaders.putAll(defaultRequestProperties.getSnapshot());
    }
    requestHeaders.putAll(requestProperties.getSnapshot());
    requestHeaders.putAll(dataSpec.httpRequestHeaders);

    for (Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      String key = headerEntry.getKey();
      String value = headerEntry.getValue();
      requestBuilder.addHeader(key, value);
    }

    if (dataSpec.httpBody != null && !requestHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
      throw new OpenException(
          "HTTP request with non-empty body must set Content-Type",
          dataSpec,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
          Status.IDLE);
    }

    @Nullable String rangeHeader = buildRangeRequestHeader(dataSpec.position, dataSpec.length);
    if (rangeHeader != null) {
      requestBuilder.addHeader(HttpHeaders.RANGE, rangeHeader);
    }
    if (userAgent != null) {
      requestBuilder.addHeader(HttpHeaders.USER_AGENT, userAgent);
    }
    // TODO: Uncomment when https://bugs.chromium.org/p/chromium/issues/detail?id=711810 is fixed
    // (adjusting the code as necessary).
    // Force identity encoding unless gzip is allowed.
    // if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
    //   requestBuilder.addHeader("Accept-Encoding", "identity");
    // }
    // Set the method and (if non-empty) the body.
    requestBuilder.setHttpMethod(dataSpec.getHttpMethodString());
    if (dataSpec.httpBody != null) {
      requestBuilder.setUploadDataProvider(
          new ByteArrayUploadDataProvider(dataSpec.httpBody), executor);
    }
    return requestBuilder;
  }

  // Internal methods.

  private boolean blockUntilConnectTimeout() throws InterruptedException {
    long now = clock.elapsedRealtime();
    boolean opened = false;
    while (!opened && now < currentConnectTimeoutMs) {
      opened = operation.block(currentConnectTimeoutMs - now + 5 /* fudge factor */);
      now = clock.elapsedRealtime();
    }
    return opened;
  }

  private void resetConnectTimeout() {
    currentConnectTimeoutMs = clock.elapsedRealtime() + connectTimeoutMs;
  }

  /**
   * Attempts to skip the specified number of bytes in full.
   *
   * <p>The methods throws an {@link OpenException} with {@link OpenException#reason} set to {@link
   * PlaybackException#ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE} when the data ended before the
   * specified number of bytes were skipped.
   *
   * @param bytesToSkip The number of bytes to skip.
   * @param dataSpec The {@link DataSpec}.
   * @throws HttpDataSourceException If the thread is interrupted during the operation, or an error
   *     occurs reading from the source; or when the data ended before the specified number of bytes
   *     were skipped.
   */
  private void skipFully(long bytesToSkip, DataSpec dataSpec) throws HttpDataSourceException {
    if (bytesToSkip == 0) {
      return;
    }
    ByteBuffer readBuffer = getOrCreateReadBuffer();

    try {
      while (bytesToSkip > 0) {
        // Fill readBuffer with more data from HttpEngine.
        operation.close();
        readBuffer.clear();
        readInternal(readBuffer, dataSpec);
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedIOException();
        }
        if (finished) {
          throw new OpenException(
              dataSpec,
              PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
              Status.READING_RESPONSE);
        } else {
          // The operation didn't time out, fail or finish, and therefore data must have been read.
          readBuffer.flip();
          Assertions.checkState(readBuffer.hasRemaining());
          int bytesSkipped = (int) Math.min(readBuffer.remaining(), bytesToSkip);
          readBuffer.position(readBuffer.position() + bytesSkipped);
          bytesToSkip -= bytesSkipped;
        }
      }
    } catch (IOException e) {
      if (e instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) e;
      } else {
        throw new OpenException(
            e,
            dataSpec,
            e instanceof SocketTimeoutException
                ? PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                : PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            Status.READING_RESPONSE);
      }
    }
  }

  /**
   * Reads the whole response body.
   *
   * @return The response body.
   * @throws IOException If an error occurs reading from the source.
   */
  private byte[] readResponseBody() throws IOException {
    byte[] responseBody = Util.EMPTY_BYTE_ARRAY;
    ByteBuffer readBuffer = getOrCreateReadBuffer();
    while (!finished) {
      operation.close();
      readBuffer.clear();
      readInternal(readBuffer, castNonNull(currentDataSpec));
      readBuffer.flip();
      if (readBuffer.remaining() > 0) {
        int existingResponseBodyEnd = responseBody.length;
        responseBody = Arrays.copyOf(responseBody, responseBody.length + readBuffer.remaining());
        readBuffer.get(responseBody, existingResponseBodyEnd, readBuffer.remaining());
      }
    }
    return responseBody;
  }

  /**
   * Reads up to {@code buffer.remaining()} bytes of data from {@code currentUrlRequest} and stores
   * them into {@code buffer}. If there is an error and {@code buffer == readBuffer}, then it resets
   * the current {@code readBuffer} object so that it is not reused in the future.
   *
   * @param buffer The ByteBuffer into which the read data is stored. Must be a direct ByteBuffer.
   * @throws HttpDataSourceException If an error occurs reading from the source.
   */
  @SuppressWarnings("ReferenceEquality")
  private void readInternal(ByteBuffer buffer, DataSpec dataSpec) throws HttpDataSourceException {
    castNonNull(currentUrlRequestWrapper).read(buffer);
    try {
      if (!operation.block(readTimeoutMs)) {
        throw new SocketTimeoutException();
      }
    } catch (InterruptedException e) {
      // The operation is ongoing so replace buffer to avoid it being written to by this
      // operation during a subsequent request.
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      Thread.currentThread().interrupt();
      exception = new InterruptedIOException();
    } catch (SocketTimeoutException e) {
      // The operation is ongoing so replace buffer to avoid it being written to by this
      // operation during a subsequent request.
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      exception =
          new HttpDataSourceException(
              e,
              dataSpec,
              PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
              HttpDataSourceException.TYPE_READ);
    }

    if (exception != null) {
      if (exception instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) exception;
      } else {
        throw HttpDataSourceException.createForIOException(
            exception, dataSpec, HttpDataSourceException.TYPE_READ);
      }
    }
  }

  private ByteBuffer getOrCreateReadBuffer() {
    if (readBuffer == null) {
      readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
      readBuffer.limit(0);
    }
    return readBuffer;
  }

  private static boolean isCompressed(UrlResponseInfo info) {
    for (Map.Entry<String, String> entry : info.getHeaders().getAsList()) {
      if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
        return !entry.getValue().equalsIgnoreCase("identity");
      }
    }
    return false;
  }

  @Nullable
  private static String parseCookies(@Nullable List<String> setCookieHeaders) {
    if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
      return null;
    }
    return TextUtils.join(";", setCookieHeaders);
  }

  @Nullable
  private static String getFirstHeader(Map<String, List<String>> allHeaders, String headerName) {
    @Nullable List<String> headers = allHeaders.get(headerName);
    return headers != null && !headers.isEmpty() ? headers.get(0) : null;
  }

  // Copy as much as possible from the src buffer into dst buffer.
  // Returns the number of bytes copied.
  private static int copyByteBuffer(ByteBuffer src, ByteBuffer dst) {
    int remaining = Math.min(src.remaining(), dst.remaining());
    int limit = src.limit();
    src.limit(src.position() + remaining);
    dst.put(src);
    src.limit(limit);
    return remaining;
  }

  /**
   * A wrapper class that manages a {@link UrlRequest} and the {@link UrlRequestCallback} associated
   * with that request.
   */
  private static final class UrlRequestWrapper {

    private final UrlRequest urlRequest;
    private final UrlRequestCallback urlRequestCallback;

    UrlRequestWrapper(UrlRequest urlRequest, UrlRequestCallback urlRequestCallback) {
      this.urlRequest = urlRequest;
      this.urlRequestCallback = urlRequestCallback;
    }

    public void start() {
      urlRequest.start();
    }

    public void read(ByteBuffer buffer) {
      urlRequest.read(buffer);
    }

    public void close() {
      urlRequestCallback.close();
      urlRequest.cancel();
    }

    public UrlRequest.Callback getUrlRequestCallback() {
      return urlRequestCallback;
    }

    public int getStatus() throws InterruptedException {
      final ConditionVariable conditionVariable = new ConditionVariable();
      final int[] statusHolder = new int[1];
      urlRequest.getStatus(
          new UrlRequest.StatusListener() {
            @Override
            public void onStatus(int status) {
              statusHolder[0] = status;
              conditionVariable.open();
            }
          });
      conditionVariable.block();
      return statusHolder[0];
    }
  }

  private final class UrlRequestCallback implements UrlRequest.Callback {

    private volatile boolean isClosed = false;

    public void close() {
      this.isClosed = true;
    }

    @Override
    public synchronized void onRedirectReceived(
        UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
      if (isClosed) {
        return;
      }
      DataSpec dataSpec = Assertions.checkNotNull(currentDataSpec);
      int responseCode = info.getHttpStatusCode();
      if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        // The industry standard is to disregard POST redirects when the status code is 307 or
        // 308.
        if (responseCode == 307 || responseCode == 308) {
          exception =
              new InvalidResponseCodeException(
                  responseCode,
                  info.getHttpStatusText(),
                  /* cause= */ null,
                  info.getHeaders().getAsMap(),
                  dataSpec,
                  /* responseBody= */ Util.EMPTY_BYTE_ARRAY);
          operation.open();
          return;
        }
      }
      if (resetTimeoutOnRedirects) {
        resetConnectTimeout();
      }

      boolean shouldKeepPost =
          keepPostFor302Redirects
              && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST
              && responseCode == 302;

      // request.followRedirect() transforms a POST request into a GET request, so if we want to
      // keep it as a POST we need to fall through to the manual redirect logic below.
      if (!shouldKeepPost && !handleSetCookieRequests) {
        request.followRedirect();
        return;
      }

      @Nullable
      String cookieHeadersValue =
          parseCookies(info.getHeaders().getAsMap().get(HttpHeaders.SET_COOKIE));
      if (!shouldKeepPost && TextUtils.isEmpty(cookieHeadersValue)) {
        request.followRedirect();
        return;
      }

      request.cancel();
      DataSpec redirectUrlDataSpec;
      if (!shouldKeepPost && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        // For POST redirects that aren't 307 or 308, the redirect is followed but request is
        // transformed into a GET unless shouldKeepPost is true.
        redirectUrlDataSpec =
            dataSpec
                .buildUpon()
                .setUri(newLocationUrl)
                .setHttpMethod(DataSpec.HTTP_METHOD_GET)
                .setHttpBody(null)
                .build();
      } else {
        redirectUrlDataSpec = dataSpec.withUri(Uri.parse(newLocationUrl));
      }
      if (!TextUtils.isEmpty(cookieHeadersValue)) {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.putAll(dataSpec.httpRequestHeaders);
        requestHeaders.put(HttpHeaders.COOKIE, cookieHeadersValue);
        redirectUrlDataSpec =
            redirectUrlDataSpec.buildUpon().setHttpRequestHeaders(requestHeaders).build();
      }
      UrlRequestWrapper redirectUrlRequestWrapper;
      try {
        redirectUrlRequestWrapper = buildRequestWrapper(redirectUrlDataSpec);
      } catch (IOException e) {
        exception = e;
        return;
      }
      if (currentUrlRequestWrapper != null) {
        currentUrlRequestWrapper.close();
      }
      currentUrlRequestWrapper = redirectUrlRequestWrapper;
      currentUrlRequestWrapper.start();
    }

    @Override
    public synchronized void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
      if (isClosed) {
        return;
      }
      responseInfo = info;
      operation.open();
    }

    @Override
    public synchronized void onReadCompleted(
        UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
      if (isClosed) {
        return;
      }
      operation.open();
    }

    @Override
    public synchronized void onSucceeded(UrlRequest request, UrlResponseInfo info) {
      if (isClosed) {
        return;
      }
      finished = true;
      operation.open();
    }

    @Override
    public synchronized void onFailed(
        UrlRequest request, @Nullable UrlResponseInfo info, HttpException error) {
      if (isClosed) {
        return;
      }
      if (error instanceof NetworkException
          && ((NetworkException) error).getErrorCode()
              == NetworkException.ERROR_HOSTNAME_NOT_RESOLVED) {
        exception = new UnknownHostException();
      } else {
        exception = error;
      }
      operation.open();
    }

    @Override
    public synchronized void onCanceled(UrlRequest request, @Nullable UrlResponseInfo info) {
      // Do nothing
    }
  }
}
