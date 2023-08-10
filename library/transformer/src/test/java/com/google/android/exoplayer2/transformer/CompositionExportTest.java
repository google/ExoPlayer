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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.TestUtil.ASSET_URI_PREFIX;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_ONLY;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_VIDEO_ONLY;
import static com.google.android.exoplayer2.transformer.TestUtil.createEncodersAndDecoders;
import static com.google.android.exoplayer2.transformer.TestUtil.createTransformerBuilder;
import static com.google.android.exoplayer2.transformer.TestUtil.getDumpFileName;
import static com.google.android.exoplayer2.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a {@link Composition} containing multiple {@link
 * EditedMediaItemSequence} instances with {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public class CompositionExportTest {

  private Context context;
  private String outputPath;
  private CapturingMuxer.Factory muxerFactory;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    muxerFactory = new CapturingMuxer.Factory();
    createEncodersAndDecoders();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void start_audioVideoTransmuxedFromDifferentSequences_producesExpectedResult()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    transformer.start(mediaItem, outputPath);
    ExportResult expectedExportResult = TransformerTestRunner.runLooper(transformer);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(ImmutableList.of(audioEditedMediaItem));
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(ImmutableList.of(videoEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    // We can't compare the muxer output against a dump file because the asset loaders in each
    // sequence load samples from their own thread, independently of each other, which makes the
    // output non-deterministic.
    assertThat(exportResult.channelCount).isEqualTo(expectedExportResult.channelCount);
    assertThat(exportResult.videoFrameCount).isEqualTo(expectedExportResult.videoFrameCount);
    assertThat(exportResult.durationMs).isEqualTo(expectedExportResult.durationMs);
  }

  @Test
  public void start_loopingTransmuxedAudio_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem), /* isLooping= */ true);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem, videoEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(6);
    assertThat(exportResult.channelCount).isEqualTo(1);
    assertThat(exportResult.videoFrameCount).isEqualTo(90);
    assertThat(exportResult.durationMs).isEqualTo(2977);
    assertThat(exportResult.fileSizeBytes).isEqualTo(293660);
  }

  @Test
  public void start_loopingTransmuxedVideo_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem));
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem), /* isLooping= */ true);
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence))
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    assertThat(exportResult.channelCount).isEqualTo(1);
    assertThat(exportResult.videoFrameCount).isEqualTo(93);
    assertThat(exportResult.durationMs).isEqualTo(3108);
    assertThat(exportResult.fileSizeBytes).isEqualTo(337308);
  }

  @Test
  public void start_loopingRawAudio_producesExpectedResult() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItemSequence audioSequence =
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
        new EditedMediaItemSequence(ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence))
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputPath);
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

    EditedMediaItem rawAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItemSequence firstSequence =
        new EditedMediaItemSequence(ImmutableList.of(rawAudioItem));

    EditedMediaItemSequence secondSequence =
        new EditedMediaItemSequence(ImmutableList.of(rawAudioItem));

    transformer.start(
        new Composition.Builder(ImmutableList.of(firstSequence, secondSequence)).build(),
        outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_RAW + ".concurrent"));
  }

  @Test
  public void start_audioVideoCompositionWithExtraAudio_isCorrect() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence audioVideoSeq =
        new EditedMediaItemSequence(ImmutableList.of(audioVideoItem));

    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence audioSeq = new EditedMediaItemSequence(ImmutableList.of(audioItem));

    transformer.start(
        new Composition.Builder(ImmutableList.of(audioVideoSeq, audioSeq))
            .setTransmuxVideo(true)
            .build(),
        outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    assertThat(exportResult.durationMs).isEqualTo(1984);
  }

  @Test
  public void start_audioVideoCompositionWithLoopingAudio_isCorrect() throws Exception {
    Transformer transformer =
        createTransformerBuilder(muxerFactory, /* enableFallback= */ false).build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence audioVideoSeq =
        new EditedMediaItemSequence(
            ImmutableList.of(audioVideoItem, audioVideoItem, audioVideoItem));

    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence audioSeq =
        new EditedMediaItemSequence(ImmutableList.of(audioItem), /* isLooping= */ true);

    transformer.start(
        new Composition.Builder(ImmutableList.of(audioVideoSeq, audioSeq))
            .setTransmuxVideo(true)
            .build(),
        outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    assertThat(exportResult.durationMs).isEqualTo(5984);
  }
}
