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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP3_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_180_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_270_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.createOpenGlObjects;
import static androidx.media3.transformer.AndroidTestUtil.generateTextureFromBitmap;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_NA;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSCODED;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSMUXED;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_FAILED_FORMAT_MISMATCH;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_SUCCEEDED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.FrameCache;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.SpeedChangeEffect;
import androidx.media3.effect.TimestampWrapper;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.transformer.AssetLoader.CompositionSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} for cases that cannot be tested using
 * robolectric.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerEndToEndTest {

  private static final GlEffect NO_OP_EFFECT = new Contrast(0f);
  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  private volatile @MonotonicNonNull TextureAssetLoader textureAssetLoader;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void compositionEditing_withThreeSequences_completes() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET_URI_STRING))
            .setEffects(
                new Effects(
                    ImmutableList.of(createSonic(/* pitch= */ 2f)),
                    ImmutableList.of(RgbFilter.createInvertedFilter())))
            .build();
    EditedMediaItem imageItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(JPG_ASSET_URI_STRING))
            .setDurationUs(1_500_000)
            .setFrameRate(30)
            .build();

    EditedMediaItemSequence audioVideoSequence =
        new EditedMediaItemSequence(audioVideoItem, imageItem, audioVideoItem);

    EditedMediaItem.Builder audioBuilder =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET_URI_STRING)).setRemoveVideo(true);

    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            audioBuilder
                .setEffects(
                    new Effects(
                        ImmutableList.of(createSonic(/* pitch= */ 1.3f)),
                        /* videoEffects= */ ImmutableList.of()))
                .build(),
            audioBuilder
                .setEffects(
                    new Effects(
                        ImmutableList.of(createSonic(/* pitch= */ 0.85f)),
                        /* videoEffects= */ ImmutableList.of()))
                .build());

    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(
                audioBuilder
                    .setEffects(
                        new Effects(
                            ImmutableList.of(createSonic(/* pitch= */ 0.4f)),
                            /* videoEffects= */ ImmutableList.of()))
                    .build()),
            /* isLooping= */ true);

    Composition composition =
        new Composition.Builder(audioVideoSequence, audioSequence, loopingAudioSequence).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    // MP4_ASSET duration is ~1s.
    // Image asset duration is ~1.5s.
    // audioVideoSequence duration: ~3.5s (3 inputs).
    // audioSequence duration: ~2s (2 inputs).
    // loopingAudioSequence: Matches max other sequence (~3.5s) -> 4 inputs of ~1s audio item.
    assertThat(result.exportResult.processedInputs).hasSize(9);
  }

  @Test
  public void videoEditing_withImageInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    int expectedFrameCount = 40;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(C.MICROS_PER_SECOND)
            .setFrameRate(expectedFrameCount)
            .setEffects(effects)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs)
        .isEqualTo((C.MILLIS_PER_SECOND / expectedFrameCount) * (expectedFrameCount - 1));
  }

  @Test
  public void videoTranscoding_withImageInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    int expectedFrameCount = 40;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(C.MICROS_PER_SECOND)
            .setFrameRate(expectedFrameCount)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs)
        .isEqualTo((C.MILLIS_PER_SECOND / expectedFrameCount) * (expectedFrameCount - 1));
  }

  @Test
  public void videoEditing_withTextureInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Bitmap bitmap =
        new DataSourceBitmapLoader(context).loadBitmap(Uri.parse(PNG_ASSET_URI_STRING)).get();
    int expectedFrameCount = 2;
    EGLContext currentContext = createOpenGlObjects();
    DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(new DefaultGlObjectsProvider(currentContext))
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestTextureAssetLoaderFactory(bitmap.getWidth(), bitmap.getHeight()))
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(C.MICROS_PER_SECOND)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .build();
    int texId = generateTextureFromBitmap(bitmap);
    HandlerThread textureQueuingThread = new HandlerThread("textureQueuingThread");
    textureQueuingThread.start();
    Looper looper = checkNotNull(textureQueuingThread.getLooper());
    Handler textureHandler =
        new Handler(looper) {
          @Override
          public void handleMessage(Message msg) {
            if (textureAssetLoader != null
                && textureAssetLoader.queueInputTexture(texId, /* presentationTimeUs= */ 0)) {
              textureAssetLoader.queueInputTexture(
                  texId, /* presentationTimeUs= */ C.MICROS_PER_SECOND / 2);
              textureAssetLoader.signalEndOfVideoInput();
              return;
            }
            sendEmptyMessage(0);
          }
        };

    textureHandler.sendEmptyMessage(0);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs).isEqualTo(C.MILLIS_PER_SECOND / 2);
  }

  @Test
  public void videoTranscoding_withTextureInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Bitmap bitmap =
        new DataSourceBitmapLoader(context).loadBitmap(Uri.parse(PNG_ASSET_URI_STRING)).get();
    int expectedFrameCount = 2;
    EGLContext currentContext = createOpenGlObjects();
    DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(new DefaultGlObjectsProvider(currentContext))
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestTextureAssetLoaderFactory(bitmap.getWidth(), bitmap.getHeight()))
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(C.MICROS_PER_SECOND)
            .build();
    int texId = generateTextureFromBitmap(bitmap);
    HandlerThread textureQueuingThread = new HandlerThread("textureQueuingThread");
    textureQueuingThread.start();
    Looper looper = checkNotNull(textureQueuingThread.getLooper());
    Handler textureHandler =
        new Handler(looper) {
          @Override
          public void handleMessage(Message msg) {
            if (textureAssetLoader != null
                && textureAssetLoader.queueInputTexture(texId, /* presentationTimeUs= */ 0)) {
              textureAssetLoader.queueInputTexture(
                  texId, /* presentationTimeUs= */ C.MICROS_PER_SECOND / 2);
              textureAssetLoader.signalEndOfVideoInput();
              return;
            }
            sendEmptyMessage(0);
          }
        };
    textureHandler.sendEmptyMessage(0);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs).isEqualTo(C.MILLIS_PER_SECOND / 2);
  }

  @Test
  public void videoEditing_completesWithConsistentFrameCount() throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoEditing_effectsOverTime_completesWithConsistentFrameCount() throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new TimestampWrapper(
                new Contrast(.5f),
                /* startTimeUs= */ 0,
                /* endTimeUs= */ Math.round(.1f * C.MICROS_PER_SECOND)),
            new TimestampWrapper(
                new FrameCache(/* capacity= */ 5),
                /* startTimeUs= */ Math.round(.2f * C.MICROS_PER_SECOND),
                /* endTimeUs= */ Math.round(.3f * C.MICROS_PER_SECOND)));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoOnly_completesWithConsistentDuration() throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).setEffects(effects).build();
    long expectedDurationMs = 967;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.durationMs).isEqualTo(expectedDurationMs);
  }

  @Test
  public void clippedMedia_completesWithClippedDuration() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer = new Transformer.Builder(context).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 11_000;
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
  }

  @Test
  public void
      clippedAndRotatedMedia_withNoOpEffect_completesWithClippedDurationAndCorrectOrientation()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer = new Transformer.Builder(context).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 11_000;
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(90).build(),
            NO_OP_EFFECT);
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
    Format format = retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO);
    // The output video is portrait, but Transformer's default setup encodes videos landscape.
    assertThat(format.rotationDegrees).isEqualTo(90);
  }

  @Test
  public void clippedMedia_trimOptimizationEnabled_fallbackToNormalExportUponFormatMismatch()
      throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 13_000;
    // The file is made artificially on computer software so phones will not have the encoder
    // available to match the csd.
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(result.exportResult.optimizationResult)
        .isEqualTo(OPTIMIZATION_FAILED_FORMAT_MISMATCH);
    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
    assertThat(result.exportResult.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void
      clippedAndRotatedMedia_trimOptimizationEnabledButFormatsMismatch_fallsbackWithCorrectOrientationOutput()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 13_000;
    // The format for this file cannot be encoded on phones, so it will trigger trim optimization
    // fallback. This is because its csd doesn't match any known phone decoder.
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build(),
            NO_OP_EFFECT);
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult)
        .isEqualTo(OPTIMIZATION_FAILED_FORMAT_MISMATCH);
    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
    Format format = retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO);
    // The video is transcoded, so the rotation is performed in the VideoFrameProcessor.
    // The output video is portrait, but Transformer's default setup encodes videos landscape.
    assertThat(format.rotationDegrees).isEqualTo(0);
  }

  @Test
  public void
      clippedMedia_trimOptimizationEnabled_noKeyFrameBetweenClipTimes_fallbackToNormalExport()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 11_000;
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(result.exportResult.optimizationResult)
        .isEqualTo(OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM);
    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
    assertThat(result.exportResult.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void
      clippedMedia_trimOptimizationEnabled_noKeyFramesAfterClipStart_fallbackToNormalExport()
          throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    long clippingStartMs = 14_500;
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(result.exportResult.optimizationResult)
        .isEqualTo(OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM);
    // The asset is 15 s 537 ms long.
    assertThat(result.exportResult.durationMs).isAtMost(1_017);
    assertThat(result.exportResult.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void clippedMedia_trimOptimizationEnabled_completesWithOptimizationApplied()
      throws Exception {
    if (!isRunningOnEmulator() || Util.SDK_INT != 33) {
      // The trim optimization is only guaranteed to work on emulator for this (emulator-transcoded)
      // file.
      recordTestSkipped(context, testId, /* reason= */ "SDK 33 Emulator only test");
      assumeTrue(false);
    }
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(2000);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void
      clippedMedia_trimOptimizationEnabled_inputFileRotated270_completesWithOptimizationApplied()
          throws Exception {
    if (!isRunningOnEmulator() || Util.SDK_INT < 33) {
      // The trim optimization is only guaranteed to work on emulator for this (emulator-transcoded)
      // file.
      recordTestSkipped(context, testId, /* reason= */ "SDK 33 Emulator only test");
      assumeTrue(false);
    }
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_270_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(2000);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
    Format format = retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(format.rotationDegrees).isEqualTo(270);
  }

  @Test
  public void
      clippedMedia_trimOptimizationEnabled_inputFileRotated180_completesWithOptimizationApplied()
          throws Exception {
    if (!isRunningOnEmulator() || Util.SDK_INT < 33) {
      // The trim optimization is only guaranteed to work on emulator for this (emulator-transcoded)
      // file.
      recordTestSkipped(context, testId, /* reason= */ "SDK 33 Emulator only test");
      assumeTrue(false);
    }
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_180_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(2000);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
    Format format = retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(format.rotationDegrees).isEqualTo(180);
  }

  @Test
  public void
      clippedMediaAudioRemovedNoOpEffectAndRotated_trimOptimizationEnabled_completedWithOptimizationAppliedAndCorrectOrientation()
          throws Exception {
    if (!isRunningOnEmulator() || Util.SDK_INT != 33) {
      // The trim optimization is only guaranteed to work on emulator for this (emulator-transcoded)
      // file.
      recordTestSkipped(context, testId, /* reason= */ "SDK 33 Emulator only test");
      assumeTrue(false);
    }
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    Effects effects =
        new Effects(
            /* audioProcessors= */ ImmutableList.of(),
            ImmutableList.of(
                new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build(),
                NO_OP_EFFECT));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).setEffects(effects).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(2000);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_NA);

    Format format = retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO);
    // The video is trim-optimized, so the rotation is performed in MuxerWrapper.
    // The MuxerWrapper rotation is clockwise while the ScaleAndRotateTransformation rotation
    // is counterclockwise.
    // Manually verified that the video has correct rotation.
    assertThat(format.rotationDegrees).isEqualTo(180);
  }

  @Test
  public void videoEditing_trimOptimizationEnabled_fallbackToNormalExport() throws Exception {
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    if (!isRunningOnEmulator()) {
      // The trim optimization is only guaranteed to work on emulator for this (emulator-transcoded)
      // file.
      recordTestSkipped(context, testId, /* reason= */ "Emulator only test");
      assumeTrue(false);
    }
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.optimizationResult)
        .isEqualTo(OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED);
    assertThat(result.exportResult.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSCODED);
    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void speedAdjustedMedia_completesWithCorrectDuration() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            new long[] {
              0L,
              3 * C.MICROS_PER_SECOND,
              6 * C.MICROS_PER_SECOND,
              9 * C.MICROS_PER_SECOND,
              12 * C.MICROS_PER_SECOND
            },
            new float[] {0.5f, 0.75f, 1f, 1.5f, 2f});
    Pair<AudioProcessor, Effect> speedEffect =
        Effects.createExperimentalSpeedChangingEffect(speedProvider);
    Effects effects =
        new Effects(
            /* audioProcessors= */ ImmutableList.of(speedEffect.first),
            /* videoEffects= */ ImmutableList.of(speedEffect.second));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setEffects(effects)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    // The input video is 15.537 seconds.
    // 3 / 0.5 + 3 / 0.75 + 3 + 3 / 1.5 + 3.537 / 2 rounds up to 16_770
    assertThat(result.exportResult.durationMs).isAtMost(16_770);
  }

  @Test
  public void videoEncoderFormatUnsupported_completesWithError() throws Exception {
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new VideoUnsupportedEncoderFactory(context))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveAudio(true)
            .build();

    ExportException exception =
        assertThrows(
            ExportException.class,
            () ->
                new TransformerAndroidTestRunner.Builder(context, transformer)
                    .build()
                    .run(testId, editedMediaItem));

    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_ENCODER_INIT_FAILED);
    assertThat(exception).hasMessageThat().contains("video");
  }

  @Test
  public void durationAdjustedSequence_completesWithCorrectDuration() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(new SpeedChangeEffect(1.5f), new SpeedChangeEffect(2f));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(
                    Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING)))
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .setRemoveAudio(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem, editedMediaItem))
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.durationMs).isEqualTo(10_351L);
  }

  @Test
  public void durationAdjustedSequence_withForcedAudioTrack_completesWithCorrectDuration()
      throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_FORMAT);
    ImmutableList<Effect> videoEffects = ImmutableList.of(new SpeedChangeEffect(1.5f));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(
                    Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING)))
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .setRemoveAudio(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem, editedMediaItem))
            .experimentalSetForceAudioTrack(true)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.durationMs).isAtMost(20_720L);
  }

  @Test
  public void audioVideoTranscodedFromDifferentSequences_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    ImmutableList<AudioProcessor> audioProcessors = ImmutableList.of(createSonic(1.2f));
    ImmutableList<Effect> videoEffects = ImmutableList.of(RgbFilter.createGrayscaleFilter());
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(audioProcessors, videoEffects))
            .build();
    ExportTestResult expectedResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of()))
            .setRemoveVideo(true)
            .build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .setRemoveAudio(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(audioEditedMediaItem),
                new EditedMediaItemSequence(videoEditedMediaItem))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.channelCount)
        .isEqualTo(expectedResult.exportResult.channelCount);
    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(expectedResult.exportResult.videoFrameCount);
    assertThat(result.exportResult.durationMs).isEqualTo(expectedResult.exportResult.durationMs);
  }

  @Test
  public void loopingTranscodedAudio_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem), /* isLooping= */ true);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            videoEditedMediaItem, videoEditedMediaItem, videoEditedMediaItem);
    Composition composition =
        new Composition.Builder(loopingAudioSequence, videoSequence).setTransmuxVideo(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(6);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(90);
    assertThat(result.exportResult.durationMs).isEqualTo(2980);
  }

  @Test
  public void loopingTranscodedVideo_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_FORMAT, /* outputFormat= */ MP4_ASSET_FORMAT);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET_URI_STRING))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence loopingVideoSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem), /* isLooping= */ true);
    Composition composition = new Composition.Builder(audioSequence, loopingVideoSequence).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(7);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(92);
    assertThat(result.exportResult.durationMs).isEqualTo(3105);
  }

  @Test
  public void loopingImage_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem);
    EditedMediaItem imageEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(1_000_000)
            .setFrameRate(30)
            .build();
    EditedMediaItemSequence loopingImageSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(imageEditedMediaItem, imageEditedMediaItem), /* isLooping= */ true);
    Composition composition = new Composition.Builder(audioSequence, loopingImageSequence).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(7);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.durationMs).isEqualTo(3133);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(95);
  }

  @Test
  public void loopingImage_loopingSequenceIsLongest_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence = new EditedMediaItemSequence(audioEditedMediaItem);
    EditedMediaItem imageEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(1_050_000)
            .setFrameRate(20)
            .build();
    EditedMediaItemSequence loopingImageSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(imageEditedMediaItem, imageEditedMediaItem), /* isLooping= */ true);
    Composition composition = new Composition.Builder(audioSequence, loopingImageSequence).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(3);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.durationMs).isEqualTo(1000);
  }

  @Test
  public void audioTranscode_processesInInt16Pcm() throws Exception {
    FormatTrackingAudioBufferSink audioFormatTracker = new FormatTrackingAudioBufferSink();

    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setEffects(
                new Effects(
                    ImmutableList.of(audioFormatTracker.createTeeAudioProcessor()),
                    /* videoEffects= */ ImmutableList.of()))
            .setRemoveVideo(true)
            .build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, editedMediaItem);

    ImmutableList<AudioFormat> audioFormats = audioFormatTracker.getFlushedAudioFormats();
    assertThat(audioFormats).hasSize(1);
    assertThat(audioFormats.get(0).encoding).isEqualTo(C.ENCODING_PCM_16BIT);
  }

  @Test
  public void audioEditing_monoToStereo_outputsStereo() throws Exception {

    ChannelMixingAudioProcessor channelMixingAudioProcessor = new ChannelMixingAudioProcessor();
    channelMixingAudioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 1, /* outputChannelCount= */ 2));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .setEffects(
                new Effects(
                    ImmutableList.of(channelMixingAudioProcessor),
                    /* videoEffects= */ ImmutableList.of()))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.channelCount).isEqualTo(2);
  }

  @Test
  public void audioComposition_noEffects_transmuxes() throws Exception {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem)).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void audioComposition_compositionEffects_transcodes() throws Exception {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem))
            .setEffects(
                new Effects(ImmutableList.of(createSonic(/* pitch= */ 2f)), ImmutableList.of()))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSCODED);
  }

  @Test
  public void transcode_withOutputVideoMimeTypeAv1_completesSuccessfully() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setCodecs(null)
            .build());
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Transformer transformer =
        new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_AV1).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    ExportResult exportResult = exportTestResult.exportResult;

    String actualMimeType =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO).sampleMimeType;
    assertThat(actualMimeType).isEqualTo(MimeTypes.VIDEO_AV1);
    assertThat(exportResult.exportException).isNull();
    assertThat(exportResult.durationMs).isGreaterThan(0);
    assertThat(exportResult.videoMimeType).isEqualTo(MimeTypes.VIDEO_AV1);
  }

  @Test
  public void transcode_withOutputAudioMimeTypeAac_completesSuccessfully() throws Exception {
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP3_ASSET_URI_STRING));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Transformer transformer =
        new Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    ExportResult exportResult = exportTestResult.exportResult;

    String actualMimeType =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_AUDIO).sampleMimeType;
    assertThat(actualMimeType).isEqualTo(MimeTypes.AUDIO_AAC);
    assertThat(exportResult.exportException).isNull();
    assertThat(exportResult.durationMs).isGreaterThan(0);
    assertThat(exportResult.audioMimeType).isEqualTo(MimeTypes.AUDIO_AAC);
  }

  @Test
  public void transmux_audioWithEditList_preservesDuration() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset:///media/mp4/long_edit_list_audioonly.mp4"));

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    Mp4Extractor mp4Extractor = new Mp4Extractor(new DefaultSubtitleParserFactory());
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(mp4Extractor, exportTestResult.filePath);
    // TODO: b/324842222 - Mp4Extractor reports incorrect duration, without considering edit lists.
    assertThat(fakeExtractorOutput.seekMap.getDurationUs()).isEqualTo(1_579_000);
    assertThat(fakeExtractorOutput.numberOfTracks).isEqualTo(1);
    FakeTrackOutput audioTrack = fakeExtractorOutput.trackOutputs.get(0);
    int expectedSampleCount = 68;
    audioTrack.assertSampleCount(expectedSampleCount);
    if (Util.SDK_INT >= 30) {
      // TODO: b/324842222 - Mp4Extractor doesn't interpret Transformer's generated output as
      //  "gapless" audio. The generated file should have encoderDelay = 742 and first
      //  sample PTS of 0.
      assertThat(audioTrack.lastFormat.encoderDelay).isEqualTo(0);
      assertThat(audioTrack.getSampleTimeUs(/* index= */ 0)).isEqualTo(-16_826);
      assertThat(audioTrack.getSampleTimeUs(/* index= */ expectedSampleCount - 1))
          .isEqualTo(1_538_911);
    } else {
      // Edit lists are not supported b/142580952 : sample times start from zero,
      // and output duration will be longer than input duration by encoder delay.
      assertThat(audioTrack.lastFormat.encoderDelay).isEqualTo(0);
      assertThat(audioTrack.getSampleTimeUs(/* index= */ 0)).isEqualTo(0);
      assertThat(audioTrack.getSampleTimeUs(/* index= */ expectedSampleCount - 1))
          .isEqualTo(1_555_736);
    }
  }

  @Test
  public void transmux_audioWithEditListUsingInAppMuxer_preservesDuration() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setMuxerFactory(new InAppMuxer.Factory.Builder().build())
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset:///media/mp4/long_edit_list_audioonly.mp4"));

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    Mp4Extractor mp4Extractor = new Mp4Extractor(new DefaultSubtitleParserFactory());
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(mp4Extractor, exportTestResult.filePath);
    // TODO: b/324903070 - The generated output file has incorrect duration.
    assertThat(fakeExtractorOutput.seekMap.getDurationUs()).isEqualTo(1_555_700);
    assertThat(fakeExtractorOutput.numberOfTracks).isEqualTo(1);
    FakeTrackOutput audioTrack = fakeExtractorOutput.trackOutputs.get(0);
    int expectedSampleCount = 68;
    audioTrack.assertSampleCount(expectedSampleCount);
    // TODO: b/324903070 - InAppMuxer doesn't write edit lists to support gapless audio muxing.
    //  Output incorrectly starts at encoderDelay 0, PTS 0
    assertThat(audioTrack.lastFormat.encoderDelay).isEqualTo(0);
    assertThat(audioTrack.getSampleTimeUs(/* index= */ 0)).isEqualTo(0);
    // TODO: b/270583563 - InAppMuxer always uses 1 / 48_000 timebase for audio.
    //  The audio file in this test is 44_100 Hz, with timebase for audio of 1 / 44_100 and
    //  each sample duration is exactly 1024 / 44_100, with no rounding errors.
    //  Since InAppMuxer uses a different timebase for audio, some rounding errors are introduced
    //  and MP4 sample durations are off.
    // TODO: b/324903070 - expectedLastSampleTimeUs & expectedDurationUs are incorrect.
    //  Last sample time cannot be greater than total duration.
    assertThat(audioTrack.getSampleTimeUs(/* index= */ expectedSampleCount - 1))
        .isEqualTo(1_555_708);
  }

  @Test
  public void transmux_videoWithEditList_trimsFirstIDRFrameDuration() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeTrue(
        "MediaMuxer doesn't support B frames reliably on older SDK versions", Util.SDK_INT >= 29);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset:///media/mp4/iibbibb_editlist_videoonly.mp4"));

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    Mp4Extractor mp4Extractor = new Mp4Extractor(new DefaultSubtitleParserFactory());
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(mp4Extractor, exportTestResult.filePath);
    assertThat(fakeExtractorOutput.numberOfTracks).isEqualTo(1);

    // TODO: b/324842222 - Duration isn't written correctly when transmuxing, and differs
    //  between SDK versions. Do not assert for duration yet.
    FakeTrackOutput videoTrack = fakeExtractorOutput.trackOutputs.get(0);
    int expectedSampleCount = 13;
    videoTrack.assertSampleCount(expectedSampleCount);
    assertThat(videoTrack.getSampleTimeUs(/* index= */ 0)).isEqualTo(0);
    int sampleIndexWithLargestSampleTime = 10;
    assertThat(videoTrack.getSampleTimeUs(sampleIndexWithLargestSampleTime)).isEqualTo(11_500_000);
    assertThat(videoTrack.getSampleTimeUs(/* index= */ expectedSampleCount - 1))
        .isEqualTo(9_500_000);
  }

  private static AudioProcessor createSonic(float pitch) {
    SonicAudioProcessor sonic = new SonicAudioProcessor();
    sonic.setPitch(pitch);
    return sonic;
  }

  private final class TestTextureAssetLoaderFactory implements AssetLoader.Factory {

    private final int width;
    private final int height;

    TestTextureAssetLoaderFactory(int width, int height) {
      this.width = width;
      this.height = height;
    }

    @Override
    public TextureAssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem,
        Looper looper,
        AssetLoader.Listener listener,
        CompositionSettings compositionSettings) {
      Format format = new Format.Builder().setWidth(width).setHeight(height).build();
      OnInputFrameProcessedListener frameProcessedListener =
          (texId, syncObject) -> {
            try {
              GlUtil.deleteTexture(texId);
              GlUtil.deleteSyncObject(syncObject);
            } catch (GlUtil.GlException e) {
              throw new VideoFrameProcessingException(e);
            }
          };
      textureAssetLoader =
          new TextureAssetLoader(editedMediaItem, listener, format, frameProcessedListener);
      return textureAssetLoader;
    }
  }

  private static final class VideoUnsupportedEncoderFactory implements Codec.EncoderFactory {

    private final Codec.EncoderFactory encoderFactory;

    public VideoUnsupportedEncoderFactory(Context context) {
      encoderFactory = new DefaultEncoderFactory.Builder(context).build();
    }

    @Override
    public Codec createForAudioEncoding(Format format) throws ExportException {
      return encoderFactory.createForAudioEncoding(format);
    }

    @Override
    public Codec createForVideoEncoding(Format format) throws ExportException {
      throw ExportException.createForCodec(
          new IllegalArgumentException(),
          ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
          /* isVideo= */ true,
          /* isDecoder= */ false,
          format);
    }

    @Override
    public boolean audioNeedsEncoding() {
      return false;
    }

    @Override
    public boolean videoNeedsEncoding() {
      return true;
    }
  }

  private static final class FormatTrackingAudioBufferSink
      implements TeeAudioProcessor.AudioBufferSink {
    private final ImmutableSet.Builder<AudioFormat> flushedAudioFormats;

    public FormatTrackingAudioBufferSink() {
      this.flushedAudioFormats = new ImmutableSet.Builder<>();
    }

    public TeeAudioProcessor createTeeAudioProcessor() {
      return new TeeAudioProcessor(this);
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
      flushedAudioFormats.add(new AudioFormat(sampleRateHz, channelCount, encoding));
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
      // Do nothing.
    }

    public ImmutableList<AudioFormat> getFlushedAudioFormats() {
      return flushedAudioFormats.build().asList();
    }
  }
}
