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

import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.audiofx.Virtualizer;
import android.os.Handler;

import java.nio.ByteBuffer;

/**
 * Decodes and renders audio using {@link MediaCodec} and {@link android.media.AudioTrack}.
 */
@TargetApi(16)
public class MediaCodecAudioTrackRenderer extends MediaCodecTrackRenderer {

  /**
   * Interface definition for a callback to be notified of {@link MediaCodecAudioTrackRenderer}
   * events.
   */
  public interface EventListener extends MediaCodecTrackRenderer.EventListener {

    /**
     * Invoked when an {@link AudioTrack} fails to initialize.
     *
     * @param e The corresponding exception.
     */
    void onAudioTrackInitializationError(AudioTrack.InitializationException e);

  }

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  public static final int MSG_SET_VOLUME = 1;

  private final EventListener eventListener;

  private final AudioTrack audioTrack;
  private int audioSessionId;

  private long currentPositionUs;

  /**
   * @param source The upstream source from which the renderer obtains samples.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source) {
    this(source, null, true);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    this(source, drmSessionManager, playClearSamplesWithoutKeys, null, null);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, Handler eventHandler,
      EventListener eventListener) {
    this(source, null, true, eventHandler, eventListener);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener) {
    this(source, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
        new AudioTrack());
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param minBufferMultiplicationFactor When instantiating an underlying
   *     {@link android.media.AudioTrack}, the size of the track is calculated as this value
   *     multiplied by the minimum buffer size obtained from
   *     {@link android.media.AudioTrack#getMinBufferSize(int, int, int)}. The multiplication
   *     factor must be greater than or equal to 1.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, float minBufferMultiplicationFactor,
      Handler eventHandler, EventListener eventListener) {
    this(source, null, true, minBufferMultiplicationFactor, eventHandler, eventListener);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param minBufferMultiplicationFactor When instantiating an underlying
   *     {@link android.media.AudioTrack}, the size of the track is calculated as this value
   *     multiplied by the minimum buffer size obtained from
   *     {@link android.media.AudioTrack#getMinBufferSize(int, int, int)}. The multiplication
   *     factor must be greater than or equal to 1.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, float minBufferMultiplicationFactor,
      Handler eventHandler, EventListener eventListener) {
    this(source, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
        new AudioTrack(minBufferMultiplicationFactor));
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisision. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioTrack Used for playing back decoded audio samples.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, Handler eventHandler, EventListener eventListener,
      AudioTrack audioTrack) {
    super(source, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener);
    this.eventListener = eventListener;
    this.audioTrack = Assertions.checkNotNull(audioTrack);
    this.audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
  }

  @Override
  protected boolean isTimeSource() {
    return true;
  }

  @Override
  protected boolean handlesMimeType(String mimeType) {
    return MimeTypes.isAudio(mimeType) && super.handlesMimeType(mimeType);
  }

  @Override
  protected void onEnabled(long positionUs, boolean joining) {
    super.onEnabled(positionUs, joining);
    currentPositionUs = Long.MIN_VALUE;
  }

  @Override
  protected void onOutputFormatChanged(MediaFormat format) {
    audioTrack.reconfigure(format);
  }

  /**
   * Invoked when the audio session id becomes known. Once the id is known it will not change
   * (and hence this method will not be invoked again) unless the renderer is disabled and then
   * subsequently re-enabled.
   * <p>
   * The default implementation is a no-op. One reason for overriding this method would be to
   * instantiate and enable a {@link Virtualizer} in order to spatialize the audio channels. For
   * this use case, any {@link Virtualizer} instances should be released in {@link #onDisabled()}
   * (if not before).
   *
   * @param audioSessionId The audio session id.
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    audioTrack.play();
  }

  @Override
  protected void onStopped() {
    audioTrack.pause();
    super.onStopped();
  }

  @Override
  protected boolean isEnded() {
    // We've exhausted the output stream, and the AudioTrack has either played all of the data
    // submitted, or has been fed insufficient data to begin playback.
    return super.isEnded() && (!audioTrack.hasPendingData()
        || !audioTrack.hasEnoughDataToBeginPlayback());
  }

  @Override
  protected boolean isReady() {
    return audioTrack.hasPendingData()
        || (super.isReady() && getSourceState() == SOURCE_STATE_READY_READ_MAY_FAIL);
  }

  @Override
  protected long getCurrentPositionUs() {
    long audioTrackCurrentPositionUs = audioTrack.getCurrentPositionUs(isEnded());
    if (audioTrackCurrentPositionUs == AudioTrack.CURRENT_POSITION_NOT_SET) {
      // Use the super class position before audio playback starts.
      currentPositionUs = Math.max(currentPositionUs, super.getCurrentPositionUs());
    } else {
      // Make sure we don't ever report time moving backwards.
      currentPositionUs = Math.max(currentPositionUs, audioTrackCurrentPositionUs);
    }
    return currentPositionUs;
  }

  @Override
  protected void onDisabled() {
    audioSessionId = AudioTrack.SESSION_ID_NOT_SET;
    try {
      audioTrack.reset();
    } finally {
      super.onDisabled();
    }
  }

  @Override
  protected void seekTo(long positionUs) throws ExoPlaybackException {
    super.seekTo(positionUs);
    // TODO: Try and re-use the same AudioTrack instance once [Internal: b/7941810] is fixed.
    audioTrack.reset();
    currentPositionUs = Long.MIN_VALUE;
  }

  @Override
  protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec,
      ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo, int bufferIndex, boolean shouldSkip)
      throws ExoPlaybackException {
    if (shouldSkip) {
      codec.releaseOutputBuffer(bufferIndex, false);
      codecCounters.skippedOutputBufferCount++;
      audioTrack.handleDiscontinuity();
      return true;
    }

    // Initialize and start the audio track now.
    if (!audioTrack.isInitialized()) {
      try {
        if (audioSessionId != AudioTrack.SESSION_ID_NOT_SET) {
          audioTrack.initialize(audioSessionId);
        } else {
          audioSessionId = audioTrack.initialize();
          onAudioSessionId(audioSessionId);
        }
      } catch (AudioTrack.InitializationException e) {
        notifyAudioTrackInitializationError(e);
        throw new ExoPlaybackException(e);
      }

      if (getState() == TrackRenderer.STATE_STARTED) {
        audioTrack.play();
      }
    }

    int handleBufferResult = audioTrack.handleBuffer(
        buffer, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs);

    // If we are out of sync, allow currentPositionUs to jump backwards.
    if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
      currentPositionUs = Long.MIN_VALUE;
    }

    // Release the buffer if it was consumed.
    if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
      codec.releaseOutputBuffer(bufferIndex, false);
      codecCounters.renderedOutputBufferCount++;
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

}
