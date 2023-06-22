/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FRAME_COUNT;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the force end of stream handling in {@code ExternalTextureManager}.
 *
 * <p>This test only applies to API29+, as it introduces {@link MediaFormat#KEY_ALLOW_FRAME_DROP},
 * and hence we allow decoder to retain more than one frames in its output. See {@link
 * Util#getMaxPendingFramesCountForMediaCodecDecoders}.
 */
@RunWith(AndroidJUnit4.class)
public class ForceEndOfStreamTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void transcode_decoderDroppingLastFourFrames_exportSucceeds() throws Exception {
    String testId = "transcode_decoderDroppingLastFourFrames_exportSucceeds";
    if (skipTestBelowApi29(context, testId)) {
      return;
    }
    int framesToSkip = 4;

    ExportTestResult testResult =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer(context, framesToSkip))
            .build()
            .run(
                testId, createComposition(MediaItem.fromUri(AndroidTestUtil.MP4_ASSET_URI_STRING)));

    assertThat(testResult.analysisException).isNull();
    assertThat(testResult.exportResult.videoFrameCount)
        .isEqualTo(MP4_ASSET_FRAME_COUNT - framesToSkip);
  }

  @Test
  public void transcode_decoderDroppingNoFrame_exportSucceeds() throws Exception {
    String testId = "transcode_decoderDroppingNoFrame_exportSucceeds";
    if (skipTestBelowApi29(context, testId)) {
      return;
    }

    ExportTestResult testResult =
        new TransformerAndroidTestRunner.Builder(
                context, buildTransformer(context, /* framesToSkip= */ 0))
            .build()
            .run(
                testId, createComposition(MediaItem.fromUri(AndroidTestUtil.MP4_ASSET_URI_STRING)));

    assertThat(testResult.analysisException).isNull();
    assertThat(testResult.exportResult.videoFrameCount).isEqualTo(MP4_ASSET_FRAME_COUNT);
  }

  private static boolean skipTestBelowApi29(Context context, String testId)
      throws JSONException, IOException {
    if (Util.SDK_INT < 29) {
      AndroidTestUtil.recordTestSkipped(
          context, testId, /* reason= */ "Decoder frame dropping is possible from API29.");
      return true;
    }
    return false;
  }

  private static Transformer buildTransformer(Context context, int framesToSkip) {
    return new Transformer.Builder(context)
        .setAssetLoaderFactory(
            new DefaultAssetLoaderFactory(
                context,
                new FrameDroppingDecoderFactory(context, MP4_ASSET_FRAME_COUNT, framesToSkip),
                /* forceInterpretHdrAsSdr= */ false,
                Clock.DEFAULT))
        .build();
  }

  private static Composition createComposition(MediaItem mediaItem) {
    return new Composition.Builder(
            ImmutableList.of(
                new EditedMediaItemSequence(
                    ImmutableList.of(
                        new EditedMediaItem.Builder(mediaItem)
                            .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
                            .build()))))
        .build();
  }

  private static final class FrameDroppingDecoderFactory implements Codec.DecoderFactory {
    private final Context context;
    private final DefaultDecoderFactory defaultDecoderFactory;
    private final int sourceFrameCount;
    private final int framesToDrop;

    private FrameDroppingDecoderFactory(Context context, int sourceFrameCount, int framesToDrop) {
      this.context = context;
      this.defaultDecoderFactory = new DefaultDecoderFactory(context);
      this.sourceFrameCount = sourceFrameCount;
      this.framesToDrop = framesToDrop;
    }

    @Override
    public Codec createForAudioDecoding(Format format) throws ExportException {
      return defaultDecoderFactory.createForAudioDecoding(format);
    }

    @Override
    public Codec createForVideoDecoding(
        Format format, Surface outputSurface, boolean requestSdrToneMapping)
        throws ExportException {
      Pair<MediaFormat, String> videoDecoderMediaFormatAndName =
          defaultDecoderFactory.findVideoDecoder(format, requestSdrToneMapping);
      return new FrameDroppingDecoder(
          context,
          format,
          videoDecoderMediaFormatAndName.first,
          videoDecoderMediaFormatAndName.second,
          outputSurface,
          sourceFrameCount,
          framesToDrop);
    }

    private static final class FrameDroppingDecoder extends DefaultCodec {

      private final int sourceFrameCount;
      private final int framesToDrop;

      private int framesReceived;

      public FrameDroppingDecoder(
          Context context,
          Format configurationFormat,
          MediaFormat configurationMediaFormat,
          String mediaCodecName,
          @Nullable Surface outputSurface,
          int sourceFrameCount,
          int framesToDrop)
          throws ExportException {
        super(
            context,
            configurationFormat,
            configurationMediaFormat,
            mediaCodecName,
            /* isDecoder= */ true,
            outputSurface);
        this.sourceFrameCount = sourceFrameCount;
        this.framesToDrop = framesToDrop;
      }

      @Override
      public void releaseOutputBuffer(long renderPresentationTimeUs) throws ExportException {
        framesReceived++;
        super.releaseOutputBuffer(
            /* render= */ sourceFrameCount - framesReceived >= framesToDrop,
            renderPresentationTimeUs);
      }
    }
  }
}
