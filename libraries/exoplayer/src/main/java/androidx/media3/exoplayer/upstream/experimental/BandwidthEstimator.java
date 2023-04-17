/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.exoplayer.upstream.experimental;

import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

/** The interface for different bandwidth estimation strategies. */
@UnstableApi
public interface BandwidthEstimator {

  long ESTIMATE_NOT_AVAILABLE = Long.MIN_VALUE;

  /**
   * Adds an {@link BandwidthMeter.EventListener}.
   *
   * @param eventHandler A handler for events.
   * @param eventListener A listener of events.
   */
  void addEventListener(Handler eventHandler, BandwidthMeter.EventListener eventListener);

  /**
   * Removes an {@link BandwidthMeter.EventListener}.
   *
   * @param eventListener The listener to be removed.
   */
  void removeEventListener(BandwidthMeter.EventListener eventListener);

  /**
   * Called when a transfer is being initialized.
   *
   * @param source The {@link DataSource} performing the transfer.
   */
  void onTransferInitializing(DataSource source);

  /**
   * Called when a transfer starts.
   *
   * @param source The {@link DataSource} performing the transfer.
   */
  void onTransferStart(DataSource source);

  /**
   * Called incrementally during a transfer.
   *
   * @param source The {@link DataSource} performing the transfer.
   * @param bytesTransferred The number of bytes transferred since the previous call to this method
   */
  void onBytesTransferred(DataSource source, int bytesTransferred);

  /**
   * Called when a transfer ends.
   *
   * @param source The {@link DataSource} performing the transfer.
   */
  void onTransferEnd(DataSource source);

  /**
   * Returns the bandwidth estimate in bits per second, or {@link #ESTIMATE_NOT_AVAILABLE} if there
   * is no estimate available yet.
   */
  long getBandwidthEstimate();

  /**
   * Notifies this estimator that a network change has been detected.
   *
   * @param newBandwidthEstimate The new initial bandwidth estimate based on network type.
   */
  void onNetworkTypeChange(long newBandwidthEstimate);
}
