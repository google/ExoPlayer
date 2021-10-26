/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.exoplayer.upstream;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;

/** Provides an estimate of the time to first byte of a transfer. */
@UnstableApi
public interface TimeToFirstByteEstimator {
  /**
   * Returns the estimated time to first byte of the response body, in microseconds, or {@link
   * C#TIME_UNSET} if no estimate is available.
   */
  long getTimeToFirstByteEstimateUs();

  /** Resets the estimator. */
  void reset();

  /**
   * Called when a transfer is being initialized.
   *
   * @param dataSpec Describes the data for which the transfer is initialized.
   */
  void onTransferInitializing(DataSpec dataSpec);

  /**
   * Called when a transfer starts.
   *
   * @param dataSpec Describes the data being transferred.
   */
  void onTransferStart(DataSpec dataSpec);
}
