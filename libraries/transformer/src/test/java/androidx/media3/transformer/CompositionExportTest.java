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

import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_ONLY;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static androidx.media3.transformer.TestUtil.FILE_VIDEO_ONLY;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createAudioEffects;
import static androidx.media3.transformer.TestUtil.createTransformerBuilder;
import static androidx.media3.transformer.TestUtil.createVolumeScalingAudioProcessor;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a {@link Composition} containing multiple {@link
 * EditedMediaItemSequence} instances with {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public class CompositionExportTest {
  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();
  private final CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory();

  @Before
  public void setUp() {
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addAudioEncoders(MimeTypes.AUDIO_AAC);
  }

  @After
  public void tearDown() {
    removeEncodersAndDecoders();
  }

  @Test
  public void start_audioVideoTransmuxedFromDifferentSequences_matchesSingleSequenceResult()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(audioEditedMediaItem),
                new EditedMediaItemSequence(videoEditedMediaItem))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();
    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_loopingTransmuxedAudio_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence(ImmutableList.of(audioEditedMediaItem), /* isLooping= */ true);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            videoEditedMediaItem, videoEditedMediaItem, videoEditedMediaItem);
    Composition composition =
        new Composition.Builder(loopingAudioSequence, videoSequence)
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(6);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_ONLY,
            /* modifications...= */ "looping",
            "mixedWith",
            getFileName(FILE_VIDEO_ONLY)));
  }

  @Test
  public void start_loopingTransmuxedVideo_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence loopingVideoSequence =
        new EditedMediaItemSequence(ImmutableList.of(videoEditedMediaItem), /* isLooping= */ true);
    Composition composition =
        new Composition.Builder(audioSequence, loopingVideoSequence)
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_VIDEO_ONLY,
            /* modifications...= */ "looping",
            "mixedWith",
            getFileName(FILE_AUDIO_ONLY)));
  }

  @Test
  public void start_longVideoCompositionWithLoopingAudio_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(
                new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
                    .build()),
            /* isLooping= */ true);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(videoEditedMediaItem, videoEditedMediaItem);
    Composition composition =
        new Composition.Builder(loopingAudioSequence, videoSequence).setTransmuxVideo(true).build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.durationMs).isEqualTo(31_053);
    // FILE_AUDIO_RAW duration is 1000ms. Input 32 times to cover the 31_053ms duration.
    assertThat(exportResult.processedInputs).hasSize(34);
    assertThat(exportResult.channelCount).isEqualTo(1);
    assertThat(exportResult.fileSizeBytes).isEqualTo(5292662);
  }

  @Test
  public void start_compositionOfConcurrentAudio_isCorrect() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem rawAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(rawAudioEditedMediaItem),
                new EditedMediaItemSequence(rawAudioEditedMediaItem))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW, /* modifications...= */ "mixed", getFileName(FILE_AUDIO_RAW)));
  }

  @Test
  public void start_audioVideoCompositionWithExtraAudio_isCorrect() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(audioVideoEditedMediaItem),
                new EditedMediaItemSequence(audioEditedMediaItem))
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "mixed",
            getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)));
  }

  @Test
  public void start_audioVideoCompositionWithMutedAudio_matchesSingleSequence() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem mutedAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setEffects(createAudioEffects(createVolumeScalingAudioProcessor(0f)))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence loopingMutedAudioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(mutedAudioEditedMediaItem), /* isLooping= */ true);

    transformer.start(
        new Composition.Builder(
                new EditedMediaItemSequence(
                    audioVideoEditedMediaItem,
                    audioVideoEditedMediaItem,
                    audioVideoEditedMediaItem),
                loopingMutedAudioSequence)
            .setTransmuxVideo(true)
            .build(),
        outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW_VIDEO, /* modifications...= */ "sequence", "repeated3Times"));
  }

  @Test
  public void start_audioVideoCompositionWithLoopingAudio_isCorrect() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence audioVideoSequence =
        new EditedMediaItemSequence(
            audioVideoEditedMediaItem, audioVideoEditedMediaItem, audioVideoEditedMediaItem);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence(ImmutableList.of(audioEditedMediaItem), /* isLooping= */ true);
    Composition composition =
        new Composition.Builder(audioVideoSequence, loopingAudioSequence)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "sequence",
            "repeated3Times",
            "mixed",
            "loopingAudio" + getFileName(FILE_AUDIO_RAW_VIDEO)));
  }

  private static String getFileName(String filePath) {
    int lastSeparator = filePath.lastIndexOf("/");
    return filePath.substring(lastSeparator + 1);
  }
}
