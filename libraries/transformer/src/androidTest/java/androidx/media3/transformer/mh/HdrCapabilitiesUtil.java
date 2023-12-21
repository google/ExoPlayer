/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.AndroidTestUtil.skipAndLogIfFormatsUnsupported;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.Context;
import androidx.media3.common.Format;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import java.io.IOException;
import org.json.JSONException;

/** Utility class for checking HDR capabilities. */
public final class HdrCapabilitiesUtil {
  private static final String SKIP_REASON_NO_OPENGL_UNDER_API_29 =
      "OpenGL-based HDR to SDR tone mapping is unsupported below API 29.";
  private static final String SKIP_REASON_NO_YUV = "Device lacks YUV extension support.";

  /**
   * Returns whether the test should be skipped because the device is incapable of tone-mapping the
   * {@code inputFormat} using OpenGL.
   *
   * <p>If the test should be skipped, logs the reason for skipping.
   */
  public static boolean skipAndLogIfOpenGlToneMappingUnsupported(String testId, Format inputFormat)
      throws JSONException, IOException, MediaCodecUtil.DecoderQueryException {
    Context context = getApplicationContext();
    if (Util.SDK_INT < 29) {
      recordTestSkipped(context, testId, SKIP_REASON_NO_OPENGL_UNDER_API_29);
      return true;
    }
    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(context, testId, SKIP_REASON_NO_YUV);
      return true;
    }
    if (skipAndLogIfFormatsUnsupported(context, testId, inputFormat, /* outputFormat= */ null)) {
      return true;
    }
    return false;
  }

  private HdrCapabilitiesUtil() {}
}
