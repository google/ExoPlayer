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
package androidx.media3.common.util;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;
import com.google.common.util.concurrent.ListenableFuture;

/** Loads images. */
@UnstableApi
public interface BitmapLoader {

  /** Returns whether the given {@code mimeType} is supported. */
  boolean supportsMimeType(String mimeType);

  /** Decodes an image from compressed binary data. */
  ListenableFuture<Bitmap> decodeBitmap(byte[] data);

  /** Loads an image from {@code uri}. */
  ListenableFuture<Bitmap> loadBitmap(Uri uri);

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
