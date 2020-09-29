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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BandwidthMeter.EventListener.EventDispatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.SlidingPercentile;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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
   * each country is a list for [Wifi, 2G, 3G, 4G, 5G_NSA].
   */
  public static final ImmutableListMultimap<String, Integer>
      DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS = createInitialBitrateCountryGroupAssignment();

  /** Default initial Wifi bitrate estimate in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI =
      ImmutableList.of(6_100_000L, 3_800_000L, 2_100_000L, 1_300_000L, 590_000L);

  /** Default initial 2G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_2G =
      ImmutableList.of(218_000L, 159_000L, 145_000L, 130_000L, 112_000L);

  /** Default initial 3G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_3G =
      ImmutableList.of(2_200_000L, 1_300_000L, 930_000L, 730_000L, 530_000L);

  /** Default initial 4G bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_4G =
      ImmutableList.of(4_800_000L, 2_700_000L, 1_800_000L, 1_200_000L, 630_000L);

  /** Default initial 5G-NSA bitrate estimates in bits per second. */
  public static final ImmutableList<Long> DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA =
      ImmutableList.of(12_000_000L, 8_800_000L, 5_900_000L, 3_500_000L, 1_800_000L);

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
          getInitialBitrateEstimatesForCountry(Util.toUpperInvariant(countryCode));
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
      Map<Integer, Long> result = new HashMap<>(/* initialCapacity= */ 6);
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
          C.NETWORK_TYPE_5G,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_5G_NSA.get(
              groupIndices.get(COUNTRY_GROUP_INDEX_5G_NSA)));
      // Assume default Wifi speed for Ethernet to prevent using the slower fallback.
      result.put(
          C.NETWORK_TYPE_ETHERNET,
          DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI.get(groupIndices.get(COUNTRY_GROUP_INDEX_WIFI)));
      return result;
    }

    private static ImmutableList<Integer> getCountryGroupIndices(String countryCode) {
      ImmutableList<Integer> groupIndices = DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS.get(countryCode);
      // Assume median group if not found.
      return groupIndices.isEmpty() ? ImmutableList.of(2, 2, 2, 2, 2) : groupIndices;
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

  @Nullable private final Context context;
  private final ImmutableMap<Integer, Long> initialBitrateEstimates;
  private final EventDispatcher eventDispatcher;
  private final SlidingPercentile slidingPercentile;
  private final Clock clock;

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
    this.context = context == null ? null : context.getApplicationContext();
    this.initialBitrateEstimates = ImmutableMap.copyOf(initialBitrateEstimates);
    this.eventDispatcher = new EventDispatcher();
    this.slidingPercentile = new SlidingPercentile(maxWeight);
    this.clock = clock;
    // Set the initial network type and bitrate estimate
    networkType = context == null ? C.NETWORK_TYPE_UNKNOWN : Util.getNetworkType(context);
    bitrateEstimate = getInitialBitrateEstimateForNetworkType(networkType);
    // Register to receive connectivity actions if possible.
    if (context != null && resetOnNetworkTypeChange) {
      ConnectivityActionReceiver connectivityActionReceiver =
          ConnectivityActionReceiver.getInstance(context);
      connectivityActionReceiver.register(/* bandwidthMeter= */ this);
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
    onConnectivityAction();
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

  private synchronized void onConnectivityAction() {
    int networkType =
        networkTypeOverrideSet
            ? networkTypeOverride
            : (context == null ? C.NETWORK_TYPE_UNKNOWN : Util.getNetworkType(context));
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

  /*
   * Note: This class only holds a weak reference to DefaultBandwidthMeter instances. It should not
   * be made non-static, since doing so adds a strong reference (i.e. DefaultBandwidthMeter.this).
   */
  private static class ConnectivityActionReceiver extends BroadcastReceiver {

    private static @MonotonicNonNull ConnectivityActionReceiver staticInstance;

    private final Handler mainHandler;
    private final ArrayList<WeakReference<DefaultBandwidthMeter>> bandwidthMeters;

    public static synchronized ConnectivityActionReceiver getInstance(Context context) {
      if (staticInstance == null) {
        staticInstance = new ConnectivityActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(staticInstance, filter);
      }
      return staticInstance;
    }

    private ConnectivityActionReceiver() {
      mainHandler = new Handler(Looper.getMainLooper());
      bandwidthMeters = new ArrayList<>();
    }

    public synchronized void register(DefaultBandwidthMeter bandwidthMeter) {
      removeClearedReferences();
      bandwidthMeters.add(new WeakReference<>(bandwidthMeter));
      // Simulate an initial update on the main thread (like the sticky broadcast we'd receive if
      // we were to register a separate broadcast receiver for each bandwidth meter).
      mainHandler.post(() -> updateBandwidthMeter(bandwidthMeter));
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
      if (isInitialStickyBroadcast()) {
        return;
      }
      removeClearedReferences();
      for (int i = 0; i < bandwidthMeters.size(); i++) {
        WeakReference<DefaultBandwidthMeter> bandwidthMeterReference = bandwidthMeters.get(i);
        DefaultBandwidthMeter bandwidthMeter = bandwidthMeterReference.get();
        if (bandwidthMeter != null) {
          updateBandwidthMeter(bandwidthMeter);
        }
      }
    }

    private void updateBandwidthMeter(DefaultBandwidthMeter bandwidthMeter) {
      bandwidthMeter.onConnectivityAction();
    }

    private void removeClearedReferences() {
      for (int i = bandwidthMeters.size() - 1; i >= 0; i--) {
        WeakReference<DefaultBandwidthMeter> bandwidthMeterReference = bandwidthMeters.get(i);
        DefaultBandwidthMeter bandwidthMeter = bandwidthMeterReference.get();
        if (bandwidthMeter == null) {
          bandwidthMeters.remove(i);
        }
      }
    }
  }

  private static ImmutableListMultimap<String, Integer>
      createInitialBitrateCountryGroupAssignment() {
    ImmutableListMultimap.Builder<String, Integer> countryGroupAssignment =
        ImmutableListMultimap.builder();
    countryGroupAssignment.putAll("AD", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("AE", 1, 4, 4, 4, 1);
    countryGroupAssignment.putAll("AF", 4, 4, 3, 4, 2);
    countryGroupAssignment.putAll("AG", 2, 2, 1, 1, 2);
    countryGroupAssignment.putAll("AI", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("AL", 1, 1, 0, 1, 2);
    countryGroupAssignment.putAll("AM", 2, 2, 1, 2, 2);
    countryGroupAssignment.putAll("AO", 3, 4, 4, 2, 2);
    countryGroupAssignment.putAll("AR", 2, 4, 2, 2, 2);
    countryGroupAssignment.putAll("AS", 2, 2, 4, 3, 2);
    countryGroupAssignment.putAll("AT", 0, 3, 0, 0, 2);
    countryGroupAssignment.putAll("AU", 0, 2, 0, 1, 1);
    countryGroupAssignment.putAll("AW", 1, 2, 0, 4, 2);
    countryGroupAssignment.putAll("AX", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("AZ", 3, 3, 3, 4, 2);
    countryGroupAssignment.putAll("BA", 1, 1, 0, 1, 2);
    countryGroupAssignment.putAll("BB", 0, 2, 0, 0, 2);
    countryGroupAssignment.putAll("BD", 2, 0, 3, 3, 2);
    countryGroupAssignment.putAll("BE", 0, 1, 2, 3, 2);
    countryGroupAssignment.putAll("BF", 4, 4, 4, 2, 2);
    countryGroupAssignment.putAll("BG", 0, 1, 0, 0, 2);
    countryGroupAssignment.putAll("BH", 1, 0, 2, 4, 2);
    countryGroupAssignment.putAll("BI", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("BJ", 4, 4, 3, 4, 2);
    countryGroupAssignment.putAll("BL", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("BM", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("BN", 4, 0, 1, 1, 2);
    countryGroupAssignment.putAll("BO", 2, 3, 3, 2, 2);
    countryGroupAssignment.putAll("BQ", 1, 2, 1, 2, 2);
    countryGroupAssignment.putAll("BR", 2, 4, 2, 1, 2);
    countryGroupAssignment.putAll("BS", 3, 2, 2, 3, 2);
    countryGroupAssignment.putAll("BT", 3, 0, 3, 2, 2);
    countryGroupAssignment.putAll("BW", 3, 4, 2, 2, 2);
    countryGroupAssignment.putAll("BY", 1, 0, 2, 1, 2);
    countryGroupAssignment.putAll("BZ", 2, 2, 2, 1, 2);
    countryGroupAssignment.putAll("CA", 0, 3, 1, 2, 3);
    countryGroupAssignment.putAll("CD", 4, 3, 2, 2, 2);
    countryGroupAssignment.putAll("CF", 4, 2, 2, 2, 2);
    countryGroupAssignment.putAll("CG", 3, 4, 1, 1, 2);
    countryGroupAssignment.putAll("CH", 0, 1, 0, 0, 0);
    countryGroupAssignment.putAll("CI", 3, 3, 3, 3, 2);
    countryGroupAssignment.putAll("CK", 3, 2, 1, 0, 2);
    countryGroupAssignment.putAll("CL", 1, 1, 2, 3, 2);
    countryGroupAssignment.putAll("CM", 3, 4, 3, 2, 2);
    countryGroupAssignment.putAll("CN", 2, 2, 2, 1, 3);
    countryGroupAssignment.putAll("CO", 2, 4, 3, 2, 2);
    countryGroupAssignment.putAll("CR", 2, 3, 4, 4, 2);
    countryGroupAssignment.putAll("CU", 4, 4, 2, 1, 2);
    countryGroupAssignment.putAll("CV", 2, 3, 3, 3, 2);
    countryGroupAssignment.putAll("CW", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("CY", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("CZ", 0, 1, 0, 0, 2);
    countryGroupAssignment.putAll("DE", 0, 1, 1, 2, 0);
    countryGroupAssignment.putAll("DJ", 4, 1, 4, 4, 2);
    countryGroupAssignment.putAll("DK", 0, 0, 1, 0, 2);
    countryGroupAssignment.putAll("DM", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("DO", 3, 4, 4, 4, 2);
    countryGroupAssignment.putAll("DZ", 3, 2, 4, 4, 2);
    countryGroupAssignment.putAll("EC", 2, 4, 3, 2, 2);
    countryGroupAssignment.putAll("EE", 0, 0, 0, 0, 2);
    countryGroupAssignment.putAll("EG", 3, 4, 2, 1, 2);
    countryGroupAssignment.putAll("EH", 2, 2, 2, 2, 2);
    countryGroupAssignment.putAll("ER", 4, 2, 2, 2, 2);
    countryGroupAssignment.putAll("ES", 0, 1, 2, 1, 2);
    countryGroupAssignment.putAll("ET", 4, 4, 4, 1, 2);
    countryGroupAssignment.putAll("FI", 0, 0, 1, 0, 0);
    countryGroupAssignment.putAll("FJ", 3, 0, 3, 3, 2);
    countryGroupAssignment.putAll("FK", 2, 2, 2, 2, 2);
    countryGroupAssignment.putAll("FM", 4, 2, 4, 3, 2);
    countryGroupAssignment.putAll("FO", 0, 2, 0, 0, 2);
    countryGroupAssignment.putAll("FR", 1, 0, 2, 1, 2);
    countryGroupAssignment.putAll("GA", 3, 3, 1, 0, 2);
    countryGroupAssignment.putAll("GB", 0, 0, 1, 2, 2);
    countryGroupAssignment.putAll("GD", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("GE", 1, 0, 1, 3, 2);
    countryGroupAssignment.putAll("GF", 2, 2, 2, 4, 2);
    countryGroupAssignment.putAll("GG", 0, 2, 0, 0, 2);
    countryGroupAssignment.putAll("GH", 3, 2, 3, 2, 2);
    countryGroupAssignment.putAll("GI", 0, 2, 0, 0, 2);
    countryGroupAssignment.putAll("GL", 1, 2, 2, 1, 2);
    countryGroupAssignment.putAll("GM", 4, 3, 2, 4, 2);
    countryGroupAssignment.putAll("GN", 4, 3, 4, 2, 2);
    countryGroupAssignment.putAll("GP", 2, 2, 3, 4, 2);
    countryGroupAssignment.putAll("GQ", 4, 2, 3, 4, 2);
    countryGroupAssignment.putAll("GR", 1, 1, 0, 1, 2);
    countryGroupAssignment.putAll("GT", 3, 2, 3, 2, 2);
    countryGroupAssignment.putAll("GU", 1, 2, 4, 4, 2);
    countryGroupAssignment.putAll("GW", 3, 4, 4, 3, 2);
    countryGroupAssignment.putAll("GY", 3, 3, 1, 0, 2);
    countryGroupAssignment.putAll("HK", 0, 2, 3, 4, 2);
    countryGroupAssignment.putAll("HN", 3, 0, 3, 3, 2);
    countryGroupAssignment.putAll("HR", 1, 1, 0, 1, 2);
    countryGroupAssignment.putAll("HT", 4, 3, 4, 4, 2);
    countryGroupAssignment.putAll("HU", 0, 1, 0, 0, 2);
    countryGroupAssignment.putAll("ID", 3, 2, 2, 3, 2);
    countryGroupAssignment.putAll("IE", 0, 0, 1, 1, 2);
    countryGroupAssignment.putAll("IL", 1, 0, 2, 3, 2);
    countryGroupAssignment.putAll("IM", 0, 2, 0, 1, 2);
    countryGroupAssignment.putAll("IN", 2, 1, 3, 3, 2);
    countryGroupAssignment.putAll("IO", 4, 2, 2, 4, 2);
    countryGroupAssignment.putAll("IQ", 3, 2, 4, 3, 2);
    countryGroupAssignment.putAll("IR", 4, 2, 3, 4, 2);
    countryGroupAssignment.putAll("IS", 0, 2, 0, 0, 2);
    countryGroupAssignment.putAll("IT", 0, 0, 1, 1, 2);
    countryGroupAssignment.putAll("JE", 2, 2, 0, 2, 2);
    countryGroupAssignment.putAll("JM", 3, 3, 4, 4, 2);
    countryGroupAssignment.putAll("JO", 1, 2, 1, 1, 2);
    countryGroupAssignment.putAll("JP", 0, 2, 0, 1, 3);
    countryGroupAssignment.putAll("KE", 3, 4, 2, 2, 2);
    countryGroupAssignment.putAll("KG", 1, 0, 2, 2, 2);
    countryGroupAssignment.putAll("KH", 2, 0, 4, 3, 2);
    countryGroupAssignment.putAll("KI", 4, 2, 3, 1, 2);
    countryGroupAssignment.putAll("KM", 4, 2, 2, 3, 2);
    countryGroupAssignment.putAll("KN", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("KP", 4, 2, 2, 2, 2);
    countryGroupAssignment.putAll("KR", 0, 2, 1, 1, 1);
    countryGroupAssignment.putAll("KW", 2, 3, 1, 1, 1);
    countryGroupAssignment.putAll("KY", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("KZ", 1, 2, 2, 3, 2);
    countryGroupAssignment.putAll("LA", 2, 2, 1, 1, 2);
    countryGroupAssignment.putAll("LB", 3, 2, 0, 0, 2);
    countryGroupAssignment.putAll("LC", 1, 1, 0, 0, 2);
    countryGroupAssignment.putAll("LI", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("LK", 2, 0, 2, 3, 2);
    countryGroupAssignment.putAll("LR", 3, 4, 3, 2, 2);
    countryGroupAssignment.putAll("LS", 3, 3, 2, 3, 2);
    countryGroupAssignment.putAll("LT", 0, 0, 0, 0, 2);
    countryGroupAssignment.putAll("LU", 0, 0, 0, 0, 2);
    countryGroupAssignment.putAll("LV", 0, 0, 0, 0, 2);
    countryGroupAssignment.putAll("LY", 4, 2, 4, 3, 2);
    countryGroupAssignment.putAll("MA", 2, 1, 2, 1, 2);
    countryGroupAssignment.putAll("MC", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("MD", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("ME", 1, 2, 1, 2, 2);
    countryGroupAssignment.putAll("MF", 1, 2, 1, 0, 2);
    countryGroupAssignment.putAll("MG", 3, 4, 3, 3, 2);
    countryGroupAssignment.putAll("MH", 4, 2, 2, 4, 2);
    countryGroupAssignment.putAll("MK", 1, 0, 0, 0, 2);
    countryGroupAssignment.putAll("ML", 4, 4, 1, 1, 2);
    countryGroupAssignment.putAll("MM", 2, 3, 2, 2, 2);
    countryGroupAssignment.putAll("MN", 2, 4, 1, 1, 2);
    countryGroupAssignment.putAll("MO", 0, 2, 4, 4, 2);
    countryGroupAssignment.putAll("MP", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("MQ", 2, 2, 2, 3, 2);
    countryGroupAssignment.putAll("MR", 3, 0, 4, 2, 2);
    countryGroupAssignment.putAll("MS", 1, 2, 2, 2, 2);
    countryGroupAssignment.putAll("MT", 0, 2, 0, 1, 2);
    countryGroupAssignment.putAll("MU", 3, 1, 2, 3, 2);
    countryGroupAssignment.putAll("MV", 4, 3, 1, 4, 2);
    countryGroupAssignment.putAll("MW", 4, 1, 1, 0, 2);
    countryGroupAssignment.putAll("MX", 2, 4, 3, 3, 2);
    countryGroupAssignment.putAll("MY", 2, 0, 3, 3, 2);
    countryGroupAssignment.putAll("MZ", 3, 3, 2, 3, 2);
    countryGroupAssignment.putAll("NA", 4, 3, 2, 2, 2);
    countryGroupAssignment.putAll("NC", 2, 0, 4, 4, 2);
    countryGroupAssignment.putAll("NE", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("NF", 2, 2, 2, 2, 2);
    countryGroupAssignment.putAll("NG", 3, 3, 2, 2, 2);
    countryGroupAssignment.putAll("NI", 3, 1, 4, 4, 2);
    countryGroupAssignment.putAll("NL", 0, 2, 4, 2, 0);
    countryGroupAssignment.putAll("NO", 0, 1, 1, 0, 2);
    countryGroupAssignment.putAll("NP", 2, 0, 4, 3, 2);
    countryGroupAssignment.putAll("NR", 4, 2, 3, 1, 2);
    countryGroupAssignment.putAll("NU", 4, 2, 2, 2, 2);
    countryGroupAssignment.putAll("NZ", 0, 2, 1, 2, 4);
    countryGroupAssignment.putAll("OM", 2, 2, 0, 2, 2);
    countryGroupAssignment.putAll("PA", 1, 3, 3, 4, 2);
    countryGroupAssignment.putAll("PE", 2, 4, 4, 4, 2);
    countryGroupAssignment.putAll("PF", 2, 2, 1, 1, 2);
    countryGroupAssignment.putAll("PG", 4, 3, 3, 2, 2);
    countryGroupAssignment.putAll("PH", 3, 0, 3, 4, 4);
    countryGroupAssignment.putAll("PK", 3, 2, 3, 3, 2);
    countryGroupAssignment.putAll("PL", 1, 0, 2, 2, 2);
    countryGroupAssignment.putAll("PM", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("PR", 1, 2, 2, 3, 4);
    countryGroupAssignment.putAll("PS", 3, 3, 2, 2, 2);
    countryGroupAssignment.putAll("PT", 1, 1, 0, 0, 2);
    countryGroupAssignment.putAll("PW", 1, 2, 3, 0, 2);
    countryGroupAssignment.putAll("PY", 2, 0, 3, 3, 2);
    countryGroupAssignment.putAll("QA", 2, 3, 1, 2, 2);
    countryGroupAssignment.putAll("RE", 1, 0, 2, 1, 2);
    countryGroupAssignment.putAll("RO", 1, 1, 1, 2, 2);
    countryGroupAssignment.putAll("RS", 1, 2, 0, 0, 2);
    countryGroupAssignment.putAll("RU", 0, 1, 0, 1, 2);
    countryGroupAssignment.putAll("RW", 4, 3, 3, 4, 2);
    countryGroupAssignment.putAll("SA", 2, 2, 2, 1, 2);
    countryGroupAssignment.putAll("SB", 4, 2, 4, 2, 2);
    countryGroupAssignment.putAll("SC", 4, 2, 0, 1, 2);
    countryGroupAssignment.putAll("SD", 4, 4, 4, 3, 2);
    countryGroupAssignment.putAll("SE", 0, 0, 0, 0, 2);
    countryGroupAssignment.putAll("SG", 0, 0, 3, 3, 4);
    countryGroupAssignment.putAll("SH", 4, 2, 2, 2, 2);
    countryGroupAssignment.putAll("SI", 0, 1, 0, 0, 2);
    countryGroupAssignment.putAll("SJ", 2, 2, 2, 2, 2);
    countryGroupAssignment.putAll("SK", 0, 1, 0, 0, 2);
    countryGroupAssignment.putAll("SL", 4, 3, 3, 1, 2);
    countryGroupAssignment.putAll("SM", 0, 2, 2, 2, 2);
    countryGroupAssignment.putAll("SN", 4, 4, 4, 3, 2);
    countryGroupAssignment.putAll("SO", 3, 4, 4, 4, 2);
    countryGroupAssignment.putAll("SR", 3, 2, 3, 1, 2);
    countryGroupAssignment.putAll("SS", 4, 1, 4, 2, 2);
    countryGroupAssignment.putAll("ST", 2, 2, 1, 2, 2);
    countryGroupAssignment.putAll("SV", 2, 1, 4, 4, 2);
    countryGroupAssignment.putAll("SX", 2, 2, 1, 0, 2);
    countryGroupAssignment.putAll("SY", 4, 3, 2, 2, 2);
    countryGroupAssignment.putAll("SZ", 3, 4, 3, 4, 2);
    countryGroupAssignment.putAll("TC", 1, 2, 1, 0, 2);
    countryGroupAssignment.putAll("TD", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("TG", 3, 2, 1, 0, 2);
    countryGroupAssignment.putAll("TH", 1, 3, 4, 3, 0);
    countryGroupAssignment.putAll("TJ", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("TL", 4, 1, 4, 4, 2);
    countryGroupAssignment.putAll("TM", 4, 2, 1, 2, 2);
    countryGroupAssignment.putAll("TN", 2, 1, 1, 1, 2);
    countryGroupAssignment.putAll("TO", 3, 3, 4, 2, 2);
    countryGroupAssignment.putAll("TR", 1, 2, 1, 1, 2);
    countryGroupAssignment.putAll("TT", 1, 3, 1, 3, 2);
    countryGroupAssignment.putAll("TV", 3, 2, 2, 4, 2);
    countryGroupAssignment.putAll("TW", 0, 0, 0, 0, 1);
    countryGroupAssignment.putAll("TZ", 3, 3, 3, 2, 2);
    countryGroupAssignment.putAll("UA", 0, 3, 0, 0, 2);
    countryGroupAssignment.putAll("UG", 3, 2, 2, 3, 2);
    countryGroupAssignment.putAll("US", 0, 1, 3, 3, 3);
    countryGroupAssignment.putAll("UY", 2, 1, 1, 1, 2);
    countryGroupAssignment.putAll("UZ", 2, 0, 3, 2, 2);
    countryGroupAssignment.putAll("VC", 2, 2, 2, 2, 2);
    countryGroupAssignment.putAll("VE", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("VG", 2, 2, 1, 2, 2);
    countryGroupAssignment.putAll("VI", 1, 2, 2, 4, 2);
    countryGroupAssignment.putAll("VN", 0, 1, 4, 4, 2);
    countryGroupAssignment.putAll("VU", 4, 1, 3, 1, 2);
    countryGroupAssignment.putAll("WS", 3, 1, 4, 2, 2);
    countryGroupAssignment.putAll("XK", 1, 1, 1, 0, 2);
    countryGroupAssignment.putAll("YE", 4, 4, 4, 4, 2);
    countryGroupAssignment.putAll("YT", 3, 2, 1, 3, 2);
    countryGroupAssignment.putAll("ZA", 2, 3, 2, 2, 2);
    countryGroupAssignment.putAll("ZM", 3, 2, 2, 3, 2);
    countryGroupAssignment.putAll("ZW", 3, 3, 3, 3, 2);
    return countryGroupAssignment.build();
  }
}
