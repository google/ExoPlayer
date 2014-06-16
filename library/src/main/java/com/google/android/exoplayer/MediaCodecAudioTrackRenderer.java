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

import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Decodes and renders audio using {@link MediaCodec} and {@link AudioTrack}.
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
    void onAudioTrackInitializationError(AudioTrackInitializationException e);

  }

  /**
   * Thrown when a failure occurs instantiating an audio track.
   */
  public static class AudioTrackInitializationException extends Exception {

    /**
     * The state as reported by {@link AudioTrack#getState()}
     */
    public final int audioTrackState;

    public AudioTrackInitializationException(int audioTrackState, int sampleRate,
        int channelConfig, int bufferSize) {
      super("AudioTrack init failed: " + audioTrackState + ", Config(" + sampleRate + ", " +
          channelConfig + ", " + bufferSize + ")");
      this.audioTrackState = audioTrackState;
    }

  }

  /**
   * The type of a message that can be passed to an instance of this class via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  public static final int MSG_SET_VOLUME = 1;

  /**
   * The default multiplication factor used when determining the size of the underlying
   * {@link AudioTrack}'s buffer.
   */
  public static final float DEFAULT_MIN_BUFFER_MULTIPLICATION_FACTOR = 4;

  private static final String TAG = "MediaCodecAudioTrackRenderer";

  private static final long MICROS_PER_SECOND = 1000000L;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 500000;

  private final EventListener eventListener;
  private final ConditionVariable audioTrackReleasingConditionVariable;
  private final AudioTimestampCompat audioTimestampCompat;
  private final long[] playheadOffsets;
  private final float minBufferMultiplicationFactor;
  private int nextPlayheadOffsetIndex;
  private int playheadOffsetCount;
  private long smoothedPlayheadOffsetUs;
  private long lastPlayheadSampleTimeUs;
  private boolean audioTimestampSet;
  private long lastTimestampSampleTimeUs;
  private long lastRawPlaybackHeadPosition;
  private long rawPlaybackHeadWrapCount;

  private int sampleRate;
  private int frameSize;
  private int channelConfig;
  private int minBufferSize;
  private int bufferSize;

  private AudioTrack audioTrack;
  private Method audioTrackGetLatencyMethod;
  private int audioSessionId;
  private long submittedBytes;
  private boolean audioTrackStartMediaTimeSet;
  private long audioTrackStartMediaTimeUs;
  private long audioTrackResumeSystemTimeUs;
  private long lastReportedCurrentPositionUs;
  private long audioTrackLatencyUs;
  private float volume;

  private byte[] temporaryBuffer;
  private int temporaryBufferOffset;
  private int temporaryBufferSize;

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
    this(source, drmSessionManager, playClearSamplesWithoutKeys,
        DEFAULT_MIN_BUFFER_MULTIPLICATION_FACTOR, eventHandler, eventListener);
  }

  /**
   * @param source The upstream source from which the renderer obtains samples.
   * @param minBufferMultiplicationFactor When instantiating an underlying {@link AudioTrack},
   *     the size of the track's is calculated as this value multiplied by the minimum buffer size
   *     obtained from {@link AudioTrack#getMinBufferSize(int, int, int)}. The multiplication
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
   * @param minBufferMultiplicationFactor When instantiating an underlying {@link AudioTrack},
   *     the size of the track's is calculated as this value multiplied by the minimum buffer size
   *     obtained from {@link AudioTrack#getMinBufferSize(int, int, int)}. The multiplication
   *     factor must be greater than or equal to 1.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioTrackRenderer(SampleSource source, DrmSessionManager drmSessionManager,
      boolean playClearSamplesWithoutKeys, float minBufferMultiplicationFactor,
      Handler eventHandler, EventListener eventListener) {
    super(source, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener);
    Assertions.checkState(minBufferMultiplicationFactor >= 1);
    this.minBufferMultiplicationFactor = minBufferMultiplicationFactor;
    this.eventListener = eventListener;
    audioTrackReleasingConditionVariable = new ConditionVariable(true);
    if (Util.SDK_INT >= 19) {
      audioTimestampCompat = new AudioTimestampCompatV19();
    } else {
      audioTimestampCompat = new NoopAudioTimestampCompat();
    }
    if (Util.SDK_INT >= 18) {
      try {
        audioTrackGetLatencyMethod = AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    playheadOffsets = new long[MAX_PLAYHEAD_OFFSET_COUNT];
    volume = 1.0f;
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
  protected void onEnabled(long timeUs, boolean joining) {
    super.onEnabled(timeUs, joining);
    lastReportedCurrentPositionUs = 0;
  }

  @Override
  protected void doSomeWork(long timeUs) throws ExoPlaybackException {
    super.doSomeWork(timeUs);
    maybeSampleSyncParams();
  }

  @Override
  protected void onOutputFormatChanged(MediaFormat format) {
    releaseAudioTrack();
    this.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    int channelConfig;
    switch (channelCount) {
      case 1:
        channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        break;
      case 2:
        channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        break;
      case 6:
        channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
        break;
      default:
        throw new IllegalArgumentException("Unsupported channel count: " + channelCount);
    }
    this.channelConfig = channelConfig;
    this.minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
        AudioFormat.ENCODING_PCM_16BIT);
    this.bufferSize = (int) (minBufferMultiplicationFactor * minBufferSize);
    this.frameSize = 2 * channelCount; // 2 bytes per 16 bit sample * number of channels.
  }

  private void initAudioTrack() throws ExoPlaybackException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    audioTrackReleasingConditionVariable.block();
    if (audioSessionId == 0) {
      audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
          AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
      checkAudioTrackInitialized();
      audioSessionId = audioTrack.getAudioSessionId();
      onAudioSessionId(audioSessionId);
    } else {
      // Re-attach to the same audio session.
      audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
          AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM, audioSessionId);
      checkAudioTrackInitialized();
    }
    audioTrack.setStereoVolume(volume, volume);
    if (getState() == TrackRenderer.STATE_STARTED) {
      audioTrackResumeSystemTimeUs = System.nanoTime() / 1000;
      audioTrack.play();
    }
  }

  /**
   * Checks that {@link #audioTrack} has been successfully initialized. If it has then calling this
   * method is a no-op. If it hasn't then {@link #audioTrack} is released and set to null, and an
   * exception is thrown.
   *
   * @throws ExoPlaybackException If {@link #audioTrack} has not been successfully initialized.
   */
  private void checkAudioTrackInitialized() throws ExoPlaybackException {
    int audioTrackState = audioTrack.getState();
    if (audioTrackState == AudioTrack.STATE_INITIALIZED) {
      return;
    }
    // The track is not successfully initialized. Release and null the track.
    try {
      audioTrack.release();
    } catch (Exception e) {
      // The track has already failed to initialize, so it wouldn't be that surprising if release
      // were to fail too. Swallow the exception.
    } finally {
      audioTrack = null;
    }
    // Propagate the relevant exceptions.
    AudioTrackInitializationException exception = new AudioTrackInitializationException(
        audioTrackState, sampleRate, channelConfig, bufferSize);
    notifyAudioTrackInitializationError(exception);
    throw new ExoPlaybackException(exception);
  }

  /**
   * Invoked when the audio session id becomes known. Once the id is known it will not change
   * (and hence this method will not be invoked again) unless the renderer is disabled and then
   * subsequently re-enabled.
   * <p>
   * The default implementation is a no-op. One reason for overriding this method would be to
   * instantiate and enable a {@link android.media.audiofx.Virtualizer} in order to spatialize the
   * audio channels. For this use case, any {@link android.media.audiofx.Virtualizer} instances
   * should be released in {@link #onDisabled()} (if not before).
   *
   * @param audioSessionId The audio session id.
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  private void releaseAudioTrack() {
    if (audioTrack != null) {
      submittedBytes = 0;
      temporaryBufferSize = 0;
      lastRawPlaybackHeadPosition = 0;
      rawPlaybackHeadWrapCount = 0;
      audioTrackStartMediaTimeUs = 0;
      audioTrackStartMediaTimeSet = false;
      resetSyncParams();
      int playState = audioTrack.getPlayState();
      if (playState == AudioTrack.PLAYSTATE_PLAYING) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final AudioTrack toRelease = audioTrack;
      audioTrack = null;
      audioTrackReleasingConditionVariable.close();
      new Thread() {
        @Override
        public void run() {
          try {
            toRelease.release();
          } finally {
            audioTrackReleasingConditionVariable.open();
          }
        }
      }.start();
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    if (audioTrack != null) {
      audioTrackResumeSystemTimeUs = System.nanoTime() / 1000;
      audioTrack.play();
    }
  }

  @Override
  protected void onStopped() {
    super.onStopped();
    if (audioTrack != null) {
      resetSyncParams();
      audioTrack.pause();
    }
  }

  @Override
  protected boolean isEnded() {
    // We've exhausted the output stream, and the AudioTrack has either played all of the data
    // submitted, or has been fed insufficient data to begin playback.
    return super.isEnded() && (getPendingFrameCount() == 0 || submittedBytes < minBufferSize);
  }

  @Override
  protected boolean isReady() {
    return getPendingFrameCount() > 0;
  }

  /**
   * This method uses a variety of techniques to compute the current position:
   *
   * 1. Prior to playback having started, calls up to the super class to obtain the pending seek
   *    position.
   * 2. During playback, uses AudioTimestamps obtained from AudioTrack.getTimestamp on supported
   *    devices.
   * 3. Else, derives a smoothed position by sampling the AudioTrack's frame position.
   */
  @Override
  protected long getCurrentPositionUs() {
    long systemClockUs = System.nanoTime() / 1000;
    long currentPositionUs;
    if (audioTrack == null || !audioTrackStartMediaTimeSet) {
      // The AudioTrack hasn't started.
      currentPositionUs = super.getCurrentPositionUs();
    } else if (audioTimestampSet) {
      // How long ago in the past the audio timestamp is (negative if it's in the future)
      long presentationDiff = systemClockUs - (audioTimestampCompat.getNanoTime() / 1000);
      long framesDiff = durationUsToFrames(presentationDiff);
      // The position of the frame that's currently being presented.
      long currentFramePosition = audioTimestampCompat.getFramePosition() + framesDiff;
      currentPositionUs = framesToDurationUs(currentFramePosition) + audioTrackStartMediaTimeUs;
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        currentPositionUs = getPlayheadPositionUs() + audioTrackStartMediaTimeUs;
      } else {
        // getPlayheadPositionUs() only has a granularity of ~20ms, so we base the position off the
        // system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        currentPositionUs = systemClockUs + smoothedPlayheadOffsetUs + audioTrackStartMediaTimeUs;
      }
      if (!isEnded()) {
        currentPositionUs -= audioTrackLatencyUs;
      }
    }
    // Make sure we don't ever report time moving backwards as a result of smoothing or switching
    // between the various code paths above.
    currentPositionUs = Math.max(lastReportedCurrentPositionUs, currentPositionUs);
    lastReportedCurrentPositionUs = currentPositionUs;
    return currentPositionUs;
  }

  private void maybeSampleSyncParams() {
    if (audioTrack == null || !audioTrackStartMediaTimeSet || getState() != STATE_STARTED) {
      // The AudioTrack isn't playing.
      return;
    }
    long playheadPositionUs = getPlayheadPositionUs();
    if (playheadPositionUs == 0) {
      // The AudioTrack hasn't output anything yet.
      return;
    }
    long systemClockUs = System.nanoTime() / 1000;
    if (systemClockUs - lastPlayheadSampleTimeUs >= MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US) {
      // Take a new sample and update the smoothed offset between the system clock and the playhead.
      playheadOffsets[nextPlayheadOffsetIndex] = playheadPositionUs - systemClockUs;
      nextPlayheadOffsetIndex = (nextPlayheadOffsetIndex + 1) % MAX_PLAYHEAD_OFFSET_COUNT;
      if (playheadOffsetCount < MAX_PLAYHEAD_OFFSET_COUNT) {
        playheadOffsetCount++;
      }
      lastPlayheadSampleTimeUs = systemClockUs;
      smoothedPlayheadOffsetUs = 0;
      for (int i = 0; i < playheadOffsetCount; i++) {
        smoothedPlayheadOffsetUs += playheadOffsets[i] / playheadOffsetCount;
      }
    }

    if (systemClockUs - lastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
      audioTimestampSet = audioTimestampCompat.initTimestamp(audioTrack);
      if (audioTimestampSet
          && (audioTimestampCompat.getNanoTime() / 1000) < audioTrackResumeSystemTimeUs) {
        // The timestamp was set, but it corresponds to a time before the track was most recently
        // resumed.
        audioTimestampSet = false;
      }
      if (audioTrackGetLatencyMethod != null) {
        try {
          // Compute the audio track latency, excluding the latency due to the buffer (leaving
          // latency due to the mixer and audio hardware driver).
          audioTrackLatencyUs =
              (Integer) audioTrackGetLatencyMethod.invoke(audioTrack, (Object[]) null) * 1000L -
              framesToDurationUs(bufferSize / frameSize);
          // Sanity check that the latency is non-negative.
          audioTrackLatencyUs = Math.max(audioTrackLatencyUs, 0);
        } catch (Exception e) {
          // The method existed, but doesn't work. Don't try again.
          audioTrackGetLatencyMethod = null;
        }
      }
      lastTimestampSampleTimeUs = systemClockUs;
    }
  }

  private void resetSyncParams() {
    smoothedPlayheadOffsetUs = 0;
    playheadOffsetCount = 0;
    nextPlayheadOffsetIndex = 0;
    lastPlayheadSampleTimeUs = 0;
    audioTimestampSet = false;
    lastTimestampSampleTimeUs = 0;
  }

  private long getPlayheadPositionUs() {
    return framesToDurationUs(getPlaybackHeadPosition());
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * MICROS_PER_SECOND) / sampleRate;
  }

  private long durationUsToFrames(long durationUs) {
    return (durationUs * sampleRate) / MICROS_PER_SECOND;
  }

  @Override
  protected void onDisabled() {
    super.onDisabled();
    releaseAudioTrack();
    audioSessionId = 0;
  }

  @Override
  protected void seekTo(long timeUs) throws ExoPlaybackException {
    super.seekTo(timeUs);
    // TODO: Try and re-use the same AudioTrack instance once [redacted] is fixed.
    releaseAudioTrack();
    lastReportedCurrentPositionUs = 0;
  }

  @Override
  protected boolean processOutputBuffer(long timeUs, MediaCodec codec, ByteBuffer buffer,
      MediaCodec.BufferInfo bufferInfo, int bufferIndex) throws ExoPlaybackException {
    if (temporaryBufferSize == 0) {
      // This is the first time we've seen this {@code buffer}.

      // Note: presentationTimeUs corresponds to the end of the sample, not the start.
      long bufferStartTime = bufferInfo.presentationTimeUs -
          framesToDurationUs(bufferInfo.size / frameSize);
      if (!audioTrackStartMediaTimeSet) {
        audioTrackStartMediaTimeUs = Math.max(0, bufferStartTime);
        audioTrackStartMediaTimeSet = true;
      } else {
        // Sanity check that bufferStartTime is consistent with the expected value.
        long expectedBufferStartTime = audioTrackStartMediaTimeUs +
            framesToDurationUs(submittedBytes / frameSize);
        if (Math.abs(expectedBufferStartTime - bufferStartTime) > 200000) {
          Log.e(TAG, "Discontinuity detected [expected " + expectedBufferStartTime + ", got " +
              bufferStartTime + "]");
          // Adjust audioTrackStartMediaTimeUs to compensate for the discontinuity. Also reset
          // lastReportedCurrentPositionUs to allow time to jump backwards if it really wants to.
          audioTrackStartMediaTimeUs += (bufferStartTime - expectedBufferStartTime);
          lastReportedCurrentPositionUs = 0;
        }
      }

      // Copy {@code buffer} into {@code temporaryBuffer}.
      // TODO: Bypass this copy step on versions of Android where [redacted] is implemented.
      if (temporaryBuffer == null || temporaryBuffer.length < bufferInfo.size) {
        temporaryBuffer = new byte[bufferInfo.size];
      }
      buffer.position(bufferInfo.offset);
      buffer.get(temporaryBuffer, 0, bufferInfo.size);
      temporaryBufferOffset = 0;
      temporaryBufferSize = bufferInfo.size;
    }

    if (audioTrack == null) {
      initAudioTrack();
    }

    // TODO: Don't bother doing this once [redacted] is fixed.
    // Work out how many bytes we can write without the risk of blocking.
    int bytesPending = (int) (submittedBytes - getPlaybackHeadPosition() * frameSize);
    int bytesToWrite = bufferSize - bytesPending;

    if (bytesToWrite > 0) {
      bytesToWrite = Math.min(temporaryBufferSize, bytesToWrite);
      audioTrack.write(temporaryBuffer, temporaryBufferOffset, bytesToWrite);
      temporaryBufferOffset += bytesToWrite;
      temporaryBufferSize -= bytesToWrite;
      submittedBytes += bytesToWrite;
      if (temporaryBufferSize == 0) {
        codec.releaseOutputBuffer(bufferIndex, false);
        codecCounters.renderedOutputBufferCount++;
        return true;
      }
    }

    return false;
  }

  /**
   * {@link AudioTrack#getPlaybackHeadPosition()} returns a value intended to be interpreted as
   * an unsigned 32 bit integer, which also wraps around periodically. This method returns the
   * playback head position as a long that will only wrap around if the value exceeds
   * {@link Long#MAX_VALUE} (which in practice will never happen).
   *
   * @return {@link AudioTrack#getPlaybackHeadPosition()} of {@link #audioTrack} expressed as a
   *     long.
   */
  private long getPlaybackHeadPosition() {
    long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
    if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
      // The value must have wrapped around.
      rawPlaybackHeadWrapCount++;
    }
    lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
    return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
  }

  private int getPendingFrameCount() {
    return audioTrack == null ?
        0 : (int) (submittedBytes / frameSize - getPlaybackHeadPosition());
  }

  @Override
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_SET_VOLUME) {
      setVolume((Float) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }

  private void setVolume(float volume) {
    this.volume = volume;
    if (audioTrack != null) {
      audioTrack.setStereoVolume(volume, volume);
    }
  }

  private void notifyAudioTrackInitializationError(final AudioTrackInitializationException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onAudioTrackInitializationError(e);
        }
      });
    }
  }

  /**
   * Interface exposing the {@link AudioTimestamp} methods we need that were added in SDK 19.
   */
  private interface AudioTimestampCompat {

    /**
     * Returns true if the audioTimestamp was retrieved from the audioTrack.
     */
    boolean initTimestamp(AudioTrack audioTrack);

    long getNanoTime();

    long getFramePosition();

  }

  /**
   * The AudioTimestampCompat implementation for SDK < 19 that does nothing or throws an exception.
   */
  private static final class NoopAudioTimestampCompat implements AudioTimestampCompat {

    @Override
    public boolean initTimestamp(AudioTrack audioTrack) {
      return false;
    }

    @Override
    public long getNanoTime() {
      // Should never be called if initTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

    @Override
    public long getFramePosition() {
      // Should never be called if initTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

  }

  /**
   * The AudioTimestampCompat implementation for SDK >= 19 that simply calls through to the actual
   * implementations added in SDK 19.
   */
  @TargetApi(19)
  private static final class AudioTimestampCompatV19 implements AudioTimestampCompat {

    private final AudioTimestamp audioTimestamp;

    public AudioTimestampCompatV19() {
      audioTimestamp = new AudioTimestamp();
    }

    @Override
    public boolean initTimestamp(AudioTrack audioTrack) {
      return audioTrack.getTimestamp(audioTimestamp);
    }

    @Override
    public long getNanoTime() {
      return audioTimestamp.nanoTime;
    }

    @Override
    public long getFramePosition() {
      return audioTimestamp.framePosition;
    }

  }

}
