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
package com.google.android.exoplayer.util.extensions;

import com.google.android.exoplayer.AudioTrackRendererEventListener;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.MediaClock;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;

import java.util.List;

/**
 * Decodes and renders audio using a {@link SimpleDecoder}.
 */
public abstract class AudioDecoderTrackRenderer extends TrackRenderer implements MediaClock {

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  public static final int MSG_SET_VOLUME = 1;

  public final CodecCounters codecCounters = new CodecCounters();

  private final Handler eventHandler;
  private final AudioTrackRendererEventListener eventListener;
  private final FormatHolder formatHolder;

  private Format format;
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

  public AudioDecoderTrackRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public AudioDecoderTrackRenderer(Handler eventHandler,
      AudioTrackRendererEventListener eventListener) {
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    this.audioTrack = new AudioTrack();
    formatHolder = new FormatHolder();
  }

  @Override
  protected MediaClock getMediaClock() {
    return this;
  }

  @Override
  protected void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    // Try and read a format if we don't have one already.
    if (format == null && !readFormat()) {
      // We can't make progress without one.
      return;
    }

    // If we don't have a decoder yet, we need to instantiate one.
    if (decoder == null) {
      try {
        decoder = createDecoder(format.initializationData);
      } catch (AudioDecoderException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
      codecCounters.codecInitCount++;
    }

    // Rendering loop.
    try {
      renderBuffer();
      while (feedInputBuffer()) {}
    } catch (AudioTrack.InitializationException e) {
      notifyAudioTrackInitializationError(e);
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    } catch (AudioTrack.WriteException e) {
      notifyAudioTrackWriteError(e);
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    } catch (AudioDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    codecCounters.ensureUpdated();
  }

  @Override
  public int getTrackType() {
    return C.TRACK_TYPE_AUDIO;
  }

  protected abstract SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
      ? extends AudioDecoderException> createDecoder(List<byte[]> initializationData)
      throws AudioDecoderException;

  private void renderBuffer() throws AudioDecoderException, AudioTrack.InitializationException,
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

    if (outputBuffer.isEndOfStream()) {
      outputStreamEnded = true;
      audioTrack.handleEndOfStream();
      outputBuffer.release();
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
    handleBufferResult = audioTrack.handleBuffer(outputBuffer.data, outputBuffer.timestampUs);

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      allowPositionDiscontinuity = true;
    }

    // Release the buffer if it was consumed.
    if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
      codecCounters.renderedOutputBufferCount++;
      outputBuffer.release();
      outputBuffer = null;
    }
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
    if (result == TrackStream.NOTHING_READ) {
      return false;
    }
    if (result == TrackStream.FORMAT_READ) {
      format = formatHolder.format;
      return true;
    }
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
    }

    decoder.queueInputBuffer(inputBuffer);
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
  protected boolean isEnded() {
    return outputStreamEnded && !audioTrack.hasPendingData();
  }

  @Override
  protected boolean isReady() {
    return audioTrack.hasPendingData()
        || (format != null && (isSourceReady() || outputBuffer != null));
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
  protected void reset(long positionUs) {
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
  protected void onEnabled(Format[] formats, boolean joining) throws ExoPlaybackException {
    notifyAudioCodecCounters();
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
    format = null;
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    try {
      if (decoder != null) {
        decoder.release();
        decoder = null;
        codecCounters.codecReleaseCount++;
      }
      audioTrack.release();
    } finally {
      super.onDisabled();
    }
  }

  private boolean readFormat() {
    int result = readSource(formatHolder, null);
    if (result == TrackStream.FORMAT_READ) {
      format = formatHolder.format;
      audioTrack.configure(MimeTypes.AUDIO_RAW, format.channelCount, format.sampleRate);
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
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onAudioTrackInitializationError(e);
        }
      });
    }
  }

  private void notifyAudioTrackWriteError(final AudioTrack.WriteException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onAudioTrackWriteError(e);
        }
      });
    }
  }

  private void notifyAudioCodecCounters() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onAudioCodecCounters(codecCounters);
        }
      });
    }
  }

}
