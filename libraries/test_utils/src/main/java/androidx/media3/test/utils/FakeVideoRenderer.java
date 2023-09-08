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

package androidx.media3.test.utils;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link FakeRenderer} that supports {@link C#TRACK_TYPE_VIDEO}. */
@UnstableApi
public class FakeVideoRenderer extends FakeRenderer {

  private final HandlerWrapper handler;
  private final VideoRendererEventListener eventListener;
  private final DecoderCounters decoderCounters;
  private final AtomicReference<VideoSize> videoSizeRef = new AtomicReference<>();
  private @MonotonicNonNull Format format;
  @Nullable private Object output;
  private long streamOffsetUs;
  private boolean renderedFirstFrameAfterReset;
  private boolean mayRenderFirstFrameAfterEnableIfNotStarted;
  private boolean renderedFirstFrameAfterEnable;

  public FakeVideoRenderer(HandlerWrapper handler, VideoRendererEventListener eventListener) {
    super(C.TRACK_TYPE_VIDEO);
    this.handler = handler;
    this.eventListener = eventListener;
    decoderCounters = new DecoderCounters();
    videoSizeRef.set(VideoSize.UNKNOWN);
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    handler.post(() -> eventListener.onVideoEnabled(decoderCounters));
    mayRenderFirstFrameAfterEnableIfNotStarted = mayRenderStartOfStream;
    renderedFirstFrameAfterEnable = false;
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
    streamOffsetUs = offsetUs;
    renderedFirstFrameAfterReset = false;
  }

  @Override
  protected void onStopped() {
    super.onStopped();
    handler.post(() -> eventListener.onDroppedFrames(/* count= */ 0, /* elapsedMs= */ 0));
    handler.post(
        () ->
            eventListener.onVideoFrameProcessingOffset(
                /* totalProcessingOffsetUs= */ 400000, /* frameCount= */ 10));
  }

  @Override
  protected void onDisabled() {
    super.onDisabled();
    videoSizeRef.set(VideoSize.UNKNOWN);
    handler.post(
        () -> {
          eventListener.onVideoDisabled(decoderCounters);
          eventListener.onVideoSizeChanged(VideoSize.UNKNOWN);
        });
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    renderedFirstFrameAfterReset = false;
  }

  @Override
  protected void onFormatChanged(Format format) {
    handler.post(
        () -> eventListener.onVideoInputFormatChanged(format, /* decoderReuseEvaluation= */ null));
    handler.post(
        () ->
            eventListener.onVideoDecoderInitialized(
                /* decoderName= */ "fake.video.decoder",
                /* initializedTimestampMs= */ SystemClock.elapsedRealtime(),
                /* initializationDurationMs= */ 0));
    this.format = format;
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VIDEO_OUTPUT:
        output = message;
        renderedFirstFrameAfterReset = false;
        break;

      case Renderer.MSG_SET_AUDIO_ATTRIBUTES:
      case Renderer.MSG_SET_AUDIO_SESSION_ID:
      case Renderer.MSG_SET_AUX_EFFECT_INFO:
      case Renderer.MSG_SET_CAMERA_MOTION_LISTENER:
      case Renderer.MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
      case Renderer.MSG_SET_SCALING_MODE:
      case Renderer.MSG_SET_SKIP_SILENCE_ENABLED:
      case Renderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
      case Renderer.MSG_SET_VOLUME:
      case Renderer.MSG_SET_WAKEUP_LISTENER:
      default:
        super.handleMessage(messageType, message);
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
      handler.post(
          () -> {
            VideoSize videoSize =
                new VideoSize(
                    format.width,
                    format.height,
                    format.rotationDegrees,
                    format.pixelWidthHeightRatio);
            if (!Objects.equals(videoSize, videoSizeRef.get())) {
              eventListener.onVideoSizeChanged(videoSize);
              videoSizeRef.set(videoSize);
            }
          });
      handler.post(
          () ->
              eventListener.onRenderedFirstFrame(
                  output, /* renderTimeMs= */ SystemClock.elapsedRealtime()));
      renderedFirstFrameAfterReset = true;
      renderedFirstFrameAfterEnable = true;
    }
    return shouldProcess;
  }
}
