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
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.XmpData;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** End-to-end test for {@link Transformer} with {@link InAppMuxer}. */
@RunWith(AndroidJUnit4.class)
public class TransformerWithInAppMuxerEndToEndTest {
  private static final String MP4_FILE = "asset:///media/mp4/sample_no_bframes.mp4";
  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();
  private String outputPath;

  @Before
  public void setup() throws Exception {
    outputPath = outputDir.newFile().getPath();
  }

  @Test
  public void transmux_withLocationMetadata_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries -> {
                  metadataEntries.removeIf(
                      (Metadata.Entry entry) -> entry instanceof Mp4LocationData);
                  metadataEntries.add(
                      new Mp4LocationData(/* latitude= */ 45f, /* longitude= */ -90f));
                })
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(), checkNotNull(outputPath));
    // [xyz: latitude=45.0, longitude=-90.0] in track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO,
            /* modifications...= */ "with_location_metadata"));
  }

  @Test
  public void transmux_withXmpData_completesSuccessfully() throws Exception {
    String xmpSampleData = "media/xmp/sample_datetime_xmp.xmp";
    byte[] xmpData = androidx.media3.test.utils.TestUtil.getByteArray(context, xmpSampleData);
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(metadataEntries -> metadataEntries.add(new XmpData(xmpData)))
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE));

    transformer.start(mediaItem, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    // TODO: b/288544833 - Use FakeExtractorOutput once it starts dumping uuid box.
    assertThat(exportResult.exportException).isNull();
  }

  @Test
  public void transmux_withCaptureFps_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries -> {
                  float captureFps = 60.0f;
                  metadataEntries.add(
                      new MdtaMetadataEntry(
                          MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS,
                          /* value= */ Util.toByteArray(captureFps),
                          /* localeIndicator= */ 0,
                          MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32));
                })
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(), checkNotNull(outputPath));
    // [mdta: key=com.android.capture.fps, value=60.0] in video track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "with_capture_fps"));
  }

  @Test
  public void transmux_withCreationTime_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries ->
                    metadataEntries.add(
                        new Mp4TimestampData(/* creationTimestampSeconds= */ 2_000_000_000L)))
            .build();

    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(), checkNotNull(outputPath));
    // [Creation time: 2_000_000_000_000] in track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */
            "with_creation_time"));
  }

  @Test
  public void transmux_withCustomeMetadata_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries -> {
                  String stringKey = "StringKey";
                  String stringValue = "StringValue";
                  metadataEntries.add(
                      new MdtaMetadataEntry(
                          stringKey,
                          Util.getUtf8Bytes(stringValue),
                          /* localeIndicator= */ 0,
                          MdtaMetadataEntry.TYPE_INDICATOR_STRING));
                  String floatKey = "FloatKey";
                  float floatValue = 600.0f;
                  metadataEntries.add(
                      new MdtaMetadataEntry(
                          floatKey,
                          Util.toByteArray(floatValue),
                          /* localeIndicator= */ 0,
                          MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32));
                })
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(), checkNotNull(outputPath));
    // [mdta: key=StringKey, value=StringValue, mdta: key=FloatKey, value=600.0] in track metadata
    // dump
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */
            "with_custom_metadata"));
  }
}
