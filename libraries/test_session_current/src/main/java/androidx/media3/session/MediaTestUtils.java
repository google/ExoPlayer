/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.METADATA_ARTWORK_URI;
import static androidx.media3.test.session.common.CommonConstants.METADATA_DESCRIPTION;
import static androidx.media3.test.session.common.CommonConstants.METADATA_EXTRAS;
import static androidx.media3.test.session.common.CommonConstants.METADATA_SUBTITLE;
import static androidx.media3.test.session.common.CommonConstants.METADATA_TITLE;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Utilities for tests. */
public final class MediaTestUtils {

  private static final String TAG = "MediaTestUtils";

  private static final String TEST_IMAGE_PATH = "media/png/non-motion-photo-shortened.png";

  private static final VideoSize DEFAULT_VIDEO_SIZE = new VideoSize(640, 480);
  private static final Format VIDEO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setAverageBitrate(2_400_000)
          .setWidth(DEFAULT_VIDEO_SIZE.width)
          .setHeight(DEFAULT_VIDEO_SIZE.height)
          .build();
  private static final Format AUDIO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setAverageBitrate(320_000)
          .setChannelCount(2)
          .setSampleRate(44100)
          .build();

  /**
   * Tracks with {@linkplain C#TRACK_TYPE_VIDEO a single video} track and {@linkplain
   * C#TRACK_TYPE_AUDIO a single audio} track for testing purpose.
   */
  public static Tracks createDefaultVideoTracks() {
    return new Tracks(
        ImmutableList.of(
            new Tracks.Group(
                new TrackGroup(VIDEO_FORMAT),
                /* adaptiveSupported= */ false,
                new int[] {C.FORMAT_HANDLED},
                /* trackSelected= */ new boolean[] {true}),
            new Tracks.Group(
                new TrackGroup(AUDIO_FORMAT),
                /* adaptiveSupported= */ false,
                new int[] {C.FORMAT_HANDLED},
                /* trackSelected= */ new boolean[] {true})));
  }

  /** Returns a new {@link VideoSize} instance for testing purpose. */
  public static VideoSize createDefaultVideoSize() {
    return new VideoSize(
        DEFAULT_VIDEO_SIZE.width,
        DEFAULT_VIDEO_SIZE.height,
        DEFAULT_VIDEO_SIZE.unappliedRotationDegrees,
        DEFAULT_VIDEO_SIZE.pixelWidthHeightRatio);
  }

  /** Create a media item with the mediaId for testing purpose. */
  public static MediaItem createMediaItem(String mediaId) {
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  public static MediaItem createMediaItemWithArtworkData(String mediaId) {
    MediaMetadata.Builder mediaMetadataBuilder =
        new MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setIsBrowsable(false)
            .setIsPlayable(true);
    try {
      byte[] artworkData =
          TestUtils.getByteArrayForScaledBitmap(
              ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
      mediaMetadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
    } catch (IOException e) {
      fail(e.getMessage());
    }
    MediaMetadata mediaMetadata = mediaMetadataBuilder.build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  public static ArrayList<MediaItem> createMediaItems(int size) {
    ArrayList<MediaItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(createMediaItem("mediaItem_" + (i + 1)));
    }
    return list;
  }

  public static ArrayList<MediaItem> createMediaItemsWithArtworkData(int size) {
    ArrayList<MediaItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(createMediaItemWithArtworkData("mediaItem_" + (i + 1)));
    }
    return list;
  }

  public static List<MediaItem> createMediaItems(String... mediaIds) {
    List<MediaItem> list = new ArrayList<>();
    for (int i = 0; i < mediaIds.length; i++) {
      list.add(createMediaItem(mediaIds[i]));
    }
    return list;
  }

  public static MediaMetadata createMediaMetadata() {
    return new MediaMetadata.Builder()
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setTitle(METADATA_TITLE)
        .setSubtitle(METADATA_SUBTITLE)
        .setDescription(METADATA_DESCRIPTION)
        .setArtworkUri(METADATA_ARTWORK_URI)
        .setExtras(METADATA_EXTRAS)
        .build();
  }

  public static MediaMetadata createMediaMetadataWithArtworkData() {
    MediaMetadata.Builder mediaMetadataBuilder =
        new MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setTitle(METADATA_TITLE)
            .setSubtitle(METADATA_SUBTITLE)
            .setDescription(METADATA_DESCRIPTION)
            .setArtworkUri(METADATA_ARTWORK_URI)
            .setExtras(METADATA_EXTRAS);

    try {
      byte[] artworkData =
          TestUtils.getByteArrayForScaledBitmap(
              ApplicationProvider.getApplicationContext(), TEST_IMAGE_PATH);
      mediaMetadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return mediaMetadataBuilder.build();
  }

  public static List<ControllerInfo> getTestControllerInfos(MediaSession session) {
    List<ControllerInfo> infos = new ArrayList<>();
    if (session != null) {
      for (ControllerInfo info : session.getConnectedControllers()) {
        if (SUPPORT_APP_PACKAGE_NAME.equals(info.getPackageName())) {
          infos.add(info);
        }
      }
    }
    if (infos.isEmpty()) {
      Log.e(TAG, "Test controller was not found in connected controllers. session=" + session);
    }
    return infos;
  }

  /**
   * Create a list of {@link MediaBrowserCompat.MediaItem} for testing purpose.
   *
   * @param size list size
   * @return the newly created playlist
   */
  public static List<MediaBrowserCompat.MediaItem> createBrowserItems(int size) {
    List<MediaBrowserCompat.MediaItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(
          new MediaBrowserCompat.MediaItem(
              new MediaDescriptionCompat.Builder().setMediaId("browserItem_" + (i + 1)).build(),
              /* flags= */ 0));
    }
    return list;
  }

  /**
   * Create a list of {@link MediaSessionCompat.QueueItem} for testing purpose.
   *
   * @param size list size
   * @return the newly created playlist
   */
  public static List<MediaSessionCompat.QueueItem> createQueueItems(int size) {
    List<MediaSessionCompat.QueueItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(
          new MediaSessionCompat.QueueItem(
              new MediaDescriptionCompat.Builder().setMediaId("queueItem_" + (i + 1)).build(), i));
    }
    return list;
  }

  public static List<MediaSessionCompat.QueueItem> convertToQueueItemsWithoutBitmap(
      List<MediaItem> mediaItems) {
    List<MediaSessionCompat.QueueItem> list = new ArrayList<>();
    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem item = mediaItems.get(i);
      MediaDescriptionCompat description =
          LegacyConversions.convertToMediaDescriptionCompat(item, null);
      long id = LegacyConversions.convertToQueueItemId(i);
      list.add(new MediaSessionCompat.QueueItem(description, id));
    }
    return list;
  }

  public static Timeline createTimeline(int windowCount) {
    return new PlaylistTimeline(createMediaItems(/* size= */ windowCount));
  }

  public static Timeline createTimeline(List<MediaItem> mediaItems) {
    return new PlaylistTimeline(mediaItems);
  }

  public static Timeline createTimelineWithPeriodSizes(int[] periodSizesPerWindow) {
    return new MultiplePeriodsPerWindowTimeline(
        createMediaItems(/* size= */ periodSizesPerWindow.length),
        periodSizesPerWindow,
        /* defaultPeriodDurationMs= */ 10_000);
  }

  public static Timeline createTimelineWithPeriodSizes(
      int[] periodSizesPerWindow, long defaultPeriodDuration) {
    return new MultiplePeriodsPerWindowTimeline(
        createMediaItems(/* size= */ periodSizesPerWindow.length),
        periodSizesPerWindow,
        defaultPeriodDuration);
  }

  public static LibraryParams createLibraryParams() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    return new LibraryParams.Builder().setExtras(extras).build();
  }

  public static void assertLibraryParamsEquals(
      @Nullable LibraryParams a, @Nullable LibraryParams b) {
    if (a == null || b == null) {
      assertThat(b).isEqualTo(a);
    } else {
      assertThat(b.isRecent).isEqualTo(a.isRecent);
      assertThat(b.isOffline).isEqualTo(a.isOffline);
      assertThat(b.isSuggested).isEqualTo(a.isSuggested);
      assertThat(TestUtils.equals(a.extras, b.extras)).isTrue();
    }
  }

  public static void assertLibraryParamsEquals(
      @Nullable LibraryParams params, @Nullable Bundle rootExtras) {
    if (params == null || rootExtras == null) {
      assertThat(params).isNull();
      assertThat(rootExtras).isNull();
    } else {
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_RECENT)).isEqualTo(params.isRecent);
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_OFFLINE)).isEqualTo(params.isOffline);
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_SUGGESTED)).isEqualTo(params.isSuggested);
      assertThat(TestUtils.contains(rootExtras, params.extras)).isTrue();
    }
  }

  public static void assertPaginatedListHasIds(
      List<MediaItem> paginatedList, List<String> fullIdList, int page, int pageSize) {
    int fromIndex = page * pageSize;
    int toIndex = Math.min((page + 1) * pageSize, fullIdList.size());
    // Compare the given results with originals.
    for (int originalIndex = fromIndex; originalIndex < toIndex; originalIndex++) {
      int relativeIndex = originalIndex - fromIndex;
      assertThat(paginatedList.get(relativeIndex).mediaId).isEqualTo(fullIdList.get(originalIndex));
    }
  }

  public static void assertMediaIdEquals(MediaItem expected, MediaItem actual) {
    assertThat(actual.mediaId).isEqualTo(expected.mediaId);
  }

  public static void assertMediaIdEquals(Timeline expected, Timeline actual) {
    assertThat(actual.getWindowCount()).isEqualTo(expected.getWindowCount());
    Timeline.Window expectedWindow = new Timeline.Window();
    Timeline.Window actualWindow = new Timeline.Window();
    for (int i = 0; i < expected.getWindowCount(); i++) {
      assertMediaIdEquals(
          expected.getWindow(i, expectedWindow).mediaItem,
          actual.getWindow(i, actualWindow).mediaItem);
    }
  }

  public static void assertTimelineContains(Timeline timeline, List<MediaItem> mediaItems) {
    assertThat(timeline.getWindowCount()).isEqualTo(mediaItems.size());
    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem itemFromTimeline = timeline.getWindow(i, new Timeline.Window()).mediaItem;
      MediaItem itemFromMediaItems = mediaItems.get(i);
      assertWithMessage(
              "media item differs at "
                  + i
                  + ", timeline="
                  + itemFromTimeline.mediaId
                  + ", items="
                  + itemFromMediaItems.mediaId)
          .that(itemFromTimeline)
          .isEqualTo(itemFromMediaItems);
    }
  }

  /**
   * Asserts whether two timeline contain equal media items. Used when a timeline is sent across the
   * process, and lose Window's uid which is used by {@link Timeline.Window#equals}.
   */
  public static void assertTimelineMediaItemsEquals(Timeline actual, Timeline expected) {
    assertThat(actual.getWindowCount()).isEqualTo(expected.getWindowCount());
    for (int i = 0; i < expected.getWindowCount(); i++) {
      MediaItem actualItem = actual.getWindow(i, new Timeline.Window()).mediaItem;
      MediaItem expectedItem = expected.getWindow(i, new Timeline.Window()).mediaItem;
      assertWithMessage(
              "media item differs at "
                  + i
                  + ", expected="
                  + expectedItem.mediaId
                  + ", actual="
                  + actualItem.mediaId)
          .that(actualItem)
          .isEqualTo(expectedItem);
    }
  }
}
