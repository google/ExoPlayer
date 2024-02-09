/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.exoplayer2.testutil;

import static com.google.android.exoplayer2.testutil.TestUtil.retrieveTrackFormat;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.video.ColorInfo;

/** Utilities for accessing details of media files. */
public final class FileUtil {

  /**
   * Returns {@link C.ColorTransfer} information from the media file, or {@link
   * C#COLOR_TRANSFER_SDR} if the information can not be found.
   */
  public static @C.ColorTransfer int retrieveColorTransfer(Context context, String filePath) {
    Format videoTrackFormat = retrieveTrackFormat(context, filePath, C.TRACK_TYPE_VIDEO);
    @Nullable ColorInfo colorInfo = videoTrackFormat.colorInfo;
    return colorInfo == null || colorInfo.colorTransfer == Format.NO_VALUE
        ? C.COLOR_TRANSFER_SDR
        : colorInfo.colorTransfer;
  }

  private FileUtil() {}
}
