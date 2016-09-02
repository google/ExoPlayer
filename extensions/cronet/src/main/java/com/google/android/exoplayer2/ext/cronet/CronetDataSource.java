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

import android.net.Uri;
import android.os.ConditionVariable;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.TraceUtil;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.chromium.net.CronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequestException;
import org.chromium.net.UrlResponseInfo;

/**
 * DataSource without intermediate buffer based on Cronet API set using UrlRequest.
 * <p>This class's methods are organized in the sequence of expected calls.
 */
public class CronetDataSource extends UrlRequest.Callback implements HttpDataSource {

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

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private static final String TAG = "CronetDataSource";
  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
  // The size of read buffer passed to cronet UrlRequest.read().
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  /* package */ static final int IDLE_CONNECTION = 5;
  /* package */ static final int OPENING_CONNECTION = 2;
  /* package */ static final int CONNECTED_CONNECTION = 3;
  /* package */ static final int OPEN_CONNECTION = 4;

  private final CronetEngine cronetEngine;
  private final Executor executor;
  private final Predicate<String> contentTypePredicate;
  private final TransferListener transferListener;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final Map<String, String> requestProperties;
  private final ConditionVariable operation;
  private final ByteBuffer readBuffer;
  private final Clock clock;

  private UrlRequest currentUrlRequest;
  private DataSpec currentDataSpec;
  private UrlResponseInfo responseInfo;

  /* package */ volatile int connectionState;
  private volatile String currentUrl;
  private volatile long currentConnectTimeoutMs;
  private volatile HttpDataSourceException exception;
  private volatile long contentLength;
  private volatile AtomicLong expectedBytesRemainingToRead;
  private volatile boolean hasData;
  private volatile boolean responseFinished;

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param transferListener A listener.
   */
  public CronetDataSource(CronetEngine cronetEngine, Executor executor,
      Predicate<String> contentTypePredicate, TransferListener transferListener) {
    this(cronetEngine, executor, contentTypePredicate, transferListener,
        DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, false);
  }

  /**
   * @param cronetEngine A CronetEngine.
   * @param executor The {@link java.util.concurrent.Executor} that will perform the requests.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then an {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param transferListener A listener.
   * @param connectTimeoutMs The connection timeout, in milliseconds.
   * @param readTimeoutMs The read timeout, in milliseconds.
   * @param resetTimeoutOnRedirects Whether the connect timeout is reset when a redirect occurs.
   */
  public CronetDataSource(CronetEngine cronetEngine, Executor executor,
      Predicate<String> contentTypePredicate, TransferListener transferListener,
      int connectTimeoutMs, int readTimeoutMs, boolean resetTimeoutOnRedirects) {
    this(cronetEngine, executor, contentTypePredicate, transferListener, connectTimeoutMs,
        readTimeoutMs, resetTimeoutOnRedirects, new SystemClock());
  }

  /* package */ CronetDataSource(CronetEngine cronetEngine, Executor executor,
      Predicate<String> contentTypePredicate, TransferListener transferListener,
      int connectTimeoutMs, int readTimeoutMs, boolean resetTimeoutOnRedirects, Clock clock) {
    this.cronetEngine = Assertions.checkNotNull(cronetEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.contentTypePredicate = contentTypePredicate;
    this.transferListener = transferListener;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.clock = Assertions.checkNotNull(clock);
    readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
    requestProperties = new HashMap<>();
    operation = new ConditionVariable();
    connectionState = IDLE_CONNECTION;
  }

  @Override
  public void setRequestProperty(String name, String value) {
    synchronized (requestProperties) {
      requestProperties.put(name, value);
    }
  }

  @Override
  public void clearRequestProperty(String name) {
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
  public Map<String, List<String>> getResponseHeaders() {
    return responseInfo == null ? null : responseInfo.getAllHeaders();
  }

  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    TraceUtil.beginSection("CronetDataSource.open");
    try {
      Assertions.checkNotNull(dataSpec);
      synchronized (this) {
        Assertions.checkState(connectionState == IDLE_CONNECTION, "Connection already open");
        connectionState = OPENING_CONNECTION;
      }

      operation.close();
      resetConnectTimeout();
      startRequest(dataSpec);
      boolean requestStarted = blockUntilConnectTimeout();

      if (exception != null) {
        // An error occurred opening the connection.
        throw exception;
      } else if (!requestStarted) {
        // The timeout was reached before the connection was opened.
        throw new OpenException(new SocketTimeoutException(), dataSpec, getCurrentRequestStatus());
      }

      // Connection was opened.
      if (transferListener != null) {
        transferListener.onTransferStart(this, dataSpec);
      }
      connectionState = OPEN_CONNECTION;
      return contentLength;
    } finally {
      TraceUtil.endSection();
    }
  }

  private void startRequest(DataSpec dataSpec) throws HttpDataSourceException {
    currentUrl = dataSpec.uri.toString();
    currentDataSpec = dataSpec;
    UrlRequest.Builder urlRequestBuilder = new UrlRequest.Builder(currentUrl, this, executor,
        cronetEngine);
    fillCurrentRequestHeader(urlRequestBuilder);
    fillCurrentRequestPostBody(urlRequestBuilder, dataSpec);
    currentUrlRequest = urlRequestBuilder.build();
    currentUrlRequest.start();
  }

  private void fillCurrentRequestHeader(UrlRequest.Builder urlRequestBuilder) {
    synchronized (requestProperties) {
      for (Entry<String, String> headerEntry : requestProperties.entrySet()) {
        urlRequestBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
      }
    }
    if (currentDataSpec.position == 0 && currentDataSpec.length == C.LENGTH_UNSET) {
      // Not required.
      return;
    }
    StringBuilder rangeValue = new StringBuilder();
    rangeValue.append("bytes=");
    rangeValue.append(currentDataSpec.position);
    rangeValue.append("-");
    if (currentDataSpec.length != C.LENGTH_UNSET) {
      rangeValue.append(currentDataSpec.position + currentDataSpec.length - 1);
    }
    urlRequestBuilder.addHeader("Range", rangeValue.toString());
  }

  private void fillCurrentRequestPostBody(UrlRequest.Builder urlRequestBuilder, DataSpec dataSpec)
      throws HttpDataSourceException {
    if (dataSpec.postBody != null) {
      if (!requestProperties.containsKey("Content-Type")) {
        throw new OpenException("POST requests must set a Content-Type header", dataSpec,
            getCurrentRequestStatus());
      }
      urlRequestBuilder.setUploadDataProvider(
          new ByteArrayUploadDataProvider(dataSpec.postBody), executor);
    }
  }

  @Override
  public synchronized void onFailed(
       UrlRequest request, UrlResponseInfo info, UrlRequestException error) {
    if (request != currentUrlRequest) {
      return;
    }
    if (connectionState == OPENING_CONNECTION) {
      IOException cause = error.getErrorCode() == UrlRequestException.ERROR_HOSTNAME_NOT_RESOLVED
          ? new UnknownHostException() : error;
      exception = new OpenException(cause, currentDataSpec, getCurrentRequestStatus());
    } else if (connectionState == OPEN_CONNECTION) {
      readBuffer.limit(0);
      exception = new HttpDataSourceException(error, currentDataSpec,
          HttpDataSourceException.TYPE_READ);
    }
    operation.open();
  }

  @Override
  public synchronized void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
    if (request != currentUrlRequest) {
      return;
    }
    TraceUtil.beginSection("CronetDataSource.onResponseStarted");
    try {
      validateResponse(info);
      responseInfo = info;
      // Check content length.
      contentLength = getContentLength(info.getAllHeaders());
      // If a specific length is requested and a specific length is returned but the 2 don't match
      // it's an error.
      if (currentDataSpec.length != C.LENGTH_UNSET
          && contentLength != C.LENGTH_UNSET
          && currentDataSpec.length != contentLength) {
        throw new OpenException("Content length did not match requested length", currentDataSpec,
            getCurrentRequestStatus());
      }

      if (contentLength > 0) {
        expectedBytesRemainingToRead = new AtomicLong(contentLength);
      }

      // Keep track of redirects.
      currentUrl = responseInfo.getUrl();
      connectionState = CONNECTED_CONNECTION;
    } catch (HttpDataSourceException e) {
      exception = e;
    } finally {
      operation.open();
      TraceUtil.endSection();
    }
  }

  private void validateResponse(UrlResponseInfo info) throws HttpDataSourceException {
    // Check for a valid response code.
    int responseCode = info.getHttpStatusCode();
    if (responseCode < 200 || responseCode > 299) {
      InvalidResponseCodeException exception = new InvalidResponseCodeException(
          responseCode, info.getAllHeaders(), currentDataSpec);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }
    // Check for a valid content type.
    try {
      String contentType = info.getAllHeaders().get("Content-Type").get(0);
      if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
        throw new InvalidContentTypeException(contentType, currentDataSpec);
      }
    } catch (IndexOutOfBoundsException e) {
      throw new InvalidContentTypeException(null, currentDataSpec);
    }
  }

  private long getContentLength(Map<String, List<String>> headers) {
    // Logic copied from {@code DefaultHttpDataSource}
    long contentLength = C.LENGTH_UNSET;
    List<String> contentLengthHeader = headers.get("Content-Length");
    if (contentLengthHeader != null
        && !contentLengthHeader.isEmpty()
        && !TextUtils.isEmpty(contentLengthHeader.get(0))) {
      try {
        contentLength = Long.parseLong(contentLengthHeader.get(0));
      } catch (NumberFormatException e) {
        log(Log.ERROR, "Unexpected Content-Length [" + contentLengthHeader + "]");
      }
    }
    List<String> contentRangeHeader = headers.get("Content-Range");
    if (contentRangeHeader != null
        && !contentRangeHeader.isEmpty()
        && !TextUtils.isEmpty(contentRangeHeader.get(0))) {
      Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRangeHeader.get(0));
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
            log(Log.WARN, "Inconsistent headers [" + contentLengthHeader + "] ["
                + contentRangeHeader + "]");
            contentLength = Math.max(contentLength, contentLengthFromRange);
          }
        } catch (NumberFormatException e) {
          log(Log.ERROR, "Unexpected Content-Range [" + contentRangeHeader + "]");
        }
      }
    }
    return contentLength;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    TraceUtil.beginSection("CronetDataSource.read");
    try {
      synchronized (this) {
        if (connectionState != OPEN_CONNECTION) {
          throw new IllegalStateException("Connection not ready");
        }
      }

      // If being asked to read beyond the amount of bytes initially requested, return
      // RESULT_END_OF_INPUT.
      if (expectedBytesRemainingToRead != null && expectedBytesRemainingToRead.get() <= 0) {
        return C.RESULT_END_OF_INPUT;
      }

      if (!hasData) {
        // Read more data from cronet.
        operation.close();
        currentUrlRequest.read(readBuffer);
        if (!operation.block(readTimeoutMs)) {
          throw new HttpDataSourceException(
              new SocketTimeoutException(), currentDataSpec, HttpDataSourceException.TYPE_READ);
        }
        if (exception != null) {
          throw exception;
        }
        // The expected response length is unknown, but cronet has indicated that the request
        // already finished successfully.
        if (responseFinished) {
          return C.RESULT_END_OF_INPUT;
        }
      }

      int bytesRead = Math.min(readBuffer.remaining(), readLength);

      readBuffer.get(buffer, offset, bytesRead);

      if (!readBuffer.hasRemaining()) {
        readBuffer.clear();
        hasData = false;
      }

      if (expectedBytesRemainingToRead != null) {
        expectedBytesRemainingToRead.addAndGet(-bytesRead);
      }

      if (transferListener != null && bytesRead >= 0) {
        transferListener.onBytesTransferred(this, bytesRead);
      }
      return bytesRead;
    } finally {
      TraceUtil.endSection();
    }
  }

  @Override
  public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
    if (request != currentUrlRequest) {
      return;
    }
    if (currentDataSpec.postBody != null) {
      int responseCode = info.getHttpStatusCode();
      // The industry standard is to disregard POST redirects when the status code is 307 or 308.
      // For other redirect response codes the POST request is converted to a GET request and the
      // redirect is followed.
      if (responseCode == 307 || responseCode == 308) {
        exception = new OpenException("POST request redirected with 307 or 308 response code",
            currentDataSpec, getCurrentRequestStatus());
        operation.open();
        return;
      }
    }
    if (resetTimeoutOnRedirects) {
      resetConnectTimeout();
    }
    request.followRedirect();
  }

  @Override
  public synchronized void onReadCompleted(UrlRequest request, UrlResponseInfo info,
      ByteBuffer buffer) {
    if (request != currentUrlRequest) {
      return;
    }
    readBuffer.flip();
    if (readBuffer.limit() > 0) {
      hasData = true;
    }
    operation.open();
  }

  @Override
  public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
    if (request != currentUrlRequest) {
      return;
    }
    responseFinished = true;
    operation.open();
  }

  @Override
  public synchronized void close() {
    TraceUtil.beginSection("CronetDataSource.close");
    try {
      if (currentUrlRequest != null) {
        currentUrlRequest.cancel();
        currentUrlRequest = null;
      }
      readBuffer.clear();
      currentDataSpec = null;
      currentUrl = null;
      exception = null;
      contentLength = 0;
      hasData = false;
      responseInfo = null;
      expectedBytesRemainingToRead = null;
      responseFinished = false;
      if (transferListener != null && connectionState == OPEN_CONNECTION) {
        transferListener.onTransferEnd(this);
      }
    } finally {
      connectionState = IDLE_CONNECTION;
      TraceUtil.endSection();
    }
  }

  @Override
  public Uri getUri() {
    return Uri.parse(currentUrl);
  }

  private void log(int priority, String message) {
    if (Log.isLoggable(TAG, priority)) {
      Log.println(priority, TAG, message);
    }
  }

  private int getCurrentRequestStatus() {
    if (currentUrlRequest == null) {
      return UrlRequest.Status.IDLE;
    }
    final ConditionVariable conditionVariable = new ConditionVariable();
    final AtomicInteger result = new AtomicInteger();
    currentUrlRequest.getStatus(new UrlRequest.StatusListener() {
      @Override
      public void onStatus(int status) {
        result.set(status);
        conditionVariable.open();
      }
    });
    return result.get();
  }

  private boolean blockUntilConnectTimeout() {
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

}
