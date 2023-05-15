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

package androidx.media3.transformer.mh;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Checks transcoding quality. */
@RunWith(AndroidJUnit4.class)
public final class TranscodeQualityTest {
  @Test
  public void exportHighQualityTargetingAvcToAvc1920x1080_ssimIsGreaterThan95Percent()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = "transformHighQualityTargetingAvcToAvc1920x1080_ssim";

    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT)) {
      return;
    }
    // TODO: b/239983127 - Remove this test skip on these devices.
    assumeTrue(!Util.MODEL.equals("SM-F711U1") && !Util.MODEL.equals("SM-F926U1"));

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build())
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        new VideoEncoderSettings.Builder()
                            .experimentalSetEnableHighQualityTargeting(true)
                            .build())
                    .build())
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(
            Uri.parse(AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);

    if (result.ssim != ExportTestResult.SSIM_UNSET) {
      assertThat(result.ssim).isGreaterThan(0.90);
    }
  }

  @Test
  public void transcodeAvcToHevc_ssimIsGreaterThan90Percent() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = "transcodeAvcToHevc_ssim";

    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build())) {
      return;
    }
    // TODO: b/239983127 - Remove this test skip on these devices.
    assumeTrue(!Util.MODEL.equals("SM-F711U1") && !Util.MODEL.equals("SM-F926U1"));

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H265).build())
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(
            Uri.parse(AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);

    if (result.ssim != ExportTestResult.SSIM_UNSET) {
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
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(
            Uri.parse(
                AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);

    if (result.ssim != ExportTestResult.SSIM_UNSET) {
      assertThat(result.ssim).isGreaterThan(0.90);
    }
  }
}
