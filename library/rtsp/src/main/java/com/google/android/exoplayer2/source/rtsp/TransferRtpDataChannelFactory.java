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

import android.util.Log;

/** Factory for {@link TransferRtpDataChannel}. */
/* package */ final class TransferRtpDataChannelFactory implements RtpDataChannel.Factory {

  private static final int INTERLEAVED_CHANNELS_PER_TRACK = 2;

  private final long timeoutMs;
  String TAG = Constants.TAG + "TransferRtpDataChannelFactory.java ";

  /**
   * Creates a new instance.
   *
   * @param timeoutMs A positive number of milliseconds to wait before lack of received RTP packets
   *     is treated as the end of input.
   */
  public TransferRtpDataChannelFactory(long timeoutMs) {
    Log.i(TAG,"Constructor. Timeout =" + timeoutMs);
    this.timeoutMs = timeoutMs;
  }

  @Override
  public RtpDataChannel createAndOpenDataChannel(int trackId) {
    Log.i(TAG,"createAndOpenDataChannel() -> Create the TransferRtpDataChannel");
    TransferRtpDataChannel dataChannel = new TransferRtpDataChannel(timeoutMs);
    dataChannel.open(RtpUtils.getIncomingRtpDataSpec(trackId * INTERLEAVED_CHANNELS_PER_TRACK));
    return dataChannel;
  }
}
