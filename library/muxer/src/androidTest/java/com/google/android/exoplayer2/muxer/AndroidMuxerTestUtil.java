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
package com.google.android.exoplayer2.muxer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Utilities for muxer test cases. */
/* package */ final class AndroidMuxerTestUtil {
  private static final String MP4_FILE_ASSET_DIRECTORY = "media/mp4/";
  private static final String DUMP_FILE_OUTPUT_DIRECTORY = "muxerdumps";
  private static final String DUMP_FILE_EXTENSION = "dump";

  private AndroidMuxerTestUtil() {}

  public static String getExpectedDumpFilePath(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
  }

  public static void feedInputDataToMuxer(Context context, Muxer muxer, String inputFileName)
      throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    extractor.setDataSource(
        context.getResources().getAssets().openFd(MP4_FILE_ASSET_DIRECTORY + inputFileName));

    List<Muxer.TrackToken> addedTracks = new ArrayList<>();
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      Muxer.TrackToken trackToken =
          muxer.addTrack(MediaFormatUtil.createFormatFromMediaFormat(extractor.getTrackFormat(i)));
      addedTracks.add(trackToken);
      extractor.selectTrack(i);
    }

    do {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.flags = extractor.getSampleFlags();
      bufferInfo.offset = 0;
      bufferInfo.presentationTimeUs = extractor.getSampleTime();
      int sampleSize = (int) extractor.getSampleSize();
      bufferInfo.size = sampleSize;

      ByteBuffer sampleBuffer = ByteBuffer.allocateDirect(sampleSize);
      extractor.readSampleData(sampleBuffer, /* offset= */ 0);

      sampleBuffer.rewind();

      muxer.writeSampleData(
          addedTracks.get(extractor.getSampleTrackIndex()), sampleBuffer, bufferInfo);
    } while (extractor.advance());

    extractor.release();
  }
}
