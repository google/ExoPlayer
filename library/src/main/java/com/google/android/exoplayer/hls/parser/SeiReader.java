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
import com.google.android.exoplayer.mp4.Mp4Util;
import com.google.android.exoplayer.text.eia608.Eia608Parser;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.annotation.SuppressLint;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * Parses a SEI data from H.264 frames and extracts samples with closed captions data.
 *
 * TODO: Technically, we shouldn't allow a sample to be read from the queue until we're sure that
 * a sample with an earlier timestamp won't be added to it.
 */
/* package */ class SeiReader extends SampleQueue implements Comparator<Sample> {

  // SEI data, used for Closed Captions.
  private static final int NAL_UNIT_TYPE_SEI = 6;

  private final ParsableByteArray seiBuffer;
  private final TreeSet<Sample> internalQueue;

  public SeiReader(SamplePool samplePool) {
    super(samplePool);
    setMediaFormat(MediaFormat.createEia608Format());
    seiBuffer = new ParsableByteArray();
    internalQueue = new TreeSet<Sample>(this);
  }

  @SuppressLint("InlinedApi")
  public void read(byte[] data, int length, long pesTimeUs) {
    seiBuffer.reset(data, length);
    while (seiBuffer.bytesLeft() > 0) {
      int currentOffset = seiBuffer.getPosition();
      int seiOffset = Mp4Util.findNalUnit(data, currentOffset, length, NAL_UNIT_TYPE_SEI);
      if (seiOffset == length) {
        return;
      }
      seiBuffer.skip(seiOffset + 4 - currentOffset);
      int ccDataSize = Eia608Parser.parseHeader(seiBuffer);
      if (ccDataSize > 0) {
        addSample(Sample.TYPE_MISC, seiBuffer, ccDataSize, pesTimeUs, true);
      }
    }
  }

  @Override
  public int compare(Sample first, Sample second) {
    // Note - We don't expect samples to have identical timestamps.
    return first.timeUs <= second.timeUs ? -1 : 1;
  }

  @Override
  protected synchronized Sample internalPeekSample() {
    return internalQueue.isEmpty() ? null : internalQueue.first();
  }

  @Override
  protected synchronized Sample internalPollSample() {
    return internalQueue.pollFirst();
  }

  @Override
  protected synchronized void internalQueueSample(Sample sample) {
    internalQueue.add(sample);
  }

}
