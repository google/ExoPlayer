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

import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.mh.FileUtil.maybeAssertFileHasColorTransfer;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EncoderUtil;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * TransformationRequest#HDR_MODE_KEEP_HDR HDR frame edit}.
 */
@RunWith(AndroidJUnit4.class)
public class HdrEditingTest {
  public static final String TAG = "HdrEditingTest";
  private static final ColorInfo HDR10_DEFAULT_COLOR_INFO =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT2020)
          .setColorRange(C.COLOR_RANGE_LIMITED)
          .setColorTransfer(C.COLOR_TRANSFER_ST2084)
          .build();
  private static final ColorInfo HLG10_DEFAULT_COLOR_INFO =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT2020)
          .setColorRange(C.COLOR_RANGE_LIMITED)
          .setColorTransfer(C.COLOR_TRANSFER_HLG)
          .build();

  @Test
  public void export_noRequestedTranscode_hdr10File_exportsOrThrows() throws Exception {
    String testId = "export_noRequestedTranscode_hdr10File_exportsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, mediaItem);
      Log.i(TAG, "Exported.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_ST2084);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isAnyOf(
              ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
              ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  @Test
  public void export_noRequestedTranscode_hlg10File_exportsOrThrows() throws Exception {
    String testId = "export_noRequestedTranscode_hlg10File_exportsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, mediaItem);
      Log.i(TAG, "Exported.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_HLG);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isAnyOf(
              ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
              ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  @Test
  public void exportAndTranscode_hdr10File_whenHdrEditingIsSupported_exports() throws Exception {
    String testId = "exportAndTranscode_hdr10File_whenHdrEditingIsSupported_exports";
    Context context = ApplicationProvider.getApplicationContext();
    if (!deviceSupportsHdrEditing(VIDEO_H265, HDR10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device lacks HDR10 editing support.");
      return;
    }

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_ST2084);
  }

  @Test
  public void exportAndTranscode_hlg10File_whenHdrEditingIsSupported_exports() throws Exception {
    String testId = "exportAndTranscode_hlg10File_whenHdrEditingIsSupported_exports";
    Context context = ApplicationProvider.getApplicationContext();
    if (!deviceSupportsHdrEditing(VIDEO_H265, HLG10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device lacks HLG10 editing support.");
      return;
    }

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_dolbyVisionFile_whenHdrEditingIsSupported_exports()
      throws Exception {
    String testId = "exportAndTranscode_dolbyVisionFile_whenHdrEditingIsSupported_exports";
    Context context = ApplicationProvider.getApplicationContext();
    // This dolby vision file has a ColorInfo identical to HLG10.
    if (!deviceSupportsHdrEditing(VIDEO_H265, HLG10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device lacks HLG10 editing support.");
      return;
    }

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_DOLBY_VISION_HDR));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_hdr10File_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    String testId = "exportAndTranscode_hdr10File_whenHdrEditingUnsupported_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();
    if (deviceSupportsHdrEditing(VIDEO_H265, HDR10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device supports HDR10 editing.");
      return;
    }

    AtomicBoolean isFallbackListenerInvoked = new AtomicBoolean();
    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    isFallbackListenerInvoked.set(true);
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(TransformationRequest.HDR_MODE_KEEP_HDR);
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.hdrMode
                            == TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      Log.i(TAG, "Tone mapped.");
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
      assertThat(isFallbackListenerInvoked.get()).isFalse();
    }
  }

  @Test
  public void exportAndTranscode_hlg10File_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    String testId = "exportAndTranscode_hlg10File_whenHdrEditingUnsupported_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();
    if (deviceSupportsHdrEditing(VIDEO_H265, HLG10_DEFAULT_COLOR_INFO)) {
      recordTestSkipped(context, testId, /* reason= */ "Device supports HLG10 editing.");
      return;
    }

    AtomicBoolean isFallbackListenerInvoked = new AtomicBoolean();
    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    isFallbackListenerInvoked.set(true);
                    assertThat(originalTransformationRequest.hdrMode)
                        .isEqualTo(TransformationRequest.HDR_MODE_KEEP_HDR);
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.hdrMode
                            == TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      Log.i(TAG, "Tone mapped.");
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
      assertThat(isFallbackListenerInvoked.get()).isFalse();
    }
  }

  private static boolean deviceSupportsHdrEditing(String mimeType, ColorInfo colorInfo) {
    return !EncoderUtil.getSupportedEncodersForHdrEditing(mimeType, colorInfo).isEmpty();
  }
}
