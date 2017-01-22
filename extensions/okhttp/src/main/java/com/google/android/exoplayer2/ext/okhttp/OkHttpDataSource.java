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
package com.google.android.exoplayer2.ext.okhttp;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Predicate;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * An {@link HttpDataSource} that delegates to Square's {@link Call.Factory}.
 */
public class OkHttpDataSource implements HttpDataSource {

  private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

  private final Call.Factory callFactory;
  private final String userAgent;
  private final Predicate<String> contentTypePredicate;
  private final TransferListener<? super OkHttpDataSource> listener;
  private final CacheControl cacheControl;
  private final HashMap<String, String> requestProperties;

  private DataSpec dataSpec;
  private Response response;
  private InputStream responseByteStream;
  private boolean opened;

  private long bytesToSkip;
  private long bytesToRead;

  private long bytesSkipped;
  private long bytesRead;

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a InvalidContentTypeException} is thrown from {@link #open(DataSpec)}.
   */
  public OkHttpDataSource(Call.Factory callFactory, String userAgent,
      Predicate<String> contentTypePredicate) {
    this(callFactory, userAgent, contentTypePredicate, null);
  }

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param listener An optional listener.
   */
  public OkHttpDataSource(Call.Factory callFactory, String userAgent,
      Predicate<String> contentTypePredicate, TransferListener<? super OkHttpDataSource> listener) {
    this(callFactory, userAgent, contentTypePredicate, listener, null);
  }

  /**
   * @param callFactory A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
   *     by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a {@link InvalidContentTypeException} is thrown from
   *     {@link #open(DataSpec)}.
   * @param listener An optional listener.
   * @param cacheControl An optional {@link CacheControl} for setting the Cache-Control header.
   */
  public OkHttpDataSource(Call.Factory callFactory, String userAgent,
      Predicate<String> contentTypePredicate, TransferListener<? super OkHttpDataSource> listener,
      CacheControl cacheControl) {
    this.callFactory = Assertions.checkNotNull(callFactory);
    this.userAgent = Assertions.checkNotEmpty(userAgent);
    this.contentTypePredicate = contentTypePredicate;
    this.listener = listener;
    this.cacheControl = cacheControl;
    this.requestProperties = new HashMap<>();
  }

  @Override
  public Uri getUri() {
    return response == null ? null : Uri.parse(response.request().url().toString());
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return response == null ? null : response.headers().toMultimap();
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
    this.bytesSkipped = 0;
    Request request = makeRequest(dataSpec);
    try {
      response = callFactory.newCall(request).execute();
      responseByteStream = response.body().byteStream();
    } catch (IOException e) {
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec, HttpDataSourceException.TYPE_OPEN);
    }

    int responseCode = response.code();

    // Check for a valid response code.
    if (!response.isSuccessful()) {
      Map<String, List<String>> headers = request.headers().toMultimap();
      closeConnectionQuietly();
      InvalidResponseCodeException exception = new InvalidResponseCodeException(
          responseCode, headers, dataSpec);
      if (responseCode == 416) {
        exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
      }
      throw exception;
    }

    // Check for a valid content type.
    MediaType mediaType = response.body().contentType();
    String contentType = mediaType != null ? mediaType.toString() : null;
    if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
      closeConnectionQuietly();
      throw new InvalidContentTypeException(contentType, dataSpec);
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Determine the length of the data to be read, after skipping.
    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesToRead = dataSpec.length;
    } else {
      long contentLength = response.body().contentLength();
      bytesToRead = contentLength != -1 ? (contentLength - bytesToSkip) : C.LENGTH_UNSET;
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    return bytesToRead;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    try {
      skipInternal();
      return readInternal(buffer, offset, readLength);
    } catch (IOException e) {
      throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ);
    }
  }

  @Override
  public void close() throws HttpDataSourceException {
    if (opened) {
      opened = false;
      if (listener != null) {
        listener.onTransferEnd(this);
      }
      closeConnectionQuietly();
    }
  }

  /**
   * Returns the number of bytes that have been skipped since the most recent call to
   * {@link #open(DataSpec)}.
   *
   * @return The number of bytes skipped.
   */
  protected final long bytesSkipped() {
    return bytesSkipped;
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
   * is returned. If the total length is unknown, {@link C#LENGTH_UNSET} is returned.
   *
   * @return The remaining length, or {@link C#LENGTH_UNSET}.
   */
  protected final long bytesRemaining() {
    return bytesToRead == C.LENGTH_UNSET ? bytesToRead : bytesToRead - bytesRead;
  }

  /**
   * Establishes a connection.
   */
  private Request makeRequest(DataSpec dataSpec) {
    long position = dataSpec.position;
    long length = dataSpec.length;
    boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

    HttpUrl url = HttpUrl.parse(dataSpec.uri.toString());
    Request.Builder builder = new Request.Builder().url(url);
    if (cacheControl != null) {
      builder.cacheControl(cacheControl);
    }
    synchronized (requestProperties) {
      for (Map.Entry<String, String> property : requestProperties.entrySet()) {
        builder.addHeader(property.getKey(), property.getValue());
      }
    }
    if (!(position == 0 && length == C.LENGTH_UNSET)) {
      String rangeRequest = "bytes=" + position + "-";
      if (length != C.LENGTH_UNSET) {
        rangeRequest += (position + length - 1);
      }
      builder.addHeader("Range", rangeRequest);
    }
    builder.addHeader("User-Agent", userAgent);
    if (!allowGzip) {
      builder.addHeader("Accept-Encoding", "identity");
    }
    if (dataSpec.postBody != null) {
      builder.post(RequestBody.create(null, dataSpec.postBody));
    }
    return builder.build();
  }

  /**
   * Skips any bytes that need skipping. Else does nothing.
   * <p>
   * This implementation is based roughly on {@code libcore.io.Streams.skipByReading()}.
   *
   * @throws InterruptedIOException If the thread is interrupted during the operation.
   * @throws EOFException If the end of the input stream is reached before the bytes are skipped.
   */
  private void skipInternal() throws IOException {
    if (bytesSkipped == bytesToSkip) {
      return;
    }

    // Acquire the shared skip buffer.
    byte[] skipBuffer = skipBufferReference.getAndSet(null);
    if (skipBuffer == null) {
      skipBuffer = new byte[4096];
    }

    while (bytesSkipped != bytesToSkip) {
      int readLength = (int) Math.min(bytesToSkip - bytesSkipped, skipBuffer.length);
      int read = responseByteStream.read(skipBuffer, 0, readLength);
      if (Thread.interrupted()) {
        throw new InterruptedIOException();
      }
      if (read == -1) {
        throw new EOFException();
      }
      bytesSkipped += read;
      if (listener != null) {
        listener.onBytesTransferred(this, read);
      }
    }

    // Release the shared skip buffer.
    skipBufferReference.set(skipBuffer);
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
      readLength = (int) Math.min(readLength, bytesRemaining);
    }

    int read = responseByteStream.read(buffer, offset, readLength);
    if (read == -1) {
      if (bytesToRead != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new EOFException();
      }
      return C.RESULT_END_OF_INPUT;
    }

    bytesRead += read;
    if (listener != null) {
      listener.onBytesTransferred(this, read);
    }
    return read;
  }

  /**
   * Closes the current connection quietly, if there is one.
   */
  private void closeConnectionQuietly() {
    response.body().close();
    response = null;
    responseByteStream = null;
  }

}
