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
package com.google.android.exoplayer.upstream;

/**
 * Provides estimates of the currently available bandwidth.
 */
public interface BandwidthMeter {

  /**
   * Indicates no bandwidth estimate is available.
   */
  final long NO_ESTIMATE = -1;

  /**
   * Gets the estimated bandwidth, in bits/sec.
   *
   * @return Estimated bandwidth in bits/sec, or {@link #NO_ESTIMATE} if no estimate is available.
   */
  long getBitrateEstimate();

}
