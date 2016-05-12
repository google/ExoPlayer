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
package com.google.android.exoplayer.ext.opus;

import com.google.android.exoplayer.AudioTrackRendererEventListener;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extensions.AudioDecoderTrackRenderer;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;

import java.util.List;

/**
 * Decodes and renders audio using the native Opus decoder.
 */
public final class LibopusAudioTrackRenderer extends AudioDecoderTrackRenderer {

  private static final int NUM_BUFFERS = 16;
  private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6;

  /**
   * Returns whether the underlying libopus library is available.
   */
  public static boolean isLibopusAvailable() {
    return OpusDecoder.IS_AVAILABLE;
  }

  /**
   * Returns the version of the underlying libopus library if available, otherwise {@code null}.
   */
  public static String getLibopusVersion() {
    return isLibopusAvailable() ? OpusDecoder.getLibopusVersion() : null;
  }

  public LibopusAudioTrackRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public LibopusAudioTrackRenderer(Handler eventHandler,
      AudioTrackRendererEventListener eventListener) {
    super(eventHandler, eventListener);
  }

  @Override
  protected int supportsFormat(Format format) {
    return isLibopusAvailable() && MimeTypes.AUDIO_OPUS.equalsIgnoreCase(format.sampleMimeType)
        ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected OpusDecoder createDecoder(List<byte[]> initializationData) throws OpusDecoderException {
    return new OpusDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE,
        initializationData);
  }

}
