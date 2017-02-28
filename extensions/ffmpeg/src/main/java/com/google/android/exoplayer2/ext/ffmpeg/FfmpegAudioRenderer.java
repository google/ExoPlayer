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
package com.google.android.exoplayer2.ext.ffmpeg;

import android.os.Handler;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

/**
 * Decodes and renders audio using FFmpeg.
 */
public final class FfmpegAudioRenderer extends SimpleDecoderAudioRenderer {

  /**
   * The number of input and output buffers.
   */
  private static final int NUM_BUFFERS = 16;
  /**
   * The initial input buffer size. Input buffers are reallocated dynamically if this value is
   * insufficient.
   */
  private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6;

  private FfmpegDecoder decoder;
  private AudioCapabilities capabilities = null;

  public FfmpegAudioRenderer() { this(null, null, null); }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities Device's audio capabilities
   * @param audioProcessors Optional {@link AudioProcessor}s which will process PCM audio buffers
   *     before they are output.
   */
  public FfmpegAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
    AudioCapabilities audioCapabilities, AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioCapabilities, audioProcessors);
    capabilities = audioCapabilities;
  }

  @Override
  protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      Format format) {
    String sampleMimeType = format.sampleMimeType;
    if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(sampleMimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!FfmpegLibrary.supportsFormat(sampleMimeType)) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  public final int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected FfmpegDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws FfmpegDecoderException {
    decoder = new FfmpegDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE,
        format.sampleMimeType, format.initializationData,
        Util.canHandle32BitFloatAudio(capabilities, format.channelCount));
    return decoder;
  }

  @Override
  public Format getOutputFormat() {
    int channelCount = decoder.getChannelCount();
    int sampleRate = decoder.getSampleRate();
    @C.PcmEncoding int pcmEncoding = C.ENCODING_PCM_16BIT;
    if (Util.canHandle32BitFloatAudio(capabilities, channelCount))
      pcmEncoding = C.ENCODING_PCM_FLOAT;
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, channelCount, sampleRate, pcmEncoding, null, null, 0, null);
  }

}
