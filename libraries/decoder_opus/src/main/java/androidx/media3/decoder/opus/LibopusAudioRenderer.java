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
package androidx.media3.decoder.opus;

import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioSink.SinkFormatSupport;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import androidx.media3.extractor.VorbisUtil;

/** Decodes and renders audio using the native Opus decoder. */
@UnstableApi
public class LibopusAudioRenderer extends DecoderAudioRenderer<OpusDecoder> {

  private static final String TAG = "LibopusAudioRenderer";

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;

  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;

  public LibopusAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public LibopusAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public LibopusAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(eventHandler, eventListener, audioSink);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    boolean drmIsSupported = OpusLibrary.supportsCryptoType(format.cryptoType);
    if (!OpusLibrary.isAvailable()
        || !MimeTypes.AUDIO_OPUS.equalsIgnoreCase(format.sampleMimeType)) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    } else if (!sinkSupportsFormat(
        Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate))) {
      return C.FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (!drmIsSupported) {
      return C.FORMAT_UNSUPPORTED_DRM;
    } else {
      return C.FORMAT_HANDLED;
    }
  }

  /**
   * {@inheritDoc}
   *
   * @hide
   */
  @Override
  protected final OpusDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws OpusDecoderException {
    TraceUtil.beginSection("createOpusDecoder");
    @SinkFormatSupport
    int formatSupport =
        getSinkFormatSupport(
            Util.getPcmFormat(C.ENCODING_PCM_FLOAT, format.channelCount, format.sampleRate));
    boolean outputFloat = formatSupport == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY;

    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    OpusDecoder decoder =
        new OpusDecoder(
            NUM_BUFFERS,
            NUM_BUFFERS,
            initialInputBufferSize,
            format.initializationData,
            cryptoConfig,
            outputFloat);
    decoder.experimentalSetDiscardPaddingEnabled(experimentalGetDiscardPaddingEnabled());

    TraceUtil.endSection();
    return decoder;
  }

  /**
   * {@inheritDoc}
   *
   * @hide
   */
  @Override
  protected final Format getOutputFormat(OpusDecoder decoder) {
    @C.PcmEncoding
    int pcmEncoding = decoder.outputFloat ? C.ENCODING_PCM_FLOAT : C.ENCODING_PCM_16BIT;
    return Util.getPcmFormat(pcmEncoding, decoder.channelCount, OpusDecoder.SAMPLE_RATE);
  }

  @Nullable
  @Override
  protected int[] getChannelMapping(OpusDecoder decoder) {
    return VorbisUtil.getVorbisToAndroidChannelLayoutMapping(decoder.channelCount);
  }

  /**
   * Returns true if support for padding removal from the end of decoder output buffer should be
   * enabled.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   */
  protected boolean experimentalGetDiscardPaddingEnabled() {
    return false;
  }
}
