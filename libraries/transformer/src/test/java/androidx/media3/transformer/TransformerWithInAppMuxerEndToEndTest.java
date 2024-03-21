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

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath;
import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.XmpData;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Predicate;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** End-to-end test for {@link Transformer} with {@link InAppMuxer}. */
@RunWith(AndroidJUnit4.class)
public class TransformerWithInAppMuxerEndToEndTest {
  private static final String MP4_FILE_PATH = "asset:///media/mp4/sample_no_bframes.mp4";
  private static final String MP4_FILE_NAME = "mp4/sample_no_bframes.mp4";

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();
  private String outputPath;

  @Before
  public void setup() throws Exception {
    outputPath = outputDir.newFile().getPath();
  }

  @Test
  public void transmux_mp4File_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries ->
                    // Add timestamp to make output file deterministic.
                    metadataEntries.add(
                        new Mp4TimestampData(
                            /* creationTimestampSeconds= */ 3_000_000_000L,
                            /* modificationTimestampSeconds= */ 4_000_000_000L)))
            .build();

    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        extractAllSamplesFromFilePath(
            new Mp4Extractor(new DefaultSubtitleParserFactory()), outputPath);
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ MP4_FILE_NAME,
            /* modifications...= */ "transmuxed_with_inappmuxer"));
  }

  @Test
  public void transmux_tsFileHavingThreeByteNalStartCode_outputMatchesExpected() throws Exception {
    String tsFilePath = "asset:///media/ts/sample_no_bframes.ts";
    String tsFileName = "ts/sample_no_bframes.ts";
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries ->
                    // Add timestamp to make output file deterministic.
                    metadataEntries.add(
                        new Mp4TimestampData(
                            /* creationTimestampSeconds= */ 3_000_000_000L,
                            /* modificationTimestampSeconds= */ 4_000_000_000L)))
            .build();

    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(tsFilePath))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(2_000).build())
            .build();
    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        extractAllSamplesFromFilePath(
            new Mp4Extractor(new DefaultSubtitleParserFactory()), outputPath);
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ tsFileName,
            /* modifications...= */ "transmuxed_with_inappmuxer"));
  }

  @Test
  public void transmux_withLocationMetadata_writesSameLocationMetadata() throws Exception {
    Mp4LocationData expectedLocationData =
        new Mp4LocationData(/* latitude= */ 45f, /* longitude= */ -90f);
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries -> {
                  metadataEntries.removeIf(
                      (Metadata.Entry entry) -> entry instanceof Mp4LocationData);
                  metadataEntries.add(expectedLocationData);
                })
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    Mp4LocationData actualLocationData =
        (Mp4LocationData)
            retrieveMetadata(context, outputPath, entry -> entry instanceof Mp4LocationData);
    assertThat(actualLocationData).isEqualTo(expectedLocationData);
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
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    // TODO: b/288544833 - Use FakeExtractorOutput once it starts dumping uuid box.
    assertThat(exportResult.exportException).isNull();
  }

  @Test
  public void transmux_withCaptureFps_writesSameCaptureFps() throws Exception {
    float captureFps = 60.0f;
    MdtaMetadataEntry expectedCaptureFps =
        new MdtaMetadataEntry(
            MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS,
            /* value= */ Util.toByteArray(captureFps),
            MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32);
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(metadataEntries -> metadataEntries.add(expectedCaptureFps))
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    MdtaMetadataEntry actualCaptureFps =
        (MdtaMetadataEntry)
            retrieveMetadata(
                context,
                outputPath,
                entry ->
                    entry instanceof MdtaMetadataEntry
                        && ((MdtaMetadataEntry) entry).key.equals(expectedCaptureFps.key));
    assertThat(actualCaptureFps).isEqualTo(expectedCaptureFps);
  }

  @Test
  public void transmux_withTimestampData_writesSameTimestampData() throws Exception {
    Mp4TimestampData expectedTimestampData =
        new Mp4TimestampData(
            /* creationTimestampSeconds= */ 3_000_000_000L,
            /* modificationTimestampSeconds= */ 4_000_000_000L);
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(metadataEntries -> metadataEntries.add(expectedTimestampData))
            .build();

    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    Mp4TimestampData actualTimestampData =
        (Mp4TimestampData)
            retrieveMetadata(context, outputPath, entry -> entry instanceof Mp4TimestampData);
    assertThat(actualTimestampData.creationTimestampSeconds)
        .isEqualTo(expectedTimestampData.creationTimestampSeconds);
    assertThat(actualTimestampData.modificationTimestampSeconds)
        .isEqualTo(expectedTimestampData.modificationTimestampSeconds);
  }

  @Test
  public void transmux_withCustomMetadata_writesSameCustomMetadata() throws Exception {
    MdtaMetadataEntry expectedStringMetadata =
        new MdtaMetadataEntry(
            "StringKey", Util.getUtf8Bytes("StringValue"), MdtaMetadataEntry.TYPE_INDICATOR_STRING);
    MdtaMetadataEntry expectedFloatMetadata =
        new MdtaMetadataEntry(
            "FloatKey",
            /* value= */ Util.toByteArray(600.0f),
            MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32);
    Muxer.Factory inAppMuxerFactory =
        new InAppMuxer.Factory.Builder()
            .setMetadataProvider(
                metadataEntries -> {
                  metadataEntries.add(expectedStringMetadata);
                  metadataEntries.add(expectedFloatMetadata);
                })
            .build();
    Transformer transformer =
        new Transformer.Builder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMuxerFactory(inAppMuxerFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_FILE_PATH));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    MdtaMetadataEntry actualStringMetadata =
        (MdtaMetadataEntry)
            retrieveMetadata(
                context,
                outputPath,
                entry ->
                    entry instanceof MdtaMetadataEntry
                        && ((MdtaMetadataEntry) entry).key.equals(expectedStringMetadata.key));
    assertThat(actualStringMetadata).isEqualTo(expectedStringMetadata);
    MdtaMetadataEntry actualFloatMetadata =
        (MdtaMetadataEntry)
            retrieveMetadata(
                context,
                outputPath,
                entry ->
                    entry instanceof MdtaMetadataEntry
                        && ((MdtaMetadataEntry) entry).key.equals(expectedFloatMetadata.key));
    assertThat(actualFloatMetadata).isEqualTo(expectedFloatMetadata);
  }

  /**
   * Returns specific {@linkplain Metadata.Entry metadata} from the media file.
   *
   * @param context The application context.
   * @param filePath The path of the media file.
   * @param predicate The {@link Predicate} to be used to retrieve the {@linkplain Metadata.Entry
   *     metadata}.
   * @return The {@linkplain Metadata.Entry metadata}.
   */
  @Nullable
  private static Metadata.Entry retrieveMetadata(
      Context context, @Nullable String filePath, Predicate<Metadata.Entry> predicate)
      throws ExecutionException, InterruptedException {
    Format videoTrackFormat = retrieveTrackFormat(context, filePath, C.TRACK_TYPE_VIDEO);
    @Nullable
    Metadata.Entry metadataEntryFromVideoTrack = findMetadataEntry(videoTrackFormat, predicate);
    Format audioTrackFormat = retrieveTrackFormat(context, filePath, C.TRACK_TYPE_AUDIO);
    @Nullable
    Metadata.Entry metadataEntryFromAudioTrack = findMetadataEntry(audioTrackFormat, predicate);

    ensureSameMetadataAcrossTracks(metadataEntryFromVideoTrack, metadataEntryFromAudioTrack);

    return metadataEntryFromVideoTrack != null
        ? metadataEntryFromVideoTrack
        : metadataEntryFromAudioTrack;
  }

  private static void ensureSameMetadataAcrossTracks(
      @Nullable Metadata.Entry firstTrackMetadata, @Nullable Metadata.Entry secondTrackMetadata) {
    // If same metadata is present in both audio and video track, then they must be same.
    if (firstTrackMetadata != null && secondTrackMetadata != null) {
      checkState(firstTrackMetadata.equals(secondTrackMetadata));
    }
  }

  @Nullable
  private static Metadata.Entry findMetadataEntry(
      Format format, Predicate<Metadata.Entry> predicate) {
    if (format.metadata == null) {
      return null;
    }

    for (int i = 0; i < format.metadata.length(); i++) {
      Metadata.Entry metadataEntry = format.metadata.get(i);
      if (predicate.apply(metadataEntry)) {
        return metadataEntry;
      }
    }
    return null;
  }
}
