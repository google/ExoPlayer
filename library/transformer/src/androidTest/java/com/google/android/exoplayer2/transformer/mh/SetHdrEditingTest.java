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
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_1_SECOND_HDR10_VIDEO_SDR_CONTAINER;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.android.exoplayer2.transformer.mh.analysis.FileUtil.assertFileHasColorTransfer;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H265;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.EncoderUtil;
import com.google.android.exoplayer2.transformer.TransformationException;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.TransformationTestResult;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * TransformationRequest.Builder#experimental_setEnableHdrEditing HDR frame edit}.
 */
@RunWith(AndroidJUnit4.class)
public class SetHdrEditingTest {
  public static final String TAG = "SetHdrEditingTest";
  private static final ColorInfo HDR10_DEFAULT_COLOR_INFO =
      new ColorInfo(
          C.COLOR_SPACE_BT2020,
          C.COLOR_RANGE_LIMITED,
          C.COLOR_TRANSFER_ST2084,
          /* hdrStaticInfo= */ null);

  @Test
  public void transform_noRequestedTranscode_hdr10File_transformsOrThrows() throws Exception {
    String testId = "transform_noRequestedTranscode_hdr10File_transformsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().experimental_setEnableHdrEditing(true).build())
            .build();

    try {
      TransformationTestResult transformationTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_4_SECOND_HDR10)));
      Log.i(TAG, "Transformed.");
      assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_ST2084);
      return;
    } catch (TransformationException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(TransformationException.ERROR_CODE_HDR_ENCODING_UNSUPPORTED);
    }
  }

  @Test
  public void transformAndTranscode_hdr10File_whenHdrEditingIsSupported_transforms()
      throws Exception {
    String testId = "transformAndTranscode_hdr10File_whenHdrEditingIsSupported_transforms";
    Context context = ApplicationProvider.getApplicationContext();
    if (!deviceSupportsHdrEditing(VIDEO_H265, HDR10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device lacks HDR10 editing support.");
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .experimental_setEnableHdrEditing(true)
                    .setRotationDegrees(180)
                    .build())
            .build();

    TransformationTestResult transformationTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_4_SECOND_HDR10)));
    assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_ST2084);
  }

  @Test
  public void transformAndTranscode_hdr10File_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    String testId = "transformAndTranscode_hdr10File_whenHdrEditingUnsupported_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();
    if (deviceSupportsHdrEditing(VIDEO_H265, HDR10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device supports HDR10 editing.");
      return;
    }

    AtomicBoolean isFallbackListenerInvoked = new AtomicBoolean();
    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .experimental_setEnableHdrEditing(true)
                    .setRotationDegrees(180)
                    .build())
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    isFallbackListenerInvoked.set(true);
                    assertThat(originalTransformationRequest.enableRequestSdrToneMapping).isFalse();
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.enableRequestSdrToneMapping);
                  }
                })
            .build();

    try {
      TransformationTestResult transformationTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_4_SECOND_HDR10)));
      Log.i(TAG, "Tone mapped.");
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (TransformationException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isAnyOf(
              TransformationException.ERROR_CODE_HDR_ENCODING_UNSUPPORTED,
              TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
      assertThat(isFallbackListenerInvoked.get()).isFalse();
      return;
    }
  }

  @Test
  public void transformUnexpectedColorInfo() throws Exception {
    String testId = "transformUnexpectedColorInfo";
    Context context = ApplicationProvider.getApplicationContext();
    if (Util.SDK_INT < 29) {
      recordTestSkipped(
          context,
          testId,
          /* reason= */ "API version lacks support for MediaFormat#getInteger(String, int).");
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().experimental_setEnableHdrEditing(true).build())
            .build();
    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(
            testId,
            MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_1_SECOND_HDR10_VIDEO_SDR_CONTAINER)));
  }

  private static boolean deviceSupportsHdrEditing(String mimeType, ColorInfo colorInfo) {
    return !EncoderUtil.getSupportedEncoderNamesForHdrEditing(mimeType, colorInfo).isEmpty();
  }
}
