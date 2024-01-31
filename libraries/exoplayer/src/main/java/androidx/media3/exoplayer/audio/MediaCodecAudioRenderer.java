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
package androidx.media3.exoplayer.audio;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.CallSuper;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DecoderReuseEvaluation.DecoderDiscardReasons;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.MediaClock;
import androidx.media3.exoplayer.PlayerMessage.Target;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.audio.AudioRendererEventListener.EventDispatcher;
import androidx.media3.exoplayer.audio.AudioSink.InitializationException;
import androidx.media3.exoplayer.audio.AudioSink.WriteException;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import androidx.media3.extractor.VorbisUtil;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/**
 * Decodes and renders audio using {@link MediaCodec} and an {@link AudioSink}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VOLUME} to set the volume. The message payload should be
 *       a {@link Float} with 0 being silence and 1 being unity gain.
 *   <li>Message with type {@link #MSG_SET_AUDIO_ATTRIBUTES} to set the audio attributes. The
 *       message payload should be an {@link AudioAttributes} instance that will configure the
 *       underlying audio track.
 *   <li>Message with type {@link #MSG_SET_AUX_EFFECT_INFO} to set the auxiliary effect. The message
 *       payload should be an {@link AuxEffectInfo} instance that will configure the underlying
 *       audio track.
 *   <li>Message with type {@link #MSG_SET_SKIP_SILENCE_ENABLED} to enable or disable skipping
 *       silences. The message payload should be a {@link Boolean}.
 *   <li>Message with type {@link #MSG_SET_AUDIO_SESSION_ID} to set the audio session ID. The
 *       message payload should be a session ID {@link Integer} that will be attached to the
 *       underlying audio track.
 * </ul>
 */
@UnstableApi
public class MediaCodecAudioRenderer extends MediaCodecRenderer implements MediaClock {

  private static final String TAG = "MediaCodecAudioRenderer";

  /**
   * Custom key used to indicate bits per sample by some decoders on Vivo devices. For example
   * OMX.vivo.alac.decoder on the Vivo Z1 Pro.
   */
  private static final String VIVO_BITS_PER_SAMPLE_KEY = "v-bits-per-sample";

  private final Context context;
  private final EventDispatcher eventDispatcher;
  private final AudioSink audioSink;

  private int codecMaxInputSize;
  private boolean codecNeedsDiscardChannelsWorkaround;
  private boolean codecNeedsVorbisToAndroidChannelMappingWorkaround;
  @Nullable private Format inputFormat;

  /** Codec used for DRM decryption only in passthrough and offload. */
  @Nullable private Format decryptOnlyCodecFormat;

  private long currentPositionUs;
  private boolean allowPositionDiscontinuity;
  private boolean audioSinkNeedsReset;

  @Nullable private WakeupListener wakeupListener;
  private boolean hasPendingReportedSkippedSilence;

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(context, mediaCodecSelector, /* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener) {
    this(
        context,
        mediaCodecSelector,
        eventHandler,
        eventListener,
        new DefaultAudioSink.Builder(context).build());
  }

  /**
   * @deprecated Use a constructor without {@link AudioCapabilities}. These are obtained
   *     automatically from the {@link Context}.
   */
  @SuppressWarnings("deprecation") // Calling deprecated method for compatibility
  @Deprecated
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(
        context,
        mediaCodecSelector,
        eventHandler,
        eventListener,
        new DefaultAudioSink.Builder()
            .setAudioCapabilities( // For backward compatibility, null == default.
                firstNonNull(audioCapabilities, AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES))
            .setAudioProcessors(audioProcessors)
            .build());
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    this(
        context,
        MediaCodecAdapter.Factory.getDefault(context),
        mediaCodecSelector,
        /* enableDecoderFallback= */ false,
        eventHandler,
        eventListener,
        audioSink);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is slower/less efficient than
   *     the primary decoder.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    this(
        context,
        MediaCodecAdapter.Factory.getDefault(context),
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        audioSink);
  }

  /**
   * Creates a new instance.
   *
   * @param context A context.
   * @param codecAdapterFactory The {@link MediaCodecAdapter.Factory} used to create {@link
   *     MediaCodecAdapter} instances.
   * @param mediaCodecSelector A decoder selector.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is slower/less efficient than
   *     the primary decoder.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(
        C.TRACK_TYPE_AUDIO,
        codecAdapterFactory,
        mediaCodecSelector,
        enableDecoderFallback,
        /* assumedMinimumCodecOperatingRate= */ 44100);
    context = context.getApplicationContext();
    this.context = context;
    this.audioSink = audioSink;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioSink.setListener(new AudioSinkListener());
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected @Capabilities int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    if (!MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
    @TunnelingSupport
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    boolean formatHasDrm = format.cryptoType != C.CRYPTO_TYPE_NONE;
    boolean supportsFormatDrm = supportsFormatDrm(format);

    @AudioOffloadSupport int audioOffloadSupport = AUDIO_OFFLOAD_NOT_SUPPORTED;
    // In direct mode, if the format has DRM then we need to use a decoder that only decrypts.
    // Else we don't need a decoder at all.
    if (supportsFormatDrm
        && (!formatHasDrm || MediaCodecUtil.getDecryptOnlyDecoderInfo() != null)) {
      audioOffloadSupport = getAudioOffloadSupport(format);
      if (audioSink.supportsFormat(format)) {
        return RendererCapabilities.create(
            C.FORMAT_HANDLED, ADAPTIVE_NOT_SEAMLESS, tunnelingSupport, audioOffloadSupport);
      }
    }
    // If the input is PCM then it will be passed directly to the sink. Hence the sink must support
    // the input format directly.
    if (MimeTypes.AUDIO_RAW.equals(format.sampleMimeType) && !audioSink.supportsFormat(format)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
    // For all other input formats, we expect the decoder to output 16-bit PCM.
    if (!audioSink.supportsFormat(
        Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate))) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
    List<MediaCodecInfo> decoderInfos =
        getDecoderInfos(mediaCodecSelector, format, /* requiresSecureDecoder= */ false, audioSink);
    if (decoderInfos.isEmpty()) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
    if (!supportsFormatDrm) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
    }
    // Check whether the first decoder supports the format. This is the preferred decoder for the
    // format's MIME type, according to the MediaCodecSelector.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
    boolean isPreferredDecoder = true;
    if (!isFormatSupported) {
      // Check whether any of the other decoders support the format.
      for (int i = 1; i < decoderInfos.size(); i++) {
        MediaCodecInfo otherDecoderInfo = decoderInfos.get(i);
        if (otherDecoderInfo.isFormatSupported(format)) {
          decoderInfo = otherDecoderInfo;
          isFormatSupported = true;
          isPreferredDecoder = false;
          break;
        }
      }
    }
    @C.FormatSupport
    int formatSupport = isFormatSupported ? C.FORMAT_HANDLED : C.FORMAT_EXCEEDS_CAPABILITIES;
    @AdaptiveSupport
    int adaptiveSupport =
        isFormatSupported && decoderInfo.isSeamlessAdaptationSupported(format)
            ? ADAPTIVE_SEAMLESS
            : ADAPTIVE_NOT_SEAMLESS;
    @HardwareAccelerationSupport
    int hardwareAccelerationSupport =
        decoderInfo.hardwareAccelerated
            ? HARDWARE_ACCELERATION_SUPPORTED
            : HARDWARE_ACCELERATION_NOT_SUPPORTED;
    @DecoderSupport
    int decoderSupport = isPreferredDecoder ? DECODER_SUPPORT_PRIMARY : DECODER_SUPPORT_FALLBACK;
    return RendererCapabilities.create(
        formatSupport,
        adaptiveSupport,
        tunnelingSupport,
        hardwareAccelerationSupport,
        decoderSupport,
        audioOffloadSupport);
  }

  private @AudioOffloadSupport int getAudioOffloadSupport(Format format) {
    androidx.media3.exoplayer.audio.AudioOffloadSupport audioSinkOffloadSupport =
        audioSink.getFormatOffloadSupport(format);
    if (!audioSinkOffloadSupport.isFormatSupported) {
      return AUDIO_OFFLOAD_NOT_SUPPORTED;
    }
    @AudioOffloadSupport int audioOffloadSupport = AUDIO_OFFLOAD_SUPPORTED;
    if (audioSinkOffloadSupport.isGaplessSupported) {
      audioOffloadSupport |= AUDIO_OFFLOAD_GAPLESS_SUPPORTED;
    }
    if (audioSinkOffloadSupport.isSpeedChangeSupported) {
      audioOffloadSupport |= AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED;
    }
    return audioOffloadSupport;
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    return MediaCodecUtil.getDecoderInfosSortedByFormatSupport(
        getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder, audioSink), format);
  }

  /**
   * Returns a list of decoders that can decode media in the specified format, in the priority order
   * specified by the {@link MediaCodecSelector}. Note that since the {@link MediaCodecSelector}
   * only has access to {@link Format#sampleMimeType}, the list is not ordered to account for
   * whether each decoder supports the details of the format (e.g., taking into account the format's
   * profile, level, channel count and so on). {@link
   * MediaCodecUtil#getDecoderInfosSortedByFormatSupport} can be used to further sort the list into
   * an order where decoders that fully support the format come first.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @param audioSink The {@link AudioSink} to which audio will be output.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  private static List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      AudioSink audioSink)
      throws DecoderQueryException {
    if (format.sampleMimeType == null) {
      return ImmutableList.of();
    }
    if (audioSink.supportsFormat(format)) {
      // The format is supported directly, so a codec is only needed for decryption.
      @Nullable MediaCodecInfo codecInfo = MediaCodecUtil.getDecryptOnlyDecoderInfo();
      if (codecInfo != null) {
        return ImmutableList.of(codecInfo);
      }
    }
    return MediaCodecUtil.getDecoderInfosSoftMatch(
        mediaCodecSelector, format, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false);
  }

  @Override
  protected boolean shouldUseBypass(Format format) {
    if (getConfiguration().offloadModePreferred != AudioSink.OFFLOAD_MODE_DISABLED) {
      @AudioOffloadSupport int audioOffloadSupport = getAudioOffloadSupport(format);
      if ((audioOffloadSupport & RendererCapabilities.AUDIO_OFFLOAD_SUPPORTED) != 0
          && (getConfiguration().offloadModePreferred
                  == AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
              || (audioOffloadSupport & RendererCapabilities.AUDIO_OFFLOAD_GAPLESS_SUPPORTED) != 0
              || (format.encoderDelay == 0 && format.encoderPadding == 0))) {
        return true;
      }
    }
    return audioSink.supportsFormat(format);
  }

  @Override
  protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(
      MediaCodecInfo codecInfo,
      Format format,
      @Nullable MediaCrypto crypto,
      float codecOperatingRate) {
    codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
    codecNeedsDiscardChannelsWorkaround = codecNeedsDiscardChannelsWorkaround(codecInfo.name);
    codecNeedsVorbisToAndroidChannelMappingWorkaround =
        codecNeedsVorbisToAndroidChannelMappingWorkaround(codecInfo.name);
    MediaFormat mediaFormat =
        getMediaFormat(format, codecInfo.codecMimeType, codecMaxInputSize, codecOperatingRate);
    // Store the input MIME type if we're only using the codec for decryption.
    boolean decryptOnlyCodecEnabled =
        MimeTypes.AUDIO_RAW.equals(codecInfo.mimeType)
            && !MimeTypes.AUDIO_RAW.equals(format.sampleMimeType);
    decryptOnlyCodecFormat = decryptOnlyCodecEnabled ? format : null;
    return MediaCodecAdapter.Configuration.createForAudioDecoding(
        codecInfo, mediaFormat, format, crypto);
  }

  @Override
  protected DecoderReuseEvaluation canReuseCodec(
      MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    DecoderReuseEvaluation evaluation = codecInfo.canReuseCodec(oldFormat, newFormat);

    @DecoderDiscardReasons int discardReasons = evaluation.discardReasons;
    if (isBypassPossible(newFormat)) {
      // We prefer direct audio playback so that for multi-channel tracks the audio is not downmixed
      // to stereo.
      discardReasons |= DecoderReuseEvaluation.DISCARD_REASON_AUDIO_BYPASS_POSSIBLE;
    }
    if (getCodecMaxInputSize(codecInfo, newFormat) > codecMaxInputSize) {
      discardReasons |= DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED;
    }

    return new DecoderReuseEvaluation(
        codecInfo.name,
        oldFormat,
        newFormat,
        discardReasons != 0 ? REUSE_RESULT_NO : evaluation.result,
        discardReasons);
  }

  @Override
  @Nullable
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  protected float getCodecOperatingRateV23(
      float targetPlaybackSpeed, Format format, Format[] streamFormats) {
    // Use the highest known stream sample-rate up front, to avoid having to reconfigure the codec
    // should an adaptive switch to that stream occur.
    int maxSampleRate = -1;
    for (Format streamFormat : streamFormats) {
      int streamSampleRate = streamFormat.sampleRate;
      if (streamSampleRate != Format.NO_VALUE) {
        maxSampleRate = max(maxSampleRate, streamSampleRate);
      }
    }
    return maxSampleRate == -1 ? CODEC_OPERATING_RATE_UNSET : (maxSampleRate * targetPlaybackSpeed);
  }

  @Override
  protected void onCodecInitialized(
      String name,
      MediaCodecAdapter.Configuration configuration,
      long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
  }

  @Override
  protected void onCodecReleased(String name) {
    eventDispatcher.decoderReleased(name);
  }

  @Override
  protected void onCodecError(Exception codecError) {
    Log.e(TAG, "Audio codec error", codecError);
    eventDispatcher.audioCodecError(codecError);
  }

  @Override
  @Nullable
  protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder)
      throws ExoPlaybackException {
    Format inputFormat = checkNotNull(formatHolder.format);
    this.inputFormat = inputFormat;
    @Nullable DecoderReuseEvaluation evaluation = super.onInputFormatChanged(formatHolder);
    eventDispatcher.inputFormatChanged(inputFormat, evaluation);
    return evaluation;
  }

  @Override
  protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat)
      throws ExoPlaybackException {
    Format audioSinkInputFormat;
    @Nullable int[] channelMap = null;
    if (decryptOnlyCodecFormat != null) { // Direct playback with a codec for decryption.
      audioSinkInputFormat = decryptOnlyCodecFormat;
    } else if (getCodec() == null) { // Direct playback with codec bypass.
      audioSinkInputFormat = format;
    } else {
      checkNotNull(mediaFormat);
      @C.PcmEncoding int pcmEncoding;
      if (MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)) {
        // For PCM streams, the encoder passes through int samples despite set to float mode.
        pcmEncoding = format.pcmEncoding;
      } else if (Util.SDK_INT >= 24 && mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        pcmEncoding = mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
      } else if (mediaFormat.containsKey(VIVO_BITS_PER_SAMPLE_KEY)) {
        pcmEncoding = Util.getPcmEncoding(mediaFormat.getInteger(VIVO_BITS_PER_SAMPLE_KEY));
      } else {
        // If the format is anything other than PCM then we assume that the audio decoder will
        // output 16-bit PCM.
        pcmEncoding = C.ENCODING_PCM_16BIT;
      }
      audioSinkInputFormat =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_RAW)
              .setPcmEncoding(pcmEncoding)
              .setEncoderDelay(format.encoderDelay)
              .setEncoderPadding(format.encoderPadding)
              .setMetadata(format.metadata)
              .setId(format.id)
              .setLabel(format.label)
              .setLabels(format.labels)
              .setLanguage(format.language)
              .setSelectionFlags(format.selectionFlags)
              .setRoleFlags(format.roleFlags)
              .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
              .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
              .build();
      if (codecNeedsDiscardChannelsWorkaround
          && audioSinkInputFormat.channelCount == 6
          && format.channelCount < 6) {
        channelMap = new int[format.channelCount];
        for (int i = 0; i < format.channelCount; i++) {
          channelMap[i] = i;
        }
      } else if (codecNeedsVorbisToAndroidChannelMappingWorkaround) {
        channelMap =
            VorbisUtil.getVorbisToAndroidChannelLayoutMapping(audioSinkInputFormat.channelCount);
      }
    }
    try {
      if (Util.SDK_INT >= 29) {
        if (isBypassEnabled()
            && getConfiguration().offloadModePreferred != AudioSink.OFFLOAD_MODE_DISABLED) {
          // TODO(b/280050553): Investigate potential issue where bypass is enabled for passthrough
          //  but offload is not supported
          audioSink.setOffloadMode(getConfiguration().offloadModePreferred);
        } else {
          audioSink.setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED);
        }
      }
      audioSink.configure(audioSinkInputFormat, /* specifiedBufferSize= */ 0, channelMap);
    } catch (AudioSink.ConfigurationException e) {
      throw createRendererException(
          e, e.format, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED);
    }
  }

  /** See {@link AudioSink.Listener#onPositionDiscontinuity()}. */
  @CallSuper
  protected void onPositionDiscontinuity() {
    // We are out of sync so allow currentPositionUs to jump backwards.
    allowPositionDiscontinuity = true;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    eventDispatcher.enabled(decoderCounters);
    if (getConfiguration().tunneling) {
      audioSink.enableTunnelingV21();
    } else {
      audioSink.disableTunneling();
    }
    audioSink.setPlayerId(getPlayerId());
    audioSink.setClock(getClock());
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    audioSink.flush();

    currentPositionUs = positionUs;
    hasPendingReportedSkippedSilence = false;
    allowPositionDiscontinuity = true;
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    audioSink.play();
  }

  @Override
  protected void onStopped() {
    updateCurrentPosition();
    audioSink.pause();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    audioSinkNeedsReset = true;
    inputFormat = null;
    try {
      audioSink.flush();
    } finally {
      try {
        super.onDisabled();
      } finally {
        eventDispatcher.disabled(decoderCounters);
      }
    }
  }

  @Override
  protected void onReset() {
    hasPendingReportedSkippedSilence = false;
    try {
      super.onReset();
    } finally {
      if (audioSinkNeedsReset) {
        audioSinkNeedsReset = false;
        audioSink.reset();
      }
    }
  }

  @Override
  protected void onRelease() {
    audioSink.release();
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && audioSink.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioSink.hasPendingData() || super.isReady();
  }

  @Override
  public long getPositionUs() {
    if (getState() == STATE_STARTED) {
      updateCurrentPosition();
    }
    return currentPositionUs;
  }

  @Override
  public boolean hasSkippedSilenceSinceLastCall() {
    boolean hasPendingReportedSkippedSilence = this.hasPendingReportedSkippedSilence;
    this.hasPendingReportedSkippedSilence = false;
    return hasPendingReportedSkippedSilence;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    audioSink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioSink.getPlaybackParameters();
  }

  @Override
  protected void onProcessedStreamChange() {
    super.onProcessedStreamChange();
    audioSink.handleDiscontinuity();
  }

  @Override
  protected boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      @Nullable MediaCodecAdapter codec,
      @Nullable ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      int sampleCount,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException {
    checkNotNull(buffer);

    if (decryptOnlyCodecFormat != null
        && (bufferFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Discard output buffers from the passthrough (raw) decoder containing codec specific data.
      checkNotNull(codec).releaseOutputBuffer(bufferIndex, false);
      return true;
    }

    if (isDecodeOnlyBuffer) {
      if (codec != null) {
        codec.releaseOutputBuffer(bufferIndex, false);
      }
      decoderCounters.skippedOutputBufferCount += sampleCount;
      audioSink.handleDiscontinuity();
      return true;
    }

    boolean fullyConsumed;
    try {
      fullyConsumed = audioSink.handleBuffer(buffer, bufferPresentationTimeUs, sampleCount);
    } catch (InitializationException e) {
      throw createRendererException(
          e,
          inputFormat,
          e.isRecoverable,
          isBypassEnabled()
                  && getConfiguration().offloadModePreferred != AudioSink.OFFLOAD_MODE_DISABLED
              ? PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED
              : PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED);
    } catch (WriteException e) {
      throw createRendererException(
          e,
          format,
          e.isRecoverable,
          isBypassEnabled()
                  && getConfiguration().offloadModePreferred != AudioSink.OFFLOAD_MODE_DISABLED
              ? PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED
              : PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
    }

    if (fullyConsumed) {
      if (codec != null) {
        codec.releaseOutputBuffer(bufferIndex, false);
      }
      decoderCounters.renderedOutputBufferCount += sampleCount;
      return true;
    }

    return false;
  }

  @Override
  protected void renderToEndOfStream() throws ExoPlaybackException {
    try {
      audioSink.playToEndOfStream();
    } catch (AudioSink.WriteException e) {
      throw createRendererException(
          e,
          e.format,
          e.isRecoverable,
          isBypassEnabled()
              ? PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED
              : PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
    }
  }

  @Override
  protected void onOutputStreamOffsetUsChanged(long outputStreamOffsetUs) {
    audioSink.setOutputStreamOffsetUs(outputStreamOffsetUs);
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VOLUME:
        audioSink.setVolume((Float) checkNotNull(message));
        break;
      case MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(checkNotNull(audioAttributes));
        break;
      case MSG_SET_AUX_EFFECT_INFO:
        AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
        audioSink.setAuxEffectInfo(checkNotNull(auxEffectInfo));
        break;
      case MSG_SET_PREFERRED_AUDIO_DEVICE:
        if (Util.SDK_INT >= 23) {
          Api23.setAudioSinkPreferredDevice(audioSink, message);
        }
        break;
      case MSG_SET_SKIP_SILENCE_ENABLED:
        audioSink.setSkipSilenceEnabled((Boolean) checkNotNull(message));
        break;
      case MSG_SET_AUDIO_SESSION_ID:
        audioSink.setAudioSessionId((Integer) checkNotNull(message));
        break;
      case MSG_SET_WAKEUP_LISTENER:
        this.wakeupListener = (WakeupListener) message;
        break;
      case MSG_SET_CAMERA_MOTION_LISTENER:
      case MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
      case MSG_SET_SCALING_MODE:
      case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
      case MSG_SET_VIDEO_OUTPUT:
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  @Override
  protected void handleInputBufferSupplementalData(DecoderInputBuffer buffer) {
    if (Util.SDK_INT >= 29
        && buffer.format != null
        && Objects.equals(buffer.format.sampleMimeType, MimeTypes.AUDIO_OPUS)
        && isBypassEnabled()) {
      ByteBuffer data = checkNotNull(buffer.supplementalData);
      int preSkip = checkNotNull(buffer.format).encoderDelay;
      if (data.remaining() == 8) {
        int discardSamples =
            (int) ((data.order(ByteOrder.LITTLE_ENDIAN).getLong() * 48_000L) / C.NANOS_PER_SECOND);
        audioSink.setOffloadDelayPadding(preSkip, discardSamples);
      }
    }
  }

  /**
   * Returns a maximum input size suitable for configuring a codec for {@code format} in a way that
   * will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The {@link Format} for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return A suitable maximum input size.
   */
  protected int getCodecMaxInputSize(
      MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
    int maxInputSize = getCodecMaxInputSize(codecInfo, format);
    if (streamFormats.length == 1) {
      // The single entry in streamFormats must correspond to the format for which the codec is
      // being configured.
      return maxInputSize;
    }
    for (Format streamFormat : streamFormats) {
      if (codecInfo.canReuseCodec(format, streamFormat).result != REUSE_RESULT_NO) {
        maxInputSize = max(maxInputSize, getCodecMaxInputSize(codecInfo, streamFormat));
      }
    }
    return maxInputSize;
  }

  /**
   * Returns a maximum input buffer size for a given {@link Format}.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The {@link Format}.
   * @return A maximum input buffer size in bytes, or {@link Format#NO_VALUE} if a maximum could not
   *     be determined.
   */
  private int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    if ("OMX.google.raw.decoder".equals(codecInfo.name)) {
      // OMX.google.raw.decoder didn't resize its output buffers correctly prior to N, except on
      // Android TV running M, so there's no point requesting a non-default input size. Doing so may
      // cause a native crash, whereas not doing so will cause a more controlled failure when
      // attempting to fill an input buffer. See: https://github.com/google/ExoPlayer/issues/4057.
      if (Util.SDK_INT < 24 && !(Util.SDK_INT == 23 && Util.isTv(context))) {
        return Format.NO_VALUE;
      }
    }
    return format.maxInputSize;
  }

  /**
   * Returns the framework {@link MediaFormat} that can be used to configure a {@link MediaCodec}
   * for decoding the given {@link Format} for playback.
   *
   * @param format The {@link Format} of the media.
   * @param codecMimeType The MIME type handled by the codec.
   * @param codecMaxInputSize The maximum input size supported by the codec.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @return The framework {@link MediaFormat}.
   */
  @SuppressLint("InlinedApi")
  protected MediaFormat getMediaFormat(
      Format format, String codecMimeType, int codecMaxInputSize, float codecOperatingRate) {
    MediaFormat mediaFormat = new MediaFormat();
    // Set format parameters that should always be set.
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.sampleRate);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    // Set codec max values.
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxInputSize);
    // Set codec configuration values.
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET && !deviceDoesntSupportOperatingRate()) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      }
    }
    if (Util.SDK_INT <= 28 && MimeTypes.AUDIO_AC4.equals(format.sampleMimeType)) {
      // On some older builds, the AC-4 decoder expects to receive samples formatted as raw frames
      // not sync frames. Set a format key to override this.
      mediaFormat.setInteger("ac4-is-sync", 1);
    }
    if (Util.SDK_INT >= 24
        && audioSink.getFormatSupport(
                Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate))
            == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY) {
      mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
    }
    if (Util.SDK_INT >= 32) {
      mediaFormat.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, 99);
    }

    return mediaFormat;
  }

  private void updateCurrentPosition() {
    long newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
          allowPositionDiscontinuity
              ? newCurrentPositionUs
              : max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
  }

  /**
   * Returns whether the device's decoders are known to not support setting the codec operating
   * rate.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/5821">GitHub issue #5821</a>.
   */
  private static boolean deviceDoesntSupportOperatingRate() {
    return Util.SDK_INT == 23
        && ("ZTE B2017G".equals(Util.MODEL) || "AXON 7 mini".equals(Util.MODEL));
  }

  /**
   * Returns whether the decoder is known to output six audio channels when provided with input with
   * fewer than six channels.
   *
   * <p>See [Internal: b/35655036].
   */
  private static boolean codecNeedsDiscardChannelsWorkaround(String codecName) {
    // The workaround applies to Samsung Galaxy S6 and Samsung Galaxy S7.
    return Util.SDK_INT < 24
        && "OMX.SEC.aac.dec".equals(codecName)
        && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("zeroflte")
            || Util.DEVICE.startsWith("herolte")
            || Util.DEVICE.startsWith("heroqlte"));
  }

  /**
   * Returns whether the decoder is known to output PCM samples in VORBIS order, which does not
   * match the channel layout required by AudioTrack.
   *
   * <p>See https://github.com/google/ExoPlayer/issues/8396#issuecomment-1833867901.
   */
  private static boolean codecNeedsVorbisToAndroidChannelMappingWorkaround(String codecName) {
    return codecName.equals("OMX.google.opus.decoder")
        || codecName.equals("c2.android.opus.decoder")
        || codecName.equals("OMX.google.vorbis.decoder")
        || codecName.equals("c2.android.vorbis.decoder");
  }

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onPositionDiscontinuity() {
      MediaCodecAudioRenderer.this.onPositionDiscontinuity();
    }

    @Override
    public void onSilenceSkipped() {
      hasPendingReportedSkippedSilence = true;
    }

    @Override
    public void onPositionAdvancing(long playoutStartSystemTimeMs) {
      eventDispatcher.positionAdvancing(playoutStartSystemTimeMs);
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.underrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

    @Override
    public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
      eventDispatcher.skipSilenceEnabledChanged(skipSilenceEnabled);
    }

    @Override
    public void onOffloadBufferEmptying() {
      if (wakeupListener != null) {
        wakeupListener.onWakeup();
      }
    }

    @Override
    public void onOffloadBufferFull() {
      if (wakeupListener != null) {
        wakeupListener.onSleep();
      }
    }

    @Override
    public void onAudioSinkError(Exception audioSinkError) {
      Log.e(TAG, "Audio sink error", audioSinkError);
      eventDispatcher.audioSinkError(audioSinkError);
    }

    @Override
    public void onAudioCapabilitiesChanged() {
      MediaCodecAudioRenderer.this.onRendererCapabilitiesChanged();
    }

    @Override
    public void onAudioTrackInitialized(AudioSink.AudioTrackConfig audioTrackConfig) {
      eventDispatcher.audioTrackInitialized(audioTrackConfig);
    }

    @Override
    public void onAudioTrackReleased(AudioSink.AudioTrackConfig audioTrackConfig) {
      eventDispatcher.audioTrackReleased(audioTrackConfig);
    }
  }

  @RequiresApi(23)
  private static final class Api23 {
    private Api23() {}

    @DoNotInline
    public static void setAudioSinkPreferredDevice(
        AudioSink audioSink, @Nullable Object messagePayload) {
      @Nullable AudioDeviceInfo audioDeviceInfo = (AudioDeviceInfo) messagePayload;
      audioSink.setPreferredDevice(audioDeviceInfo);
    }
  }
}
