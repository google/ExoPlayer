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
package com.google.android.exoplayer2.source.dash;

import static com.google.android.exoplayer2.source.dash.manifest.BaseUrl.DEFAULT_DVB_PRIORITY;
import static com.google.android.exoplayer2.source.dash.manifest.BaseUrl.DEFAULT_WEIGHT;
import static com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy.DEFAULT_LOCATION_EXCLUSION_MS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.dash.manifest.BaseUrl;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link BaseUrlExclusionList}. */
@RunWith(AndroidJUnit4.class)
public class BaseUrlExclusionListTest {

  @Test
  public void selectBaseUrl_excludeByServiceLocation_excludesAllBaseUrlOfSameServiceLocation() {
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "a", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "c", /* priority= */ 3, /* weight= */ 1));

    baseUrlExclusionList.exclude(baseUrls.get(0), 5000);

    ShadowSystemClock.advanceBy(Duration.ofMillis(4999));
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("c");
    ShadowSystemClock.advanceBy(Duration.ofMillis(1));
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("a");
  }

  @Test
  public void selectBaseUrl_excludeByPriority_excludesAllBaseUrlsOfSamePriority() {
    Random mockRandom = mock(Random.class);
    when(mockRandom.nextInt(anyInt())).thenReturn(0);
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList(mockRandom);
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "b", /* priority= */ 1, /* weight= */ 99),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "c", /* priority= */ 2, /* weight= */ 1));

    baseUrlExclusionList.exclude(baseUrls.get(0), 5000);

    ShadowSystemClock.advanceBy(Duration.ofMillis(4999));
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("c");
    ShadowSystemClock.advanceBy(Duration.ofMillis(1));
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("a");
  }

  @Test
  public void selectBaseUrl_samePriority_choiceIsRandom() {
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 99),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "b", /* priority= */ 1, /* weight= */ 1));
    Random mockRandom = mock(Random.class);
    when(mockRandom.nextInt(anyInt())).thenReturn(99);

    assertThat(new BaseUrlExclusionList(mockRandom).selectBaseUrl(baseUrls))
        .isEqualTo(baseUrls.get(1));

    // Random is used for random choice.
    verify(mockRandom).nextInt(/* bound= */ 100);
    verifyNoMoreInteractions(mockRandom);
  }

  @Test
  public void selectBaseUrl_samePriority_choiceFromSameElementsRandomOnlyOnceSameAfterwards() {
    List<BaseUrl> baseUrlsVideo =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a/v", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 99),
            new BaseUrl(
                /* url= */ "b/v", /* serviceLocation= */ "b", /* priority= */ 1, /* weight= */ 1));
    List<BaseUrl> baseUrlsAudio =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a/a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 99),
            new BaseUrl(
                /* url= */ "b/a", /* serviceLocation= */ "b", /* priority= */ 1, /* weight= */ 1));
    Random mockRandom = mock(Random.class);
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList(mockRandom);
    when(mockRandom.nextInt(anyInt())).thenReturn(99);

    for (int i = 0; i < 5; i++) {
      assertThat(baseUrlExclusionList.selectBaseUrl(baseUrlsVideo).serviceLocation).isEqualTo("b");
      assertThat(baseUrlExclusionList.selectBaseUrl(baseUrlsAudio).serviceLocation).isEqualTo("b");
    }
    // Random is used only once.
    verify(mockRandom).nextInt(/* bound= */ 100);
    verifyNoMoreInteractions(mockRandom);
  }

  @Test
  public void selectBaseUrl_twiceTheSameLocationExcluded_correctExpirationDuration() {
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "a", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "d", /* serviceLocation= */ "d", /* priority= */ 2, /* weight= */ 1));
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();

    // Exclude location 'a'.
    baseUrlExclusionList.exclude(baseUrls.get(0), 5000);
    // Exclude location 'a' which increases exclusion duration of 'a'.
    baseUrlExclusionList.exclude(baseUrls.get(1), 10000);
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls)).isNull();
    ShadowSystemClock.advanceBy(Duration.ofMillis(9999));
    // Location 'a' still excluded.
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls)).isNull();
    ShadowSystemClock.advanceBy(Duration.ofMillis(1));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(2);
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("a");
  }

  @Test
  public void selectBaseUrl_twiceTheSamePriorityExcluded_correctExpirationDuration() {
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "b", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "c", /* priority= */ 2, /* weight= */ 1));
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();

    // Exclude priority 1.
    baseUrlExclusionList.exclude(baseUrls.get(0), 5000);
    // Exclude priority 1 again which increases the exclusion duration.
    baseUrlExclusionList.exclude(baseUrls.get(1), 10000);
    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("c");
    ShadowSystemClock.advanceBy(Duration.ofMillis(9999));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(1);
    ShadowSystemClock.advanceBy(Duration.ofMillis(1));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(2);
  }

  @Test
  public void selectBaseUrl_priorityUnset_isNotExcluded() {
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();
    ImmutableList<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a-1",
                /* serviceLocation= */ "a",
                BaseUrl.PRIORITY_UNSET,
                /* weight= */ 1),
            new BaseUrl(
                /* url= */ "a-2",
                /* serviceLocation= */ "a",
                BaseUrl.PRIORITY_UNSET,
                /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b",
                /* serviceLocation= */ "b",
                BaseUrl.PRIORITY_UNSET,
                /* weight= */ 1));

    baseUrlExclusionList.exclude(baseUrls.get(0), 10_000);

    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).serviceLocation).isEqualTo("b");
  }

  @Test
  public void selectBaseUrl_emptyBaseUrlList_selectionIsNull() {
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();

    assertThat(baseUrlExclusionList.selectBaseUrl(ImmutableList.of())).isNull();
  }

  @Test
  public void reset_dropsAllExclusions() {
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();
    ImmutableList<BaseUrl> baseUrls =
        ImmutableList.of(new BaseUrl("a", "a", DEFAULT_DVB_PRIORITY, DEFAULT_WEIGHT));
    baseUrlExclusionList.exclude(baseUrls.get(0), 5000);

    baseUrlExclusionList.reset();

    assertThat(baseUrlExclusionList.selectBaseUrl(baseUrls).url).isEqualTo("a");
  }

  @Test
  public void getPriorityCountAfterExclusion_correctPriorityCount() {
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "b", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "c", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "d", /* serviceLocation= */ "d", /* priority= */ 3, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "e", /* serviceLocation= */ "e", /* priority= */ 3, /* weight= */ 1));
    BaseUrlExclusionList baseUrlExclusionList = new BaseUrlExclusionList();

    // Empty base URL list.
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(ImmutableList.of()))
        .isEqualTo(0);

    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(3);
    // Exclude base urls.
    baseUrlExclusionList.exclude(baseUrls.get(0), DEFAULT_LOCATION_EXCLUSION_MS);
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(2);
    baseUrlExclusionList.exclude(baseUrls.get(1), 2 * DEFAULT_LOCATION_EXCLUSION_MS);
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(1);
    baseUrlExclusionList.exclude(baseUrls.get(3), 3 * DEFAULT_LOCATION_EXCLUSION_MS);
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(0);
    // Time passes.
    ShadowSystemClock.advanceBy(Duration.ofMillis(DEFAULT_LOCATION_EXCLUSION_MS));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(1);
    ShadowSystemClock.advanceBy(Duration.ofMillis(DEFAULT_LOCATION_EXCLUSION_MS));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(2);
    ShadowSystemClock.advanceBy(Duration.ofMillis(DEFAULT_LOCATION_EXCLUSION_MS));
    assertThat(baseUrlExclusionList.getPriorityCountAfterExclusion(baseUrls)).isEqualTo(3);
  }

  @Test
  public void getPriorityCount_correctPriorityCount() {
    List<BaseUrl> baseUrls =
        ImmutableList.of(
            new BaseUrl(
                /* url= */ "a", /* serviceLocation= */ "a", /* priority= */ 1, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "b", /* serviceLocation= */ "b", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "c", /* serviceLocation= */ "c", /* priority= */ 2, /* weight= */ 1),
            new BaseUrl(
                /* url= */ "d", /* serviceLocation= */ "d", /* priority= */ 3, /* weight= */ 1));

    assertThat(BaseUrlExclusionList.getPriorityCount(baseUrls)).isEqualTo(3);
    assertThat(BaseUrlExclusionList.getPriorityCount(ImmutableList.of())).isEqualTo(0);
  }
}
