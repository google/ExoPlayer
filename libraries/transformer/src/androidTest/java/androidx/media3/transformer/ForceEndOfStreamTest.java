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
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FRAME_COUNT;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.nio.ByteBuffer;
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
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
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
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
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
            new EditedMediaItemSequence(
                new EditedMediaItem.Builder(mediaItem)
                    .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
                    .build()))
        .build();
  }

  private static final class FrameDroppingDecoderFactory implements Codec.DecoderFactory {
    private final DefaultDecoderFactory defaultDecoderFactory;
    private final int sourceFrameCount;
    private final int framesToDrop;

    private FrameDroppingDecoderFactory(Context context, int sourceFrameCount, int framesToDrop) {
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
      return new FrameDroppingDecoder(
          defaultDecoderFactory.createForVideoDecoding(
              format, outputSurface, requestSdrToneMapping),
          sourceFrameCount,
          framesToDrop);
    }

    public static final class FrameDroppingDecoder implements Codec {

      private final DefaultCodec wrappedDecoder;
      private final int sourceFrameCount;
      private final int framesToDrop;

      private int framesReceived;

      public FrameDroppingDecoder(DefaultCodec decoder, int sourceFrameCount, int framesToDrop)
          throws ExportException {
        wrappedDecoder = decoder;
        this.sourceFrameCount = sourceFrameCount;
        this.framesToDrop = framesToDrop;
      }

      @Override
      public Format getConfigurationFormat() {
        return wrappedDecoder.getConfigurationFormat();
      }

      @Override
      public String getName() {
        return wrappedDecoder.getName();
      }

      @Override
      public Surface getInputSurface() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getMaxPendingFrameCount() {
        return wrappedDecoder.getMaxPendingFrameCount();
      }

      @Override
      public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer)
          throws ExportException {
        return wrappedDecoder.maybeDequeueInputBuffer(inputBuffer);
      }

      @Override
      public void queueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException {
        wrappedDecoder.queueInputBuffer(inputBuffer);
      }

      @Override
      public void signalEndOfInputStream() throws ExportException {
        wrappedDecoder.signalEndOfInputStream();
      }

      @Nullable
      @Override
      public Format getOutputFormat() throws ExportException {
        return wrappedDecoder.getOutputFormat();
      }

      @Override
      public ByteBuffer getOutputBuffer() throws ExportException {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public MediaCodec.BufferInfo getOutputBufferInfo() throws ExportException {
        return wrappedDecoder.getOutputBufferInfo();
      }

      @Override
      public void releaseOutputBuffer(boolean render) throws ExportException {
        wrappedDecoder.releaseOutputBuffer(render);
      }

      @Override
      public void releaseOutputBuffer(long renderPresentationTimeUs) throws ExportException {
        framesReceived++;
        wrappedDecoder.releaseOutputBuffer(
            /* render= */ sourceFrameCount - framesReceived >= framesToDrop,
            renderPresentationTimeUs);
      }

      @Override
      public boolean isEnded() {
        return wrappedDecoder.isEnded();
      }

      @Override
      public void release() {
        wrappedDecoder.release();
      }
    }
  }
}
