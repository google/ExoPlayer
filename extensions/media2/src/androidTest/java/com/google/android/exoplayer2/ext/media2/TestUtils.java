/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import static androidx.media2.common.SessionPlayer.PlayerResult.RESULT_SUCCESS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import androidx.media2.common.UriMediaItem;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Utilities for tests. */
/* package */ final class TestUtils {
  private static final long PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;

  public static UriMediaItem createMediaItem() {
    return createMediaItem(R.raw.video_desks);
  }

  public static UriMediaItem createMediaItem(int resId) {
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Integer.toString(resId))
            .build();
    return new UriMediaItem.Builder(RawResourceDataSource.buildRawResourceUri(resId))
        .setMetadata(metadata)
        .build();
  }

  public static List<MediaItem> createPlaylist(int size) {
    List<MediaItem> items = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      items.add(createMediaItem());
    }
    return items;
  }

  public static void loadResource(int resId, SessionPlayer sessionPlayer) throws Exception {
    MediaItem mediaItem = createMediaItem(resId);
    assertPlayerResultSuccess(sessionPlayer.setMediaItem(mediaItem));
  }

  public static void assertPlayerResultSuccess(Future<PlayerResult> future) throws Exception {
    assertPlayerResult(future, RESULT_SUCCESS);
  }

  public static void assertPlayerResult(
      Future<PlayerResult> future, /* @PlayerResult.ResultCode */ int playerResult)
      throws Exception {
    assertThat(future).isNotNull();
    PlayerResult result = future.get(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS);
    assertThat(result).isNotNull();
    assertThat(result.getResultCode()).isEqualTo(playerResult);
  }

  private TestUtils() {
    // Prevent from instantiation.
  }
}
