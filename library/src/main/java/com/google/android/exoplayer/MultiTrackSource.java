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

import com.google.android.exoplayer.ExoPlayer;

public interface MultiTrackSource {
  /**
   * A message to indicate a source selection. Source selection can only be performed when the
   * source is disabled.
   */
  public void selectTrack(ExoPlayer player, int index);

  /**
   * Gets the number of tracks that this source can switch between. May be called safely from any
   * thread.
   *
   * @return The number of tracks.
   */
  public int getTrackCount();
}
