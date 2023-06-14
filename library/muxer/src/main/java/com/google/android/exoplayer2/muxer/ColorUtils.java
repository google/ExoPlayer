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

import android.media.MediaFormat;
import com.google.common.collect.ImmutableList;

/**
 * Utilities for color information.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class ColorUtils {
  // The constants are defined as per ISO/IEC 29199-2 (mentioned in MP4 spec ISO/IEC 14496-12:
  // 8.5.2.3).

  private static final short TRANSFER_SMPTE170_M = 1; // Main; also 6, 14 and 15
  private static final short TRANSFER_UNSPECIFIED = 2;
  private static final short TRANSFER_GAMMA22 = 4;
  private static final short TRANSFER_GAMMA28 = 5;
  private static final short TRANSFER_SMPTE240_M = 7;
  private static final short TRANSFER_LINEAR = 8;
  private static final short TRANSFER_OTHER = 9; // Also 10
  private static final short TRANSFER_XV_YCC = 11;
  private static final short TRANSFER_BT1361 = 12;
  private static final short TRANSFER_SRGB = 13;
  private static final short TRANSFER_ST2084 = 16;
  private static final short TRANSFER_ST428 = 17;
  private static final short TRANSFER_HLG = 18;

  // MediaFormat contains three color-related fields: "standard", "transfer" and "range". The color
  // standard maps to "primaries" and "matrix" in the "colr" box, while "transfer" and "range" are
  // mapped to a single value each (although for "transfer", it's still not the same enum values).
  private static final short PRIMARIES_BT709_5 = 1;
  private static final short PRIMARIES_UNSPECIFIED = 2;
  private static final short PRIMARIES_BT601_6_625 = 5;
  private static final short PRIMARIES_BT601_6_525 = 6; // It's also 7?
  private static final short PRIMARIES_GENERIC_FILM = 8;
  private static final short PRIMARIES_BT2020 = 9;
  private static final short PRIMARIES_BT470_6_M = 4;

  private static final short MATRIX_UNSPECIFIED = 2;
  private static final short MATRIX_BT709_5 = 1;
  private static final short MATRIX_BT601_6 = 6;
  private static final short MATRIX_SMPTE240_M = 7;
  private static final short MATRIX_BT2020 = 9;
  private static final short MATRIX_BT2020_CONSTANT = 10;
  private static final short MATRIX_BT470_6_M = 4;

  /**
   * Map from {@link MediaFormat} standards to MP4 primaries and matrix indices.
   *
   * <p>The i-th element corresponds to a {@link MediaFormat} value of i.
   */
  public static final ImmutableList<ImmutableList<Short>>
      MEDIAFORMAT_STANDARD_TO_PRIMARIES_AND_MATRIX =
          ImmutableList.of(
              ImmutableList.of(PRIMARIES_UNSPECIFIED, MATRIX_UNSPECIFIED), // Unspecified
              ImmutableList.of(PRIMARIES_BT709_5, MATRIX_BT709_5), // BT709
              ImmutableList.of(PRIMARIES_BT601_6_625, MATRIX_BT601_6), // BT601_625
              ImmutableList.of(PRIMARIES_BT601_6_625, MATRIX_BT709_5), // BT601_625_Unadjusted
              ImmutableList.of(PRIMARIES_BT601_6_525, MATRIX_BT601_6), // BT601_525
              ImmutableList.of(PRIMARIES_BT601_6_525, MATRIX_SMPTE240_M), // BT601_525_Unadjusted
              ImmutableList.of(PRIMARIES_BT2020, MATRIX_BT2020), // BT2020
              ImmutableList.of(PRIMARIES_BT2020, MATRIX_BT2020_CONSTANT), // BT2020Constant
              ImmutableList.of(PRIMARIES_BT470_6_M, MATRIX_BT470_6_M), // BT470M
              ImmutableList.of(PRIMARIES_GENERIC_FILM, MATRIX_BT2020) // Film
              );

  /**
   * Map from {@link MediaFormat} standards to MP4 transfer indices.
   *
   * <p>The i-th element corresponds to a {@link MediaFormat} value of i.
   */
  public static final ImmutableList<Short> MEDIAFORMAT_TRANSFER_TO_MP4_TRANSFER =
      ImmutableList.of(
          TRANSFER_UNSPECIFIED, // Unspecified
          TRANSFER_LINEAR, // Linear
          TRANSFER_SRGB, // SRGB
          TRANSFER_SMPTE170_M, // SMPTE_170M
          TRANSFER_GAMMA22, // Gamma22
          TRANSFER_GAMMA28, // Gamma28
          TRANSFER_ST2084, // ST2084
          TRANSFER_HLG // HLG
          );

  private ColorUtils() {}
}
