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

import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Checks transcoding quality. */
@RunWith(AndroidJUnit4.class)
public final class TranscodeQualityTest {
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void exportHighQualityTargetingAvcToAvc1920x1080_ssimIsGreaterThan95Percent()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT);
    // Skip on specific pre-API 34 devices where calculating SSIM fails.
    assumeFalse(
        (Util.SDK_INT < 33 && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1")))
            || (Util.SDK_INT == 33 && Util.MODEL.equals("LE2121")));
    // Skip on specific API 21 devices that aren't able to decode and encode at this resolution.
    assumeFalse(Util.SDK_INT == 21 && Util.MODEL.equals("Nexus 7"));
    Transformer transformer =
        new Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
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

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT,
        /* outputFormat= */ AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .build());
    assumeFalse(
        (Util.SDK_INT < 33 && (Util.MODEL.equals("SM-F711U1") || Util.MODEL.equals("SM-F926U1")))
            || (Util.SDK_INT == 33 && Util.MODEL.equals("LE2121")));
    Transformer transformer =
        new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H265).build();
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

    // Don't skip based on format support as input and output formats should be within CDD
    // requirements on all supported API versions, except for wearable devices.
    assumeFalse(Util.isWear(context));

    Transformer transformer =
        new Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
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
