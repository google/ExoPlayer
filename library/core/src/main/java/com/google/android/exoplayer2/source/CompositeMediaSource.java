/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.HashMap;

/**
 * Composite {@link MediaSource} consisting of multiple child sources.
 *
 * @param <T> The type of the id used to identify prepared child sources.
 */
public abstract class CompositeMediaSource<T> implements MediaSource {

  private final HashMap<T, MediaSource> childSources;
  private ExoPlayer player;

  /** Create composite media source without child sources. */
  protected CompositeMediaSource() {
    childSources = new HashMap<>();
  }

  @Override
  @CallSuper
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    this.player = player;
  }

  @Override
  @CallSuper
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (MediaSource childSource : childSources.values()) {
      childSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  @CallSuper
  public void releaseSource() {
    for (MediaSource childSource : childSources.values()) {
      childSource.releaseSource();
    }
    childSources.clear();
    player = null;
  }

  /**
   * Called when the source info of a child source has been refreshed.
   *
   * @param id The unique id used to prepare the child source.
   * @param mediaSource The child source whose source info has been refreshed.
   * @param timeline The timeline of the child source.
   * @param manifest The manifest of the child source.
   */
  protected abstract void onChildSourceInfoRefreshed(
      @Nullable T id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest);

  /**
   * Prepares a child source.
   *
   * <p>{@link #onChildSourceInfoRefreshed(Object, MediaSource, Timeline, Object)} will be called
   * when the child source updates its timeline and/or manifest with the same {@code id} passed to
   * this method.
   *
   * <p>Any child sources that aren't explicitly released with {@link #releaseChildSource(Object)}
   * will be released in {@link #releaseSource()}.
   *
   * @param id A unique id to identify the child source preparation. Null is allowed as an id.
   * @param mediaSource The child {@link MediaSource}.
   */
  protected void prepareChildSource(@Nullable final T id, final MediaSource mediaSource) {
    Assertions.checkArgument(!childSources.containsKey(id));
    childSources.put(id, mediaSource);
    mediaSource.prepareSource(
        player,
        /* isTopLevelSource= */ false,
        new Listener() {
          @Override
          public void onSourceInfoRefreshed(
              MediaSource source, Timeline timeline, @Nullable Object manifest) {
            onChildSourceInfoRefreshed(id, mediaSource, timeline, manifest);
          }
        });
  }

  /**
   * Releases a child source.
   *
   * @param id The unique id used to prepare the child source.
   */
  protected void releaseChildSource(@Nullable T id) {
    MediaSource removedChild = childSources.remove(id);
    removedChild.releaseSource();
  }
}
