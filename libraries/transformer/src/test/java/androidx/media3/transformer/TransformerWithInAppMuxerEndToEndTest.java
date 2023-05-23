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

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end test for {@link Transformer} with {@link InAppMuxer}. */
@RunWith(AndroidJUnit4.class)
public class TransformerWithInAppMuxerEndToEndTest {
  private static final String MP4_FILE_ASSET_DIRECTORY = "asset:///media/";
  private static final String H264_MP4 = "mp4/sample.mp4";
  private Context context;
  private String outputPath;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
  }

  @Test
  public void transmux_withLocationMetadata_outputMatchedExpected() throws Exception {
    String outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory(
            DefaultMuxer.Factory.DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS,
            metadataEntries -> {
              metadataEntries.removeIf((Metadata.Entry entry) -> entry instanceof Mp4LocationData);
              metadataEntries.add(new Mp4LocationData(/* latitude= */ 45f, /* longitude= */ -90f));
            });
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_ASSET_DIRECTORY + H264_MP4));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of()))
            .build();

    transformer.start(editedMediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(), checkNotNull(outputPath));
    // [xyz: latitude=45.0, longitude=-90.0] in track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(H264_MP4 + ".with_location_metadata"));
  }
}
