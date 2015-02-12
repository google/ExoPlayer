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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.annotation.SuppressLint;

/**
 * Parses ID3 data and extracts individual text information frames.
 */
/* package */ class Id3Reader extends PesPayloadReader {

  private Sample currentSample;

  public Id3Reader(SamplePool samplePool) {
    super(samplePool);
    setMediaFormat(MediaFormat.createId3Format());
  }

  @SuppressLint("InlinedApi")
  @Override
  public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
    if (startOfPacket) {
      currentSample = getSample(Sample.TYPE_MISC);
      currentSample.timeUs = pesTimeUs;
      currentSample.isKeyframe = true;
    }
    if (currentSample != null) {
      addToSample(currentSample, data, data.bytesLeft());
    }
  }

  @Override
  public void packetFinished() {
    addSample(currentSample);
    currentSample = null;
  }

  @Override
  public void release() {
    super.release();
    if (currentSample != null) {
      recycle(currentSample);
      currentSample = null;
    }
  }

}
