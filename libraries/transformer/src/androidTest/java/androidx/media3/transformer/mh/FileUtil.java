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

package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.DecodeOneFrameUtil;
import java.io.IOException;

/** Utilities for accessing details of media files. */
/* package */ class FileUtil {

  /**
   * Assert that the file has a certain color transfer, if supported on this device.
   *
   * <p>This will silently pass if under API 24, or if decoding this file is not supported on this
   * device.
   *
   * @param filePath The path of the input file.
   * @param expectedColorTransfer The expected {@link C.ColorTransfer} for the input file.
   * @throws IOException If extractor or codec creation fails.
   */
  public static void maybeAssertFileHasColorTransfer(
      @Nullable String filePath, @C.ColorTransfer int expectedColorTransfer) throws IOException {
    if (Util.SDK_INT < 24) {
      // MediaFormat#KEY_COLOR_TRANSFER unsupported before API 24.
      return;
    }
    DecodeOneFrameUtil.Listener listener =
        new DecodeOneFrameUtil.Listener() {
          @Override
          public void onContainerExtracted(MediaFormat mediaFormat) {
            @Nullable ColorInfo extractedColorInfo = MediaFormatUtil.getColorInfo(mediaFormat);
            assertColorInfoHasTransfer(extractedColorInfo, expectedColorTransfer);
          }

          @Override
          public void onFrameDecoded(MediaFormat mediaFormat) {
            @Nullable ColorInfo decodedColorInfo = MediaFormatUtil.getColorInfo(mediaFormat);
            assertColorInfoHasTransfer(decodedColorInfo, expectedColorTransfer);
          }
        };

    try {
      DecodeOneFrameUtil.decodeOneCacheFileFrame(
          checkNotNull(filePath), listener, /* surface= */ null);
    } catch (UnsupportedOperationException e) {
      if (e.getMessage() != null
          && e.getMessage().equals(DecodeOneFrameUtil.NO_DECODER_SUPPORT_ERROR_STRING)) {
        return;
      } else {
        throw e;
      }
    }
  }

  private static void assertColorInfoHasTransfer(
      @Nullable ColorInfo colorInfo, @C.ColorTransfer int expectedColorTransfer) {
    @C.ColorTransfer
    int actualColorTransfer = colorInfo == null ? C.COLOR_TRANSFER_SDR : colorInfo.colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(expectedColorTransfer);
  }

  private FileUtil() {}
}
