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
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.createTransformerBuilder;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
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
 * End-to-end test for exporting a {@link Composition} containing multiple {@link
 * EditedMediaItemSequence} instances with {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public class CompositionExportTest {

  private String outputPath;
  private TestMuxerHolder testMuxerHolder;

  @Before
  public void setUp() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    testMuxerHolder = new TestUtil.TestMuxerFactory.TestMuxerHolder();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
  }

  @Test
  public void start_audioVideoTransmuxedFromDifferentSequences_producesExpectedResult()
      throws Exception {
    Transformer transformer =
        createTransformerBuilder(testMuxerHolder, /* enableFallback= */ false).build();
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
}
