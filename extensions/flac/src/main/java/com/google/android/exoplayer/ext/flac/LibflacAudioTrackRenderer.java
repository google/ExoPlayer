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
package com.google.android.exoplayer.ext.flac;

import com.google.android.exoplayer.AudioTrackRendererEventListener;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.extensions.AudioDecoderTrackRenderer;
import com.google.android.exoplayer.util.MimeTypes;

import android.os.Handler;

import java.util.List;

/**
 * Decodes and renders audio using the native Flac decoder.
 */
public class LibflacAudioTrackRenderer extends AudioDecoderTrackRenderer {

  private static final int NUM_BUFFERS = 16;

  /**
   * Returns whether the underlying libflac library is available.
   */
  public static boolean isLibflacAvailable() {
    return FlacJni.IS_AVAILABLE;
  }

  public LibflacAudioTrackRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public LibflacAudioTrackRenderer(Handler eventHandler,
      AudioTrackRendererEventListener eventListener) {
    super(eventHandler, eventListener);
  }
  @Override
  protected int supportsFormat(Format format) {
    return isLibflacAvailable() && MimeTypes.AUDIO_FLAC.equalsIgnoreCase(format.sampleMimeType)
        ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected FlacDecoder createDecoder(List<byte[]> initializationData) throws FlacDecoderException {
    return new FlacDecoder(NUM_BUFFERS, NUM_BUFFERS, initializationData);
  }

}
