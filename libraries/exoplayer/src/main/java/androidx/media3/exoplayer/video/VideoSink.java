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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;

/** A sink that consumes decoded video frames. */
@UnstableApi
public interface VideoSink {

  /** Thrown by {@link VideoSink} implementations. */
  final class VideoSinkException extends Exception {
    /**
     * The {@link Format} of the frames set to the {@link VideoSink} when this exception occurred.
     */
    public final Format format;

    /** Creates a new instance. */
    public VideoSinkException(Throwable cause, Format format) {
      super(cause);
      this.format = format;
    }
  }

  /** Listener for {@link VideoSink} events. */
  interface Listener {
    /** Called when the sink renderers the first frame. */
    void onFirstFrameRendered(VideoSink videoSink);

    /** Called when the sink dropped a frame. */
    void onFrameDropped(VideoSink videoSink);

    /**
     * Called before a frame is rendered for the first time since setting the surface, and each time
     * there's a change in the size, rotation or pixel aspect ratio of the video being rendered.
     */
    void onVideoSizeChanged(VideoSink videoSink, VideoSize videoSize);

    /** Called when the {@link VideoSink} encountered an error. */
    void onError(VideoSink videoSink, VideoSinkException videoSinkException);

    /** A no-op listener implementation. */
    Listener NO_OP =
        new Listener() {
          @Override
          public void onFirstFrameRendered(VideoSink videoSink) {}

          @Override
          public void onFrameDropped(VideoSink videoSink) {}

          @Override
          public void onVideoSizeChanged(VideoSink videoSink, VideoSize videoSize) {}

          @Override
          public void onError(VideoSink videoSink, VideoSinkException videoSinkException) {}
        };
  }

  /**
   * Specifies how the input frames are made available to the video sink. One of {@link
   * #INPUT_TYPE_SURFACE} or {@link #INPUT_TYPE_BITMAP}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({INPUT_TYPE_SURFACE, INPUT_TYPE_BITMAP})
  @interface InputType {}

  /** Input frames come from a {@link #getInputSurface surface}. */
  int INPUT_TYPE_SURFACE = 1;

  /** Input frames come from a {@link Bitmap}. */
  int INPUT_TYPE_BITMAP = 2;

  /**
   * Sets a {@link Listener} on this sink. Callbacks are triggered on the supplied {@link Executor}.
   *
   * @param listener The {@link Listener}.
   * @param executor The {@link Executor} to dispatch the callbacks.
   */
  void setListener(Listener listener, Executor executor);

  /**
   * Flushes the video sink.
   *
   * <p>After calling this method, any frames stored inside the video sink are discarded.
   */
  void flush();

  /** Whether the video sink is able to immediately render media from the current position. */
  boolean isReady();

  /**
   * Whether all queued video frames have been rendered, including the frame marked as last buffer.
   */
  boolean isEnded();

  /**
   * Whether frames could be dropped from the sink's {@linkplain #getInputSurface() input surface}.
   */
  boolean isFrameDropAllowedOnInput();

  /** Returns the input {@link Surface} where the video sink consumes input frames from. */
  Surface getInputSurface();

  /** Sets the playback speed. */
  void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed);

  /**
   * Informs the video sink that a new input stream will be queued.
   *
   * @param inputType The {@link InputType} of the stream.
   * @param format The {@link Format} of the stream.
   */
  void registerInputStream(@InputType int inputType, Format format);

  /**
   * Informs the video sink that a frame will be queued to its {@linkplain #getInputSurface() input
   * surface}.
   *
   * @param framePresentationTimeUs The frame's presentation time, in microseconds.
   * @param isLastFrame Whether this is the last frame of the video stream.
   * @return a release timestamp, in nanoseconds, that should be associated when releasing this
   *     frame, or {@link C#TIME_UNSET} if the sink was not able to register the frame and the
   *     caller must try again later.
   */
  long registerInputFrame(long framePresentationTimeUs, boolean isLastFrame);

  /**
   * Provides an input {@link Bitmap} to the video sink.
   *
   * @param inputBitmap The {@link Bitmap} queued to the video sink.
   * @param timestampIterator The times within the current stream that the bitmap should be shown
   *     at. The timestamps should be monotonically increasing.
   * @return Whether the bitmap was queued successfully. A {@code false} value indicates the caller
   *     must try again later.
   */
  boolean queueBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator);

  /**
   * Incrementally renders processed video frames.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   * @throws VideoSinkException If an error occurs during rendering.
   */
  void render(long positionUs, long elapsedRealtimeUs) throws VideoSinkException;
}
