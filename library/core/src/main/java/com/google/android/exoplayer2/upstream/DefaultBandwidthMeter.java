/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BandwidthMeter.EventListener.EventDispatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.NetworkTypeObserver;
import com.google.android.exoplayer2.util.SlidingPercentile;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates bandwidth by listening to data transfers.
 *
 * <p>The bandwidth estimate is calculated using a {@link SlidingPercentile} and is updated each
 * time a transfer ends. The initial estimate is based on the current operator's network country
 * code or the locale of the user, as well as the network connection type. This can be configured in
 * the {@link Builder}.
 */
public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener {

  /**
   * Country groups used to determine the default initial bitrate estimate. The group assignment for
   * each country is a list for [Wifi, 2G, 3G, 4G, 5G_NSA, 5G_SA].
   */
  public static final ImmutableListMultimap<String, Integer>
      DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS = createInitialBitrateCountryGroupAssignment();

  /** Default initial Wifi bitrate estimate in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI =
      ImmutableList.of(6_200_000L, 3_900_000L, 2_300_000L, 1_300_000L, 620_000L);

  /** Default initial 2G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_2G =
      ImmutableList.of(248_000L, 160_000L, 142_000L, 127_000L, 113_000L);

  /** Default initial 3G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_3G =
      ImmutableList.of(2_200_000L, 1_300_000L, 950_000L, 760_000L, 520_000L);

  /** Default initial 4G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_4G =
      ImmutableList.of(4_400_000L, 2_300_000L, 1_500_000L, 1_100_000L, 640_000L);

  /** Default initial 5G-NSA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA =
      ImmutableList.of(10_000_000L, 7_200_000L, 5_000_000L, 2_700_000L, 1_600_000L);

  /** Default initial 5G-SA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA =
      ImmutableList.of(2_600_000L, 2_200_000L, 2_000_000L, 1_500_000L, 470_000L);

  /**
   * Default initial bitrate estimate used when the device is offline or the network type cannot be
   * determined, in bits per second.
   */
  public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1_000_000;

  /** Default maximum weight for the sliding window. */
  public static final int DEFAULT_SLIDING_WINDOW_MAX_WEIGHT = 2000;

  /** Index for the Wifi group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_WIFI = 0;
  /** Index for the 2G group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_2G = 1;
  /** Index for the 3G group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_3G = 2;
  /** Index for the 4G group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_4G = 3;
  /** Index for the 5G-NSA group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_5G_NSA = 4;
  /** Index for the 5G-SA group index in {@link #DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS}. */
  private static final int COUNTRY_GROUP_INDEX_5G_SA = 5;

  @Nullable private static DefaultBandwidthMeter singletonInstance;

  /** Builder for a bandwidth meter. */
  public static final class Builder {

    @Nullable private final Context context;

    private Map<Integer, Long> initialBitrateEstimates;
    private int slidingWindowMaxWeight;
    private Clock clock;
    private boolean resetOnNetworkTypeChange;

    /**
     * Creates a builder with default parameters and without listener.
     *
     * @param context A context.
     */
    public Builder(Context context) {
      // Handling of null is for backward compatibility only.
      this.context = context == null ? null : context.getApplicationContext();
      initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.getCountryCode(context));
      slidingWindowMaxWeight = DEFAULT_SLIDING_WINDOW_MAX_WEIGHT;
      clock = Clock.DEFAULT;
      resetOnNetworkTypeChange = true;
    }

    /**
     * Sets the maximum weight for the sliding window.
     *
     * @param slidingWindowMaxWeight The maximum weight for the sliding window.
     * @return This builder.
     */
    public Builder setSlidingWindowMaxWeight(int slidingWindowMaxWeight) {
      this.slidingWindowMaxWeight = slidingWindowMaxWeight;
      return this;
    }

    /**
     * Sets the initial bitrate estimate in bits per second that should be assumed when a bandwidth
     * estimate is unavailable.
     *
     * @param initialBitrateEstimate The initial bitrate estimate in bits per second.
     * @return This builder.
     */
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
    public Builder setInitialBitrateEstimate(String countryCode) {
      initialBitrateEstimates =
          getInitialBitrateEstimatesForCountry(Ascii.toUpperCase(countryCode));
      return this;
    }

    /**
     * Sets the clock used to estimate bandwidth from data transfers. Should only be set for testing
     * purposes.
     *
     * @param clock The clock used to estimate bandwidth from data transfers.
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets whether to reset if the network type changes. The default value is {@code true}.
     *
     * @param resetOnNetworkTypeChange Whether to reset if the network type changes.
     * @return This builder.
     */
    public Builder setResetOnNetworkTypeChange(boolean resetOnNetworkTypeChange) {
      this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
      return this;
    }

    /**
     * Builds the bandwidth meter.
     *
     * @return A bandwidth meter with the configured properties.
     */
    public DefaultBandwidthMeter build() {
      return new DefaultBandwidthMeter(
          context,
          initialBitrateEstimates,
          slidingWindowMaxWeight,
          clock,
          resetOnNetworkTypeChange);
    }

    private static Map<Integer, Long> getInitialBitrateEstimatesForCountry(String countryCode) {
      List<Integer> groupIndices = getCountryGroupIndices(countryCode);
      Map<Integer, Long> result = new HashMap<>(/* initialCapacity= */ 8);
      result.put(C.NETWORK_TYPE_UNKNOWN, DEFAULT_INITIAL_BITRATE_ESTIMATE);
      result.put(
          C.NETWORK_TYPE_WIFI,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices.get(COUNTRY_GROUP_INDEX_WIFI)));
      result.put(
          C.NETWORK_TYPE_2G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_2G.get(groupIndices.get(COUNTRY_GROUP_INDEX_2G)));
      result.put(
          C.NETWORK_TYPE_3G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_3G.get(groupIndices.get(COUNTRY_GROUP_INDEX_3G)));
      result.put(
          C.NETWORK_TYPE_4G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_4G.get(groupIndices.get(COUNTRY_GROUP_INDEX_4G)));
      result.put(
          C.NETWORK_TYPE_5G_NSA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA.get(
              groupIndices.get(COUNTRY_GROUP_INDEX_5G_NSA)));
      result.put(
          C.NETWORK_TYPE_5G_SA,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_SA.get(groupIndices.get(COUNTRY_GROUP_INDEX_5G_SA)));
      // Assume default Wifi speed for Ethernet to prevent using the slower fallback.
      result.put(
          C.NETWORK_TYPE_ETHERNET,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices.get(COUNTRY_GROUP_INDEX_WIFI)));
      return result;
    }

    private static ImmutableList<Integer> getCountryGroupIndices(String countryCode) {
      ImmutableList<Integer> groupIndices = DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS.get(countryCode);
      // Assume median group if not found.
      return groupIndices.isEmpty() ? ImmutableList.of(2, 2, 2, 2, 2, 2) : groupIndices;
    }
  }

  /**
   * Returns a singleton instance of a {@link DefaultBandwidthMeter} with default configuration.
   *
   * @param context A {@link Context}.
   * @return The singleton instance.
   */
  public static synchronized DefaultBandwidthMeter getSingletonInstance(Context context) {
    if (singletonInstance == null) {
      singletonInstance = new DefaultBandwidthMeter.Builder(context).build();
    }
    return singletonInstance;
  }

  private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
  private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 512 * 1024;

  private final ImmutableMap<Integer, Long> initialBitrateEstimates;
  private final EventDispatcher eventDispatcher;
  private final SlidingPercentile slidingPercentile;
  private final Clock clock;
  private final boolean resetOnNetworkTypeChange;

  private int streamCount;
  private long sampleStartTimeMs;
  private long sampleBytesTransferred;

  @C.NetworkType private int networkType;
  private long totalElapsedTimeMs;
  private long totalBytesTransferred;
  private long bitrateEstimate;
  private long lastReportedBitrateEstimate;

  private boolean networkTypeOverrideSet;
  @C.NetworkType private int networkTypeOverride;

  /** @deprecated Use {@link Builder} instead. */
  @Deprecated
  public DefaultBandwidthMeter() {
    this(
        /* context= */ null,
        /* initialBitrateEstimates= */ ImmutableMap.of(),
        DEFAULT_SLIDING_WINDOW_MAX_WEIGHT,
        Clock.DEFAULT,
        /* resetOnNetworkTypeChange= */ false);
  }

  private DefaultBandwidthMeter(
      @Nullable Context context,
      Map<Integer, Long> initialBitrateEstimates,
      int maxWeight,
      Clock clock,
      boolean resetOnNetworkTypeChange) {
    this.initialBitrateEstimates = ImmutableMap.copyOf(initialBitrateEstimates);
    this.eventDispatcher = new EventDispatcher();
    this.slidingPercentile = new SlidingPercentile(maxWeight);
    this.clock = clock;
    this.resetOnNetworkTypeChange = resetOnNetworkTypeChange;
    if (context != null) {
      NetworkTypeObserver networkTypeObserver = NetworkTypeObserver.getInstance(context);
      networkType = networkTypeObserver.getNetworkType();
      bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
      networkTypeObserver.register(/* listener= */ this::onNetworkTypeChanged);
    } else {
      networkType = C.NETWORK_TYPE_UNKNOWN;
      bitrateEstimate = getInitialBitrateEstimateForNetworkType(C.NETWORK_TYPE_UNKNOWN);
    }
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
    return bitrateEstimate;
  }

  @Override
  public TransferListener getTransferListener() {
    return this;
  }

  @Override
  public void addEventListener(Handler eventHandler, EventListener eventListener) {
    Assertions.checkNotNull(eventHandler);
    Assertions.checkNotNull(eventListener);
    eventDispatcher.addListener(eventHandler, eventListener);
  }

  @Override
  public void removeEventListener(EventListener eventListener) {
    eventDispatcher.removeListener(eventListener);
  }

  @Override
  public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    // Do nothing.
  }

  @Override
  public synchronized void onTransferStart(
      DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    if (streamCount == 0) {
      sampleStartTimeMs = clock.elapsedRealtime();
    }
    streamCount++;
  }

  @Override
  public synchronized void onBytesTransferred(
      DataSource source, DataSpec dataSpec, boolean isNetwork, int bytes) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    sampleBytesTransferred += bytes;
  }

  @Override
  public synchronized void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    if (!isTransferAtFullNetworkSpeed(dataSpec, isNetwork)) {
      return;
    }
    Assertions.checkState(streamCount > 0);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
    totalElapsedTimeMs += sampleElapsedTimeMs;
    totalBytesTransferred += sampleBytesTransferred;
    if (sampleElapsedTimeMs > 0) {
      float bitsPerSecond = (sampleBytesTransferred * 8000f) / sampleElapsedTimeMs;
      slidingPercentile.addSample((int) Math.sqrt(sampleBytesTransferred), bitsPerSecond);
      if (totalElapsedTimeMs >= ELAPSED_MILLIS_FOR_ESTIMATE
          || totalBytesTransferred >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
        bitrateEstimate = (long) slidingPercentile.getPercentile(0.5f);
      }
      maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);
      sampleStartTimeMs = nowMs;
      sampleBytesTransferred = 0;
    } // Else any sample bytes transferred will be carried forward into the next sample.
    streamCount--;
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
    this.bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    long nowMs = clock.elapsedRealtime();
    int sampleElapsedTimeMs = streamCount > 0 ? (int) (nowMs - sampleStartTimeMs) : 0;
    maybeNotifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);

    // Reset the remainder of the state.
    sampleStartTimeMs = nowMs;
    sampleBytesTransferred = 0;
    totalBytesTransferred = 0;
    totalElapsedTimeMs = 0;
    slidingPercentile.reset();
  }

  private void maybeNotifyBandwidthSample(
      int elapsedMs, long bytesTransferred, long bitrateEstimate) {
    if (elapsedMs == 0 && bytesTransferred == 0 && bitrateEstimate == lastReportedBitrateEstimate) {
      return;
    }
    lastReportedBitrateEstimate = bitrateEstimate;
    eventDispatcher.bandwidthSample(elapsedMs, bytesTransferred, bitrateEstimate);
  }

  private long getInitialBitrateEstimateForNetworkType(@C.NetworkType int networkType) {
    Long initialBitrateEstimate = initialBitrateEstimates.get(networkType);
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

  private static ImmutableListMultimap<String, Integer>
      createInitialBitrateCountryGroupAssignment() {
    return ImmutableListMultimap.<String, Integer>builder()
        .putAll("AD", 1, 2, 0, 0, 2, 2)
        .putAll("AE", 1, 4, 4, 4, 2, 2)
        .putAll("AF", 4, 4, 3, 4, 2, 2)
        .putAll("AG", 4, 2, 1, 4, 2, 2)
        .putAll("AI", 1, 2, 2, 2, 2, 2)
        .putAll("AL", 1, 1, 1, 1, 2, 2)
        .putAll("AM", 2, 2, 1, 3, 2, 2)
        .putAll("AO", 3, 4, 3, 1, 2, 2)
        .putAll("AR", 2, 4, 2, 1, 2, 2)
        .putAll("AS", 2, 2, 3, 3, 2, 2)
        .putAll("AT", 0, 1, 0, 0, 0, 2)
        .putAll("AU", 0, 2, 0, 1, 1, 2)
        .putAll("AW", 1, 2, 0, 4, 2, 2)
        .putAll("AX", 0, 2, 2, 2, 2, 2)
        .putAll("AZ", 3, 3, 3, 4, 4, 2)
        .putAll("BA", 1, 1, 0, 1, 2, 2)
        .putAll("BB", 0, 2, 0, 0, 2, 2)
        .putAll("BD", 2, 0, 3, 3, 2, 2)
        .putAll("BE", 0, 0, 2, 3, 2, 2)
        .putAll("BF", 4, 4, 4, 2, 2, 2)
        .putAll("BG", 0, 1, 0, 0, 2, 2)
        .putAll("BH", 1, 0, 2, 4, 2, 2)
        .putAll("BI", 4, 4, 4, 4, 2, 2)
        .putAll("BJ", 4, 4, 4, 4, 2, 2)
        .putAll("BL", 1, 2, 2, 2, 2, 2)
        .putAll("BM", 0, 2, 0, 0, 2, 2)
        .putAll("BN", 3, 2, 1, 0, 2, 2)
        .putAll("BO", 1, 2, 4, 2, 2, 2)
        .putAll("BQ", 1, 2, 1, 2, 2, 2)
        .putAll("BR", 2, 4, 3, 2, 2, 2)
        .putAll("BS", 2, 2, 1, 3, 2, 2)
        .putAll("BT", 3, 0, 3, 2, 2, 2)
        .putAll("BW", 3, 4, 1, 1, 2, 2)
        .putAll("BY", 1, 1, 1, 2, 2, 2)
        .putAll("BZ", 2, 2, 2, 2, 2, 2)
        .putAll("CA", 0, 3, 1, 2, 4, 2)
        .putAll("CD", 4, 2, 2, 1, 2, 2)
        .putAll("CF", 4, 2, 3, 2, 2, 2)
        .putAll("CG", 3, 4, 2, 2, 2, 2)
        .putAll("CH", 0, 0, 0, 0, 1, 2)
        .putAll("CI", 3, 3, 3, 3, 2, 2)
        .putAll("CK", 2, 2, 3, 0, 2, 2)
        .putAll("CL", 1, 1, 2, 2, 2, 2)
        .putAll("CM", 3, 4, 3, 2, 2, 2)
        .putAll("CN", 2, 2, 2, 1, 3, 2)
        .putAll("CO", 2, 3, 4, 2, 2, 2)
        .putAll("CR", 2, 3, 4, 4, 2, 2)
        .putAll("CU", 4, 4, 2, 2, 2, 2)
        .putAll("CV", 2, 3, 1, 0, 2, 2)
        .putAll("CW", 1, 2, 0, 0, 2, 2)
        .putAll("CY", 1, 1, 0, 0, 2, 2)
        .putAll("CZ", 0, 1, 0, 0, 1, 2)
        .putAll("DE", 0, 0, 1, 1, 0, 2)
        .putAll("DJ", 4, 0, 4, 4, 2, 2)
        .putAll("DK", 0, 0, 1, 0, 0, 2)
        .putAll("DM", 1, 2, 2, 2, 2, 2)
        .putAll("DO", 3, 4, 4, 4, 2, 2)
        .putAll("DZ", 3, 3, 4, 4, 2, 4)
        .putAll("EC", 2, 4, 3, 1, 2, 2)
        .putAll("EE", 0, 1, 0, 0, 2, 2)
        .putAll("EG", 3, 4, 3, 3, 2, 2)
        .putAll("EH", 2, 2, 2, 2, 2, 2)
        .putAll("ER", 4, 2, 2, 2, 2, 2)
        .putAll("ES", 0, 1, 1, 1, 2, 2)
        .putAll("ET", 4, 4, 4, 1, 2, 2)
        .putAll("FI", 0, 0, 0, 0, 0, 2)
        .putAll("FJ", 3, 0, 2, 3, 2, 2)
        .putAll("FK", 4, 2, 2, 2, 2, 2)
        .putAll("FM", 3, 2, 4, 4, 2, 2)
        .putAll("FO", 1, 2, 0, 1, 2, 2)
        .putAll("FR", 1, 1, 2, 0, 1, 2)
        .putAll("GA", 3, 4, 1, 1, 2, 2)
        .putAll("GB", 0, 0, 1, 1, 1, 2)
        .putAll("GD", 1, 2, 2, 2, 2, 2)
        .putAll("GE", 1, 1, 1, 2, 2, 2)
        .putAll("GF", 2, 2, 2, 3, 2, 2)
        .putAll("GG", 1, 2, 0, 0, 2, 2)
        .putAll("GH", 3, 1, 3, 2, 2, 2)
        .putAll("GI", 0, 2, 0, 0, 2, 2)
        .putAll("GL", 1, 2, 0, 0, 2, 2)
        .putAll("GM", 4, 3, 2, 4, 2, 2)
        .putAll("GN", 4, 3, 4, 2, 2, 2)
        .putAll("GP", 2, 1, 2, 3, 2, 2)
        .putAll("GQ", 4, 2, 2, 4, 2, 2)
        .putAll("GR", 1, 2, 0, 0, 2, 2)
        .putAll("GT", 3, 2, 3, 1, 2, 2)
        .putAll("GU", 1, 2, 3, 4, 2, 2)
        .putAll("GW", 4, 4, 4, 4, 2, 2)
        .putAll("GY", 3, 3, 3, 4, 2, 2)
        .putAll("HK", 0, 1, 2, 3, 2, 0)
        .putAll("HN", 3, 1, 3, 3, 2, 2)
        .putAll("HR", 1, 1, 0, 0, 3, 2)
        .putAll("HT", 4, 4, 4, 4, 2, 2)
        .putAll("HU", 0, 0, 0, 0, 0, 2)
        .putAll("ID", 3, 2, 3, 3, 2, 2)
        .putAll("IE", 0, 0, 1, 1, 3, 2)
        .putAll("IL", 1, 0, 2, 3, 4, 2)
        .putAll("IM", 0, 2, 0, 1, 2, 2)
        .putAll("IN", 2, 1, 3, 3, 2, 2)
        .putAll("IO", 4, 2, 2, 4, 2, 2)
        .putAll("IQ", 3, 3, 4, 4, 2, 2)
        .putAll("IR", 3, 2, 3, 2, 2, 2)
        .putAll("IS", 0, 2, 0, 0, 2, 2)
        .putAll("IT", 0, 4, 0, 1, 2, 2)
        .putAll("JE", 2, 2, 1, 2, 2, 2)
        .putAll("JM", 3, 3, 4, 4, 2, 2)
        .putAll("JO", 2, 2, 1, 1, 2, 2)
        .putAll("JP", 0, 0, 0, 0, 2, 1)
        .putAll("KE", 3, 4, 2, 2, 2, 2)
        .putAll("KG", 2, 0, 1, 1, 2, 2)
        .putAll("KH", 1, 0, 4, 3, 2, 2)
        .putAll("KI", 4, 2, 4, 3, 2, 2)
        .putAll("KM", 4, 3, 2, 3, 2, 2)
        .putAll("KN", 1, 2, 2, 2, 2, 2)
        .putAll("KP", 4, 2, 2, 2, 2, 2)
        .putAll("KR", 0, 0, 1, 3, 1, 2)
        .putAll("KW", 1, 3, 1, 1, 1, 2)
        .putAll("KY", 1, 2, 0, 2, 2, 2)
        .putAll("KZ", 2, 2, 2, 3, 2, 2)
        .putAll("LA", 1, 2, 1, 1, 2, 2)
        .putAll("LB", 3, 2, 0, 0, 2, 2)
        .putAll("LC", 1, 2, 0, 0, 2, 2)
        .putAll("LI", 0, 2, 2, 2, 2, 2)
        .putAll("LK", 2, 0, 2, 3, 2, 2)
        .putAll("LR", 3, 4, 4, 3, 2, 2)
        .putAll("LS", 3, 3, 2, 3, 2, 2)
        .putAll("LT", 0, 0, 0, 0, 2, 2)
        .putAll("LU", 1, 0, 1, 1, 2, 2)
        .putAll("LV", 0, 0, 0, 0, 2, 2)
        .putAll("LY", 4, 2, 4, 3, 2, 2)
        .putAll("MA", 3, 2, 2, 1, 2, 2)
        .putAll("MC", 0, 2, 0, 0, 2, 2)
        .putAll("MD", 1, 2, 0, 0, 2, 2)
        .putAll("ME", 1, 2, 0, 1, 2, 2)
        .putAll("MF", 2, 2, 1, 1, 2, 2)
        .putAll("MG", 3, 4, 2, 2, 2, 2)
        .putAll("MH", 4, 2, 2, 4, 2, 2)
        .putAll("MK", 1, 1, 0, 0, 2, 2)
        .putAll("ML", 4, 4, 2, 2, 2, 2)
        .putAll("MM", 2, 3, 3, 3, 2, 2)
        .putAll("MN", 2, 4, 2, 2, 2, 2)
        .putAll("MO", 0, 2, 4, 4, 2, 2)
        .putAll("MP", 0, 2, 2, 2, 2, 2)
        .putAll("MQ", 2, 2, 2, 3, 2, 2)
        .putAll("MR", 3, 0, 4, 3, 2, 2)
        .putAll("MS", 1, 2, 2, 2, 2, 2)
        .putAll("MT", 0, 2, 0, 0, 2, 2)
        .putAll("MU", 2, 1, 1, 2, 2, 2)
        .putAll("MV", 4, 3, 2, 4, 2, 2)
        .putAll("MW", 4, 2, 1, 0, 2, 2)
        .putAll("MX", 2, 4, 4, 4, 4, 2)
        .putAll("MY", 1, 0, 3, 2, 2, 2)
        .putAll("MZ", 3, 3, 2, 1, 2, 2)
        .putAll("NA", 4, 3, 3, 2, 2, 2)
        .putAll("NC", 3, 0, 4, 4, 2, 2)
        .putAll("NE", 4, 4, 4, 4, 2, 2)
        .putAll("NF", 2, 2, 2, 2, 2, 2)
        .putAll("NG", 3, 3, 2, 3, 2, 2)
        .putAll("NI", 2, 1, 4, 4, 2, 2)
        .putAll("NL", 0, 2, 3, 2, 0, 2)
        .putAll("NO", 0, 1, 2, 0, 0, 2)
        .putAll("NP", 2, 0, 4, 2, 2, 2)
        .putAll("NR", 3, 2, 3, 1, 2, 2)
        .putAll("NU", 4, 2, 2, 2, 2, 2)
        .putAll("NZ", 0, 2, 1, 2, 4, 2)
        .putAll("OM", 2, 2, 1, 3, 3, 2)
        .putAll("PA", 1, 3, 3, 3, 2, 2)
        .putAll("PE", 2, 3, 4, 4, 2, 2)
        .putAll("PF", 2, 2, 2, 1, 2, 2)
        .putAll("PG", 4, 4, 3, 2, 2, 2)
        .putAll("PH", 2, 1, 3, 3, 3, 2)
        .putAll("PK", 3, 2, 3, 3, 2, 2)
        .putAll("PL", 1, 0, 1, 2, 3, 2)
        .putAll("PM", 0, 2, 2, 2, 2, 2)
        .putAll("PR", 2, 1, 2, 2, 4, 3)
        .putAll("PS", 3, 3, 2, 2, 2, 2)
        .putAll("PT", 0, 1, 1, 0, 2, 2)
        .putAll("PW", 1, 2, 4, 1, 2, 2)
        .putAll("PY", 2, 0, 3, 2, 2, 2)
        .putAll("QA", 2, 3, 1, 2, 3, 2)
        .putAll("RE", 1, 0, 2, 2, 2, 2)
        .putAll("RO", 0, 1, 0, 1, 0, 2)
        .putAll("RS", 1, 2, 0, 0, 2, 2)
        .putAll("RU", 0, 1, 0, 1, 4, 2)
        .putAll("RW", 3, 3, 3, 1, 2, 2)
        .putAll("SA", 2, 2, 2, 1, 1, 2)
        .putAll("SB", 4, 2, 3, 2, 2, 2)
        .putAll("SC", 4, 2, 1, 3, 2, 2)
        .putAll("SD", 4, 4, 4, 4, 2, 2)
        .putAll("SE", 0, 0, 0, 0, 0, 2)
        .putAll("SG", 1, 0, 1, 2, 3, 2)
        .putAll("SH", 4, 2, 2, 2, 2, 2)
        .putAll("SI", 0, 0, 0, 0, 2, 2)
        .putAll("SJ", 2, 2, 2, 2, 2, 2)
        .putAll("SK", 0, 1, 0, 0, 2, 2)
        .putAll("SL", 4, 3, 4, 0, 2, 2)
        .putAll("SM", 0, 2, 2, 2, 2, 2)
        .putAll("SN", 4, 4, 4, 4, 2, 2)
        .putAll("SO", 3, 3, 3, 4, 2, 2)
        .putAll("SR", 3, 2, 2, 2, 2, 2)
        .putAll("SS", 4, 4, 3, 3, 2, 2)
        .putAll("ST", 2, 2, 1, 2, 2, 2)
        .putAll("SV", 2, 1, 4, 3, 2, 2)
        .putAll("SX", 2, 2, 1, 0, 2, 2)
        .putAll("SY", 4, 3, 3, 2, 2, 2)
        .putAll("SZ", 3, 3, 2, 4, 2, 2)
        .putAll("TC", 2, 2, 2, 0, 2, 2)
        .putAll("TD", 4, 3, 4, 4, 2, 2)
        .putAll("TG", 3, 2, 2, 4, 2, 2)
        .putAll("TH", 0, 3, 2, 3, 2, 2)
        .putAll("TJ", 4, 4, 4, 4, 2, 2)
        .putAll("TL", 4, 0, 4, 4, 2, 2)
        .putAll("TM", 4, 2, 4, 3, 2, 2)
        .putAll("TN", 2, 1, 1, 2, 2, 2)
        .putAll("TO", 3, 3, 4, 3, 2, 2)
        .putAll("TR", 1, 2, 1, 1, 2, 2)
        .putAll("TT", 1, 4, 0, 1, 2, 2)
        .putAll("TV", 3, 2, 2, 4, 2, 2)
        .putAll("TW", 0, 0, 0, 0, 1, 0)
        .putAll("TZ", 3, 3, 3, 2, 2, 2)
        .putAll("UA", 0, 3, 1, 1, 2, 2)
        .putAll("UG", 3, 2, 3, 3, 2, 2)
        .putAll("US", 1, 1, 2, 2, 4, 2)
        .putAll("UY", 2, 2, 1, 1, 2, 2)
        .putAll("UZ", 2, 1, 3, 4, 2, 2)
        .putAll("VC", 1, 2, 2, 2, 2, 2)
        .putAll("VE", 4, 4, 4, 4, 2, 2)
        .putAll("VG", 2, 2, 1, 1, 2, 2)
        .putAll("VI", 1, 2, 1, 2, 2, 2)
        .putAll("VN", 0, 1, 3, 4, 2, 2)
        .putAll("VU", 4, 0, 3, 1, 2, 2)
        .putAll("WF", 4, 2, 2, 4, 2, 2)
        .putAll("WS", 3, 1, 3, 1, 2, 2)
        .putAll("XK", 0, 1, 1, 0, 2, 2)
        .putAll("YE", 4, 4, 4, 3, 2, 2)
        .putAll("YT", 4, 2, 2, 3, 2, 2)
        .putAll("ZA", 3, 3, 2, 1, 2, 2)
        .putAll("ZM", 3, 2, 3, 3, 2, 2)
        .putAll("ZW", 3, 2, 4, 3, 2, 2)
        .build();
  }
}
