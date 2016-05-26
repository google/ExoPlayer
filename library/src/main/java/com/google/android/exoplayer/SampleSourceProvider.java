/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

// TODO[playlists]: Rename this and maybe change the interface once we support multi-period DASH.
/**
 * Provides a sequence of {@link SampleSource}s to play back.
 */
public interface SampleSourceProvider {

  /**
   * Returned by {@link #getSourceCount()} if the number of sources is not known.
   */
  int UNKNOWN_SOURCE_COUNT = -1;

  /**
   * Returns the number of sources in the sequence, or {@link #UNKNOWN_SOURCE_COUNT} if the number
   * of sources is not yet known.
   */
  int getSourceCount();

  /**
   * Returns a new {@link SampleSource} providing media at the specified index in the sequence, or
   * {@code null} if the source at the specified index is not yet available.
   *
   * @param index The index of the source to create, which must be less than the count returned by
   *     {@link #getSourceCount()}.
   * @return A new {@link SampleSource}, or {@code null} if the source at the specified index is not
   *     yet available.
   */
  SampleSource createSource(int index);

}
