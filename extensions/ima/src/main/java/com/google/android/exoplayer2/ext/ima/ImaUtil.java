/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima;

import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_AVAILABLE;
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.getMediaPeriodPositionUsForContent;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.sum;
import static java.lang.Math.max;

import android.content.Context;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.FriendlyObstruction;
import com.google.ads.interactivemedia.v3.api.FriendlyObstructionPurpose;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdPlaybackState.AdGroup;
import com.google.android.exoplayer2.ui.AdOverlayInfo;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSchemeDataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.DoubleMath;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utilities for working with IMA SDK and IMA extension data types. */
/* package */ final class ImaUtil {

  /** Factory for objects provided by the IMA SDK. */
  public interface ImaFactory {
    /** Creates {@link ImaSdkSettings} for configuring the IMA SDK. */
    ImaSdkSettings createImaSdkSettings();
    /**
     * Creates {@link AdsRenderingSettings} for giving the {@link AdsManager} parameters that
     * control rendering of ads.
     */
    AdsRenderingSettings createAdsRenderingSettings();
    /**
     * Creates an {@link AdDisplayContainer} to hold the player for video ads, a container for
     * non-linear ads, and slots for companion ads.
     */
    AdDisplayContainer createAdDisplayContainer(ViewGroup container, VideoAdPlayer player);
    /** Creates an {@link AdDisplayContainer} to hold the player for audio ads. */
    AdDisplayContainer createAudioAdDisplayContainer(Context context, VideoAdPlayer player);
    /**
     * Creates a {@link FriendlyObstruction} to describe an obstruction considered "friendly" for
     * viewability measurement purposes.
     */
    FriendlyObstruction createFriendlyObstruction(
        View view,
        FriendlyObstructionPurpose friendlyObstructionPurpose,
        @Nullable String reasonDetail);
    /** Creates an {@link AdsRequest} to contain the data used to request ads. */
    AdsRequest createAdsRequest();
    /** Creates an {@link AdsLoader} for requesting ads using the specified settings. */
    AdsLoader createAdsLoader(
        Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer);
  }

  /** Stores configuration for ad loading and playback. */
  public static final class Configuration {

    public final long adPreloadTimeoutMs;
    public final int vastLoadTimeoutMs;
    public final int mediaLoadTimeoutMs;
    public final boolean focusSkipButtonWhenAvailable;
    public final boolean playAdBeforeStartPosition;
    public final int mediaBitrate;
    @Nullable public final Boolean enableContinuousPlayback;
    @Nullable public final List<String> adMediaMimeTypes;
    @Nullable public final Set<UiElement> adUiElements;
    @Nullable public final Collection<CompanionAdSlot> companionAdSlots;
    @Nullable public final AdErrorEvent.AdErrorListener applicationAdErrorListener;
    @Nullable public final AdEvent.AdEventListener applicationAdEventListener;
    @Nullable public final VideoAdPlayer.VideoAdPlayerCallback applicationVideoAdPlayerCallback;
    @Nullable public final ImaSdkSettings imaSdkSettings;
    public final boolean debugModeEnabled;

    public Configuration(
        long adPreloadTimeoutMs,
        int vastLoadTimeoutMs,
        int mediaLoadTimeoutMs,
        boolean focusSkipButtonWhenAvailable,
        boolean playAdBeforeStartPosition,
        int mediaBitrate,
        @Nullable Boolean enableContinuousPlayback,
        @Nullable List<String> adMediaMimeTypes,
        @Nullable Set<UiElement> adUiElements,
        @Nullable Collection<CompanionAdSlot> companionAdSlots,
        @Nullable AdErrorEvent.AdErrorListener applicationAdErrorListener,
        @Nullable AdEvent.AdEventListener applicationAdEventListener,
        @Nullable VideoAdPlayer.VideoAdPlayerCallback applicationVideoAdPlayerCallback,
        @Nullable ImaSdkSettings imaSdkSettings,
        boolean debugModeEnabled) {
      this.adPreloadTimeoutMs = adPreloadTimeoutMs;
      this.vastLoadTimeoutMs = vastLoadTimeoutMs;
      this.mediaLoadTimeoutMs = mediaLoadTimeoutMs;
      this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
      this.playAdBeforeStartPosition = playAdBeforeStartPosition;
      this.mediaBitrate = mediaBitrate;
      this.enableContinuousPlayback = enableContinuousPlayback;
      this.adMediaMimeTypes = adMediaMimeTypes;
      this.adUiElements = adUiElements;
      this.companionAdSlots = companionAdSlots;
      this.applicationAdErrorListener = applicationAdErrorListener;
      this.applicationAdEventListener = applicationAdEventListener;
      this.applicationVideoAdPlayerCallback = applicationVideoAdPlayerCallback;
      this.imaSdkSettings = imaSdkSettings;
      this.debugModeEnabled = debugModeEnabled;
    }
  }

  /** Stores configuration for playing server side ad insertion content. */
  public static final class ServerSideAdInsertionConfiguration {

    public final AdViewProvider adViewProvider;
    public final ImaSdkSettings imaSdkSettings;
    @Nullable public final AdEvent.AdEventListener applicationAdEventListener;
    @Nullable public final AdErrorEvent.AdErrorListener applicationAdErrorListener;
    public final ImmutableList<CompanionAdSlot> companionAdSlots;
    public final boolean focusSkipButtonWhenAvailable;
    public final boolean debugModeEnabled;

    public ServerSideAdInsertionConfiguration(
        AdViewProvider adViewProvider,
        ImaSdkSettings imaSdkSettings,
        @Nullable AdEvent.AdEventListener applicationAdEventListener,
        @Nullable AdErrorEvent.AdErrorListener applicationAdErrorListener,
        List<CompanionAdSlot> companionAdSlots,
        boolean focusSkipButtonWhenAvailable,
        boolean debugModeEnabled) {
      this.imaSdkSettings = imaSdkSettings;
      this.adViewProvider = adViewProvider;
      this.applicationAdEventListener = applicationAdEventListener;
      this.applicationAdErrorListener = applicationAdErrorListener;
      this.companionAdSlots = ImmutableList.copyOf(companionAdSlots);
      this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
      this.debugModeEnabled = debugModeEnabled;
    }
  }

  public static final int TIMEOUT_UNSET = -1;
  public static final int BITRATE_UNSET = -1;

  /**
   * Returns the IMA {@link FriendlyObstructionPurpose} corresponding to the given {@link
   * AdOverlayInfo#purpose}.
   */
  public static FriendlyObstructionPurpose getFriendlyObstructionPurpose(
      @AdOverlayInfo.Purpose int purpose) {
    switch (purpose) {
      case AdOverlayInfo.PURPOSE_CONTROLS:
        return FriendlyObstructionPurpose.VIDEO_CONTROLS;
      case AdOverlayInfo.PURPOSE_CLOSE_AD:
        return FriendlyObstructionPurpose.CLOSE_AD;
      case AdOverlayInfo.PURPOSE_NOT_VISIBLE:
        return FriendlyObstructionPurpose.NOT_VISIBLE;
      case AdOverlayInfo.PURPOSE_OTHER:
      default:
        return FriendlyObstructionPurpose.OTHER;
    }
  }

  /**
   * Returns the microsecond ad group timestamps corresponding to the specified cue points.
   *
   * @param cuePoints The cue points of the ads in seconds, provided by the IMA SDK.
   * @return The corresponding microsecond ad group timestamps.
   */
  public static long[] getAdGroupTimesUsForCuePoints(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      return new long[] {0L};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = Math.round(C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return adGroupTimesUs;
  }

  /** Returns an {@link AdsRequest} based on the specified ad tag {@link DataSpec}. */
  public static AdsRequest getAdsRequestForAdTagDataSpec(
      ImaFactory imaFactory, DataSpec adTagDataSpec) throws IOException {
    AdsRequest request = imaFactory.createAdsRequest();
    if (DataSchemeDataSource.SCHEME_DATA.equals(adTagDataSpec.uri.getScheme())) {
      DataSchemeDataSource dataSchemeDataSource = new DataSchemeDataSource();
      try {
        dataSchemeDataSource.open(adTagDataSpec);
        request.setAdsResponse(Util.fromUtf8Bytes(DataSourceUtil.readToEnd(dataSchemeDataSource)));
      } finally {
        dataSchemeDataSource.close();
      }
    } else {
      request.setAdTagUrl(adTagDataSpec.uri.toString());
    }
    return request;
  }

  /** Returns whether the ad error indicates that an entire ad group failed to load. */
  public static boolean isAdGroupLoadError(AdError adError) {
    // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
    // a single ad, ad group or the whole timeline.
    return adError.getErrorCode() == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
        || adError.getErrorCode() == AdError.AdErrorCode.UNKNOWN_ERROR;
  }

  /** Returns the looper on which all IMA SDK interaction must occur. */
  public static Looper getImaLooper() {
    // IMA SDK callbacks occur on the main thread. This method can be used to check that the player
    // is using the same looper, to ensure all interaction with this class is on the main thread.
    return Looper.getMainLooper();
  }

  /** Returns a human-readable representation of a video progress update. */
  @SuppressWarnings("RestrictedApi") // VideoProgressUpdate.equals() is annotated as hidden.
  public static String getStringForVideoProgressUpdate(VideoProgressUpdate videoProgressUpdate) {
    if (VideoProgressUpdate.VIDEO_TIME_NOT_READY.equals(videoProgressUpdate)) {
      return "not ready";
    } else {
      return Util.formatInvariant(
          "%d ms of %d ms",
          videoProgressUpdate.getCurrentTimeMs(), videoProgressUpdate.getDurationMs());
    }
  }

  /**
   * Expands a placeholder ad group with a single ad to the requested number of ads and sets the
   * duration of the inserted ad.
   *
   * <p>The remaining ad group duration is propagated to the ad following the inserted ad. If the
   * inserted ad is the last ad, the remaining ad group duration is wrapped around to the first ad
   * in the group.
   *
   * @param adGroupIndex The ad group index of the ad group to expand.
   * @param adIndexInAdGroup The ad index to set the duration.
   * @param adDurationUs The duration of the ad.
   * @param adGroupDurationUs The duration of the whole ad group.
   * @param adsInAdGroupCount The number of ads of the ad group.
   * @param adPlaybackState The ad playback state to modify.
   * @return The updated ad playback state.
   */
  @CheckResult
  public static AdPlaybackState expandAdGroupPlaceholder(
      int adGroupIndex,
      long adGroupDurationUs,
      int adIndexInAdGroup,
      long adDurationUs,
      int adsInAdGroupCount,
      AdPlaybackState adPlaybackState) {
    checkArgument(adIndexInAdGroup < adsInAdGroupCount);
    long[] adDurationsUs =
        updateAdDurationAndPropagate(
            new long[adsInAdGroupCount], adIndexInAdGroup, adDurationUs, adGroupDurationUs);
    return adPlaybackState
        .withAdCount(adGroupIndex, adDurationsUs.length)
        .withAdDurationsUs(adGroupIndex, adDurationsUs);
  }

  /**
   * Updates the duration of an ad in and ad group.
   *
   * <p>The difference of the previous duration and the updated duration is propagated to the ad
   * following the updated ad. If the updated ad is the last ad, the remaining duration is wrapped
   * around to the first ad in the group.
   *
   * <p>The remaining ad duration is only propagated if the destination ad has a duration of 0.
   *
   * @param adGroupIndex The ad group index of the ad group to expand.
   * @param adIndexInAdGroup The ad index to set the duration.
   * @param adDurationUs The duration of the ad.
   * @param adPlaybackState The ad playback state to modify.
   * @return The updated ad playback state.
   */
  @CheckResult
  public static AdPlaybackState updateAdDurationInAdGroup(
      int adGroupIndex, int adIndexInAdGroup, long adDurationUs, AdPlaybackState adPlaybackState) {
    AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
    checkArgument(adIndexInAdGroup < adGroup.durationsUs.length);
    long[] adDurationsUs =
        updateAdDurationAndPropagate(
            Arrays.copyOf(adGroup.durationsUs, adGroup.durationsUs.length),
            adIndexInAdGroup,
            adDurationUs,
            adGroup.durationsUs[adIndexInAdGroup]);
    return adPlaybackState.withAdDurationsUs(adGroupIndex, adDurationsUs);
  }

  /**
   * Updates the duration of the given ad in the array.
   *
   * <p>The remaining difference when subtracting {@code adDurationUs} from {@code
   * remainingDurationUs} is used as the duration of the next ad after {@code adIndex}. If the
   * updated ad is the last ad, the remaining duration is wrapped around to the first ad of the
   * group.
   *
   * <p>The remaining ad duration is only propagated if the destination ad has a duration of 0.
   *
   * @param adDurationsUs The array to edit.
   * @param adIndex The index of the ad in the durations array.
   * @param adDurationUs The new ad duration.
   * @param remainingDurationUs The remaining ad duration before updating the new ad duration.
   * @return The updated input array, for convenience.
   */
  private static long[] updateAdDurationAndPropagate(
      long[] adDurationsUs, int adIndex, long adDurationUs, long remainingDurationUs) {
    adDurationsUs[adIndex] = adDurationUs;
    int nextAdIndex = (adIndex + 1) % adDurationsUs.length;
    if (adDurationsUs[nextAdIndex] == 0) {
      // Propagate the remaining duration to the next ad.
      adDurationsUs[nextAdIndex] = max(0, remainingDurationUs - adDurationUs);
    }
    return adDurationsUs;
  }

  /**
   * Splits an {@link AdPlaybackState} into a separate {@link AdPlaybackState} for each period of a
   * content timeline.
   *
   * <p>If a period is enclosed by an ad group, the period is considered an ad period. Splitting
   * results in a separate {@link AdPlaybackState ad playback state} for each period that has either
   * no ads or a single ad. In the latter case, the duration of the single ad is set to the duration
   * of the period consuming the entire duration of the period. Accordingly an ad period does not
   * contribute to the duration of the containing window.
   *
   * @param adPlaybackState The ad playback state to be split.
   * @param contentTimeline The content timeline for each period of which to create an {@link
   *     AdPlaybackState}.
   * @return A map of ad playback states for each period UID in the content timeline.
   */
  public static ImmutableMap<Object, AdPlaybackState> splitAdPlaybackStateForPeriods(
      AdPlaybackState adPlaybackState, Timeline contentTimeline) {
    Timeline.Period period = new Timeline.Period();
    if (contentTimeline.getPeriodCount() == 1) {
      // A single period gets the entire ad playback state that may contain multiple ad groups.
      return ImmutableMap.of(
          checkNotNull(
              contentTimeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ true).uid),
          adPlaybackState);
    }

    int periodIndex = 0;
    long totalElapsedContentDurationUs = 0;
    Object adsId = checkNotNull(adPlaybackState.adsId);
    AdPlaybackState contentOnlyAdPlaybackState = new AdPlaybackState(adsId);
    Map<Object, AdPlaybackState> adPlaybackStates = new HashMap<>();
    for (int i = adPlaybackState.removedAdGroupCount; i < adPlaybackState.adGroupCount; i++) {
      AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ i);
      if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
        checkState(i == adPlaybackState.adGroupCount - 1);
        // The last ad group is a placeholder for a potential post roll. We can just stop here.
        break;
      }
      // The ad group start timeUs is in content position. We need to add the ad
      // duration before the ad group to translate the start time to the position in the period.
      long adGroupDurationUs = sum(adGroup.durationsUs);
      long elapsedAdGroupAdDurationUs = 0;
      for (int j = periodIndex; j < contentTimeline.getPeriodCount(); j++) {
        contentTimeline.getPeriod(j, period, /* setIds= */ true);
        if (totalElapsedContentDurationUs < adGroup.timeUs) {
          // Period starts before the ad group, so it is a content period.
          adPlaybackStates.put(checkNotNull(period.uid), contentOnlyAdPlaybackState);
          totalElapsedContentDurationUs += period.durationUs;
        } else {
          long periodStartUs = totalElapsedContentDurationUs + elapsedAdGroupAdDurationUs;
          if (periodStartUs + period.durationUs <= adGroup.timeUs + adGroupDurationUs) {
            // The period ends before the end of the ad group, so it is an ad period (Note: A VOD ad
            // reported by the IMA SDK spans multiple periods before the LOADED event arrives).
            adPlaybackStates.put(
                checkNotNull(period.uid),
                splitAdGroupForPeriod(adsId, adGroup, periodStartUs, period.durationUs));
            elapsedAdGroupAdDurationUs += period.durationUs;
          } else {
            // Period is after the current ad group. Continue with next ad group.
            break;
          }
        }
        // Increment the period index to the next unclassified period.
        periodIndex++;
      }
    }
    // The remaining periods end after the last ad group, so these are content periods.
    for (int i = periodIndex; i < contentTimeline.getPeriodCount(); i++) {
      contentTimeline.getPeriod(i, period, /* setIds= */ true);
      adPlaybackStates.put(checkNotNull(period.uid), contentOnlyAdPlaybackState);
    }
    return ImmutableMap.copyOf(adPlaybackStates);
  }

  private static AdPlaybackState splitAdGroupForPeriod(
      Object adsId, AdGroup adGroup, long periodStartUs, long periodDurationUs) {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(checkNotNull(adsId), /* adGroupTimesUs...= */ 0)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, periodDurationUs)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, adGroup.contentResumeOffsetUs);
    long periodEndUs = periodStartUs + periodDurationUs;
    long adDurationsUs = 0;
    for (int i = 0; i < adGroup.count; i++) {
      adDurationsUs += adGroup.durationsUs[i];
      if (periodEndUs <= adGroup.timeUs + adDurationsUs + 10_000) {
        // Map the state of the global ad state to the period specific ad state.
        switch (adGroup.states[i]) {
          case AdPlaybackState.AD_STATE_PLAYED:
            adPlaybackState =
                adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
            break;
          case AdPlaybackState.AD_STATE_SKIPPED:
            adPlaybackState =
                adPlaybackState.withSkippedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
            break;
          case AdPlaybackState.AD_STATE_ERROR:
            adPlaybackState =
                adPlaybackState.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
            break;
          default:
            // Do nothing.
            break;
        }
        break;
      }
    }
    return adPlaybackState;
  }

  /**
   * Returns the {@code adGroupIndex} and the {@code adIndexInAdGroup} for the given period index of
   * an ad period.
   *
   * @param adPeriodIndex The period index of the ad period.
   * @param adPlaybackState The ad playback state that holds the ad group and ad information.
   * @param contentTimeline The timeline that contains the ad period.
   * @return A pair with the ad group index (first) and the ad index in that ad group (second).
   */
  public static Pair<Integer, Integer> getAdGroupAndIndexInMultiPeriodWindow(
      int adPeriodIndex, AdPlaybackState adPlaybackState, Timeline contentTimeline) {
    Timeline.Period period = new Timeline.Period();
    int periodIndex = 0;
    long totalElapsedContentDurationUs = 0;
    for (int i = adPlaybackState.removedAdGroupCount; i < adPlaybackState.adGroupCount; i++) {
      int adIndexInAdGroup = 0;
      AdGroup adGroup = adPlaybackState.getAdGroup(/* adGroupIndex= */ i);
      long adGroupDurationUs = sum(adGroup.durationsUs);
      long elapsedAdGroupAdDurationUs = 0;
      for (int j = periodIndex; j < contentTimeline.getPeriodCount(); j++) {
        contentTimeline.getPeriod(j, period, /* setIds= */ true);
        if (totalElapsedContentDurationUs < adGroup.timeUs) {
          // Period starts before the ad group, so it is a content period.
          totalElapsedContentDurationUs += period.durationUs;
        } else {
          long periodStartUs = totalElapsedContentDurationUs + elapsedAdGroupAdDurationUs;
          if (periodStartUs + period.durationUs <= adGroup.timeUs + adGroupDurationUs) {
            // The period ends before the end of the ad group, so it is an ad period.
            if (j == adPeriodIndex) {
              return new Pair<>(/* adGroupIndex= */ i, adIndexInAdGroup);
            }
            elapsedAdGroupAdDurationUs += period.durationUs;
            adIndexInAdGroup++;
          } else {
            // Period is after the current ad group. Continue with next ad group.
            break;
          }
        }
        // Increment the period index to the next unclassified period.
        periodIndex++;
      }
    }
    throw new IllegalStateException();
  }

  /**
   * Called when the SDK emits a {@code LOADED} event of an IMA SSAI live stream.
   *
   * <p>For each ad, the SDK emits a {@code LOADED} event at the start of the ad. The {@code LOADED}
   * event provides the information of a certain ad (index and duration) and its ad pod (number of
   * ads and total ad duration) that is mapped to an ad in an {@linkplain AdGroup ad group} of an
   * {@linkplain AdPlaybackState ad playback state} to reflect ads in the ExoPlayer media structure.
   *
   * <p>In the normal case (when all ad information is available completely and in time), the
   * life-cycle of a live ad group and its ads has these phases:
   *
   * <ol>
   *   <li>When playing content and a {@code LOADED} event arrives, an ad group is inserted at the
   *       current position with the number of ads reported by the ad pod. The duration of the first
   *       ad is set and its state is set to {@link AdPlaybackState#AD_STATE_AVAILABLE}. The
   *       duration of the 2nd ad is set to the remaining duration of the total ad group duration.
   *       This pads out the duration of the ad group, so it doesn't end before the next ad event
   *       arrives. When inserting the ad group at the current position, the player immediately
   *       advances to play the inserted ad period.
   *   <li>When playing an ad group and a further {@code LOADED} event arrives, the ad state is
   *       inspected to find the {@linkplain AdPlaybackState#getAdGroupIndexForPositionUs(long,
   *       long) ad group currently being played}. We query for the first {@linkplain
   *       AdPlaybackState#AD_STATE_UNAVAILABLE unavailable ad} of that ad group, override its
   *       placeholder duration, mark it {@linkplain AdPlaybackState#AD_STATE_AVAILABLE available}
   *       and propagate the remainder of the placeholder duration to the next ad. Repeating this
   *       step until all ads are configured and marked as available.
   *   <li>When playing an ad and a {@code LOADED} event arrives but no more ads are in {@link
   *       AdPlaybackState#AD_STATE_UNAVAILABLE}, the group is expanded by inserting a new ad at the
   *       end of the ad group.
   *   <li>After playing an ad: When playback exits from an ad period to the next ad or back to
   *       content, {@link ImaServerSideAdInsertionMediaSource} detects {@linkplain
   *       Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int) a
   *       position discontinuity}, identifies {@linkplain Player.PositionInfo#adIndexInAdGroup the
   *       ad being exited} and {@linkplain AdPlaybackState#AD_STATE_PLAYED marks the ad as played}.
   * </ol>
   *
   * <p>Some edge-cases need consideration. When a user joins a live stream during an ad being
   * played, ad information previous to the first received {@code LOADED} event is missing. Only ads
   * starting from the first ad with full information are inserted into the group (back to happy
   * path step 2).
   *
   * <p>There is further a chance, that a (pre-fetch) event arrives after the ad group has already
   * ended. In such a case, the pre-fetch ad starts a new ad group with the remaining ads in the
   * same way as the during-ad-joiner case that can afterwards be expanded again (back to end of
   * happy path step 2).
   *
   * @param currentContentPeriodPositionUs The current public content position, in microseconds.
   * @param adDurationUs The duration of the ad to be inserted, in microseconds.
   * @param adPositionInAdPod The ad position in the ad pod (Note: starts with index 1).
   * @param totalAdDurationUs The total duration of all ads as declared by the ad pod.
   * @param totalAdsInAdPod The total number of ads declared by the ad pod.
   * @param adPlaybackState The ad playback state with the current ad information.
   * @return The updated {@link AdPlaybackState}.
   */
  @CheckResult
  public static AdPlaybackState addLiveAdBreak(
      long currentContentPeriodPositionUs,
      long adDurationUs,
      int adPositionInAdPod,
      long totalAdDurationUs,
      int totalAdsInAdPod,
      AdPlaybackState adPlaybackState) {
    checkArgument(adPositionInAdPod > 0);
    long mediaPeriodPositionUs =
        getMediaPeriodPositionUsForContent(
            currentContentPeriodPositionUs, /* nextAdGroupIndex= */ C.INDEX_UNSET, adPlaybackState);
    // TODO(b/217187518) Support seeking backwards.
    int adGroupIndex =
        adPlaybackState.getAdGroupIndexForPositionUs(
            mediaPeriodPositionUs, /* periodDurationUs= */ C.TIME_UNSET);
    if (adGroupIndex == C.INDEX_UNSET) {
      int adIndexInAdGroup = adPositionInAdPod - 1;
      long[] adDurationsUs =
          updateAdDurationAndPropagate(
              new long[totalAdsInAdPod - adIndexInAdGroup],
              /* adIndex= */ 0,
              adDurationUs,
              totalAdDurationUs);
      adPlaybackState =
          addAdGroupToAdPlaybackState(
              adPlaybackState,
              /* fromPositionUs= */ currentContentPeriodPositionUs,
              /* contentResumeOffsetUs= */ sum(adDurationsUs),
              /* adDurationsUs...= */ adDurationsUs);
      adGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(
              mediaPeriodPositionUs, /* periodDurationUs= */ C.TIME_UNSET);
      if (adGroupIndex != C.INDEX_UNSET) {
        adPlaybackState =
            adPlaybackState
                .withAvailableAd(adGroupIndex, /* adIndexInAdGroup= */ 0)
                .withOriginalAdCount(adGroupIndex, /* originalAdCount= */ totalAdsInAdPod);
      }
    } else {
      AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
      long[] newDurationsUs = Arrays.copyOf(adGroup.durationsUs, adGroup.count);
      int nextUnavailableAdIndex = getNextUnavailableAdIndex(adGroup);
      if (adGroup.originalCount < totalAdsInAdPod || nextUnavailableAdIndex == adGroup.count) {
        int adInAdGroupCount = max(totalAdsInAdPod, nextUnavailableAdIndex + 1);
        adPlaybackState =
            adPlaybackState
                .withAdCount(adGroupIndex, adInAdGroupCount)
                .withOriginalAdCount(adGroupIndex, /* originalAdCount= */ adInAdGroupCount);
        newDurationsUs = Arrays.copyOf(newDurationsUs, adInAdGroupCount);
        newDurationsUs[nextUnavailableAdIndex] = totalAdDurationUs;
        Arrays.fill(
            newDurationsUs,
            /* fromIndex= */ nextUnavailableAdIndex + 1,
            /* toIndex= */ adInAdGroupCount,
            /* val= */ 0L);
      }
      long remainingDurationUs = max(adDurationUs, newDurationsUs[nextUnavailableAdIndex]);
      updateAdDurationAndPropagate(
          newDurationsUs, nextUnavailableAdIndex, adDurationUs, remainingDurationUs);
      adPlaybackState =
          adPlaybackState
              .withAdDurationsUs(adGroupIndex, newDurationsUs)
              .withAvailableAd(adGroupIndex, nextUnavailableAdIndex)
              .withContentResumeOffsetUs(adGroupIndex, sum(newDurationsUs));
    }
    return adPlaybackState;
  }

  /**
   * Splits the ad group at an available ad at a given split index.
   *
   * <p>When splitting, the ads from and after the split index are removed from the existing ad
   * group. Then the ad events of all removed available ads are replicated to get the exact same
   * result as if the new ad group was created by SDK ad events.
   *
   * @param adGroup The ad group to split.
   * @param adGroupIndex The index of the ad group in the ad playback state.
   * @param splitIndexExclusive The first index that should be part of the newly created ad group.
   * @param adPlaybackState The ad playback state to modify.
   * @return The ad playback state with the split ad group.
   */
  @CheckResult
  public static AdPlaybackState splitAdGroup(
      AdGroup adGroup, int adGroupIndex, int splitIndexExclusive, AdPlaybackState adPlaybackState) {
    checkArgument(splitIndexExclusive > 0 && splitIndexExclusive < adGroup.count);
    // Remove the ads from the ad group.
    for (int i = 0; i < adGroup.count - splitIndexExclusive; i++) {
      adPlaybackState = adPlaybackState.withLastAdRemoved(adGroupIndex);
    }
    AdGroup previousAdGroup = adPlaybackState.getAdGroup(adGroupIndex);
    long newAdGroupTimeUs = previousAdGroup.timeUs + previousAdGroup.contentResumeOffsetUs;
    // Replicate ad events for each available ad that has been removed.
    @AdPlaybackState.AdState
    int[] removedStates = Arrays.copyOfRange(adGroup.states, splitIndexExclusive, adGroup.count);
    long[] removedDurationsUs =
        Arrays.copyOfRange(adGroup.durationsUs, splitIndexExclusive, adGroup.count);
    long remainingAdDurationUs = sum(removedDurationsUs);
    for (int i = 0; i < removedStates.length && removedStates[i] == AD_STATE_AVAILABLE; i++) {
      adPlaybackState =
          addLiveAdBreak(
              newAdGroupTimeUs,
              /* adDurationUs= */ removedDurationsUs[i],
              /* adPositionInAdPod= */ i + 1,
              /* totalAdDurationUs= */ remainingAdDurationUs,
              /* totalAdsInAdPod= */ removedDurationsUs.length,
              adPlaybackState);
      remainingAdDurationUs -= removedDurationsUs[i];
    }
    return adPlaybackState;
  }

  private static int getNextUnavailableAdIndex(AdGroup adGroup) {
    for (int i = 0; i < adGroup.states.length; i++) {
      if (adGroup.states[i] == AD_STATE_UNAVAILABLE) {
        return i;
      }
    }
    return adGroup.states.length;
  }

  /**
   * Converts a time in seconds to the corresponding time in microseconds.
   *
   * <p>Fractional values are rounded to the nearest microsecond using {@link RoundingMode#HALF_UP}.
   *
   * @param timeSec The time in seconds.
   * @return The corresponding time in microseconds.
   */
  public static long secToUsRounded(double timeSec) {
    return DoubleMath.roundToLong(
        BigDecimal.valueOf(timeSec).scaleByPowerOfTen(6).doubleValue(), RoundingMode.HALF_UP);
  }

  /**
   * Converts a time in seconds to the corresponding time in milliseconds.
   *
   * <p>Fractional values are rounded to the nearest millisecond using {@link RoundingMode#HALF_UP}.
   *
   * @param timeSec The time in seconds.
   * @return The corresponding time in milliseconds.
   */
  public static long secToMsRounded(double timeSec) {
    return DoubleMath.roundToLong(
        BigDecimal.valueOf(timeSec).scaleByPowerOfTen(3).doubleValue(), RoundingMode.HALF_UP);
  }

  private ImaUtil() {}
}
