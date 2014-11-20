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
package com.google.android.exoplayer.audio;

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.MediaFormat;
import android.os.ConditionVariable;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Plays audio data. The implementation delegates to an {@link android.media.AudioTrack} and handles
 * playback position smoothing, non-blocking writes and reconfiguration.
 *
 * <p>If {@link #isInitialized} returns {@code false}, the instance can be {@link #initialize}d.
 * After initialization, start playback by calling {@link #play}.
 *
 * <p>Call {@link #handleBuffer} to write data for playback.
 *
 * <p>Call {@link #handleDiscontinuity} when a buffer is skipped.
 *
 * <p>Call {@link #reconfigure} when the output format changes.
 *
 * <p>Call {@link #reset} to free resources. It is safe to re-{@link #initialize} the instance.
 */
@TargetApi(16)
public final class AudioTrack {

  /**
   * Thrown when a failure occurs instantiating an {@link android.media.AudioTrack}.
   */
  public static class InitializationException extends Exception {

    /** The state as reported by {@link android.media.AudioTrack#getState()}. */
    public final int audioTrackState;

    public InitializationException(
        int audioTrackState, int sampleRate, int channelConfig, int bufferSize) {
      super("AudioTrack init failed: " + audioTrackState + ", Config(" + sampleRate + ", "
          + channelConfig + ", " + bufferSize + ")");
      this.audioTrackState = audioTrackState;
    }

  }

  /** Returned in the result of {@link #handleBuffer} if the buffer was discontinuous. */
  public static final int RESULT_POSITION_DISCONTINUITY = 1;
  /** Returned in the result of {@link #handleBuffer} if the buffer can be released. */
  public static final int RESULT_BUFFER_CONSUMED = 2;

  /** Represents an unset {@link android.media.AudioTrack} session identifier. */
  public static final int SESSION_ID_NOT_SET = 0;

  /** The default multiplication factor used when determining the size of the track's buffer. */
  public static final float DEFAULT_MIN_BUFFER_MULTIPLICATION_FACTOR = 4;

  /** Returned by {@link #getCurrentPositionUs} when the position is not set. */
  public static final long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;

  private static final String TAG = "AudioTrack";

  private static final long MICROS_PER_SECOND = 1000000L;

  /**
   * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more
   * than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_AUDIO_TIMESTAMP_OFFSET_US = 10 * MICROS_PER_SECOND;

  /**
   * AudioTrack latencies are deemed impossibly large if they are greater than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_LATENCY_US = 10 * MICROS_PER_SECOND;

  private static final int START_NOT_SET = 0;
  private static final int START_IN_SYNC = 1;
  private static final int START_NEED_SYNC = 2;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 500000;

  private final ConditionVariable releasingConditionVariable;
  private final AudioTimestampCompat audioTimestampCompat;
  private final long[] playheadOffsets;
  private final float minBufferMultiplicationFactor;

  private android.media.AudioTrack audioTrack;
  private int sampleRate;
  private int channelConfig;
  private int encoding;
  private int frameSize;
  private int minBufferSize;
  private int bufferSize;

  private int nextPlayheadOffsetIndex;
  private int playheadOffsetCount;
  private long smoothedPlayheadOffsetUs;
  private long lastPlayheadSampleTimeUs;
  private boolean audioTimestampSet;
  private long lastTimestampSampleTimeUs;
  private long lastRawPlaybackHeadPosition;
  private long rawPlaybackHeadWrapCount;

  private Method getLatencyMethod;
  private long submittedBytes;
  private int startMediaTimeState;
  private long startMediaTimeUs;
  private long resumeSystemTimeUs;
  private long latencyUs;
  private float volume;

  private byte[] temporaryBuffer;
  private int temporaryBufferOffset;
  private int temporaryBufferSize;

  /** Constructs an audio track using the default minimum buffer size multiplier. */
  public AudioTrack() {
    this(DEFAULT_MIN_BUFFER_MULTIPLICATION_FACTOR);
  }

  /** Constructs an audio track using the specified minimum buffer size multiplier. */
  public AudioTrack(float minBufferMultiplicationFactor) {
    Assertions.checkArgument(minBufferMultiplicationFactor >= 1);
    this.minBufferMultiplicationFactor = minBufferMultiplicationFactor;
    releasingConditionVariable = new ConditionVariable(true);
    if (Util.SDK_INT >= 19) {
      audioTimestampCompat = new AudioTimestampCompatV19();
    } else {
      audioTimestampCompat = new NoopAudioTimestampCompat();
    }
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod =
            android.media.AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    playheadOffsets = new long[MAX_PLAYHEAD_OFFSET_COUNT];
    volume = 1.0f;
    startMediaTimeState = START_NOT_SET;
  }

  /**
   * Returns whether the audio track has been successfully initialized via {@link #initialize} and
   * not yet {@link #reset}.
   */
  public boolean isInitialized() {
    return audioTrack != null;
  }

  /**
   * Returns the playback position in the stream starting at zero, in microseconds, or
   * {@link #CURRENT_POSITION_NOT_SET} if it is not yet available.
   *
   * <p>If the device supports it, the method uses the playback timestamp from
   * {@link android.media.AudioTrack#getTimestamp}. Otherwise, it derives a smoothed position by
   * sampling the {@link android.media.AudioTrack}'s frame position.
   *
   * @param sourceEnded Specify {@code true} if no more input buffers will be provided.
   * @return The playback position relative to the start of playback, in microseconds.
   */
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (!hasCurrentPositionUs()) {
      return CURRENT_POSITION_NOT_SET;
    }

    if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
      maybeSampleSyncParams();
    }

    long systemClockUs = System.nanoTime() / 1000;
    long currentPositionUs;
    if (audioTimestampSet) {
      // How long ago in the past the audio timestamp is (negative if it's in the future).
      long presentationDiff = systemClockUs - (audioTimestampCompat.getNanoTime() / 1000);
      long framesDiff = durationUsToFrames(presentationDiff);
      // The position of the frame that's currently being presented.
      long currentFramePosition = audioTimestampCompat.getFramePosition() + framesDiff;
      currentPositionUs = framesToDurationUs(currentFramePosition) + startMediaTimeUs;
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        currentPositionUs = getPlaybackPositionUs() + startMediaTimeUs;
      } else {
        // getPlayheadPositionUs() only has a granularity of ~20ms, so we base the position off the
        // system clock (and a smoothed offset between it and the playhead position) so as to
        // prevent jitter in the reported positions.
        currentPositionUs = systemClockUs + smoothedPlayheadOffsetUs + startMediaTimeUs;
      }
      if (!sourceEnded) {
        currentPositionUs -= latencyUs;
      }
    }

    return currentPositionUs;
  }

  /**
   * Initializes the audio track for writing new buffers using {@link #handleBuffer}.
   *
   * @return The audio track session identifier.
   */
  public int initialize() throws InitializationException {
    return initialize(SESSION_ID_NOT_SET);
  }

  /**
   * Initializes the audio track for writing new buffers using {@link #handleBuffer}.
   *
   * @param sessionId Audio track session identifier to re-use, or {@link #SESSION_ID_NOT_SET} to
   *     create a new one.
   * @return The new (or re-used) session identifier.
   */
  public int initialize(int sessionId) throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    releasingConditionVariable.block();

    if (sessionId == SESSION_ID_NOT_SET) {
      audioTrack = new android.media.AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
          channelConfig, encoding, bufferSize, android.media.AudioTrack.MODE_STREAM);
    } else {
      // Re-attach to the same audio session.
      audioTrack = new android.media.AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
          channelConfig, encoding, bufferSize, android.media.AudioTrack.MODE_STREAM, sessionId);
    }
    checkAudioTrackInitialized();
    setVolume(volume);
    return audioTrack.getAudioSessionId();
  }

  /**
   * Reconfigures the audio track to play back media in {@code format}. The encoding is assumed to
   * be {@link AudioFormat#ENCODING_PCM_16BIT}.
   */
  public void reconfigure(MediaFormat format) {
    reconfigure(format, AudioFormat.ENCODING_PCM_16BIT, 0);
  }

  /**
   * Reconfigures the audio track to play back media in {@code format}. Buffers passed to
   * {@link #handleBuffer} must using the specified {@code encoding}, which should be a constant
   * from {@link AudioFormat}.
   *
   * @param format Specifies the channel count and sample rate to play back.
   * @param encoding The format in which audio is represented.
   * @param bufferSize The total size of the playback buffer in bytes. Specify 0 to use a buffer
   *     size based on the minimum for format.
   */
  public void reconfigure(MediaFormat format, int encoding, int bufferSize) {
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
      case 8:
        channelConfig = AudioFormat.CHANNEL_OUT_7POINT1;
        break;
      default:
        throw new IllegalArgumentException("Unsupported channel count: " + channelCount);
    }

    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

    // TODO: Does channelConfig determine channelCount?
    if (audioTrack != null && this.sampleRate == sampleRate
        && this.channelConfig == channelConfig) {
      // We already have an existing audio track with the correct sample rate and channel config.
      return;
    }

    reset();

    minBufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);

    this.encoding = encoding;
    this.bufferSize =
        bufferSize == 0 ? (int) (minBufferMultiplicationFactor * minBufferSize) : bufferSize;
    this.sampleRate = sampleRate;
    this.channelConfig = channelConfig;

    frameSize = 2 * channelCount; // 2 bytes per 16 bit sample * number of channels.
  }

  /** Starts/resumes playing audio if the audio track has been initialized. */
  public void play() {
    if (isInitialized()) {
      resumeSystemTimeUs = System.nanoTime() / 1000;
      audioTrack.play();
    }
  }

  /** Signals to the audio track that the next buffer is discontinuous with the previous buffer. */
  public void handleDiscontinuity() {
    // Force resynchronization after a skipped buffer.
    if (startMediaTimeState == START_IN_SYNC) {
      startMediaTimeState = START_NEED_SYNC;
    }
  }

  /**
   * Attempts to write {@code size} bytes from {@code buffer} at {@code offset} to the audio track.
   * Returns a bit field containing {@link #RESULT_BUFFER_CONSUMED} if the buffer can be released
   * (due to having been written), and {@link #RESULT_POSITION_DISCONTINUITY} if the buffer was
   * discontinuous with previously written data.
   *
   * @param buffer The buffer containing audio data to play back.
   * @param offset The offset in the buffer from which to consume data.
   * @param size The number of bytes to consume from {@code buffer}.
   * @param presentationTimeUs Presentation timestamp of the next buffer in microseconds.
   * @return A bit field with {@link #RESULT_BUFFER_CONSUMED} if the buffer can be released, and
   *     {@link #RESULT_POSITION_DISCONTINUITY} if the buffer was not contiguous with previously
   *     written data.
   */
  public int handleBuffer(ByteBuffer buffer, int offset, int size, long presentationTimeUs) {
    int result = 0;

    if (temporaryBufferSize == 0 && size != 0) {
      // This is the first time we've seen this {@code buffer}.
      // Note: presentationTimeUs corresponds to the end of the sample, not the start.
      long bufferStartTime = presentationTimeUs - framesToDurationUs(bytesToFrames(size));
      if (startMediaTimeUs == START_NOT_SET) {
        startMediaTimeUs = Math.max(0, bufferStartTime);
        startMediaTimeState = START_IN_SYNC;
      } else {
        // Sanity check that bufferStartTime is consistent with the expected value.
        long expectedBufferStartTime = startMediaTimeUs
            + framesToDurationUs(bytesToFrames(submittedBytes));
        if (startMediaTimeState == START_IN_SYNC
            && Math.abs(expectedBufferStartTime - bufferStartTime) > 200000) {
          Log.e(TAG, "Discontinuity detected [expected " + expectedBufferStartTime + ", got "
              + bufferStartTime + "]");
          startMediaTimeState = START_NEED_SYNC;
        }
        if (startMediaTimeState == START_NEED_SYNC) {
          // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
          // number of bytes submitted.
          startMediaTimeUs += (bufferStartTime - expectedBufferStartTime);
          startMediaTimeState = START_IN_SYNC;
          result = RESULT_POSITION_DISCONTINUITY;
        }
      }
    }

    if (size == 0) {
      return result;
    }

    if (temporaryBufferSize == 0) {
      temporaryBufferSize = size;
      buffer.position(offset);
      if (Util.SDK_INT < 21) {
        // Copy {@code buffer} into {@code temporaryBuffer}.
        if (temporaryBuffer == null || temporaryBuffer.length < size) {
          temporaryBuffer = new byte[size];
        }
        buffer.get(temporaryBuffer, 0, size);
        temporaryBufferOffset = 0;
      }
    }

    int bytesWritten = 0;
    if (Util.SDK_INT < 21) {
      // Work out how many bytes we can write without the risk of blocking.
      int bytesPending = (int) (submittedBytes - framesToBytes(getPlaybackPositionFrames()));
      int bytesToWrite = bufferSize - bytesPending;
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(temporaryBufferSize, bytesToWrite);
        bytesWritten = audioTrack.write(temporaryBuffer, temporaryBufferOffset, bytesToWrite);
        if (bytesWritten < 0) {
          Log.w(TAG, "AudioTrack.write returned error code: " + bytesWritten);
        } else {
          temporaryBufferOffset += bytesWritten;
        }
      }
    } else {
      bytesWritten = writeNonBlockingV21(audioTrack, buffer, temporaryBufferSize);
    }

    temporaryBufferSize -= bytesWritten;
    submittedBytes += bytesWritten;
    if (temporaryBufferSize == 0) {
      result |= RESULT_BUFFER_CONSUMED;
    }

    return result;
  }

  @TargetApi(21)
  private static int writeNonBlockingV21(
      android.media.AudioTrack audioTrack, ByteBuffer buffer, int size) {
    return audioTrack.write(buffer, size, android.media.AudioTrack.WRITE_NON_BLOCKING);
  }

  /** Returns whether the audio track has more data pending that will be played back. */
  public boolean hasPendingData() {
    return audioTrack != null && bytesToFrames(submittedBytes) > getPlaybackPositionFrames();
  }

  /** Returns whether enough data has been supplied via {@link #handleBuffer} to begin playback. */
  public boolean hasEnoughDataToBeginPlayback() {
    return submittedBytes >= minBufferSize;
  }

  /** Sets the playback volume. */
  public void setVolume(float volume) {
    this.volume = volume;
    if (audioTrack != null) {
      if (Util.SDK_INT >= 21) {
        setVolumeV21(audioTrack, volume);
      } else {
        setVolumeV3(audioTrack, volume);
      }
    }
  }

  @TargetApi(21)
  private static void setVolumeV21(android.media.AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  @SuppressWarnings("deprecation")
  private static void setVolumeV3(android.media.AudioTrack audioTrack, float volume) {
    audioTrack.setStereoVolume(volume, volume);
  }

  /** Pauses playback. */
  public void pause() {
    if (audioTrack != null) {
      resetSyncParams();
      audioTrack.pause();
    }
  }

  /**
   * Releases resources associated with this instance asynchronously. Calling {@link #initialize}
   * will block until the audio track has been released, so it is safe to initialize immediately
   * after resetting.
   */
  public void reset() {
    if (audioTrack != null) {
      submittedBytes = 0;
      temporaryBufferSize = 0;
      lastRawPlaybackHeadPosition = 0;
      rawPlaybackHeadWrapCount = 0;
      startMediaTimeUs = START_NOT_SET;
      resetSyncParams();
      int playState = audioTrack.getPlayState();
      if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final android.media.AudioTrack toRelease = audioTrack;
      audioTrack = null;
      releasingConditionVariable.close();
      new Thread() {
        @Override
        public void run() {
          try {
            toRelease.release();
          } finally {
            releasingConditionVariable.open();
          }
        }
      }.start();
    }
  }

  /** Returns whether {@link #getCurrentPositionUs} can return the current playback position. */
  private boolean hasCurrentPositionUs() {
    return isInitialized() && startMediaTimeUs != START_NOT_SET;
  }

  /** Updates the audio track latency and playback position parameters. */
  private void maybeSampleSyncParams() {
    long playbackPositionUs = getPlaybackPositionUs();
    if (playbackPositionUs == 0) {
      // The AudioTrack hasn't output anything yet.
      return;
    }
    long systemClockUs = System.nanoTime() / 1000;
    if (systemClockUs - lastPlayheadSampleTimeUs >= MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US) {
      // Take a new sample and update the smoothed offset between the system clock and the playhead.
      playheadOffsets[nextPlayheadOffsetIndex] = playbackPositionUs - systemClockUs;
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
      audioTimestampSet = audioTimestampCompat.update(audioTrack);
      if (audioTimestampSet) {
        // Perform sanity checks on the timestamp.
        long audioTimestampUs = audioTimestampCompat.getNanoTime() / 1000;
        if (audioTimestampUs < resumeSystemTimeUs) {
          // The timestamp corresponds to a time before the track was most recently resumed.
          audioTimestampSet = false;
        } else if (Math.abs(audioTimestampUs - systemClockUs) > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
          // The timestamp time base is probably wrong.
          audioTimestampSet = false;
          Log.w(TAG, "Spurious audio timestamp: " + audioTimestampCompat.getFramePosition() + ", "
              + audioTimestampUs + ", " + systemClockUs);
        }
      }
      if (getLatencyMethod != null) {
        try {
          // Compute the audio track latency, excluding the latency due to the buffer (leaving
          // latency due to the mixer and audio hardware driver).
          latencyUs = (Integer) getLatencyMethod.invoke(audioTrack, (Object[]) null) * 1000L
              - framesToDurationUs(bytesToFrames(bufferSize));
          // Sanity check that the latency is non-negative.
          latencyUs = Math.max(latencyUs, 0);
          // Sanity check that the latency isn't too large.
          if (latencyUs > MAX_LATENCY_US) {
            Log.w(TAG, "Ignoring impossibly large audio latency: " + latencyUs);
            latencyUs = 0;
          }
        } catch (Exception e) {
          // The method existed, but doesn't work. Don't try again.
          getLatencyMethod = null;
        }
      }
      lastTimestampSampleTimeUs = systemClockUs;
    }
  }

  /**
   * Checks that {@link #audioTrack} has been successfully initialized. If it has then calling this
   * method is a no-op. If it hasn't then {@link #audioTrack} is released and set to null, and an
   * exception is thrown.
   *
   * @throws InitializationException If {@link #audioTrack} has not been successfully initialized.
   */
  private void checkAudioTrackInitialized() throws InitializationException {
    int state = audioTrack.getState();
    if (state == android.media.AudioTrack.STATE_INITIALIZED) {
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

    throw new InitializationException(state, sampleRate, channelConfig, bufferSize);
  }

  /**
   * {@link android.media.AudioTrack#getPlaybackHeadPosition()} returns a value intended to be
   * interpreted as an unsigned 32 bit integer, which also wraps around periodically. This method
   * returns the playback head position as a long that will only wrap around if the value exceeds
   * {@link Long#MAX_VALUE} (which in practice will never happen).
   *
   * @return {@link android.media.AudioTrack#getPlaybackHeadPosition()} of {@link #audioTrack}
   *     expressed as a long.
   */
  private long getPlaybackPositionFrames() {
    long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
    if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
      // The value must have wrapped around.
      rawPlaybackHeadWrapCount++;
    }
    lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
    return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
  }

  private long getPlaybackPositionUs() {
    return framesToDurationUs(getPlaybackPositionFrames());
  }

  private long framesToBytes(long frameCount) {
    return frameCount * frameSize;
  }

  private long bytesToFrames(long byteCount) {
    return byteCount / frameSize;
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * MICROS_PER_SECOND) / sampleRate;
  }

  private long durationUsToFrames(long durationUs) {
    return (durationUs * sampleRate) / MICROS_PER_SECOND;
  }

  private void resetSyncParams() {
    smoothedPlayheadOffsetUs = 0;
    playheadOffsetCount = 0;
    nextPlayheadOffsetIndex = 0;
    lastPlayheadSampleTimeUs = 0;
    audioTimestampSet = false;
    lastTimestampSampleTimeUs = 0;
  }

  /**
   * Interface exposing the {@link android.media.AudioTimestamp} methods we need that were added in
   * SDK 19.
   */
  private interface AudioTimestampCompat {

    /**
     * Returns true if the audioTimestamp was retrieved from the audioTrack.
     */
    boolean update(android.media.AudioTrack audioTrack);

    long getNanoTime();

    long getFramePosition();

  }

  /**
   * The AudioTimestampCompat implementation for SDK < 19 that does nothing or throws an exception.
   */
  private static final class NoopAudioTimestampCompat implements AudioTimestampCompat {

    @Override
    public boolean update(android.media.AudioTrack audioTrack) {
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
    public boolean update(android.media.AudioTrack audioTrack) {
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
