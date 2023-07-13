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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static androidx.media3.transformer.TestUtil.createEncodersAndDecoders;
import static androidx.media3.transformer.TestUtil.createPitchChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.createTransformerBuilder;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;

import android.content.Context;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Util;
import androidx.media3.effect.RgbFilter;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.transformer.TestUtil.TestMuxerFactory.TestMuxerHolder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a single {@link EditedMediaItemSequence} containing multiple {@link
 * EditedMediaItem} instances with {@link Transformer}.
 *
 * <p>Video tracks can not be processed by Robolectric, as the muxer audio/video interleaving means
 * it waits for more audio samples before writing video samples. Robolectric decoders (currently)
 * just copy input buffers to the output. Audio timestamps are computed based on the amount of data
 * passed through (see [internal: b/178685617]), so are much smaller than expected because they are
 * based on encoded samples. As a result, input files with video and audio must either remove or
 * transmux the video.
 */
@RunWith(AndroidJUnit4.class)
public final class SequenceExportTest {

  private Context context;
  private String outputPath;
  private TestMuxerHolder testMuxerHolder;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    testMuxerHolder = new TestMuxerHolder();
    createEncodersAndDecoders();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void start_concatenateSameMediaItemWithTransmux_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated_transmux"));
  }

  @Test
  public void start_concatenateSameMediaItemWithEffectsAndTransmux_ignoresEffects()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    SonicAudioProcessor sonicAudioProcessor = createPitchChangingAudioProcessor(/* pitch= */ 2f);
    Effect videoEffect = RgbFilter.createGrayscaleFilter();
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), ImmutableList.of(videoEffect));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_VIDEO + ".concatenated_transmux"));
  }

  @Test
  public void start_concatenateClippedMediaItemsWithTransmux_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem.ClippingConfiguration clippingConfiguration1 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0) // Corresponds to key frame.
            .setEndPositionMs(500)
            .build();
    MediaItem mediaItem1 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration1)
            .build();
    EditedMediaItem editedMediaItem1 = new EditedMediaItem.Builder(mediaItem1).build();
    MediaItem.ClippingConfiguration clippingConfiguration2 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(12_500) // Corresponds to key frame.
            .setEndPositionMs(14_000)
            .build();
    MediaItem mediaItem2 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration2)
            .build();
    EditedMediaItem editedMediaItem2 = new EditedMediaItem.Builder(mediaItem2).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem1, editedMediaItem2));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(
            FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S + ".clipped_concatenated_transmux"));
  }

  @Test
  public void start_concatenateSilenceAndAudioWithTransmuxVideo_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem videoOnlyMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    EditedMediaItem audioVideoMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(videoOnlyMediaItem, audioVideoMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(sequence))
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW_VIDEO + ".silence_then_audio"));
  }

  @Test
  public void start_concatenateSilenceAndAudioWithEffectsAndTransmuxVideo_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    SonicAudioProcessor sonicAudioProcessor = createPitchChangingAudioProcessor(/* pitch= */ 2f);
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), /* videoEffects= */ ImmutableList.of());
    EditedMediaItem noAudioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).setEffects(effects).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(noAudioEditedMediaItem, audioEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(sequence))
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW_VIDEO + ".silence_then_audio_with_effects"));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormat_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(audioOnlyMediaItem).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW + ".concatenated"));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormatAndSameEffects_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    SonicAudioProcessor sonicAudioProcessor = createPitchChangingAudioProcessor(/* pitch= */ 2f);
    Effects effects =
        new Effects(ImmutableList.of(sonicAudioProcessor), /* videoEffects= */ ImmutableList.of());
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem).setEffects(effects).build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem, editedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW + ".concatenated_high_pitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormatAndDiffEffects_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    Effects highPitchEffects =
        new Effects(
            ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 2f)),
            /* videoEffects= */ ImmutableList.of());
    EditedMediaItem highPitchMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(highPitchEffects)
            .build();
    Effects lowPitchEffects =
        new Effects(
            ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 0.5f)),
            /* videoEffects= */ ImmutableList.of());
    EditedMediaItem lowPitchMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(lowPitchEffects)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(highPitchMediaItem, lowPitchMediaItem));
    Composition composition = new Composition.Builder(ImmutableList.of(sequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW + ".high_pitch_then_low_pitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormat_completesSuccessfully() throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
    MediaItem stereo48000Audio =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ);
    MediaItem mono44100Audio = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(
                new EditedMediaItem.Builder(stereo48000Audio).build(),
                new EditedMediaItem.Builder(mono44100Audio).build()));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW_STEREO_48000KHZ + "_then_sample.wav"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormatAndSameEffects_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();

    Effects highPitch =
        new Effects(
            ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 2f)),
            /* videoEffects= */ ImmutableList.of());

    EditedMediaItem stereo48000Audio =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setEffects(highPitch)
            .build();
    EditedMediaItem mono44100Audio =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setEffects(highPitch)
            .build();

    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(ImmutableList.of(stereo48000Audio, mono44100Audio));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW_STEREO_48000KHZ + "-high_pitch_then_sample.wav-high_pitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormatAndDiffEffects_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();

    EditedMediaItem stereo48000AudioHighPitch =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setEffects(
                new Effects(
                    ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 2f)),
                    /* videoEffects= */ ImmutableList.of()))
            .build();
    EditedMediaItem mono44100AudioLowPitch =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setEffects(
                new Effects(
                    ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 0.5f)),
                    /* videoEffects= */ ImmutableList.of()))
            .build();
    EditedMediaItemSequence editedMediaItemSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(stereo48000AudioHighPitch, mono44100AudioLowPitch));
    Composition composition =
        new Composition.Builder(ImmutableList.of(editedMediaItemSequence)).build();

    transformer.start(composition, outputPath);
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        checkNotNull(testMuxerHolder.testMuxer),
        getDumpFileName(FILE_AUDIO_RAW_STEREO_48000KHZ + "-high_pitch_then_sample.wav-low_pitch"));
  }
}
