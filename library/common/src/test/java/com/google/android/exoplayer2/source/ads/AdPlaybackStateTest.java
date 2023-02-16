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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_AVAILABLE;
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_ERROR;
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_PLAYED;
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_SKIPPED;
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AdPlaybackState}. */
@RunWith(AndroidJUnit4.class)
public class AdPlaybackStateTest {

  private static final long[] TEST_AD_GROUP_TIMES_US = new long[] {0, 5_000_000, 10_000_000};
  private static final Uri TEST_URI = Uri.parse("http://www.google.com");
  private static final Object TEST_ADS_ID = new Object();

  @Test
  public void setAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);

    assertThat(state.getAdGroup(1).count).isEqualTo(C.LENGTH_UNSET);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1);

    assertThat(state.getAdGroup(1).count).isEqualTo(1);
  }

  @Test
  public void setAdUriBeforeAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);

    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2);

    assertThat(state.getAdGroup(1).uris[0]).isNull();
    assertThat(state.getAdGroup(1).states[0]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.getAdGroup(1).uris[1]).isSameInstanceAs(TEST_URI);
    assertThat(state.getAdGroup(1).states[1]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void setAdErrorBeforeAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);

    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2);

    assertThat(state.getAdGroup(1).uris[0]).isNull();
    assertThat(state.getAdGroup(1).states[0]).isEqualTo(AdPlaybackState.AD_STATE_ERROR);
    assertThat(state.isAdInErrorState(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)).isTrue();
    assertThat(state.getAdGroup(1).states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.isAdInErrorState(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1)).isFalse();
  }

  @Test
  public void withAdGroupTimeUs_updatesAdGroupTimeUs() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 0, 5_000, 10_000)
            .withRemovedAdGroupCount(1);

    state =
        state
            .withAdGroupTimeUs(/* adGroupIndex= */ 1, 3_000)
            .withAdGroupTimeUs(/* adGroupIndex= */ 2, 6_000);

    assertThat(state.adGroupCount).isEqualTo(3);
    assertThat(state.getAdGroup(1).timeUs).isEqualTo(3_000);
    assertThat(state.getAdGroup(2).timeUs).isEqualTo(6_000);
  }

  @Test
  public void withNewAdGroup_addsGroupAndKeepsExistingGroups() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 0, 3_000, 6_000)
            .withRemovedAdGroupCount(1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
            .withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI)
            .withSkippedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0);

    state =
        state
            .withNewAdGroup(/* adGroupIndex= */ 1, /* adGroupTimeUs= */ 1_000)
            .withNewAdGroup(/* adGroupIndex= */ 3, /* adGroupTimeUs= */ 5_000)
            .withNewAdGroup(/* adGroupIndex= */ 5, /* adGroupTimeUs= */ 8_000);

    assertThat(state.adGroupCount).isEqualTo(6);
    assertThat(state.getAdGroup(1).count).isEqualTo(C.INDEX_UNSET);
    assertThat(state.getAdGroup(2).count).isEqualTo(2);
    assertThat(state.getAdGroup(2).uris[1]).isSameInstanceAs(TEST_URI);
    assertThat(state.getAdGroup(3).count).isEqualTo(C.INDEX_UNSET);
    assertThat(state.getAdGroup(4).count).isEqualTo(1);
    assertThat(state.getAdGroup(4).states[0]).isEqualTo(AdPlaybackState.AD_STATE_SKIPPED);
    assertThat(state.getAdGroup(5).count).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void withAdDurationsUs_updatesAdDurations() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 0, 10_000)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withAdDurationsUs(new long[][] {new long[] {5_000, 6_000}, new long[] {7_000, 8_000}});

    state = state.withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 1_000, 2_000);

    assertThat(state.getAdGroup(0).durationsUs[0]).isEqualTo(5_000);
    assertThat(state.getAdGroup(0).durationsUs[1]).isEqualTo(6_000);
    assertThat(state.getAdGroup(1).durationsUs[0]).isEqualTo(1_000);
    assertThat(state.getAdGroup(1).durationsUs[1]).isEqualTo(2_000);
  }

  @Test
  public void getFirstAdIndexToPlayIsZero() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsPlayedAd() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.getAdGroup(1).states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.getAdGroup(1).states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsSkippedAd() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withSkippedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.getAdGroup(1).states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.getAdGroup(1).states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsErrorAds() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(2);
  }

  @Test
  public void getNextAdIndexToPlaySkipsErrorAds() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI);

    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1);

    assertThat(state.getAdGroup(1).getNextAdIndexToPlay(0)).isEqualTo(2);
  }

  @Test
  public void getFirstAdIndexToPlay_withPlayedServerSideInsertedAds_returnsFirstIndex() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void getNextAdIndexToPlay_withPlayedServerSideInsertedAds_returnsNextIndex() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1);
    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2);

    assertThat(state.getAdGroup(1).getNextAdIndexToPlay(/* lastPlayedAdIndex= */ 0)).isEqualTo(1);
    assertThat(state.getAdGroup(1).getNextAdIndexToPlay(/* lastPlayedAdIndex= */ 1)).isEqualTo(2);
  }

  @Test
  public void setAdStateTwiceThrows() {
    AdPlaybackState state = new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    try {
      state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
      fail();
    } catch (RuntimeException e) {
      // Expected.
    }
  }

  @Test
  public void withAvailableAd() {
    int adGroupIndex = 2;
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US)
            .withRemovedAdGroupCount(2)
            .withAdCount(adGroupIndex, 3)
            .withAdDurationsUs(adGroupIndex, /* adDurationsUs...*/ 10, 20, 30)
            .withIsServerSideInserted(adGroupIndex, true);

    state = state.withAvailableAd(adGroupIndex, /* adIndexInAdGroup= */ 2);

    assertThat(state.getAdGroup(adGroupIndex).states)
        .asList()
        .containsExactly(AD_STATE_UNAVAILABLE, AD_STATE_UNAVAILABLE, AD_STATE_AVAILABLE)
        .inOrder();
    assertThat(state.getAdGroup(adGroupIndex).uris)
        .asList()
        .containsExactly(null, null, Uri.EMPTY)
        .inOrder();

    state =
        state
            .withAvailableAd(adGroupIndex, /* adIndexInAdGroup= */ 0)
            .withAvailableAd(adGroupIndex, /* adIndexInAdGroup= */ 1)
            .withAvailableAd(adGroupIndex, /* adIndexInAdGroup= */ 2);

    assertThat(state.getAdGroup(adGroupIndex).states)
        .asList()
        .containsExactly(AD_STATE_AVAILABLE, AD_STATE_AVAILABLE, AD_STATE_AVAILABLE)
        .inOrder();
  }

  @Test
  public void withAvailableAd_forClientSideAdGroup_throwsRuntimeException() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US)
            .withRemovedAdGroupCount(2)
            .withAdCount(/* adGroupIndex= */ 2, 3)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...*/ 10, 20, 30);

    Assert.assertThrows(
        IllegalStateException.class, () -> state.withAvailableAd(/* adGroupIndex= */ 2, 1));
  }

  @Test
  public void skipAllWithoutAdCount() {
    AdPlaybackState state = new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US);
    state = state.withSkippedAdGroup(0);
    state = state.withSkippedAdGroup(1);
    assertThat(state.getAdGroup(0).count).isEqualTo(0);
    assertThat(state.getAdGroup(1).count).isEqualTo(0);
  }

  @Test
  public void withResetAdGroup_beforeSetAdCount_doesNothing() {
    AdPlaybackState state = new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US);

    state = state.withResetAdGroup(/* adGroupIndex= */ 1);

    assertThat(state.getAdGroup(1).count).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void withOriginalAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 5_000_000)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    state = state.withOriginalAdCount(/* adGroupIndex= */ 0, /* originalAdCount= */ 3);

    assertThat(state.getAdGroup(0).count).isEqualTo(2);
    assertThat(state.getAdGroup(0).originalCount).isEqualTo(3);
  }

  @Test
  public void withOriginalAdCount_unsetValue_defaultsToIndexUnset() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 5_000_000)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    assertThat(state.getAdGroup(0).count).isEqualTo(2);
    assertThat(state.getAdGroup(0).originalCount).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void withLastAdGroupRemoved() {
    AdPlaybackState state = new AdPlaybackState(TEST_ADS_ID, /* adGroupTimesUs...= */ 5_000_000);
    state =
        state
            .withAdCount(/* adGroupIndex= */ 0, 3)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 10_000L, 20_000L, 30_000L)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true);

    state = state.withLastAdRemoved(0);

    assertThat(state.getAdGroup(/* adGroupIndex= */ 0).states).asList().hasSize(2);
    assertThat(state.getAdGroup(/* adGroupIndex= */ 0).durationsUs)
        .asList()
        .containsExactly(10_000L, 20_000L)
        .inOrder();
    assertThat(state.getAdGroup(/* adGroupIndex= */ 0).states)
        .asList()
        .containsExactly(AD_STATE_PLAYED, AD_STATE_PLAYED);
  }

  @Test
  public void withResetAdGroup_resetsAdsInFinalStates() {
    AdPlaybackState state = new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 5);
    state =
        state.withAdDurationsUs(
            /* adGroupIndex= */ 1, /* adDurationsUs...= */ 1_000L, 2_000L, 3_000L, 4_000L, 5_000L);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 3, TEST_URI);
    state = state.withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 4, TEST_URI);
    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2);
    state = state.withSkippedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 3);
    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 4);
    // Verify setup.
    assertThat(state.getAdGroup(/* adGroupIndex= */ 1).states)
        .asList()
        .containsExactly(
            AD_STATE_UNAVAILABLE,
            AD_STATE_AVAILABLE,
            AD_STATE_PLAYED,
            AD_STATE_SKIPPED,
            AD_STATE_ERROR)
        .inOrder();

    state = state.withResetAdGroup(/* adGroupIndex= */ 1);

    assertThat(state.getAdGroup(/* adGroupIndex= */ 1).states)
        .asList()
        .containsExactly(
            AD_STATE_UNAVAILABLE,
            AD_STATE_AVAILABLE,
            AD_STATE_AVAILABLE,
            AD_STATE_AVAILABLE,
            AD_STATE_AVAILABLE)
        .inOrder();
    assertThat(state.getAdGroup(/* adGroupIndex= */ 1).uris)
        .asList()
        .containsExactly(null, TEST_URI, TEST_URI, TEST_URI, TEST_URI)
        .inOrder();
    assertThat(state.getAdGroup(/* adGroupIndex= */ 1).durationsUs)
        .asList()
        .containsExactly(1_000L, 2_000L, 3_000L, 4_000L, 5_000L);
  }

  @Test
  public void adPlaybackStateWithNoAds_checkValues() {
    AdPlaybackState adPlaybackStateWithNoAds = AdPlaybackState.NONE;

    // Please refrain from altering these values since doing so would cause issues with backwards
    // compatibility.
    assertThat(adPlaybackStateWithNoAds.adsId).isNull();
    assertThat(adPlaybackStateWithNoAds.adGroupCount).isEqualTo(0);
    assertThat(adPlaybackStateWithNoAds.adResumePositionUs).isEqualTo(0);
    assertThat(adPlaybackStateWithNoAds.contentDurationUs).isEqualTo(C.TIME_UNSET);
    assertThat(adPlaybackStateWithNoAds.removedAdGroupCount).isEqualTo(0);
  }

  @Test
  public void adPlaybackStateWithNoAds_toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    AdPlaybackState adPlaybackStateWithNoAds = AdPlaybackState.NONE;

    Bundle adPlaybackStateWithNoAdsBundle = adPlaybackStateWithNoAds.toBundle();

    // Check that default values are skipped when bundling.
    assertThat(adPlaybackStateWithNoAdsBundle.keySet()).isEmpty();

    AdPlaybackState adPlaybackStateWithNoAdsFromBundle =
        AdPlaybackState.CREATOR.fromBundle(adPlaybackStateWithNoAdsBundle);

    assertThat(adPlaybackStateWithNoAdsFromBundle.adsId).isEqualTo(adPlaybackStateWithNoAds.adsId);
    assertThat(adPlaybackStateWithNoAdsFromBundle.adGroupCount)
        .isEqualTo(adPlaybackStateWithNoAds.adGroupCount);
    assertThat(adPlaybackStateWithNoAdsFromBundle.adResumePositionUs)
        .isEqualTo(adPlaybackStateWithNoAds.adResumePositionUs);
    assertThat(adPlaybackStateWithNoAdsFromBundle.contentDurationUs)
        .isEqualTo(adPlaybackStateWithNoAds.contentDurationUs);
    assertThat(adPlaybackStateWithNoAdsFromBundle.removedAdGroupCount)
        .isEqualTo(adPlaybackStateWithNoAds.removedAdGroupCount);
  }

  @Test
  public void createAdPlaybackState_roundTripViaBundle_yieldsEqualFieldsExceptAdsId() {
    AdPlaybackState originalState =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US)
            .withRemovedAdGroupCount(1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)
            .withAvailableAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withSkippedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1)
            .withAvailableAdUri(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, TEST_URI)
            .withAvailableAdUri(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, TEST_URI)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, /* contentResumeOffsetUs= */ 4444)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 3333)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 12)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 34, 56)
            .withAdResumePositionUs(123)
            .withContentDurationUs(456);

    AdPlaybackState restoredState = AdPlaybackState.CREATOR.fromBundle(originalState.toBundle());

    assertThat(restoredState.adsId).isNull();
    assertThat(restoredState.adGroupCount).isEqualTo(originalState.adGroupCount);
    for (int i = 0; i < restoredState.adGroupCount; i++) {
      assertThat(restoredState.getAdGroup(i)).isEqualTo(originalState.getAdGroup(i));
    }
    assertThat(restoredState.adResumePositionUs).isEqualTo(originalState.adResumePositionUs);
    assertThat(restoredState.contentDurationUs).isEqualTo(originalState.contentDurationUs);
  }

  @Test
  public void roundTripViaBundle_ofAdGroup_yieldsEqualInstance() {
    AdPlaybackState.AdGroup adGroup =
        new AdPlaybackState.AdGroup(/* timeUs= */ 42)
            .withAdCount(2)
            .withAdState(AD_STATE_AVAILABLE, /* index= */ 0)
            .withAdState(AD_STATE_PLAYED, /* index= */ 1)
            .withAdUri(Uri.parse("https://www.google.com"), /* index= */ 0)
            .withAdUri(Uri.EMPTY, /* index= */ 1)
            .withAdDurationsUs(new long[] {1234, 5678})
            .withContentResumeOffsetUs(4444)
            .withIsServerSideInserted(true);

    assertThat(AdPlaybackState.AdGroup.CREATOR.fromBundle(adGroup.toBundle())).isEqualTo(adGroup);
  }

  @Test
  public void
      getAdGroupIndexAfterPositionUs_withClientSideInsertedAds_returnsNextAdGroupWithUnplayedAds() {
    AdPlaybackState state =
        new AdPlaybackState(
                /* adsId= */ new Object(),
                /* adGroupTimesUs...= */ 0,
                1000,
                2000,
                3000,
                4000,
                C.TIME_END_OF_SOURCE)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 3, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 4, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 5, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 3, /* adIndexInAdGroup= */ 0);

    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 0, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(/* positionUs= */ 0, /* periodDurationUs= */ 5000))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1999, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1999, /* periodDurationUs= */ 5000))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(4);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ 5000))
        .isEqualTo(4);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 3999, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(4);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 3999, /* periodDurationUs= */ 5000))
        .isEqualTo(4);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 4000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(5);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 4000, /* periodDurationUs= */ 5000))
        .isEqualTo(5);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 4999, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(5);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 4999, /* periodDurationUs= */ 5000))
        .isEqualTo(5);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 5000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(5);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 5000, /* periodDurationUs= */ 5000))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ 5000))
        .isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void getAdGroupIndexAfterPositionUs_withServerSideInsertedAds_returnsNextAdGroup() {
    AdPlaybackState state =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0, 1000, 2000)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0);

    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 0, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(/* positionUs= */ 0, /* periodDurationUs= */ 5000))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 999, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 999, /* periodDurationUs= */ 5000))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1000, /* periodDurationUs= */ 5000))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1999, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1999, /* periodDurationUs= */ 5000))
        .isEqualTo(2);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ 5000))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ 5000))
        .isEqualTo(C.INDEX_UNSET);
  }
}
