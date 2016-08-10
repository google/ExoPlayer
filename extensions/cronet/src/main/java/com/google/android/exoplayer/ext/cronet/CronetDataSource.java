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
package com.google.android.exoplayer.ext.cronet;

import android.os.ConditionVariable;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.upstream.TransferListener;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Clock;
import com.google.android.exoplayer.util.Predicate;
import com.google.android.exoplayer.util.TraceUtil;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
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
  public static class OpenException extends HttpDataSourceException {

    private final Integer cronetConnectionStatus;

    public OpenException(IOException cause, DataSpec dataSpec) {
      this(cause, dataSpec, null);
    }

    public OpenException(IOException cause, DataSpec dataSpec, Integer cronetConnectionStatus) {
      super(cause, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(String errorMessage, DataSpec dataSpec) {
      super(errorMessage, dataSpec, TYPE_OPEN);
      this.cronetConnectionStatus = null;
    }

    /**
     * Returns the status of the connection establishment near the moment when the OpenException
     * occurred. The Integer values match definitions at https://goo.gl/dOSBIQ. Value is
     * null when unknown.
     */
    public Integer getCronetConnectionStatus() {
      return cronetConnectionStatus;
    }

  }

  private static final String TAG = "CronetDataSource";
  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
  // The size of read buffer passed to cronet UrlRequest.read(). Cronet does not always fill the
  // buffer completely before calling back the listener.
  private static final int READ_BUFFER_SIZE_BYTES = 32 * 1024;

  enum ConnectionState {
    NEW,
    OPENING,
    CONNECTED,
    OPEN,
    CLOSED,
  }

  private final CronetEngine cronetEngine;
  private final Executor executor;
  private final Predicate<String> contentTypePredicate;
  private final TransferListener transferListener;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final boolean useExtendableTimeoutOperation;
  private final Map<String, String> headers;
  private final ConditionVariable operation;
  private final ByteBuffer readBuffer;
  private final Clock clock;

  private UrlRequest currentUrlRequest;
  private DataSpec currentDataSpec;
  private UrlResponseInfo responseInfo;

  volatile ConnectionState connectionState;
  TimeoutCheckerRunnable timeoutCheckerRunnable;
  ExtendableTimeoutConditionVariable extendableTimeoutOperation;
  private volatile String currentUrl;
  private volatile HttpDataSourceException exception;
  private volatile long contentLength;
  private volatile AtomicLong expectedBytesRemainingToRead;
  private volatile boolean hasData;
  private volatile int connectionStatus;
  private volatile boolean responseFinished;

  public CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      Clock clock,
      Predicate<String> contentTypePredicate,
      TransferListener transferListener,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      boolean useExtendableTimeoutOperation) {
    this.cronetEngine = Assertions.checkNotNull(cronetEngine);
    this.executor = Assertions.checkNotNull(executor);
    this.clock = Assertions.checkNotNull(clock);
    this.contentTypePredicate = contentTypePredicate;
    this.transferListener = transferListener;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.useExtendableTimeoutOperation = useExtendableTimeoutOperation;
    this.headers = new HashMap<>();
    this.connectionState = ConnectionState.NEW;
    this.readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE_BYTES);
    this.operation = new ConditionVariable();
    readBuffer.clear();
    if (resetTimeoutOnRedirects && useExtendableTimeoutOperation) {
      extendableTimeoutOperation = new ExtendableTimeoutConditionVariable();
    }
  }

  @Override
  public void setRequestProperty(String name, String value) {
    headers.put(name, value);
  }

  @Override
  public void clearRequestProperty(String name) {
    headers.remove(name);
  }

  @Override
  public void clearAllRequestProperties() {
    headers.clear();
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
        if (connectionState != ConnectionState.NEW && connectionState != ConnectionState.CLOSED) {
          throw new IllegalStateException("Connection already open");
        }
        connectionState = ConnectionState.OPENING;
      }

      createRequest(dataSpec);

      if (resetTimeoutOnRedirects) {
        if (useExtendableTimeoutOperation) {
          extendableTimeoutOperation.extendTimeout(connectTimeoutMs);
          currentUrlRequest.start();
          extendableTimeoutOperation.block();
        } else {
          operation.close();
          timeoutCheckerRunnable = new TimeoutCheckerRunnable();
          executor.execute(timeoutCheckerRunnable);
          currentUrlRequest.start();
          operation.block();
        }
      } else {
        operation.close();
        currentUrlRequest.start();
        operation.block(connectTimeoutMs);
      }

      if (exception != null) {
        throw exception;
      } else if (connectionState != ConnectionState.CONNECTED) {
        // If the connection timed out. Get the last connection status then throw with exception.
        final ConditionVariable getStatusOperation = new ConditionVariable();
        getStatusOperation.close();
        currentUrlRequest.getStatus(new UrlRequest.StatusListener() {
          @Override
          public void onStatus(int i) {
            connectionStatus = i;
            getStatusOperation.open();
          }
        });
        getStatusOperation.block();
        throw new OpenException(new SocketTimeoutException(), dataSpec, connectionStatus);
      }

      // At this point it's connected.
      if (transferListener != null) {
        transferListener.onTransferStart();
      }
      connectionState = ConnectionState.OPEN;
      return contentLength;
    } finally {
      TraceUtil.endSection();
    }
  }

  private void createRequest(DataSpec dataSpec) throws HttpDataSourceException {
    currentUrl = dataSpec.uri.toString();
    currentDataSpec = dataSpec;
    UrlRequest.Builder urlRequestBuilder = new UrlRequest.Builder(
        currentUrl,
        this, // UrlRequest.Callback
        executor,
        cronetEngine);
    fillCurrentRequestHeader(urlRequestBuilder);
    fillCurrentRequestPostBody(urlRequestBuilder, dataSpec);
    currentUrlRequest = urlRequestBuilder.build();
  }

  private void fillCurrentRequestHeader(UrlRequest.Builder urlRequestBuilder) {
    for (Entry<String, String> headerEntry : headers.entrySet()) {
      urlRequestBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
    }
    if (currentDataSpec.position == 0 && currentDataSpec.length == C.LENGTH_UNBOUNDED) {
      // Not required.
      return;
    }
    StringBuilder rangeValue = new StringBuilder();
    rangeValue.append("bytes=");
    rangeValue.append(currentDataSpec.position);
    rangeValue.append("-");
    if (currentDataSpec.length != C.LENGTH_UNBOUNDED) {
      rangeValue.append(currentDataSpec.position + currentDataSpec.length - 1);
    }
    urlRequestBuilder.addHeader("Range", rangeValue.toString());
  }

  private void fillCurrentRequestPostBody(UrlRequest.Builder urlRequestBuilder, DataSpec dataSpec)
      throws HttpDataSourceException {
    if (dataSpec.postBody != null) {
      if (!headers.containsKey("Content-Type")) {
        throw new OpenException("POST requests must set a Content-Type header", dataSpec);
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
    if (connectionState == ConnectionState.OPENING) {
      IOException cause = error.getErrorCode() == UrlRequestException.ERROR_HOSTNAME_NOT_RESOLVED
          ? new UnknownHostException() : error;
      exception = new OpenException(cause, currentDataSpec);
      if (timeoutCheckerRunnable != null) {
        timeoutCheckerRunnable.cancel();
        timeoutCheckerRunnable = null;
      }
      if (extendableTimeoutOperation != null) {
        extendableTimeoutOperation.open();
      } else {
        operation.open();
      }
    } else if (connectionState == ConnectionState.OPEN) {
      readBuffer.limit(0);
      exception = new HttpDataSourceException(
          error, currentDataSpec, HttpDataSourceException.TYPE_READ);
      operation.open();
    }
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
      if (currentDataSpec.length != C.LENGTH_UNBOUNDED
          && contentLength != C.LENGTH_UNBOUNDED
          && currentDataSpec.length != contentLength) {
        throw new OpenException("Content length did not match requested length", currentDataSpec);
      }

      if (contentLength > 0) {
        expectedBytesRemainingToRead = new AtomicLong(contentLength);
      }

      // Keep track of redirects.
      currentUrl = responseInfo.getUrl();
      connectionState = ConnectionState.CONNECTED;
    } catch (HttpDataSourceException e) {
      exception = e;
    } finally {
      if (timeoutCheckerRunnable != null) {
        timeoutCheckerRunnable.cancel();
        timeoutCheckerRunnable = null;
      }
      if (extendableTimeoutOperation != null) {
        extendableTimeoutOperation.open();
      } else {
        operation.open();
      }
      TraceUtil.endSection();
    }
  }

  private void validateResponse(UrlResponseInfo info) throws HttpDataSourceException {
    // Check for a valid response code.
    int responseCode = info.getHttpStatusCode();
    if (responseCode < 200 || responseCode > 299) {
      throw new HttpDataSource.InvalidResponseCodeException(
          responseCode,
          info.getAllHeaders(),
          currentDataSpec);
    }

    // Check for a valid content type.
    try {
      String contentType = info.getAllHeaders().get("Content-Type").get(0);
      if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
        throw new HttpDataSource.InvalidContentTypeException(contentType, currentDataSpec);
      }
    } catch (IndexOutOfBoundsException e) {
      throw new HttpDataSource.InvalidContentTypeException(null, currentDataSpec);
    }
  }

  private long getContentLength(Map<String, List<String>> headers) {
    // Logic copied from {@code DefaultHttpDataSource}
    long contentLength = C.LENGTH_UNBOUNDED;
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
        if (connectionState != ConnectionState.OPEN) {
          throw new IllegalStateException("Connection not ready");
        }
      }

      // If being asked to read beyond the amount of bytes initially requested, return
      // RESULT_END_OF_INPUT.
      if (expectedBytesRemainingToRead != null && expectedBytesRemainingToRead.get() <= 0) {
        return C.RESULT_END_OF_INPUT;
      }

      // If buffer hasn't been fully consumed previously, reuse. Otherwise, read more from cronet.
      if (!hasData) {
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
        transferListener.onBytesTransferred(bytesRead);
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
        exception = new OpenException(
            "POST request redirected with 307 or 308 response code.", currentDataSpec);
        if (extendableTimeoutOperation != null) {
          extendableTimeoutOperation.open();
        } else {
          operation.open();
        }
        return;
      }
    }
    if (timeoutCheckerRunnable != null) {
      timeoutCheckerRunnable.resetTimeoutLimit();
    }
    if (extendableTimeoutOperation != null) {
      extendableTimeoutOperation.extendTimeout(connectTimeoutMs);
    }
    request.followRedirect();
  }

  @Override
  public synchronized void onReadCompleted(
      UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
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

      if (timeoutCheckerRunnable != null) {
        timeoutCheckerRunnable.cancel();
        timeoutCheckerRunnable = null;
      }

      currentDataSpec = null;
      currentUrl = null;
      exception = null;
      contentLength = 0;
      readBuffer.clear();
      hasData = false;
      responseInfo = null;
      connectionStatus = 0;
      expectedBytesRemainingToRead = null;
      responseFinished = false;

      if (transferListener != null && connectionState == ConnectionState.OPEN) {
        transferListener.onTransferEnd();
      }
    } finally {
      connectionState = ConnectionState.CLOSED;
      TraceUtil.endSection();
    }
  }

  @Override
  public String getUri() {
    return currentUrl;
  }

  private void log(int priority, String message) {
    if (Log.isLoggable(TAG, priority)) {
      Log.println(priority, TAG, message);
    }
  }

  /**
   * Similar to ConditionVariable but allows the timeout to be extended.
   */
  class ExtendableTimeoutConditionVariable {

    ConditionVariable operation;
    private volatile long timeoutElapsedTimeMs;

    ExtendableTimeoutConditionVariable() {
      operation = new ConditionVariable();
    }

    void block() {
      while (true) {
        long now = clock.elapsedRealtime();
        if (now >= timeoutElapsedTimeMs) {
          return;
        }
        long timeout = timeoutElapsedTimeMs - now;
        if (operation.block(timeout)) {
          return;
        }
      }
    }

    void extendTimeout(long timeout) {
      operation.close();
      timeoutElapsedTimeMs = Math.max(timeoutElapsedTimeMs, clock.elapsedRealtime() + timeout);
    }

    void open() {
      operation.open();
    }

  }

  class TimeoutCheckerRunnable implements Runnable {

    private volatile long connectTimeoutElapsedTimeMs;
    private boolean cancelled;

    private TimeoutCheckerRunnable() {
      resetTimeoutLimit();
    }

    @Override
    public void run() {
      try {
        while (true) {
          if (checkTimeout()) {
            return;
          }

          Thread.sleep(200);
        }
      } catch (InterruptedException e) {
        // Shouldn't happen but if it does, it results in timing out.
      }
    }

    boolean checkTimeout() {
      if (cancelled) {
        return true;
      }
      if (clock.elapsedRealtime() > connectTimeoutElapsedTimeMs) {
        operation.open();
        return true;
      }
      return false;
    }

    private void resetTimeoutLimit() {
      connectTimeoutElapsedTimeMs = clock.elapsedRealtime() + connectTimeoutMs;
    }

    private void cancel() {
      cancelled = true;
    }

  }

}
