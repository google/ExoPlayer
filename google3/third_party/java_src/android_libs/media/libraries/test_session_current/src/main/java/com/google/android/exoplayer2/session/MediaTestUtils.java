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
package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.session.MediaLibraryService.LibraryParams;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.Log;
import java.util.ArrayList;
import java.util.List;

/** Utilities for tests. */
public final class MediaTestUtils {

  private static final String TAG = "MediaTestUtils";

  /** Create a media item with the mediaId for testing purpose. */
  public static MediaItem createConvergedMediaItem(String mediaId) {
    return new MediaItem.Builder().setMediaId(mediaId).build();
  }

  public static List<MediaItem> createConvergedMediaItems(int size) {
    List<MediaItem> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(createConvergedMediaItem("mediaItem_" + (i + 1)));
    }
    return list;
  }

  public static ControllerInfo getTestControllerInfo(MediaSession session) {
    if (session == null) {
      return null;
    }
    for (ControllerInfo info : session.getConnectedControllers()) {
      if (SUPPORT_APP_PACKAGE_NAME.equals(info.getPackageName())) {
        return info;
      }
    }
    Log.e(TAG, "Test controller was not found in connected controllers. session=" + session);
    return null;
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

  public static Timeline createTimeline(int windowCount) {
    return new PlaylistTimeline(createConvergedMediaItems(/* size= */ windowCount));
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
      assertThat(b.recent).isEqualTo(a.recent);
      assertThat(b.offline).isEqualTo(a.offline);
      assertThat(b.suggested).isEqualTo(a.suggested);
      assertThat(TestUtils.equals(a.extras, b.extras)).isTrue();
    }
  }

  public static void assertLibraryParamsEquals(
      @Nullable LibraryParams params, @Nullable Bundle rootExtras) {
    if (params == null || rootExtras == null) {
      assertThat(params).isNull();
      assertThat(rootExtras).isNull();
    } else {
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_RECENT)).isEqualTo(params.recent);
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_OFFLINE)).isEqualTo(params.offline);
      assertThat(rootExtras.getBoolean(BrowserRoot.EXTRA_SUGGESTED)).isEqualTo(params.suggested);
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
}
