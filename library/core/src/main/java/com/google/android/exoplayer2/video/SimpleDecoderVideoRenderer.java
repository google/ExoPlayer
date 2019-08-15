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
package com.google.android.exoplayer2.video;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener.EventDispatcher;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Decodes and renders video using a {@link SimpleDecoder}. */
public abstract class SimpleDecoderVideoRenderer extends BaseRenderer {

  /** Decoder reinitialization states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
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
  private final boolean playClearSamplesWithoutKeys;
  private final EventDispatcher eventDispatcher;
  private final FormatHolder formatHolder;
  private final TimedValueQueue<Format> formatQueue;
  private final DecoderInputBuffer flagsOnlyBuffer;
  private final DrmSessionManager<ExoMediaCrypto> drmSessionManager;

  private Format format;
  private Format pendingFormat;
  private Format outputFormat;
  private SimpleDecoder<
          VideoDecoderInputBuffer,
          ? extends VideoDecoderOutputBuffer,
          ? extends VideoDecoderException>
      decoder;
  private VideoDecoderInputBuffer inputBuffer;
  private VideoDecoderOutputBuffer outputBuffer;
  @Nullable private DrmSession<ExoMediaCrypto> decoderDrmSession;
  @Nullable private DrmSession<ExoMediaCrypto> sourceDrmSession;

  @ReinitializationState private int decoderReinitializationState;
  private boolean decoderReceivedBuffers;

  private boolean renderedFirstFrame;
  private long initialPositionUs;
  private long joiningDeadlineMs;
  private boolean waitingForKeys;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private int reportedWidth;
  private int reportedHeight;

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
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   */
  protected SimpleDecoderVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    super(C.TRACK_TYPE_VIDEO);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
    this.drmSessionManager = drmSessionManager;
    this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
    joiningDeadlineMs = C.TIME_UNSET;
    clearReportedVideoSize();
    formatHolder = new FormatHolder();
    formatQueue = new TimedValueQueue<>();
    flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
  }

  // BaseRenderer implementation.

  @Override
  public final int supportsFormat(Format format) {
    return supportsFormatInternal(drmSessionManager, format);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (format == null) {
      // We don't have a format yet, so try and read one.
      flagsOnlyBuffer.clear();
      int result = readSource(formatHolder, flagsOnlyBuffer, true);
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
      } catch (VideoDecoderException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
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
    if (waitingForKeys) {
      return false;
    }
    if (format != null
        && (isSourceReady() || outputBuffer != null)
        && (renderedFirstFrame || !hasOutputSurface())) {
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

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    clearRenderedFirstFrame();
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
    lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  @Override
  protected void onStopped() {
    joiningDeadlineMs = C.TIME_UNSET;
    maybeNotifyDroppedFrames();
  }

  @Override
  protected void onDisabled() {
    format = null;
    waitingForKeys = false;
    clearReportedVideoSize();
    clearRenderedFirstFrame();
    try {
      setSourceDrmSession(null);
      releaseDecoder();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    outputStreamOffsetUs = offsetUs;
    super.onStreamChanged(formats, offsetUs);
  }

  /**
   * Called when a decoder has been created and configured.
   *
   * <p>The default implementation is a no-op.
   *
   * @param name The name of the decoder that was initialized.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder, in milliseconds.
   */
  @CallSuper
  protected void onDecoderInitialized(
      String name, long initializedTimestampMs, long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
  }

  /**
   * Flushes the decoder.
   *
   * @throws ExoPlaybackException If an error occurs reinitializing a decoder.
   */
  @CallSuper
  protected void flushDecoder() throws ExoPlaybackException {
    waitingForKeys = false;
    buffersInCodecCount = 0;
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder();
      maybeInitDecoder();
    } else {
      inputBuffer = null;
      if (outputBuffer != null) {
        outputBuffer.release();
        clearOutputBuffer();
      }
      decoder.flush();
      decoderReceivedBuffers = false;
    }
  }

  /** Releases the decoder. */
  @CallSuper
  protected void releaseDecoder() {
    inputBuffer = null;
    clearOutputBuffer();
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
    buffersInCodecCount = 0;
    if (decoder != null) {
      decoder.release();
      decoder = null;
      decoderCounters.decoderReleaseCount++;
    }
    setDecoderDrmSession(null);
  }

  private void setSourceDrmSession(@Nullable DrmSession<ExoMediaCrypto> session) {
    DrmSession.replaceSessionReferences(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setDecoderDrmSession(@Nullable DrmSession<ExoMediaCrypto> session) {
    DrmSession.replaceSessionReferences(decoderDrmSession, session);
    decoderDrmSession = session;
  }

  /**
   * Called when a new format is read from the upstream source.
   *
   * @param formatHolder A {@link FormatHolder} that holds the new {@link Format}.
   * @throws ExoPlaybackException If an error occurs (re-)initializing the decoder.
   */
  @CallSuper
  @SuppressWarnings("unchecked")
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    Format oldFormat = format;
    format = formatHolder.format;
    pendingFormat = format;

    boolean drmInitDataChanged =
        !Util.areEqual(format.drmInitData, oldFormat == null ? null : oldFormat.drmInitData);
    if (drmInitDataChanged) {
      if (format.drmInitData != null) {
        if (formatHolder.includesDrmSession) {
          setSourceDrmSession((DrmSession<ExoMediaCrypto>) formatHolder.drmSession);
        } else {
          if (drmSessionManager == null) {
            throw ExoPlaybackException.createForRenderer(
                new IllegalStateException("Media requires a DrmSessionManager"), getIndex());
          }
          DrmSession<ExoMediaCrypto> session =
              drmSessionManager.acquireSession(Looper.myLooper(), format.drmInitData);
          if (sourceDrmSession != null) {
            sourceDrmSession.releaseReference();
          }
          sourceDrmSession = session;
        }
      } else {
        setSourceDrmSession(null);
      }
    }

    if (sourceDrmSession != decoderDrmSession) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder();
        maybeInitDecoder();
      }
    }

    eventDispatcher.inputFormatChanged(format);
  }

  /**
   * Called immediately before an input buffer is queued into the decoder.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The buffer that will be queued.
   */
  protected void onQueueInputBuffer(VideoDecoderInputBuffer buffer) {
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
    updateDroppedBufferCounters(1);
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
    updateDroppedBufferCounters(buffersInCodecCount + droppedSourceBufferCount);
    flushDecoder();
    return true;
  }

  /**
   * Updates decoder counters to reflect that {@code droppedBufferCount} additional buffers were
   * dropped.
   *
   * @param droppedBufferCount The number of additional dropped buffers.
   */
  protected void updateDroppedBufferCounters(int droppedBufferCount) {
    decoderCounters.droppedBufferCount += droppedBufferCount;
    droppedFrames += droppedBufferCount;
    consecutiveDroppedFrameCount += droppedBufferCount;
    decoderCounters.maxConsecutiveDroppedBufferCount =
        Math.max(consecutiveDroppedFrameCount, decoderCounters.maxConsecutiveDroppedBufferCount);
    if (maxDroppedFramesToNotify > 0 && droppedFrames >= maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  /**
   * Returns the extent to which the subclass supports a given format.
   *
   * @param drmSessionManager The renderer's {@link DrmSessionManager}.
   * @param format The format, which has a video {@link Format#sampleMimeType}.
   * @return The extent to which the subclass supports the format itself.
   * @see RendererCapabilities#supportsFormat(Format)
   */
  protected abstract int supportsFormatInternal(
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager, Format format);

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param mediaCrypto The {@link ExoMediaCrypto} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws VideoDecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract SimpleDecoder<
          VideoDecoderInputBuffer,
          ? extends VideoDecoderOutputBuffer,
          ? extends VideoDecoderException>
      createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
          throws VideoDecoderException;

  /**
   * Dequeues output buffer.
   *
   * @return Dequeued video decoder output buffer, or null if an output buffer isn't available.
   * @throws VideoDecoderException If an error occurs while dequeuing the output buffer.
   */
  @Nullable
  protected abstract VideoDecoderOutputBuffer dequeueOutputBuffer() throws VideoDecoderException;

  /** Clears output buffer. */
  protected void clearOutputBuffer() {
    outputBuffer = null;
  }

  /**
   * Renders the specified output buffer.
   *
   * <p>The implementation of this method takes ownership of the output buffer and is responsible
   * for calling {@link VideoDecoderOutputBuffer#release()} either immediately or in the future.
   *
   * @param presentationTimeUs Presentation time in microseconds.
   * @param outputFormat Output format.
   */
  // TODO: The output buffer is not being passed to this method currently. Due to the need of
  // decoder-specific output buffer type, the reference to the output buffer is being kept in the
  // subclass. Once the common output buffer is established, this method can be updated to receive
  // the output buffer as an argument. See [Internal: b/139174707].
  protected abstract void renderOutputBuffer(long presentationTimeUs, Format outputFormat)
      throws VideoDecoderException;

  /**
   * Returns whether the renderer has output surface.
   *
   * @return Whether the renderer has output surface.
   */
  protected abstract boolean hasOutputSurface();

  /** Called when the output surface is changed. */
  protected final void onOutputSurfaceChanged() {
    // If we know the video size, report it again immediately.
    maybeRenotifyVideoSizeChanged();
    // We haven't rendered to the new output yet.
    clearRenderedFirstFrame();
    if (getState() == STATE_STARTED) {
      setJoiningDeadlineMs();
    }
  }

  /** Called when the output surface is removed. */
  protected final void onOutputSurfaceRemoved() {
    clearReportedVideoSize();
    clearRenderedFirstFrame();
  }

  /**
   * Called when the output surface is set again to the same non-null value.
   *
   * @param surface Output surface.
   */
  protected final void onOutputSurfaceReset(Surface surface) {
    // The output is unchanged and non-null. If we know the video size and/or have already
    // rendered to the output, report these again immediately.
    maybeRenotifyVideoSizeChanged();
    maybeRenotifyRenderedFirstFrame(surface);
  }

  /**
   * Notifies event dispatcher if video size changed.
   *
   * @param width New video width.
   * @param height New video height.
   */
  protected final void maybeNotifyVideoSizeChanged(int width, int height) {
    if (reportedWidth != width || reportedHeight != height) {
      reportedWidth = width;
      reportedHeight = height;
      eventDispatcher.videoSizeChanged(
          width, height, /* unappliedRotationDegrees= */ 0, /* pixelWidthHeightRatio= */ 1);
    }
  }

  /** Called after rendering a frame. */
  protected final void onFrameRendered(Surface surface) {
    consecutiveDroppedFrameCount = 0;
    decoderCounters.renderedOutputBufferCount++;
    maybeNotifyRenderedFirstFrame(surface);
  }

  // Internal methods.

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    setDecoderDrmSession(sourceDrmSession);

    ExoMediaCrypto mediaCrypto = null;
    if (decoderDrmSession != null) {
      mediaCrypto = decoderDrmSession.getMediaCrypto();
      if (mediaCrypto == null) {
        DrmSessionException drmError = decoderDrmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if the session recovers, or if a new
          // input format causes the session to be replaced before it's used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      }
    }

    try {
      long decoderInitializingTimestamp = SystemClock.elapsedRealtime();
      decoder = createDecoder(format, mediaCrypto);
      long decoderInitializedTimestamp = SystemClock.elapsedRealtime();
      onDecoderInitialized(
          decoder.getName(),
          decoderInitializedTimestamp,
          decoderInitializedTimestamp - decoderInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (VideoDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  private boolean feedInputBuffer() throws VideoDecoderException, ExoPlaybackException {
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

    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    int result;
    if (waitingForKeys) {
      // We've already read an encrypted sample into buffer, and are waiting for keys.
      result = C.RESULT_BUFFER_READ;
    } else {
      result = readSource(formatHolder, inputBuffer, false);
    }

    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder);
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      return false;
    }
    boolean bufferEncrypted = inputBuffer.isEncrypted();
    waitingForKeys = shouldWaitForKeys(bufferEncrypted);
    if (waitingForKeys) {
      return false;
    }
    if (pendingFormat != null) {
      formatQueue.add(inputBuffer.timeUs, pendingFormat);
      pendingFormat = null;
    }
    inputBuffer.flip();
    inputBuffer.colorInfo = format.colorInfo;
    onQueueInputBuffer(inputBuffer);
    decoder.queueInputBuffer(inputBuffer);
    buffersInCodecCount++;
    decoderReceivedBuffers = true;
    decoderCounters.inputBufferCount++;
    inputBuffer = null;
    return true;
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
      throws ExoPlaybackException, VideoDecoderException {
    if (outputBuffer == null) {
      outputBuffer = dequeueOutputBuffer();
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
        clearOutputBuffer();
        outputStreamEnded = true;
      }
      return false;
    }

    boolean processedOutputBuffer = processOutputBuffer(positionUs, elapsedRealtimeUs);
    if (processedOutputBuffer) {
      onProcessedOutputBuffer(outputBuffer.timeUs);
      clearOutputBuffer();
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
      throws ExoPlaybackException, VideoDecoderException {
    if (initialPositionUs == C.TIME_UNSET) {
      initialPositionUs = positionUs;
    }

    long earlyUs = outputBuffer.timeUs - positionUs;
    if (!hasOutputSurface()) {
      // Skip frames in sync with playback, so we'll be at the right frame if the mode changes.
      if (isBufferLate(earlyUs)) {
        skipOutputBuffer(outputBuffer);
        return true;
      }
      return false;
    }

    long presentationTimeUs = outputBuffer.timeUs - outputStreamOffsetUs;
    Format format = formatQueue.pollFloor(presentationTimeUs);
    if (format != null) {
      outputFormat = format;
    }

    long elapsedRealtimeNowUs = SystemClock.elapsedRealtime() * 1000;
    boolean isStarted = getState() == STATE_STARTED;
    if (!renderedFirstFrame
        || (isStarted
            && shouldForceRenderOutputBuffer(earlyUs, elapsedRealtimeNowUs - lastRenderTimeUs))) {
      lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
      renderOutputBuffer(presentationTimeUs, outputFormat);
      return true;
    }

    if (!isStarted || positionUs == initialPositionUs) {
      return false;
    }

    if (shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs)
        && maybeDropBuffersToKeyframe(positionUs)) {
      return false;
    } else if (shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs)) {
      dropOutputBuffer(outputBuffer);
      return true;
    }

    if (earlyUs < 30000) {
      lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
      renderOutputBuffer(presentationTimeUs, outputFormat);
      return true;
    }

    return false;
  }

  private boolean shouldWaitForKeys(boolean bufferEncrypted) throws ExoPlaybackException {
    if (decoderDrmSession == null || (!bufferEncrypted && playClearSamplesWithoutKeys)) {
      return false;
    }
    @DrmSession.State int drmSessionState = decoderDrmSession.getState();
    if (drmSessionState == DrmSession.STATE_ERROR) {
      throw ExoPlaybackException.createForRenderer(decoderDrmSession.getError(), getIndex());
    }
    return drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS;
  }

  private void setJoiningDeadlineMs() {
    joiningDeadlineMs =
        allowedJoiningTimeMs > 0
            ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs)
            : C.TIME_UNSET;
  }

  private void clearRenderedFirstFrame() {
    renderedFirstFrame = false;
  }

  private void maybeNotifyRenderedFirstFrame(Surface surface) {
    if (!renderedFirstFrame) {
      renderedFirstFrame = true;
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void maybeRenotifyRenderedFirstFrame(Surface surface) {
    if (renderedFirstFrame) {
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void clearReportedVideoSize() {
    reportedWidth = Format.NO_VALUE;
    reportedHeight = Format.NO_VALUE;
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedWidth != Format.NO_VALUE || reportedHeight != Format.NO_VALUE) {
      eventDispatcher.videoSizeChanged(
          reportedWidth,
          reportedHeight,
          /* unappliedRotationDegrees= */ 0,
          /* pixelWidthHeightRatio= */ 1);
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
