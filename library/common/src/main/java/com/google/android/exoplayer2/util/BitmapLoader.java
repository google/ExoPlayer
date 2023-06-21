/*
 * Copyright 2022 The Android Open Source Project
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
 * limitations under the License
 */
package com.google.android.exoplayer2.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Loads images.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface BitmapLoader {
  /** Decodes an image from compressed binary data. */
  ListenableFuture<Bitmap> decodeBitmap(byte[] data);

  /** Loads an image from {@code uri}. */
  default ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    return loadBitmap(uri, /* options= */ null);
  }

  /** Loads an image from {@code uri} with the given {@link BitmapFactory.Options}. */
  ListenableFuture<Bitmap> loadBitmap(Uri uri, @Nullable BitmapFactory.Options options);

  /**
   * Loads an image from {@link MediaMetadata}. Returns null if {@code metadata} doesn't contain
   * bitmap information.
   *
   * <p>By default, the method will try to decode an image from {@link MediaMetadata#artworkData} if
   * it is present. Otherwise, the method will try to load an image from {@link
   * MediaMetadata#artworkUri} if it is present. The method will return null if neither {@link
   * MediaMetadata#artworkData} nor {@link MediaMetadata#artworkUri} is present.
   */
  @Nullable
  default ListenableFuture<Bitmap> loadBitmapFromMetadata(MediaMetadata metadata) {
    @Nullable ListenableFuture<Bitmap> future;
    if (metadata.artworkData != null) {
      future = decodeBitmap(metadata.artworkData);
    } else if (metadata.artworkUri != null) {
      future = loadBitmap(metadata.artworkUri);
    } else {
      future = null;
    }
    return future;
  }
}
