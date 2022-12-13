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

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** The default {@link AssetLoader.Factory} implementation. */
@UnstableApi
public final class DefaultAssetLoaderFactory implements AssetLoader.Factory {

  @Nullable private Context context;
  @Nullable private MediaItem mediaItem;
  private boolean removeAudio;
  private boolean removeVideo;
  private boolean flattenVideoForSlowMotion;
  @Nullable private MediaSource.Factory mediaSourceFactory;
  @Nullable private Codec.DecoderFactory decoderFactory;
  @Nullable private Looper looper;
  @Nullable private AssetLoader.Listener listener;
  @Nullable private Clock clock;

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setContext(Context context) {
    this.context = context;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setMediaItem(MediaItem mediaItem) {
    this.mediaItem = mediaItem;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setRemoveAudio(boolean removeAudio) {
    this.removeAudio = removeAudio;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setRemoveVideo(boolean removeVideo) {
    this.removeVideo = removeVideo;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setFlattenVideoForSlowMotion(boolean flattenVideoForSlowMotion) {
    this.flattenVideoForSlowMotion = flattenVideoForSlowMotion;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
    this.mediaSourceFactory = mediaSourceFactory;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setDecoderFactory(Codec.DecoderFactory decoderFactory) {
    this.decoderFactory = decoderFactory;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setLooper(Looper looper) {
    this.looper = looper;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setListener(AssetLoader.Listener listener) {
    this.listener = listener;
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public AssetLoader.Factory setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  @Override
  public AssetLoader createAssetLoader() {
    return new ExoPlayerAssetLoader(
        checkStateNotNull(context),
        checkStateNotNull(mediaItem),
        removeAudio,
        removeVideo,
        flattenVideoForSlowMotion,
        checkStateNotNull(mediaSourceFactory),
        checkStateNotNull(decoderFactory),
        checkStateNotNull(looper),
        checkStateNotNull(listener),
        checkStateNotNull(clock));
  }
}
