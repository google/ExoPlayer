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

import static androidx.media2.common.MediaMetadata.METADATA_KEY_DISPLAY_TITLE;
import static androidx.media2.common.MediaMetadata.METADATA_KEY_MEDIA_ID;
import static androidx.media2.common.MediaMetadata.METADATA_KEY_MEDIA_URI;
import static androidx.media2.common.MediaMetadata.METADATA_KEY_TITLE;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.UriMediaItem;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Default implementation of {@link MediaItemConverter}.
 *
 * <p>Note that {@link #getMetadata} can be overridden to fill in additional metadata when
 * converting {@link MediaItem ExoPlayer MediaItems} to their AndroidX equivalents.
 */
public class DefaultMediaItemConverter implements MediaItemConverter {

  @Override
  public MediaItem convertToExoPlayerMediaItem(androidx.media2.common.MediaItem media2MediaItem) {
    if (media2MediaItem instanceof FileMediaItem) {
      throw new IllegalStateException("FileMediaItem isn't supported");
    }
    if (media2MediaItem instanceof CallbackMediaItem) {
      throw new IllegalStateException("CallbackMediaItem isn't supported");
    }

    @Nullable Uri uri = null;
    @Nullable String mediaId = null;
    @Nullable String title = null;
    if (media2MediaItem instanceof UriMediaItem) {
      UriMediaItem uriMediaItem = (UriMediaItem) media2MediaItem;
      uri = uriMediaItem.getUri();
    }
    @Nullable androidx.media2.common.MediaMetadata metadata = media2MediaItem.getMetadata();
    if (metadata != null) {
      @Nullable String uriString = metadata.getString(METADATA_KEY_MEDIA_URI);
      mediaId = metadata.getString(METADATA_KEY_MEDIA_ID);
      if (uri == null) {
        if (uriString != null) {
          uri = Uri.parse(uriString);
        } else if (mediaId != null) {
          uri = Uri.parse("media2:///" + mediaId);
        }
      }
      title = metadata.getString(METADATA_KEY_DISPLAY_TITLE);
      if (title == null) {
        title = metadata.getString(METADATA_KEY_TITLE);
      }
    }
    if (uri == null) {
      // Generate a URI to make it non-null. If not, then the tag passed to setTag will be ignored.
      uri = Uri.parse("media2:///");
    }
    long startPositionMs = media2MediaItem.getStartPosition();
    if (startPositionMs == androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      startPositionMs = 0;
    }
    long endPositionMs = media2MediaItem.getEndPosition();
    if (endPositionMs == androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      endPositionMs = C.TIME_END_OF_SOURCE;
    }

    return new MediaItem.Builder()
        .setUri(uri)
        .setMediaId(mediaId != null ? mediaId : MediaItem.DEFAULT_MEDIA_ID)
        .setMediaMetadata(
            new com.google.android.exoplayer2.MediaMetadata.Builder().setTitle(title).build())
        .setTag(media2MediaItem)
        .setClipStartPositionMs(startPositionMs)
        .setClipEndPositionMs(endPositionMs)
        .build();
  }

  @Override
  public androidx.media2.common.MediaItem convertToMedia2MediaItem(MediaItem exoPlayerMediaItem) {
    Assertions.checkNotNull(exoPlayerMediaItem);
    MediaItem.PlaybackProperties playbackProperties =
        Assertions.checkNotNull(exoPlayerMediaItem.playbackProperties);

    @Nullable Object tag = playbackProperties.tag;
    if (tag instanceof androidx.media2.common.MediaItem) {
      return (androidx.media2.common.MediaItem) tag;
    }

    androidx.media2.common.MediaMetadata metadata = getMetadata(exoPlayerMediaItem);
    long startPositionMs = exoPlayerMediaItem.clippingProperties.startPositionMs;
    long endPositionMs = exoPlayerMediaItem.clippingProperties.endPositionMs;
    if (endPositionMs == C.TIME_END_OF_SOURCE) {
      endPositionMs = androidx.media2.common.MediaItem.POSITION_UNKNOWN;
    }

    return new androidx.media2.common.MediaItem.Builder()
        .setMetadata(metadata)
        .setStartPosition(startPositionMs)
        .setEndPosition(endPositionMs)
        .build();
  }

  /**
   * Returns a {@link androidx.media2.common.MediaMetadata} corresponding to the given {@link
   * MediaItem ExoPlayer MediaItem}.
   */
  protected androidx.media2.common.MediaMetadata getMetadata(MediaItem exoPlayerMediaItem) {
    @Nullable CharSequence title = exoPlayerMediaItem.mediaMetadata.title;

    androidx.media2.common.MediaMetadata.Builder metadataBuilder =
        new androidx.media2.common.MediaMetadata.Builder()
            .putString(METADATA_KEY_MEDIA_ID, exoPlayerMediaItem.mediaId);
    if (title != null) {
      metadataBuilder.putString(METADATA_KEY_TITLE, title.toString());
      metadataBuilder.putString(METADATA_KEY_DISPLAY_TITLE, title.toString());
    }
    return metadataBuilder.build();
  }
}
