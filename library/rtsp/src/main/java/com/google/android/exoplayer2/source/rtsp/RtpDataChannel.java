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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import java.io.IOException;

/** An RTP {@link DataSource}. */
/* package */ interface RtpDataChannel extends DataSource {

  /** Creates {@link RtpDataChannel} for RTSP streams. */
  interface Factory {

    /**
     * Creates a new {@link RtpDataChannel} instance for RTP data transfer.
     *
     * @throws IOException If the data channels failed to open.
     */
    RtpDataChannel createAndOpenDataChannel(int trackId) throws IOException;
  }

  /** Returns the RTSP transport header for this {@link RtpDataChannel} */
  String getTransport();

  /**
   * Returns the receiving port or channel used by the underlying transport protocol, {@link
   * C#INDEX_UNSET} if the data channel is not opened.
   */
  int getLocalPort();

  /**
   * Returns whether the data channel is using sideband binary data to transmit RTP packets. For
   * example, RTP-over-RTSP.
   */
  boolean usesSidebandBinaryData();

  /**
   * Writes data to the channel.
   *
   * <p>The channel owns the written buffer, the user must not alter its content after writing.
   *
   * @param buffer The buffer from which data should be written. The buffer should be full.
   */
  void write(byte[] buffer);
}
