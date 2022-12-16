/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NO_TRANSFORMATION;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.TestTransformerBuilderFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end test for {@link Transformer}. */
@RunWith(AndroidJUnit4.class)
public final class TransformerEndToEndTest {

  @Rule
  public final ShadowMediaCodecConfig mediaCodecConfig = ShadowMediaCodecConfig.forTranscoding();

  private static final String ASSET_URI_PREFIX = "asset:///media/";
  private static final String FILE_VIDEO_ONLY = "mp4/sample_18byte_nclx_colr.mp4";
  private static final String FILE_AUDIO_VIDEO = "mp4/sample.mp4";
  private static final String FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S =
      "mp4/sample_with_increasing_timestamps_320w_240h.mp4";
  private static final String FILE_WITH_SUBTITLES = "mkv/sample_with_srt.mkv";
  private static final String FILE_WITH_SEF_SLOW_MOTION = "mp4/sample_sef_slow_motion.mp4";
  private static final String FILE_AUDIO_UNSUPPORTED_BY_DECODER = "amr/sample_wb.amr";
  private static final String FILE_AUDIO_UNSUPPORTED_BY_ENCODER = "amr/sample_nb.amr";
  private static final String FILE_AUDIO_UNSUPPORTED_BY_MUXER = "mp4/sample_ac3.mp4";
  private static final String FILE_UNKNOWN_DURATION = "mp4/sample_fragmented.mp4";
  private static final String DUMP_FILE_OUTPUT_DIRECTORY = "transformerdumps";
  private static final String DUMP_FILE_EXTENSION = "dump";

  private Context context;
  private String outputPath;
  private ProgressHolder progressHolder;
  private TestTransformerBuilderFactory testTransformerBuilderFactory;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    progressHolder = new ProgressHolder();
    testTransformerBuilderFactory = new TestTransformerBuilderFactory(context);
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
  }

  @Test
  public void startTransformation_videoOnlyPassthrough_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void startTransformation_audioOnlyPassthrough_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();

    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_ENCODER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_ENCODER));
  }

  @Test
  public void startTransformation_audioOnlyTranscoding_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AAC) // supported by encoder and muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_ENCODER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_ENCODER + ".aac"));
  }

  @Test
  public void startTransformation_audioAndVideo_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_audioAndVideo_withClippingStartAtKeyFrame_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(12_500)
                    .setEndPositionMs(14_000)
                    .setStartsAtKeyFrame(true)
                    .build())
            .build();

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S + ".clipped"));
  }

  @Test
  public void startTransformation_withSubtitles_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SUBTITLES);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_WITH_SUBTITLES));
  }

  @Test
  public void startTransformation_successiveTransformations_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    // Transform first media item.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    Files.delete(Paths.get(outputPath));

    // Transform second media item.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_concurrentTransformations_throwsError() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);

    assertThrows(
        IllegalStateException.class, () -> transformer.startTransformation(mediaItem, outputPath));
  }

  @Test
  public void startTransformation_removeAudio_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setRemoveAudio(true)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_VIDEO + ".noaudio"));
  }

  @Test
  public void startTransformation_removeVideo_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setRemoveVideo(true)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_VIDEO + ".novideo"));
  }

  @Test
  public void startTransformation_silentAudio_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .experimentalSetForceSilentAudio(true)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_VIDEO + ".silentaudio"));
  }

  @Test
  public void startTransformation_adjustSampleRate_completesSuccessfully() throws Exception {
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setAudioProcessors(ImmutableList.of(sonicAudioProcessor))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_VIDEO + ".48000hz"));
  }

  @Test
  public void startTransformation_withMultipleListeners_callsEachOnCompletion() throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    verify(mockListener1).onTransformationCompleted(eq(mediaItem), any());
    verify(mockListener2).onTransformationCompleted(eq(mediaItem), any());
    verify(mockListener3).onTransformationCompleted(eq(mediaItem), any());
  }

  @Test
  public void startTransformation_withMultipleListeners_callsEachOnError() throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .setTransformationRequest( // Request transcoding so that decoder is used.
                new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_DECODER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformationException exception = TransformerTestRunner.runUntilError(transformer);

    verify(mockListener1).onTransformationError(eq(mediaItem), any(), eq(exception));
    verify(mockListener2).onTransformationError(eq(mediaItem), any(), eq(exception));
    verify(mockListener3).onTransformationError(eq(mediaItem), any(), eq(exception));
  }

  @Test
  public void startTransformation_withMultipleListeners_callsEachOnFallback() throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ true)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    verify(mockListener1)
        .onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
    verify(mockListener2)
        .onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
    verify(mockListener3)
        .onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
  }

  @Test
  public void startTransformation_afterBuildUponWithListenerRemoved_onlyCallsRemainingListeners()
      throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer1 =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    Transformer transformer2 = transformer1.buildUpon().removeListener(mockListener2).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer2.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer2);

    verify(mockListener1).onTransformationCompleted(eq(mediaItem), any());
    verify(mockListener2, never()).onTransformationCompleted(eq(mediaItem), any());
    verify(mockListener3).onTransformationCompleted(eq(mediaItem), any());
  }

  @Test
  public void startTransformation_flattenForSlowMotion_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder().setFlattenForSlowMotion(true).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SEF_SLOW_MOTION);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_WITH_SEF_SLOW_MOTION));
  }

  @Test
  public void startTransformation_completesWithValidBitrate() throws Exception {
    AtomicReference<@NullableType TransformationResult> resultReference = new AtomicReference<>();
    Transformer.Listener listener =
        new Transformer.Listener() {
          @Override
          public void onTransformationCompleted(
              MediaItem inputMediaItem, TransformationResult transformationResult) {
            resultReference.set(transformationResult);
          }
        };
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .addListener(listener)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    @Nullable TransformationResult result = resultReference.get();
    assertThat(result).isNotNull();
    assertThat(result.averageAudioBitrate).isGreaterThan(0);
    assertThat(result.averageVideoBitrate).isGreaterThan(0);
  }

  @Test
  public void startTransformation_withAudioEncoderFormatUnsupported_completesWithError()
      throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(
                        MimeTypes.AUDIO_AMR_NB) // unsupported by encoder, supported by muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformationException exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode)
        .isEqualTo(TransformationException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);
  }

  @Test
  public void startTransformation_withAudioDecoderFormatUnsupported_completesWithError()
      throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AAC) // supported by encoder and muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_DECODER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformationException exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode)
        .isEqualTo(TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
  }

  @Test
  public void startTransformation_withIoError_completesWithError() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri("asset:///non-existing-path.mp4");

    transformer.startTransformation(mediaItem, outputPath);
    TransformationException exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).hasCauseThat().hasCauseThat().isInstanceOf(IOException.class);
    assertThat(exception.errorCode).isEqualTo(TransformationException.ERROR_CODE_IO_FILE_NOT_FOUND);
  }

  @Test
  public void startTransformation_withAudioMuxerFormatUnsupported_completesSuccessfully()
      throws Exception {
    // Test succeeds because MIME type fallback is mandatory.
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ false)
            .addListener(mockListener)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_MUXER + ".fallback"));
    verify(mockListener)
        .onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
  }

  @Test
  public void startTransformation_withAudioMuxerFormatFallback_completesSuccessfully()
      throws Exception {
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        testTransformerBuilderFactory
            .create(/* enableFallback= */ true)
            .addListener(mockListener)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testTransformerBuilderFactory.getTestMuxer(),
        getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_MUXER + ".fallback"));
    verify(mockListener)
        .onFallbackApplied(mediaItem, originalTransformationRequest, fallbackTransformationRequest);
  }

  @Test
  public void startTransformation_withSlowOutputSampleRate_completesWithError() throws Exception {
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(
            context, new SlowExtractorsFactory(/* delayBetweenReadsMs= */ 10));
    Transformer transformer =
        testTransformerBuilderFactory
            .setMaxDelayBetweenSamplesMs(1)
            .create(/* enableFallback= */ false)
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformationException exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(exception.errorCode).isEqualTo(TransformationException.ERROR_CODE_MUXING_FAILED);
  }

  @Test
  public void startTransformation_withUnsetMaxDelayBetweenSamples_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory
            .setMaxDelayBetweenSamplesMs(C.TIME_UNSET)
            .create(/* enableFallback= */ false)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_afterCancellation_completesSuccessfully() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    transformer.cancel();
    Files.delete(Paths.get(outputPath));

    // This would throw if the previous transformation had not been cancelled.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_fromSpecifiedThread_completesSuccessfully() throws Exception {
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    anotherThread.start();
    Looper looper = anotherThread.getLooper();
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).setLooper(looper).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    AtomicReference<Exception> exception = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    new Handler(looper)
        .post(
            () -> {
              try {
                transformer.startTransformation(mediaItem, outputPath);
                TransformerTestRunner.runUntilCompleted(transformer);
              } catch (Exception e) {
                exception.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(exception.get()).isNull();
    DumpFileAsserts.assertOutput(
        context, testTransformerBuilderFactory.getTestMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_fromWrongThread_throwsError() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.startTransformation(mediaItem, outputPath);
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  public void getProgress_knownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    AtomicInteger previousProgressState =
        new AtomicInteger(PROGRESS_STATE_WAITING_FOR_AVAILABILITY);
    AtomicBoolean foundInconsistentState = new AtomicBoolean();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            if (progressState == PROGRESS_STATE_UNAVAILABLE) {
              foundInconsistentState.set(true);
              return;
            }
            switch (previousProgressState.get()) {
              case PROGRESS_STATE_WAITING_FOR_AVAILABILITY:
                break;
              case PROGRESS_STATE_AVAILABLE:
                if (progressState == PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              case PROGRESS_STATE_NO_TRANSFORMATION:
                if (progressState != PROGRESS_STATE_NO_TRANSFORMATION) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              default:
                throw new IllegalStateException();
            }
            previousProgressState.set(progressState);
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_knownDuration_givesIncreasingPercentages() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    List<Integer> progresses = new ArrayList<>();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            if (progressState == PROGRESS_STATE_NO_TRANSFORMATION) {
              return;
            }
            if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY
                && (progresses.isEmpty()
                    || Iterables.getLast(progresses) != progressHolder.progress)) {
              progresses.add(progressHolder.progress);
            }
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(progresses).isInOrder();
    if (!progresses.isEmpty()) {
      // The progress list could be empty if the transformation ends before any progress can be
      // retrieved.
      assertThat(progresses.get(0)).isAtLeast(0);
      assertThat(Iterables.getLast(progresses)).isLessThan(100);
    }
  }

  @Test
  public void getProgress_noCurrentTransformation_returnsNoTransformation() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    @Transformer.ProgressState int stateBeforeTransform = transformer.getProgress(progressHolder);
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    @Transformer.ProgressState int stateAfterTransform = transformer.getProgress(progressHolder);

    assertThat(stateBeforeTransform).isEqualTo(Transformer.PROGRESS_STATE_NO_TRANSFORMATION);
    assertThat(stateAfterTransform).isEqualTo(Transformer.PROGRESS_STATE_NO_TRANSFORMATION);
  }

  @Test
  public void getProgress_unknownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_UNKNOWN_DURATION);
    AtomicInteger previousProgressState =
        new AtomicInteger(PROGRESS_STATE_WAITING_FOR_AVAILABILITY);
    AtomicBoolean foundInconsistentState = new AtomicBoolean();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            switch (previousProgressState.get()) {
              case PROGRESS_STATE_WAITING_FOR_AVAILABILITY:
                break;
              case PROGRESS_STATE_UNAVAILABLE:
              case PROGRESS_STATE_AVAILABLE: // See [Internal: b/176145097].
                if (progressState == PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              case PROGRESS_STATE_NO_TRANSFORMATION:
                if (progressState != PROGRESS_STATE_NO_TRANSFORMATION) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              default:
                throw new IllegalStateException();
            }
            previousProgressState.set(progressState);
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_fromWrongThread_throwsError() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.getProgress(progressHolder);
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  public void cancel_afterCompletion_doesNotThrow() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    transformer.cancel();
  }

  @Test
  public void cancel_fromWrongThread_throwsError() throws Exception {
    Transformer transformer =
        testTransformerBuilderFactory.create(/* enableFallback= */ false).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.cancel();
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  private static String getDumpFileName(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
  }

  private static final class SlowExtractorsFactory implements ExtractorsFactory {

    private final long delayBetweenReadsMs;
    private final ExtractorsFactory defaultExtractorsFactory;

    public SlowExtractorsFactory(long delayBetweenReadsMs) {
      this.delayBetweenReadsMs = delayBetweenReadsMs;
      this.defaultExtractorsFactory = new DefaultExtractorsFactory();
    }

    @Override
    public Extractor[] createExtractors() {
      return slowDownExtractors(defaultExtractorsFactory.createExtractors());
    }

    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
      return slowDownExtractors(defaultExtractorsFactory.createExtractors(uri, responseHeaders));
    }

    private Extractor[] slowDownExtractors(Extractor[] extractors) {
      Extractor[] slowExtractors = new Extractor[extractors.length];
      Arrays.setAll(slowExtractors, i -> new SlowExtractor(extractors[i], delayBetweenReadsMs));
      return slowExtractors;
    }

    private static final class SlowExtractor implements Extractor {

      private final Extractor extractor;
      private final long delayBetweenReadsMs;

      public SlowExtractor(Extractor extractor, long delayBetweenReadsMs) {
        this.extractor = extractor;
        this.delayBetweenReadsMs = delayBetweenReadsMs;
      }

      @Override
      public boolean sniff(ExtractorInput input) throws IOException {
        return extractor.sniff(input);
      }

      @Override
      public void init(ExtractorOutput output) {
        extractor.init(output);
      }

      @Override
      public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
          throws IOException {
        try {
          Thread.sleep(delayBetweenReadsMs);
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
        return extractor.read(input, seekPosition);
      }

      @Override
      public void seek(long position, long timeUs) {
        extractor.seek(position, timeUs);
      }

      @Override
      public void release() {
        extractor.release();
      }
    }
  }
}
