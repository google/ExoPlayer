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

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.NetworkTypeObserver;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.TimeToFirstByteEstimator;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;

/**
 * An experimental {@link BandwidthMeter} that estimates bandwidth by listening to data transfers.
 *
 * <p>The initial estimate is based on the current operator's network country code or the locale of
 * the user, as well as the network connection type. This can be configured in the {@link Builder}.
 */
@UnstableApi
public final class ExperimentalBandwidthMeter implements BandwidthMeter, TransferListener {

  /** Default initial Wifi bitrate estimate in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI =
      ImmutableList.of(4_300_000L, 3_200_000L, 2_400_000L, 1_700_000L, 860_000L);

  /** Default initial 2G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_2G =
      ImmutableList.of(1_500_000L, 980_000L, 750_000L, 520_000L, 290_000L);

  /** Default initial 3G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_3G =
      ImmutableList.of(2_000_000L, 1_300_000L, 1_000_000L, 860_000L, 610_000L);

  /** Default initial 4G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_4G =
      ImmutableList.of(2_500_000L, 1_700_000L, 1_200_000L, 970_000L, 680_000L);

  /** Default initial 5G-NSA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA =
      ImmutableList.of(4_700_000L, 2_800_000L, 2_100_000L, 1_700_000L, 980_000L);

  /** Default initial 5G-SA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA =
      ImmutableList.of(2_700_000L, 2_000_000L, 1_600_000L, 1_300_000L, 1_000_000L);

  /**
   * Default number of samples to keep in the sliding window for estimating the time to first byte.
   */
  public static final int DEFAULT_TIME_TO_FIRST_BYTE_SAMPLES = 20;

  /** Default percentile for estimating the time to first byte. */
  public static final float DEFAULT_TIME_TO_FIRST_BYTE_PERCENTILE = 0.5f;

  /**
   * Default initial bitrate estimate used when the device is offline or the network type cannot be
   * determined, in bits per second.
   */
  public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1_000_000;

  /**
   * Index for the Wifi group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_WIFI = 0;

  /**
   * Index for the 2G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_2G = 1;

  /**
   * Index for the 3G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_3G = 2;

  /**
   * Index for the 4G group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_4G = 3;

  /**
   * Index for the 5G-NSA group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_5G_NSA = 4;

  /**
   * Index for the 5G-SA group index in the array returned by {@link
   * #getInitialBitrateCountryGroupAssignment}.
   */
  private static final int COUNTRY_GROUP_INDEX_5G_SA = 5;

  /** Builder for a bandwidth meter. */
  public static final class Builder {

    private final Context context;

    private Map<Integer, Long> initialBitrateEstimates;
    private TimeToFirstByteEstimator timeToFirstByteEstimator;
    private BandwidthEstimator bandwidthEstimator;
    private boolean resetOnNetworkTypeChange;

    /**
     * Creates a builder with default parameters and without listener.
     *
     * @param context A context.
     */
    public Builder(Context context) {
      // Handling of null is for backward compatibility only.
      this.context = context.getApplicationContext();
      initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.getCountryCode(context));
      timeToFirstByteEstimator =
          new PercentileTimeToFirstByteEstimator(
              /* numberOfSamples= */ DEFAULT_TIME_TO_FIRST_BYTE_SAMPLES,
              /* percentile= */ DEFAULT_TIME_TO_FIRST_BYTE_PERCENTILE);
      bandwidthEstimator = new SplitParallelSampleBandwidthEstimator.Builder().build();
      resetOnNetworkTypeChange = true;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable.
     *
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(long initialBitrateEstimate) {
      for (Integer networkType : initialBitrateEstimates.keySet()) {
        setInitialBitrateEstimate(networkType, initialBitrateEstimate);
      }
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable and the current network connection is of the specified type.
     *
     * @param networkType The {@link C.NetworkType} this initial estimate is for.
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(
        @C.NetworkType int networkType, long initialBitrateEstimate) {
      initialBitrateEstimates.put(networkType, initialBitrateEstimate);
      return this;
    }

    /**
     * Sets the initial bitrate estimates to the default values of the specified country. The
     * initial estimates are used when a bandwidth estimate is unavailable.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code of the country whose default bitrate
     *     estimates should be used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitialBitrateEstimate(String countryCode) {
      initialBitrateEstimates =
          getInitialBitrateEstimatesForCountry(Ascii.toUpperCase(countryCode));
      return this;
    }

    /**
     * Sets the {@link TimeToFirstByteEstimator} to be used.
     *
     * <p>Default is {@link PercentileTimeToFirstByteEstimator} with a sliding window size of {@link
     * #DEFAULT_TIME_TO_FIRST_BYTE_SAMPLES} that uses a percentile of {@link
     * #DEFAULT_TIME_TO_FIRST_BYTE_PERCENTILE}.
     *
     * @param timeToFirstByteEstimator The {@link TimeToFirstByteEstimator} to be used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setTimeToFirstByteEstimator(TimeToFirstByteEstimator timeToFirstByteEstimator) {
      this.timeToFirstByteEstimator = timeToFirstByteEstimator;
      return this;
    }

    /**
     * Sets the {@link BandwidthEstimator} used. By default, this is set to a {@link
     * SplitParallelSampleBandwidthEstimator} using a {@link
     * SlidingWeightedAverageBandwidthStatistic}.
     */
    @CanIgnoreReturnValue
    public Builder setBandwidthEstimator(BandwidthEstimator bandwidthEstimator) {
      this.bandwidthEstimator = bandwidthEstimator;
      return this;
    }

    /**
     * Sets whether to reset if the network type changes. The default value is {@code true}.
     *
     * @param resetOnNetworkTypeChange Whether to reset if the network type changes.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setResetOnNetworkTypeChange(boolean resetOnNetworkTypeChange) {
      this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
      return this;
    }

    /**
     * Builds the bandwidth meter.
     *
     * @return A bandwidth meter with the configured properties.
     */
    public ExperimentalBandwidthMeter build() {
      return new ExperimentalBandwidthMeter(
          context,
          initialBitrateEstimates,
          timeToFirstByteEstimator,
          bandwidthEstimator,
          resetOnNetworkTypeChange);
    }

    private static Map<Integer, Long> getInitialBitrateEstimatesForCountry(String countryCode) {
      int[] groupIndices = getInitialBitrateCountryGroupAssignment(countryCode);
      Map<Integer, Long> result = new HashMap<>(/* initialCapacity= */ 8);
      result.put(C.NETWORK_TYPE_UNKNOWN, DEFAULT_INITIAL_BITRATE_ESTIMATE);
      result.put(
          C.NETWORK_TYPE_WIFI,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices[COUNTRY_GROUP_INDEX_WIFI]));
      result.put(
          C.NETWORK_TYPE_2G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_2G.get(groupIndices[COUNTRY_GROUP_INDEX_2G]));
      result.put(
          C.NETWORK_TYPE_3G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_3G.get(groupIndices[COUNTRY_GROUP_INDEX_3G]));
      result.put(
          C.NETWORK_TYPE_4G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_4G.get(groupIndices[COUNTRY_GROUP_INDEX_4G]));
      result.put(
          C.NETWORK_TYPE_5G_NSA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA.get(groupIndices[COUNTRY_GROUP_INDEX_5G_NSA]));
      result.put(
          C.NETWORK_TYPE_5G_SA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA.get(groupIndices[COUNTRY_GROUP_INDEX_5G_SA]));
      // Assume default Wifi speed for Ethernet to prevent using the slower fallback.
      result.put(
          C.NETWORK_TYPE_ETHERNET,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices[COUNTRY_GROUP_INDEX_WIFI]));
      return result;
    }
  }

  private final ImmutableMap<Integer, Long> initialBitrateEstimates;
  private final boolean resetOnNetworkTypeChange;

  @GuardedBy("this") // Used in TransferListener methods that are called on a background thread.
  private final TimeToFirstByteEstimator timeToFirstByteEstimator;

  @GuardedBy("this") // Used in TransferListener methods that are called on a background thread.
  private final BandwidthEstimator bandwidthEstimator;

  private @C.NetworkType int networkType;
  private long initialBitrateEstimate;
  private boolean networkTypeOverrideSet;
  private @C.NetworkType int networkTypeOverride;

  private ExperimentalBandwidthMeter(
      Context context,
      Map<Integer, Long> initialBitrateEstimates,
      TimeToFirstByteEstimator timeToFirstByteEstimator,
      BandwidthEstimator bandwidthEstimator,
      boolean resetOnNetworkTypeChange) {
    this.initialBitrateEstimates = ImmutableMap.copyOf(initialBitrateEstimates);
    this.timeToFirstByteEstimator = timeToFirstByteEstimator;
    this.bandwidthEstimator = bandwidthEstimator;
    this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
    NetworkTypeObserver networkTypeObserver = NetworkTypeObserver.getInstance(context);
    networkType = networkTypeObserver.getNetworkType();
    initialBitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    networkTypeObserver.register(/* listener= */ this::onNetworkTypeChanged);
  }

  /**
   * Overrides the network type. Handled in the same way as if the meter had detected a change from
   * the current network type to the specified network type internally.
   *
   * <p>Applications should not normally call this method. It is intended for testing purposes.
   *
   * @param networkType The overriding network type.
   */
  public synchronized void setNetworkTypeOverride(@C.NetworkType int networkType) {
    networkTypeOverride = networkType;
    networkTypeOverrideSet = true;
    onNetworkTypeChanged(networkType);
  }

  @Override
  public synchronized long getBitrateEstimate() {
    long bandwidthEstimate = bandwidthEstimator.getBandwidthEstimate();
    return bandwidthEstimate != BandwidthEstimator.ESTIMATE_NOT_AVAILABLE
        ? bandwidthEstimate
        : initialBitrateEstimate;
  }

  @Override
  public synchronized long getTimeToFirstByteEstimateUs() {
    return timeToFirstByteEstimator.getTimeToFirstByteEstimateUs();
  }

  @Override
  public TransferListener getTransferListener() {
    return this;
  }

  @Override
  public synchronized void addEventListener(Handler eventHandler, EventListener eventListener) {
    checkNotNull(eventHandler);
    checkNotNull(eventListener);
    bandwidthEstimator.addEventListener(eventHandler, eventListener);
  }

  @Override
  public synchronized void removeEventListener(EventListener eventListener) {
    bandwidthEstimator.removeEventListener(eventListener);
  }

  @Override
  public synchronized void onTransferInitializing(
      DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    timeToFirstByteEstimator.onTransferInitializing(dataSpec);
    bandwidthEstimator.onTransferInitializing(source);
  }

  @Override
  public synchronized void onTransferStart(
      DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    timeToFirstByteEstimator.onTransferStart(dataSpec);
    bandwidthEstimator.onTransferStart(source);
  }

  @Override
  public synchronized void onBytesTransferred(
      DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    bandwidthEstimator.onBytesTransferred(source, bytesTransferred);
  }

  @Override
  public synchronized void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    bandwidthEstimator.onTransferEnd(source);
  }

  private synchronized void onNetworkTypeChanged(@C.NetworkType int networkType) {
    if (this.networkType != C.NETWORK_TYPE_UNKNOWN && !resetOnNetworkTypeChange) {
      // Reset on network change disabled. Ignore all updates except the initial one.
      return;
    }

    if (networkTypeOverrideSet) {
      networkType = networkTypeOverride;
    }
    if (this.networkType == networkType) {
      return;
    }

    this.networkType = networkType;
    if (networkType == C.NETWORK_TYPE_OFFLINE
        || networkType == C.NETWORK_TYPE_UNKNOWN
        || networkType == C.NETWORK_TYPE_OTHER) {
      // It's better not to reset the bandwidth meter for these network types.
      return;
    }

    // Reset the bitrate estimate and report it, along with any bytes transferred.
    this.initialBitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    bandwidthEstimator.onNetworkTypeChange(initialBitrateEstimate);
    timeToFirstByteEstimator.reset();
  }

  private long getInitialBitrateEstimateForNetworkType(@C.NetworkType int networkType) {
    @Nullable Long initialBitrateEstimate = initialBitrateEstimates.get(networkType);
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = initialBitrateEstimates.get(C.NETWORK_TYPE_UNKNOWN);
    }
    if (initialBitrateEstimate == null) {
      initialBitrateEstimate = DEFAULT_INITIAL_BITRATE_ESTIMATE;
    }
    return initialBitrateEstimate;
  }

  private static boolean isTransferAtFullNetworkSpeed(DataSpec dataSpec, boolean isNetwork) {
    return isNetwork && !dataSpec.isFlagSet(DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED);
  }

  /**
   * Returns initial bitrate group assignments for a {@code country}. The initial bitrate is a list
   * of indices for [Wifi, 2G, 3G, 4G, 5G_NSA, 5G_SA].
   */
  private static int[] getInitialBitrateCountryGroupAssignment(String country) {
    switch (country) {
      case "AE":
        return new int[] {1, 4, 2, 3, 4, 1};
      case "AL":
        return new int[] {1, 1, 1, 2, 2, 2};
      case "AO":
        return new int[] {3, 4, 4, 3, 2, 2};
      case "AR":
        return new int[] {2, 2, 2, 2, 1, 2};
      case "AS":
        return new int[] {2, 2, 3, 3, 2, 2};
      case "AU":
        return new int[] {0, 3, 1, 1, 3, 0};
      case "AW":
        return new int[] {2, 2, 3, 4, 2, 2};
      case "BD":
        return new int[] {2, 1, 3, 2, 4, 2};
      case "BE":
        return new int[] {0, 0, 1, 0, 1, 2};
      case "BH":
        return new int[] {1, 3, 1, 3, 4, 2};
      case "BJ":
        return new int[] {4, 4, 2, 3, 2, 2};
      case "BM":
        return new int[] {0, 2, 0, 0, 2, 2};
      case "BN":
        return new int[] {3, 2, 0, 0, 2, 2};
      case "BO":
        return new int[] {1, 2, 4, 4, 2, 2};
      case "BR":
        return new int[] {1, 1, 1, 1, 2, 4};
      case "BS":
        return new int[] {3, 2, 1, 1, 2, 2};
      case "BT":
        return new int[] {3, 1, 2, 2, 3, 2};
      case "BW":
        return new int[] {3, 2, 1, 0, 2, 2};
      case "BY":
        return new int[] {1, 2, 3, 3, 2, 2};
      case "CF":
        return new int[] {4, 2, 4, 2, 2, 2};
      case "CH":
        return new int[] {0, 1, 0, 0, 0, 2};
      case "AG":
      case "CI":
        return new int[] {2, 4, 3, 4, 2, 2};
      case "BZ":
      case "CK":
        return new int[] {2, 2, 2, 1, 2, 2};
      case "CN":
        return new int[] {2, 0, 1, 1, 3, 1};
      case "CO":
        return new int[] {2, 3, 3, 2, 2, 2};
      case "CV":
        return new int[] {2, 3, 0, 1, 2, 2};
      case "CY":
        return new int[] {1, 0, 1, 0, 0, 2};
      case "CZ":
        return new int[] {0, 0, 2, 0, 1, 2};
      case "DE":
        return new int[] {0, 1, 4, 2, 2, 1};
      case "DK":
        return new int[] {0, 0, 2, 0, 0, 2};
      case "EC":
        return new int[] {1, 3, 2, 1, 2, 2};
      case "ES":
        return new int[] {0, 0, 0, 0, 1, 0};
      case "ET":
        return new int[] {4, 3, 4, 4, 4, 2};
      case "FI":
        return new int[] {0, 0, 0, 1, 0, 2};
      case "FJ":
        return new int[] {3, 2, 2, 3, 2, 2};
      case "FM":
        return new int[] {4, 2, 4, 0, 2, 2};
      case "FO":
        return new int[] {0, 2, 2, 0, 2, 2};
      case "FR":
        return new int[] {1, 1, 1, 1, 0, 2};
      case "GA":
        return new int[] {3, 4, 0, 0, 2, 2};
      case "GB":
        return new int[] {1, 1, 3, 2, 2, 2};
      case "GD":
        return new int[] {2, 2, 0, 0, 2, 2};
      case "GE":
        return new int[] {1, 1, 0, 2, 2, 2};
      case "GF":
        return new int[] {3, 2, 3, 3, 2, 2};
      case "GG":
        return new int[] {0, 2, 1, 1, 2, 2};
      case "GH":
        return new int[] {3, 3, 3, 2, 2, 2};
      case "GN":
        return new int[] {3, 4, 4, 2, 2, 2};
      case "GP":
        return new int[] {2, 1, 1, 3, 2, 2};
      case "GR":
        return new int[] {1, 0, 0, 0, 1, 2};
      case "GT":
        return new int[] {2, 1, 2, 1, 2, 2};
      case "GU":
        return new int[] {2, 2, 4, 3, 3, 2};
      case "GW":
        return new int[] {4, 4, 1, 2, 2, 2};
      case "GY":
        return new int[] {3, 1, 1, 3, 2, 2};
      case "HK":
        return new int[] {0, 1, 0, 1, 1, 0};
      case "ID":
        return new int[] {3, 1, 3, 3, 2, 4};
      case "IE":
        return new int[] {1, 1, 1, 1, 1, 2};
      case "IL":
        return new int[] {1, 2, 2, 3, 4, 2};
      case "IN":
        return new int[] {1, 1, 3, 2, 2, 3};
      case "IO":
        return new int[] {3, 2, 2, 0, 2, 2};
      case "IQ":
        return new int[] {3, 2, 3, 2, 2, 2};
      case "IR":
        return new int[] {4, 2, 3, 3, 4, 3};
      case "IT":
        return new int[] {0, 1, 1, 2, 1, 2};
      case "GI":
      case "IM":
      case "JE":
        return new int[] {0, 2, 0, 1, 2, 2};
      case "JM":
        return new int[] {2, 4, 3, 1, 2, 2};
      case "JP":
        return new int[] {0, 3, 2, 3, 4, 2};
      case "KE":
        return new int[] {3, 2, 1, 1, 1, 2};
      case "KG":
        return new int[] {2, 1, 1, 2, 2, 2};
      case "KH":
        return new int[] {1, 0, 4, 2, 2, 2};
      case "KR":
        return new int[] {0, 2, 2, 4, 4, 4};
      case "HR":
      case "KW":
        return new int[] {1, 0, 0, 0, 0, 2};
      case "KZ":
        return new int[] {2, 1, 2, 2, 3, 2};
      case "LA":
        return new int[] {1, 2, 1, 3, 2, 2};
      case "LB":
        return new int[] {3, 1, 1, 2, 2, 2};
      case "LC":
        return new int[] {2, 2, 1, 1, 2, 2};
      case "DO":
      case "LR":
        return new int[] {3, 4, 4, 4, 2, 2};
      case "LT":
        return new int[] {0, 1, 0, 1, 0, 2};
      case "LU":
        return new int[] {4, 0, 3, 2, 1, 3};
      case "MA":
        return new int[] {3, 3, 1, 1, 2, 2};
      case "GL":
      case "MC":
        return new int[] {1, 2, 2, 0, 2, 2};
      case "MD":
        return new int[] {1, 0, 0, 0, 2, 2};
      case "ME":
        return new int[] {2, 0, 0, 1, 3, 2};
      case "MF":
        return new int[] {1, 2, 2, 3, 2, 2};
      case "CG":
      case "EG":
      case "MG":
        return new int[] {3, 4, 3, 3, 2, 2};
      case "MK":
        return new int[] {1, 0, 0, 1, 3, 2};
      case "CD":
      case "ML":
        return new int[] {3, 3, 2, 2, 2, 2};
      case "LK":
      case "MM":
        return new int[] {3, 2, 3, 3, 4, 2};
      case "MN":
        return new int[] {2, 0, 2, 2, 2, 2};
      case "MO":
        return new int[] {0, 2, 4, 4, 3, 1};
      case "MQ":
        return new int[] {2, 1, 2, 3, 2, 2};
      case "CM":
      case "MR":
        return new int[] {4, 3, 3, 4, 2, 2};
      case "MU":
        return new int[] {3, 1, 0, 2, 2, 2};
      case "MV":
        return new int[] {3, 2, 1, 3, 4, 2};
      case "MW":
        return new int[] {3, 2, 2, 1, 2, 2};
      case "MX":
        return new int[] {2, 4, 4, 4, 3, 2};
      case "MY":
        return new int[] {1, 0, 4, 1, 1, 0};
      case "NA":
        return new int[] {3, 4, 3, 2, 2, 2};
      case "NG":
        return new int[] {3, 4, 2, 1, 2, 2};
      case "CR":
      case "NI":
        return new int[] {2, 4, 4, 4, 2, 2};
      case "NL":
        return new int[] {2, 1, 4, 3, 0, 4};
      case "NO":
        return new int[] {0, 0, 3, 0, 0, 2};
      case "NP":
        return new int[] {2, 2, 4, 3, 2, 2};
      case "NZ":
        return new int[] {0, 0, 1, 2, 4, 2};
      case "OM":
        return new int[] {2, 3, 1, 2, 4, 2};
      case "AM":
      case "PA":
        return new int[] {2, 3, 2, 3, 2, 2};
      case "PE":
        return new int[] {1, 2, 4, 4, 3, 2};
      case "PF":
        return new int[] {2, 2, 3, 1, 2, 2};
      case "LS":
      case "PG":
        return new int[] {4, 3, 3, 3, 2, 2};
      case "PH":
        return new int[] {2, 1, 2, 3, 2, 1};
      case "PK":
        return new int[] {3, 3, 3, 3, 2, 2};
      case "PL":
        return new int[] {1, 0, 2, 2, 4, 4};
      case "PR":
        return new int[] {2, 0, 2, 1, 2, 0};
      case "PS":
        return new int[] {3, 4, 1, 3, 2, 2};
      case "PW":
        return new int[] {2, 2, 4, 1, 2, 2};
      case "BL":
      case "MP":
      case "PY":
        return new int[] {1, 2, 2, 2, 2, 2};
      case "QA":
        return new int[] {1, 4, 4, 4, 4, 2};
      case "RE":
        return new int[] {0, 3, 2, 3, 1, 2};
      case "RO":
        return new int[] {0, 0, 1, 1, 3, 2};
      case "RS":
        return new int[] {1, 0, 0, 1, 2, 2};
      case "RU":
        return new int[] {1, 0, 0, 1, 3, 3};
      case "RW":
        return new int[] {3, 3, 2, 0, 2, 2};
      case "SA":
        return new int[] {3, 1, 1, 2, 2, 0};
      case "AT":
      case "EE":
      case "HU":
      case "IS":
      case "LV":
      case "MT":
      case "SE":
        return new int[] {0, 0, 0, 0, 0, 2};
      case "SG":
        return new int[] {2, 3, 3, 3, 1, 1};
      case "AQ":
      case "ER":
      case "NU":
      case "SC":
      case "SH":
        return new int[] {4, 2, 2, 2, 2, 2};
      case "BG":
      case "PT":
      case "SI":
        return new int[] {0, 0, 0, 0, 1, 2};
      case "FK":
      case "NF":
      case "SJ":
        return new int[] {3, 2, 2, 2, 2, 2};
      case "SK":
        return new int[] {0, 1, 1, 1, 2, 2};
      case "AZ":
      case "DJ":
      case "LY":
      case "SL":
        return new int[] {4, 2, 3, 3, 2, 2};
      case "SN":
        return new int[] {4, 4, 3, 2, 2, 2};
      case "SO":
        return new int[] {2, 2, 3, 4, 4, 2};
      case "SR":
        return new int[] {2, 4, 4, 1, 2, 2};
      case "GM":
      case "SS":
        return new int[] {4, 3, 2, 4, 2, 2};
      case "ST":
        return new int[] {2, 2, 1, 2, 2, 2};
      case "SV":
        return new int[] {2, 3, 2, 1, 2, 2};
      case "AF":
      case "SZ":
        return new int[] {4, 4, 3, 4, 2, 2};
      case "TC":
        return new int[] {3, 2, 1, 2, 2, 2};
      case "BF":
      case "SD":
      case "SY":
      case "TD":
        return new int[] {4, 3, 4, 4, 2, 2};
      case "TG":
        return new int[] {3, 4, 1, 0, 2, 2};
      case "CL":
      case "TH":
        return new int[] {0, 1, 2, 2, 2, 2};
      case "DZ":
      case "TJ":
        return new int[] {3, 3, 4, 4, 2, 2};
      case "CU":
      case "KI":
      case "NR":
      case "TL":
        return new int[] {4, 2, 4, 4, 2, 2};
      case "TN":
        return new int[] {3, 1, 1, 1, 2, 2};
      case "TO":
        return new int[] {3, 2, 4, 3, 2, 2};
      case "BA":
      case "JO":
      case "TR":
        return new int[] {1, 1, 1, 1, 2, 2};
      case "TT":
        return new int[] {2, 4, 1, 0, 2, 2};
      case "TW":
        return new int[] {0, 0, 0, 0, 0, 0};
      case "TZ":
        return new int[] {3, 4, 2, 1, 3, 2};
      case "CA":
      case "UA":
        return new int[] {0, 2, 1, 2, 3, 3};
      case "UG":
        return new int[] {3, 3, 2, 3, 4, 2};
      case "US":
        return new int[] {2, 2, 4, 1, 3, 1};
      case "UY":
        return new int[] {2, 1, 1, 2, 1, 2};
      case "UZ":
        return new int[] {1, 2, 3, 4, 3, 2};
      case "AX":
      case "CX":
      case "LI":
      case "MS":
      case "PM":
      case "SM":
      case "VA":
        return new int[] {0, 2, 2, 2, 2, 2};
      case "AD":
      case "AI":
      case "BB":
      case "BQ":
      case "CW":
      case "DM":
      case "KN":
      case "KY":
      case "SX":
      case "VC":
        return new int[] {1, 2, 0, 0, 2, 2};
      case "VG":
        return new int[] {2, 2, 1, 1, 2, 4};
      case "VI":
        return new int[] {0, 2, 1, 2, 2, 2};
      case "VN":
        return new int[] {0, 0, 1, 2, 2, 2};
      case "KM":
      case "VU":
        return new int[] {4, 3, 3, 2, 2, 2};
      case "MH":
      case "TM":
      case "TV":
      case "WF":
        return new int[] {4, 2, 2, 4, 2, 2};
      case "MZ":
      case "WS":
        return new int[] {3, 1, 2, 2, 2, 2};
      case "XK":
        return new int[] {1, 2, 1, 1, 2, 2};
      case "BI":
      case "GQ":
      case "HT":
      case "NE":
      case "VE":
      case "YE":
        return new int[] {4, 4, 4, 4, 2, 2};
      case "NC":
      case "YT":
        return new int[] {2, 3, 3, 4, 2, 2};
      case "ZA":
        return new int[] {2, 4, 2, 1, 1, 2};
      case "ZM":
        return new int[] {4, 4, 4, 3, 2, 2};
      case "SB":
      case "ZW":
        return new int[] {4, 2, 4, 3, 2, 2};
      default:
        return new int[] {2, 2, 2, 2, 2, 2};
    }
  }
}
