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

import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED;
import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.audio.AudioSink.InitializationException;
import com.google.android.exoplayer2.audio.AudioSink.WriteException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DecoderDiscardReasons;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 *       message payload should be an {@link com.google.android.exoplayer2.audio.AudioAttributes}
 *       instance that will configure the underlying audio track.
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
  /** Codec used for DRM decryption only in passthrough and offload. */
  @Nullable private Format decryptOnlyCodecFormat;

  private long currentPositionUs;
  private boolean allowFirstBufferPositionDiscontinuity;
  private boolean allowPositionDiscontinuity;
  private boolean audioSinkNeedsReset;

  private boolean experimentalKeepAudioTrackOnSeek;

  @Nullable private WakeupListener wakeupListener;

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
    this(context, mediaCodecSelector, eventHandler, eventListener, (AudioCapabilities) null);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process PCM audio before
   *     output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(
        context,
        mediaCodecSelector,
        eventHandler,
        eventListener,
        new DefaultAudioSink(audioCapabilities, audioProcessors));
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
        MediaCodecAdapter.Factory.DEFAULT,
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
        MediaCodecAdapter.Factory.DEFAULT,
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
    this.context = context.getApplicationContext();
    this.audioSink = audioSink;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioSink.setListener(new AudioSinkListener());
  }

  @Override
  public String getName() {
    return TAG;
  }

  /**
   * Sets whether to enable the experimental feature that keeps and flushes the {@link
   * android.media.AudioTrack} when a seek occurs, as opposed to releasing and reinitialising. Off
   * by default.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enableKeepAudioTrackOnSeek Whether to keep the {@link android.media.AudioTrack} on seek.
   */
  public void experimentalSetEnableKeepAudioTrackOnSeek(boolean enableKeepAudioTrackOnSeek) {
    this.experimentalKeepAudioTrackOnSeek = enableKeepAudioTrackOnSeek;
  }

  @Override
  @Capabilities
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    if (!MimeTypes.isAudio(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
    @TunnelingSupport
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    boolean formatHasDrm = format.exoMediaCryptoType != null;
    boolean supportsFormatDrm = supportsFormatDrm(format);
    // In direct mode, if the format has DRM then we need to use a decoder that only decrypts.
    // Else we don't don't need a decoder at all.
    if (supportsFormatDrm
        && audioSink.supportsFormat(format)
        && (!formatHasDrm || MediaCodecUtil.getDecryptOnlyDecoderInfo() != null)) {
      return RendererCapabilities.create(C.FORMAT_HANDLED, ADAPTIVE_NOT_SEAMLESS, tunnelingSupport);
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
        getDecoderInfos(mediaCodecSelector, format, /* requiresSecureDecoder= */ false);
    if (decoderInfos.isEmpty()) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
    if (!supportsFormatDrm) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
    }
    // Check capabilities for the first decoder in the list, which takes priority.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
    @AdaptiveSupport
    int adaptiveSupport =
        isFormatSupported && decoderInfo.isSeamlessAdaptationSupported(format)
            ? ADAPTIVE_SEAMLESS
            : ADAPTIVE_NOT_SEAMLESS;
    @C.FormatSupport
    int formatSupport = isFormatSupported ? C.FORMAT_HANDLED : C.FORMAT_EXCEEDS_CAPABILITIES;
    return RendererCapabilities.create(formatSupport, adaptiveSupport, tunnelingSupport);
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    @Nullable String mimeType = format.sampleMimeType;
    if (mimeType == null) {
      return Collections.emptyList();
    }
    if (audioSink.supportsFormat(format)) {
      // The format is supported directly, so a codec is only needed for decryption.
      @Nullable MediaCodecInfo codecInfo = MediaCodecUtil.getDecryptOnlyDecoderInfo();
      if (codecInfo != null) {
        return Collections.singletonList(codecInfo);
      }
    }
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(
            mimeType, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false);
    decoderInfos = MediaCodecUtil.getDecoderInfosSortedByFormatSupport(decoderInfos, format);
    if (MimeTypes.AUDIO_E_AC3_JOC.equals(mimeType)) {
      // E-AC3 decoders can decode JOC streams, but in 2-D rather than 3-D.
      List<MediaCodecInfo> decoderInfosWithEac3 = new ArrayList<>(decoderInfos);
      decoderInfosWithEac3.addAll(
          mediaCodecSelector.getDecoderInfos(
              MimeTypes.AUDIO_E_AC3, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false));
      decoderInfos = decoderInfosWithEac3;
    }
    return Collections.unmodifiableList(decoderInfos);
  }

  @Override
  protected boolean shouldUseBypass(Format format) {
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
    MediaFormat mediaFormat =
        getMediaFormat(format, codecInfo.codecMimeType, codecMaxInputSize, codecOperatingRate);
    // Store the input MIME type if we're only using the codec for decryption.
    boolean decryptOnlyCodecEnabled =
        MimeTypes.AUDIO_RAW.equals(codecInfo.mimeType)
            && !MimeTypes.AUDIO_RAW.equals(format.sampleMimeType);
    decryptOnlyCodecFormat = decryptOnlyCodecEnabled ? format : null;
    return new MediaCodecAdapter.Configuration(
        codecInfo, mediaFormat, format, /* surface= */ null, crypto, /* flags= */ 0);
  }

  @Override
  protected DecoderReuseEvaluation canReuseCodec(
      MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    DecoderReuseEvaluation evaluation = codecInfo.canReuseCodec(oldFormat, newFormat);

    @DecoderDiscardReasons int discardReasons = evaluation.discardReasons;
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
      String name, long initializedTimestampMs, long initializationDurationMs) {
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
    @Nullable DecoderReuseEvaluation evaluation = super.onInputFormatChanged(formatHolder);
    eventDispatcher.inputFormatChanged(formatHolder.format, evaluation);
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
        pcmEncoding =
            MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)
                ? format.pcmEncoding
                : C.ENCODING_PCM_16BIT;
      }
      audioSinkInputFormat =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_RAW)
              .setPcmEncoding(pcmEncoding)
              .setEncoderDelay(format.encoderDelay)
              .setEncoderPadding(format.encoderPadding)
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
      }
    }
    try {
      audioSink.configure(audioSinkInputFormat, /* specifiedBufferSize= */ 0, channelMap);
    } catch (AudioSink.ConfigurationException e) {
      throw createRendererException(e, e.format);
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
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    if (experimentalKeepAudioTrackOnSeek) {
      audioSink.experimentalFlushWithoutAudioTrackRelease();
    } else {
      audioSink.flush();
    }

    currentPositionUs = positionUs;
    allowFirstBufferPositionDiscontinuity = true;
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
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    audioSink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioSink.getPlaybackParameters();
  }

  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
      // TODO: Remove this hack once we have a proper fix for [Internal: b/71876314].
      // Allow the position to jump if the first presentable input buffer has a timestamp that
      // differs significantly from what was expected.
      if (Math.abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs;
      }
      allowFirstBufferPositionDiscontinuity = false;
    }
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
      throw createRendererException(e, e.format, e.isRecoverable);
    } catch (WriteException e) {
      throw createRendererException(e, format, e.isRecoverable);
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
      throw createRendererException(e, e.format, e.isRecoverable);
    }
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VOLUME:
        audioSink.setVolume((Float) message);
        break;
      case MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(audioAttributes);
        break;
      case MSG_SET_AUX_EFFECT_INFO:
        AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
        audioSink.setAuxEffectInfo(auxEffectInfo);
        break;
      case MSG_SET_SKIP_SILENCE_ENABLED:
        audioSink.setSkipSilenceEnabled((Boolean) message);
        break;
      case MSG_SET_AUDIO_SESSION_ID:
        audioSink.setAudioSessionId((Integer) message);
        break;
      case MSG_SET_WAKEUP_LISTENER:
        this.wakeupListener = (WakeupListener) message;
        break;
      default:
        super.handleMessage(messageType, message);
        break;
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

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onPositionDiscontinuity() {
      MediaCodecAudioRenderer.this.onPositionDiscontinuity();
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
    public void onOffloadBufferFull(long bufferEmptyingDeadlineMs) {
      if (wakeupListener != null) {
        wakeupListener.onSleep(bufferEmptyingDeadlineMs);
      }
    }

    @Override
    public void onAudioSinkError(Exception audioSinkError) {
      Log.e(TAG, "Audio sink error", audioSinkError);
      eventDispatcher.audioSinkError(audioSinkError);
    }
  }
}
