/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.TimedValueQueue;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlaybackException;

/** Controls rendering of video frames. */
/* package */ final class VideoFrameRenderControl {

  /** Receives frames from a {@link VideoFrameRenderControl}. */
  interface FrameRenderer {

    /**
     * Called when the {@link VideoSize} changes. This method is called before the frame that
     * changes the {@link VideoSize} is passed for render.
     */
    void onVideoSizeChanged(VideoSize videoSize);

    /**
     * Called to release the {@linkplain
     * VideoFrameRenderControl#onOutputFrameAvailableForRendering(long)} oldest frame that is
     * available for rendering}.
     *
     * @param renderTimeNs The specific time, in nano seconds, that this frame should be rendered or
     *     {@link VideoFrameProcessor#RENDER_OUTPUT_FRAME_IMMEDIATELY} if the frame needs to be
     *     rendered immediately.
     * @param presentationTimeUs The frame's presentation time, in microseconds, which was announced
     *     with {@link VideoFrameRenderControl#onOutputFrameAvailableForRendering(long)}.
     * @param streamOffsetUs The stream offset, in microseconds, that is associated with this frame.
     * @param isFirstFrame Whether this is the first frame of the stream.
     */
    void renderFrame(
        long renderTimeNs, long presentationTimeUs, long streamOffsetUs, boolean isFirstFrame);

    /**
     * Called to drop the {@linkplain
     * VideoFrameRenderControl#onOutputFrameAvailableForRendering(long)} oldest frame that is
     * available for rendering}.
     */
    void dropFrame();
  }

  private final FrameRenderer frameRenderer;
  private final VideoFrameReleaseControl videoFrameReleaseControl;
  private final VideoFrameReleaseControl.FrameReleaseInfo videoFrameReleaseInfo;
  private final TimedValueQueue<VideoSize> videoSizeChanges;
  private final TimedValueQueue<Long> streamOffsets;
  private final LongArrayQueue presentationTimestampsUs;

  /**
   * Stores a video size that is announced with {@link #onOutputSizeChanged(int, int)} until an
   * output frame is made available. Once the next frame arrives, we associate the frame's timestamp
   * with the video size change in {@link #videoSizeChanges} and clear this field.
   */
  @Nullable private VideoSize pendingOutputVideoSize;

  private VideoSize reportedVideoSize;
  private long outputStreamOffsetUs;
  private long lastPresentationTimeUs;

  /** Creates an instance. */
  public VideoFrameRenderControl(
      FrameRenderer frameRenderer, VideoFrameReleaseControl videoFrameReleaseControl) {
    this.frameRenderer = frameRenderer;
    this.videoFrameReleaseControl = videoFrameReleaseControl;
    videoFrameReleaseInfo = new VideoFrameReleaseControl.FrameReleaseInfo();
    videoSizeChanges = new TimedValueQueue<>();
    streamOffsets = new TimedValueQueue<>();
    presentationTimestampsUs = new LongArrayQueue();
    reportedVideoSize = VideoSize.UNKNOWN;
    lastPresentationTimeUs = C.TIME_UNSET;
  }

  /** Flushes the renderer. */
  public void flush() {
    presentationTimestampsUs.clear();
    lastPresentationTimeUs = C.TIME_UNSET;
    if (streamOffsets.size() > 0) {
      // There is a pending streaming offset change. If seeking within the same stream, keep the
      // pending offset with timestamp zero ensures the offset is applied on the frames after
      // flushing. Otherwise if seeking to another stream, a new offset will be set before a new
      // frame arrives so we'll be able to apply the new offset.
      long lastStreamOffset = getLastAndClear(streamOffsets);
      streamOffsets.add(/* timestamp= */ 0, lastStreamOffset);
    }
    if (pendingOutputVideoSize == null) {
      if (videoSizeChanges.size() > 0) {
        // Do not clear the last pending video size, we still want to report the size change after a
        // flush. If after the flush, a new video size is announced, it will overwrite
        // pendingOutputVideoSize. When the next frame is available for rendering, we will announce
        // pendingOutputVideoSize.
        pendingOutputVideoSize = getLastAndClear(videoSizeChanges);
      }
    } else {
      // we keep the latest value of pendingOutputVideoSize
      videoSizeChanges.clear();
    }
  }

  /** Returns whether the renderer is ready. */
  public boolean isReady() {
    return videoFrameReleaseControl.isReady(/* rendererReady= */ true);
  }

  /**
   * Returns whether the renderer has released a frame after a specific presentation timestamp.
   *
   * @param presentationTimeUs The requested timestamp, in microseconds.
   * @return Whether the renderer has released a frame with a timestamp greater than or equal to
   *     {@code presentationTimeUs}.
   */
  public boolean hasReleasedFrame(long presentationTimeUs) {
    return lastPresentationTimeUs != C.TIME_UNSET && lastPresentationTimeUs >= presentationTimeUs;
  }

  /** Sets the playback speed. */
  public void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed) {
    checkArgument(speed > 0);
    videoFrameReleaseControl.setPlaybackSpeed(speed);
  }

  /**
   * Incrementally renders available video frames.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   */
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    while (!presentationTimestampsUs.isEmpty()) {
      long presentationTimeUs = presentationTimestampsUs.element();
      // Check whether this buffer comes with a new stream offset.
      if (maybeUpdateOutputStreamOffset(presentationTimeUs)) {
        videoFrameReleaseControl.onProcessedStreamChange();
      }
      @VideoFrameReleaseControl.FrameReleaseAction
      int frameReleaseAction =
          videoFrameReleaseControl.getFrameReleaseAction(
              presentationTimeUs,
              positionUs,
              elapsedRealtimeUs,
              outputStreamOffsetUs,
              /* isLastFrame= */ false,
              videoFrameReleaseInfo);
      switch (frameReleaseAction) {
        case VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER:
          return;
        case VideoFrameReleaseControl.FRAME_RELEASE_SKIP:
        case VideoFrameReleaseControl.FRAME_RELEASE_DROP:
        case VideoFrameReleaseControl.FRAME_RELEASE_IGNORE:
          // TODO b/293873191 - Handle very late buffers and drop to key frame. Need to flush
          //  VideoGraph input frames in this case.
          lastPresentationTimeUs = presentationTimeUs;
          dropFrame();
          break;
        case VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY:
        case VideoFrameReleaseControl.FRAME_RELEASE_SCHEDULED:
          lastPresentationTimeUs = presentationTimeUs;
          renderFrame(
              /* shouldRenderImmediately= */ frameReleaseAction
                  == VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
          break;
        default:
          throw new IllegalStateException(String.valueOf(frameReleaseAction));
      }
    }
  }

  /** Called when the size of the available frames has changed. */
  public void onOutputSizeChanged(int width, int height) {
    VideoSize newVideoSize = new VideoSize(width, height);
    if (!Util.areEqual(pendingOutputVideoSize, newVideoSize)) {
      pendingOutputVideoSize = newVideoSize;
    }
  }

  /**
   * Called when a frame is available for rendering.
   *
   * @param presentationTimeUs The frame's presentation timestamp, in microseconds.
   */
  public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
    if (pendingOutputVideoSize != null) {
      videoSizeChanges.add(presentationTimeUs, pendingOutputVideoSize);
      pendingOutputVideoSize = null;
    }
    presentationTimestampsUs.add(presentationTimeUs);
    // TODO b/257464707 - Support extensively modified media.
  }

  public void onStreamOffsetChange(long presentationTimeUs, long streamOffsetUs) {
    streamOffsets.add(presentationTimeUs, streamOffsetUs);
  }

  private void dropFrame() {
    checkStateNotNull(presentationTimestampsUs.remove());
    frameRenderer.dropFrame();
  }

  private void renderFrame(boolean shouldRenderImmediately) {
    long presentationTimeUs = checkStateNotNull(presentationTimestampsUs.remove());

    boolean videoSizeUpdated = maybeUpdateVideoSize(presentationTimeUs);
    if (videoSizeUpdated) {
      frameRenderer.onVideoSizeChanged(reportedVideoSize);
    }
    long renderTimeNs =
        shouldRenderImmediately
            ? VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY
            : videoFrameReleaseInfo.getReleaseTimeNs();
    frameRenderer.renderFrame(
        renderTimeNs,
        presentationTimeUs,
        outputStreamOffsetUs,
        videoFrameReleaseControl.onFrameReleasedIsFirstFrame());
  }

  private boolean maybeUpdateOutputStreamOffset(long presentationTimeUs) {
    @Nullable Long newOutputStreamOffsetUs = streamOffsets.pollFloor(presentationTimeUs);
    if (newOutputStreamOffsetUs != null && newOutputStreamOffsetUs != outputStreamOffsetUs) {
      outputStreamOffsetUs = newOutputStreamOffsetUs;
      return true;
    }
    return false;
  }

  private boolean maybeUpdateVideoSize(long presentationTimeUs) {
    @Nullable VideoSize videoSize = videoSizeChanges.pollFloor(presentationTimeUs);
    if (videoSize == null) {
      return false;
    }
    if (!videoSize.equals(VideoSize.UNKNOWN) && !videoSize.equals(reportedVideoSize)) {
      reportedVideoSize = videoSize;
      return true;
    }
    return false;
  }

  private static <T> T getLastAndClear(TimedValueQueue<T> queue) {
    checkArgument(queue.size() > 0);
    while (queue.size() > 1) {
      queue.pollFirst();
    }
    return checkNotNull(queue.pollFirst());
  }
}
