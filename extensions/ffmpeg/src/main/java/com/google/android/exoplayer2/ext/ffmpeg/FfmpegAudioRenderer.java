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
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;

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

  public FfmpegAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public FfmpegAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  @Override
  protected int supportsFormatInternal(Format format) {
    if (!FfmpegLibrary.isAvailable()) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    String mimeType = format.sampleMimeType;
    return FfmpegLibrary.supportsFormat(mimeType) ? FORMAT_HANDLED
        : MimeTypes.isAudio(mimeType) ? FORMAT_UNSUPPORTED_SUBTYPE : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  public final int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected FfmpegDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws FfmpegDecoderException {
    decoder = new FfmpegDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE,
        format.sampleMimeType, format.initializationData);
    return decoder;
  }

  @Override
  public Format getOutputFormat() {
    int channelCount = decoder.getChannelCount();
    int sampleRate = decoder.getSampleRate();
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, channelCount, sampleRate, C.ENCODING_PCM_16BIT, null, null, 0, null);
  }

}
