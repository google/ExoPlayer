/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.av1;

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;

/**
 * Configures and queries the underlying native library.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Gav1Library {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.gav1");
  }

  private static final LibraryLoader LOADER =
      new LibraryLoader("gav1JNI") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  private Gav1Library() {}

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }
}
