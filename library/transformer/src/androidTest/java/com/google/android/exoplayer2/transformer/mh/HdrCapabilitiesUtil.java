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
package com.google.android.exoplayer2.transformer.mh;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.skipAndLogIfFormatsUnsupported;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.transformer.EncoderUtil;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.io.IOException;
import org.json.JSONException;
import org.junit.AssumptionViolatedException;

/** Utility class for checking HDR capabilities. */
public final class HdrCapabilitiesUtil {
  private static final String SKIP_REASON_NO_OPENGL_UNDER_API_29 =
      "OpenGL-based HDR to SDR tone mapping is unsupported below API 29.";
  private static final String SKIP_REASON_NO_YUV = "Device lacks YUV extension support.";

  /**
   * Assumes that the device supports OpenGL tone-mapping for the {@code inputFormat}.
   *
   * @throws AssumptionViolatedException if the device does not support OpenGL tone-mapping.
   */
  public static void assumeDeviceSupportsOpenGlToneMapping(String testId, Format inputFormat)
      throws JSONException, IOException, MediaCodecUtil.DecoderQueryException {
    Context context = getApplicationContext();
    if (Util.SDK_INT < 29) {
      recordTestSkipped(context, testId, SKIP_REASON_NO_OPENGL_UNDER_API_29);
      throw new AssumptionViolatedException(SKIP_REASON_NO_OPENGL_UNDER_API_29);
    }
    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(context, testId, SKIP_REASON_NO_YUV);
      throw new AssumptionViolatedException(SKIP_REASON_NO_YUV);
    }
    if (skipAndLogIfFormatsUnsupported(context, testId, inputFormat, /* outputFormat= */ null)) {
      throw new AssumptionViolatedException("Input format is unsupported: " + inputFormat);
    }
  }

  /**
   * Assumes that the device supports HDR editing for the given {@code colorInfo}.
   *
   * @throws AssumptionViolatedException if the device does not support HDR editing.
   */
  public static void assumeDeviceSupportsHdrEditing(String testId, Format format)
      throws JSONException, IOException {
    checkState(ColorInfo.isTransferHdr(format.colorInfo));
    if (EncoderUtil.getSupportedEncodersForHdrEditing(format.sampleMimeType, format.colorInfo)
        .isEmpty()) {
      String skipReason = "No HDR editing support for " + format.colorInfo;
      recordTestSkipped(getApplicationContext(), testId, skipReason);
      throw new AssumptionViolatedException(skipReason);
    }
  }

  /**
   * Assumes that the device does not support HDR editing for the given {@code colorInfo}.
   *
   * @throws AssumptionViolatedException if the device does support HDR editing.
   */
  public static void assumeDeviceDoesNotSupportHdrEditing(String testId, Format format)
      throws JSONException, IOException {
    checkState(ColorInfo.isTransferHdr(format.colorInfo));
    if (!EncoderUtil.getSupportedEncodersForHdrEditing(format.sampleMimeType, format.colorInfo)
        .isEmpty()) {
      String skipReason = "HDR editing support for " + format.colorInfo;
      recordTestSkipped(getApplicationContext(), testId, skipReason);
      throw new AssumptionViolatedException(skipReason);
    }
  }

  private HdrCapabilitiesUtil() {}
}
