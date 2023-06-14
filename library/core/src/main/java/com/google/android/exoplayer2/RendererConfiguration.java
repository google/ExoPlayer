/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.audio.AudioSink.OFFLOAD_MODE_DISABLED;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioSink;

/**
 * The configuration of a {@link Renderer}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class RendererConfiguration {

  /** The default configuration. */
  public static final RendererConfiguration DEFAULT =
      new RendererConfiguration(
          /* offloadModePreferred= */ OFFLOAD_MODE_DISABLED, /* tunneling= */ false);

  /** The offload mode preference with which to configure the renderer. */
  public final @AudioSink.OffloadMode int offloadModePreferred;

  /** Whether to enable tunneling. */
  public final boolean tunneling;

  /**
   * Creates an instance with {@code tunneling} and sets {@link #offloadModePreferred} to {@link
   * AudioSink#OFFLOAD_MODE_DISABLED}.
   *
   * @param tunneling Whether to enable tunneling.
   */
  public RendererConfiguration(boolean tunneling) {
    this.offloadModePreferred = OFFLOAD_MODE_DISABLED;
    this.tunneling = tunneling;
  }

  /**
   * Creates an instance.
   *
   * @param offloadModePreferred The offload mode to use.
   * @param tunneling Whether to enable tunneling.
   */
  public RendererConfiguration(@AudioSink.OffloadMode int offloadModePreferred, boolean tunneling) {
    this.offloadModePreferred = offloadModePreferred;
    this.tunneling = tunneling;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RendererConfiguration other = (RendererConfiguration) obj;
    return offloadModePreferred == other.offloadModePreferred && tunneling == other.tunneling;
  }

  @Override
  public int hashCode() {
    int hashCode = offloadModePreferred << 1;
    hashCode += (tunneling ? 1 : 0);
    return hashCode;
  }
}
