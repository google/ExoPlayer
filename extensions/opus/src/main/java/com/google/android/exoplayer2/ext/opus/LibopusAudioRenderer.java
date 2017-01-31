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
package com.google.android.exoplayer2.ext.opus;

import android.os.Handler;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Decodes and renders audio using the native Opus decoder.
 */
public final class LibopusAudioRenderer extends SimpleDecoderAudioRenderer {

  private static final int NUM_BUFFERS = 16;
  private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6;

  public LibopusAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public LibopusAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener) {
    super(eventHandler, eventListener);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   */
  public LibopusAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
      AudioCapabilities audioCapabilities) {
    super(eventHandler, eventListener, audioCapabilities);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   */
  public LibopusAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
      AudioCapabilities audioCapabilities, DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    super(eventHandler, eventListener, audioCapabilities, drmSessionManager,
        playClearSamplesWithoutKeys);
  }

  @Override
  protected int supportsFormatInternal(Format format) {
    return OpusLibrary.isAvailable() && MimeTypes.AUDIO_OPUS.equalsIgnoreCase(format.sampleMimeType)
        ? FORMAT_HANDLED : FORMAT_UNSUPPORTED_TYPE;
  }

  @Override
  protected OpusDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws OpusDecoderException {
    return new OpusDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE,
        format.initializationData, mediaCrypto);
  }

}
