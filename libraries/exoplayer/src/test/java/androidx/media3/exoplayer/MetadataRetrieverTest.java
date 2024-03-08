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

package androidx.media3.exoplayer;

import static androidx.media3.container.MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS;
import static androidx.media3.container.MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32;
import static androidx.media3.container.MdtaMetadataEntry.TYPE_INDICATOR_STRING;
import static androidx.media3.exoplayer.MetadataRetriever.retrieveMetadata;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.extractor.metadata.mp4.MotionPhotoMetadata;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import androidx.media3.extractor.metadata.mp4.SmtaMetadataEntry;
import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Tests for {@link MetadataRetriever}. */
@RunWith(AndroidJUnit4.class)
public class MetadataRetrieverTest {

  private static final long TEST_TIMEOUT_SEC = 10;

  private Context context;
  private FakeClock clock;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    clock = new FakeClock(/* isAutoAdvancing= */ true);
  }

  @Test
  public void retrieveMetadata_singleMediaItem_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"));

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(2);
    // Video group.
    assertThat(trackGroups.get(0).length).isEqualTo(1);
    assertThat(trackGroups.get(0).getFormat(0).sampleMimeType).isEqualTo(MimeTypes.VIDEO_H264);
    // Audio group.
    assertThat(trackGroups.get(1).length).isEqualTo(1);
    assertThat(trackGroups.get(1).getFormat(0).sampleMimeType).isEqualTo(MimeTypes.AUDIO_AAC);
  }

  @Test
  public void retrieveMetadata_multipleMediaItems_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem1 =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"));
    MediaItem mediaItem2 =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp3/bear-id3.mp3"));

    ListenableFuture<TrackGroupArray> trackGroupsFuture1 =
        retrieveMetadata(context, mediaItem1, clock);
    ListenableFuture<TrackGroupArray> trackGroupsFuture2 =
        retrieveMetadata(context, mediaItem2, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups1 = trackGroupsFuture1.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    TrackGroupArray trackGroups2 = trackGroupsFuture2.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    // First track group.
    assertThat(trackGroups1.length).isEqualTo(2);
    // First track group - Video group.
    assertThat(trackGroups1.get(0).length).isEqualTo(1);
    assertThat(trackGroups1.get(0).getFormat(0).sampleMimeType).isEqualTo(MimeTypes.VIDEO_H264);
    // First track group - Audio group.
    assertThat(trackGroups1.get(1).length).isEqualTo(1);
    assertThat(trackGroups1.get(1).getFormat(0).sampleMimeType).isEqualTo(MimeTypes.AUDIO_AAC);

    // Second track group.
    assertThat(trackGroups2.length).isEqualTo(1);
    // Second track group - Audio group.
    assertThat(trackGroups2.get(0).length).isEqualTo(1);
    assertThat(trackGroups2.get(0).getFormat(0).sampleMimeType).isEqualTo(MimeTypes.AUDIO_MPEG);
  }

  @Test
  public void retrieveMetadata_heicMotionPhoto_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample_MP.heic"));
    MotionPhotoMetadata expectedMotionPhotoMetadata =
        new MotionPhotoMetadata(
            /* photoStartPosition= */ 0,
            /* photoSize= */ 28_853,
            /* photoPresentationTimestampUs= */ C.TIME_UNSET,
            /* videoStartPosition= */ 28_869,
            /* videoSize= */ 28_803);

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(1);
    assertThat(trackGroups.get(0).length).isEqualTo(1);
    assertThat(trackGroups.get(0).getFormat(0).metadata.length()).isEqualTo(1);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(0))
        .isEqualTo(expectedMotionPhotoMetadata);
  }

  @Test
  public void retrieveMetadata_heicStillPhoto_outputsEmptyMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample_still_photo.heic"));

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(1);
    assertThat(trackGroups.get(0).length).isEqualTo(1);
    assertThat(trackGroups.get(0).getFormat(0).metadata).isNull();
  }

  @Test
  public void retrieveMetadata_sefSlowMotionAvc_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample_sef_slow_motion.mp4"));
    MdtaMetadataEntry expectedAndroidVersionMetadata =
        new MdtaMetadataEntry(
            /* key= */ "com.android.version",
            /* value= */ Util.getUtf8Bytes("10"),
            TYPE_INDICATOR_STRING);
    MdtaMetadataEntry expectedTemporalLayersCountMetdata =
        new MdtaMetadataEntry(
            /* key= */ "com.android.video.temporal_layers_count",
            /* value= */ Ints.toByteArray(4),
            MdtaMetadataEntry.TYPE_INDICATOR_INT32);
    SmtaMetadataEntry expectedSmtaEntry =
        new SmtaMetadataEntry(/* captureFrameRate= */ 240, /* svcTemporalLayerCount= */ 4);
    List<SlowMotionData.Segment> segments = new ArrayList<>();
    segments.add(
        new SlowMotionData.Segment(
            /* startTimeMs= */ 88, /* endTimeMs= */ 879, /* speedDivisor= */ 2));
    segments.add(
        new SlowMotionData.Segment(
            /* startTimeMs= */ 1255, /* endTimeMs= */ 1970, /* speedDivisor= */ 8));
    SlowMotionData expectedSlowMotionData = new SlowMotionData(segments);
    Mp4TimestampData expectedMp4TimestampData =
        new Mp4TimestampData(
            /* creationTimestampSeconds= */ 3_686_904_890L,
            /* modificationTimestampSeconds= */ 3_686_904_890L,
            /* timescale= */ 1000);
    MdtaMetadataEntry expectedMdtaEntry =
        new MdtaMetadataEntry(
            KEY_ANDROID_CAPTURE_FPS, /* value= */ Util.toByteArray(240.0f), TYPE_INDICATOR_FLOAT32);

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(2); // Video and audio
    // Audio
    assertThat(trackGroups.get(0).getFormat(0).metadata.length()).isEqualTo(5);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(0))
        .isEqualTo(expectedAndroidVersionMetadata);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(1))
        .isEqualTo(expectedTemporalLayersCountMetdata);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(2)).isEqualTo(expectedSlowMotionData);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(3)).isEqualTo(expectedSmtaEntry);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(4)).isEqualTo(expectedMp4TimestampData);

    // Video
    assertThat(trackGroups.get(1).getFormat(0).metadata.length()).isEqualTo(6);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(0))
        .isEqualTo(expectedAndroidVersionMetadata);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(1))
        .isEqualTo(expectedTemporalLayersCountMetdata);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(2)).isEqualTo(expectedMdtaEntry);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(3)).isEqualTo(expectedSlowMotionData);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(4)).isEqualTo(expectedSmtaEntry);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(5)).isEqualTo(expectedMp4TimestampData);
  }

  @Test
  public void retrieveMetadata_sefSlowMotionHevc_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(
            Uri.parse("asset://android_asset/media/mp4/sample_sef_slow_motion_hevc.mp4"));
    MdtaMetadataEntry expectedAndroidVersionMetadata =
        new MdtaMetadataEntry(
            /* key= */ "com.android.version",
            /* value= */ Util.getUtf8Bytes("13"),
            TYPE_INDICATOR_STRING);
    SmtaMetadataEntry expectedSmtaEntry =
        new SmtaMetadataEntry(/* captureFrameRate= */ 240, /* svcTemporalLayerCount= */ 4);
    SlowMotionData expectedSlowMotionData =
        new SlowMotionData(
            ImmutableList.of(
                new SlowMotionData.Segment(
                    /* startTimeMs= */ 2128, /* endTimeMs= */ 9856, /* speedDivisor= */ 8)));
    MdtaMetadataEntry expectedCaptureFpsMdtaEntry =
        new MdtaMetadataEntry(
            KEY_ANDROID_CAPTURE_FPS, /* value= */ Util.toByteArray(240.0f), TYPE_INDICATOR_FLOAT32);
    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(2); // Video and audio

    // Video
    Metadata videoFormatMetadata = trackGroups.get(0).getFormat(0).metadata;
    List<Metadata.Entry> videoMetadataEntries = new ArrayList<>();
    for (int i = 0; i < videoFormatMetadata.length(); i++) {
      videoMetadataEntries.add(videoFormatMetadata.get(i));
    }
    assertThat(videoMetadataEntries).contains(expectedAndroidVersionMetadata);
    assertThat(videoMetadataEntries).contains(expectedSlowMotionData);
    assertThat(videoMetadataEntries).contains(expectedSmtaEntry);
    assertThat(videoMetadataEntries).contains(expectedCaptureFpsMdtaEntry);

    // Audio
    Metadata audioFormatMetadata = trackGroups.get(1).getFormat(0).metadata;
    List<Metadata.Entry> audioMetadataEntries = new ArrayList<>();
    for (int i = 0; i < audioFormatMetadata.length(); i++) {
      audioMetadataEntries.add(audioFormatMetadata.get(i));
    }
    assertThat(audioMetadataEntries).contains(expectedAndroidVersionMetadata);
    assertThat(audioMetadataEntries).contains(expectedSlowMotionData);
    assertThat(audioMetadataEntries).contains(expectedSmtaEntry);
  }

  @Test
  public void retrieveMetadata_invalidMediaItem_throwsError() {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/does_not_exist"));

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();

    assertThrows(
        ExecutionException.class, () -> trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS));
  }
}
