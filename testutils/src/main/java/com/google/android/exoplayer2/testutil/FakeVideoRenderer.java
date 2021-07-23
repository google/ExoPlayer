/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.VideoSize;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link FakeRenderer} that supports {@link C#TRACK_TYPE_VIDEO}. */
public class FakeVideoRenderer extends FakeRenderer {

  private final VideoRendererEventListener.EventDispatcher eventDispatcher;
  private final DecoderCounters decoderCounters;
  private @MonotonicNonNull Format format;
  @Nullable private Object output;
  private long streamOffsetUs;
  private boolean renderedFirstFrameAfterReset;
  private boolean mayRenderFirstFrameAfterEnableIfNotStarted;
  private boolean renderedFirstFrameAfterEnable;

  public FakeVideoRenderer(Handler handler, VideoRendererEventListener eventListener) {
    super(C.TRACK_TYPE_VIDEO);
    eventDispatcher = new VideoRendererEventListener.EventDispatcher(handler, eventListener);
    decoderCounters = new DecoderCounters();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    eventDispatcher.enabled(decoderCounters);
    mayRenderFirstFrameAfterEnableIfNotStarted = mayRenderStartOfStream;
    renderedFirstFrameAfterEnable = false;
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    super.onStreamChanged(formats, startPositionUs, offsetUs);
    streamOffsetUs = offsetUs;
    renderedFirstFrameAfterReset = false;
  }

  @Override
  protected void onStopped() {
    super.onStopped();
    eventDispatcher.droppedFrames(/* droppedFrameCount= */ 0, /* elapsedMs= */ 0);
    eventDispatcher.reportVideoFrameProcessingOffset(
        /* totalProcessingOffsetUs= */ 400000, /* frameCount= */ 10);
  }

  @Override
  protected void onDisabled() {
    super.onDisabled();
    eventDispatcher.disabled(decoderCounters);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    renderedFirstFrameAfterReset = false;
  }

  @Override
  protected void onFormatChanged(Format format) {
    eventDispatcher.inputFormatChanged(format, /* decoderReuseEvaluation= */ null);
    eventDispatcher.decoderInitialized(
        /* decoderName= */ "fake.video.decoder",
        /* initializedTimestampMs= */ SystemClock.elapsedRealtime(),
        /* initializationDurationMs= */ 0);
    this.format = format;
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object payload) throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VIDEO_OUTPUT:
        output = payload;
        renderedFirstFrameAfterReset = false;
        break;
      default:
        super.handleMessage(messageType, payload);
    }
  }

  @Override
  protected boolean shouldProcessBuffer(long bufferTimeUs, long playbackPositionUs) {
    boolean shouldProcess = super.shouldProcessBuffer(bufferTimeUs, playbackPositionUs);
    boolean shouldRenderFirstFrame =
        output != null
            && (!renderedFirstFrameAfterEnable
                ? (getState() == Renderer.STATE_STARTED
                    || mayRenderFirstFrameAfterEnableIfNotStarted)
                : !renderedFirstFrameAfterReset);
    shouldProcess |= shouldRenderFirstFrame && playbackPositionUs >= streamOffsetUs;
    @Nullable Object output = this.output;
    if (shouldProcess && !renderedFirstFrameAfterReset && output != null) {
      @MonotonicNonNull Format format = Assertions.checkNotNull(this.format);
      eventDispatcher.videoSizeChanged(
          new VideoSize(
              format.width, format.height, format.rotationDegrees, format.pixelWidthHeightRatio));
      eventDispatcher.renderedFirstFrame(output);
      renderedFirstFrameAfterReset = true;
      renderedFirstFrameAfterEnable = true;
    }
    return shouldProcess;
  }
}
