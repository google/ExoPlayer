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
package com.google.android.exoplayer.playbacktests.util;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;

import java.nio.ByteBuffer;

/**
 * Decodes and renders video using {@link MediaCodecVideoTrackRenderer}. Provides buffer timestamp
 * assertions.
 */
@TargetApi(16)
public class DebugMediaCodecVideoTrackRenderer extends MediaCodecVideoTrackRenderer {

  private static final int ARRAY_SIZE = 1000;

  private final long[] timestampsList = new long[ARRAY_SIZE];

  private int startIndex;
  private int queueSize;
  private int bufferCount;

  public DebugMediaCodecVideoTrackRenderer(Context context, SampleSource source,
      MediaCodecSelector mediaCodecSelector, int videoScalingMode, long allowedJoiningTimeMs,
      Handler eventHandler, EventListener eventListener, int maxDroppedFrameCountToNotify) {
    super(context, source, mediaCodecSelector, videoScalingMode, allowedJoiningTimeMs, null, false,
        eventHandler, eventListener, maxDroppedFrameCountToNotify);
    startIndex = 0;
    queueSize = 0;
  }

  @Override
  protected void releaseCodec() {
    super.releaseCodec();
    clearTimestamps();
  }

  @Override
  protected void flushCodec() throws ExoPlaybackException {
    super.flushCodec();
    clearTimestamps();
  }

  @Override
  protected void onQueuedInputBuffer(
      long presentationTimeUs, ByteBuffer buffer, int bufferSize, boolean sampleEncrypted) {
    insertTimestamp(presentationTimeUs);
    maybeShiftTimestampsList();
  }

  @Override
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    bufferCount++;
    long expectedTimestampUs = dequeueTimestamp();
    if (expectedTimestampUs != presentationTimeUs) {
      throw new IllegalStateException("Expected to dequeue video buffer with presentation "
          + "timestamp: " + expectedTimestampUs + ". Instead got: " + presentationTimeUs
          + " (Processed buffers since last flush: " + bufferCount + ").");
    }
  }

  private void clearTimestamps() {
    startIndex = 0;
    queueSize = 0;
    bufferCount = 0;
  }

  private void insertTimestamp(long presentationTimeUs) {
    for (int i = startIndex + queueSize - 1; i >= startIndex; i--) {
      if (presentationTimeUs >= timestampsList[i]) {
        timestampsList[i + 1] = presentationTimeUs;
        queueSize++;
        return;
      }
      timestampsList[i + 1] = timestampsList[i];
    }
    timestampsList[startIndex] = presentationTimeUs;
    queueSize++;
  }

  private void maybeShiftTimestampsList() {
    if (startIndex + queueSize == ARRAY_SIZE) {
      System.arraycopy(timestampsList, startIndex, timestampsList, 0, queueSize);
      startIndex = 0;
    }
  }

  private long dequeueTimestamp() {
    startIndex++;
    queueSize--;
    return timestampsList[startIndex - 1];
  }
}
