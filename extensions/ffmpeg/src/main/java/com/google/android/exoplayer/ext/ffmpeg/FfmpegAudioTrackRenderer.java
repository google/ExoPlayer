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
package com.google.android.exoplayer.ext.ffmpeg;

import com.google.android.exoplayer.AudioTrackRendererEventListener;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extensions.AudioDecoderTrackRenderer;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;

/**
 * Decodes and renders audio using FFmpeg.
 */
public final class FfmpegAudioTrackRenderer extends AudioDecoderTrackRenderer {

  private static final int NUM_BUFFERS = 16;
  private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6;

  private FfmpegDecoder decoder;

  public FfmpegAudioTrackRenderer() {
    this(null, null);
  }

  public FfmpegAudioTrackRenderer(Handler eventHandler,
      AudioTrackRendererEventListener eventListener) {
    super(eventHandler, eventListener);
  }

  @Override
  protected int supportsFormat(Format format) {
    if (!FfmpegDecoder.IS_AVAILABLE) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    String mimeType = format.sampleMimeType;
    return FfmpegDecoder.supportsFormat(mimeType) ? FORMAT_HANDLED
        : MimeTypes.isAudio(mimeType) ? FORMAT_UNSUPPORTED_SUBTYPE : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected FfmpegDecoder createDecoder(Format format) throws FfmpegDecoderException {
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
