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
package androidx.media3.session;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.PlayerNotificationManager.BitmapCallback;
import androidx.media3.session.PlayerNotificationManager.MediaDescriptionAdapter;

/**
 * Default implementation of {@link MediaDescriptionAdapter}.
 *
 * <p>Uses values from the {@link Player#getMediaMetadata() player mediaMetadata} to populate the
 * notification.
 */
@UnstableApi
public final class DefaultMediaDescriptionAdapter implements MediaDescriptionAdapter {

  /** Creates a default {@link MediaDescriptionAdapter}. */
  public DefaultMediaDescriptionAdapter() {}

  @Override
  public CharSequence getCurrentContentTitle(MediaSession session) {
    Player player = session.getPlayer();
    @Nullable CharSequence displayTitle = player.getMediaMetadata().displayTitle;
    if (!TextUtils.isEmpty(displayTitle)) {
      return displayTitle;
    }

    @Nullable CharSequence title = player.getMediaMetadata().title;
    return title != null ? title : "";
  }

  @Nullable
  @Override
  public CharSequence getCurrentContentText(MediaSession session) {
    Player player = session.getPlayer();
    @Nullable CharSequence artist = player.getMediaMetadata().artist;
    if (!TextUtils.isEmpty(artist)) {
      return artist;
    }

    return player.getMediaMetadata().albumArtist;
  }

  @Nullable
  @Override
  public Bitmap getCurrentLargeIcon(MediaSession session, BitmapCallback callback) {
    Player player = session.getPlayer();
    @Nullable byte[] data = player.getMediaMetadata().artworkData;
    if (data == null) {
      return null;
    }
    return BitmapFactory.decodeByteArray(data, /* offset= */ 0, data.length);
  }
}
