/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link BitmapLoader} that caches the result of the last {@link #decodeBitmap(byte[])} or {@link
 * #loadBitmap(Uri)} request. Requests are fulfilled from the last bitmap load request when the last
 * bitmap is requested from the same {@code data} or the last bitmap is requested from the same
 * {@code uri}. If it's not the above two cases, the request is forwarded to the provided {@link
 * BitmapLoader} and the result is cached.
 */
@UnstableApi
public final class CacheBitmapLoader implements BitmapLoader {

  private final BitmapLoader bitmapLoader;

  private @MonotonicNonNull BitmapLoadRequest lastBitmapLoadRequest;

  /**
   * Creates an instance that is able to cache the last bitmap load request to the given bitmap
   * loader.
   */
  public CacheBitmapLoader(BitmapLoader bitmapLoader) {
    this.bitmapLoader = bitmapLoader;
  }

  @Override
  public boolean supportsMimeType(String mimeType) {
    return bitmapLoader.supportsMimeType(mimeType);
  }

  @Override
  public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
    if (lastBitmapLoadRequest != null && lastBitmapLoadRequest.matches(data)) {
      return lastBitmapLoadRequest.getFuture();
    }
    ListenableFuture<Bitmap> future = bitmapLoader.decodeBitmap(data);
    lastBitmapLoadRequest = new BitmapLoadRequest(data, future);
    return future;
  }

  @Override
  public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
    if (lastBitmapLoadRequest != null && lastBitmapLoadRequest.matches(uri)) {
      return lastBitmapLoadRequest.getFuture();
    }
    ListenableFuture<Bitmap> future = bitmapLoader.loadBitmap(uri);
    lastBitmapLoadRequest = new BitmapLoadRequest(uri, future);
    return future;
  }

  /**
   * Stores the result of a bitmap load request. Requests are identified either by a byte array, if
   * the bitmap is loaded from compressed data, or a URI, if the bitmap was loaded from a URI.
   */
  private static class BitmapLoadRequest {
    @Nullable private final byte[] data;
    @Nullable private final Uri uri;
    @Nullable private final ListenableFuture<Bitmap> future;

    public BitmapLoadRequest(byte[] data, ListenableFuture<Bitmap> future) {
      this.data = data;
      this.uri = null;
      this.future = future;
    }

    public BitmapLoadRequest(Uri uri, ListenableFuture<Bitmap> future) {
      this.data = null;
      this.uri = uri;
      this.future = future;
    }

    /** Whether the bitmap load request was performed for {@code data}. */
    public boolean matches(@Nullable byte[] data) {
      return this.data != null && Arrays.equals(this.data, data);
    }

    /** Whether the bitmap load request was performed for {@code uri}. */
    public boolean matches(@Nullable Uri uri) {
      return this.uri != null && this.uri.equals(uri);
    }

    /** Returns the future that set for the bitmap load request. */
    public ListenableFuture<Bitmap> getFuture() {
      return checkStateNotNull(future);
    }
  }
}
