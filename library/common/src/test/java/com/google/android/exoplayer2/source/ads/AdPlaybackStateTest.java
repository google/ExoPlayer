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
import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_PLAYED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AdPlaybackState}. */
@RunWith(AndroidJUnit4.class)
public class AdPlaybackStateTest {

  private static final long[] TEST_AD_GROUP_TMES_US = new long[] {0, C.msToUs(10_000)};
  private static final Uri TEST_URI = Uri.EMPTY;
  private static final Object TEST_ADS_ID = new Object();

  private AdPlaybackState state;

  @Before
  public void setUp() {
    state = new AdPlaybackState(TEST_ADS_ID, TEST_AD_GROUP_TMES_US);
  }

  @Test
  public void setAdCount() {
    assertThat(state.adGroups[0].count).isEqualTo(C.LENGTH_UNSET);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    assertThat(state.adGroups[0].count).isEqualTo(1);
  }

  @Test
  public void setAdUriBeforeAdCount() {
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1, TEST_URI);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    assertThat(state.adGroups[0].uris[0]).isNull();
    assertThat(state.adGroups[0].states[0]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].uris[1]).isSameInstanceAs(TEST_URI);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void setAdErrorBeforeAdCount() {
    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2);

    assertThat(state.adGroups[0].uris[0]).isNull();
    assertThat(state.adGroups[0].states[0]).isEqualTo(AdPlaybackState.AD_STATE_ERROR);
    assertThat(state.isAdInErrorState(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)).isTrue();
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.isAdInErrorState(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1)).isFalse();
  }

  @Test
  public void getFirstAdIndexToPlayIsZero() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(0);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsPlayedAd() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsSkippedAd() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withSkippedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(1);
    assertThat(state.adGroups[0].states[1]).isEqualTo(AdPlaybackState.AD_STATE_UNAVAILABLE);
    assertThat(state.adGroups[0].states[2]).isEqualTo(AdPlaybackState.AD_STATE_AVAILABLE);
  }

  @Test
  public void getFirstAdIndexToPlaySkipsErrorAds() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2, TEST_URI);

    state = state.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);

    assertThat(state.adGroups[0].getFirstAdIndexToPlay()).isEqualTo(2);
  }

  @Test
  public void getNextAdIndexToPlaySkipsErrorAds() {
    state = state.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 3);
    state = state.withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1, TEST_URI);

    state = state.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1);

    assertThat(state.adGroups[0].getNextAdIndexToPlay(0)).isEqualTo(2);
  }

  @Test
  public void setAdStateTwiceThrows() {
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
  public void skipAllWithoutAdCount() {
    state = state.withSkippedAdGroup(0);
    state = state.withSkippedAdGroup(1);
    assertThat(state.adGroups[0].count).isEqualTo(0);
    assertThat(state.adGroups[1].count).isEqualTo(0);
  }

  @Test
  public void roundTripViaBundle_yieldsEqualFieldsExceptAdsId() {
    AdPlaybackState originalState =
        state
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
            .withAdUri(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, TEST_URI)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withSkippedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)
            .withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1)
            .withAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0, TEST_URI)
            .withAdUri(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 1, TEST_URI)
            .withAdDurationsUs(new long[][] {{12}, {34, 56}})
            .withAdResumePositionUs(123)
            .withContentDurationUs(456);

    AdPlaybackState restoredState = AdPlaybackState.CREATOR.fromBundle(originalState.toBundle());

    assertThat(restoredState.adsId).isNull();
    assertThat(restoredState.adGroupCount).isEqualTo(originalState.adGroupCount);
    assertThat(restoredState.adGroupTimesUs).isEqualTo(originalState.adGroupTimesUs);
    assertThat(restoredState.adGroups).isEqualTo(originalState.adGroups);
    assertThat(restoredState.adResumePositionUs).isEqualTo(originalState.adResumePositionUs);
    assertThat(restoredState.contentDurationUs).isEqualTo(originalState.contentDurationUs);
  }

  @Test
  public void roundTripViaBundle_ofAdGroup_yieldsEqualInstance() {
    AdPlaybackState.AdGroup adGroup =
        new AdPlaybackState.AdGroup()
            .withAdCount(2)
            .withAdState(AD_STATE_AVAILABLE, /* index= */ 0)
            .withAdState(AD_STATE_PLAYED, /* index= */ 1)
            .withAdUri(Uri.parse("https://www.google.com"), /* index= */ 0)
            .withAdUri(Uri.EMPTY, /* index= */ 1)
            .withAdDurationsUs(new long[] {1234, 5678});

    assertThat(AdPlaybackState.AdGroup.CREATOR.fromBundle(adGroup.toBundle())).isEqualTo(adGroup);
  }
}
