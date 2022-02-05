/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Mp4vChunkPeekerTest {

  private ByteBuffer makeSequence() {
    return DataHelper.appendNal(AviExtractor.allocate(32), Mp4vChunkHandler.SEQUENCE_START_CODE);
  }

  @Test
  public void peek_givenNoSequence() throws IOException {
    ByteBuffer byteBuffer = makeSequence();
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    final Mp4vChunkHandler mp4vChunkPeeker = new Mp4vChunkHandler(0, fakeTrackOutput,
        new ChunkClock(1_000_000L, 24), formatBuilder);
    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(1f, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  @Test
  public void peek_givenAspectRatio() throws IOException {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] bytes = TestUtil.getByteArray(context, "extractordumps/avi/mp4v_sequence.dump");
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(bytes).build();
    final Mp4vChunkHandler mp4vChunkPeeker = new Mp4vChunkHandler(0, fakeTrackOutput,
        new ChunkClock(1_000_000L, 24), formatBuilder);

    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(1.2121212, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }

  @Test
  public void peek_givenCustomAspectRatio() throws IOException {
    ByteBuffer byteBuffer = makeSequence();
    byteBuffer.putInt(0x5555);
    DataHelper.appendNal(byteBuffer, (byte) Mp4vChunkHandler.LAYER_START_CODE);

    BitBuffer bitBuffer = new BitBuffer();
    bitBuffer.push(false); //random_accessible_vol
    bitBuffer.push(8, 8); //video_object_type_indication
    bitBuffer.push(true); // is_object_layer_identifier
    bitBuffer.push(7, 7); // video_object_layer_verid, video_object_layer_priority
    bitBuffer.push(4, Mp4vChunkHandler.Extended_PAR);
    bitBuffer.push(8, 16);
    bitBuffer.push(8, 9);
    final byte bytes[] = bitBuffer.getBytes();
    byteBuffer.put(bytes);

    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    final Format.Builder formatBuilder = new Format.Builder();
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array())
        .build();
    final Mp4vChunkHandler mp4vChunkPeeker = new Mp4vChunkHandler(0, fakeTrackOutput,
        new ChunkClock(1_000_000L, 24), formatBuilder);
    mp4vChunkPeeker.peek(input, (int) input.getLength());
    Assert.assertEquals(16f/9f, mp4vChunkPeeker.pixelWidthHeightRatio, 0.01);
  }
}