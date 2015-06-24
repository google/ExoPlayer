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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Ac3Util;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
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
 *
 * <p>Call {@link #release} when the instance will no longer be used.
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

  /**
   * Thrown when a failure occurs writing to an {@link android.media.AudioTrack}.
   */
  public static class WriteException extends Exception {

    /** The value returned from {@link android.media.AudioTrack#write(byte[], int, int)}. */
    public final int errorCode;

    public WriteException(int errorCode) {
      super("AudioTrack write failed: " + errorCode);
      this.errorCode = errorCode;
    }

  }

  /** Returned in the result of {@link #handleBuffer} if the buffer was discontinuous. */
  public static final int RESULT_POSITION_DISCONTINUITY = 1;
  /** Returned in the result of {@link #handleBuffer} if the buffer can be released. */
  public static final int RESULT_BUFFER_CONSUMED = 2;

  /** Represents an unset {@link android.media.AudioTrack} session identifier. */
  public static final int SESSION_ID_NOT_SET = 0;

  /** Returned by {@link #getCurrentPositionUs} when the position is not set. */
  public static final long CURRENT_POSITION_NOT_SET = Long.MIN_VALUE;

  /** A minimum length for the {@link android.media.AudioTrack} buffer, in microseconds. */
  private static final long MIN_BUFFER_DURATION_US = 250000;
  /** A maximum length for the {@link android.media.AudioTrack} buffer, in microseconds. */
  private static final long MAX_BUFFER_DURATION_US = 750000;
  /**
   * A multiplication factor to apply to the minimum buffer size requested by the underlying
   * {@link android.media.AudioTrack}.
   */
  private static final int BUFFER_MULTIPLICATION_FACTOR = 4;

  private static final String TAG = "AudioTrack";

  /**
   * AudioTrack timestamps are deemed spurious if they are offset from the system clock by more
   * than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_AUDIO_TIMESTAMP_OFFSET_US = 5 * C.MICROS_PER_SECOND;

  /**
   * AudioTrack latencies are deemed impossibly large if they are greater than this amount.
   *
   * <p>This is a fail safe that should not be required on correctly functioning devices.
   */
  private static final long MAX_LATENCY_US = 5 * C.MICROS_PER_SECOND;

  /** Value for ac3Bitrate before the bitrate has been calculated. */
  private static final int UNKNOWN_AC3_BITRATE = 0;

  private static final int START_NOT_SET = 0;
  private static final int START_IN_SYNC = 1;
  private static final int START_NEED_SYNC = 2;

  private static final int MAX_PLAYHEAD_OFFSET_COUNT = 10;
  private static final int MIN_PLAYHEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
  private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 500000;

  /**
   * Set to {@code true} to enable a workaround for an issue where an audio effect does not keep its
   * session active across releasing/initializing a new audio track, on platform API version < 21.
   * The flag must be set before creating the player.
   */
  public static boolean enablePreV21AudioSessionWorkaround = false;

  private final ConditionVariable releasingConditionVariable;
  private final long[] playheadOffsets;
  private final AudioTrackUtil audioTrackUtil;

  /** Used to keep the audio session active on pre-V21 builds (see {@link #initialize()}). */
  private android.media.AudioTrack keepSessionIdAudioTrack;

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

  private boolean isAc3;

  /** Bitrate measured in kilobits per second, if {@link #isAc3} is true. */
  private int ac3Bitrate;

  public AudioTrack() {
    releasingConditionVariable = new ConditionVariable(true);
    if (Util.SDK_INT >= 18) {
      try {
        getLatencyMethod =
            android.media.AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
      } catch (NoSuchMethodException e) {
        // There's no guarantee this method exists. Do nothing.
      }
    }
    if (Util.SDK_INT >= 19) {
      audioTrackUtil = new AudioTrackUtilV19();
    } else {
      audioTrackUtil = new AudioTrackUtil();
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
      long presentationDiff = systemClockUs - (audioTrackUtil.getTimestampNanoTime() / 1000);
      long framesDiff = durationUsToFrames(presentationDiff);
      // The position of the frame that's currently being presented.
      long currentFramePosition = audioTrackUtil.getTimestampFramePosition() + framesDiff;
      currentPositionUs = framesToDurationUs(currentFramePosition) + startMediaTimeUs;
    } else {
      if (playheadOffsetCount == 0) {
        // The AudioTrack has started, but we don't have any samples to compute a smoothed position.
        currentPositionUs = audioTrackUtil.getPlaybackHeadPositionUs() + startMediaTimeUs;
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

    sessionId = audioTrack.getAudioSessionId();
    if (enablePreV21AudioSessionWorkaround) {
      if (Util.SDK_INT < 21) {
        // The workaround creates an audio track with a one byte buffer on the same session, and
        // does not release it until this object is released, which keeps the session active.
        if (keepSessionIdAudioTrack != null
            && sessionId != keepSessionIdAudioTrack.getAudioSessionId()) {
          releaseKeepSessionIdAudioTrack();
        }
        if (keepSessionIdAudioTrack == null) {
          int sampleRate = 4000; // Equal to private android.media.AudioTrack.MIN_SAMPLE_RATE.
          int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
          int encoding = AudioFormat.ENCODING_PCM_16BIT;
          int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
          keepSessionIdAudioTrack = new android.media.AudioTrack(AudioManager.STREAM_MUSIC,
              sampleRate, channelConfig, encoding, bufferSize, android.media.AudioTrack.MODE_STATIC,
              sessionId);
        }
      }
    }

    audioTrackUtil.reconfigure(audioTrack, isAc3);
    setVolume(volume);

    return sessionId;
  }

  /**
   * Reconfigures the audio track to play back media in {@code format}, inferring a buffer size from
   * the format.
   */
  public void reconfigure(MediaFormat format) {
    reconfigure(format, 0);
  }

  /**
   * Reconfigures the audio track to play back media in {@code format}.
   *
   * @param format Specifies the channel count and sample rate to play back.
   * @param specifiedBufferSize A specific size for the playback buffer in bytes, or 0 to use a
   *     size inferred from the format.
   */
  public void reconfigure(MediaFormat format, int specifiedBufferSize) {
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
    String mimeType = format.getString(MediaFormat.KEY_MIME);

    // TODO: Does channelConfig determine channelCount?
    int encoding = MimeTypes.getEncodingForMimeType(mimeType);
    boolean isAc3 = encoding == C.ENCODING_AC3 || encoding == C.ENCODING_E_AC3;
    if (isInitialized() && this.sampleRate == sampleRate && this.channelConfig == channelConfig
        && !this.isAc3 && !isAc3) {
      // We already have an existing audio track with the correct sample rate and channel config.
      return;
    }

    reset();

    this.encoding = encoding;
    this.sampleRate = sampleRate;
    this.channelConfig = channelConfig;
    this.isAc3 = isAc3;
    ac3Bitrate = UNKNOWN_AC3_BITRATE; // Calculated on receiving the first buffer if isAc3 is true.
    frameSize = 2 * channelCount; // 2 bytes per 16 bit sample * number of channels.
    minBufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);
    Assertions.checkState(minBufferSize != android.media.AudioTrack.ERROR_BAD_VALUE);

    if (specifiedBufferSize != 0) {
      bufferSize = specifiedBufferSize;
    } else {
      int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
      int minAppBufferSize = (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * frameSize;
      int maxAppBufferSize = (int) Math.max(minBufferSize,
          durationUsToFrames(MAX_BUFFER_DURATION_US) * frameSize);
      bufferSize = multipliedBufferSize < minAppBufferSize ? minAppBufferSize
          : multipliedBufferSize > maxAppBufferSize ? maxAppBufferSize
          : multipliedBufferSize;
    }
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
   * @throws WriteException If an error occurs writing the audio data.
   */
  public int handleBuffer(ByteBuffer buffer, int offset, int size, long presentationTimeUs)
      throws WriteException {
    if (size == 0) {
      return RESULT_BUFFER_CONSUMED;
    }

    // Workarounds for issues with AC-3 passthrough AudioTracks on API versions 21/22:
    if (Util.SDK_INT <= 22 && isAc3) {
      // An AC-3 audio track continues to play data written while it is paused. Stop writing so its
      // buffer empties. See [Internal: b/18899620].
      if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED) {
        return 0;
      }

      // A new AC-3 audio track's playback position continues to increase from the old track's
      // position for a short time after is has been released. Avoid writing data until the playback
      // head position actually returns to zero.
      if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_STOPPED
          && audioTrackUtil.getPlaybackHeadPosition() != 0) {
        return 0;
      }
    }

    int result = 0;
    if (temporaryBufferSize == 0) {
      if (isAc3 && ac3Bitrate == UNKNOWN_AC3_BITRATE) {
        ac3Bitrate = Ac3Util.getBitrate(size, sampleRate);
      }

      // This is the first time we've seen this {@code buffer}.
      // Note: presentationTimeUs corresponds to the end of the sample, not the start.
      long bufferStartTime = presentationTimeUs - framesToDurationUs(bytesToFrames(size));
      if (startMediaTimeState == START_NOT_SET) {
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
          result |= RESULT_POSITION_DISCONTINUITY;
        }
      }
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
      int bytesPending =
          (int) (submittedBytes - (audioTrackUtil.getPlaybackHeadPosition() * frameSize));
      int bytesToWrite = bufferSize - bytesPending;
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(temporaryBufferSize, bytesToWrite);
        bytesWritten = audioTrack.write(temporaryBuffer, temporaryBufferOffset, bytesToWrite);
        if (bytesWritten >= 0) {
          temporaryBufferOffset += bytesWritten;
        }
      }
    } else {
      bytesWritten = writeNonBlockingV21(audioTrack, buffer, temporaryBufferSize);
    }

    if (bytesWritten < 0) {
      throw new WriteException(bytesWritten);
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
    return isInitialized()
        && (bytesToFrames(submittedBytes) > audioTrackUtil.getPlaybackHeadPosition()
            || audioTrackUtil.overrideHasPendingData());
  }

  /** Returns whether enough data has been supplied via {@link #handleBuffer} to begin playback. */
  public boolean hasEnoughDataToBeginPlayback() {
    // The value of minBufferSize can be slightly less than what's actually required for playback
    // to start, hence the multiplication factor.
    return submittedBytes > (minBufferSize * 3) / 2;
  }

  /** Sets the playback volume. */
  public void setVolume(float volume) {
    this.volume = volume;
    if (isInitialized()) {
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
    if (isInitialized()) {
      resetSyncParams();
      audioTrack.pause();
    }
  }

  /**
   * Releases the underlying audio track asynchronously. Calling {@link #initialize} will block
   * until the audio track has been released, so it is safe to initialize immediately after
   * resetting. The audio session may remain active until the instance is {@link #release}d.
   */
  public void reset() {
    if (isInitialized()) {
      submittedBytes = 0;
      temporaryBufferSize = 0;
      startMediaTimeState = START_NOT_SET;
      latencyUs = 0;
      resetSyncParams();
      int playState = audioTrack.getPlayState();
      if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final android.media.AudioTrack toRelease = audioTrack;
      audioTrack = null;
      audioTrackUtil.reconfigure(null, false);
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

  /** Releases all resources associated with this instance. */
  public void release() {
    reset();
    releaseKeepSessionIdAudioTrack();
  }

  /** Releases {@link #keepSessionIdAudioTrack} asynchronously, if it is non-{@code null}. */
  private void releaseKeepSessionIdAudioTrack() {
    if (keepSessionIdAudioTrack == null) {
      return;
    }

    // AudioTrack.release can take some time, so we call it on a background thread.
    final android.media.AudioTrack toRelease = keepSessionIdAudioTrack;
    keepSessionIdAudioTrack = null;
    new Thread() {
      @Override
      public void run() {
        toRelease.release();
      }
    }.start();
  }

  /** Returns whether {@link #getCurrentPositionUs} can return the current playback position. */
  private boolean hasCurrentPositionUs() {
    return isInitialized() && startMediaTimeState != START_NOT_SET;
  }

  /** Updates the audio track latency and playback position parameters. */
  private void maybeSampleSyncParams() {
    long playbackPositionUs = audioTrackUtil.getPlaybackHeadPositionUs();
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

    // Don't sample the timestamp and latency if this is an AC-3 passthrough AudioTrack, as the
    // returned values cause audio/video synchronization to be incorrect.
    if (!isAc3 && systemClockUs - lastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
      audioTimestampSet = audioTrackUtil.updateTimestamp();
      if (audioTimestampSet) {
        // Perform sanity checks on the timestamp.
        long audioTimestampUs = audioTrackUtil.getTimestampNanoTime() / 1000;
        long audioTimestampFramePosition = audioTrackUtil.getTimestampFramePosition();
        if (audioTimestampUs < resumeSystemTimeUs) {
          // The timestamp corresponds to a time before the track was most recently resumed.
          audioTimestampSet = false;
        } else if (Math.abs(audioTimestampUs - systemClockUs) > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
          // The timestamp time base is probably wrong.
          audioTimestampSet = false;
          Log.w(TAG, "Spurious audio timestamp (system clock mismatch): "
              + audioTimestampFramePosition + ", " + audioTimestampUs + ", " + systemClockUs + ", "
              + playbackPositionUs);
        } else if (Math.abs(framesToDurationUs(audioTimestampFramePosition) - playbackPositionUs)
            > MAX_AUDIO_TIMESTAMP_OFFSET_US) {
          // The timestamp frame position is probably wrong.
          audioTimestampSet = false;
          Log.w(TAG, "Spurious audio timestamp (frame position mismatch): "
              + audioTimestampFramePosition + ", " + audioTimestampUs + ", " + systemClockUs + ", "
              + playbackPositionUs);
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

  private long bytesToFrames(long byteCount) {
    if (isAc3) {
      return
          ac3Bitrate == UNKNOWN_AC3_BITRATE ? 0L : byteCount * 8 * sampleRate / (1000 * ac3Bitrate);
    } else {
      return byteCount / frameSize;
    }
  }

  private long framesToDurationUs(long frameCount) {
    return (frameCount * C.MICROS_PER_SECOND) / sampleRate;
  }

  private long durationUsToFrames(long durationUs) {
    return (durationUs * sampleRate) / C.MICROS_PER_SECOND;
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
   * Wraps an {@link android.media.AudioTrack} to expose useful utility methods.
   */
  private static class AudioTrackUtil {

    protected android.media.AudioTrack audioTrack;
    private boolean isPassthrough;
    private int sampleRate;
    private long lastRawPlaybackHeadPosition;
    private long rawPlaybackHeadWrapCount;
    private long passthroughWorkaroundPauseOffset;

    /**
     * Reconfigures the audio track utility helper to use the specified {@code audioTrack}.
     *
     * @param audioTrack The audio track to wrap.
     * @param isPassthrough Whether the audio track is used for passthrough (e.g. AC-3) playback.
     */
    public void reconfigure(android.media.AudioTrack audioTrack, boolean isPassthrough) {
      this.audioTrack = audioTrack;
      this.isPassthrough = isPassthrough;
      lastRawPlaybackHeadPosition = 0;
      rawPlaybackHeadWrapCount = 0;
      passthroughWorkaroundPauseOffset = 0;
      if (audioTrack != null) {
        sampleRate = audioTrack.getSampleRate();
      }
    }

    /**
     * Returns whether the audio track should behave as though it has pending data. This is to work
     * around an issue on platform API versions 21/22 where AC-3 audio tracks can't be paused, so we
     * empty their buffers when paused. In this case, they should still behave as if they have
     * pending data, otherwise writing will never resume.
     *
     * @see #handleBuffer
     */
    public boolean overrideHasPendingData() {
      return Util.SDK_INT <= 22 && isPassthrough
          && audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED
          && audioTrack.getPlaybackHeadPosition() == 0;
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
    public long getPlaybackHeadPosition() {
      long rawPlaybackHeadPosition = 0xFFFFFFFFL & audioTrack.getPlaybackHeadPosition();
      if (Util.SDK_INT <= 22 && isPassthrough) {
        // Work around issues with passthrough/direct AudioTracks on platform API versions 21/22:
        // - After resetting, the new AudioTrack's playback position continues to increase for a
        //   short time from the old AudioTrack's position, while in the PLAYSTATE_STOPPED state.
        // - The playback head position jumps back to zero on paused passthrough/direct audio
        //   tracks. See [Internal: b/19187573].
        if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_STOPPED) {
          // Prevent detecting a wrapped position.
          lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
        } else if (audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED
            && rawPlaybackHeadPosition == 0) {
          passthroughWorkaroundPauseOffset = lastRawPlaybackHeadPosition;
        }
        rawPlaybackHeadPosition += passthroughWorkaroundPauseOffset;
      }
      if (lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
        // The value must have wrapped around.
        rawPlaybackHeadWrapCount++;
      }
      lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
      return rawPlaybackHeadPosition + (rawPlaybackHeadWrapCount << 32);
    }

    /**
     * Returns {@link #getPlaybackHeadPosition()} expressed as microseconds.
     */
    public long getPlaybackHeadPositionUs() {
      return (getPlaybackHeadPosition() * C.MICROS_PER_SECOND) / sampleRate;
    }

    /**
     * Updates the values returned by {@link #getTimestampNanoTime()} and
     * {@link #getTimestampFramePosition()}.
     *
     * @return True if the timestamp values were updated. False otherwise.
     */
    public boolean updateTimestamp() {
      return false;
    }

    /**
     * Returns the {@link android.media.AudioTimestamp#nanoTime} obtained during the most recent
     * call to {@link #updateTimestamp()} that returned true.
     *
     * @return The nanoTime obtained during the most recent call to {@link #updateTimestamp()} that
     *     returned true.
     * @throws UnsupportedOperationException If the implementation does not support audio timestamp
     *     queries. {@link #updateTimestamp()} will always return false in this case.
     */
    public long getTimestampNanoTime() {
      // Should never be called if updateTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link android.media.AudioTimestamp#framePosition} obtained during the most
     * recent call to {@link #updateTimestamp()} that returned true. The value is adjusted so that
     * wrap around only occurs if the value exceeds {@link Long#MAX_VALUE} (which in practice will
     * never happen).
     *
     * @return The framePosition obtained during the most recent call to {@link #updateTimestamp()}
     *     that returned true.
     * @throws UnsupportedOperationException If the implementation does not support audio timestamp
     *     queries. {@link #updateTimestamp()} will always return false in this case.
     */
    public long getTimestampFramePosition() {
      // Should never be called if updateTimestamp() returned false.
      throw new UnsupportedOperationException();
    }

  }

  @TargetApi(19)
  private static class AudioTrackUtilV19 extends AudioTrackUtil {

    private final AudioTimestamp audioTimestamp;

    private long rawTimestampFramePositionWrapCount;
    private long lastRawTimestampFramePosition;
    private long lastTimestampFramePosition;

    public AudioTrackUtilV19() {
      audioTimestamp = new AudioTimestamp();
    }

    @Override
    public void reconfigure(android.media.AudioTrack audioTrack, boolean isPassthrough) {
      super.reconfigure(audioTrack, isPassthrough);
      rawTimestampFramePositionWrapCount = 0;
      lastRawTimestampFramePosition = 0;
      lastTimestampFramePosition = 0;
    }

    @Override
    public boolean updateTimestamp() {
      boolean updated = audioTrack.getTimestamp(audioTimestamp);
      if (updated) {
        long rawFramePosition = audioTimestamp.framePosition;
        if (lastRawTimestampFramePosition > rawFramePosition) {
          // The value must have wrapped around.
          rawTimestampFramePositionWrapCount++;
        }
        lastRawTimestampFramePosition = rawFramePosition;
        lastTimestampFramePosition = rawFramePosition + (rawTimestampFramePositionWrapCount << 32);
      }
      return updated;
    }

    @Override
    public long getTimestampNanoTime() {
      return audioTimestamp.nanoTime;
    }

    @Override
    public long getTimestampFramePosition() {
      return lastTimestampFramePosition;
    }

  }

}
