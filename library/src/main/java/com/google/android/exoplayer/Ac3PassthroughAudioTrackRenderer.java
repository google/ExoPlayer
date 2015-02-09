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
package com.google.android.exoplayer;

import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.os.Handler;

import java.io.IOException;

/**
 * Renders encoded AC-3/enhanced AC-3 data to an {@link AudioTrack} for decoding on the playback
 * device.
 *
 * <p>To determine whether the playback device supports passthrough, receive an audio configuration
 * using {@link AudioCapabilitiesReceiver} and check whether the audio capabilities include
 * AC-3/enhanced AC-3 passthrough.
 */
@TargetApi(21)
public final class Ac3PassthroughAudioTrackRenderer extends TrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link Ac3PassthroughAudioTrackRenderer}
   * events.
   */
  public interface EventListener {

    /**
     * Invoked when an {@link AudioTrack} fails to initialize.
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

  }

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  public static final int MSG_SET_VOLUME = 1;

  private static final int SOURCE_STATE_NOT_READY = 0;
  private static final int SOURCE_STATE_READY = 1;

  /** Default buffer size for AC-3 packets from the sample source */
  private static final int DEFAULT_BUFFER_SIZE = 16384 * 2;

  private final Handler eventHandler;
  private final EventListener eventListener;

  private final SampleSource source;
  private final SampleHolder sampleHolder;
  private final MediaFormatHolder formatHolder;

  private int trackIndex;
  private MediaFormat format;

  private int sourceState;
  private boolean inputStreamEnded;
  private boolean shouldReadInputBuffer;

  private long currentPositionUs;

  private AudioTrack audioTrack;
  private int audioSessionId;

  /**
   * Constructs a new track renderer that passes AC-3 samples directly to an audio track.
   *
   * @param source The upstream source from which the renderer obtains samples.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public Ac3PassthroughAudioTrackRenderer(SampleSource source, Handler eventHandler,
      EventListener eventListener) {
    this.source = Assertions.checkNotNull(source);
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
    sampleHolder.replaceBuffer(DEFAULT_BUFFER_SIZE);
    formatHolder = new MediaFormatHolder();
    audioTrack = new AudioTrack();
    shouldReadInputBuffer = true;
  }

  @Override
  protected boolean isTimeSource() {
    return true;
  }

  @Override
  protected int doPrepare() throws ExoPlaybackException {
    try {
      boolean sourcePrepared = source.prepare();
      if (!sourcePrepared) {
        return TrackRenderer.STATE_UNPREPARED;
      }
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }

    for (int i = 0; i < source.getTrackCount(); i++) {
      // TODO(andrewlewis): Choose best format here after checking playout formats from HDMI config.
      if (handlesMimeType(source.getTrackInfo(i).mimeType)) {
        trackIndex = i;
        return TrackRenderer.STATE_PREPARED;
      }
    }

    return TrackRenderer.STATE_IGNORE;
  }

  private static boolean handlesMimeType(String mimeType) {
    return MimeTypes.AUDIO_MP4.equals(mimeType);
  }

  @Override
  protected void onEnabled(long positionUs, boolean joining) {
    source.enable(trackIndex, positionUs);
    sourceState = SOURCE_STATE_NOT_READY;
    inputStreamEnded = false;
    currentPositionUs = positionUs;
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    try {
      sourceState = source.continueBuffering(positionUs)
          ? (sourceState == SOURCE_STATE_NOT_READY ? SOURCE_STATE_READY : sourceState)
          : SOURCE_STATE_NOT_READY;

      if (format == null) {
        readFormat();
      } else {
        // Initialize and start the audio track now.
        if (!audioTrack.isInitialized()) {
          int oldAudioSessionId = audioSessionId;
          try {
            audioSessionId = audioTrack.initialize(oldAudioSessionId);
          } catch (AudioTrack.InitializationException e) {
            notifyAudioTrackInitializationError(e);
            throw new ExoPlaybackException(e);
          }

          if (getState() == TrackRenderer.STATE_STARTED) {
            audioTrack.play();
          }
        }

        feedInputBuffer();
      }
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
  }

  private void readFormat() throws IOException {
    int result = source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, false);
    if (result == SampleSource.FORMAT_READ) {
      format = formatHolder.format;
      // TODO: For E-AC-3 input, reconfigure with AudioFormat.ENCODING_E_AC3.
      audioTrack.reconfigure(format.getFrameworkMediaFormatV16(), AudioFormat.ENCODING_AC3, 0);
    }
  }

  private void feedInputBuffer() throws IOException, ExoPlaybackException {
    if (!audioTrack.isInitialized() || inputStreamEnded) {
      return;
    }

    // Get more data if we have run out.
    if (shouldReadInputBuffer) {
      sampleHolder.clearData();
      int result =
          source.readData(trackIndex, currentPositionUs, formatHolder, sampleHolder, false);
      if (result == SampleSource.FORMAT_READ) {
        format = formatHolder.format;
        audioTrack.reconfigure(format.getFrameworkMediaFormatV16(), AudioFormat.ENCODING_AC3, 0);
      }
      if (result == SampleSource.END_OF_STREAM) {
        inputStreamEnded = true;
      }
      if (result != SampleSource.SAMPLE_READ) {
        return;
      }
      shouldReadInputBuffer = false;
    }

    int handleBufferResult;
    try {
      handleBufferResult =
          audioTrack.handleBuffer(sampleHolder.data, 0, sampleHolder.size, sampleHolder.timeUs);
    } catch (AudioTrack.WriteException e) {
      notifyAudioTrackWriteError(e);
      throw new ExoPlaybackException(e);
    }

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      currentPositionUs = Long.MIN_VALUE;
    }

    // Get another input buffer if this one was consumed.
    shouldReadInputBuffer = (handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0;
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
  protected boolean isEnded() {
    // We've exhausted the input stream, and the AudioTrack has either played all of the data
    // submitted, or has been fed insufficient data to begin playback.
    return inputStreamEnded && (!audioTrack.hasPendingData()
        || !audioTrack.hasEnoughDataToBeginPlayback());
  }

  @Override
  protected boolean isReady() {
    return audioTrack.hasPendingData() || (format != null && sourceState != SOURCE_STATE_NOT_READY);
  }

  @Override
  protected long getCurrentPositionUs() {
    long audioTrackCurrentPositionUs = audioTrack.getCurrentPositionUs(isEnded());
    if (audioTrackCurrentPositionUs != AudioTrack.CURRENT_POSITION_NOT_SET) {
      // Make sure we don't ever report time moving backwards.
      currentPositionUs = Math.max(currentPositionUs, audioTrackCurrentPositionUs);
    }
    return currentPositionUs;
  }

  @Override
  protected long getDurationUs() {
    return source.getTrackInfo(trackIndex).durationUs;
  }

  @Override
  protected long getBufferedPositionUs() {
    long sourceBufferedPosition = source.getBufferedPositionUs();
    return sourceBufferedPosition == UNKNOWN_TIME_US || sourceBufferedPosition == END_OF_TRACK_US
        ? sourceBufferedPosition : Math.max(sourceBufferedPosition, getCurrentPositionUs());
  }

  @Override
  protected void onDisabled() {
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    shouldReadInputBuffer = true;
    audioTrack.reset();
  }

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    source.seekToUs(positionUs);
    sourceState = SOURCE_STATE_NOT_READY;
    inputStreamEnded = false;
    shouldReadInputBuffer = true;

    // TODO: Try and re-use the same AudioTrack instance once [Internal: b/7941810] is fixed.
    audioTrack.reset();
    currentPositionUs = Long.MIN_VALUE;
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

}
