/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_DRM_SESSION_CHANGED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_REUSE_NOT_IMPLEMENTED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.C.VideoOutputMode;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.TimedValueQueue;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.PlayerMessage;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import androidx.media3.exoplayer.video.VideoRendererEventListener.EventDispatcher;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Decodes and renders video using a {@link Decoder}.
 *
 * <p>This renderer accepts the following messages sent via {@link
 * ExoPlayer#createMessage(PlayerMessage.Target)} on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VIDEO_OUTPUT} to set the output surface. The message
 *       payload should be the target {@link Surface} or {@link VideoDecoderOutputBufferRenderer},
 *       or null. Other non-null payloads have the effect of clearing the output.
 *   <li>Message with type {@link #MSG_SET_VIDEO_FRAME_METADATA_LISTENER} to set a listener for
 *       metadata associated with frames being rendered. The message payload should be the {@link
 *       VideoFrameMetadataListener}, or null.
 * </ul>
 */
@UnstableApi
public abstract class DecoderVideoRenderer extends BaseRenderer {

  private static final String TAG = "DecoderVideoRenderer";

  /** Decoder reinitialization states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}

  /** The decoder does not need to be re-initialized. */
  private static final int REINITIALIZATION_STATE_NONE = 0;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;

  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;

  private final long allowedJoiningTimeMs;
  private final int maxDroppedFramesToNotify;
  private final EventDispatcher eventDispatcher;
  private final TimedValueQueue<Format> formatQueue;
  private final DecoderInputBuffer flagsOnlyBuffer;

  @Nullable private Format inputFormat;
  @Nullable private Format outputFormat;

  @Nullable
  private Decoder<
          DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException>
      decoder;

  @Nullable private DecoderInputBuffer inputBuffer;
  @Nullable private VideoDecoderOutputBuffer outputBuffer;
  private @VideoOutputMode int outputMode;
  @Nullable private Object output;
  @Nullable private Surface outputSurface;
  @Nullable private VideoDecoderOutputBufferRenderer outputBufferRenderer;
  @Nullable private VideoFrameMetadataListener frameMetadataListener;

  @Nullable private DrmSession decoderDrmSession;
  @Nullable private DrmSession sourceDrmSession;

  private @ReinitializationState int decoderReinitializationState;
  private boolean decoderReceivedBuffers;

  private @C.FirstFrameState int firstFrameState;
  private long initialPositionUs;
  private long joiningDeadlineMs;
  private boolean waitingForFirstSampleInFormat;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  @Nullable private VideoSize reportedVideoSize;

  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrames;
  private int consecutiveDroppedFrameCount;
  private int buffersInCodecCount;
  private long lastRenderTimeUs;
  private long outputStreamOffsetUs;

  /** Decoder event counters used for debugging purposes. */
  protected DecoderCounters decoderCounters;

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  protected DecoderVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(C.TRACK_TYPE_VIDEO);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
    joiningDeadlineMs = C.TIME_UNSET;
    formatQueue = new TimedValueQueue<>();
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    outputMode = C.VIDEO_OUTPUT_MODE_NONE;
    firstFrameState = C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
    decoderCounters = new DecoderCounters();
  }

  // BaseRenderer implementation.

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @ReadDataResult int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder);
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        outputStreamEnded = true;
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder();

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } catch (DecoderException e) {
        Log.e(TAG, "Video codec error", e);
        eventDispatcher.videoCodecError(e);
        throw createRendererException(e, inputFormat, PlaybackException.ERROR_CODE_DECODING_FAILED);
      }
      decoderCounters.ensureUpdated();
    }
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    if (inputFormat != null
        && (isSourceReady() || outputBuffer != null)
        && (firstFrameState == C.FIRST_FRAME_RENDERED || !hasOutput())) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return true;
    } else if (joiningDeadlineMs == C.TIME_UNSET) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return false;
    }
  }

  // PlayerMessage.Target implementation.

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    if (messageType == MSG_SET_VIDEO_OUTPUT) {
      setOutput(message);
    } else if (messageType == MSG_SET_VIDEO_FRAME_METADATA_LISTENER) {
      frameMetadataListener = (VideoFrameMetadataListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  // Protected methods.

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    firstFrameState =
        mayRenderStartOfStream
            ? C.FIRST_FRAME_NOT_RENDERED
            : C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED;
  }

  @Override
  public void enableMayRenderStartOfStream() {
    if (firstFrameState == C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED) {
      firstFrameState = C.FIRST_FRAME_NOT_RENDERED;
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED);
    initialPositionUs = C.TIME_UNSET;
    consecutiveDroppedFrameCount = 0;
    if (decoder != null) {
      flushDecoder();
    }
    if (joining) {
      setJoiningDeadlineMs();
    } else {
      joiningDeadlineMs = C.TIME_UNSET;
    }
    formatQueue.clear();
  }

  @Override
  protected void onStarted() {
    droppedFrames = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
    lastRenderTimeUs = msToUs(SystemClock.elapsedRealtime());
  }

  @Override
  protected void onStopped() {
    joiningDeadlineMs = C.TIME_UNSET;
    maybeNotifyDroppedFrames();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    reportedVideoSize = null;
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED);
    try {
      setSourceDrmSession(null);
      releaseDecoder();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    // TODO: This shouldn't just update the output stream offset as long as there are still buffers
    // of the previous stream in the decoder. It should also make sure to render the first frame of
    // the next stream if the playback position reached the new stream.
    outputStreamOffsetUs = offsetUs;
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
  }

  /**
   * Flushes the decoder.
   *
   * @throws ExoPlaybackException If an error occurs reinitializing a decoder.
   */
  @CallSuper
  protected void flushDecoder() throws ExoPlaybackException {
    buffersInCodecCount = 0;
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder();
      maybeInitDecoder();
    } else {
      inputBuffer = null;
      if (outputBuffer != null) {
        outputBuffer.release();
        outputBuffer = null;
      }
      Decoder<?, ?, ?> decoder = checkNotNull(this.decoder);
      decoder.flush();
      decoder.setOutputStartTimeUs(getLastResetPositionUs());
      decoderReceivedBuffers = false;
    }
  }

  /** Releases the decoder. */
  @CallSuper
  protected void releaseDecoder() {
    inputBuffer = null;
    outputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
    buffersInCodecCount = 0;
    if (decoder != null) {
      decoderCounters.decoderReleaseCount++;
      decoder.release();
      eventDispatcher.decoderReleased(decoder.getName());
      decoder = null;
    }
    setDecoderDrmSession(null);
  }

  /**
   * Called when a new format is read from the upstream source.
   *
   * @param formatHolder A {@link FormatHolder} that holds the new {@link Format}.
   * @throws ExoPlaybackException If an error occurs (re-)initializing the decoder.
   */
  @CallSuper
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    waitingForFirstSampleInFormat = true;
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    setSourceDrmSession(formatHolder.drmSession);
    Format oldFormat = inputFormat;
    inputFormat = newFormat;

    if (decoder == null) {
      maybeInitDecoder();
      eventDispatcher.inputFormatChanged(
          checkNotNull(inputFormat), /* decoderReuseEvaluation= */ null);
      return;
    }

    DecoderReuseEvaluation evaluation;
    if (sourceDrmSession != decoderDrmSession) {
      evaluation =
          new DecoderReuseEvaluation(
              decoder.getName(),
              checkNotNull(oldFormat),
              newFormat,
              REUSE_RESULT_NO,
              DISCARD_REASON_DRM_SESSION_CHANGED);
    } else {
      evaluation = canReuseDecoder(decoder.getName(), checkNotNull(oldFormat), newFormat);
    }

    if (evaluation.result == REUSE_RESULT_NO) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder();
        maybeInitDecoder();
      }
    }
    eventDispatcher.inputFormatChanged(checkNotNull(inputFormat), evaluation);
  }

  /**
   * Called immediately before an input buffer is queued into the decoder.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The buffer that will be queued.
   */
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    // Do nothing.
  }

  /**
   * Called when an output buffer is successfully processed.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  @CallSuper
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    buffersInCodecCount--;
  }

  /**
   * Returns whether the buffer being processed should be dropped.
   *
   * @param earlyUs The time until the buffer should be presented in microseconds. A negative value
   *     indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   */
  protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs) {
    return isBufferLate(earlyUs);
  }

  /**
   * Returns whether to drop all buffers from the buffer being processed to the keyframe at or after
   * the current playback position, if possible.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   */
  protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs) {
    return isBufferVeryLate(earlyUs);
  }

  /**
   * Returns whether to force rendering an output buffer.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedSinceLastRenderUs The elapsed time since the last output buffer was rendered, in
   *     microseconds.
   * @return Returns whether to force rendering an output buffer.
   */
  protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
    return isBufferLate(earlyUs) && elapsedSinceLastRenderUs > 100000;
  }

  /**
   * Skips the specified output buffer and releases it.
   *
   * @param outputBuffer The output buffer to skip.
   */
  protected void skipOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    decoderCounters.skippedOutputBufferCount++;
    outputBuffer.release();
  }

  /**
   * Drops the specified output buffer and releases it.
   *
   * @param outputBuffer The output buffer to drop.
   */
  protected void dropOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    updateDroppedBufferCounters(
        /* droppedInputBufferCount= */ 0, /* droppedDecoderBufferCount= */ 1);
    outputBuffer.release();
  }

  /**
   * Drops frames from the current output buffer to the next keyframe at or before the playback
   * position. If no such keyframe exists, as the playback position is inside the same group of
   * pictures as the buffer being processed, returns {@code false}. Returns {@code true} otherwise.
   *
   * @param positionUs The current playback position, in microseconds.
   * @return Whether any buffers were dropped.
   * @throws ExoPlaybackException If an error occurs flushing the decoder.
   */
  protected boolean maybeDropBuffersToKeyframe(long positionUs) throws ExoPlaybackException {
    int droppedSourceBufferCount = skipSource(positionUs);
    if (droppedSourceBufferCount == 0) {
      return false;
    }
    decoderCounters.droppedToKeyframeCount++;
    // We dropped some buffers to catch up, so update the decoder counters and flush the decoder,
    // which releases all pending buffers buffers including the current output buffer.
    updateDroppedBufferCounters(
        droppedSourceBufferCount, /* droppedDecoderBufferCount= */ buffersInCodecCount);
    flushDecoder();
    return true;
  }

  /**
   * Updates local counters and {@link #decoderCounters} to reflect that buffers were dropped.
   *
   * @param droppedInputBufferCount The number of buffers dropped from the source before being
   *     passed to the decoder.
   * @param droppedDecoderBufferCount The number of buffers dropped after being passed to the
   *     decoder.
   */
  protected void updateDroppedBufferCounters(
      int droppedInputBufferCount, int droppedDecoderBufferCount) {
    decoderCounters.droppedInputBufferCount += droppedInputBufferCount;
    int totalDroppedBufferCount = droppedInputBufferCount + droppedDecoderBufferCount;
    decoderCounters.droppedBufferCount += totalDroppedBufferCount;
    droppedFrames += totalDroppedBufferCount;
    consecutiveDroppedFrameCount += totalDroppedBufferCount;
    decoderCounters.maxConsecutiveDroppedBufferCount =
        max(consecutiveDroppedFrameCount, decoderCounters.maxConsecutiveDroppedBufferCount);
    if (maxDroppedFramesToNotify > 0 && droppedFrames >= maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param cryptoConfig The {@link CryptoConfig} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws DecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract Decoder<
          DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException>
      createDecoder(Format format, @Nullable CryptoConfig cryptoConfig) throws DecoderException;

  /**
   * Renders the specified output buffer.
   *
   * <p>The implementation of this method takes ownership of the output buffer and is responsible
   * for calling {@link VideoDecoderOutputBuffer#release()} either immediately or in the future.
   *
   * @param outputBuffer {@link VideoDecoderOutputBuffer} to render.
   * @param presentationTimeUs Presentation time in microseconds.
   * @param outputFormat Output {@link Format}.
   * @throws DecoderException If an error occurs when rendering the output buffer.
   */
  protected void renderOutputBuffer(
      VideoDecoderOutputBuffer outputBuffer, long presentationTimeUs, Format outputFormat)
      throws DecoderException {
    if (frameMetadataListener != null) {
      frameMetadataListener.onVideoFrameAboutToBeRendered(
          presentationTimeUs, getClock().nanoTime(), outputFormat, /* mediaFormat= */ null);
    }
    lastRenderTimeUs = msToUs(SystemClock.elapsedRealtime());
    int bufferMode = outputBuffer.mode;
    boolean renderSurface = bufferMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && outputSurface != null;
    boolean renderYuv = bufferMode == C.VIDEO_OUTPUT_MODE_YUV && outputBufferRenderer != null;
    if (!renderYuv && !renderSurface) {
      dropOutputBuffer(outputBuffer);
    } else {
      maybeNotifyVideoSizeChanged(outputBuffer.width, outputBuffer.height);
      if (renderYuv) {
        checkNotNull(outputBufferRenderer).setOutputBuffer(outputBuffer);
      } else {
        renderOutputBufferToSurface(outputBuffer, checkNotNull(outputSurface));
      }
      consecutiveDroppedFrameCount = 0;
      decoderCounters.renderedOutputBufferCount++;
      maybeNotifyRenderedFirstFrame();
    }
  }

  /**
   * Renders the specified output buffer to the passed surface.
   *
   * <p>The implementation of this method takes ownership of the output buffer and is responsible
   * for calling {@link VideoDecoderOutputBuffer#release()} either immediately or in the future.
   *
   * @param outputBuffer {@link VideoDecoderOutputBuffer} to render.
   * @param surface Output {@link Surface}.
   * @throws DecoderException If an error occurs when rendering the output buffer.
   */
  protected abstract void renderOutputBufferToSurface(
      VideoDecoderOutputBuffer outputBuffer, Surface surface) throws DecoderException;

  /** Sets the video output. */
  protected final void setOutput(@Nullable Object output) {
    if (output instanceof Surface) {
      outputSurface = (Surface) output;
      outputBufferRenderer = null;
      outputMode = C.VIDEO_OUTPUT_MODE_SURFACE_YUV;
    } else if (output instanceof VideoDecoderOutputBufferRenderer) {
      outputSurface = null;
      outputBufferRenderer = (VideoDecoderOutputBufferRenderer) output;
      outputMode = C.VIDEO_OUTPUT_MODE_YUV;
    } else {
      // Handle unsupported outputs by clearing the output.
      output = null;
      outputSurface = null;
      outputBufferRenderer = null;
      outputMode = C.VIDEO_OUTPUT_MODE_NONE;
    }
    if (this.output != output) {
      this.output = output;
      if (output != null) {
        if (decoder != null) {
          setDecoderOutputMode(outputMode);
        }
        onOutputChanged();
      } else {
        // The output has been removed. We leave the outputMode of the underlying decoder unchanged
        // in anticipation that a subsequent output will likely be of the same type.
        onOutputRemoved();
      }
    } else if (output != null) {
      // The output is unchanged and non-null.
      onOutputReset();
    }
  }

  /**
   * Sets output mode of the decoder.
   *
   * @param outputMode Output mode.
   */
  protected abstract void setDecoderOutputMode(@VideoOutputMode int outputMode);

  /**
   * Evaluates whether the existing decoder can be reused for a new {@link Format}.
   *
   * <p>The default implementation does not allow decoder reuse.
   *
   * @param decoderName The name of the decoder.
   * @param oldFormat The previous format.
   * @param newFormat The new format.
   * @return The result of the evaluation.
   */
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    return new DecoderReuseEvaluation(
        decoderName, oldFormat, newFormat, REUSE_RESULT_NO, DISCARD_REASON_REUSE_NOT_IMPLEMENTED);
  }

  // Internal methods.

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setDecoderDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(decoderDrmSession, session);
    decoderDrmSession = session;
  }

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    setDecoderDrmSession(sourceDrmSession);

    CryptoConfig cryptoConfig = null;
    if (decoderDrmSession != null) {
      cryptoConfig = decoderDrmSession.getCryptoConfig();
      if (cryptoConfig == null) {
        DrmSessionException drmError = decoderDrmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if a new input format causes the
          // session to be replaced without it having been used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      }
    }

    try {
      long decoderInitializingTimestamp = SystemClock.elapsedRealtime();
      decoder = createDecoder(checkNotNull(inputFormat), cryptoConfig);
      decoder.setOutputStartTimeUs(getLastResetPositionUs());
      setDecoderOutputMode(outputMode);
      long decoderInitializedTimestamp = SystemClock.elapsedRealtime();
      eventDispatcher.decoderInitialized(
          checkNotNull(decoder).getName(),
          decoderInitializedTimestamp,
          decoderInitializedTimestamp - decoderInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (DecoderException e) {
      Log.e(TAG, "Video codec error", e);
      eventDispatcher.videoCodecError(e);
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    } catch (OutOfMemoryError e) {
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
  }

  // Setting deprecated decode-only flag for compatibility with decoders that are still using it.
  @SuppressWarnings("deprecation")
  private boolean feedInputBuffer() throws DecoderException, ExoPlaybackException {
    if (decoder == null
        || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    DecoderInputBuffer inputBuffer = checkNotNull(this.inputBuffer);
    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      checkNotNull(decoder).queueInputBuffer(inputBuffer);
      this.inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    FormatHolder formatHolder = getFormatHolder();
    switch (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      case C.RESULT_NOTHING_READ:
        return false;
      case C.RESULT_FORMAT_READ:
        onInputFormatChanged(formatHolder);
        return true;
      case C.RESULT_BUFFER_READ:
        if (inputBuffer.isEndOfStream()) {
          inputStreamEnded = true;
          checkNotNull(decoder).queueInputBuffer(inputBuffer);
          this.inputBuffer = null;
          return false;
        }
        if (waitingForFirstSampleInFormat) {
          formatQueue.add(inputBuffer.timeUs, checkNotNull(inputFormat));
          waitingForFirstSampleInFormat = false;
        }
        if (inputBuffer.timeUs < getLastResetPositionUs()) {
          inputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
        }
        inputBuffer.flip();
        inputBuffer.format = inputFormat;
        onQueueInputBuffer(inputBuffer);
        checkNotNull(decoder).queueInputBuffer(inputBuffer);
        buffersInCodecCount++;
        decoderReceivedBuffers = true;
        decoderCounters.queuedInputBufferCount++;
        this.inputBuffer = null;
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Attempts to dequeue an output buffer from the decoder and, if successful, passes it to {@link
   * #processOutputBuffer(long, long)}.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain more output data.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException, DecoderException {
    if (outputBuffer == null) {
      outputBuffer = checkNotNull(decoder).dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
      buffersInCodecCount -= outputBuffer.skippedOutputBufferCount;
    }

    if (outputBuffer.isEndOfStream()) {
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoder();
        maybeInitDecoder();
      } else {
        outputBuffer.release();
        outputBuffer = null;
        outputStreamEnded = true;
      }
      return false;
    }

    boolean processedOutputBuffer = processOutputBuffer(positionUs, elapsedRealtimeUs);
    if (processedOutputBuffer) {
      onProcessedOutputBuffer(checkNotNull(outputBuffer).timeUs);
      outputBuffer = null;
    }
    return processedOutputBuffer;
  }

  /**
   * Processes {@link #outputBuffer} by rendering it, skipping it or doing nothing, and returns
   * whether it may be possible to process another output buffer.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain another output buffer.
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  private boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException, DecoderException {
    if (initialPositionUs == C.TIME_UNSET) {
      initialPositionUs = positionUs;
    }

    VideoDecoderOutputBuffer outputBuffer = checkNotNull(this.outputBuffer);
    long bufferTimeUs = outputBuffer.timeUs;
    long earlyUs = bufferTimeUs - positionUs;
    if (!hasOutput()) {
      // Skip frames in sync with playback, so we'll be at the right frame if the mode changes.
      if (isBufferLate(earlyUs)) {
        skipOutputBuffer(outputBuffer);
        return true;
      }
      return false;
    }

    Format format = formatQueue.pollFloor(bufferTimeUs);
    if (format != null) {
      outputFormat = format;
    } else if (outputFormat == null) {
      // After a stream change or after the initial start, there should be an input format change
      // which we've not found. Check the Format queue in case the corresponding presentation
      // timestamp is greater than bufferTimeUs
      outputFormat = formatQueue.pollFirst();
    }

    long presentationTimeUs = bufferTimeUs - outputStreamOffsetUs;
    if (shouldForceRender(earlyUs)) {
      renderOutputBuffer(outputBuffer, presentationTimeUs, checkNotNull(outputFormat));
      return true;
    }

    boolean isStarted = getState() == STATE_STARTED;
    if (!isStarted || positionUs == initialPositionUs) {
      return false;
    }

    // TODO: Treat dropped buffers as skipped while we are joining an ongoing playback.
    if (shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs)
        && maybeDropBuffersToKeyframe(positionUs)) {
      return false;
    } else if (shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs)) {
      dropOutputBuffer(outputBuffer);
      return true;
    }

    if (earlyUs < 30000) {
      renderOutputBuffer(outputBuffer, presentationTimeUs, checkNotNull(outputFormat));
      return true;
    }

    return false;
  }

  /** Returns whether a buffer or a processed frame should be force rendered. */
  private boolean shouldForceRender(long earlyUs) {
    // TODO: We shouldn't force render while we are joining an ongoing playback.
    boolean isStarted = getState() == STATE_STARTED;
    switch (firstFrameState) {
      case C.FIRST_FRAME_NOT_RENDERED_ONLY_ALLOWED_IF_STARTED:
        return isStarted;
      case C.FIRST_FRAME_NOT_RENDERED:
        return true;
      case C.FIRST_FRAME_RENDERED:
        long elapsedSinceLastRenderUs = msToUs(SystemClock.elapsedRealtime()) - lastRenderTimeUs;
        return isStarted && shouldForceRenderOutputBuffer(earlyUs, elapsedSinceLastRenderUs);
      default:
        throw new IllegalStateException();
    }
  }

  private boolean hasOutput() {
    return outputMode != C.VIDEO_OUTPUT_MODE_NONE;
  }

  private void onOutputChanged() {
    // If we know the video size, report it again immediately.
    maybeRenotifyVideoSizeChanged();
    // We haven't rendered to the new output yet.
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED);
    if (getState() == STATE_STARTED) {
      setJoiningDeadlineMs();
    }
  }

  private void onOutputRemoved() {
    reportedVideoSize = null;
    lowerFirstFrameState(C.FIRST_FRAME_NOT_RENDERED);
  }

  private void onOutputReset() {
    // The output is unchanged and non-null. If we know the video size and/or have already
    // rendered to the output, report these again immediately.
    maybeRenotifyVideoSizeChanged();
    maybeRenotifyRenderedFirstFrame();
  }

  private void setJoiningDeadlineMs() {
    joiningDeadlineMs =
        allowedJoiningTimeMs > 0
            ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs)
            : C.TIME_UNSET;
  }

  private void lowerFirstFrameState(@C.FirstFrameState int firstFrameState) {
    this.firstFrameState = min(this.firstFrameState, firstFrameState);
  }

  private void maybeNotifyRenderedFirstFrame() {
    if (firstFrameState != C.FIRST_FRAME_RENDERED) {
      firstFrameState = C.FIRST_FRAME_RENDERED;
      if (output != null) {
        eventDispatcher.renderedFirstFrame(output);
      }
    }
  }

  private void maybeRenotifyRenderedFirstFrame() {
    if (firstFrameState == C.FIRST_FRAME_RENDERED && output != null) {
      eventDispatcher.renderedFirstFrame(output);
    }
  }

  private void maybeNotifyVideoSizeChanged(int width, int height) {
    if (reportedVideoSize == null
        || reportedVideoSize.width != width
        || reportedVideoSize.height != height) {
      reportedVideoSize = new VideoSize(width, height);
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedVideoSize != null) {
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeNotifyDroppedFrames() {
    if (droppedFrames > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
      droppedFrames = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  private static boolean isBufferLate(long earlyUs) {
    // Class a buffer as late if it should have been presented more than 30 ms ago.
    return earlyUs < -30000;
  }

  private static boolean isBufferVeryLate(long earlyUs) {
    // Class a buffer as very late if it should have been presented more than 500 ms ago.
    return earlyUs < -500000;
  }
}
