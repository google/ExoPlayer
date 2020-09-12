/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.annotation.SuppressLint;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.media2.session.MediaSession;

/** Utility methods to use {@link MediaSession} with other ExoPlayer modules. */
public final class MediaSessionUtil {

  /** Gets the {@link MediaSessionCompat.Token} from the {@link MediaSession}. */
  // TODO(b/152764014): Deprecate this API when MediaSession#getSessionCompatToken() is released.
  public static MediaSessionCompat.Token getSessionCompatToken(MediaSession mediaSession) {
    @SuppressLint("RestrictedApi")
    @SuppressWarnings("RestrictTo")
    MediaSessionCompat sessionCompat = mediaSession.getSessionCompat();
    return sessionCompat.getSessionToken();
  }

  private MediaSessionUtil() {
    // Prevent from instantiation.
  }
}
