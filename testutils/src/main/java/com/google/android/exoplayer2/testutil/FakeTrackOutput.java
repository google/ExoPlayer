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
package com.google.android.exoplayer2.testutil;

import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import junit.framework.Assert;

/**
 * A fake {@link TrackOutput}.
 */
public final class FakeTrackOutput implements TrackOutput, Dumper.Dumpable {

  private final ArrayList<Long> sampleTimesUs;
  private final ArrayList<Integer> sampleFlags;
  private final ArrayList<Integer> sampleStartOffsets;
  private final ArrayList<Integer> sampleEndOffsets;
  private final ArrayList<CryptoData> cryptoDatas;

  private byte[] sampleData;
  public Format format;

  public FakeTrackOutput() {
    sampleData = new byte[0];
    sampleTimesUs = new ArrayList<>();
    sampleFlags = new ArrayList<>();
    sampleStartOffsets = new ArrayList<>();
    sampleEndOffsets = new ArrayList<>();
    cryptoDatas = new ArrayList<>();
  }

  public void clear() {
    sampleData = new byte[0];
    sampleTimesUs.clear();
    sampleFlags.clear();
    sampleStartOffsets.clear();
    sampleEndOffsets.clear();
    cryptoDatas.clear();
  }

  @Override
  public void format(Format format) {
    this.format = format;
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    byte[] newData = new byte[length];
    int bytesAppended = input.read(newData, 0, length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    newData = Arrays.copyOf(newData, bytesAppended);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
    return bytesAppended;
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    byte[] newData = new byte[length];
    data.readBytes(newData, 0, length);
    sampleData = TestUtil.joinByteArrays(sampleData, newData);
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
      CryptoData cryptoData) {
    sampleTimesUs.add(timeUs);
    sampleFlags.add(flags);
    sampleStartOffsets.add(sampleData.length - offset - size);
    sampleEndOffsets.add(sampleData.length - offset);
    cryptoDatas.add(cryptoData);
  }

  public void assertSampleCount(int count) {
    Assert.assertEquals(count, sampleTimesUs.size());
  }

  public void assertSample(int index, byte[] data, long timeUs, int flags, CryptoData cryptoData) {
    byte[] actualData = getSampleData(index);
    MoreAsserts.assertEquals(data, actualData);
    Assert.assertEquals(timeUs, (long) sampleTimesUs.get(index));
    Assert.assertEquals(flags, (int) sampleFlags.get(index));
    Assert.assertEquals(cryptoData, cryptoDatas.get(index));
  }

  public byte[] getSampleData(int index) {
    return Arrays.copyOfRange(sampleData, sampleStartOffsets.get(index),
        sampleEndOffsets.get(index));
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
      if (expected.cryptoDatas.get(i) == null) {
        Assert.assertNull(cryptoDatas.get(i));
      } else {
        Assert.assertEquals(expected.cryptoDatas.get(i), cryptoDatas.get(i));
      }
    }
  }

  @Override
  public void dump(Dumper dumper) {
    dumper.startBlock("format")
        .add("bitrate", format.bitrate)
        .add("id", format.id)
        .add("containerMimeType", format.containerMimeType)
        .add("sampleMimeType", format.sampleMimeType)
        .add("maxInputSize", format.maxInputSize)
        .add("width", format.width)
        .add("height", format.height)
        .add("frameRate", format.frameRate)
        .add("rotationDegrees", format.rotationDegrees)
        .add("pixelWidthHeightRatio", format.pixelWidthHeightRatio)
        .add("channelCount", format.channelCount)
        .add("sampleRate", format.sampleRate)
        .add("pcmEncoding", format.pcmEncoding)
        .add("encoderDelay", format.encoderDelay)
        .add("encoderPadding", format.encoderPadding)
        .add("subsampleOffsetUs", format.subsampleOffsetUs)
        .add("selectionFlags", format.selectionFlags)
        .add("language", format.language)
        .add("drmInitData", format.drmInitData != null ? format.drmInitData.hashCode() : "-");

    dumper.startBlock("initializationData");
    for (int i = 0; i < format.initializationData.size(); i++) {
      dumper.add("data", format.initializationData.get(i));
    }
    dumper.endBlock().endBlock();

    dumper.add("sample count", sampleTimesUs.size());

    for (int i = 0; i < sampleTimesUs.size(); i++) {
      dumper.startBlock("sample " + i)
          .add("time", sampleTimesUs.get(i))
          .add("flags", sampleFlags.get(i))
          .add("data", getSampleData(i));
      CryptoData cryptoData = cryptoDatas.get(i);
      if (cryptoData != null) {
        dumper.add("crypto mode", cryptoData.cryptoMode);
        dumper.add("encryption key", cryptoData.encryptionKey);
      }
      dumper.endBlock();
    }
  }

}
