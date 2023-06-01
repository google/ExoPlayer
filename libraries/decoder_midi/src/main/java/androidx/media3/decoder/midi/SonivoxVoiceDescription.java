/*
 * Copyright 2023 The Android Open Source Project
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
import androidx.media3.common.util.UnstableApi;
import com.jsyn.unitgen.UnitVoice;
import com.jsyn.util.VoiceDescription;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Synthesizer voice description, used for obtaining {@link SonivoxSynthVoice} instances. */
@UnstableApi
/* package */ final class SonivoxVoiceDescription extends VoiceDescription {
  private static final String VOICE_CLASS_NAME = "SonivoxVoiceDescription";
  private static final String[] tags = {"wavetable", "GM2", "ringtone"};

  private static final Object LOCK = new Object();
  private static @MonotonicNonNull SonivoxVoiceDescription instance;

  public static SonivoxVoiceDescription getInstance(Context context) throws MidiDecoderException {
    synchronized (LOCK) {
      if (instance == null) {
        instance = new SonivoxVoiceDescription(SonivoxWaveData.loadWaveTableData(context));
      }
      return instance;
    }
  }

  private final short[] waveTableData;

  private SonivoxVoiceDescription(short[] waveTableData) {
    super(VOICE_CLASS_NAME, SonivoxWaveData.getProgramNames());
    this.waveTableData = waveTableData;
  }

  @Override
  public UnitVoice createUnitVoice() {
    // We must return a new instance every time.
    return new SonivoxSynthVoice(waveTableData);
  }

  @Override
  public String[] getTags(int presetIndex) {
    return tags;
  }

  @Override
  public String getVoiceClassName() {
    return VOICE_CLASS_NAME;
  }
}
