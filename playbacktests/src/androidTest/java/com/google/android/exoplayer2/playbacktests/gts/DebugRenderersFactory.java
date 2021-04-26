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
package com.google.android.exoplayer2.playbacktests.gts;

import static java.lang.Math.max;

import android.content.Context;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * A debug extension of {@link DefaultRenderersFactory}. Provides a video renderer that performs
 * video buffer timestamp assertions, and modifies the default value for {@link
 * #setAllowedVideoJoiningTimeMs(long)} to be {@code 0}.
 */
// TODO: Move this class to `testutils` and add basic tests.
/* package */ final class DebugRenderersFactory extends DefaultRenderersFactory {

  public DebugRenderersFactory(Context context) {
    super(context);
    setAllowedVideoJoiningTimeMs(0);
  }

  @Override
  protected void buildVideoRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      ArrayList<Renderer> out) {
    out.add(
        new DebugMediaCodecVideoRenderer(
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
  }

  /**
   * Decodes and renders video using {@link MediaCodecVideoRenderer}. Provides buffer timestamp
   * assertions.
   */
  private static class DebugMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

    private static final String TAG = "DebugMediaCodecVideoRenderer";
    private static final int ARRAY_SIZE = 1000;

    private final long[] timestampsList;
    private final ArrayDeque<Long> inputFormatChangeTimesUs;
    private final boolean shouldMediaFormatChangeTimesBeChecked;

    private boolean skipToPositionBeforeRenderingFirstFrame;

    private int startIndex;
    private int queueSize;
    private int bufferCount;
    private int minimumInsertIndex;
    private boolean inputFormatChanged;
    private boolean outputMediaFormatChanged;

    @Nullable private MediaFormat currentMediaFormat;

    public DebugMediaCodecVideoRenderer(
        Context context,
        MediaCodecSelector mediaCodecSelector,
        long allowedJoiningTimeMs,
        Handler eventHandler,
        VideoRendererEventListener eventListener,
        int maxDroppedFrameCountToNotify) {
      super(
          context,
          mediaCodecSelector,
          allowedJoiningTimeMs,
          eventHandler,
          eventListener,
          maxDroppedFrameCountToNotify);
      timestampsList = new long[ARRAY_SIZE];
      inputFormatChangeTimesUs = new ArrayDeque<>();

      /*
      // Output MediaFormat changes are known to occur too early until API 30 (see [internal:
      // b/149818050, b/149751672]).
      shouldMediaFormatChangeTimesBeChecked = Util.SDK_INT > 30;
      */

      // [Internal ref: b/149751672] Seeking currently causes an unexpected MediaFormat change, so
      // this check is disabled until that is deemed fixed.
      shouldMediaFormatChangeTimesBeChecked = false;
    }

    @Override
    public String getName() {
      return TAG;
    }

    @Override
    protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(
        MediaCodecInfo codecInfo, Format format, MediaCrypto crypto, float operatingRate) {
      return super.getMediaCodecConfiguration(codecInfo, format, crypto, operatingRate);
    }

    @Override
    protected void resetCodecStateForFlush() {
      super.resetCodecStateForFlush();
      clearTimestamps();
      // Check if there is a format change on the input side still pending propagation to the
      // output.
      inputFormatChanged = !inputFormatChangeTimesUs.isEmpty();
      inputFormatChangeTimesUs.clear();
      outputMediaFormatChanged = false;
    }

    @Override
    protected void onCodecInitialized(
        String name, long initializedTimestampMs, long initializationDurationMs) {
      // If the codec was initialized whilst the renderer is started, default behavior is to
      // render the first frame (i.e. the keyframe before the current position), then drop frames up
      // to the current playback position. For test runs that place a maximum limit on the number of
      // dropped frames allowed, this is not desired behavior. Hence we skip (rather than drop)
      // frames up to the current playback position [Internal: b/66494991].
      skipToPositionBeforeRenderingFirstFrame = getState() == Renderer.STATE_STARTED;
      super.onCodecInitialized(name, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    protected void resetCodecStateForRelease() {
      super.resetCodecStateForRelease();
      skipToPositionBeforeRenderingFirstFrame = false;
    }

    @Override
    @Nullable
    protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder)
        throws ExoPlaybackException {
      @Nullable DecoderReuseEvaluation evaluation = super.onInputFormatChanged(formatHolder);
      // Ensure timestamps of buffers queued after this format change are never inserted into the
      // queue of expected output timestamps before those of buffers that have already been queued.
      minimumInsertIndex = startIndex + queueSize;
      inputFormatChanged = true;
      return evaluation;
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
      super.onQueueInputBuffer(buffer);
      insertTimestamp(buffer.timeUs);
      maybeShiftTimestampsList();
      if (inputFormatChanged) {
        inputFormatChangeTimesUs.add(buffer.timeUs);
        inputFormatChanged = false;
      }
    }

    @Override
    protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat) {
      super.onOutputFormatChanged(format, mediaFormat);
      if (mediaFormat != null && !mediaFormat.equals(currentMediaFormat)) {
        outputMediaFormatChanged = true;
        currentMediaFormat = mediaFormat;
      } else {
        inputFormatChangeTimesUs.remove();
      }
    }

    @Override
    protected boolean processOutputBuffer(
        long positionUs,
        long elapsedRealtimeUs,
        @Nullable MediaCodecAdapter codec,
        ByteBuffer buffer,
        int bufferIndex,
        int bufferFlags,
        int sampleCount,
        long bufferPresentationTimeUs,
        boolean isDecodeOnlyBuffer,
        boolean isLastBuffer,
        Format format)
        throws ExoPlaybackException {
      if (skipToPositionBeforeRenderingFirstFrame && bufferPresentationTimeUs < positionUs) {
        // After the codec has been initialized, don't render the first frame until we've caught up
        // to the playback position. Else test runs on devices that do not support dummy surface
        // will drop frames between rendering the first one and catching up [Internal: b/66494991].
        isDecodeOnlyBuffer = true;
      }
      return super.processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          codec,
          buffer,
          bufferIndex,
          bufferFlags,
          sampleCount,
          bufferPresentationTimeUs,
          isDecodeOnlyBuffer,
          isLastBuffer,
          format);
    }

    @Override
    protected void renderOutputBuffer(MediaCodecAdapter codec, int index, long presentationTimeUs) {
      skipToPositionBeforeRenderingFirstFrame = false;
      super.renderOutputBuffer(codec, index, presentationTimeUs);
    }

    @RequiresApi(21)
    @Override
    protected void renderOutputBufferV21(
        MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
      skipToPositionBeforeRenderingFirstFrame = false;
      super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
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

      if (outputMediaFormatChanged) {
        long inputFormatChangeTimeUs =
            inputFormatChangeTimesUs.isEmpty() ? C.TIME_UNSET : inputFormatChangeTimesUs.remove();
        outputMediaFormatChanged = false;

        if (shouldMediaFormatChangeTimesBeChecked
            && presentationTimeUs != inputFormatChangeTimeUs) {
          throw new IllegalStateException(
              "Expected output MediaFormat change timestamp ("
                  + presentationTimeUs
                  + " us) to match input Format change timestamp ("
                  + inputFormatChangeTimeUs
                  + " us).");
        }
      }
    }

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
      // Disable all workarounds for testing - devices that require the workaround should fail GTS.
      return false;
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
      minimumInsertIndex = max(minimumInsertIndex, startIndex);
      return timestampsList[startIndex - 1];
    }

  }

}
