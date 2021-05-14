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

package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.MetadataRetriever.retrieveMetadata;
import static com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry.KEY_ANDROID_CAPTURE_FPS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.mp4.MdtaMetadataEntry;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.metadata.mp4.SmtaMetadataEntry;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.MimeTypes;
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
  public void retrieveMetadata_sefSlowMotion_outputsExpectedMetadata() throws Exception {
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse("asset://android_asset/media/mp4/sample_sef_slow_motion.mp4"));
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
    MdtaMetadataEntry expectedMdtaEntry =
        new MdtaMetadataEntry(
            KEY_ANDROID_CAPTURE_FPS,
            /* value= */ new byte[] {67, 112, 0, 0},
            /* localeIndicator= */ 0,
            /* typeIndicator= */ 23);

    ListenableFuture<TrackGroupArray> trackGroupsFuture =
        retrieveMetadata(context, mediaItem, clock);
    ShadowLooper.idleMainLooper();
    TrackGroupArray trackGroups = trackGroupsFuture.get(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);

    assertThat(trackGroups.length).isEqualTo(2); // Video and audio
    // Audio
    assertThat(trackGroups.get(0).getFormat(0).metadata.length()).isEqualTo(2);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(0)).isEqualTo(expectedSmtaEntry);
    assertThat(trackGroups.get(0).getFormat(0).metadata.get(1)).isEqualTo(expectedSlowMotionData);
    // Video
    assertThat(trackGroups.get(1).getFormat(0).metadata.length()).isEqualTo(3);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(0)).isEqualTo(expectedMdtaEntry);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(1)).isEqualTo(expectedSmtaEntry);
    assertThat(trackGroups.get(1).getFormat(0).metadata.get(2)).isEqualTo(expectedSlowMotionData);
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
