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

package com.google.android.exoplayer2.effect;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Joiner;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A debugging tracing utility.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DebugTraceUtil {

  private static final int MUXER_CAN_WRITE_SAMPLE_RECORD_COUNT = 10;

  /** The timestamps at which the decoder received end of stream signal, in milliseconds. */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> DECODER_RECEIVE_EOS_TIMES_MS = new ArrayDeque<>();

  /** The timestamps at which the decoder signalled end of stream, in milliseconds. */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> DECODER_SIGNAL_EOS_TIMES_MS = new ArrayDeque<>();

  /**
   * The timestamps at which {@code VideoFrameProcessor} received end of stream signal from the
   * decoder, in milliseconds.
   */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> VIDEO_FRAME_PROCESSOR_RECEIVE_DECODER_EOS_TIMES_MS =
      new ArrayDeque<>();

  /**
   * The timestamps at which {@code ExternalTextureManager} signalled end of current input stream,
   * in milliseconds.
   */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOCIS_TIMES_MS =
      new ArrayDeque<>();

  /**
   * The timestamps at which {@code VideoFrameProcessor} signalled end of stream, in milliseconds.
   */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> VIDEO_FRAME_PROCESSOR_SIGNAL_EOS_TIMES_MS = new ArrayDeque<>();

  /** The timestamps at which the encoder received end of stream signal, in milliseconds. */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> ENCODER_RECEIVE_EOS_TIMES_MS = new ArrayDeque<>();

  /**
   * The last {@link #MUXER_CAN_WRITE_SAMPLE_RECORD_COUNT} values returned by {@code
   * MuxerWrapper#canWriteSample}.
   */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Boolean> MUXER_CAN_WRITE_VIDEO_SAMPLE = new ArrayDeque<>();

  /** The timestamps at which the muxer video track is stopped, in milliseconds. */
  @GuardedBy("DebugTraceUtil.class")
  private static final Queue<Long> MUXER_TRACK_END_TIMES_MS = new ArrayDeque<>();

  /** The input {@link Format} of the latest media item. */
  @GuardedBy("DebugTraceUtil.class")
  private static @Nullable Format latestVideoInputFormat = null;

  /** The number of decoded frames. */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfDecodedFrames = 0;

  /** The number of frames made available on {@code VideoFrameProcessor}'s input surface. */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfFramesRenderedToVideoFrameProcessorInput = 0;

  /**
   * The number of frames sent to the {@link GlShaderProgram} after they arrive on {@code
   * VideoFrameProcessor}'s input surface.
   */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfFramesDequeuedFromVideoProcessorInput = 0;

  /** The number of frames rendered to {@code VideoFrameProcessor}'s output. */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfFramesRenderedToVideoFrameProcessorOutput = 0;

  /** The number of encoded frames. */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfEncodedFrames = 0;

  /** The number of video frames written to the muxer. */
  @GuardedBy("DebugTraceUtil.class")
  private static int numberOfMuxedFrames = 0;

  private DebugTraceUtil() {}

  public static synchronized void reset() {
    latestVideoInputFormat = null;
    numberOfDecodedFrames = 0;
    numberOfFramesRenderedToVideoFrameProcessorInput = 0;
    numberOfFramesDequeuedFromVideoProcessorInput = 0;
    numberOfFramesRenderedToVideoFrameProcessorOutput = 0;
    numberOfEncodedFrames = 0;
    numberOfMuxedFrames = 0;
    DECODER_RECEIVE_EOS_TIMES_MS.clear();
    DECODER_SIGNAL_EOS_TIMES_MS.clear();
    VIDEO_FRAME_PROCESSOR_RECEIVE_DECODER_EOS_TIMES_MS.clear();
    EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOCIS_TIMES_MS.clear();
    VIDEO_FRAME_PROCESSOR_SIGNAL_EOS_TIMES_MS.clear();
    ENCODER_RECEIVE_EOS_TIMES_MS.clear();
    MUXER_CAN_WRITE_VIDEO_SAMPLE.clear();
    MUXER_TRACK_END_TIMES_MS.clear();
  }

  public static synchronized void recordLatestVideoInputFormat(Format format) {
    latestVideoInputFormat = format;
  }

  public static synchronized void recordDecodedFrame() {
    numberOfDecodedFrames++;
  }

  public static synchronized void recordFrameRenderedToVideoFrameProcessorInput() {
    numberOfFramesRenderedToVideoFrameProcessorInput++;
  }

  public static synchronized void recordFrameDequeuedFromVideoFrameProcessorInput() {
    numberOfFramesDequeuedFromVideoProcessorInput++;
  }

  public static synchronized void recordFrameRenderedToVideoFrameProcessorOutput() {
    numberOfFramesRenderedToVideoFrameProcessorOutput++;
  }

  public static synchronized void recordEncodedFrame() {
    numberOfEncodedFrames++;
  }

  public static synchronized void recordMuxerInput(@C.TrackType int trackType) {
    if (trackType == C.TRACK_TYPE_VIDEO) {
      numberOfMuxedFrames++;
    }
  }

  public static synchronized void recordDecoderReceiveEos() {
    DECODER_RECEIVE_EOS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordDecoderSignalEos() {
    DECODER_SIGNAL_EOS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordVideoFrameProcessorReceiveDecoderEos() {
    VIDEO_FRAME_PROCESSOR_RECEIVE_DECODER_EOS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordExternalInputManagerSignalEndOfCurrentInputStream() {
    EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOCIS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordVideoFrameProcessorSignalEos() {
    VIDEO_FRAME_PROCESSOR_SIGNAL_EOS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordEncoderReceiveEos() {
    ENCODER_RECEIVE_EOS_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
  }

  public static synchronized void recordMuxerCanAddSample(
      @C.TrackType int trackType, boolean canAddSample) {
    if (trackType == C.TRACK_TYPE_VIDEO) {
      if (MUXER_CAN_WRITE_VIDEO_SAMPLE.size() == MUXER_CAN_WRITE_SAMPLE_RECORD_COUNT) {
        MUXER_CAN_WRITE_VIDEO_SAMPLE.poll();
      }
      MUXER_CAN_WRITE_VIDEO_SAMPLE.add(canAddSample);
    }
  }

  public static synchronized void recordMuxerTrackEnded(@C.TrackType int trackType) {
    if (trackType == C.TRACK_TYPE_VIDEO) {
      MUXER_TRACK_END_TIMES_MS.add(SystemClock.DEFAULT.elapsedRealtime());
    }
  }

  public static synchronized String generateTrace() {
    return "Video input format: "
        + latestVideoInputFormat
        + ", Decoded: "
        + numberOfDecodedFrames
        + ", Rendered to VFP: "
        + numberOfFramesRenderedToVideoFrameProcessorInput
        + ", Rendered to GlSP: "
        + numberOfFramesDequeuedFromVideoProcessorInput
        + ", Rendered to encoder: "
        + numberOfFramesRenderedToVideoFrameProcessorOutput
        + ", Encoded: "
        + numberOfEncodedFrames
        + ", Muxed: "
        + numberOfMuxedFrames
        + ", Decoder receive EOS: "
        + generateString(DECODER_RECEIVE_EOS_TIMES_MS)
        + ", Decoder signal EOS: "
        + generateString(DECODER_SIGNAL_EOS_TIMES_MS)
        + ", VFP receive EOS: "
        + generateString(VIDEO_FRAME_PROCESSOR_RECEIVE_DECODER_EOS_TIMES_MS)
        + ", VFP ExtTexMgr signal EndOfCurrentInputStream: "
        + generateString(EXTERNAL_TEXTURE_MANAGER_SIGNAL_EOCIS_TIMES_MS)
        + ", VFP signal EOS: "
        + generateString(VIDEO_FRAME_PROCESSOR_SIGNAL_EOS_TIMES_MS)
        + ", Encoder receive EOS: "
        + generateString(ENCODER_RECEIVE_EOS_TIMES_MS)
        + Util.formatInvariant(
            ", Muxer last %d video canWriteSample: ", MUXER_CAN_WRITE_SAMPLE_RECORD_COUNT)
        + generateString(MUXER_CAN_WRITE_VIDEO_SAMPLE)
        + ", Muxer stopped: "
        + generateString(MUXER_TRACK_END_TIMES_MS);
  }

  private static <T> String generateString(Queue<T> queue) {
    return queue.isEmpty() ? "NO" : Util.formatInvariant("[%s]", Joiner.on(',').join(queue));
  }
}
