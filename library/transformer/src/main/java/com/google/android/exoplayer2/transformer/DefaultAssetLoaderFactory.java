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
 * limitations under the License.
 */

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSourceBitmapLoader;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.BitmapLoader;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.FileTypes;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Objects;
import java.util.concurrent.Executors;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * The default {@link AssetLoader.Factory} implementation.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DefaultAssetLoaderFactory implements AssetLoader.Factory {

  private final Context context;
  private final Codec.DecoderFactory decoderFactory;
  private final @Composition.HdrMode int hdrMode;
  private final Clock clock;
  private final MediaSource.@MonotonicNonNull Factory mediaSourceFactory;
  private final BitmapLoader bitmapLoader;

  private AssetLoader.@MonotonicNonNull Factory imageAssetLoaderFactory;
  private AssetLoader.@MonotonicNonNull Factory exoPlayerAssetLoaderFactory;

  /**
   * Creates an instance.
   *
   * <p>Uses {@link DataSourceBitmapLoader} to load images, setting the {@link
   * android.graphics.BitmapFactory.Options#inPreferredColorSpace} to {@link
   * android.graphics.ColorSpace.Named#SRGB} when possible.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param hdrMode The {@link Composition.HdrMode} to apply. Only {@link
   *     Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC} and {@link
   *     Composition#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR} are applied in this
   *     component.
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      @Composition.HdrMode int hdrMode,
      Clock clock) {
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.hdrMode = hdrMode;
    this.clock = clock;
    this.mediaSourceFactory = null;
    @Nullable BitmapFactory.Options options = null;
    if (Util.SDK_INT >= 26) {
      options = new BitmapFactory.Options();
      options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
    }
    this.bitmapLoader =
        new DataSourceBitmapLoader(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            new DefaultDataSource.Factory(context),
            options);
  }

  /**
   * Creates an instance with the default {@link Clock} and {@link Codec.DecoderFactory}.
   *
   * <p>For multi-picture formats (e.g. gifs), a single image frame from the container is loaded.
   * The frame loaded is determined by the {@link BitmapLoader} implementation.
   *
   * @param context The {@link Context}.
   * @param hdrMode The {@link Composition.HdrMode} to apply. Only {@link
   *     Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC} and {@link
   *     Composition#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR} are applied.
   * @param bitmapLoader The {@link BitmapLoader} to use to load and decode images.
   */
  public DefaultAssetLoaderFactory(
      Context context, @Composition.HdrMode int hdrMode, BitmapLoader bitmapLoader) {
    this.context = context.getApplicationContext();
    this.decoderFactory = new DefaultDecoderFactory(context);
    this.hdrMode = hdrMode;
    this.clock = Clock.DEFAULT;
    this.mediaSourceFactory = null;
    this.bitmapLoader = bitmapLoader;
  }

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param hdrMode The {@link Composition.HdrMode} to apply. Only {@link
   *     Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC} and {@link
   *     Composition#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR} are applied.
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
   *     transform when an {@link ExoPlayerAssetLoader} is used.
   * @param bitmapLoader The {@link BitmapLoader} to use to load and decode images.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      @Composition.HdrMode int hdrMode,
      Clock clock,
      MediaSource.Factory mediaSourceFactory,
      BitmapLoader bitmapLoader) {
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.hdrMode = hdrMode;
    this.clock = clock;
    this.mediaSourceFactory = mediaSourceFactory;
    this.bitmapLoader = bitmapLoader;
  }

  @Override
  public AssetLoader createAssetLoader(
      EditedMediaItem editedMediaItem, Looper looper, AssetLoader.Listener listener) {
    MediaItem mediaItem = editedMediaItem.mediaItem;
    if (isImage(mediaItem.localConfiguration)) {
      if (imageAssetLoaderFactory == null) {
        imageAssetLoaderFactory = new ImageAssetLoader.Factory(bitmapLoader);
      }
      return imageAssetLoaderFactory.createAssetLoader(editedMediaItem, looper, listener);
    }
    if (exoPlayerAssetLoaderFactory == null) {
      exoPlayerAssetLoaderFactory =
          mediaSourceFactory != null
              ? new ExoPlayerAssetLoader.Factory(
                  context, decoderFactory, hdrMode, clock, mediaSourceFactory)
              : new ExoPlayerAssetLoader.Factory(context, decoderFactory, hdrMode, clock);
    }
    return exoPlayerAssetLoaderFactory.createAssetLoader(editedMediaItem, looper, listener);
  }

  private boolean isImage(@Nullable MediaItem.LocalConfiguration localConfiguration) {
    if (localConfiguration == null) {
      return false;
    }
    @Nullable String mimeType = localConfiguration.mimeType;
    if (mimeType == null) {
      if (Objects.equals(localConfiguration.uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
        ContentResolver cr = context.getContentResolver();
        mimeType = cr.getType(localConfiguration.uri);
      } else {
        String fileExtension = FileTypes.getFileExtensionFromUri(localConfiguration.uri);
        mimeType = getCommonImageMimeTypeFromExtension(Ascii.toLowerCase(fileExtension));
      }
    }
    if (mimeType == null) {
      return false;
    }
    if (!MimeTypes.isImage(mimeType)) {
      return false;
    }
    checkState(
        bitmapLoader.supportsMimeType(mimeType),
        "Image format not supported by given bitmapLoader");
    return true;
  }

  @Nullable
  private static String getCommonImageMimeTypeFromExtension(String extension) {
    switch (extension) {
      case "bmp":
      case "dib":
        return MimeTypes.IMAGE_BMP;
      case "heif":
      case "heic":
        return MimeTypes.IMAGE_HEIF;
      case "jpg":
      case "jpeg":
      case "jpe":
      case "jif":
      case "jfif":
      case "jfi":
        return MimeTypes.IMAGE_JPEG;
      case "png":
        return MimeTypes.IMAGE_PNG;
      case "webp":
        return MimeTypes.IMAGE_WEBP;
      case "gif":
        return "image/gif";
      case "tiff":
      case "tif":
        return "image/tiff";
      case "raw":
      case "arw":
      case "cr2":
      case "k25":
        return "image/raw";
      case "svg":
      case "svgz":
        return "image/svg+xml";
      case "ico":
        return "image/x-icon";
      case "avif":
        return "image/avif";
      default:
        return null;
    }
  }
}
