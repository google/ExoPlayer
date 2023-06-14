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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
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
  private final boolean forceInterpretHdrAsSdr;
  private final Clock clock;
  private final MediaSource.@MonotonicNonNull Factory mediaSourceFactory;

  private AssetLoader.@MonotonicNonNull Factory imageAssetLoaderFactory;
  private AssetLoader.@MonotonicNonNull Factory exoPlayerAssetLoaderFactory;
  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param forceInterpretHdrAsSdr Whether to apply {@link
   *     TransformationRequest#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      boolean forceInterpretHdrAsSdr,
      Clock clock) {
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
    this.clock = clock;
    this.mediaSourceFactory = null;
  }

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param forceInterpretHdrAsSdr Whether to apply {@link
   *     TransformationRequest#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
   *     transform when an {@link ExoPlayerAssetLoader} is used.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      boolean forceInterpretHdrAsSdr,
      Clock clock,
      MediaSource.Factory mediaSourceFactory) {
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.forceInterpretHdrAsSdr = forceInterpretHdrAsSdr;
    this.clock = clock;
    this.mediaSourceFactory = mediaSourceFactory;
  }

  @Override
  public AssetLoader createAssetLoader(
      EditedMediaItem editedMediaItem, Looper looper, AssetLoader.Listener listener) {
    MediaItem mediaItem = editedMediaItem.mediaItem;
    if (isImage(mediaItem.localConfiguration)) {
      if (imageAssetLoaderFactory == null) {
        imageAssetLoaderFactory = new ImageAssetLoader.Factory(context);
      }
      return imageAssetLoaderFactory.createAssetLoader(editedMediaItem, looper, listener);
    }
    if (exoPlayerAssetLoaderFactory == null) {
      exoPlayerAssetLoaderFactory =
          mediaSourceFactory != null
              ? new ExoPlayerAssetLoader.Factory(
                  context, decoderFactory, forceInterpretHdrAsSdr, clock, mediaSourceFactory)
              : new ExoPlayerAssetLoader.Factory(
                  context, decoderFactory, forceInterpretHdrAsSdr, clock);
    }
    return exoPlayerAssetLoaderFactory.createAssetLoader(editedMediaItem, looper, listener);
  }

  private static boolean isImage(@Nullable MediaItem.LocalConfiguration localConfiguration) {
    if (localConfiguration == null) {
      return false;
    }
    if (localConfiguration.mimeType != null) {
      return MimeTypes.isImage(localConfiguration.mimeType);
    }
    ImmutableList<String> supportedImageTypes =
        ImmutableList.of(".png", ".webp", ".jpg", ".jpeg", ".heic", ".heif", ".bmp");
    String uriPath = checkNotNull(localConfiguration.uri.getPath());
    int fileExtensionStart = uriPath.lastIndexOf(".");
    if (fileExtensionStart < 0) {
      return false;
    }
    String extension = Ascii.toLowerCase(uriPath.substring(fileExtensionStart));
    return supportedImageTypes.contains(extension);
  }
}
