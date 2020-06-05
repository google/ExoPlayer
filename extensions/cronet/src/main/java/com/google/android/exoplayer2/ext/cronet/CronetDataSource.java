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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Predicate;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
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

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.cronet");
  }

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  /* package */ final UrlRequest.Callback urlRequestCallback;

  private static final String TAG = "CronetDataSource";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String SET_COOKIE = "Set-Cookie";
  private static final String COOKIE = "Cookie";

  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
  // The size of read buffer passed to cronet UrlRequest.read().
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  private final CronetEngine cronetEngine;
  private final Executor executor;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final boolean handleSetCookieRequests;
  @Nullable private final RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;
  private final ConditionVariable operation;
  private final Clock clock;

  @Nullable private Predicate<String> contentTypePredicate;

  // Accessed by the calling thread only.
  private boolean opened;
  private long bytesToSkip;
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

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   */
  public CronetDataSource(CronetEngine cronetEngine, Executor executor) {
    this(
        cronetEngine,
        executor,
        DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DEFAULT_READ_TIMEOUT_MILLIS,
        /* resetTimeoutOnRedirects= */ false,
        /* defaultRequestProperties= */ null);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties Optional default {@link RequestProperties} to be sent to the
   *     server as HTTP headers on every request.
   */
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
        Clock.DEFAULT,
        defaultRequestProperties,
        /* handleSetCookieRequests= */ false);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties Optional default {@link RequestProperties} to be sent to the
   *     server as HTTP headers on every request.
   * @param handleSetCookieRequests Whether "Set-Cookie" requests on redirect should be forwarded to
   *     the redirect url in the "Cookie" header.
   */
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
        Clock.DEFAULT,
        defaultRequestProperties,
        handleSetCookieRequests);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @deprecated Use {@link #CronetDataSource(CronetEngine, Executor)} and {@link
   *     #setContentTypePredicate(Predicate)}.
   */
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

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties Optional default {@link RequestProperties} to be sent to the
   *     server as HTTP headers on every request.
   * @deprecated Use {@link #CronetDataSource(CronetEngine, Executor, int, int, boolean,
   *     RequestProperties)} and {@link #setContentTypePredicate(Predicate)}.
   */
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

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will handle responses. This may
   *     be a direct executor (i.e. executes tasks on the calling thread) in order to avoid a thread
   *     hop from Cronet's internal network thread to the response handling thread. However, to
   *     avoid slowing down overall network performance, care must be taken to make sure response
   *     handling is a fast operation when using a direct executor.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from {@link
   *     #open(DataSpec)}.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   * @param defaultRequestProperties Optional default {@link RequestProperties} to be sent to the
   *     server as HTTP headers on every request.
   * @param handleSetCookieRequests Whether "Set-Cookie" requests on redirect should be forwarded to
   *     the redirect url in the "Cookie" header.
   * @deprecated Use {@link #CronetDataSource(CronetEngine, Executor, int, int, boolean,
   *     RequestProperties, boolean)} and {@link #setContentTypePredicate(Predicate)}.
   */
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
        Clock.DEFAULT,
        defaultRequestProperties,
        handleSetCookieRequests);
    this.contentTypePredicate = contentTypePredicate;
  }

  /* package */ CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      Clock clock,
      @Nullable RequestProperties defaultRequestProperties,
      boolean handleSetCookieRequests) {
    super(/* isNetwork= */ true);
    this.urlRequestCallback = new UrlRequestCallback();
    this.cronetEngine = Assertions.checkNotNull(cronetEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.clock = Assertions.checkNotNull(clock);
    this.defaultRequestProperties = defaultRequestProperties;
    this.handleSetCookieRequests = handleSetCookieRequests;
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
      if (exception != null) {
        throw new OpenException(exception, dataSpec, getStatus(urlRequest));
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
    if (responseCode < 200 || responseCode > 299) {
      InvalidResponseCodeException exception =
          new InvalidResponseCodeException(
              responseCode,
              responseInfo.getHttpStatusText(),
              responseInfo.getAllHeaders(),
              dataSpec);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }

    // Check for a valid content type.
    Predicate<String> contentTypePredicate = this.contentTypePredicate;
    if (contentTypePredicate != null) {
      List<String> contentTypeHeaders = responseInfo.getAllHeaders().get(CONTENT_TYPE);
      String contentType = isEmpty(contentTypeHeaders) ? null : contentTypeHeaders.get(0);
      if (contentType != null && !contentTypePredicate.evaluate(contentType)) {
        throw new InvalidContentTypeException(contentType, dataSpec);
      }
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Calculate the content length.
    if (!isCompressed(responseInfo)) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = getContentLength(responseInfo);
      }
    } else {
      // If the response is compressed then the content length will be that of the compressed data
      // which isn't what we want. Always use the dataSpec length in this case.
      bytesRemaining = dataSpec.length;
    }

    opened = true;
    transferStarted(dataSpec);

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

    ByteBuffer readBuffer = this.readBuffer;
    if (readBuffer == null) {
      readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
      readBuffer.limit(0);
      this.readBuffer = readBuffer;
    }
    while (!readBuffer.hasRemaining()) {
      // Fill readBuffer with more data from Cronet.
      operation.close();
      readBuffer.clear();
      readInternal(castNonNull(readBuffer));

      if (finished) {
        bytesRemaining = 0;
        return C.RESULT_END_OF_INPUT;
      } else {
        // The operation didn't time out, fail or finish, and therefore data must have been read.
        readBuffer.flip();
        Assertions.checkState(readBuffer.hasRemaining());
        if (bytesToSkip > 0) {
          int bytesSkipped = (int) Math.min(readBuffer.remaining(), bytesToSkip);
          readBuffer.position(readBuffer.position() + bytesSkipped);
          bytesToSkip -= bytesSkipped;
        }
      }
    }

    int bytesRead = Math.min(readBuffer.remaining(), readLength);
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
      // Skip all the bytes we can from readBuffer if there are still bytes to skip.
      if (bytesToSkip != 0) {
        if (bytesToSkip >= readBuffer.remaining()) {
          bytesToSkip -= readBuffer.remaining();
          readBuffer.position(readBuffer.limit());
        } else {
          readBuffer.position(readBuffer.position() + (int) bytesToSkip);
          bytesToSkip = 0;
        }
      }

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

    boolean readMore = true;
    while (readMore) {
      // If bytesToSkip > 0, read into intermediate buffer that we can discard instead of caller's
      // buffer. If we do not need to skip bytes, we may write to buffer directly.
      final boolean useCallerBuffer = bytesToSkip == 0;

      operation.close();

      if (!useCallerBuffer) {
        if (readBuffer == null) {
          readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
        } else {
          readBuffer.clear();
        }
        if (bytesToSkip < READ_BUFFER_SIZE_BYTES) {
          readBuffer.limit((int) bytesToSkip);
        }
      }

      // Fill buffer with more data from Cronet.
      readInternal(useCallerBuffer ? buffer : castNonNull(readBuffer));

      if (finished) {
        bytesRemaining = 0;
        return C.RESULT_END_OF_INPUT;
      } else {
        // The operation didn't time out, fail or finish, and therefore data must have been read.
        Assertions.checkState(
            useCallerBuffer
                ? readLength > buffer.remaining()
                : castNonNull(readBuffer).position() > 0);
        // If we meant to skip bytes, subtract what was left and repeat, otherwise, continue.
        if (useCallerBuffer) {
          readMore = false;
        } else {
          bytesToSkip -= castNonNull(readBuffer).position();
        }
      }
    }

    final int bytesRead = readLength - buffer.remaining();
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

  // Internal methods.

  private UrlRequest.Builder buildRequestBuilder(DataSpec dataSpec) throws IOException {
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

    if (dataSpec.httpBody != null && !requestHeaders.containsKey(CONTENT_TYPE)) {
      throw new IOException("HTTP request with non-empty body must set Content-Type");
    }

    // Set the Range header.
    if (dataSpec.position != 0 || dataSpec.length != C.LENGTH_UNSET) {
      StringBuilder rangeValue = new StringBuilder();
      rangeValue.append("bytes=");
      rangeValue.append(dataSpec.position);
      rangeValue.append("-");
      if (dataSpec.length != C.LENGTH_UNSET) {
        rangeValue.append(dataSpec.position + dataSpec.length - 1);
      }
      requestBuilder.addHeader("Range", rangeValue.toString());
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
   * Reads up to {@code buffer.remaining()} bytes of data from {@code currentUrlRequest} and stores
   * them into {@code buffer}. If there is an error and {@code buffer == readBuffer}, then it resets
   * the current {@code readBuffer} object so that it is not reused in the future.
   *
   * @param buffer The ByteBuffer into which the read data is stored. Must be a direct ByteBuffer.
   * @throws HttpDataSourceException If an error occurs reading from the source.
   */
  @SuppressWarnings("ReferenceEquality")
  private void readInternal(ByteBuffer buffer) throws HttpDataSourceException {
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
      throw new HttpDataSourceException(
          new InterruptedIOException(),
          castNonNull(currentDataSpec),
          HttpDataSourceException.TYPE_READ);
    } catch (SocketTimeoutException e) {
      // The operation is ongoing so replace buffer to avoid it being written to by this
      // operation during a subsequent request.
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      throw new HttpDataSourceException(
          e, castNonNull(currentDataSpec), HttpDataSourceException.TYPE_READ);
    }

    if (exception != null) {
      throw new HttpDataSourceException(
          exception, castNonNull(currentDataSpec), HttpDataSourceException.TYPE_READ);
    }
  }

  private static boolean isCompressed(UrlResponseInfo info) {
    for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
      if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
        return !entry.getValue().equalsIgnoreCase("identity");
      }
    }
    return false;
  }

  private static long getContentLength(UrlResponseInfo info) {
    long contentLength = C.LENGTH_UNSET;
    Map<String, List<String>> headers = info.getAllHeaders();
    List<String> contentLengthHeaders = headers.get("Content-Length");
    String contentLengthHeader = null;
    if (!isEmpty(contentLengthHeaders)) {
      contentLengthHeader = contentLengthHeaders.get(0);
      if (!TextUtils.isEmpty(contentLengthHeader)) {
        try {
          contentLength = Long.parseLong(contentLengthHeader);
        } catch (NumberFormatException e) {
          Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
        }
      }
    }
    List<String> contentRangeHeaders = headers.get("Content-Range");
    if (!isEmpty(contentRangeHeaders)) {
      String contentRangeHeader = contentRangeHeaders.get(0);
      Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRangeHeader);
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
            // change one of them to reduce the size of a request, but it is unlikely anybody
            // would increase it.
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

  private static String parseCookies(List<String> setCookieHeaders) {
    return TextUtils.join(";", setCookieHeaders);
  }

  private static void attachCookies(UrlRequest.Builder requestBuilder, String cookies) {
    if (TextUtils.isEmpty(cookies)) {
      return;
    }
    requestBuilder.addHeader(COOKIE, cookies);
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

  @EnsuresNonNullIf(result = false, expression = "#1")
  private static boolean isEmpty(@Nullable List<?> list) {
    return list == null || list.isEmpty();
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
                  responseCode, info.getHttpStatusText(), info.getAllHeaders(), dataSpec);
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

      List<String> setCookieHeaders = info.getAllHeaders().get(SET_COOKIE);
      if (isEmpty(setCookieHeaders)) {
        request.followRedirect();
        return;
      }

      urlRequest.cancel();
      DataSpec redirectUrlDataSpec;
      if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        // For POST redirects that aren't 307 or 308, the redirect is followed but request is
        // transformed into a GET.
        redirectUrlDataSpec =
            new DataSpec(
                Uri.parse(newLocationUrl),
                DataSpec.HTTP_METHOD_GET,
                /* httpBody= */ null,
                dataSpec.absoluteStreamPosition,
                dataSpec.position,
                dataSpec.length,
                dataSpec.key,
                dataSpec.flags,
                dataSpec.httpRequestHeaders);
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
