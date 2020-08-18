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

package com.google.android.exoplayer2.ext.media2;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.UriMediaItem;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Assertions;

/** Default implementation of {@link MediaItemConverter}. */
public final class DefaultMediaItemConverter implements MediaItemConverter {

  @Override
  public MediaItem convertToExoPlayerMediaItem(androidx.media2.common.MediaItem androidXMediaItem) {
    if (androidXMediaItem instanceof FileMediaItem) {
      throw new IllegalStateException("FileMediaItem isn't supported");
    }
    if (androidXMediaItem instanceof CallbackMediaItem) {
      throw new IllegalStateException("CallbackMediaItem isn't supported");
    }

    MediaItem.Builder exoPlayerMediaItemBuilder = new MediaItem.Builder();

    // Set mediaItem as tag for creating MediaSource via MediaSourceFactory methods.
    exoPlayerMediaItemBuilder.setTag(androidXMediaItem);

    // Media ID or URI must be present. Get it from androidx.MediaItem if possible.
    @Nullable Uri uri = null;
    @Nullable String mediaId = null;
    if (androidXMediaItem instanceof UriMediaItem) {
      UriMediaItem uriMediaItem = (UriMediaItem) androidXMediaItem;
      uri = uriMediaItem.getUri();
    }
    @Nullable MediaMetadata metadata = androidXMediaItem.getMetadata();
    if (metadata != null) {
      mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
      @Nullable String uriString = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
      if (uri == null && uriString != null) {
        uri = Uri.parse(uriString);
      }
    }
    if (uri == null) {
      // Generate a Uri to make it non-null. If not, tag will be ignored.
      uri = Uri.parse("exoplayer://" + androidXMediaItem.hashCode());
    }
    exoPlayerMediaItemBuilder.setUri(uri);
    exoPlayerMediaItemBuilder.setMediaId(mediaId);

    if (androidXMediaItem.getStartPosition() != androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      exoPlayerMediaItemBuilder.setClipStartPositionMs(androidXMediaItem.getStartPosition());
      exoPlayerMediaItemBuilder.setClipRelativeToDefaultPosition(true);
    }
    if (androidXMediaItem.getEndPosition() != androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      exoPlayerMediaItemBuilder.setClipEndPositionMs(androidXMediaItem.getEndPosition());
      exoPlayerMediaItemBuilder.setClipRelativeToDefaultPosition(true);
    }

    return exoPlayerMediaItemBuilder.build();
  }

  @Override
  public androidx.media2.common.MediaItem convertToAndroidXMediaItem(MediaItem exoPlayerMediaItem) {
    Assertions.checkNotNull(exoPlayerMediaItem);
    MediaItem.PlaybackProperties playbackProperties =
        Assertions.checkNotNull(exoPlayerMediaItem.playbackProperties);
    @Nullable Object tag = playbackProperties.tag;
    if (tag instanceof androidx.media2.common.MediaItem) {
      return (androidx.media2.common.MediaItem) tag;
    }

    return new UriMediaItem.Builder(playbackProperties.uri).build();
  }
}
