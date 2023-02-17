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

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runLooperUntil;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static com.google.android.exoplayer2.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
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
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.SilenceSkippingAudioProcessor;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.effect.ScaleAndRotateTransformation;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodec;
import org.robolectric.shadows.ShadowMediaCodecList;

/** End-to-end test for {@link Transformer}. */
@RunWith(AndroidJUnit4.class)
public final class TransformerEndToEndTest {

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
  private TestMuxer testMuxer;
  private FakeClock clock;
  private ProgressHolder progressHolder;
  private ArgumentCaptor<Composition> compositionArgumentCaptor;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    clock = new FakeClock(/* isAutoAdvancing= */ true);
    progressHolder = new ProgressHolder();
    compositionArgumentCaptor = ArgumentCaptor.forClass(Composition.class);
    createEncodersAndDecoders();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void start_videoOnlyPassthrough_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void start_audioOnlyPassthrough_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_ENCODER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_ENCODER));
  }

  @Test
  public void start_audioOnlyTranscoding_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AAC) // supported by encoder and muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_ENCODER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_ENCODER + ".aac"));
  }

  @Test
  public void start_audioAndVideo_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_audioAndVideo_withClippingStartAtKeyFrame_completesSuccessfully()
      throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
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

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        testMuxer,
        getDumpFileName(FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S + ".clipped"));
  }

  @Test
  public void start_withSubtitles_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SUBTITLES);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_WITH_SUBTITLES));
  }

  @Test
  public void start_successiveExports_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    // Transform first media item.
    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);
    Files.delete(Paths.get(outputPath));

    // Transform second media item.
    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_concurrentExports_throwsError() {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.start(mediaItem, outputPath);

    assertThrows(IllegalStateException.class, () -> transformer.start(mediaItem, outputPath));
  }

  @Test
  public void start_removeAudio_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveAudio(true)
            .build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".noaudio"));
  }

  @Test
  public void start_removeVideo_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveVideo(true)
            .build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".novideo"));
  }

  @Test
  public void start_forceAudioTrackOnAudioOnly_isIgnored() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_ENCODER);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    Composition composition =
        new Composition(
            ImmutableList.of(sequence), Effects.EMPTY, /* experimentalForceAudioTrack= */ true);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_ENCODER));
  }

  @Test
  public void start_forceAudioTrackOnAudioVideo_isIgnored() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    Composition composition =
        new Composition(
            ImmutableList.of(sequence), Effects.EMPTY, /* experimentalForceAudioTrack= */ true);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_forceAudioTrackAndRemoveAudio_generatesSilentAudio() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    Composition composition =
        new Composition(
            ImmutableList.of(sequence), Effects.EMPTY, /* experimentalForceAudioTrack= */ true);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".silentaudio"));
  }

  @Test
  public void start_forceAudioTrackAndRemoveVideo_isIgnored() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    Composition composition =
        new Composition(
            ImmutableList.of(sequence), Effects.EMPTY, /* experimentalForceAudioTrack= */ true);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);
    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".novideo"));
  }

  @Test
  public void start_forceAudioTrackOnVideoOnly_generatesSilentAudio() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem));
    Composition composition =
        new Composition(
            ImmutableList.of(sequence), Effects.EMPTY, /* experimentalForceAudioTrack= */ true);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY + ".silentaudio"));
  }

  @Test
  public void start_adjustSampleRate_completesSuccessfully() throws Exception {
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    ImmutableList<AudioProcessor> audioProcessors = ImmutableList.of(sonicAudioProcessor);
    Effects effects = new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".48000hz"));
  }

  @Test
  public void start_concatenateMediaItemsWithSameFormat_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).setTransmux(true).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(Effects.EMPTY).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition(ImmutableList.of(editedMediaItemSequence), Effects.EMPTY);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated"));
  }

  @Test
  public void start_concatenateMediaItemsWithSameFormatAndEffects_completesSuccessfully()
      throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    AudioProcessor audioProcessor = new SilenceSkippingAudioProcessor();
    Effects effects =
        new Effects(ImmutableList.of(audioProcessor), /* videoEffects= */ ImmutableList.of());
    // The video track must be removed in order for the export to end. Indeed, the
    // Robolectric decoder just copies the input buffers to the output and the audio timestamps are
    // therefore computed based on the encoded samples. As a result, the audio timestamps are much
    // smaller than they should be and the muxer waits for more audio samples before writing video
    // samples.
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).setRemoveVideo(true).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition(ImmutableList.of(editedMediaItemSequence), Effects.EMPTY);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".silence_skipped_concatenated"));
  }

  @Test
  public void start_singleMediaItemAndTransmux_ignoresTransmux() throws Exception {
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).setTransmux(true).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    ImmutableList<AudioProcessor> audioProcessors = ImmutableList.of(sonicAudioProcessor);
    Effects effects = new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".48000hz"));
  }

  @Test
  public void start_multipleMediaItemsWithEffectsAndTransmux_ignoresTransmux() throws Exception {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).setTransmux(true).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    AudioProcessor audioProcessor = new SilenceSkippingAudioProcessor();
    Effects effects =
        new Effects(ImmutableList.of(audioProcessor), /* videoEffects= */ ImmutableList.of());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).setRemoveVideo(true).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition(ImmutableList.of(editedMediaItemSequence), Effects.EMPTY);

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    // The inputs should be transcoded even though transmuxing has been requested. This is because
    // audio effects have been added to the first MediaItem in the sequence, so the transcoding
    // audio sample pipeline should be picked to apply these effects.
    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".silence_skipped_concatenated"));
  }

  @Test
  public void start_withMultipleListeners_callsEachOnCompletion() throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    verify(mockListener1).onCompleted(compositionArgumentCaptor.capture(), any());
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2).onCompleted(eq(composition), any());
    verify(mockListener3).onCompleted(eq(composition), any());
  }

  @Test
  public void start_withMultipleListeners_callsEachOnError() {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .setTransformationRequest( // Request transcoding so that decoder is used.
                new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_DECODER);

    transformer.start(mediaItem, outputPath);
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));

    verify(mockListener1).onError(compositionArgumentCaptor.capture(), any(), eq(exception));
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2).onError(eq(composition), any(), eq(exception));
    verify(mockListener3).onError(eq(composition), any(), eq(exception));
  }

  @Test
  public void start_withMultipleListeners_callsEachOnFallback() throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ true)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    verify(mockListener1)
        .onFallbackApplied(
            compositionArgumentCaptor.capture(),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2)
        .onFallbackApplied(
            composition, originalTransformationRequest, fallbackTransformationRequest);
    verify(mockListener3)
        .onFallbackApplied(
            composition, originalTransformationRequest, fallbackTransformationRequest);
  }

  @Test
  public void start_success_callsDeprecatedCompletionCallbacks() throws Exception {
    AtomicBoolean deprecatedFallbackCalled1 = new AtomicBoolean();
    AtomicBoolean deprecatedFallbackCalled2 = new AtomicBoolean();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationCompleted(MediaItem inputMediaItem) {
                    deprecatedFallbackCalled1.set(true);
                  }
                })
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationCompleted(
                      MediaItem inputMediaItem, TransformationResult result) {
                    deprecatedFallbackCalled2.set(true);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    assertThat(deprecatedFallbackCalled1.get()).isTrue();
    assertThat(deprecatedFallbackCalled2.get()).isTrue();
  }

  @Test
  public void start_withError_callsDeprecatedErrorCallbacks() throws Exception {
    AtomicBoolean deprecatedFallbackCalled1 = new AtomicBoolean();
    AtomicBoolean deprecatedFallbackCalled2 = new AtomicBoolean();
    AtomicBoolean deprecatedFallbackCalled3 = new AtomicBoolean();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationError(MediaItem inputMediaItem, Exception exception) {
                    deprecatedFallbackCalled1.set(true);
                  }
                })
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationError(
                      MediaItem inputMediaItem, TransformationException exception) {
                    deprecatedFallbackCalled2.set(true);
                  }
                })
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationError(
                      MediaItem inputMediaItem,
                      TransformationResult result,
                      TransformationException exception) {
                    deprecatedFallbackCalled3.set(true);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri("invalid.uri");

    transformer.start(mediaItem, outputPath);
    try {
      TransformerTestRunner.runLooper(transformer);
    } catch (ExportException exportException) {
      // Ignore exception thrown.
    }

    assertThat(deprecatedFallbackCalled1.get()).isTrue();
    assertThat(deprecatedFallbackCalled2.get()).isTrue();
    assertThat(deprecatedFallbackCalled3.get()).isTrue();
  }

  @Test
  public void start_withFallback_callsDeprecatedFallbackCallbacks() throws Exception {
    AtomicBoolean deprecatedFallbackCalled = new AtomicBoolean();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ true)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    deprecatedFallbackCalled.set(true);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    assertThat(deprecatedFallbackCalled.get()).isTrue();
  }

  @Test
  public void start_afterBuildUponWithListenerRemoved_onlyCallsRemainingListeners()
      throws Exception {
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer1 =
        createTransformerBuilder(/* enableFallback= */ false)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    Transformer transformer2 = transformer1.buildUpon().removeListener(mockListener2).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer2.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer2);

    verify(mockListener1).onCompleted(compositionArgumentCaptor.capture(), any());
    verify(mockListener2, never()).onCompleted(any(Composition.class), any());
    verify(mockListener3).onCompleted(eq(compositionArgumentCaptor.getValue()), any());
  }

  @Test
  public void start_flattenForSlowMotion_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SEF_SLOW_MOTION))
            .setFlattenForSlowMotion(true)
            .build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_WITH_SEF_SLOW_MOTION));
  }

  @Test
  public void start_completesWithValidBitrate() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.averageAudioBitrate).isGreaterThan(0);
    assertThat(exportResult.averageVideoBitrate).isGreaterThan(0);
  }

  @Test
  public void start_withAudioEncoderFormatUnsupported_completesWithError() {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(
                        MimeTypes.AUDIO_AMR_NB) // unsupported by encoder, supported by muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.start(mediaItem, outputPath);
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode)
        .isEqualTo(ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);
  }

  @Test
  public void start_withAudioDecoderFormatUnsupported_completesWithError() {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AAC) // supported by encoder and muxer
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_DECODER);

    transformer.start(mediaItem, outputPath);
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode)
        .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
  }

  @Test
  public void start_withIoError_completesWithError() {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri("asset:///non-existing-path.mp4");

    transformer.start(mediaItem, outputPath);
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().hasCauseThat().isInstanceOf(IOException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_IO_FILE_NOT_FOUND);
  }

  @Test
  public void start_withAudioMuxerFormatUnsupported_completesSuccessfully() throws Exception {
    // Test succeeds because MIME type fallback is mandatory.
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).addListener(mockListener).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_MUXER + ".fallback"));
    verify(mockListener)
        .onFallbackApplied(
            any(Composition.class),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
  }

  @Test
  public void start_withAudioMuxerFormatFallback_completesSuccessfully() throws Exception {
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ true).addListener(mockListener).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_UNSUPPORTED_BY_MUXER);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_UNSUPPORTED_BY_MUXER + ".fallback"));
    verify(mockListener)
        .onFallbackApplied(
            any(Composition.class),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
  }

  @Test
  public void start_withSlowOutputSampleRate_completesWithError() {
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(
            context, new SlowExtractorsFactory(/* delayBetweenReadsMs= */ 10));
    Codec.DecoderFactory decoderFactory = new DefaultDecoderFactory(context);
    AssetLoader.Factory assetLoaderFactory =
        new ExoPlayerAssetLoader.Factory(
            context,
            decoderFactory,
            /* forceInterpretHdrAsSdr= */ false,
            clock,
            mediaSourceFactory);
    Muxer.Factory muxerFactory = new TestMuxerFactory(/* maxDelayBetweenSamplesMs= */ 1);
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setMuxerFactory(muxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_MUXING_FAILED);
  }

  @Test
  public void start_withUnsetMaxDelayBetweenSamples_completesSuccessfully() throws Exception {
    Muxer.Factory muxerFactory = new TestMuxerFactory(/* maxDelayBetweenSamplesMs= */ C.TIME_UNSET);
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_afterCancellation_completesSuccessfully() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    transformer.cancel();
    Files.delete(Paths.get(outputPath));

    // This would throw if the previous export had not been cancelled.
    transformer.start(mediaItem, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    // TODO(b/264974805): Make export output deterministic and check it against dump file.
    assertThat(exportResult.exportException).isNull();
  }

  @Test
  public void start_fromSpecifiedThread_completesSuccessfully() throws Exception {
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    anotherThread.start();
    Looper looper = anotherThread.getLooper();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false).setLooper(looper).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    AtomicReference<Exception> exception = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    new Handler(looper)
        .post(
            () -> {
              try {
                transformer.start(mediaItem, outputPath);
                TransformerTestRunner.runLooper(transformer);
              } catch (Exception e) {
                exception.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(exception.get()).isNull();
    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.start(mediaItem, outputPath);
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
  public void start_withAssetLoaderAlwaysDecoding_pipelineExpectsDecoded() throws Exception {
    AtomicReference<SampleConsumer> sampleConsumerRef = new AtomicReference<>();
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setAssetLoaderFactory(
                new FakeAssetLoader.Factory(SUPPORTED_OUTPUT_TYPE_DECODED, sampleConsumerRef))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputPath);
    runLooperUntil(transformer.getApplicationLooper(), () -> sampleConsumerRef.get() != null);

    assertThat(sampleConsumerRef.get().expectsDecodedData()).isTrue();
  }

  @Test
  public void start_withAssetLoaderNotDecodingAndDecodingNeeded_completesWithError() {
    Transformer transformer =
        createTransformerBuilder(/* enableFallback= */ false)
            .setAssetLoaderFactory(
                new FakeAssetLoader.Factory(
                    SUPPORTED_OUTPUT_TYPE_ENCODED, /* sampleConsumerRef= */ null))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    ImmutableList<AudioProcessor> audioProcessors = ImmutableList.of(new SonicAudioProcessor());
    Effects effects = new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputPath);
    ExportException exportException =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));

    assertThat(exportException).hasCauseThat().isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void start_withNoOpEffects_transmuxes() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    int mediaItemHeightPixels = 720;
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            Presentation.createForHeight(mediaItemHeightPixels),
            new ScaleAndRotateTransformation.Builder().build());
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    // Video transcoding in unit tests is not supported.
    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void getProgress_knownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
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
              case PROGRESS_STATE_NOT_STARTED:
                if (progressState != PROGRESS_STATE_NOT_STARTED) {
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

    transformer.start(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runLooper(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_knownDuration_givesIncreasingPercentages() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    List<Integer> progresses = new ArrayList<>();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            if (progressState == PROGRESS_STATE_NOT_STARTED) {
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

    transformer.start(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runLooper(transformer);

    assertThat(progresses).isInOrder();
    if (!progresses.isEmpty()) {
      // The progress list could be empty if the export ends before any progress can be retrieved.
      assertThat(progresses.get(0)).isAtLeast(0);
      assertThat(Iterables.getLast(progresses)).isLessThan(100);
    }
  }

  @Test
  public void getProgress_noCurrentExport_returnsNotStarted() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    @Transformer.ProgressState int stateBeforeTransform = transformer.getProgress(progressHolder);
    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);
    @Transformer.ProgressState int stateAfterTransform = transformer.getProgress(progressHolder);

    assertThat(stateBeforeTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
    assertThat(stateAfterTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
  }

  @Test
  public void getProgress_unknownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
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
              case PROGRESS_STATE_NOT_STARTED:
                if (progressState != PROGRESS_STATE_NOT_STARTED) {
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

    transformer.start(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runLooper(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
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
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);
    transformer.cancel();
  }

  @Test
  public void cancel_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = createTransformerBuilder(/* enableFallback= */ false).build();
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

  private Transformer.Builder createTransformerBuilder(boolean enableFallback) {
    return new Transformer.Builder(context)
        .setClock(clock)
        .setMuxerFactory(new TestMuxerFactory())
        .setEncoderFactory(
            new DefaultEncoderFactory.Builder(context).setEnableFallback(enableFallback).build());
  }

  private static void createEncodersAndDecoders() {
    ShadowMediaCodec.CodecConfig codecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            /* codec= */ (in, out) -> out.put(in));
    addCodec(
        MimeTypes.AUDIO_AAC,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AC3,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AMR_NB,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AAC,
        codecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ false);

    ShadowMediaCodec.CodecConfig throwingCodecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            new ShadowMediaCodec.CodecConfig.Codec() {

              @Override
              public void process(ByteBuffer in, ByteBuffer out) {
                out.put(in);
              }

              @Override
              public void onConfigured(
                  MediaFormat format,
                  @Nullable Surface surface,
                  @Nullable MediaCrypto crypto,
                  int flags) {
                throw new IllegalArgumentException("Format unsupported");
              }
            });

    addCodec(
        MimeTypes.AUDIO_AMR_WB,
        throwingCodecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ true);
    addCodec(
        MimeTypes.AUDIO_AMR_NB,
        throwingCodecConfig,
        /* colorFormats= */ ImmutableList.of(),
        /* isDecoder= */ false);
  }

  private static void addCodec(
      String mimeType,
      ShadowMediaCodec.CodecConfig codecConfig,
      List<Integer> colorFormats,
      boolean isDecoder) {
    String codecName =
        Util.formatInvariant(
            isDecoder ? "exo.%s.decoder" : "exo.%s.encoder", mimeType.replace('/', '-'));
    if (isDecoder) {
      ShadowMediaCodec.addDecoder(codecName, codecConfig);
    } else {
      ShadowMediaCodec.addEncoder(codecName, codecConfig);
    }

    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
    MediaCodecInfoBuilder.CodecCapabilitiesBuilder codecCapabilities =
        MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(mediaFormat)
            .setIsEncoder(!isDecoder);

    if (!colorFormats.isEmpty()) {
      codecCapabilities.setColorFormats(Ints.toArray(colorFormats));
    }

    ShadowMediaCodecList.addCodec(
        MediaCodecInfoBuilder.newBuilder()
            .setName(codecName)
            .setIsEncoder(!isDecoder)
            .setCapabilities(codecCapabilities.build())
            .build());
  }

  private static void removeEncodersAndDecoders() {
    ShadowMediaCodec.clearCodecs();
    ShadowMediaCodecList.reset();
    EncoderUtil.clearCachedEncoders();
  }

  private static String getDumpFileName(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
  }

  private final class TestMuxerFactory implements Muxer.Factory {

    private final Muxer.Factory defaultMuxerFactory;

    public TestMuxerFactory() {
      defaultMuxerFactory = new DefaultMuxer.Factory();
    }

    public TestMuxerFactory(long maxDelayBetweenSamplesMs) {
      defaultMuxerFactory = new DefaultMuxer.Factory(maxDelayBetweenSamplesMs);
    }

    @Override
    public Muxer create(String path) throws Muxer.MuxerException {
      testMuxer = new TestMuxer(path, defaultMuxerFactory);
      return testMuxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return defaultMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
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

  private static final class FakeAssetLoader implements AssetLoader {

    public static final class Factory implements AssetLoader.Factory {

      private final @SupportedOutputTypes int supportedOutputTypes;
      @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

      public Factory(
          @SupportedOutputTypes int supportedOutputTypes,
          @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
        this.supportedOutputTypes = supportedOutputTypes;
        this.sampleConsumerRef = sampleConsumerRef;
      }

      @Override
      public AssetLoader createAssetLoader(
          EditedMediaItem editedMediaItem, Looper looper, Listener listener) {
        return new FakeAssetLoader(listener, supportedOutputTypes, sampleConsumerRef);
      }
    }

    private final AssetLoader.Listener listener;
    private final @SupportedOutputTypes int supportedOutputTypes;
    @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

    public FakeAssetLoader(
        Listener listener,
        @SupportedOutputTypes int supportedOutputTypes,
        @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
      this.listener = listener;
      this.supportedOutputTypes = supportedOutputTypes;
      this.sampleConsumerRef = sampleConsumerRef;
    }

    @Override
    public void start() {
      listener.onDurationUs(10_000_000);
      listener.onTrackCount(1);
      Format format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_AAC)
              .setSampleRate(44100)
              .setChannelCount(2)
              .build();
      try {
        SampleConsumer sampleConsumer =
            listener.onTrackAdded(
                format,
                supportedOutputTypes,
                /* streamStartPositionUs= */ 0,
                /* streamOffsetUs= */ 0);
        if (sampleConsumerRef != null) {
          sampleConsumerRef.set(sampleConsumer);
        }
      } catch (ExportException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
      return 0;
    }

    @Override
    public ImmutableMap<Integer, String> getDecoderNames() {
      return ImmutableMap.of();
    }

    @Override
    public void release() {}
  }
}
