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

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.mh.FileUtil.maybeAssertFileHasColorTransfer;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.EditedMediaItem;
import com.google.android.exoplayer2.transformer.ExportException;
import com.google.android.exoplayer2.transformer.ExportTestResult;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.util.Log;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * TransformationRequest#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC HDR to SDR tone mapping
 * edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingMediaCodecTest {
  public static final String TAG = "ToneMapHdrToSdrUsingMediaCodecTest";

  @Test
  public void export_toneMapNoRequestedTranscode_hdr10File_toneMapsOrThrows() throws Exception {
    String testId = "export_toneMapNoRequestedTranscode_hdr10File_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
                    .build())
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
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, mediaItem);
      Log.i(TAG, "Tone mapped.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  @Test
  public void export_toneMapNoRequestedTranscode_hlg10File_toneMapsOrThrows() throws Exception {
    String testId = "export_toneMapNoRequestedTranscode_hlg10File_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
                    .build())
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
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, mediaItem);
      Log.i(TAG, "Tone mapped.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  @Test
  public void export_toneMapAndTranscode_hdr10File_toneMapsOrThrows() throws Exception {
    String testId = "export_toneMapAndTranscode_hdr10File_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
                    .build())
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
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      Log.i(TAG, "Tone mapped.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }

  @Test
  public void export_toneMapAndTranscode_hlg10File_toneMapsOrThrows() throws Exception {
    String testId = "export_toneMapAndTranscode_hlg10File_toneMapsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
                    .build())
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
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      Log.i(TAG, "Tone mapped.");
      maybeAssertFileHasColorTransfer(exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(exception.errorCode)
          .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
    }
  }
}
