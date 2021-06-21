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

import com.google.android.exoplayer2.source.rtsp.RtspMessageChannel.InterleavedBinaryDataListener;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

/** Transmits media RTP packets periodically. */
/* package */ final class RtpPacketTransmitter {

  private static final byte[] END_OF_STREAM = new byte[0];

  private final ImmutableList<String> packets;
  private final HandlerWrapper transmissionHandler;
  private final long transmissionIntervalMs;

  private RtspMessageChannel.InterleavedBinaryDataListener binaryDataListener;
  private int packetIndex;
  private volatile boolean isTransmitting;

  /**
   * Creates a new instance.
   *
   * @param rtpPacketStreamDump The {@link RtpPacketStreamDump} to provide RTP packets.
   * @param clock The {@link Clock} to use.
   */
  public RtpPacketTransmitter(RtpPacketStreamDump rtpPacketStreamDump, Clock clock) {
    this.packets = ImmutableList.copyOf(rtpPacketStreamDump.packets);
    this.transmissionHandler =
        clock.createHandler(Util.getCurrentOrMainLooper(), /* callback= */ null);
    this.transmissionIntervalMs = rtpPacketStreamDump.transmissionIntervalMs;
  }

  /**
   * Starts transmitting binary data to the {@link InterleavedBinaryDataListener}.
   *
   * <p>Calling this method after starting the transmission has no effect.
   */
  public void startTransmitting(InterleavedBinaryDataListener binaryDataListener) {
    if (isTransmitting) {
      return;
    }

    this.binaryDataListener = binaryDataListener;
    packetIndex = 0;
    isTransmitting = true;
    transmissionHandler.post(this::transmitNextPacket);
  }

  /** Stops transmitting, if transmitting has started. */
  private void stopTransmitting() {
    if (!isTransmitting) {
      return;
    }

    signalEndOfStream();
    transmissionHandler.removeCallbacksAndMessages(/* token= */ null);
    isTransmitting = false;
  }

  private void transmitNextPacket() {
    if (packetIndex == packets.size()) {
      stopTransmitting();
      return;
    }

    byte[] data = Util.getBytesFromHexString(packets.get(packetIndex++));
    binaryDataListener.onInterleavedBinaryDataReceived(data);
    transmissionHandler.postDelayed(this::transmitNextPacket, transmissionIntervalMs);
  }

  private void signalEndOfStream() {
    binaryDataListener.onInterleavedBinaryDataReceived(END_OF_STREAM);
  }
}
