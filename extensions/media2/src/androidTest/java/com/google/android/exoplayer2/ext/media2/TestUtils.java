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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import androidx.media2.common.UriMediaItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Utilities for tests. */
public final class TestUtils {
  private static final long PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;

  public static Uri createResourceUri(Context context, int resId) {
    Resources resources = context.getResources();
    return new Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(resId))
        .appendPath(resources.getResourceTypeName(resId))
        .appendPath(resources.getResourceEntryName(resId))
        .build();
  }

  public static MediaItem createMediaItem(Context context) {
    return createMediaItem(context, com.google.android.exoplayer2.ext.media2.test.R.raw.testvideo);
  }

  public static MediaItem createMediaItem(Context context, int resId) {
    Uri testVideoUri = createResourceUri(context, resId);
    String resourceName = context.getResources().getResourceName(resId);
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, resourceName)
            .build();
    return new UriMediaItem.Builder(testVideoUri).setMetadata(metadata).build();
  }

  public static List<MediaItem> createPlaylist(Context context, int size) {
    List<MediaItem> items = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      items.add(createMediaItem(context));
    }
    return items;
  }

  public static void loadResource(Context context, int resId, SessionPlayer sessionPlayer)
      throws Exception {
    Uri testUri = TestUtils.createResourceUri(context, resId);
    MediaItem mediaItem = createMediaItem(context, resId);
    assertPlayerResultSuccess(sessionPlayer.setMediaItem(mediaItem));
  }

  public static void assertPlayerResultSuccess(Future<PlayerResult> future) throws Exception {
    assertPlayerResult(future, RESULT_SUCCESS);
  }

  public static void assertPlayerResult(
      Future<PlayerResult> future, /* @PlayerResult.ResultCode */ int playerResult)
      throws Exception {
    assertThat(future).isNotNull();
    PlayerResult result = future.get(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    assertThat(result).isNotNull();
    assertThat(result.getResultCode()).isEqualTo(playerResult);
  }

  private TestUtils() {
    // Prevent from instantiation.
  }
}
