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
package androidx.media3.common;

import static androidx.media3.common.AdPlaybackState.AD_STATE_AVAILABLE;
import static androidx.media3.common.AdPlaybackState.AD_STATE_ERROR;
import static androidx.media3.common.AdPlaybackState.AD_STATE_PLAYED;
import static androidx.media3.common.AdPlaybackState.AD_STATE_SKIPPED;
import static androidx.media3.common.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AdPlaybackState}. */
@RunWith(AndroidJUnit4.class)
public class AdPlaybackStateTest {

  private static final long[] TEST_AD_GROUP_TIMES_US = new long[] {0, 5_000_000, 10_000_000};
  private static final MediaItem TEST_MEDIA_ITEM = MediaItem.fromUri("http://www.google.com");
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
  public void setAdMediaItemBeforeAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);

    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2);

    assertThat(state.getAdGroup(1).mediaItems[0]).isNull();
    assertThat(state.getAdGroup(1).states[0]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.getAdGroup(1).mediaItems[1]).isSameInstanceAs(TEST_MEDIA_ITEM);
    assertThat(state.getAdGroup(1).states[1]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void setAdErrorBeforeAdCount() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);

    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2);

    assertThat(state.getAdGroup(1).mediaItems[0]).isNull();
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
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM)
            .withSkippedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0);

    state =
        state
            .withNewAdGroup(/* adGroupIndex= */ 1, /* adGroupTimeUs= */ 1_000)
            .withNewAdGroup(/* adGroupIndex= */ 3, /* adGroupTimeUs= */ 5_000)
            .withNewAdGroup(/* adGroupIndex= */ 5, /* adGroupTimeUs= */ 8_000);

    assertThat(state.adGroupCount).isEqualTo(6);
    assertThat(state.getAdGroup(1).count).isEqualTo(C.INDEX_UNSET);
    assertThat(state.getAdGroup(2).count).isEqualTo(2);
    assertThat(state.getAdGroup(2).mediaItems[1]).isSameInstanceAs(TEST_MEDIA_ITEM);
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
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsPlayedAd() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

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
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

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
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(2);
  }

  @Test
  public void getNextAdIndexToPlaySkipsErrorAds() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM);

    state = state.withAdLoadError(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1);

    assertThat(state.getAdGroup(1).getNextAdIndexToPlay(0)).isEqualTo(2);
  }

  @Test
  public void getFirstAdIndexToPlay_withPlayedServerSideInsertedAds_returnsFirstIndex() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

    state = state.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);

    assertThat(state.getAdGroup(1).getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void getNextAdIndexToPlay_withPlayedServerSideInsertedAds_returnsNextIndex() {
    AdPlaybackState state =
        new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TIMES_US).withRemovedAdGroupCount(1);
    state = state.withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true);
    state = state.withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 3);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);

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
    assertThat(state.getAdGroup(adGroupIndex).mediaItems)
        .asList()
        .containsExactly(null, null, MediaItem.fromUri(Uri.EMPTY))
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
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 3, TEST_MEDIA_ITEM);
    state =
        state.withAvailableAdMediaItem(
            /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 4, TEST_MEDIA_ITEM);
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
    assertThat(state.getAdGroup(/* adGroupIndex= */ 1).mediaItems)
        .asList()
        .containsExactly(null, TEST_MEDIA_ITEM, TEST_MEDIA_ITEM, TEST_MEDIA_ITEM, TEST_MEDIA_ITEM)
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
        AdPlaybackState.fromBundle(adPlaybackStateWithNoAdsBundle);

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
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM)
            .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 2)
            .withSkippedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 0, TEST_MEDIA_ITEM)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 2, /* adIndexInAdGroup= */ 1, TEST_MEDIA_ITEM)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, /* contentResumeOffsetUs= */ 4444)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 3333)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 12)
            .withAdDurationsUs(/* adGroupIndex= */ 2, /* adDurationsUs...= */ 34, 56)
            .withAdResumePositionUs(123)
            .withContentDurationUs(456);

    AdPlaybackState restoredState = AdPlaybackState.fromBundle(originalState.toBundle());

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
            .withAdMediaItem(
                new MediaItem.Builder().setUri("https://www.google.com").setMediaId("id").build(),
                /* index= */ 0)
            .withAdMediaItem(new MediaItem.Builder().setUri(Uri.EMPTY).build(), /* index= */ 1)
            .withAdDurationsUs(new long[] {1234, 5678})
            .withContentResumeOffsetUs(4444)
            .withIsServerSideInserted(true);

    assertThat(AdPlaybackState.AdGroup.fromBundle(adGroup.toBundle())).isEqualTo(adGroup);
  }

  @Test
  public void fromBundle_ofAdGroupWithOnlyUris_yieldsCorrectInstance() {
    AdPlaybackState.AdGroup adGroup =
        new AdPlaybackState.AdGroup(/* timeUs= */ 42)
            .withAdCount(2)
            .withAdState(AD_STATE_AVAILABLE, /* index= */ 0)
            .withAdState(AD_STATE_PLAYED, /* index= */ 1)
            .withAdMediaItem(
                new MediaItem.Builder().setUri("https://www.google.com").build(), /* index= */ 0)
            .withAdMediaItem(new MediaItem.Builder().setUri(Uri.EMPTY).build(), /* index= */ 1)
            .withAdDurationsUs(new long[] {1234, 5678})
            .withContentResumeOffsetUs(4444)
            .withIsServerSideInserted(true);
    Bundle bundle = adGroup.toBundle();
    bundle.remove(AdPlaybackState.AdGroup.FIELD_MEDIA_ITEMS);

    assertThat(AdPlaybackState.AdGroup.fromBundle(bundle)).isEqualTo(adGroup);
  }

  @Test
  public void withLivePostrollPlaceholderAppended_emptyAdPlaybackState_insertsPlaceholder() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId").withLivePostrollPlaceholderAppended();

    assertThat(adPlaybackState.adGroupCount).isEqualTo(1);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 0).timeUs)
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 0).count).isEqualTo(C.LENGTH_UNSET);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 0).isServerSideInserted).isTrue();
  }

  @Test
  public void withLivePostrollPlaceholderAppended_withExistingAdGroups_appendsPlaceholder() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("state", /* adGroupTimesUs...= */ 0L, 10_000_000L)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ 10_000_000L)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 5_000_000L);

    adPlaybackState = adPlaybackState.withLivePostrollPlaceholderAppended();

    assertThat(adPlaybackState.adGroupCount).isEqualTo(3);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 2).timeUs)
        .isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 2).count).isEqualTo(C.LENGTH_UNSET);
    assertThat(adPlaybackState.getAdGroup(/* adGroupIndex= */ 2).isServerSideInserted).isTrue();
  }

  @Test
  public void endsWithLivePostrollPlaceHolder_withExistingAdGroups_postrollDetected() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 0L, 10_000_000L)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, /* adDurationsUs...= */ 10_000_000L)
            .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ 5_000_000L);

    boolean endsWithLivePostrollPlaceHolder = adPlaybackState.endsWithLivePostrollPlaceHolder();

    assertThat(endsWithLivePostrollPlaceHolder).isFalse();

    adPlaybackState = adPlaybackState.withLivePostrollPlaceholderAppended();
    endsWithLivePostrollPlaceHolder = adPlaybackState.endsWithLivePostrollPlaceHolder();

    assertThat(endsWithLivePostrollPlaceHolder).isTrue();
  }

  @Test
  public void endsWithLivePostrollPlaceHolder_emptyAdPlaybackState_postrollNotDetected() {
    assertThat(AdPlaybackState.NONE.endsWithLivePostrollPlaceHolder()).isFalse();
    assertThat(new AdPlaybackState("adsId").endsWithLivePostrollPlaceHolder()).isFalse();
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

  @Test
  public void
      getAdGroupIndexAfterPositionUs_withServerSidePostrollPlaceholderForLive_placeholderAsNextAdGroupIndex() {
    AdPlaybackState state =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 2000)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withLivePostrollPlaceholderAppended();

    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 1999, /* periodDurationUs= */ 5000))
        .isEqualTo(0);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexAfterPositionUs(
                /* positionUs= */ 2000, /* periodDurationUs= */ 5000))
        .isEqualTo(1);
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
  public void
      getAdGroupIndexForPositionUs_withServerSidePostrollPlaceholderForLive_ignoresPlaceholder() {
    AdPlaybackState state =
        new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 0L, 5_000_000L, C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 4_999_999L, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 4_999_999L, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 5_000_000L, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 5_000_000L, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(1);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(1);
  }

  @Test
  public void
      getAdGroupIndexForPositionUs_withOnlyServerSidePostrollPlaceholderForLive_ignoresPlaceholder() {
    AdPlaybackState state =
        new AdPlaybackState("adsId", /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true);

    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 5_000_000L, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 5_000_000L, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ 10_000_001L, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ 10_000_000L))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            state.getAdGroupIndexForPositionUs(
                /* positionUs= */ C.TIME_END_OF_SOURCE, /* periodDurationUs= */ C.TIME_UNSET))
        .isEqualTo(C.INDEX_UNSET);
  }
}
