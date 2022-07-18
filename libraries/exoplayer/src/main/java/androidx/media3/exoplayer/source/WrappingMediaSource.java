/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import androidx.media3.common.Timeline;

/**
 * Single-child {@link CompositeMediaSource}.
 */
public abstract class WrappingMediaSource extends CompositeMediaSource<Void> {

  /**
   * @deprecated - Use {@link #onChildSourceInfoRefreshed}.
   */
  @Deprecated
  @Override
  protected final void onChildSourceInfoRefreshed(
      Void id, MediaSource mediaSource, Timeline timeline
  ) {
    onChildSourceInfoRefreshed(timeline);
  }

  /**
   * Called when the source info has been refreshed.
   *
   * @param timeline The timeline of the source.
   */
  protected abstract void onChildSourceInfoRefreshed(Timeline timeline);

  /**
   * Prepares a source.
   *
   * <p>{@link #onChildSourceInfoRefreshed(Timeline)} will be called when the
   * child source updates its timeline with the same {@code id} passed to this method.
   *
   * <p>If sources aren't explicitly released with {@link #releaseChildSource()} they will be
   * released in {@link #releaseSourceInternal()}.
   *
   * @param mediaSource The child {@link MediaSource}.
   */
  protected final void prepareChildSource(MediaSource mediaSource) {
    prepareChildSource(CHILD_SOURCE_ID, mediaSource);
  }

  /**
   * Enables the child source.
   */
  protected final void enableChildSource() {
    enableChildSource(CHILD_SOURCE_ID);
  }

  /**
   * Disables the child source.
   */
  protected final void disableChildSource() {
    disableChildSource(CHILD_SOURCE_ID);
  }

  /**
   * Releases the child source.
   */
  protected final void releaseChildSource() {
    releaseChildSource(CHILD_SOURCE_ID);
  }

  private static final Void CHILD_SOURCE_ID = null;
}
