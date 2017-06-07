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
package com.google.android.exoplayer2.testutil;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.ArrayList;

/**
 * A debug extension of {@link DefaultRenderersFactory}. Provides a video renderer that performs
 * video buffer timestamp assertions.
 */
@TargetApi(16)
public class DebugRenderersFactory extends DefaultRenderersFactory {

  public DebugRenderersFactory(Context context,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    super(context, drmSessionManager, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, 0);
  }

  @Override
  protected void buildVideoRenderers(Context context,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, long allowedVideoJoiningTimeMs,
      Handler eventHandler, VideoRendererEventListener eventListener,
      @ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new DebugMediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT,
        allowedVideoJoiningTimeMs, drmSessionManager, eventHandler, eventListener,
        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
  }

  /**
   * Decodes and renders video using {@link MediaCodecVideoRenderer}. Provides buffer timestamp
   * assertions.
   */
  private static class DebugMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

    private static final int ARRAY_SIZE = 1000;

    private final long[] timestampsList = new long[ARRAY_SIZE];

    private int startIndex;
    private int queueSize;
    private int bufferCount;
    private int minimumInsertIndex;

    public DebugMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
        long allowedJoiningTimeMs, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
        Handler eventHandler, VideoRendererEventListener eventListener,
        int maxDroppedFrameCountToNotify) {
      super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, false,
          eventHandler, eventListener, maxDroppedFrameCountToNotify);
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
    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
      super.onInputFormatChanged(newFormat);
      // Ensure timestamps of buffers queued after this format change are never inserted into the
      // queue of expected output timestamps before those of buffers that have already been queued.
      minimumInsertIndex = startIndex + queueSize;
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
      super.onQueueInputBuffer(buffer);
      insertTimestamp(buffer.timeUs);
      maybeShiftTimestampsList();
    }

    @Override
    protected void onProcessedOutputBuffer(long presentationTimeUs) {
      super.onProcessedOutputBuffer(presentationTimeUs);
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
      minimumInsertIndex = 0;
    }

    private void insertTimestamp(long presentationTimeUs) {
      for (int i = startIndex + queueSize - 1; i >= minimumInsertIndex; i--) {
        if (presentationTimeUs >= timestampsList[i]) {
          timestampsList[i + 1] = presentationTimeUs;
          queueSize++;
          return;
        }
        timestampsList[i + 1] = timestampsList[i];
      }
      timestampsList[minimumInsertIndex] = presentationTimeUs;
      queueSize++;
    }

    private void maybeShiftTimestampsList() {
      if (startIndex + queueSize == ARRAY_SIZE) {
        System.arraycopy(timestampsList, startIndex, timestampsList, 0, queueSize);
        minimumInsertIndex -= startIndex;
        startIndex = 0;
      }
    }

    private long dequeueTimestamp() {
      queueSize--;
      startIndex++;
      minimumInsertIndex = Math.max(minimumInsertIndex, startIndex);
      return timestampsList[startIndex - 1];
    }

  }

}
