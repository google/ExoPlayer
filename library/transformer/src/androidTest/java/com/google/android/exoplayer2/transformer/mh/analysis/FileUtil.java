/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer.mh.analysis;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.media.MediaFormat;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.DecodeOneFrameUtil;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;

/** Utilities for reading color info from a file. */
public class FileUtil {
  public static void assertFileHasColorTransfer(
      @Nullable String filePath, @C.ColorTransfer int expectedColorTransfer) throws Exception {
    if (Util.SDK_INT < 29) {
      // Skipping on this API version due to lack of support for MediaFormat#getInteger, which is
      // required for MediaFormatUtil#getColorInfo.
      return;
    }
    DecodeOneFrameUtil.decodeOneCacheFileFrame(
        checkNotNull(filePath),
        new DecodeOneFrameUtil.Listener() {
          @Override
          public void onContainerExtracted(MediaFormat mediaFormat) {
            @Nullable ColorInfo extractedColor = MediaFormatUtil.getColorInfo(mediaFormat);
            assertThat(checkNotNull(extractedColor).colorTransfer).isEqualTo(expectedColorTransfer);
          }

          @Override
          public void onFrameDecoded(MediaFormat mediaFormat) {
            @Nullable ColorInfo decodedColor = MediaFormatUtil.getColorInfo(mediaFormat);
            assertThat(checkNotNull(decodedColor).colorTransfer).isEqualTo(expectedColorTransfer);
          }
        },
        /* surface= */ null);
  }

  private FileUtil() {}
}
