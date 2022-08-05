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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.transformer.DefaultEncoderFactory;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.TransformationTestResult;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.transformer.VideoEncoderSettings;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Checks transcoding quality. */
@RunWith(AndroidJUnit4.class)
public final class TranscodeQualityTest {
  @Test
  public void transformHighQualityTargetingAvcToAvc1920x1080_ssimIsGreaterThan95Percent()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = "transformHighQualityTargetingAvcToAvc1920x1080_ssim";

    if (AndroidTestUtil.skipAndLogIfInsufficientCodecSupport(
        context,
        testId,
        /* decodingFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* encodingFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT)) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build())
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        new VideoEncoderSettings.Builder()
                            .setEnableHighQualityTargeting(true)
                            .build())
                    .build())
            .setRemoveAudio(true)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(
                testId,
                MediaItem.fromUri(
                    Uri.parse(AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING)));

    if (result.ssim != TransformationTestResult.SSIM_UNSET) {
      assertThat(result.ssim).isGreaterThan(0.90);
    }
  }

  @Test
  public void transcodeAvcToHevc_ssimIsGreaterThan90Percent() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = "transcodeAvcToHevc_ssim";

    if (AndroidTestUtil.skipAndLogIfInsufficientCodecSupport(
        context,
        testId,
        /* decodingFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* encodingFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build())) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H265).build())
            .setRemoveAudio(true)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(
                testId,
                MediaItem.fromUri(
                    Uri.parse(AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING)));

    if (result.ssim != TransformationTestResult.SSIM_UNSET) {
      assertThat(result.ssim).isGreaterThan(0.90);
    }
  }

  @Test
  public void transcodeAvcToAvc320x240_ssimIsGreaterThan90Percent() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = "transcodeAvcToAvc320x240_ssim";

    // Note: We never skip this test as the input and output formats should be within CDD
    // requirements on all supported API versions.

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build())
            .setEncoderFactory(new AndroidTestUtil.ForceEncodeEncoderFactory(context))
            .setRemoveAudio(true)
            .build();

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(
                testId,
                MediaItem.fromUri(
                    Uri.parse(
                        AndroidTestUtil
                            .MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING)));

    if (result.ssim != TransformationTestResult.SSIM_UNSET) {
      assertThat(result.ssim).isGreaterThan(0.90);
    }
  }
}
