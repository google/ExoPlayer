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

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.ConditionVariable;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Plays audio data. The implementation delegates to an {@link AudioTrack} and handles playback
 * position smoothing, non-blocking writes and reconfiguration.
 * <p>
 * If tunneling mode is enabled, care must be taken that audio processors do not output buffers with
 * a different duration than their input, and buffer processors must produce output corresponding to
 * their last input immediately after that input is queued. This means that, for example, speed
 * adjustment is not possible while using tunneling.
 */
public final class DefaultAudioSink implements AudioSink {

  /**
   * Thrown when the audio track has provided a spurious timestamp, if {@link
   * #failOnSpuriousAudioTimestamp} is set.
   */
  public static final class InvalidAudioTrackTimestampException extends RuntimeException {

    /**
     * Creates a new invalid timestamp exception with the specified message.
     *
     * @param message The detail message for this exception.
     */
    private InvalidAudioTrackTimestampException(String message) {
      super(message);
    }

  }

  /**
   * Provides a chain of audio processors, which are used for any user-defined processing and
   * applying playback parameters (if supported). Because applying playback parameters can skip and
   * stretch/compress audio, the sink will query the chain for information on how to transform its
   * output position to map it onto a media position, via {@link #getMediaDuration(long)} and {@link
   * #getSkippedOutputFrameCount()}.
   */
  public interface AudioProcessorChain {

    /**
     * Returns the fixed chain of audio processors that will process audio. This method is called
     * once during initialization, but audio processors may change state to become active/inactive
     * during playback.
     */
    AudioProcessor[] getAudioProcessors();

    /**
     * @deprecated Use {@link #applyPlaybackSpeed(float)} and {@link
     *     #applySkipSilenceEnabled(boolean)} instead.
     */
    @Deprecated
    PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters);

    /**
     * Configures audio processors to apply the specified playback speed immediately, returning the
     * new playback speed, which may differ from the speed passed in. Only called when processors
     * have no input pending.
     *
     * @param playbackSpeed The playback speed to try to apply.
     * @return The playback speed that was actually applied.
     */
    float applyPlaybackSpeed(float playbackSpeed);

    /**
     * Configures audio processors to apply whether to skip silences immediately, returning the new
     * value. Only called when processors have no input pending.
     *
     * @param skipSilenceEnabled Whether silences should be skipped in the audio stream.
     * @return The new value.
     */
    boolean applySkipSilenceEnabled(boolean skipSilenceEnabled);

    /**
     * Scales the specified playout duration to take into account speedup due to audio processing,
     * returning an input media duration, in arbitrary units.
     */
    long getMediaDuration(long playoutDuration);

    /**
     * Returns the number of output audio frames skipped since the audio processors were last
     * flushed.
     */
    long getSkippedOutputFrameCount();
  }

  /**
   * The default audio processor chain, which applies a (possibly empty) chain of user-defined audio
   * processors followed by {@link SilenceSkippingAudioProcessor} and {@link SonicAudioProcessor}.
   */
  public static class DefaultAudioProcessorChain implements AudioProcessorChain {

    private final AudioProcessor[] audioProcessors;
    private final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;
    private final SonicAudioProcessor sonicAudioProcessor;

    /**
     * Creates a new default chain of audio processors, with the user-defined {@code
     * audioProcessors} applied before silence skipping and playback parameters.
     */
    public DefaultAudioProcessorChain(AudioProcessor... audioProcessors) {
      // The passed-in type may be more specialized than AudioProcessor[], so allocate a new array
      // rather than using Arrays.copyOf.
      this.audioProcessors = new AudioProcessor[audioProcessors.length + 2];
      System.arraycopy(
          /* src= */ audioProcessors,
          /* srcPos= */ 0,
          /* dest= */ this.audioProcessors,
          /* destPos= */ 0,
          /* length= */ audioProcessors.length);
      silenceSkippingAudioProcessor = new SilenceSkippingAudioProcessor();
      sonicAudioProcessor = new SonicAudioProcessor();
      this.audioProcessors[audioProcessors.length] = silenceSkippingAudioProcessor;
      this.audioProcessors[audioProcessors.length + 1] = sonicAudioProcessor;
    }

    @Override
    public AudioProcessor[] getAudioProcessors() {
      return audioProcessors;
    }

    /**
     * @deprecated Use {@link #applyPlaybackSpeed(float)} and {@link
     *     #applySkipSilenceEnabled(boolean)} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
      return new PlaybackParameters(applyPlaybackSpeed(playbackParameters.speed));
    }

    @Override
    public float applyPlaybackSpeed(float playbackSpeed) {
      return sonicAudioProcessor.setSpeed(playbackSpeed);
    }

    @Override
    public boolean applySkipSilenceEnabled(boolean skipSilenceEnabled) {
      silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled);
      return skipSilenceEnabled;
    }

    @Override
    public long getMediaDuration(long playoutDuration) {
      return sonicAudioProcessor.scaleDurationForSpeedup(playoutDuration);
    }

    @Override
    public long getSkippedOutputFrameCount() {
      return silenceSkippingAudioProcessor.getSkippedFrames();
    }
  }

  /**
   * A minimum length for the {@link AudioTrack} buffer, in microseconds.
   */
  private static final long MIN_BUFFER_DURATION_US = 250000;
  /**
   * A maximum length for the {@link AudioTrack} buffer, in microseconds.
   */
  private static final long MAX_BUFFER_DURATION_US = 750000;
  /**
   * The length for passthrough {@link AudioTrack} buffers, in microseconds.
   */
  private static final long PASSTHROUGH_BUFFER_DURATION_US = 250000;
  /**
   * A multiplication factor to apply to the minimum buffer size requested by the underlying
   * {@link AudioTrack}.
   */
  private static final int BUFFER_MULTIPLICATION_FACTOR = 4;

  /** To avoid underruns on some devices (e.g., Broadcom 7271), scale up the AC3 buffer duration. */
  private static final int AC3_BUFFER_MULTIPLICATION_FACTOR = 2;

  /**
   * @see AudioTrack#ERROR_BAD_VALUE
   */
  private static final int ERROR_BAD_VALUE = AudioTrack.ERROR_BAD_VALUE;
  /**
   * @see AudioTrack#MODE_STATIC
   */
  private static final int MODE_STATIC = AudioTrack.MODE_STATIC;
  /**
   * @see AudioTrack#MODE_STREAM
   */
  private static final int MODE_STREAM = AudioTrack.MODE_STREAM;
  /**
   * @see AudioTrack#STATE_INITIALIZED
   */
  private static final int STATE_INITIALIZED = AudioTrack.STATE_INITIALIZED;
  /**
   * @see AudioTrack#WRITE_NON_BLOCKING
   */
  @SuppressLint("InlinedApi")
  private static final int WRITE_NON_BLOCKING = AudioTrack.WRITE_NON_BLOCKING;
  /** The default playback speed. */
  private static final float DEFAULT_PLAYBACK_SPEED = 1.0f;
  /** The default skip silence flag. */
  private static final boolean DEFAULT_SKIP_SILENCE = false;

  private static final String TAG = "AudioTrack";

  /**
   * Whether to enable a workaround for an issue where an audio effect does not keep its session
   * active across releasing/initializing a new audio track, on platform builds where
   * {@link Util#SDK_INT} &lt; 21.
   * <p>
   * The flag must be set before creating a player.
   */
  public static boolean enablePreV21AudioSessionWorkaround = false;

  /**
   * Whether to throw an {@link InvalidAudioTrackTimestampException} when a spurious timestamp is
   * reported from {@link AudioTrack#getTimestamp}.
   * <p>
   * The flag must be set before creating a player. Should be set to {@code true} for testing and
   * debugging purposes only.
   */
  public static boolean failOnSpuriousAudioTimestamp = false;

  @Nullable private final AudioCapabilities audioCapabilities;
  private final AudioProcessorChain audioProcessorChain;
  private final boolean enableFloatOutput;
  private final ChannelMappingAudioProcessor channelMappingAudioProcessor;
  private final TrimmingAudioProcessor trimmingAudioProcessor;
  private final AudioProcessor[] toIntPcmAvailableAudioProcessors;
  private final AudioProcessor[] toFloatPcmAvailableAudioProcessors;
  private final ConditionVariable releasingConditionVariable;
  private final AudioTrackPositionTracker audioTrackPositionTracker;
  private final ArrayDeque<MediaPositionParameters> mediaPositionParametersCheckpoints;

  @Nullable private Listener listener;
  /** Used to keep the audio session active on pre-V21 builds (see {@link #initialize(long)}). */
  @Nullable private AudioTrack keepSessionIdAudioTrack;

  @Nullable private Configuration pendingConfiguration;
  private Configuration configuration;
  private AudioTrack audioTrack;

  private AudioAttributes audioAttributes;
  @Nullable private MediaPositionParameters afterDrainParameters;
  private MediaPositionParameters mediaPositionParameters;

  @Nullable private ByteBuffer avSyncHeader;
  private int bytesUntilNextAvSync;

  private long submittedPcmBytes;
  private long submittedEncodedFrames;
  private long writtenPcmBytes;
  private long writtenEncodedFrames;
  private int framesPerEncodedSample;
  private boolean startMediaTimeUsNeedsSync;
  private long startMediaTimeUs;
  private float volume;

  private AudioProcessor[] activeAudioProcessors;
  private ByteBuffer[] outputBuffers;
  @Nullable private ByteBuffer inputBuffer;
  private int inputBufferAccessUnitCount;
  @Nullable private ByteBuffer outputBuffer;
  private byte[] preV21OutputBuffer;
  private int preV21OutputBufferOffset;
  private int drainingAudioProcessorIndex;
  private boolean handledEndOfStream;
  private boolean stoppedAudioTrack;

  private boolean playing;
  private int audioSessionId;
  private AuxEffectInfo auxEffectInfo;
  private boolean tunneling;
  private long lastFeedElapsedRealtimeMs;

  /**
   * Creates a new default audio sink.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio before
   *     output. May be empty.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors) {
    this(audioCapabilities, audioProcessors, /* enableFloatOutput= */ false);
  }

  /**
   * Creates a new default audio sink, optionally using float output for high resolution PCM.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio before
   *     output. May be empty.
   * @param enableFloatOutput Whether to enable 32-bit float output. Where possible, 32-bit float
   *     output will be used if the input is 32-bit float, and also if the input is high resolution
   *     (24-bit or 32-bit) integer PCM. Audio processing (for example, speed adjustment) will not
   *     be available when float output is in use.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor[] audioProcessors,
      boolean enableFloatOutput) {
    this(audioCapabilities, new DefaultAudioProcessorChain(audioProcessors), enableFloatOutput);
  }

  /**
   * Creates a new default audio sink, optionally using float output for high resolution PCM and
   * with the specified {@code audioProcessorChain}.
   *
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessorChain An {@link AudioProcessorChain} which is used to apply playback
   *     parameters adjustments. The instance passed in must not be reused in other sinks.
   * @param enableFloatOutput Whether to enable 32-bit float output. Where possible, 32-bit float
   *     output will be used if the input is 32-bit float, and also if the input is high resolution
   *     (24-bit or 32-bit) integer PCM. Audio processing (for example, speed adjustment) will not
   *     be available when float output is in use.
   */
  public DefaultAudioSink(
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessorChain audioProcessorChain,
      boolean enableFloatOutput) {
    this.audioCapabilities = audioCapabilities;
    this.audioProcessorChain = Assertions.checkNotNull(audioProcessorChain);
    this.enableFloatOutput = enableFloatOutput;
    releasingConditionVariable = new ConditionVariable(true);
    audioTrackPositionTracker = new AudioTrackPositionTracker(new PositionTrackerListener());
    channelMappingAudioProcessor = new ChannelMappingAudioProcessor();
    trimmingAudioProcessor = new TrimmingAudioProcessor();
    ArrayList<AudioProcessor> toIntPcmAudioProcessors = new ArrayList<>();
    Collections.addAll(
        toIntPcmAudioProcessors,
        new ResamplingAudioProcessor(),
        channelMappingAudioProcessor,
        trimmingAudioProcessor);
    Collections.addAll(toIntPcmAudioProcessors, audioProcessorChain.getAudioProcessors());
    toIntPcmAvailableAudioProcessors = toIntPcmAudioProcessors.toArray(new AudioProcessor[0]);
    toFloatPcmAvailableAudioProcessors = new AudioProcessor[] {new FloatResamplingAudioProcessor()};
    volume = 1.0f;
    audioAttributes = AudioAttributes.DEFAULT;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    auxEffectInfo = new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f);
    mediaPositionParameters =
        new MediaPositionParameters(
            DEFAULT_PLAYBACK_SPEED,
            DEFAULT_SKIP_SILENCE,
            /* mediaTimeUs= */ 0,
            /* audioTrackPositionUs= */ 0);
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    activeAudioProcessors = new AudioProcessor[0];
    outputBuffers = new ByteBuffer[0];
    mediaPositionParametersCheckpoints = new ArrayDeque<>();
  }

  // AudioSink implementation.

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public boolean supportsOutput(int channelCount, @C.Encoding int encoding) {
    if (Util.isEncodingLinearPcm(encoding)) {
      // AudioTrack supports 16-bit integer PCM output in all platform API versions, and float
      // output from platform API version 21 only. Other integer PCM encodings are resampled by this
      // sink to 16-bit PCM. We assume that the audio framework will downsample any number of
      // channels to the output device's required number of channels.
      return encoding != C.ENCODING_PCM_FLOAT || Util.SDK_INT >= 21;
    } else {
      return audioCapabilities != null
          && audioCapabilities.supportsEncoding(encoding)
          && (channelCount == Format.NO_VALUE
              || channelCount <= audioCapabilities.getMaxChannelCount());
    }
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (!isInitialized()) {
      return CURRENT_POSITION_NOT_SET;
    }
    long positionUs = audioTrackPositionTracker.getCurrentPositionUs(sourceEnded);
    positionUs = Math.min(positionUs, configuration.framesToDurationUs(getWrittenFrames()));
    return applySkipping(applyMediaPositionParameters(positionUs));
  }

  @Override
  public void configure(
      @C.Encoding int inputEncoding,
      int inputChannelCount,
      int inputSampleRate,
      int specifiedBufferSize,
      @Nullable int[] outputChannels,
      int trimStartFrames,
      int trimEndFrames)
      throws ConfigurationException {
    if (Util.SDK_INT < 21 && inputChannelCount == 8 && outputChannels == null) {
      // AudioTrack doesn't support 8 channel output before Android L. Discard the last two (side)
      // channels to give a 6 channel stream that is supported.
      outputChannels = new int[6];
      for (int i = 0; i < outputChannels.length; i++) {
        outputChannels[i] = i;
      }
    }

    boolean isInputPcm = Util.isEncodingLinearPcm(inputEncoding);
    boolean processingEnabled = isInputPcm;
    int sampleRate = inputSampleRate;
    int channelCount = inputChannelCount;
    @C.Encoding int encoding = inputEncoding;
    boolean useFloatOutput =
        enableFloatOutput
            && supportsOutput(inputChannelCount, C.ENCODING_PCM_FLOAT)
            && Util.isEncodingHighResolutionPcm(inputEncoding);
    AudioProcessor[] availableAudioProcessors =
        useFloatOutput ? toFloatPcmAvailableAudioProcessors : toIntPcmAvailableAudioProcessors;
    if (processingEnabled) {
      trimmingAudioProcessor.setTrimFrameCount(trimStartFrames, trimEndFrames);
      channelMappingAudioProcessor.setChannelMap(outputChannels);
      AudioProcessor.AudioFormat outputFormat =
          new AudioProcessor.AudioFormat(sampleRate, channelCount, encoding);
      for (AudioProcessor audioProcessor : availableAudioProcessors) {
        try {
          AudioProcessor.AudioFormat nextFormat = audioProcessor.configure(outputFormat);
          if (audioProcessor.isActive()) {
            outputFormat = nextFormat;
          }
        } catch (UnhandledAudioFormatException e) {
          throw new ConfigurationException(e);
        }
      }
      sampleRate = outputFormat.sampleRate;
      channelCount = outputFormat.channelCount;
      encoding = outputFormat.encoding;
    }

    int outputChannelConfig = getChannelConfig(channelCount, isInputPcm);
    if (outputChannelConfig == AudioFormat.CHANNEL_INVALID) {
      throw new ConfigurationException("Unsupported channel count: " + channelCount);
    }

    int inputPcmFrameSize =
        isInputPcm ? Util.getPcmFrameSize(inputEncoding, inputChannelCount) : C.LENGTH_UNSET;
    int outputPcmFrameSize =
        isInputPcm ? Util.getPcmFrameSize(encoding, channelCount) : C.LENGTH_UNSET;
    boolean canApplyPlaybackParameters = processingEnabled && !useFloatOutput;
    Configuration pendingConfiguration =
        new Configuration(
            isInputPcm,
            inputPcmFrameSize,
            inputSampleRate,
            outputPcmFrameSize,
            sampleRate,
            outputChannelConfig,
            encoding,
            specifiedBufferSize,
            processingEnabled,
            canApplyPlaybackParameters,
            availableAudioProcessors);
    if (isInitialized()) {
      this.pendingConfiguration = pendingConfiguration;
    } else {
      configuration = pendingConfiguration;
    }
  }

  private void setupAudioProcessors() {
    AudioProcessor[] audioProcessors = configuration.availableAudioProcessors;
    ArrayList<AudioProcessor> newAudioProcessors = new ArrayList<>();
    for (AudioProcessor audioProcessor : audioProcessors) {
      if (audioProcessor.isActive()) {
        newAudioProcessors.add(audioProcessor);
      } else {
        audioProcessor.flush();
      }
    }
    int count = newAudioProcessors.size();
    activeAudioProcessors = newAudioProcessors.toArray(new AudioProcessor[count]);
    outputBuffers = new ByteBuffer[count];
    flushAudioProcessors();
  }

  private void flushAudioProcessors() {
    for (int i = 0; i < activeAudioProcessors.length; i++) {
      AudioProcessor audioProcessor = activeAudioProcessors[i];
      audioProcessor.flush();
      outputBuffers[i] = audioProcessor.getOutput();
    }
  }

  private void initialize(long presentationTimeUs) throws InitializationException {
    // If we're asynchronously releasing a previous audio track then we block until it has been
    // released. This guarantees that we cannot end up in a state where we have multiple audio
    // track instances. Without this guarantee it would be possible, in extreme cases, to exhaust
    // the shared memory that's available for audio track buffers. This would in turn cause the
    // initialization of the audio track to fail.
    releasingConditionVariable.block();

    audioTrack =
        Assertions.checkNotNull(configuration)
            .buildAudioTrack(tunneling, audioAttributes, audioSessionId);
    int audioSessionId = audioTrack.getAudioSessionId();
    if (enablePreV21AudioSessionWorkaround) {
      if (Util.SDK_INT < 21) {
        // The workaround creates an audio track with a two byte buffer on the same session, and
        // does not release it until this object is released, which keeps the session active.
        if (keepSessionIdAudioTrack != null
            && audioSessionId != keepSessionIdAudioTrack.getAudioSessionId()) {
          releaseKeepSessionIdAudioTrack();
        }
        if (keepSessionIdAudioTrack == null) {
          keepSessionIdAudioTrack = initializeKeepSessionIdAudioTrack(audioSessionId);
        }
      }
    }
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      if (listener != null) {
        listener.onAudioSessionId(audioSessionId);
      }
    }

    startMediaTimeUs = Math.max(0, presentationTimeUs);
    startMediaTimeUsNeedsSync = false;

    applyPlaybackSpeedAndSkipSilence(presentationTimeUs);

    audioTrackPositionTracker.setAudioTrack(
        audioTrack,
        configuration.outputEncoding,
        configuration.outputPcmFrameSize,
        configuration.bufferSize);
    setVolumeInternal();

    if (auxEffectInfo.effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
      audioTrack.attachAuxEffect(auxEffectInfo.effectId);
      audioTrack.setAuxEffectSendLevel(auxEffectInfo.sendLevel);
    }
  }

  @Override
  public void play() {
    playing = true;
    if (isInitialized()) {
      audioTrackPositionTracker.start();
      audioTrack.play();
    }
  }

  @Override
  public void handleDiscontinuity() {
    // Force resynchronization after a skipped buffer.
    startMediaTimeUsNeedsSync = true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException {
    Assertions.checkArgument(inputBuffer == null || buffer == inputBuffer);

    if (pendingConfiguration != null) {
      if (!drainToEndOfStream()) {
        // There's still pending data in audio processors to write to the track.
        return false;
      } else if (!pendingConfiguration.canReuseAudioTrack(configuration)) {
        playPendingData();
        if (hasPendingData()) {
          // We're waiting for playout on the current audio track to finish.
          return false;
        }
        flush();
      } else {
        // The current audio track can be reused for the new configuration.
        configuration = pendingConfiguration;
        pendingConfiguration = null;
      }
      // Re-apply playback parameters.
      applyPlaybackSpeedAndSkipSilence(presentationTimeUs);
    }

    if (!isInitialized()) {
      initialize(presentationTimeUs);
      if (playing) {
        play();
      }
    }

    if (!audioTrackPositionTracker.mayHandleBuffer(getWrittenFrames())) {
      return false;
    }

    if (inputBuffer == null) {
      // We are seeing this buffer for the first time.
      if (!buffer.hasRemaining()) {
        // The buffer is empty.
        return true;
      }

      if (!configuration.isInputPcm && framesPerEncodedSample == 0) {
        // If this is the first encoded sample, calculate the sample size in frames.
        framesPerEncodedSample = getFramesPerEncodedSample(configuration.outputEncoding, buffer);
        if (framesPerEncodedSample == 0) {
          // We still don't know the number of frames per sample, so drop the buffer.
          // For TrueHD this can occur after some seek operations, as not every sample starts with
          // a syncframe header. If we chunked samples together so the extracted samples always
          // started with a syncframe header, the chunks would be too large.
          return true;
        }
      }

      if (afterDrainParameters != null) {
        if (!drainToEndOfStream()) {
          // Don't process any more input until draining completes.
          return false;
        }
        applyPlaybackSpeedAndSkipSilence(presentationTimeUs);
        afterDrainParameters = null;
      }

      // Sanity check that presentationTimeUs is consistent with the expected value.
      long expectedPresentationTimeUs =
          startMediaTimeUs
              + configuration.inputFramesToDurationUs(
                  getSubmittedFrames() - trimmingAudioProcessor.getTrimmedFrameCount());
      if (!startMediaTimeUsNeedsSync
          && Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000) {
        Log.e(
            TAG,
            "Discontinuity detected [expected "
                + expectedPresentationTimeUs
                + ", got "
                + presentationTimeUs
                + "]");
        startMediaTimeUsNeedsSync = true;
      }
      if (startMediaTimeUsNeedsSync) {
        if (!drainToEndOfStream()) {
          // Don't update timing until pending AudioProcessor buffers are completely drained.
          return false;
        }
        // Adjust startMediaTimeUs to be consistent with the current buffer's start time and the
        // number of bytes submitted.
        long adjustmentUs = presentationTimeUs - expectedPresentationTimeUs;
        startMediaTimeUs += adjustmentUs;
        startMediaTimeUsNeedsSync = false;
        // Re-apply playback parameters because the startMediaTimeUs changed.
        applyPlaybackSpeedAndSkipSilence(presentationTimeUs);
        if (listener != null && adjustmentUs != 0) {
          listener.onPositionDiscontinuity();
        }
      }

      if (configuration.isInputPcm) {
        submittedPcmBytes += buffer.remaining();
      } else {
        submittedEncodedFrames += framesPerEncodedSample * encodedAccessUnitCount;
      }

      inputBuffer = buffer;
      inputBufferAccessUnitCount = encodedAccessUnitCount;
    }

    processBuffers(presentationTimeUs);

    if (!inputBuffer.hasRemaining()) {
      inputBuffer = null;
      inputBufferAccessUnitCount = 0;
      return true;
    }

    if (audioTrackPositionTracker.isStalled(getWrittenFrames())) {
      Log.w(TAG, "Resetting stalled audio track");
      flush();
      return true;
    }

    return false;
  }

  private void processBuffers(long avSyncPresentationTimeUs) throws WriteException {
    int count = activeAudioProcessors.length;
    int index = count;
    while (index >= 0) {
      ByteBuffer input = index > 0 ? outputBuffers[index - 1]
          : (inputBuffer != null ? inputBuffer : AudioProcessor.EMPTY_BUFFER);
      if (index == count) {
        writeBuffer(input, avSyncPresentationTimeUs);
      } else {
        AudioProcessor audioProcessor = activeAudioProcessors[index];
        audioProcessor.queueInput(input);
        ByteBuffer output = audioProcessor.getOutput();
        outputBuffers[index] = output;
        if (output.hasRemaining()) {
          // Handle the output as input to the next audio processor or the AudioTrack.
          index++;
          continue;
        }
      }

      if (input.hasRemaining()) {
        // The input wasn't consumed and no output was produced, so give up for now.
        return;
      }

      // Get more input from upstream.
      index--;
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void writeBuffer(ByteBuffer buffer, long avSyncPresentationTimeUs) throws WriteException {
    if (!buffer.hasRemaining()) {
      return;
    }
    if (outputBuffer != null) {
      Assertions.checkArgument(outputBuffer == buffer);
    } else {
      outputBuffer = buffer;
      if (Util.SDK_INT < 21) {
        int bytesRemaining = buffer.remaining();
        if (preV21OutputBuffer == null || preV21OutputBuffer.length < bytesRemaining) {
          preV21OutputBuffer = new byte[bytesRemaining];
        }
        int originalPosition = buffer.position();
        buffer.get(preV21OutputBuffer, 0, bytesRemaining);
        buffer.position(originalPosition);
        preV21OutputBufferOffset = 0;
      }
    }
    int bytesRemaining = buffer.remaining();
    int bytesWritten = 0;
    if (Util.SDK_INT < 21) { // isInputPcm == true
      // Work out how many bytes we can write without the risk of blocking.
      int bytesToWrite = audioTrackPositionTracker.getAvailableBufferSize(writtenPcmBytes);
      if (bytesToWrite > 0) {
        bytesToWrite = Math.min(bytesRemaining, bytesToWrite);
        bytesWritten = audioTrack.write(preV21OutputBuffer, preV21OutputBufferOffset, bytesToWrite);
        if (bytesWritten > 0) {
          preV21OutputBufferOffset += bytesWritten;
          buffer.position(buffer.position() + bytesWritten);
        }
      }
    } else if (tunneling) {
      Assertions.checkState(avSyncPresentationTimeUs != C.TIME_UNSET);
      bytesWritten = writeNonBlockingWithAvSyncV21(audioTrack, buffer, bytesRemaining,
          avSyncPresentationTimeUs);
    } else {
      bytesWritten = writeNonBlockingV21(audioTrack, buffer, bytesRemaining);
    }

    lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();

    if (bytesWritten < 0) {
      throw new WriteException(bytesWritten);
    }

    if (configuration.isInputPcm) {
      writtenPcmBytes += bytesWritten;
    }
    if (bytesWritten == bytesRemaining) {
      if (!configuration.isInputPcm) {
        // When playing non-PCM, the inputBuffer is never processed, thus the last inputBuffer
        // must be the current input buffer.
        Assertions.checkState(buffer == inputBuffer);
        writtenEncodedFrames += framesPerEncodedSample * inputBufferAccessUnitCount;
      }
      outputBuffer = null;
    }
  }

  @Override
  public void playToEndOfStream() throws WriteException {
    if (!handledEndOfStream && isInitialized() && drainToEndOfStream()) {
      playPendingData();
      handledEndOfStream = true;
    }
  }

  private boolean drainToEndOfStream() throws WriteException {
    boolean audioProcessorNeedsEndOfStream = false;
    if (drainingAudioProcessorIndex == C.INDEX_UNSET) {
      drainingAudioProcessorIndex =
          configuration.processingEnabled ? 0 : activeAudioProcessors.length;
      audioProcessorNeedsEndOfStream = true;
    }
    while (drainingAudioProcessorIndex < activeAudioProcessors.length) {
      AudioProcessor audioProcessor = activeAudioProcessors[drainingAudioProcessorIndex];
      if (audioProcessorNeedsEndOfStream) {
        audioProcessor.queueEndOfStream();
      }
      processBuffers(C.TIME_UNSET);
      if (!audioProcessor.isEnded()) {
        return false;
      }
      audioProcessorNeedsEndOfStream = true;
      drainingAudioProcessorIndex++;
    }

    // Finish writing any remaining output to the track.
    if (outputBuffer != null) {
      writeBuffer(outputBuffer, C.TIME_UNSET);
      if (outputBuffer != null) {
        return false;
      }
    }
    drainingAudioProcessorIndex = C.INDEX_UNSET;
    return true;
  }

  @Override
  public boolean isEnded() {
    return !isInitialized() || (handledEndOfStream && !hasPendingData());
  }

  @Override
  public boolean hasPendingData() {
    return isInitialized() && audioTrackPositionTracker.hasPendingData(getWrittenFrames());
  }

  /**
   * @deprecated Use {@link #setPlaybackSpeed(float)} and {@link #setSkipSilenceEnabled(boolean)}
   *     instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    setPlaybackSpeedAndSkipSilence(playbackParameters.speed, getSkipSilenceEnabled());
  }

  /** @deprecated Use {@link #getPlaybackSpeed()} and {@link #getSkipSilenceEnabled()} instead. */
  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public PlaybackParameters getPlaybackParameters() {
    MediaPositionParameters mediaPositionParameters = getMediaPositionParameters();
    return new PlaybackParameters(mediaPositionParameters.playbackSpeed);
  }

  @Override
  public void setPlaybackSpeed(float playbackSpeed) {
    setPlaybackSpeedAndSkipSilence(playbackSpeed, getSkipSilenceEnabled());
  }

  @Override
  public float getPlaybackSpeed() {
    return getMediaPositionParameters().playbackSpeed;
  }

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
    setPlaybackSpeedAndSkipSilence(getPlaybackSpeed(), skipSilenceEnabled);
  }

  @Override
  public boolean getSkipSilenceEnabled() {
    return getMediaPositionParameters().skipSilence;
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    if (this.audioAttributes.equals(audioAttributes)) {
      return;
    }
    this.audioAttributes = audioAttributes;
    if (tunneling) {
      // The audio attributes are ignored in tunneling mode, so no need to reset.
      return;
    }
    flush();
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {
    if (this.audioSessionId != audioSessionId) {
      this.audioSessionId = audioSessionId;
      flush();
    }
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    if (this.auxEffectInfo.equals(auxEffectInfo)) {
      return;
    }
    int effectId = auxEffectInfo.effectId;
    float sendLevel = auxEffectInfo.sendLevel;
    if (audioTrack != null) {
      if (this.auxEffectInfo.effectId != effectId) {
        audioTrack.attachAuxEffect(effectId);
      }
      if (effectId != AuxEffectInfo.NO_AUX_EFFECT_ID) {
        audioTrack.setAuxEffectSendLevel(sendLevel);
      }
    }
    this.auxEffectInfo = auxEffectInfo;
  }

  @Override
  public void enableTunnelingV21(int tunnelingAudioSessionId) {
    Assertions.checkState(Util.SDK_INT >= 21);
    if (!tunneling || audioSessionId != tunnelingAudioSessionId) {
      tunneling = true;
      audioSessionId = tunnelingAudioSessionId;
      flush();
    }
  }

  @Override
  public void disableTunneling() {
    if (tunneling) {
      tunneling = false;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
      flush();
    }
  }

  @Override
  public void setVolume(float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      setVolumeInternal();
    }
  }

  private void setVolumeInternal() {
    if (!isInitialized()) {
      // Do nothing.
    } else if (Util.SDK_INT >= 21) {
      setVolumeInternalV21(audioTrack, volume);
    } else {
      setVolumeInternalV3(audioTrack, volume);
    }
  }

  @Override
  public void pause() {
    playing = false;
    if (isInitialized() && audioTrackPositionTracker.pause()) {
      audioTrack.pause();
    }
  }

  @Override
  public void flush() {
    if (isInitialized()) {
      submittedPcmBytes = 0;
      submittedEncodedFrames = 0;
      writtenPcmBytes = 0;
      writtenEncodedFrames = 0;
      framesPerEncodedSample = 0;
      mediaPositionParameters =
          new MediaPositionParameters(
              getPlaybackSpeed(),
              getSkipSilenceEnabled(),
              /* mediaTimeUs= */ 0,
              /* audioTrackPositionUs= */ 0);
      startMediaTimeUs = 0;
      afterDrainParameters = null;
      mediaPositionParametersCheckpoints.clear();
      trimmingAudioProcessor.resetTrimmedFrameCount();
      flushAudioProcessors();
      inputBuffer = null;
      inputBufferAccessUnitCount = 0;
      outputBuffer = null;
      stoppedAudioTrack = false;
      handledEndOfStream = false;
      drainingAudioProcessorIndex = C.INDEX_UNSET;
      avSyncHeader = null;
      bytesUntilNextAvSync = 0;
      if (audioTrackPositionTracker.isPlaying()) {
        audioTrack.pause();
      }
      // AudioTrack.release can take some time, so we call it on a background thread.
      final AudioTrack toRelease = audioTrack;
      audioTrack = null;
      if (pendingConfiguration != null) {
        configuration = pendingConfiguration;
        pendingConfiguration = null;
      }
      audioTrackPositionTracker.reset();
      releasingConditionVariable.close();
      new Thread("ExoPlayer:AudioTrackReleaseThread") {
        @Override
        public void run() {
          try {
            toRelease.flush();
            toRelease.release();
          } finally {
            releasingConditionVariable.open();
          }
        }
      }.start();
    }
  }

  @Override
  public void reset() {
    flush();
    releaseKeepSessionIdAudioTrack();
    for (AudioProcessor audioProcessor : toIntPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    for (AudioProcessor audioProcessor : toFloatPcmAvailableAudioProcessors) {
      audioProcessor.reset();
    }
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    playing = false;
  }

  // Internal methods.

  /** Releases {@link #keepSessionIdAudioTrack} asynchronously, if it is non-{@code null}. */
  private void releaseKeepSessionIdAudioTrack() {
    if (keepSessionIdAudioTrack == null) {
      return;
    }

    // AudioTrack.release can take some time, so we call it on a background thread.
    final AudioTrack toRelease = keepSessionIdAudioTrack;
    keepSessionIdAudioTrack = null;
    new Thread() {
      @Override
      public void run() {
        toRelease.release();
      }
    }.start();
  }

  private void setPlaybackSpeedAndSkipSilence(float playbackSpeed, boolean skipSilence) {
    MediaPositionParameters currentMediaPositionParameters = getMediaPositionParameters();
    if (playbackSpeed != currentMediaPositionParameters.playbackSpeed
        || skipSilence != currentMediaPositionParameters.skipSilence) {
      MediaPositionParameters mediaPositionParameters =
          new MediaPositionParameters(
              playbackSpeed,
              skipSilence,
              /* mediaTimeUs= */ C.TIME_UNSET,
              /* audioTrackPositionUs= */ C.TIME_UNSET);
      if (isInitialized()) {
        // Drain the audio processors so we can determine the frame position at which the new
        // parameters apply.
        this.afterDrainParameters = mediaPositionParameters;
      } else {
        // Update the audio processor chain parameters now. They will be applied to the audio
        // processors during initialization.
        this.mediaPositionParameters = mediaPositionParameters;
      }
    }
  }

  private MediaPositionParameters getMediaPositionParameters() {
    // Mask the already set parameters.
    return afterDrainParameters != null
        ? afterDrainParameters
        : !mediaPositionParametersCheckpoints.isEmpty()
            ? mediaPositionParametersCheckpoints.getLast()
            : mediaPositionParameters;
  }

  private void applyPlaybackSpeedAndSkipSilence(long presentationTimeUs) {
    float playbackSpeed =
        configuration.canApplyPlaybackParameters
            ? audioProcessorChain.applyPlaybackSpeed(getPlaybackSpeed())
            : DEFAULT_PLAYBACK_SPEED;
    boolean skipSilenceEnabled =
        configuration.canApplyPlaybackParameters
            ? audioProcessorChain.applySkipSilenceEnabled(getSkipSilenceEnabled())
            : DEFAULT_SKIP_SILENCE;
    mediaPositionParametersCheckpoints.add(
        new MediaPositionParameters(
            playbackSpeed,
            skipSilenceEnabled,
            /* mediaTimeUs= */ Math.max(0, presentationTimeUs),
            /* audioTrackPositionUs= */ configuration.framesToDurationUs(getWrittenFrames())));
    setupAudioProcessors();
    if (listener != null) {
      listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
    }
  }

  /**
   * Applies and updates media position parameters.
   *
   * @param positionUs The current audio track position, in microseconds.
   * @return The current media time, in microseconds.
   */
  private long applyMediaPositionParameters(long positionUs) {
    while (!mediaPositionParametersCheckpoints.isEmpty()
        && positionUs >= mediaPositionParametersCheckpoints.getFirst().audioTrackPositionUs) {
      // We are playing (or about to play) media with the new parameters, so update them.
      mediaPositionParameters = mediaPositionParametersCheckpoints.remove();
    }

    long playoutDurationSinceLastCheckpoint =
        positionUs - mediaPositionParameters.audioTrackPositionUs;
    if (mediaPositionParameters.playbackSpeed != 1f) {
      if (mediaPositionParametersCheckpoints.isEmpty()) {
        playoutDurationSinceLastCheckpoint =
            audioProcessorChain.getMediaDuration(playoutDurationSinceLastCheckpoint);
      } else {
        // Playing data at a previous playback speed, so fall back to multiplying by the speed.
        playoutDurationSinceLastCheckpoint =
            Util.getMediaDurationForPlayoutDuration(
                playoutDurationSinceLastCheckpoint, mediaPositionParameters.playbackSpeed);
      }
    }
    return mediaPositionParameters.mediaTimeUs + playoutDurationSinceLastCheckpoint;
  }

  private long applySkipping(long positionUs) {
    return positionUs
        + configuration.framesToDurationUs(audioProcessorChain.getSkippedOutputFrameCount());
  }

  private boolean isInitialized() {
    return audioTrack != null;
  }

  private long getSubmittedFrames() {
    return configuration.isInputPcm
        ? (submittedPcmBytes / configuration.inputPcmFrameSize)
        : submittedEncodedFrames;
  }

  private long getWrittenFrames() {
    return configuration.isInputPcm
        ? (writtenPcmBytes / configuration.outputPcmFrameSize)
        : writtenEncodedFrames;
  }

  private static AudioTrack initializeKeepSessionIdAudioTrack(int audioSessionId) {
    int sampleRate = 4000; // Equal to private AudioTrack.MIN_SAMPLE_RATE.
    int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
    @C.PcmEncoding int encoding = C.ENCODING_PCM_16BIT;
    int bufferSize = 2; // Use a two byte buffer, as it is not actually used for playback.
    return new AudioTrack(C.STREAM_TYPE_DEFAULT, sampleRate, channelConfig, encoding, bufferSize,
        MODE_STATIC, audioSessionId);
  }

  private static int getChannelConfig(int channelCount, boolean isInputPcm) {
    if (Util.SDK_INT <= 28 && !isInputPcm) {
      // In passthrough mode the channel count used to configure the audio track doesn't affect how
      // the stream is handled, except that some devices do overly-strict channel configuration
      // checks. Therefore we override the channel count so that a known-working channel
      // configuration is chosen in all cases. See [Internal: b/29116190].
      if (channelCount == 7) {
        channelCount = 8;
      } else if (channelCount == 3 || channelCount == 4 || channelCount == 5) {
        channelCount = 6;
      }
    }

    // Workaround for Nexus Player not reporting support for mono passthrough.
    // (See [Internal: b/34268671].)
    if (Util.SDK_INT <= 26 && "fugu".equals(Util.DEVICE) && !isInputPcm && channelCount == 1) {
      channelCount = 2;
    }

    return Util.getAudioTrackChannelConfig(channelCount);
  }

  private static int getMaximumEncodedRateBytesPerSecond(@C.Encoding int encoding) {
    switch (encoding) {
      case C.ENCODING_MP3:
        return MpegAudioUtil.MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_LC:
        return AacUtil.AAC_LC_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_HE_V1:
        return AacUtil.AAC_HE_V1_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_HE_V2:
        return AacUtil.AAC_HE_V2_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_XHE:
        return AacUtil.AAC_XHE_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_ELD:
        return AacUtil.AAC_ELD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AC3:
        return Ac3Util.AC3_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_E_AC3:
      case C.ENCODING_E_AC3_JOC:
        return Ac3Util.E_AC3_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AC4:
        return Ac4Util.MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DTS:
        return DtsUtil.DTS_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DTS_HD:
        return DtsUtil.DTS_HD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DOLBY_TRUEHD:
        return Ac3Util.TRUEHD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }

  private static int getFramesPerEncodedSample(@C.Encoding int encoding, ByteBuffer buffer) {
    switch (encoding) {
      case C.ENCODING_MP3:
        int headerDataInBigEndian = Util.getBigEndianInt(buffer, buffer.position());
        return MpegAudioUtil.parseMpegAudioFrameSampleCount(headerDataInBigEndian);
      case C.ENCODING_AAC_LC:
        return AacUtil.AAC_LC_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_HE_V1:
      case C.ENCODING_AAC_HE_V2:
        return AacUtil.AAC_HE_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_XHE:
        return AacUtil.AAC_XHE_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_AAC_ELD:
        return AacUtil.AAC_LD_AUDIO_SAMPLE_COUNT;
      case C.ENCODING_DTS:
      case C.ENCODING_DTS_HD:
        return DtsUtil.parseDtsAudioSampleCount(buffer);
      case C.ENCODING_AC3:
      case C.ENCODING_E_AC3:
      case C.ENCODING_E_AC3_JOC:
        return Ac3Util.parseAc3SyncframeAudioSampleCount(buffer);
      case C.ENCODING_AC4:
        return Ac4Util.parseAc4SyncframeAudioSampleCount(buffer);
      case C.ENCODING_DOLBY_TRUEHD:
        int syncframeOffset = Ac3Util.findTrueHdSyncframeOffset(buffer);
        return syncframeOffset == C.INDEX_UNSET
            ? 0
            : (Ac3Util.parseTrueHdSyncframeAudioSampleCount(buffer, syncframeOffset)
                * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT);
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalStateException("Unexpected audio encoding: " + encoding);
    }
  }

  @RequiresApi(21)
  private static int writeNonBlockingV21(AudioTrack audioTrack, ByteBuffer buffer, int size) {
    return audioTrack.write(buffer, size, WRITE_NON_BLOCKING);
  }

  @RequiresApi(21)
  private int writeNonBlockingWithAvSyncV21(
      AudioTrack audioTrack, ByteBuffer buffer, int size, long presentationTimeUs) {
    if (Util.SDK_INT >= 26) {
      // The underlying platform AudioTrack writes AV sync headers directly.
      return audioTrack.write(buffer, size, WRITE_NON_BLOCKING, presentationTimeUs * 1000);
    }
    if (avSyncHeader == null) {
      avSyncHeader = ByteBuffer.allocate(16);
      avSyncHeader.order(ByteOrder.BIG_ENDIAN);
      avSyncHeader.putInt(0x55550001);
    }
    if (bytesUntilNextAvSync == 0) {
      avSyncHeader.putInt(4, size);
      avSyncHeader.putLong(8, presentationTimeUs * 1000);
      avSyncHeader.position(0);
      bytesUntilNextAvSync = size;
    }
    int avSyncHeaderBytesRemaining = avSyncHeader.remaining();
    if (avSyncHeaderBytesRemaining > 0) {
      int result = audioTrack.write(avSyncHeader, avSyncHeaderBytesRemaining, WRITE_NON_BLOCKING);
      if (result < 0) {
        bytesUntilNextAvSync = 0;
        return result;
      }
      if (result < avSyncHeaderBytesRemaining) {
        return 0;
      }
    }
    int result = writeNonBlockingV21(audioTrack, buffer, size);
    if (result < 0) {
      bytesUntilNextAvSync = 0;
      return result;
    }
    bytesUntilNextAvSync -= result;
    return result;
  }

  @RequiresApi(21)
  private static void setVolumeInternalV21(AudioTrack audioTrack, float volume) {
    audioTrack.setVolume(volume);
  }

  private static void setVolumeInternalV3(AudioTrack audioTrack, float volume) {
    audioTrack.setStereoVolume(volume, volume);
  }

  private void playPendingData() {
    if (!stoppedAudioTrack) {
      stoppedAudioTrack = true;
      audioTrackPositionTracker.handleEndOfStream(getWrittenFrames());
      audioTrack.stop();
      bytesUntilNextAvSync = 0;
    }
  }

  /** Stores parameters used to calculate the current media position. */
  private static final class MediaPositionParameters {

    /** The playback speed. */
    public final float playbackSpeed;
    /** Whether to skip silences. */
    public final boolean skipSilence;
    /** The media time from which the playback parameters apply, in microseconds. */
    public final long mediaTimeUs;
    /** The audio track position from which the playback parameters apply, in microseconds. */
    public final long audioTrackPositionUs;

    private MediaPositionParameters(
        float playbackSpeed, boolean skipSilence, long mediaTimeUs, long audioTrackPositionUs) {
      this.playbackSpeed = playbackSpeed;
      this.skipSilence = skipSilence;
      this.mediaTimeUs = mediaTimeUs;
      this.audioTrackPositionUs = audioTrackPositionUs;
    }
  }

  private final class PositionTrackerListener implements AudioTrackPositionTracker.Listener {

    @Override
    public void onPositionFramesMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (frame position mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      Log.w(TAG, message);
    }

    @Override
    public void onSystemTimeUsMismatch(
        long audioTimestampPositionFrames,
        long audioTimestampSystemTimeUs,
        long systemTimeUs,
        long playbackPositionUs) {
      String message =
          "Spurious audio timestamp (system clock mismatch): "
              + audioTimestampPositionFrames
              + ", "
              + audioTimestampSystemTimeUs
              + ", "
              + systemTimeUs
              + ", "
              + playbackPositionUs
              + ", "
              + getSubmittedFrames()
              + ", "
              + getWrittenFrames();
      if (failOnSpuriousAudioTimestamp) {
        throw new InvalidAudioTrackTimestampException(message);
      }
      Log.w(TAG, message);
    }

    @Override
    public void onInvalidLatency(long latencyUs) {
      Log.w(TAG, "Ignoring impossibly large audio latency: " + latencyUs);
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs) {
      if (listener != null) {
        long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - lastFeedElapsedRealtimeMs;
        listener.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }
  }

  /** Stores configuration relating to the audio format. */
  private static final class Configuration {

    public final boolean isInputPcm;
    public final int inputPcmFrameSize;
    public final int inputSampleRate;
    public final int outputPcmFrameSize;
    public final int outputSampleRate;
    public final int outputChannelConfig;
    @C.Encoding public final int outputEncoding;
    public final int bufferSize;
    public final boolean processingEnabled;
    public final boolean canApplyPlaybackParameters;
    public final AudioProcessor[] availableAudioProcessors;

    public Configuration(
        boolean isInputPcm,
        int inputPcmFrameSize,
        int inputSampleRate,
        int outputPcmFrameSize,
        int outputSampleRate,
        int outputChannelConfig,
        int outputEncoding,
        int specifiedBufferSize,
        boolean processingEnabled,
        boolean canApplyPlaybackParameters,
        AudioProcessor[] availableAudioProcessors) {
      this.isInputPcm = isInputPcm;
      this.inputPcmFrameSize = inputPcmFrameSize;
      this.inputSampleRate = inputSampleRate;
      this.outputPcmFrameSize = outputPcmFrameSize;
      this.outputSampleRate = outputSampleRate;
      this.outputChannelConfig = outputChannelConfig;
      this.outputEncoding = outputEncoding;
      this.bufferSize = specifiedBufferSize != 0 ? specifiedBufferSize : getDefaultBufferSize();
      this.processingEnabled = processingEnabled;
      this.canApplyPlaybackParameters = canApplyPlaybackParameters;
      this.availableAudioProcessors = availableAudioProcessors;
    }

    public boolean canReuseAudioTrack(Configuration audioTrackConfiguration) {
      return audioTrackConfiguration.outputEncoding == outputEncoding
          && audioTrackConfiguration.outputSampleRate == outputSampleRate
          && audioTrackConfiguration.outputChannelConfig == outputChannelConfig;
    }

    public long inputFramesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / inputSampleRate;
    }

    public long framesToDurationUs(long frameCount) {
      return (frameCount * C.MICROS_PER_SECOND) / outputSampleRate;
    }

    public long durationUsToFrames(long durationUs) {
      return (durationUs * outputSampleRate) / C.MICROS_PER_SECOND;
    }

    public AudioTrack buildAudioTrack(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId)
        throws InitializationException {
      AudioTrack audioTrack;
      if (Util.SDK_INT >= 21) {
        audioTrack = createAudioTrackV21(tunneling, audioAttributes, audioSessionId);
      } else {
        int streamType = Util.getStreamTypeForAudioUsage(audioAttributes.usage);
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
          audioTrack =
              new AudioTrack(
                  streamType,
                  outputSampleRate,
                  outputChannelConfig,
                  outputEncoding,
                  bufferSize,
                  MODE_STREAM);
        } else {
          // Re-attach to the same audio session.
          audioTrack =
              new AudioTrack(
                  streamType,
                  outputSampleRate,
                  outputChannelConfig,
                  outputEncoding,
                  bufferSize,
                  MODE_STREAM,
                  audioSessionId);
        }
      }

      int state = audioTrack.getState();
      if (state != STATE_INITIALIZED) {
        try {
          audioTrack.release();
        } catch (Exception e) {
          // The track has already failed to initialize, so it wouldn't be that surprising if
          // release were to fail too. Swallow the exception.
        }
        throw new InitializationException(state, outputSampleRate, outputChannelConfig, bufferSize);
      }
      return audioTrack;
    }

    @RequiresApi(21)
    private AudioTrack createAudioTrackV21(
        boolean tunneling, AudioAttributes audioAttributes, int audioSessionId) {
      android.media.AudioAttributes attributes;
      if (tunneling) {
        attributes =
            new android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build();
      } else {
        attributes = audioAttributes.getAudioAttributesV21();
      }
      AudioFormat format =
          new AudioFormat.Builder()
              .setChannelMask(outputChannelConfig)
              .setEncoding(outputEncoding)
              .setSampleRate(outputSampleRate)
              .build();
      return new AudioTrack(
          attributes,
          format,
          bufferSize,
          MODE_STREAM,
          audioSessionId != C.AUDIO_SESSION_ID_UNSET
              ? audioSessionId
              : AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    private int getDefaultBufferSize() {
      if (isInputPcm) {
        int minBufferSize =
            AudioTrack.getMinBufferSize(outputSampleRate, outputChannelConfig, outputEncoding);
        Assertions.checkState(minBufferSize != ERROR_BAD_VALUE);
        int multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR;
        int minAppBufferSize =
            (int) durationUsToFrames(MIN_BUFFER_DURATION_US) * outputPcmFrameSize;
        int maxAppBufferSize =
            (int)
                Math.max(
                    minBufferSize, durationUsToFrames(MAX_BUFFER_DURATION_US) * outputPcmFrameSize);
        return Util.constrainValue(multipliedBufferSize, minAppBufferSize, maxAppBufferSize);
      } else {
        int rate = getMaximumEncodedRateBytesPerSecond(outputEncoding);
        if (outputEncoding == C.ENCODING_AC3) {
          rate *= AC3_BUFFER_MULTIPLICATION_FACTOR;
        }
        return (int) (PASSTHROUGH_BUFFER_DURATION_US * rate / C.MICROS_PER_SECOND);
      }
    }
  }
}
