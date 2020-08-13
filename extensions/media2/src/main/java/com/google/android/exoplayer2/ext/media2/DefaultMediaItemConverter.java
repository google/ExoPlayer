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

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.UriMediaItem;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/** Default implementation of both {@link MediaItemConverter} and {@link MediaSourceFactory}. */
public final class DefaultMediaItemConverter implements MediaItemConverter, MediaSourceFactory {
  private static final int[] SUPPORTED_TYPES =
      new int[] {C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
  private final Context context;
  private final DataSource.Factory dataSourceFactory;

  /**
   * Default constructor with {@link DefaultDataSourceFactory}.
   *
   * @param context The context.
   */
  public DefaultMediaItemConverter(Context context) {
    this(
        context,
        new DefaultDataSourceFactory(
            context, Util.getUserAgent(context, ExoPlayerLibraryInfo.VERSION_SLASHY)));
  }

  /**
   * Default constructor with {@link DataSource.Factory}.
   *
   * @param context The {@link Context}.
   * @param dataSourceFactory The {@link DataSource.Factory} to create {@link MediaSource} from
   *     {@link MediaItem ExoPlayer MediaItem}.
   */
  public DefaultMediaItemConverter(Context context, DataSource.Factory dataSourceFactory) {
    this.context = Assertions.checkNotNull(context);
    this.dataSourceFactory = Assertions.checkNotNull(dataSourceFactory);
  }

  // Implements MediaItemConverter

  @Override
  public MediaItem convertToExoPlayerMediaItem(androidx.media2.common.MediaItem androidXMediaItem) {
    if (androidXMediaItem instanceof FileMediaItem) {
      throw new IllegalStateException("FileMediaItem isn't supported");
    }

    com.google.android.exoplayer2.MediaItem.Builder exoplayerMediaItemBuilder =
        new com.google.android.exoplayer2.MediaItem.Builder();

    // Set mediaItem as tag for creating MediaSource via MediaSourceFactory methods.
    exoplayerMediaItemBuilder.setTag(androidXMediaItem);

    // Media id or Uri must be present. Get it from androidx.MediaItem if possible.
    Uri uri = null;
    String mediaId = null;
    if (androidXMediaItem instanceof UriMediaItem) {
      UriMediaItem uriMediaItem = (UriMediaItem) androidXMediaItem;
      uri = uriMediaItem.getUri();
    }
    MediaMetadata metadata = androidXMediaItem.getMetadata();
    if (metadata != null) {
      mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
      String uriString = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
      if (uriString != null) {
        uri = Uri.parse(uriString);
      }
    }
    if (uri == null) {
      // Generate a Uri to make it non-null. If not, tag will be ignored.
      uri = Uri.parse("exoplayer://" + androidXMediaItem.hashCode());
    }
    exoplayerMediaItemBuilder.setUri(uri);
    exoplayerMediaItemBuilder.setMediaId(mediaId);

    // These are actually aren't needed, because MediaSource will be generated only via tag.
    // However, fills in the exoplayer2.MediaItem's fields as much as possible just in case.
    if (androidXMediaItem.getStartPosition() != androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      exoplayerMediaItemBuilder.setClipStartPositionMs(androidXMediaItem.getStartPosition());
      exoplayerMediaItemBuilder.setClipRelativeToDefaultPosition(true);
    }
    if (androidXMediaItem.getEndPosition() != androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      exoplayerMediaItemBuilder.setClipEndPositionMs(androidXMediaItem.getEndPosition());
      exoplayerMediaItemBuilder.setClipRelativeToDefaultPosition(true);
    }

    return exoplayerMediaItemBuilder.build();
  }

  @Override
  public androidx.media2.common.MediaItem convertToAndroidXMediaItem(MediaItem exoplayerMediaItem) {
    Assertions.checkNotNull(exoplayerMediaItem);
    MediaItem.PlaybackProperties playbackProperties =
        Assertions.checkNotNull(exoplayerMediaItem.playbackProperties);
    Object tag = playbackProperties.tag;
    if (!(tag instanceof androidx.media2.common.MediaItem)) {
      throw new IllegalStateException(
          "DefaultMediaItemConverter cannot understand "
              + exoplayerMediaItem
              + ". Unexpected tag "
              + tag
              + " in PlaybackProperties");
    }
    return (androidx.media2.common.MediaItem) tag;
  }

  // Implements MediaSourceFactory

  @Override
  public MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    // No-op
    return this;
  }

  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(
      @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    // No-op
    return this;
  }

  @Override
  public int[] getSupportedTypes() {
    return SUPPORTED_TYPES;
  }

  @Override
  public MediaSource createMediaSource(com.google.android.exoplayer2.MediaItem exoplayerMediaItem) {
    Assertions.checkNotNull(
        exoplayerMediaItem.playbackProperties,
        "DefaultMediaItemConverter cannot understand "
            + exoplayerMediaItem
            + ". PlaybackProperties is missing.");
    Object tag = exoplayerMediaItem.playbackProperties.tag;
    if (!(tag instanceof androidx.media2.common.MediaItem)) {
      throw new IllegalStateException(
          "DefaultMediaItemConverter cannot understand "
              + exoplayerMediaItem
              + ". Unexpected tag "
              + tag
              + " in PlaybackProperties");
    }
    androidx.media2.common.MediaItem androidXMediaItem = (androidx.media2.common.MediaItem) tag;

    // Create a source for the item.
    MediaSource mediaSource =
        Utils.createUnclippedMediaSource(context, dataSourceFactory, androidXMediaItem);

    // Apply clipping if needed.
    long startPosition = androidXMediaItem.getStartPosition();
    long endPosition = androidXMediaItem.getEndPosition();
    if (startPosition != 0L || endPosition != androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
      if (endPosition == androidx.media2.common.MediaItem.POSITION_UNKNOWN) {
        endPosition = C.TIME_END_OF_SOURCE;
      }
      // Disable the initial discontinuity to give seamless transitions to clips.
      mediaSource =
          new ClippingMediaSource(
              mediaSource,
              C.msToUs(startPosition),
              C.msToUs(endPosition),
              /* enableInitialDiscontinuity= */ false,
              /* allowDynamicClippingUpdates= */ false,
              /* relativeToDefaultPosition= */ true);
    }
    return mediaSource;
  }
}
