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
package com.google.android.exoplayer2.audio;

import android.media.AudioManager;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.SystemClock;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;

/**
 * Decodes and renders audio using a {@link SimpleDecoder}.
 */
public abstract class SimpleDecoderAudioRenderer extends BaseRenderer implements MediaClock {

  private final EventDispatcher eventDispatcher;
  private final FormatHolder formatHolder;

  private DecoderCounters decoderCounters;
  private Format inputFormat;
  private SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
        ? extends AudioDecoderException> decoder;
  private DecoderInputBuffer inputBuffer;
  private SimpleOutputBuffer outputBuffer;

  private long currentPositionUs;
  private boolean allowPositionDiscontinuity;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;

  private final AudioTrack audioTrack;
  private int audioSessionId;

  private boolean audioTrackHasData;
  private long lastFeedElapsedRealtimeMs;

  public SimpleDecoderAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SimpleDecoderAudioRenderer(Handler eventHandler,
      AudioRendererEventListener eventListener) {
    this (eventHandler, eventListener, null, AudioManager.STREAM_MUSIC);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param streamType The type of audio stream for the {@link AudioTrack}.
   */
  public SimpleDecoderAudioRenderer(Handler eventHandler,
      AudioRendererEventListener eventListener, AudioCapabilities audioCapabilities,
      int streamType) {
    super(C.TRACK_TYPE_AUDIO);
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    audioTrack = new AudioTrack(audioCapabilities, streamType);
    formatHolder = new FormatHolder();
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    // Try and read a format if we don't have one already.
    if (inputFormat == null && !readFormat()) {
      // We can't make progress without one.
      return;
    }

    // If we don't have a decoder yet, we need to instantiate one.
    if (decoder == null) {
      try {
        long codecInitializingTimestamp = SystemClock.elapsedRealtime();
        TraceUtil.beginSection("createAudioDecoder");
        decoder = createDecoder(inputFormat);
        TraceUtil.endSection();
        long codecInitializedTimestamp = SystemClock.elapsedRealtime();
        eventDispatcher.decoderInitialized(decoder.getName(), codecInitializedTimestamp,
            codecInitializedTimestamp - codecInitializingTimestamp);
        decoderCounters.decoderInitCount++;
      } catch (AudioDecoderException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
    }

    // Rendering loop.
    try {
      TraceUtil.beginSection("drainAndFeed");
      while (drainOutputBuffer()) {}
      while (feedInputBuffer()) {}
      TraceUtil.endSection();
    } catch (AudioTrack.InitializationException | AudioTrack.WriteException
        | AudioDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    decoderCounters.ensureUpdated();
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @return The decoder.
   * @throws AudioDecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
      ? extends AudioDecoderException> createDecoder(Format format) throws AudioDecoderException;

  /**
   * Returns the format of audio buffers output by the decoder. Will not be called until the first
   * output buffer has been dequeued, so the decoder may use input data to determine the format.
   * <p>
   * The default implementation returns a 16-bit PCM format with the same channel count and sample
   * rate as the input.
   */
  protected Format getOutputFormat() {
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, inputFormat.channelCount, inputFormat.sampleRate, C.ENCODING_PCM_16BIT,
        null, null, 0, null);
  }

  private boolean drainOutputBuffer() throws AudioDecoderException,
      AudioTrack.InitializationException, AudioTrack.WriteException {
    if (outputStreamEnded) {
      return false;
    }

    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
    }

    if (outputBuffer.isEndOfStream()) {
      outputStreamEnded = true;
      audioTrack.handleEndOfStream();
      outputBuffer.release();
      outputBuffer = null;
      return false;
    }

    if (!audioTrack.isInitialized()) {
      Format outputFormat = getOutputFormat();
      audioTrack.configure(outputFormat.sampleMimeType, outputFormat.channelCount,
          outputFormat.sampleRate, outputFormat.pcmEncoding, 0);
      if (audioSessionId == AudioTrack.SESSION_ID_NOT_SET) {
        audioSessionId = audioTrack.initialize(AudioTrack.SESSION_ID_NOT_SET);
        eventDispatcher.audioSessionId(audioSessionId);
        onAudioSessionId(audioSessionId);
      } else {
        audioTrack.initialize(audioSessionId);
      }
      audioTrackHasData = false;
      if (getState() == STATE_STARTED) {
        audioTrack.play();
      }
    } else {
      // Check for AudioTrack underrun.
      boolean audioTrackHadData = audioTrackHasData;
      audioTrackHasData = audioTrack.hasPendingData();
      if (audioTrackHadData && !audioTrackHasData && getState() == STATE_STARTED) {
        long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
        long bufferSizeMs = C.usToMs(audioTrack.getBufferSizeUs());
        eventDispatcher.audioTrackUnderrun(audioTrack.getBufferSize(), bufferSizeMs,
            elapsedSinceLastFeedMs);
      }
    }

    int handleBufferResult = audioTrack.handleBuffer(outputBuffer.data, outputBuffer.timeUs);
    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      allowPositionDiscontinuity = true;
    }

    // Release the buffer if it was consumed.
    if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
      decoderCounters.renderedOutputBufferCount++;
      outputBuffer.release();
      outputBuffer = null;
      return true;
    }

    return false;
  }

  private boolean feedInputBuffer() throws AudioDecoderException {
    if (inputStreamEnded) {
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    int result = readSource(formatHolder, inputBuffer);
    if (result == C.RESULT_NOTHING_READ) {
      return false;
    }
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder.format);
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      return false;
    }
    inputBuffer.flip();
    decoder.queueInputBuffer(inputBuffer);
    decoderCounters.inputBufferCount++;
    inputBuffer = null;
    return true;
  }

  private void flushDecoder() {
    inputBuffer = null;
    if (outputBuffer != null) {
      outputBuffer.release();
      outputBuffer = null;
    }
    decoder.flush();
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded && !audioTrack.hasPendingData();
  }

  @Override
  public boolean isReady() {
    return audioTrack.hasPendingData()
        || (inputFormat != null && (isSourceReady() || outputBuffer != null));
  }

  @Override
  public long getPositionUs() {
    long newCurrentPositionUs = audioTrack.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioTrack.CURRENT_POSITION_NOT_SET) {
      currentPositionUs = allowPositionDiscontinuity ? newCurrentPositionUs
          : Math.max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
    return currentPositionUs;
  }

  /**
   * Called when the audio session id becomes known. Once the id is known it will not change (and
   * hence this method will not be called again) unless the renderer is disabled and then
   * subsequently re-enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param audioSessionId The audio session id.
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    audioTrack.reset();
    currentPositionUs = positionUs;
    allowPositionDiscontinuity = true;
    inputStreamEnded = false;
    outputStreamEnded = false;
    if (decoder != null) {
      flushDecoder();
    }
  }

  @Override
  protected void onStarted() {
    audioTrack.play();
  }

  @Override
  protected void onStopped() {
    audioTrack.pause();
  }

  @Override
  protected void onDisabled() {
    inputBuffer = null;
    outputBuffer = null;
    inputFormat = null;
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    try {
      if (decoder != null) {
        decoder.release();
        decoder = null;
        decoderCounters.decoderReleaseCount++;
      }
      audioTrack.release();
    } finally {
      decoderCounters.ensureUpdated();
      eventDispatcher.disabled(decoderCounters);
    }
  }

  private boolean readFormat() {
    int result = readSource(formatHolder, null);
    if (result == C.RESULT_FORMAT_READ) {
      onInputFormatChanged(formatHolder.format);
      return true;
    }
    return false;
  }

  private void onInputFormatChanged(Format newFormat) {
    inputFormat = newFormat;
    eventDispatcher.inputFormatChanged(newFormat);
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    switch (messageType) {
      case C.MSG_SET_VOLUME:
        audioTrack.setVolume((Float) message);
        break;
      case C.MSG_SET_PLAYBACK_PARAMS:
        audioTrack.setPlaybackParams((PlaybackParams) message);
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

}
