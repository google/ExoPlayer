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
package com.google.android.exoplayer2.ext.cronet;

import static com.google.android.exoplayer2.upstream.HttpUtil.buildRangeRequestHeader;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpUtil;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Longs;
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
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Status;
import org.chromium.net.UrlResponseInfo;

/**
 * DataSource without intermediate buffer based on Cronet API set using UrlRequest.
 *
 * <p>Note: HTTP request headers will be set using all parameters passed via (in order of decreasing
 * priority) the {@code dataSpec}, {@link #setRequestProperty} and the default parameters used to
 * construct the instance.
 */
public class CronetDataSource extends BaseDataSource implements HttpDataSource {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.cronet");
  }

  /** {@link DataSource.Factory} for {@link CronetDataSource} instances. */
  public static final class Factory implements HttpDataSource.Factory {

    private final CronetEngineWrapper cronetEngineWrapper;
    private final Executor executor;
    private final RequestProperties defaultRequestProperties;
    private final DefaultHttpDataSource.Factory internalFallbackFactory;

    @Nullable private HttpDataSource.Factory fallbackFactory;
    @Nullable private Predicate<String> contentTypePredicate;
    @Nullable private TransferListener transferListener;
    @Nullable private String userAgent;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private boolean resetTimeoutOnRedirects;
    private boolean handleSetCookieRequests;

    /**
     * Creates an instance.
     *
     * @param cronetEngineWrapper A {@link CronetEngineWrapper}.
     * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This
     *     may be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a
     *     thread hop from Cronet's internal network thread to the response handling thread.
     *     However, to avoid slowing down overall network performance, care must be taken to make
     *     sure response handling is a fast operation when using a direct executor.
     */
    public Factory(CronetEngineWrapper cronetEngineWrapper, Executor executor) {
      this.cronetEngineWrapper = cronetEngineWrapper;
      this.executor = executor;
      defaultRequestProperties = new RequestProperties();
      internalFallbackFactory = new DefaultHttpDataSource.Factory();
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
      internalFallbackFactory.setDefaultRequestProperties(defaultRequestProperties);
      return this;
    }

    /**
     * Sets the user agent that will be used.
     *
     * <p>The default is {@code null}, which causes the default user agent of the underlying {@link
     * CronetEngine} to be used.
     *
     * @param userAgent The user agent that will be used, or {@code null} to use the default user
     *     agent of the underlying {@link CronetEngine}.
     * @return This factory.
     */
    public Factory setUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      internalFallbackFactory.setUserAgent(userAgent);
      return this;
    }

    /**
     * Sets the connect timeout, in milliseconds.
     *
     * <p>The default is {@link CronetDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
     *
     * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    public Factory setConnectionTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
      internalFallbackFactory.setConnectTimeoutMs(connectTimeoutMs);
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
    public Factory setHandleSetCookieRequests(boolean handleSetCookieRequests) {
      this.handleSetCookieRequests = handleSetCookieRequests;
      return this;
    }

    /**
     * Sets the read timeout, in milliseconds.
     *
     * <p>The default is {@link CronetDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
     *
     * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
     * @return This factory.
     */
    public Factory setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
      internalFallbackFactory.setReadTimeoutMs(readTimeoutMs);
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
    public Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
      this.contentTypePredicate = contentTypePredicate;
      internalFallbackFactory.setContentTypePredicate(contentTypePredicate);
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
      internalFallbackFactory.setTransferListener(transferListener);
      return this;
    }

    /**
     * Sets the fallback {@link HttpDataSource.Factory} that is used as a fallback if the {@link
     * CronetEngineWrapper} fails to provide a {@link CronetEngine}.
     *
     * <p>By default a {@link DefaultHttpDataSource} is used as fallback factory.
     *
     * @param fallbackFactory The fallback factory that will be used.
     * @return This factory.
     */
    public Factory setFallbackFactory(@Nullable HttpDataSource.Factory fallbackFactory) {
      this.fallbackFactory = fallbackFactory;
      return this;
    }

    @Override
    public HttpDataSource createDataSource() {
      @Nullable CronetEngine cronetEngine = cronetEngineWrapper.getCronetEngine();
      if (cronetEngine == null) {
        return (fallbackFactory != null)
            ? fallbackFactory.createDataSource()
            : internalFallbackFactory.createDataSource();
      }
      CronetDataSource dataSource =
          new CronetDataSource(
              cronetEngine,
              executor,
              connectTimeoutMs,
              readTimeoutMs,
              resetTimeoutOnRedirects,
              handleSetCookieRequests,
              userAgent,
              defaultRequestProperties,
              contentTypePredicate);
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return dataSource;
    }
  }

  /**
   * Thrown when an error is encountered when trying to open a {@link CronetDataSource}.
   */
  public static final class OpenException extends HttpDataSourceException {

    /**
     * Returns the status of the connection establishment at the moment when the error occurred, as
     * defined by {@link UrlRequest.Status}.
     */
    public final int cronetConnectionStatus;

    public OpenException(IOException cause, DataSpec dataSpec, int cronetConnectionStatus) {
      super(cause, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(String errorMessage, DataSpec dataSpec, int cronetConnectionStatus) {
      super(errorMessage, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }
  }

  /** The default connection timeout, in milliseconds. */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /** The default read timeout, in milliseconds. */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  /* package */ final UrlRequest.Callback urlRequestCallback;

  // The size of read buffer passed to cronet UrlRequest.read().
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  private final CronetEngine cronetEngine;
  private final Executor executor;
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

  // Accessed by the calling thread only.
  private boolean opened;
  private long bytesRemaining;

  // Written from the calling thread only. currentUrlRequest.start() calls ensure writes are visible
  // to reads made by the Cronet thread.
  @Nullable private UrlRequest currentUrlRequest;
  @Nullable private DataSpec currentDataSpec;

  // Reference written and read by calling thread only. Passed to Cronet thread as a local variable.
  // operation.open() calls ensure writes into the buffer are visible to reads made by the calling
  // thread.
  @Nullable private ByteBuffer readBuffer;

  // Written from the Cronet thread only. operation.open() calls ensure writes are visible to reads
  // made by the calling thread.
  @Nullable private UrlResponseInfo responseInfo;
  @Nullable private IOException exception;
  private boolean finished;

  private volatile long currentConnectTimeoutMs;

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public CronetDataSource(CronetEngine cronetEngine, Executor executor) {
    this(
        cronetEngine,
        executor,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        /* resetTimeoutOnRedirects= */ false,
        /* defaultRequestProperties= */ null);
  }

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @Deprecated
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      @Nullable RequestProperties defaultRequestProperties) {
    this(
        cronetEngine,
        executor,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        /* handleSetCookieRequests= */ false,
        /* userAgent= */ null,
        defaultRequestProperties,
        /* contentTypePredicate= */ null);
  }

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @Deprecated
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      @Nullable RequestProperties defaultRequestProperties,
      boolean handleSetCookieRequests) {
    this(
        cronetEngine,
        executor,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        handleSetCookieRequests,
        /* userAgent= */ null,
        defaultRequestProperties,
        /* contentTypePredicate= */ null);
  }

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate) {
    this(
        cronetEngine,
        executor,
        contentTypePredicate,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        /* resetTimeoutOnRedirects= */ false,
        /* defaultRequestProperties= */ null);
  }

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      @Nullable RequestProperties defaultRequestProperties) {
    this(
        cronetEngine,
        executor,
        contentTypePredicate,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        defaultRequestProperties,
        /* handleSetCookieRequests= */ false);
  }

  /** @deprecated Use {@link CronetDataSource.Factory} instead. */
  @Deprecated
  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      @Nullable Predicate<String> contentTypePredicate,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      @Nullable RequestProperties defaultRequestProperties,
      boolean handleSetCookieRequests) {
    this(
        cronetEngine,
        executor,
        connectTimeoutMs,
        readTimeoutMs,
        resetTimeoutOnRedirects,
        handleSetCookieRequests,
        /* userAgent= */ null,
        defaultRequestProperties,
        contentTypePredicate);
  }

  private CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      boolean handleSetCookieRequests,
      @Nullable String userAgent,
      @Nullable RequestProperties defaultRequestProperties,
      @Nullable Predicate<String> contentTypePredicate) {
    super(/* isNetwork= */ true);
    this.cronetEngine = Assertions.checkNotNull(cronetEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.handleSetCookieRequests = handleSetCookieRequests;
    this.userAgent = userAgent;
    this.defaultRequestProperties = defaultRequestProperties;
    this.contentTypePredicate = contentTypePredicate;
    clock = Clock.DEFAULT;
    urlRequestCallback = new UrlRequestCallback();
    requestProperties = new RequestProperties();
    operation = new ConditionVariable();
  }

  /**
   * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
   * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link #open(DataSpec)}.
   *
   * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
   *     predicate that was previously set.
   */
  @Deprecated
  public void setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
    this.contentTypePredicate = contentTypePredicate;
  }

  // HttpDataSource implementation.

  @Override
  public void setRequestProperty(String name, String value) {
    requestProperties.set(name, value);
  }

  @Override
  public void clearRequestProperty(String name) {
    requestProperties.remove(name);
  }

  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  @Override
  public int getResponseCode() {
    return responseInfo == null || responseInfo.getHttpStatusCode() <= 0
        ? -1
        : responseInfo.getHttpStatusCode();
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return responseInfo == null ? Collections.emptyMap() : responseInfo.getAllHeaders();
  }

  @Override
  @Nullable
  public Uri getUri() {
    return responseInfo == null ? null : Uri.parse(responseInfo.getUrl());
  }

  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    Assertions.checkNotNull(dataSpec);
    Assertions.checkState(!opened);

    operation.close();
    resetConnectTimeout();
    currentDataSpec = dataSpec;
    UrlRequest urlRequest;
    try {
      urlRequest = buildRequestBuilder(dataSpec).build();
      currentUrlRequest = urlRequest;
    } catch (IOException e) {
      throw new OpenException(e, dataSpec, Status.IDLE);
    }
    urlRequest.start();

    transferInitializing(dataSpec);
    try {
      boolean connectionOpened = blockUntilConnectTimeout();
      @Nullable IOException connectionOpenException = exception;
      if (connectionOpenException != null) {
        @Nullable String message = connectionOpenException.getMessage();
        if (message != null && Ascii.toLowerCase(message).contains("err_cleartext_not_permitted")) {
          throw new CleartextNotPermittedException(connectionOpenException, dataSpec);
        }
        throw new OpenException(connectionOpenException, dataSpec, getStatus(urlRequest));
      } else if (!connectionOpened) {
        // The timeout was reached before the connection was opened.
        throw new OpenException(new SocketTimeoutException(), dataSpec, getStatus(urlRequest));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OpenException(new InterruptedIOException(), dataSpec, Status.INVALID);
    }

    // Check for a valid response code.
    UrlResponseInfo responseInfo = Assertions.checkNotNull(this.responseInfo);
    int responseCode = responseInfo.getHttpStatusCode();
    Map<String, List<String>> responseHeaders = responseInfo.getAllHeaders();
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

      InvalidResponseCodeException exception =
          new InvalidResponseCodeException(
              responseCode,
              responseInfo.getHttpStatusText(),
              responseHeaders,
              dataSpec,
              responseBody);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
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

    try {
      if (!skipFully(bytesToSkip)) {
        throw new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE);
      }
    } catch (IOException e) {
      throw new OpenException(e, dataSpec, Status.READING_RESPONSE);
    }

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    Assertions.checkState(opened);

    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    ByteBuffer readBuffer = getOrCreateReadBuffer();
    if (!readBuffer.hasRemaining()) {
      // Fill readBuffer with more data from Cronet.
      operation.close();
      readBuffer.clear();
      try {
        readInternal(readBuffer);
      } catch (IOException e) {
        throw new HttpDataSourceException(
            e, castNonNull(currentDataSpec), HttpDataSourceException.TYPE_READ);
      }

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
                readLength);

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
   * HttpDataSourceException.TYPE_READ}, note that Cronet may continue writing into {@code buffer}
   * after the method has returned. Thus the caller should not attempt to reuse the buffer.
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

    // Fill buffer with more data from Cronet.
    operation.close();
    try {
      readInternal(buffer);
    } catch (IOException e) {
      throw new HttpDataSourceException(
          e, castNonNull(currentDataSpec), HttpDataSourceException.TYPE_READ);
    }

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

  @Override
  public synchronized void close() {
    if (currentUrlRequest != null) {
      currentUrlRequest.cancel();
      currentUrlRequest = null;
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

  /** Returns current {@link UrlRequest}. May be null if the data source is not opened. */
  @Nullable
  protected UrlRequest getCurrentUrlRequest() {
    return currentUrlRequest;
  }

  /** Returns current {@link UrlResponseInfo}. May be null if the data source is not opened. */
  @Nullable
  protected UrlResponseInfo getCurrentUrlResponseInfo() {
    return responseInfo;
  }

  protected UrlRequest.Builder buildRequestBuilder(DataSpec dataSpec) throws IOException {
    UrlRequest.Builder requestBuilder =
        cronetEngine
            .newUrlRequestBuilder(dataSpec.uri.toString(), urlRequestCallback, executor)
            .allowDirectExecutor();

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
      throw new IOException("HTTP request with non-empty body must set Content-Type");
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
    ByteBuffer readBuffer = getOrCreateReadBuffer();
    while (bytesToSkip > 0) {
      // Fill readBuffer with more data from Cronet.
      operation.close();
      readBuffer.clear();
      readInternal(readBuffer);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedIOException();
      }
      if (finished) {
        return false;
      } else {
        // The operation didn't time out, fail or finish, and therefore data must have been read.
        readBuffer.flip();
        Assertions.checkState(readBuffer.hasRemaining());
        int bytesSkipped = (int) Math.min(readBuffer.remaining(), bytesToSkip);
        readBuffer.position(readBuffer.position() + bytesSkipped);
        bytesToSkip -= bytesSkipped;
      }
    }
    return true;
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
      readInternal(readBuffer);
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
   * @throws IOException If an error occurs reading from the source.
   */
  @SuppressWarnings("ReferenceEquality")
  private void readInternal(ByteBuffer buffer) throws IOException {
    castNonNull(currentUrlRequest).read(buffer);
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
      throw new InterruptedIOException();
    } catch (SocketTimeoutException e) {
      // The operation is ongoing so replace buffer to avoid it being written to by this
      // operation during a subsequent request.
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      throw e;
    }

    if (exception != null) {
      throw exception;
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
    for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
      if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
        return !entry.getValue().equalsIgnoreCase("identity");
      }
    }
    return false;
  }

  private static String parseCookies(List<String> setCookieHeaders) {
    return TextUtils.join(";", setCookieHeaders);
  }

  private static void attachCookies(UrlRequest.Builder requestBuilder, String cookies) {
    if (TextUtils.isEmpty(cookies)) {
      return;
    }
    requestBuilder.addHeader(HttpHeaders.COOKIE, cookies);
  }

  private static int getStatus(UrlRequest request) throws InterruptedException {
    final ConditionVariable conditionVariable = new ConditionVariable();
    final int[] statusHolder = new int[1];
    request.getStatus(new UrlRequest.StatusListener() {
      @Override
      public void onStatus(int status) {
        statusHolder[0] = status;
        conditionVariable.open();
      }
    });
    conditionVariable.block();
    return statusHolder[0];
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

  private final class UrlRequestCallback extends UrlRequest.Callback {

    @Override
    public synchronized void onRedirectReceived(
        UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
      if (request != currentUrlRequest) {
        return;
      }
      UrlRequest urlRequest = Assertions.checkNotNull(currentUrlRequest);
      DataSpec dataSpec = Assertions.checkNotNull(currentDataSpec);
      if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        int responseCode = info.getHttpStatusCode();
        // The industry standard is to disregard POST redirects when the status code is 307 or 308.
        if (responseCode == 307 || responseCode == 308) {
          exception =
              new InvalidResponseCodeException(
                  responseCode,
                  info.getHttpStatusText(),
                  info.getAllHeaders(),
                  dataSpec,
                  /* responseBody= */ Util.EMPTY_BYTE_ARRAY);
          operation.open();
          return;
        }
      }
      if (resetTimeoutOnRedirects) {
        resetConnectTimeout();
      }

      if (!handleSetCookieRequests) {
        request.followRedirect();
        return;
      }

      @Nullable List<String> setCookieHeaders = info.getAllHeaders().get(HttpHeaders.SET_COOKIE);
      if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
        request.followRedirect();
        return;
      }

      urlRequest.cancel();
      DataSpec redirectUrlDataSpec;
      if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        // For POST redirects that aren't 307 or 308, the redirect is followed but request is
        // transformed into a GET.
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
      UrlRequest.Builder requestBuilder;
      try {
        requestBuilder = buildRequestBuilder(redirectUrlDataSpec);
      } catch (IOException e) {
        exception = e;
        return;
      }
      String cookieHeadersValue = parseCookies(setCookieHeaders);
      attachCookies(requestBuilder, cookieHeadersValue);
      currentUrlRequest = requestBuilder.build();
      currentUrlRequest.start();
    }

    @Override
    public synchronized void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
      if (request != currentUrlRequest) {
        return;
      }
      responseInfo = info;
      operation.open();
    }

    @Override
    public synchronized void onReadCompleted(
        UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
      if (request != currentUrlRequest) {
        return;
      }
      operation.open();
    }

    @Override
    public synchronized void onSucceeded(UrlRequest request, UrlResponseInfo info) {
      if (request != currentUrlRequest) {
        return;
      }
      finished = true;
      operation.open();
    }

    @Override
    public synchronized void onFailed(
        UrlRequest request, UrlResponseInfo info, CronetException error) {
      if (request != currentUrlRequest) {
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
  }
}
