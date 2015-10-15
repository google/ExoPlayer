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
package com.google.android.exoplayer.ext.okhttp;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.upstream.TransferListener;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Predicate;

import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.Util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link HttpDataSource} that delegates to Square's {@link OkHttpClient}.
 */
public class OkHttpDataSource implements HttpDataSource {

  private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

  private final OkHttpClient okHttpClient;
  private final String userAgent;
  private final Predicate<String> contentTypePredicate;
  private final TransferListener listener;
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
   * @param client An {@link OkHttpClient} for use by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a
   *     {@link com.google.android.exoplayer.upstream.HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   */
  public OkHttpDataSource(OkHttpClient client, String userAgent,
      Predicate<String> contentTypePredicate) {
    this(client, userAgent, contentTypePredicate, null);
  }

  /**
   * @param client An {@link OkHttpClient} for use by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a
   *     {@link com.google.android.exoplayer.upstream.HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   * @param listener An optional listener.
   */
  public OkHttpDataSource(OkHttpClient client, String userAgent,
      Predicate<String> contentTypePredicate, TransferListener listener) {
    this(client, userAgent, contentTypePredicate, listener, null);
  }

  /**
   * @param client An {@link OkHttpClient} for use by the source.
   * @param userAgent The User-Agent string that should be used.
   * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
   *     predicate then a
   *     {@link com.google.android.exoplayer.upstream.HttpDataSource.InvalidContentTypeException} is
   *     thrown from {@link #open(DataSpec)}.
   * @param listener An optional listener.
   * @param cacheControl An optional {@link CacheControl} which sets all requests' Cache-Control
   *     header. For example, you could force the network response for all requests.
   *
   */
  public OkHttpDataSource(OkHttpClient client, String userAgent,
      Predicate<String> contentTypePredicate, TransferListener listener,
      CacheControl cacheControl) {
    this.okHttpClient = Assertions.checkNotNull(client);
    this.userAgent = Assertions.checkNotEmpty(userAgent);
    this.contentTypePredicate = contentTypePredicate;
    this.listener = listener;
    this.cacheControl = cacheControl;
    this.requestProperties = new HashMap<>();
  }

  @Override
  public String getUri() {
    return response == null ? null : response.request().urlString();
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
      response = okHttpClient.newCall(request).execute();
      responseByteStream = response.body().byteStream();
    } catch (IOException e) {
      throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
          dataSpec);
    }

    int responseCode = response.code();

    // Check for a valid response code.
    if (!response.isSuccessful()) {
      Map<String, List<String>> headers = request.headers().toMultimap();
      closeConnectionQuietly();
      throw new InvalidResponseCodeException(responseCode, headers, dataSpec);
    }

    // Check for a valid content type.
    String contentType = response.body().contentType().toString();
    if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
      closeConnectionQuietly();
      throw new InvalidContentTypeException(contentType, dataSpec);
    }

    // If we requested a range starting from a non-zero position and received a 200 rather than a
    // 206, then the server does not support partial requests. We'll need to manually skip to the
    // requested position.
    bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    // Determine the length of the data to be read, after skipping.
    try {
      long contentLength = response.body().contentLength();
      bytesToRead = dataSpec.length != C.LENGTH_UNBOUNDED ? dataSpec.length
          : contentLength != -1 ? contentLength - bytesToSkip
          : C.LENGTH_UNBOUNDED;
    } catch (IOException e) {
      closeConnectionQuietly();
      throw new HttpDataSourceException(e, dataSpec);
    }

    opened = true;
    if (listener != null) {
      listener.onTransferStart();
    }

    return bytesToRead;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
    try {
      skipInternal();
      return readInternal(buffer, offset, readLength);
    } catch (IOException e) {
      throw new HttpDataSourceException(e, dataSpec);
    }
  }

  @Override
  public void close() throws HttpDataSourceException {
    if (opened) {
      opened = false;
      if (listener != null) {
        listener.onTransferEnd();
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
   * is returned. If the total length is unknown, {@link C#LENGTH_UNBOUNDED} is returned.
   *
   * @return The remaining length, or {@link C#LENGTH_UNBOUNDED}.
   */
  protected final long bytesRemaining() {
    return bytesToRead == C.LENGTH_UNBOUNDED ? bytesToRead : bytesToRead - bytesRead;
  }

  /**
   * Establishes a connection.
   */
  private Request makeRequest(DataSpec dataSpec) {
    long position = dataSpec.position;
    long length = dataSpec.length;
    boolean allowGzip = (dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) != 0;

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
    if (!(position == 0 && length == C.LENGTH_UNBOUNDED)) {
      String rangeRequest = "bytes=" + position + "-";
      if (length != C.LENGTH_UNBOUNDED) {
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
        listener.onBytesTransferred(read);
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
    readLength = bytesToRead == C.LENGTH_UNBOUNDED ? readLength
        : (int) Math.min(readLength, bytesToRead - bytesRead);
    if (readLength == 0) {
      // We've read all of the requested data.
      return C.RESULT_END_OF_INPUT;
    }

    int read = responseByteStream.read(buffer, offset, readLength);
    if (read == -1) {
      if (bytesToRead != C.LENGTH_UNBOUNDED && bytesToRead != bytesRead) {
        // The server closed the connection having not sent sufficient data.
        throw new EOFException();
      }
      return C.RESULT_END_OF_INPUT;
    }

    bytesRead += read;
    if (listener != null) {
      listener.onBytesTransferred(read);
    }
    return read;
  }

  /**
   * Closes the current connection quietly, if there is one.
   */
  private void closeConnectionQuietly() {
    Util.closeQuietly(response.body());
    response = null;
    responseByteStream = null;
  }

}
