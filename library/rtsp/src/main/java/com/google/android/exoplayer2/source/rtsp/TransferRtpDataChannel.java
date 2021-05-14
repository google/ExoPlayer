/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/** An {@link RtpDataChannel} that transfers received data in-memory. */
/* package */ final class TransferRtpDataChannel extends BaseDataSource implements RtpDataChannel {

  private static final String DEFAULT_TCP_TRANSPORT_FORMAT =
      "RTP/AVP/TCP;unicast;interleaved=%d-%d";
  private static final long TIMEOUT_MS = 8_000;

  private final LinkedBlockingQueue<byte[]> packetQueue;

  private byte[] unreadData;
  private int channelNumber;

  /** Creates a new instance. */
  public TransferRtpDataChannel() {
    super(/* isNetwork= */ true);
    packetQueue = new LinkedBlockingQueue<>();
    unreadData = new byte[0];
    channelNumber = C.INDEX_UNSET;
  }

  @Override
  public String getTransport() {
    checkState(channelNumber != C.INDEX_UNSET); // Assert open() is called.
    return Util.formatInvariant(DEFAULT_TCP_TRANSPORT_FORMAT, channelNumber, channelNumber + 1);
  }

  @Override
  public int getLocalPort() {
    return channelNumber;
  }

  @Override
  public boolean usesSidebandBinaryData() {
    return true;
  }

  @Override
  public long open(DataSpec dataSpec) {
    this.channelNumber = dataSpec.uri.getPort();
    return C.LENGTH_UNSET;
  }

  @Override
  public void close() {}

  @Nullable
  @Override
  public Uri getUri() {
    return null;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }

    int bytesRead = 0;
    int bytesToRead = min(length, unreadData.length);
    System.arraycopy(unreadData, /* srcPos= */ 0, target, offset, bytesToRead);
    bytesRead += bytesToRead;
    unreadData = Arrays.copyOfRange(unreadData, bytesToRead, unreadData.length);

    if (bytesRead == length) {
      return bytesRead;
    }

    @Nullable byte[] data;
    try {
      // TODO(internal b/172331505) Consider move the receiving timeout logic to an upper level
      // (maybe RtspClient). There is no actual socket receiving here.
      data = packetQueue.poll(TIMEOUT_MS, MILLISECONDS);
      if (data == null) {
        throw new IOException(new SocketTimeoutException());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return C.RESULT_END_OF_INPUT;
    }

    bytesToRead = min(length - bytesRead, data.length);
    System.arraycopy(data, /* srcPos= */ 0, target, offset + bytesRead, bytesToRead);
    if (bytesToRead < data.length) {
      unreadData = Arrays.copyOfRange(data, bytesToRead, data.length);
    }
    return bytesRead + bytesToRead;
  }

  @Override
  public void write(byte[] buffer) {
    packetQueue.add(buffer);
  }
}
