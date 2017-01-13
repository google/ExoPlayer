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
package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.source.TrackGroupArray;

/**
 * The result of a {@link TrackSelector} operation.
 */
public final class TrackSelectorResult {

  /**
   * The groups provided to the {@link TrackSelector}.
   */
  public final TrackGroupArray groups;
  /**
   * A {@link TrackSelectionArray} containing the selection for each renderer.
   */
  public final TrackSelectionArray selections;
  /**
   * An opaque object that will be returned to {@link TrackSelector#onSelectionActivated(Object)}
   * should the selections be activated.
   */
  public final Object info;

  /**
   * @param groups The groups provided to the {@link TrackSelector}.
   * @param selections A {@link TrackSelectionArray} containing the selection for each renderer.
   * @param info An opaque object that will be returned to
   *     {@link TrackSelector#onSelectionActivated(Object)} should the selections be activated.
   */
  public TrackSelectorResult(TrackGroupArray groups, TrackSelectionArray selections, Object info) {
    this.groups = groups;
    this.selections = selections;
    this.info = info;
  }

}
