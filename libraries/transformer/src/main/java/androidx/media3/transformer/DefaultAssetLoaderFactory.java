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

package androidx.media3.transformer;

import android.content.Context;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** The default {@link AssetLoader.Factory} implementation. */
@UnstableApi
public final class DefaultAssetLoaderFactory implements AssetLoader.Factory {

  private final AssetLoader.Factory assetLoaderFactory;

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
   *     transform when an {@link ExoPlayerAssetLoader} is used.
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   */
  public DefaultAssetLoaderFactory(
      Context context, MediaSource.Factory mediaSourceFactory, Clock clock) {
    assetLoaderFactory = new ExoPlayerAssetLoader.Factory(context, mediaSourceFactory, clock);
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setRemoveAudio(boolean removeAudio) {
    return assetLoaderFactory.setRemoveAudio(removeAudio);
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setRemoveVideo(boolean removeVideo) {
    return assetLoaderFactory.setRemoveVideo(removeVideo);
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setFlattenVideoForSlowMotion(boolean flattenVideoForSlowMotion) {
    assetLoaderFactory.setFlattenVideoForSlowMotion(flattenVideoForSlowMotion);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setDecoderFactory(Codec.DecoderFactory decoderFactory) {
    return assetLoaderFactory.setDecoderFactory(decoderFactory);
  }

  @Override
  public AssetLoader createAssetLoader(
      MediaItem mediaItem, Looper looper, AssetLoader.Listener listener) {
    return assetLoaderFactory.createAssetLoader(mediaItem, looper, listener);
  }
}
