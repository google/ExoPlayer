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
package androidx.media3.decoder.midi;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

/** Decodes and renders MIDI audio. */
@UnstableApi
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

  /**
   * {@inheritDoc}
   *
   * @hide
   */
  @Override
  protected MidiDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws MidiDecoderException {
    return new MidiDecoder(context);
  }

  /**
   * {@inheritDoc}
   *
   * @hide
   */
  @Override
  protected Format getOutputFormat(MidiDecoder decoder) {
    return MidiDecoder.getDecoderOutputFormat();
  }
}
