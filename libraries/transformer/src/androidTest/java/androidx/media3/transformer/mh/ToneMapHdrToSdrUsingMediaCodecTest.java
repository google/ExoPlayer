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

import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC HDR to SDR tone mapping edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingMediaCodecTest {
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_toneMapNoRequestedTranscode_hdr10File_toneMapsOrThrows() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
        /* outputFormat= */ null);

    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Tone mapping flag shouldn't change in fallback when tone mapping is
                    // requested.
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(fallbackTransformationRequest.hdrMode);
                  }
                })
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10)))
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
            .build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, composition);
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null
          && (Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping HDR is not supported on this device.")
              || Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping requested but not supported by the decoder."))) {
        // Expected on devices without a tone-mapping plugin for this codec.
        return;
      }
      throw exception;
    }
  }

  @Test
  public void export_toneMapNoRequestedTranscode_hlg10File_toneMapsOrThrows() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT,
        /* outputFormat= */ null);

    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Tone mapping flag shouldn't change in fallback when tone mapping is
                    // requested.
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(fallbackTransformationRequest.hdrMode);
                  }
                })
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10)))
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
            .build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, composition);
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null
          && (Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping HDR is not supported on this device.")
              || Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping requested but not supported by the decoder."))) {
        // Expected on devices without a tone-mapping plugin for this codec.
        return;
      }
      throw exception;
    }
  }

  @Test
  public void export_toneMapAndTranscode_hdr10File_toneMapsOrThrows() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
        /* outputFormat= */ null);

    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Tone mapping flag shouldn't change in fallback when tone mapping is
                    // requested.
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(fallbackTransformationRequest.hdrMode);
                  }
                })
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10)))
            .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
            .build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, composition);
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null
          && (Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping HDR is not supported on this device.")
              || Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping requested but not supported by the decoder."))) {
        // Expected on devices without a tone-mapping plugin for this codec.
        return;
      }
      throw exception;
    }
  }

  @Test
  public void export_toneMapAndTranscode_hlg10File_toneMapsOrThrows() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT,
        /* outputFormat= */ null);

    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Tone mapping flag shouldn't change in fallback when tone mapping is
                    // requested.
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(fallbackTransformationRequest.hdrMode);
                  }
                })
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10)))
            .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
            .build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, composition);
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null
          && (Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping HDR is not supported on this device.")
              || Objects.equals(
                  exception.getCause().getMessage(),
                  "Tone-mapping requested but not supported by the decoder."))) {
        // Expected on devices without a tone-mapping plugin for this codec.
        return;
      }
      throw exception;
    }
  }
}
