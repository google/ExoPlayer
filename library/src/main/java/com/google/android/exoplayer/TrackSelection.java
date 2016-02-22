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

/**
 * Defines a track selection.
 */
public final class TrackSelection {

  /**
   * The index of the {@link TrackGroup}.
   */
  public final int group;
  /**
   * The indices of the individual tracks within the {@link TrackGroup}.
   */
  public final int[] tracks;

  /**
   * @param group The index of the {@link TrackGroup}.
   * @param tracks The indices of the individual tracks within the {@link TrackGroup}.
   */
  public TrackSelection(int group, int... tracks) {
    this.group = group;
    this.tracks = tracks;
  }

}
