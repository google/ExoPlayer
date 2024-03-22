/*
 * Copyright 2024 The Android Open Source Project
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
 *
 */

package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_PORTRAIT_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static androidx.media3.transformer.SequenceEffectTestUtil.SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS;
import static androidx.media3.transformer.SequenceEffectTestUtil.assertBitmapsMatchExpectedAndSave;
import static androidx.media3.transformer.SequenceEffectTestUtil.clippedVideo;
import static androidx.media3.transformer.SequenceEffectTestUtil.createComposition;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceDoesNotSupportHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsOpenGlToneMapping;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for using different {@linkplain Effect effects} for {@link MediaItem MediaItems} in one
 * {@link EditedMediaItemSequence}, with HDR assets.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerSequenceEffectTestWithHdr {

  private static final int EXPORT_HEIGHT = 240;
  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUp() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_withSdrThenHdr() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Pixel 7 Pro, because emulators don't
    // support decoding HDR.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  /**
   * If the first asset in a sequence is HDR, then Transformer will output HDR. However, because SDR
   * to HDR tone-mapping is not implemented, VideoFrameProcessor cannot take a later SDR input asset
   * after already being configured for HDR output.
   */
  @Test
  public void export_withHdrThenSdr_whenHdrEditingSupported_throws() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
        /* outputFormat= */ null);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    @Nullable ExportException expectedException = null;
    try {
      new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
          .build()
          .run(testId, composition);
    } catch (ExportException e) {
      expectedException = e;
    }
    assertThat(expectedException).isNotNull();
    assertThat(checkNotNull(checkNotNull(expectedException).getMessage()))
        .isEqualTo("Video frame processing error");
  }

  /**
   * If the first asset in a sequence is HDR, but HDR editing is not supported, then the first asset
   * will fallback to OpenGL tone-mapping, and configure VideoFrameProcessor for SDR output.
   */
  @Test
  public void export_withHdrThenSdr_whenHdrEditingUnsupported() throws Exception {
    assumeDeviceDoesNotSupportHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
        /* outputFormat= */ null);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Samsung S22 Ultra (US), because emulators don't
    // support decoding HDR, and the Pixel 7 Pro does support HDR editing.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }
}
