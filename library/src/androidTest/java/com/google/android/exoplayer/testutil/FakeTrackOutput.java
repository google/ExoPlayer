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
package com.google.android.exoplayer.testutil;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.test.MoreAsserts;

import junit.framework.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A fake {@link TrackOutput}.
 */
public final class FakeTrackOutput implements TrackOutput {

  private final ArrayList<Long> sampleTimesUs;
  private final ArrayList<Integer> sampleFlags;
  private final ArrayList<Integer> sampleStartOffsets;
  private final ArrayList<Integer> sampleEndOffsets;
  private final ArrayList<byte[]> sampleEncryptionKeys;

  private byte[] sampleData;
  public MediaFormat format;

  public FakeTrackOutput() {
    sampleData = new byte[0];
    sampleTimesUs = new ArrayList<>();
    sampleFlags = new ArrayList<>();
    sampleStartOffsets = new ArrayList<>();
    sampleEndOffsets = new ArrayList<>();
    sampleEncryptionKeys = new ArrayList<>();
  }

  @Override
  public void format(MediaFormat format) {
    this.format = format;
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    byte[] newData = new byte[length];
    input.readFully(newData, 0, length);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
    return length;
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    byte[] newData = new byte[length];
    data.readBytes(newData, 0, length);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    sampleTimesUs.add(timeUs);
    sampleFlags.add(flags);
    sampleStartOffsets.add(sampleData.length - offset - size);
    sampleEndOffsets.add(sampleData.length - offset);
    sampleEncryptionKeys.add(encryptionKey);
  }

  public void assertSampleCount(int count) {
    Assert.assertEquals(count, sampleTimesUs.size());
  }

  public void assertSample(int index, byte[] data, long timeUs, int flags, byte[] encryptionKey) {
    byte[] actualData = Arrays.copyOfRange(sampleData, sampleStartOffsets.get(index),
        sampleEndOffsets.get(index));
    MoreAsserts.assertEquals(data, actualData);
    Assert.assertEquals(timeUs, (long) sampleTimesUs.get(index));
    Assert.assertEquals(flags, (int) sampleFlags.get(index));
    byte[] sampleEncryptionKey = sampleEncryptionKeys.get(index);
    if (encryptionKey == null) {
      Assert.assertEquals(null, sampleEncryptionKey);
    } else {
      MoreAsserts.assertEquals(encryptionKey, sampleEncryptionKey);
    }
  }

  public void assertEquals(FakeTrackOutput expected) {
    Assert.assertEquals(expected.format, format);
    Assert.assertEquals(expected.sampleTimesUs.size(), sampleTimesUs.size());
    MoreAsserts.assertEquals(expected.sampleData, sampleData);
    for (int i = 0; i < sampleTimesUs.size(); i++) {
      Assert.assertEquals(expected.sampleTimesUs.get(i), sampleTimesUs.get(i));
      Assert.assertEquals(expected.sampleFlags.get(i), sampleFlags.get(i));
      Assert.assertEquals(expected.sampleStartOffsets.get(i), sampleStartOffsets.get(i));
      Assert.assertEquals(expected.sampleEndOffsets.get(i), sampleEndOffsets.get(i));
      if (expected.sampleEncryptionKeys.get(i) == null) {
        Assert.assertNull(sampleEncryptionKeys.get(i));
      } else {
        MoreAsserts.assertEquals(expected.sampleEncryptionKeys.get(i), sampleEncryptionKeys.get(i));
      }
    }
  }

}
