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
package com.google.android.exoplayer.dash.mpd;

import java.util.Collections;
import java.util.List;

/**
 * Represents a DASH media presentation description (mpd).
 */
public final class MediaPresentationDescription {

  public final long duration;

  public final long minBufferTime;

  public final boolean dynamic;

  public final long minUpdatePeriod;

  public final List<Period> periods;

  public MediaPresentationDescription(long duration, long minBufferTime, boolean dynamic,
      long minUpdatePeriod, List<Period> periods) {
    this.duration = duration;
    this.minBufferTime = minBufferTime;
    this.dynamic = dynamic;
    this.minUpdatePeriod = minUpdatePeriod;
    this.periods = Collections.unmodifiableList(periods);
  }

}
