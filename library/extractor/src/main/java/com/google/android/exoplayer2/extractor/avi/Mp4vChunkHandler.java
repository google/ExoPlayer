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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import java.io.IOException;

/**
 * Peeks an MP4V stream looking for pixelWidthHeightRatio data
 */
public class Mp4vChunkHandler extends NalChunkPeeker {
  @VisibleForTesting
  static final byte SEQUENCE_START_CODE = (byte)0xb0;
  @VisibleForTesting
  static final int LAYER_START_CODE = 0x20;
  private static final float[] ASPECT_RATIO = {0f, 1f, 12f/11f, 10f/11f, 16f/11f, 40f/33f};
  @VisibleForTesting
  static final int Extended_PAR = 0xf;

  private final Format.Builder formatBuilder;

  @VisibleForTesting()
  float pixelWidthHeightRatio = 1f;

  public Mp4vChunkHandler(int id, @NonNull TrackOutput trackOutput,
      @NonNull ChunkClock clock, @NonNull Format.Builder formatBuilder) {
    super(id, trackOutput, clock, 5);
    this.formatBuilder = formatBuilder;
  }

  @Override
  boolean skip(byte nalType) {
    return nalType != SEQUENCE_START_CODE;
  }

  @VisibleForTesting
  void processLayerStart(int nalTypeOffset) {
    @NonNull final ParsableNalUnitBitArray in = new ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, pos);
    in.skipBit(); // random_accessible_vol
    in.skipBits(8); // video_object_type_indication
    boolean is_object_layer_identifier = in.readBit();
    if (is_object_layer_identifier) {
      in.skipBits(7); // video_object_layer_verid, video_object_layer_priority
    }
    int aspect_ratio_info = in.readBits(4);
    final float aspectRatio;
    if (aspect_ratio_info == Extended_PAR) {
      float par_width = (float)in.readBits(8);
      float par_height = (float)in.readBits(8);
      aspectRatio = par_width / par_height;
    } else {
      aspectRatio = ASPECT_RATIO[aspect_ratio_info];
    }
    if (aspectRatio != pixelWidthHeightRatio) {
      trackOutput.format(formatBuilder.setPixelWidthHeightRatio(aspectRatio).build());
      pixelWidthHeightRatio = aspectRatio;
    }
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {
    while (true) {
      if ((buffer[nalTypeOffset] & 0xf0) == LAYER_START_CODE) {
        seekNextNal(input, nalTypeOffset);
        processLayerStart(nalTypeOffset);
        break;
      }
      nalTypeOffset = seekNextNal(input, nalTypeOffset);
      if (nalTypeOffset < 0) {
        break;
      }
      compact();
    }
  }
}
