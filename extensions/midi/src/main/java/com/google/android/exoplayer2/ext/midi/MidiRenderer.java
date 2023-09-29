/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.midi;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.DecoderAudioRenderer;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Decodes and renders MIDI audio.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class MidiRenderer extends DecoderAudioRenderer<MidiDecoder> {

  private final Context context;

  /** Creates the renderer instance. */
  public MidiRenderer(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public String getName() {
    return "MidiRenderer";
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    if (!MimeTypes.AUDIO_EXOPLAYER_MIDI.equals(format.sampleMimeType)) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    }

    if (!sinkSupportsFormat(MidiDecoder.getDecoderOutputFormat())) {
      return C.FORMAT_UNSUPPORTED_SUBTYPE;
    }

    return C.FORMAT_HANDLED;
  }

  /** {@inheritDoc} */
  @Override
  protected MidiDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws MidiDecoderException {
    return new MidiDecoder(context);
  }

  /** {@inheritDoc} */
  @Override
  protected Format getOutputFormat(MidiDecoder decoder) {
    return MidiDecoder.getDecoderOutputFormat();
  }
}
