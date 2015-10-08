/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.ext.opus;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaClock;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SampleSourceTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.ext.opus.OpusDecoderWrapper.InputBuffer;
import com.google.android.exoplayer.ext.opus.OpusDecoderWrapper.OutputBuffer;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Decodes and renders audio using the native Opus decoder.
 *
 * @author vigneshv@google.com (Vignesh Venkatasubramanian)
 */
public final class LibopusAudioTrackRenderer extends SampleSourceTrackRenderer
    implements MediaClock {

  /**
   * Interface definition for a callback to be notified of {@link LibopusAudioTrackRenderer} events.
   */
  public interface EventListener {

    /**
     * Invoked when the {@link AudioTrack} fails to initialize.
     *
     * @param e The corresponding exception.
     */
    void onAudioTrackInitializationError(AudioTrack.InitializationException e);

    /**
     * Invoked when an {@link AudioTrack} write fails.
     *
     * @param e The corresponding exception.
     */
    void onAudioTrackWriteError(AudioTrack.WriteException e);

    /**
     * Invoked when decoding fails.
     *
     * @param e The corresponding exception.
     */
    void onDecoderError(OpusDecoderException e);

  }

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  public static final int MSG_SET_VOLUME = 1;

  private final Handler eventHandler;
  private final EventListener eventListener;
  private final MediaFormatHolder formatHolder;

  private MediaFormat format;
  private OpusDecoderWrapper decoder;
  private InputBuffer inputBuffer;
  private OutputBuffer outputBuffer;

  private long currentPositionUs;
  private boolean allowPositionDiscontinuity;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean sourceIsReady;
  private boolean notifyDiscontinuityToDecoder;

  private AudioTrack audioTrack;
  private int audioSessionId;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   */
  public LibopusAudioTrackRenderer(SampleSource source) {
    this(source, null, null);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public LibopusAudioTrackRenderer(SampleSource source, Handler eventHandler,
      EventListener eventListener) {
    super(source);
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    this.audioTrack = new AudioTrack();
    formatHolder = new MediaFormatHolder();
  }

  @Override
  protected MediaClock getMediaClock() {
    return this;
  }

  @Override
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return MimeTypes.AUDIO_OPUS.equalsIgnoreCase(mediaFormat.mimeType);
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    super.onEnabled(track, positionUs, joining);
    seekToInternal(positionUs);
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }
    sourceIsReady = continueBufferingSource(positionUs);
    checkForDiscontinuity(positionUs);

    // Try and read a format if we don't have one already.
    if (format == null && !readFormat(positionUs)) {
      // We can't make progress without one.
      return;
    }

    // If we don't have a decoder yet, we need to instantiate one.
    if (decoder == null) {
      // For opus, the format can contain upto 3 entries in initializationData in the following
      // exact order:
      // 1) Opus Header Information (required)
      // 2) Codec Delay in nanoseconds (required if Seek Preroll is present)
      // 3) Seek Preroll in nanoseconds (required if Codec Delay is present)
      List<byte[]> initializationData = format.initializationData;
      if (initializationData.size() < 1) {
        throw new ExoPlaybackException("Missing initialization data");
      }
      long codecDelayNs = -1;
      long seekPreRollNs = -1;
      if (initializationData.size() == 3) {
        if (initializationData.get(1).length != 8 || initializationData.get(2).length != 8) {
          throw new ExoPlaybackException("Invalid Codec Delay or Seek Preroll");
        }
        codecDelayNs =
            ByteBuffer.wrap(initializationData.get(1)).order(ByteOrder.LITTLE_ENDIAN).getLong();
        seekPreRollNs =
            ByteBuffer.wrap(initializationData.get(2)).order(ByteOrder.LITTLE_ENDIAN).getLong();
      }
      try {
        decoder = new OpusDecoderWrapper(initializationData.get(0), codecDelayNs, seekPreRollNs);
      } catch (OpusDecoderException e) {
        notifyDecoderError(e);
        throw new ExoPlaybackException(e);
      }
      decoder.start();
    }

    // Rendering loop.
    try {
      renderBuffer();
      while (feedInputBuffer(positionUs)) {}
    } catch (AudioTrack.InitializationException e) {
      notifyAudioTrackInitializationError(e);
      throw new ExoPlaybackException(e);
    } catch (AudioTrack.WriteException e) {
      notifyAudioTrackWriteError(e);
      throw new ExoPlaybackException(e);
    } catch (OpusDecoderException e) {
      notifyDecoderError(e);
      throw new ExoPlaybackException(e);
    }
  }

  private void renderBuffer() throws OpusDecoderException, AudioTrack.InitializationException,
      AudioTrack.WriteException {
    if (outputStreamEnded) {
      return;
    }

    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return;
      }
    }

    if (outputBuffer.getFlag(OpusDecoderWrapper.FLAG_END_OF_STREAM)) {
      outputStreamEnded = true;
      audioTrack.handleEndOfStream();
      decoder.releaseOutputBuffer(outputBuffer);
      outputBuffer = null;
      return;
    }

    if (!audioTrack.isInitialized()) {
      if (audioSessionId != AudioTrack.SESSION_ID_NOT_SET) {
        audioTrack.initialize(audioSessionId);
      } else {
        audioSessionId = audioTrack.initialize();
      }
      if (getState() == TrackRenderer.STATE_STARTED) {
        audioTrack.play();
      }
    }

    int handleBufferResult;
    handleBufferResult = audioTrack.handleBuffer(outputBuffer.data,
        outputBuffer.data.position(), outputBuffer.size, outputBuffer.timestampUs);

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      allowPositionDiscontinuity = true;
    }

    // Release the buffer if it was consumed.
    if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
      decoder.releaseOutputBuffer(outputBuffer);
      outputBuffer = null;
    }
  }

  private boolean feedInputBuffer(long positionUs) throws OpusDecoderException {
    if (inputStreamEnded) {
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.getInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    int result = readSource(positionUs, formatHolder, inputBuffer.sampleHolder, false);
    if (result == SampleSource.NOTHING_READ) {
      return false;
    }
    if (result == SampleSource.DISCONTINUITY_READ) {
      flushDecoder();
      return true;
    }
    if (result == SampleSource.FORMAT_READ) {
      format = formatHolder.format;
      return true;
    }
    if (result == SampleSource.END_OF_STREAM) {
      inputBuffer.setFlag(OpusDecoderWrapper.FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      inputStreamEnded = true;
      return false;
    }
    if (notifyDiscontinuityToDecoder) {
      notifyDiscontinuityToDecoder = false;
      inputBuffer.setFlag(OpusDecoderWrapper.FLAG_RESET_DECODER);
    }

    decoder.queueInputBuffer(inputBuffer);
    inputBuffer = null;
    return true;
  }

  private void checkForDiscontinuity(long positionUs) {
    if (decoder == null) {
      return;
    }
    int result = readSource(positionUs, formatHolder, null, true);
    if (result == SampleSource.DISCONTINUITY_READ) {
      flushDecoder();
    }
  }

  private void flushDecoder() {
    inputBuffer = null;
    outputBuffer = null;
    decoder.flush();
    notifyDiscontinuityToDecoder = true;
  }

  @Override
  protected boolean isEnded() {
    return outputStreamEnded && !audioTrack.hasPendingData();
  }

  @Override
  protected boolean isReady() {
    return audioTrack.hasPendingData() || (format != null && sourceIsReady);
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

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    super.seekTo(positionUs);
    seekToInternal(positionUs);
  }

  private void seekToInternal(long positionUs) {
    audioTrack.reset();
    currentPositionUs = positionUs;
    allowPositionDiscontinuity = true;
    inputStreamEnded = false;
    outputStreamEnded = false;
    sourceIsReady = false;
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
  protected void onDisabled() throws ExoPlaybackException {
    inputBuffer = null;
    outputBuffer = null;
    format = null;
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    try {
      if (decoder != null) {
        decoder.release();
        decoder = null;
      }
      audioTrack.release();
    } finally {
      super.onDisabled();
    }
  }

  private boolean readFormat(long positionUs) {
    int result = readSource(positionUs, formatHolder, null, false);
    if (result == SampleSource.FORMAT_READ) {
      format = formatHolder.format;
      audioTrack.reconfigure(format.getFrameworkMediaFormatV16(), false);
      return true;
    }
    return false;
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_VOLUME) {
      audioTrack.setVolume((Float) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  private void notifyAudioTrackInitializationError(final AudioTrack.InitializationException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onAudioTrackInitializationError(e);
        }
      });
    }
  }

  private void notifyAudioTrackWriteError(final AudioTrack.WriteException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onAudioTrackWriteError(e);
        }
      });
    }
  }

  private void notifyDecoderError(final OpusDecoderException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDecoderError(e);
        }
      });
    }
  }

}
