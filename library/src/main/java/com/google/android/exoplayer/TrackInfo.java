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
 * Holds high level information about a media track.
 */
public final class TrackInfo {

  /**
   * The mime type.
   */
  public final String mimeType;

  /**
   * The duration in microseconds, or {@link C#UNKNOWN_TIME_US} if the duration is unknown.
   */
  public final long durationUs;

  /**
   * @param mimeType The mime type.
   * @param durationUs The duration in microseconds, or {@link C#UNKNOWN_TIME_US} if the duration
   *     is unknown.
   */
  public TrackInfo(String mimeType, long durationUs) {
    this.mimeType = mimeType;
    this.durationUs = durationUs;
  }

}
