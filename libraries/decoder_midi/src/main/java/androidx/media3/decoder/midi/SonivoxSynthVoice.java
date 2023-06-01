/*
 * Copyright 2023 The Android Open Source Project
 * Copyright 2009 Sonic Network Inc.
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

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.midi.SonivoxWaveData.Envelope;
import androidx.media3.decoder.midi.SonivoxWaveData.WavetableRegion;
import com.jsyn.data.ShortSample;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;
import com.jsyn.unitgen.Circuit;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterLowPass;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.UnitVoice;
import com.jsyn.unitgen.VariableRateDataReader;
import com.jsyn.unitgen.VariableRateMonoReader;
import com.jsyn.unitgen.WhiteNoise;
import com.softsynth.math.AudioMath;
import com.softsynth.shared.time.TimeStamp;

/**
 * Synthesizer voice with a wavetable oscillator. Modulates the amplitude and filter using DAHDSR
 * envelopes. This synth uses the {@linkplain SonivoxWaveData Sonivox wave and instrument data} to
 * implement General MIDI.
 */
@UnstableApi
/* package */ final class SonivoxSynthVoice extends Circuit implements UnitVoice {

  // TODO(b/228838584): Replace with automatic gain control.
  private static final double AMPLITUDE_SCALER = 10.0;
  // TODO(b/228838584): Remove when modulation tuning is complete.
  private static final boolean FILTER_ENABLED = false;

  private static final double DEFAULT_FILTER_CUTOFF = 4000.0;

  private final ShortSample sonivoxSample;
  private final UnitInputPort amplitude;
  private final UnitInputPort frequency;
  private final VariableRateDataReader waveOscillator;
  private final WhiteNoise whiteNoise;
  private final FilterLowPass filter;
  private final EnvelopeDAHDSR ampEnv;
  private final EnvelopeDAHDSR filterEnv;
  private final Multiply amplitudeMultiplier;
  private final UnitInputPort cutoff;
  private final UnitInputPort filterEnvDepth;

  private int waveOffset;
  private int waveSize;
  private int presetIndex;
  @Nullable private WavetableRegion region;

  /** Creates a new instance with the supplied wave table data. */
  public SonivoxSynthVoice(short[] waveData) {
    sonivoxSample = new ShortSample(waveData);
    amplitudeMultiplier = new Multiply();
    waveOscillator = new VariableRateMonoReader();
    whiteNoise = new WhiteNoise();
    ampEnv = new EnvelopeDAHDSR();
    filterEnv = new EnvelopeDAHDSR();
    filterEnvDepth = filterEnv.amplitude;
    filter = new FilterLowPass();
    amplitude = amplitudeMultiplier.inputB;
    Multiply frequencyMultiplier = new Multiply();
    frequency = frequencyMultiplier.inputA;
    Add cutoffAdder = new Add();
    cutoff = cutoffAdder.inputB;

    // Scales the frequency value. You can use this to modulate a group of instruments using a
    // shared LFO and they will stay in tune. Set to 1.0 for no modulation.
    UnitInputPort frequencyScaler = frequencyMultiplier.inputB;
    Multiply amplitudeBoost = new Multiply();
    add(frequencyMultiplier);
    add(amplitudeMultiplier);
    add(amplitudeBoost);
    add(waveOscillator);
    add(whiteNoise);
    // Use an envelope to control the amplitude.
    add(ampEnv);
    // Use an envelope to control the filter cutoff.
    add(filterEnv);
    add(filter);
    add(cutoffAdder);

    filterEnv.output.connect(cutoffAdder.inputA);
    cutoffAdder.output.connect(filter.frequency);
    frequencyMultiplier.output.connect(waveOscillator.rate);
    if (FILTER_ENABLED) {
      amplitudeMultiplier.output.connect(filter.input);
      filter.output.connect(amplitudeBoost.inputA);
    } else {
      amplitudeMultiplier.output.connect(amplitudeBoost.inputA);
    }
    amplitudeBoost.output.connect(ampEnv.amplitude);

    addPort(amplitude, PORT_NAME_AMPLITUDE);
    addPort(frequency, PORT_NAME_FREQUENCY);
    addPort(cutoff, PORT_NAME_CUTOFF);
    addPortAlias(cutoff, PORT_NAME_TIMBRE);
    addPort(frequencyScaler, PORT_NAME_FREQUENCY_SCALER);
    addPort(filterEnvDepth, /* name= */ "FilterEnvDepth");

    filterEnv.export(this, /* prefix= */ "Filter");
    ampEnv.export(this, /* prefix= */ "Amp");
    frequency.setup(waveOscillator.rate);
    frequencyScaler.setup(/* minimum= */ 0.2, /* value= */ 1.0, /* maximum= */ 4.0);
    cutoff.setup(filter.frequency);
    // Allow negative filter sweeps
    filterEnvDepth.setup(/* minimum= */ -4000.0, /* value= */ 2000.0, /* maximum= */ 4000.0);
    waveOscillator.amplitude.set(0.5);
    // Make the circuit turn off when the envelope finishes to reduce CPU load.
    ampEnv.setupAutoDisable(this);
    // Add named port for mapping pressure.
    amplitudeBoost.inputB.setup(/* minimum= */ 1.0, /* value= */ 1.0, /* maximum= */ 4.0);
    addPortAlias(amplitudeBoost.inputB, PORT_NAME_PRESSURE);

    usePreset(/* presetIndex= */ 0);
  }

  @Override
  public void noteOff(TimeStamp timeStamp) {
    if (region == null) {
      return;
    }
    ampEnv.input.off(timeStamp);
    filterEnv.input.off(timeStamp);
    WavetableRegion region = checkNotNull(this.region);
    if (region.useNoise()) {
      if (region.isLooped()) {
        if ((region.loopEnd + 1) < waveSize) {
          int releaseStart = waveOffset + region.loopEnd;
          int releaseSize = waveSize - releaseStart;
          // Queue release portion.
          waveOscillator.dataQueue.queue(sonivoxSample, releaseStart, releaseSize, timeStamp);
        }
      }
    }
  }

  @Override
  public void noteOn(double freq, double ampl, TimeStamp timeStamp) {
    // TODO(b/228838584): add noteOnByPitch
    double pitch = AudioMath.frequencyToPitch(freq);
    region = selectRegionByNoteNumber(region, (int) (pitch + 0.5), timeStamp);
    if (region == null) {
      return;
    }

    WavetableRegion region = checkNotNull(this.region);
    double rate = 22050.0 * calculateRateScaler(region, pitch);
    double velocityGain = ampl * ampl; // Sonivox squares the velocity.
    double regionGain = region.gain * (1.0 / 32768.0);
    double gain = velocityGain * regionGain * AMPLITUDE_SCALER;
    frequency.set(rate, timeStamp);
    amplitude.set(gain, timeStamp);
    ampEnv.input.on(timeStamp);
    filterEnv.input.on(timeStamp);
    waveOscillator.output.disconnectAll();
    whiteNoise.output.disconnectAll();

    if (region.useNoise()) {
      // TODO(b/228838584): Use a switching gate instead of connecting.
      whiteNoise.output.connect(amplitudeMultiplier.inputA);
    } else {
      waveOscillator.output.connect(amplitudeMultiplier.inputA);
      waveOscillator.dataQueue.clear(timeStamp);
      if (region.isLooped()) {
        int loopStart = waveOffset + region.loopStart;
        int loopSize = waveOffset + region.loopEnd - loopStart;
        // Queue attack portion.
        if (loopStart > waveOffset) {
          waveOscillator.dataQueue.queue(
              sonivoxSample, waveOffset, loopStart - waveOffset, timeStamp);
        }
        waveOscillator.dataQueue.queueLoop(sonivoxSample, loopStart, loopSize, timeStamp);
      } else {
        waveOscillator.dataQueue.queue(sonivoxSample, waveOffset, waveSize, timeStamp);
      }
    }
  }

  @Nullable
  private WavetableRegion selectRegionByNoteNumber(
      @Nullable WavetableRegion region, int noteNumber, TimeStamp timeStamp) {
    if ((region == null) || !region.isNoteInRange(noteNumber)) {
      int regionIndex = SonivoxWaveData.getProgramRegion(presetIndex);
      region = SonivoxWaveData.extractRegion(regionIndex);
      while (!region.isNoteInRange(noteNumber) && !region.isLast()) {
        regionIndex++; // Try next region
        region = SonivoxWaveData.extractRegion(regionIndex);
      }
    }

    int waveIndex = region.waveIndex;
    if (region.useNoise()) {
      waveOffset = -1;
      waveSize = 0;
    } else {
      if (waveIndex >= SonivoxWaveData.getWaveCount()) {
        return null;
      }
      waveOffset = SonivoxWaveData.getWaveOffset(waveIndex);
      waveSize = SonivoxWaveData.getWaveSize(waveIndex);
    }
    SonivoxWaveData.Articulation articulation =
        SonivoxWaveData.extractArticulation(region.artIndex);
    applyToEnvelope(articulation.eg1, ampEnv, timeStamp);
    applyToEnvelope(articulation.eg2, filterEnv, timeStamp);

    int cutoffCents = articulation.filterCutoff;
    if (cutoffCents > 0) {
      double filterCutoffHertz = AudioMath.pitchToFrequency(cutoffCents * 0.01);
      cutoff.set(filterCutoffHertz);
    } else {
      cutoff.set(DEFAULT_FILTER_CUTOFF);
    }

    return region;
  }

  @Override
  public UnitOutputPort getOutput() {
    return ampEnv.output;
  }

  @Override
  public void usePreset(int presetIndex) {
    if (this.presetIndex == presetIndex) {
      return;
    }
    reset();
    this.presetIndex = presetIndex;
    if (presetIndex == 0) {
      ampEnv.attack.set(0.1);
      ampEnv.decay.set(0.9);
      ampEnv.sustain.set(0.1);
      ampEnv.release.set(0.1);
      cutoff.set(300.0);
      filterEnvDepth.set(500.0);
      filter.Q.set(3.0);
    }
  }

  private void reset() {
    ampEnv.attack.set(0.1);
    ampEnv.decay.set(0.9);
    ampEnv.sustain.set(0.1);
    ampEnv.release.set(0.1);
    filterEnv.attack.set(0.01);
    filterEnv.decay.set(0.6);
    filterEnv.sustain.set(0.4);
    filterEnv.release.set(1.0);
    filter.Q.set(1.0);
    cutoff.set(5000.0);
    filterEnvDepth.set(500.0);
    region = null;
  }

  private static double calculateRateScaler(WavetableRegion region, double pitch) {
    double detuneSemitones = pitch + (region.tuning * 0.01);
    return Math.pow(2.0, detuneSemitones / 12.0);
  }

  private static void applyToEnvelope(Envelope eg, EnvelopeDAHDSR dahdsr, TimeStamp timeStamp) {
    dahdsr.attack.set(eg.getAttackTimeInSeconds(), timeStamp);
    dahdsr.decay.set(eg.getDecayTimeInSeconds(), timeStamp);
    dahdsr.sustain.set(eg.getSustainLevel(), timeStamp);
    dahdsr.release.set(eg.getReleaseTimeInSeconds(), timeStamp);
  }
}
