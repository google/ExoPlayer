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
package androidx.media3.muxer;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.util.Pair;
import androidx.media3.common.Format;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import java.nio.ByteBuffer;

/** Utilities for muxer test cases. */
/* package */ class MuxerTestUtil {
  private static final byte[] FAKE_CSD_0 =
      BaseEncoding.base16().decode("0000000167F4000A919B2BF3CB3640000003004000000C83C4896580");
  private static final byte[] FAKE_CSD_1 = BaseEncoding.base16().decode("0000000168EBE3C448");
  private static final byte[] FAKE_H264_SAMPLE =
      BaseEncoding.base16()
          .decode(
              "0000000167F4000A919B2BF3CB3640000003004000000C83C48965800000000168EBE3C448000001658884002BFFFEF5DBF32CAE4A43FF");

  private static final String DUMP_FILE_OUTPUT_DIRECTORY = "muxerdumps";
  private static final String DUMP_FILE_EXTENSION = "dump";

  public static String getExpectedDumpFilePath(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
  }

  public static Format getFakeVideoFormat() {
    return new Format.Builder()
        .setSampleMimeType("video/avc")
        .setWidth(12)
        .setHeight(10)
        .setInitializationData(ImmutableList.of(FAKE_CSD_0, FAKE_CSD_1))
        .build();
  }

  public static Format getFakeAudioFormat() {
    return new Format.Builder()
        .setSampleMimeType("audio/mp4a-latm")
        .setSampleRate(40000)
        .setChannelCount(2)
        .build();
  }

  public static Pair<ByteBuffer, BufferInfo> getFakeSampleAndSampleInfo(long presentationTimeUs) {
    ByteBuffer sampleDirectBuffer = ByteBuffer.allocateDirect(FAKE_H264_SAMPLE.length);
    sampleDirectBuffer.put(FAKE_H264_SAMPLE);
    sampleDirectBuffer.rewind();

    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.presentationTimeUs = presentationTimeUs;
    bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
    bufferInfo.size = FAKE_H264_SAMPLE.length;

    return new Pair<>(sampleDirectBuffer, bufferInfo);
  }

  private MuxerTestUtil() {}
}
