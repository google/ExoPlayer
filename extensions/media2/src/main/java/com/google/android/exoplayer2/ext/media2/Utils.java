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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.UriMediaItem;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Utility methods for the media2 extension (primarily for translating between the media2 and
 * ExoPlayer {@link Player} APIs).
 */
/* package */ final class Utils {

  private static final ExtractorsFactory sExtractorsFactory =
      new DefaultExtractorsFactory()
          .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

  /**
   * Returns an ExoPlayer media source for the given media item. The given {@link MediaItem} is set
   * as the tag of the source.
   */
  public static MediaSource createUnclippedMediaSource(
      Context context, DataSource.Factory dataSourceFactory, MediaItem mediaItem) {
    if (mediaItem instanceof UriMediaItem) {
      Uri uri = ((UriMediaItem) mediaItem).getUri();
      if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
        String path = Assertions.checkNotNull(uri.getPath());
        int resourceIdentifier;
        if (uri.getPathSegments().size() == 1 && uri.getPathSegments().get(0).matches("\\d+")) {
          resourceIdentifier = Integer.parseInt(uri.getPathSegments().get(0));
        } else {
          if (path.startsWith("/")) {
            path = path.substring(1);
          }
          @Nullable String host = uri.getHost();
          String resourceName = (TextUtils.isEmpty(host) ? "" : (host + ":")) + path;
          resourceIdentifier =
              context.getResources().getIdentifier(resourceName, "raw", context.getPackageName());
        }
        Assertions.checkState(resourceIdentifier != 0);
        uri = RawResourceDataSource.buildRawResourceUri(resourceIdentifier);
      }
      return createMediaSource(uri, dataSourceFactory, /* tag= */ mediaItem);
    } else if (mediaItem instanceof CallbackMediaItem) {
      CallbackMediaItem callbackMediaItem = (CallbackMediaItem) mediaItem;
      dataSourceFactory =
          DataSourceCallbackDataSource.getFactory(callbackMediaItem.getDataSourceCallback());
      return new ProgressiveMediaSource.Factory(dataSourceFactory, sExtractorsFactory)
          .setTag(mediaItem)
          .createMediaSource(Uri.EMPTY);
    } else {
      throw new IllegalStateException();
    }
  }

  /** Returns ExoPlayer audio attributes for the given audio attributes. */
  public static AudioAttributes getAudioAttributes(AudioAttributesCompat audioAttributesCompat) {
    return new AudioAttributes.Builder()
        .setContentType(audioAttributesCompat.getContentType())
        .setFlags(audioAttributesCompat.getFlags())
        .setUsage(audioAttributesCompat.getUsage())
        .build();
  }

  /** Returns audio attributes for the given ExoPlayer audio attributes. */
  public static AudioAttributesCompat getAudioAttributesCompat(AudioAttributes audioAttributes) {
    return new AudioAttributesCompat.Builder()
        .setContentType(audioAttributes.contentType)
        .setFlags(audioAttributes.flags)
        .setUsage(audioAttributes.usage)
        .build();
  }

  /** Returns the SimpleExoPlayer's shuffle mode for the given shuffle mode. */
  public static boolean getExoPlayerShuffleMode(int shuffleMode) {
    switch (shuffleMode) {
      case SessionPlayer.SHUFFLE_MODE_ALL:
      case SessionPlayer.SHUFFLE_MODE_GROUP:
        return true;
      case SessionPlayer.SHUFFLE_MODE_NONE:
        return false;
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the shuffle mode for the given ExoPlayer's shuffle mode */
  public static int getShuffleMode(boolean exoPlayerShuffleMode) {
    return exoPlayerShuffleMode ? SessionPlayer.SHUFFLE_MODE_ALL : SessionPlayer.SHUFFLE_MODE_NONE;
  }

  /** Returns the ExoPlayer's repeat mode for the given repeat mode. */
  @Player.RepeatMode
  public static int getExoPlayerRepeatMode(int repeatMode) {
    switch (repeatMode) {
      case SessionPlayer.REPEAT_MODE_ALL:
      case SessionPlayer.REPEAT_MODE_GROUP:
        return Player.REPEAT_MODE_ALL;
      case SessionPlayer.REPEAT_MODE_ONE:
        return Player.REPEAT_MODE_ONE;
      case SessionPlayer.REPEAT_MODE_NONE:
        return Player.REPEAT_MODE_OFF;
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the repeat mode for the given SimpleExoPlayer's repeat mode. */
  public static int getRepeatMode(@Player.RepeatMode int exoPlayerRepeatMode) {
    switch (exoPlayerRepeatMode) {
      case Player.REPEAT_MODE_ALL:
        return SessionPlayer.REPEAT_MODE_ALL;
      case Player.REPEAT_MODE_ONE:
        return SessionPlayer.REPEAT_MODE_ONE;
      case Player.REPEAT_MODE_OFF:
        return SessionPlayer.REPEAT_MODE_NONE;
      default:
        throw new IllegalArgumentException();
    }
  }

  private static MediaSource createMediaSource(
      Uri uri, DataSource.Factory dataSourceFactory, Object tag) {
    // TODO: Deduplicate with DefaultMediaSource once MediaItem support in ExoPlayer has been
    // released. See [Internal: b/150857202].
    @Nullable Class<? extends MediaSourceFactory> factoryClazz = null;
    try {
      // LINT.IfChange
      switch (Util.inferContentType(uri)) {
        case C.TYPE_DASH:
          factoryClazz =
              Class.forName("com.google.android.exoplayer2.source.dash.DashMediaSource$Factory")
                  .asSubclass(MediaSourceFactory.class);
          break;
        case C.TYPE_HLS:
          factoryClazz =
              Class.forName("com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory")
                  .asSubclass(MediaSourceFactory.class);
          break;
        case C.TYPE_SS:
          factoryClazz =
              Class.forName(
                      "com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory")
                  .asSubclass(MediaSourceFactory.class);
          break;
        case C.TYPE_OTHER:
        default:
          break;
      }
      if (factoryClazz != null) {
        MediaSourceFactory mediaSourceFactory =
            factoryClazz.getConstructor(DataSource.Factory.class).newInstance(dataSourceFactory);
        factoryClazz.getMethod("setTag", Object.class).invoke(mediaSourceFactory, tag);
        return mediaSourceFactory.createMediaSource(uri);
      }
      // LINT.ThenChange(../../../../../../../../../proguard-rules.txt)
    } catch (Exception e) {
      // Expected if the app was built without the corresponding module.
    }
    return new ProgressiveMediaSource.Factory(dataSourceFactory).setTag(tag).createMediaSource(uri);
  }

  private Utils() {
    // Prevent instantiation.
  }
}
