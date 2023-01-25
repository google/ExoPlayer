/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import static com.google.android.exoplayer2.Player.COMMAND_GET_MEDIA_ITEMS_METADATA;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.BitmapCallback;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.MediaDescriptionAdapter;

/**
 * Default implementation of {@link MediaDescriptionAdapter}.
 *
 * <p>Uses values from the {@link Player#getMediaMetadata() player mediaMetadata} to populate the
 * notification.
 */
public final class DefaultMediaDescriptionAdapter implements MediaDescriptionAdapter {

  @Nullable private final PendingIntent pendingIntent;

  /**
   * Creates a default {@link MediaDescriptionAdapter}.
   *
   * @param pendingIntent The {@link PendingIntent} to be returned from {@link
   *     #createCurrentContentIntent(Player)}, or null if no intent should be fired.
   */
  public DefaultMediaDescriptionAdapter(@Nullable PendingIntent pendingIntent) {
    this.pendingIntent = pendingIntent;
  }

  @Override
  public CharSequence getCurrentContentTitle(Player player) {
    if (!player.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS_METADATA)) {
      return "";
    }
    @Nullable CharSequence displayTitle = player.getMediaMetadata().displayTitle;
    if (!TextUtils.isEmpty(displayTitle)) {
      return displayTitle;
    }

    @Nullable CharSequence title = player.getMediaMetadata().title;
    return title != null ? title : "";
  }

  @Nullable
  @Override
  public PendingIntent createCurrentContentIntent(Player player) {
    return pendingIntent;
  }

  @Nullable
  @Override
  public CharSequence getCurrentContentText(Player player) {
    if (!player.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS_METADATA)) {
      return null;
    }
    @Nullable CharSequence artist = player.getMediaMetadata().artist;
    if (!TextUtils.isEmpty(artist)) {
      return artist;
    }

    return player.getMediaMetadata().albumArtist;
  }

  @Nullable
  @Override
  public Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback) {
    if (!player.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS_METADATA)) {
      return null;
    }
    @Nullable byte[] data = player.getMediaMetadata().artworkData;
    if (data == null) {
      return null;
    }
    return BitmapFactory.decodeByteArray(data, /* offset= */ 0, data.length);
  }
}
