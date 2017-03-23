/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2010 Bill Cox, Sonic Library
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
package com.google.android.exoplayer2.audio;

/**
 * Sonic audio time/pitch stretching library. Based on https://github.com/waywardgeek/sonic.
 */
/* package */ final class Sonic {

  private static final int SONIC_MIN_PITCH = 65;
  private static final int SONIC_MAX_PITCH = 400;
  /* This is used to down-sample some inputs to improve speed */
  private static final int SONIC_AMDF_FREQ = 4000;

  private short[] inputBuffer;
  private short[] outputBuffer;
  private short[] pitchBuffer;
  private short[] downSampleBuffer;
  private float speed;
  private float volume;
  private float pitch;
  private float rate;
  private int oldRatePosition;
  private int newRatePosition;
  private boolean useChordPitch;
  private int quality;
  private int numChannels;
  private int inputBufferSize;
  private int pitchBufferSize;
  private int outputBufferSize;
  private int numInputSamples;
  private int numOutputSamples;
  private int numPitchSamples;
  private int minPeriod;
  private int maxPeriod;
  private int maxRequired;
  private int remainingInputToCopy;
  private int sampleRate;
  private int prevPeriod;
  private int prevMinDiff;
  private int minDiff;
  private int maxDiff;

  // Resize the array.
  private short[] resize(short[] oldArray, int newLength) {
    newLength *= numChannels;
    short[] newArray = new short[newLength];
    int length = Math.min(oldArray.length, newLength);

    System.arraycopy(oldArray, 0, newArray, 0, length);
    return newArray;
  }

  // Move samples from one array to another.  May move samples down within an array, but not up.
  private void move(short[] dest, int destPos, short[] source, int sourcePos, int numSamples) {
    System.arraycopy(
        source, sourcePos * numChannels, dest, destPos * numChannels, numSamples * numChannels);
  }

  // Scale the samples by the factor.
  private void scaleSamples(short[] samples, int position, int numSamples, float volume) {
    int fixedPointVolume = (int) (volume * 4096.0f);
    int start = position * numChannels;
    int stop = start + numSamples * numChannels;

    for (int xSample = start; xSample < stop; xSample++) {
      int value = (samples[xSample] * fixedPointVolume) >> 12;
      if (value > 32767) {
        value = 32767;
      } else if (value < -32767) {
        value = -32767;
      }
      samples[xSample] = (short) value;
    }
  }

  // Get the speed of the stream.
  public float getSpeed() {
    return speed;
  }

  // Set the speed of the stream.
  public void setSpeed(float speed) {
    this.speed = speed;
  }

  // Get the pitch of the stream.
  public float getPitch() {
    return pitch;
  }

  // Set the pitch of the stream.
  public void setPitch(float pitch) {
    this.pitch = pitch;
  }

  // Get the rate of the stream.
  public float getRate() {
    return rate;
  }

  // Set the playback rate of the stream. This scales pitch and speed at the same time.
  public void setRate(float rate) {
    this.rate = rate;
    this.oldRatePosition = 0;
    this.newRatePosition = 0;
  }

  // Get the vocal chord pitch setting.
  public boolean getChordPitch() {
    return useChordPitch;
  }

  // Set the vocal chord mode for pitch computation.  Default is off.
  public void setChordPitch(boolean useChordPitch) {
    this.useChordPitch = useChordPitch;
  }

  // Get the quality setting.
  public int getQuality() {
    return quality;
  }

  // Set the "quality".  Default 0 is virtually as good as 1, but very much faster.
  public void setQuality(int quality) {
    this.quality = quality;
  }

  // Get the scaling factor of the stream.
  public float getVolume() {
    return volume;
  }

  // Set the scaling factor of the stream.
  public void setVolume(float volume) {
    this.volume = volume;
  }

  // Allocate stream buffers.
  private void allocateStreamBuffers(int sampleRate, int numChannels) {
    minPeriod = sampleRate / SONIC_MAX_PITCH;
    maxPeriod = sampleRate / SONIC_MIN_PITCH;
    maxRequired = 2 * maxPeriod;
    inputBufferSize = maxRequired;
    inputBuffer = new short[maxRequired * numChannels];
    outputBufferSize = maxRequired;
    outputBuffer = new short[maxRequired * numChannels];
    pitchBufferSize = maxRequired;
    pitchBuffer = new short[maxRequired * numChannels];
    downSampleBuffer = new short[maxRequired];
    this.sampleRate = sampleRate;
    this.numChannels = numChannels;
    oldRatePosition = 0;
    newRatePosition = 0;
    prevPeriod = 0;
  }

  // Create a sonic stream.
  public Sonic(int sampleRate, int numChannels) {
    allocateStreamBuffers(sampleRate, numChannels);
    speed = 1.0f;
    pitch = 1.0f;
    volume = 1.0f;
    rate = 1.0f;
    oldRatePosition = 0;
    newRatePosition = 0;
    useChordPitch = false;
    quality = 0;
  }

  // Get the sample rate of the stream.
  public int getSampleRate() {
    return sampleRate;
  }

  // Set the sample rate of the stream.  This will cause samples buffered in the stream to be lost.
  public void setSampleRate(int sampleRate) {
    allocateStreamBuffers(sampleRate, numChannels);
  }

  // Get the number of channels.
  public int getNumChannels() {
    return numChannels;
  }

  // Set the num channels of the stream.  This will cause samples buffered in the stream to be lost.
  public void setNumChannels(int numChannels) {
    allocateStreamBuffers(sampleRate, numChannels);
  }

  // Enlarge the output buffer if needed.
  private void enlargeOutputBufferIfNeeded(int numSamples) {
    if (numOutputSamples + numSamples > outputBufferSize) {
      outputBufferSize += (outputBufferSize >> 1) + numSamples;
      outputBuffer = resize(outputBuffer, outputBufferSize);
    }
  }

  // Enlarge the input buffer if needed.
  private void enlargeInputBufferIfNeeded(int numSamples) {
    if (numInputSamples + numSamples > inputBufferSize) {
      inputBufferSize += (inputBufferSize >> 1) + numSamples;
      inputBuffer = resize(inputBuffer, inputBufferSize);
    }
  }

  // Add the input samples to the input buffer.
  private void addFloatSamplesToInputBuffer(float[] samples, int numSamples) {
    if (numSamples == 0) {
      return;
    }
    enlargeInputBufferIfNeeded(numSamples);
    int xBuffer = numInputSamples * numChannels;
    for (int xSample = 0; xSample < numSamples * numChannels; xSample++) {
      inputBuffer[xBuffer++] = (short) (samples[xSample] * 32767.0f);
    }
    numInputSamples += numSamples;
  }

  // Add the input samples to the input buffer.
  private void addShortSamplesToInputBuffer(short[] samples, int numSamples) {
    if (numSamples == 0) {
      return;
    }
    enlargeInputBufferIfNeeded(numSamples);
    move(inputBuffer, numInputSamples, samples, 0, numSamples);
    numInputSamples += numSamples;
  }

  // Add the input samples to the input buffer.
  private void addUnsignedByteSamplesToInputBuffer(byte[] samples, int numSamples) {
    short sample;

    enlargeInputBufferIfNeeded(numSamples);
    int xBuffer = numInputSamples * numChannels;
    for (int xSample = 0; xSample < numSamples * numChannels; xSample++) {
      sample = (short) ((samples[xSample] & 0xff) - 128); // Convert from unsigned to signed
      inputBuffer[xBuffer++] = (short) (sample << 8);
    }
    numInputSamples += numSamples;
  }

  // Add the input samples to the input buffer.  They must be 16-bit little-endian encoded in a byte
  // array.
  private void addBytesToInputBuffer(byte[] inBuffer, int numBytes) {
    int numSamples = numBytes / (2 * numChannels);
    short sample;

    enlargeInputBufferIfNeeded(numSamples);
    int xBuffer = numInputSamples * numChannels;
    for (int xByte = 0; xByte + 1 < numBytes; xByte += 2) {
      sample = (short) ((inBuffer[xByte] & 0xff) | (inBuffer[xByte + 1] << 8));
      inputBuffer[xBuffer++] = sample;
    }
    numInputSamples += numSamples;
  }

  // Remove input samples that we have already processed.
  private void removeInputSamples(int position) {
    int remainingSamples = numInputSamples - position;

    move(inputBuffer, 0, inputBuffer, position, remainingSamples);
    numInputSamples = remainingSamples;
  }

  // Just copy from the array to the output buffer
  private void copyToOutput(short[] samples, int position, int numSamples) {
    enlargeOutputBufferIfNeeded(numSamples);
    move(outputBuffer, numOutputSamples, samples, position, numSamples);
    numOutputSamples += numSamples;
  }

  // Just copy from the input buffer to the output buffer.  Return num samples copied.
  private int copyInputToOutput(int position) {
    int numSamples = remainingInputToCopy;

    if (numSamples > maxRequired) {
      numSamples = maxRequired;
    }
    copyToOutput(inputBuffer, position, numSamples);
    remainingInputToCopy -= numSamples;
    return numSamples;
  }

  // Read data out of the stream.  Sometimes no data will be available, and zero
  // is returned, which is not an error condition.
  public int readFloatFromStream(float[] samples, int maxSamples) {
    int numSamples = numOutputSamples;
    int remainingSamples = 0;

    if (numSamples == 0) {
      return 0;
    }
    if (numSamples > maxSamples) {
      remainingSamples = numSamples - maxSamples;
      numSamples = maxSamples;
    }
    for (int xSample = 0; xSample < numSamples * numChannels; xSample++) {
      samples[xSample++] = (outputBuffer[xSample]) / 32767.0f;
    }
    move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples);
    numOutputSamples = remainingSamples;
    return numSamples;
  }

  // Read short data out of the stream.  Sometimes no data will be available, and zero
  // is returned, which is not an error condition.
  public int readShortFromStream(short[] samples, int maxSamples) {
    int numSamples = numOutputSamples;
    int remainingSamples = 0;

    if (numSamples == 0) {
      return 0;
    }
    if (numSamples > maxSamples) {
      remainingSamples = numSamples - maxSamples;
      numSamples = maxSamples;
    }
    move(samples, 0, outputBuffer, 0, numSamples);
    move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples);
    numOutputSamples = remainingSamples;
    return numSamples;
  }

  // Read unsigned byte data out of the stream.  Sometimes no data will be available, and zero
  // is returned, which is not an error condition.
  public int readBytesFromStream(byte[] outBuffer, int maxBytes) {
    int maxSamples = maxBytes / (2 * numChannels);
    int numSamples = numOutputSamples;
    int remainingSamples = 0;

    if (numSamples == 0 || maxSamples == 0) {
      return 0;
    }
    if (numSamples > maxSamples) {
      remainingSamples = numSamples - maxSamples;
      numSamples = maxSamples;
    }
    for (int xSample = 0; xSample < numSamples * numChannels; xSample++) {
      short sample = outputBuffer[xSample];
      outBuffer[xSample << 1] = (byte) (sample & 0xff);
      outBuffer[(xSample << 1) + 1] = (byte) (sample >> 8);
    }
    move(outputBuffer, 0, outputBuffer, numSamples, remainingSamples);
    numOutputSamples = remainingSamples;
    return 2 * numSamples * numChannels;
  }

  // Force the sonic stream to generate output using whatever data it currently
  // has.  No extra delay will be added to the output, but flushing in the middle of
  // words could introduce distortion.
  public void flushStream() {
    int remainingSamples = numInputSamples;
    float s = speed / pitch;
    float r = rate * pitch;
    int expectedOutputSamples =
        numOutputSamples + (int) ((remainingSamples / s + numPitchSamples) / r + 0.5f);

    // Add enough silence to flush both input and pitch buffers.
    enlargeInputBufferIfNeeded(remainingSamples + 2 * maxRequired);
    for (int xSample = 0; xSample < 2 * maxRequired * numChannels; xSample++) {
      inputBuffer[remainingSamples * numChannels + xSample] = 0;
    }
    numInputSamples += 2 * maxRequired;
    writeShortToStream(null, 0);
    // Throw away any extra samples we generated due to the silence we added.
    if (numOutputSamples > expectedOutputSamples) {
      numOutputSamples = expectedOutputSamples;
    }
    // Empty input and pitch buffers.
    numInputSamples = 0;
    remainingInputToCopy = 0;
    numPitchSamples = 0;
  }

  // Return the number of samples in the output buffer
  public int samplesAvailable() {
    return numOutputSamples;
  }

  // If skip is greater than one, average skip samples together and write them to
  // the down-sample buffer.  If numChannels is greater than one, mix the channels
  // together as we down sample.
  private void downSampleInput(short[] samples, int position, int skip) {
    int numSamples = maxRequired / skip;
    int samplesPerValue = numChannels * skip;
    int value;

    position *= numChannels;
    for (int i = 0; i < numSamples; i++) {
      value = 0;
      for (int j = 0; j < samplesPerValue; j++) {
        value += samples[position + i * samplesPerValue + j];
      }
      value /= samplesPerValue;
      downSampleBuffer[i] = (short) value;
    }
  }

  // Find the best frequency match in the range, and given a sample skip multiple.
  // For now, just find the pitch of the first channel.
  private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
    int bestPeriod = 0;
    int worstPeriod = 255;
    int minDiff = 1;
    int maxDiff = 0;

    position *= numChannels;
    for (int period = minPeriod; period <= maxPeriod; period++) {
      int diff = 0;
      for (int i = 0; i < period; i++) {
        short sVal = samples[position + i];
        short pVal = samples[position + period + i];
        diff += sVal >= pVal ? sVal - pVal : pVal - sVal;
      }
      // Note that the highest number of samples we add into diff will be less than 256, since we
      // skip samples.  Thus, diff is a 24 bit number, and we can safely multiply by numSamples
      // without overflow.
      if (diff * bestPeriod < minDiff * period) {
        minDiff = diff;
        bestPeriod = period;
      }
      if (diff * worstPeriod > maxDiff * period) {
        maxDiff = diff;
        worstPeriod = period;
      }
    }
    this.minDiff = minDiff / bestPeriod;
    this.maxDiff = maxDiff / worstPeriod;

    return bestPeriod;
  }

  // At abrupt ends of voiced words, we can have pitch periods that are better
  // approximated by the previous pitch period estimate.  Try to detect this case.
  private boolean prevPeriodBetter(int minDiff, int maxDiff, boolean preferNewPeriod) {
    if (minDiff == 0 || prevPeriod == 0) {
      return false;
    }
    if (preferNewPeriod) {
      if (maxDiff > minDiff * 3) {
        // Got a reasonable match this period
        return false;
      }
      if (minDiff * 2 <= prevMinDiff * 3) {
        // Mismatch is not that much greater this period
        return false;
      }
    } else {
      if (minDiff <= prevMinDiff) {
        return false;
      }
    }
    return true;
  }

  // Find the pitch period.  This is a critical step, and we may have to try
  // multiple ways to get a good answer.  This version uses AMDF.  To improve
  // speed, we down sample by an integer factor get in the 11KHz range, and then
  // do it again with a narrower frequency range without down sampling
  private int findPitchPeriod(short[] samples, int position, boolean preferNewPeriod) {
    int period;
    int retPeriod;
    int skip = 1;

    if (sampleRate > SONIC_AMDF_FREQ && quality == 0) {
      skip = sampleRate / SONIC_AMDF_FREQ;
    }
    if (numChannels == 1 && skip == 1) {
      period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
    } else {
      downSampleInput(samples, position, skip);
      period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
      if (skip != 1) {
        period *= skip;
        int minP = period - (skip << 2);
        int maxP = period + (skip << 2);
        if (minP < minPeriod) {
          minP = minPeriod;
        }
        if (maxP > maxPeriod) {
          maxP = maxPeriod;
        }
        if (numChannels == 1) {
          period = findPitchPeriodInRange(samples, position, minP, maxP);
        } else {
          downSampleInput(samples, position, 1);
          period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
        }
      }
    }
    if (prevPeriodBetter(minDiff, maxDiff, preferNewPeriod)) {
      retPeriod = prevPeriod;
    } else {
      retPeriod = period;
    }
    prevMinDiff = minDiff;
    prevPeriod = period;
    return retPeriod;
  }

  // Overlap two sound segments, ramp the volume of one down, while ramping the
  // other one from zero up, and add them, storing the result at the output.
  private static void overlapAdd(int numSamples, int numChannels, short[] out, int outPos,
      short[] rampDown, int rampDownPos, short[] rampUp, int rampUpPos) {
    for (int i = 0; i < numChannels; i++) {
      int o = outPos * numChannels + i;
      int u = rampUpPos * numChannels + i;
      int d = rampDownPos * numChannels + i;
      for (int t = 0; t < numSamples; t++) {
        out[o] = (short) ((rampDown[d] * (numSamples - t) + rampUp[u] * t) / numSamples);
        o += numChannels;
        d += numChannels;
        u += numChannels;
      }
    }
  }

  // Overlap two sound segments, ramp the volume of one down, while ramping the
  // other one from zero up, and add them, storing the result at the output.
  private static void overlapAddWithSeparation(int numSamples, int numChannels, int separation,
      short[] out, int outPos, short[] rampDown, int rampDownPos, short[] rampUp, int rampUpPos) {
    for (int i = 0; i < numChannels; i++) {
      int o = outPos * numChannels + i;
      int u = rampUpPos * numChannels + i;
      int d = rampDownPos * numChannels + i;
      for (int t = 0; t < numSamples + separation; t++) {
        if (t < separation) {
          out[o] = (short) (rampDown[d] * (numSamples - t) / numSamples);
          d += numChannels;
        } else if (t < numSamples) {
          out[o] =
              (short) ((rampDown[d] * (numSamples - t) + rampUp[u] * (t - separation))
                  / numSamples);
          d += numChannels;
          u += numChannels;
        } else {
          out[o] = (short) (rampUp[u] * (t - separation) / numSamples);
          u += numChannels;
        }
        o += numChannels;
      }
    }
  }

  // Just move the new samples in the output buffer to the pitch buffer
  private void moveNewSamplesToPitchBuffer(int originalNumOutputSamples) {
    int numSamples = numOutputSamples - originalNumOutputSamples;

    if (numPitchSamples + numSamples > pitchBufferSize) {
      pitchBufferSize += (pitchBufferSize >> 1) + numSamples;
      pitchBuffer = resize(pitchBuffer, pitchBufferSize);
    }
    move(pitchBuffer, numPitchSamples, outputBuffer, originalNumOutputSamples, numSamples);
    numOutputSamples = originalNumOutputSamples;
    numPitchSamples += numSamples;
  }

  // Remove processed samples from the pitch buffer.
  private void removePitchSamples(int numSamples) {
    if (numSamples == 0) {
      return;
    }
    move(pitchBuffer, 0, pitchBuffer, numSamples, numPitchSamples - numSamples);
    numPitchSamples -= numSamples;
  }

  // Change the pitch.  The latency this introduces could be reduced by looking at
  // past samples to determine pitch, rather than future.
  private void adjustPitch(int originalNumOutputSamples) {
    int period;
    int newPeriod;
    int separation;
    int position = 0;

    if (numOutputSamples == originalNumOutputSamples) {
      return;
    }
    moveNewSamplesToPitchBuffer(originalNumOutputSamples);
    while (numPitchSamples - position >= maxRequired) {
      period = findPitchPeriod(pitchBuffer, position, false);
      newPeriod = (int) (period / pitch);
      enlargeOutputBufferIfNeeded(newPeriod);
      if (pitch >= 1.0f) {
        overlapAdd(newPeriod, numChannels, outputBuffer, numOutputSamples, pitchBuffer, position,
            pitchBuffer, position + period - newPeriod);
      } else {
        separation = newPeriod - period;
        overlapAddWithSeparation(period, numChannels, separation, outputBuffer, numOutputSamples,
            pitchBuffer, position, pitchBuffer, position);
      }
      numOutputSamples += newPeriod;
      position += period;
    }
    removePitchSamples(position);
  }

  // Interpolate the new output sample.
  private short interpolate(short[] in, int inPos, int oldSampleRate, int newSampleRate) {
    short left = in[inPos * numChannels];
    short right = in[inPos * numChannels + numChannels];
    int position = newRatePosition * oldSampleRate;
    int leftPosition = oldRatePosition * newSampleRate;
    int rightPosition = (oldRatePosition + 1) * newSampleRate;
    int ratio = rightPosition - position;
    int width = rightPosition - leftPosition;

    return (short) ((ratio * left + (width - ratio) * right) / width);
  }

  // Change the rate.
  private void adjustRate(float rate, int originalNumOutputSamples) {
    int newSampleRate = (int) (sampleRate / rate);
    int oldSampleRate = sampleRate;
    int position;

    // Set these values to help with the integer math
    while (newSampleRate > (1 << 14) || oldSampleRate > (1 << 14)) {
      newSampleRate >>= 1;
      oldSampleRate >>= 1;
    }
    if (numOutputSamples == originalNumOutputSamples) {
      return;
    }
    moveNewSamplesToPitchBuffer(originalNumOutputSamples);
    // Leave at least one pitch sample in the buffer
    for (position = 0; position < numPitchSamples - 1; position++) {
      while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
        enlargeOutputBufferIfNeeded(1);
        for (int i = 0; i < numChannels; i++) {
          outputBuffer[numOutputSamples * numChannels + i] =
              interpolate(pitchBuffer, position + i, oldSampleRate, newSampleRate);
        }
        newRatePosition++;
        numOutputSamples++;
      }
      oldRatePosition++;
      if (oldRatePosition == oldSampleRate) {
        oldRatePosition = 0;
        if (newRatePosition != newSampleRate) {
          System.out.printf("Assertion failed: newRatePosition != newSampleRate\n");
          assert false;
        }
        newRatePosition = 0;
      }
    }
    removePitchSamples(position);
  }

  // Skip over a pitch period, and copy period/speed samples to the output
  private int skipPitchPeriod(short[] samples, int position, float speed, int period) {
    int newSamples;

    if (speed >= 2.0f) {
      newSamples = (int) (period / (speed - 1.0f));
    } else {
      newSamples = period;
      remainingInputToCopy = (int) (period * (2.0f - speed) / (speed - 1.0f));
    }
    enlargeOutputBufferIfNeeded(newSamples);
    overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples, samples, position, samples,
        position + period);
    numOutputSamples += newSamples;
    return newSamples;
  }

  // Insert a pitch period, and determine how much input to copy directly.
  private int insertPitchPeriod(short[] samples, int position, float speed, int period) {
    int newSamples;

    if (speed < 0.5f) {
      newSamples = (int) (period * speed / (1.0f - speed));
    } else {
      newSamples = period;
      remainingInputToCopy = (int) (period * (2.0f * speed - 1.0f) / (1.0f - speed));
    }
    enlargeOutputBufferIfNeeded(period + newSamples);
    move(outputBuffer, numOutputSamples, samples, position, period);
    overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples + period, samples,
        position + period, samples, position);
    numOutputSamples += period + newSamples;
    return newSamples;
  }

  // Resample as many pitch periods as we have buffered on the input.  Return 0 if
  // we fail to resize an input or output buffer.  Also scale the output by the volume.
  private void changeSpeed(float speed) {
    int numSamples = numInputSamples;
    int position = 0;
    int period;
    int newSamples;

    if (numInputSamples < maxRequired) {
      return;
    }
    do {
      if (remainingInputToCopy > 0) {
        newSamples = copyInputToOutput(position);
        position += newSamples;
      } else {
        period = findPitchPeriod(inputBuffer, position, true);
        if (speed > 1.0) {
          newSamples = skipPitchPeriod(inputBuffer, position, speed, period);
          position += period + newSamples;
        } else {
          newSamples = insertPitchPeriod(inputBuffer, position, speed, period);
          position += newSamples;
        }
      }
    } while (position + maxRequired <= numSamples);
    removeInputSamples(position);
  }

  // Resample as many pitch periods as we have buffered on the input. Scale the output by the
  // volume.
  private void processStreamInput() {
    int originalNumOutputSamples = numOutputSamples;
    float s = speed / pitch;
    float r = rate;

    if (!useChordPitch) {
      r *= pitch;
    }
    if (s > 1.00001 || s < 0.99999) {
      changeSpeed(s);
    } else {
      copyToOutput(inputBuffer, 0, numInputSamples);
      numInputSamples = 0;
    }
    if (useChordPitch) {
      if (pitch != 1.0f) {
        adjustPitch(originalNumOutputSamples);
      }
    } else if (r != 1.0f) {
      adjustRate(r, originalNumOutputSamples);
    }
    if (volume != 1.0f) {
      // Adjust output volume.
      scaleSamples(outputBuffer, originalNumOutputSamples,
          numOutputSamples - originalNumOutputSamples, volume);
    }
  }

  // Write floating point data to the input buffer and process it.
  public void writeFloatToStream(float[] samples, int numSamples) {
    addFloatSamplesToInputBuffer(samples, numSamples);
    processStreamInput();
  }

  // Write the data to the input stream, and process it.
  public void writeShortToStream(short[] samples, int numSamples) {
    addShortSamplesToInputBuffer(samples, numSamples);
    processStreamInput();
  }

  // Simple wrapper around sonicWriteFloatToStream that does the unsigned byte to short
  // conversion for you.
  public void writeUnsignedByteToStream(byte[] samples, int numSamples) {
    addUnsignedByteSamplesToInputBuffer(samples, numSamples);
    processStreamInput();
  }

  // Simple wrapper around sonicWriteBytesToStream that does the byte to 16-bit LE conversion.
  public void writeBytesToStream(byte[] inBuffer, int numBytes) {
    addBytesToInputBuffer(inBuffer, numBytes);
    processStreamInput();
  }

  // This is a non-stream oriented interface to just change the speed of a sound sample
  public static int changeFloatSpeed(float[] samples, int numSamples, float speed, float pitch,
      float rate, float volume, boolean useChordPitch, int sampleRate, int numChannels) {
    Sonic stream = new Sonic(sampleRate, numChannels);

    stream.setSpeed(speed);
    stream.setPitch(pitch);
    stream.setRate(rate);
    stream.setVolume(volume);
    stream.setChordPitch(useChordPitch);
    stream.writeFloatToStream(samples, numSamples);
    stream.flushStream();
    numSamples = stream.samplesAvailable();
    stream.readFloatFromStream(samples, numSamples);
    return numSamples;
  }

  /* This is a non-stream oriented interface to just change the speed of a sound sample */
  public int sonicChangeShortSpeed(short[] samples, int numSamples, float speed, float pitch,
      float rate, float volume, boolean useChordPitch, int sampleRate, int numChannels) {
    Sonic stream = new Sonic(sampleRate, numChannels);

    stream.setSpeed(speed);
    stream.setPitch(pitch);
    stream.setRate(rate);
    stream.setVolume(volume);
    stream.setChordPitch(useChordPitch);
    stream.writeShortToStream(samples, numSamples);
    stream.flushStream();
    numSamples = stream.samplesAvailable();
    stream.readShortFromStream(samples, numSamples);
    return numSamples;
  }

}
