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
package com.google.android.exoplayer2.offline;

import java.util.List;

/**
 * A manifest that can generate copies of itself including only the tracks specified by the given
 * track keys.
 *
 * @param <T> The manifest type.
 * @param <K> The track key type.
 */
public interface FilterableManifest<T, K> {

  /**
   * Returns a copy of the manifest including only the tracks specified by the given track keys.
   *
   * @param trackKeys A non-empty list of track keys.
   * @return The filtered manifest.
   */
  T copy(List<K> trackKeys);
}
