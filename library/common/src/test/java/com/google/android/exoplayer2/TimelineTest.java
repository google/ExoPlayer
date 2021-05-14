/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem.LiveConfiguration;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Timeline}. */
@RunWith(AndroidJUnit4.class)
public class TimelineTest {

  @Test
  public void emptyTimeline() {
    TimelineAsserts.assertEmpty(Timeline.EMPTY);
  }

  @Test
  public void singlePeriodTimeline() {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(1, 111));
    TimelineAsserts.assertWindowTags(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
  }

  @Test
  public void multiPeriodTimeline() {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(5, 111));
    TimelineAsserts.assertWindowTags(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 5);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
  }

  @Test
  public void windowEquals() {
    MediaItem mediaItem = new MediaItem.Builder().setUri("uri").setTag(new Object()).build();
    Timeline.Window window = new Timeline.Window();
    assertThat(window).isEqualTo(new Timeline.Window());

    Timeline.Window otherWindow = new Timeline.Window();
    otherWindow.mediaItem = mediaItem;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.manifest = new Object();
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.presentationStartTimeMs = C.TIME_UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.windowStartTimeMs = C.TIME_UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.isSeekable = true;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.isDynamic = true;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.liveConfiguration = LiveConfiguration.UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.isPlaceholder = true;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.defaultPositionUs = C.TIME_UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.durationUs = C.TIME_UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.firstPeriodIndex = 1;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.lastPeriodIndex = 1;
    assertThat(window).isNotEqualTo(otherWindow);

    otherWindow = new Timeline.Window();
    otherWindow.positionInFirstPeriodUs = C.TIME_UNSET;
    assertThat(window).isNotEqualTo(otherWindow);

    window = populateWindow(mediaItem, mediaItem.playbackProperties.tag);
    otherWindow =
        otherWindow.set(
            window.uid,
            window.mediaItem,
            window.manifest,
            window.presentationStartTimeMs,
            window.windowStartTimeMs,
            window.elapsedRealtimeEpochOffsetMs,
            window.isSeekable,
            window.isDynamic,
            window.liveConfiguration,
            window.defaultPositionUs,
            window.durationUs,
            window.firstPeriodIndex,
            window.lastPeriodIndex,
            window.positionInFirstPeriodUs);
    assertThat(window).isEqualTo(otherWindow);
  }

  @Test
  public void windowHashCode() {
    Timeline.Window window = new Timeline.Window();
    Timeline.Window otherWindow = new Timeline.Window();
    assertThat(window.hashCode()).isEqualTo(otherWindow.hashCode());

    window.mediaItem = new MediaItem.Builder().setMediaId("mediaId").setTag(new Object()).build();
    assertThat(window.hashCode()).isNotEqualTo(otherWindow.hashCode());
    otherWindow.mediaItem = window.mediaItem;
    assertThat(window.hashCode()).isEqualTo(otherWindow.hashCode());
  }

  @Test
  public void periodEquals() {
    Timeline.Period period = new Timeline.Period();
    assertThat(period).isEqualTo(new Timeline.Period());

    Timeline.Period otherPeriod = new Timeline.Period();
    otherPeriod.id = new Object();
    assertThat(period).isNotEqualTo(otherPeriod);

    otherPeriod = new Timeline.Period();
    otherPeriod.uid = new Object();
    assertThat(period).isNotEqualTo(otherPeriod);

    otherPeriod = new Timeline.Period();
    otherPeriod.windowIndex = 12;
    assertThat(period).isNotEqualTo(otherPeriod);

    otherPeriod = new Timeline.Period();
    otherPeriod.durationUs = 11L;
    assertThat(period).isNotEqualTo(otherPeriod);

    otherPeriod = new Timeline.Period();
    otherPeriod.isPlaceholder = true;
    assertThat(period).isNotEqualTo(otherPeriod);

    otherPeriod = new Timeline.Period();
    period.id = new Object();
    period.uid = new Object();
    period.windowIndex = 1;
    period.durationUs = 123L;
    period.isPlaceholder = true;
    otherPeriod =
        otherPeriod.set(
            period.id,
            period.uid,
            period.windowIndex,
            period.durationUs,
            /* positionInWindowUs= */ 0);
    otherPeriod.isPlaceholder = true;
    assertThat(period).isEqualTo(otherPeriod);
  }

  @Test
  public void periodHashCode() {
    Timeline.Period period = new Timeline.Period();
    Timeline.Period otherPeriod = new Timeline.Period();
    assertThat(period.hashCode()).isEqualTo(otherPeriod.hashCode());

    period.windowIndex = 12;
    assertThat(period.hashCode()).isNotEqualTo(otherPeriod.hashCode());
    otherPeriod.windowIndex = period.windowIndex;
    assertThat(period.hashCode()).isEqualTo(otherPeriod.hashCode());
  }

  @Test
  public void roundTripViaBundle_ofTimeline_yieldsEqualInstanceExceptIdsAndManifest() {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 2,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 2,
                /* defaultPositionUs= */ 22,
                /* windowOffsetInFirstPeriodUs= */ 222,
                AdPlaybackState.NONE,
                new MediaItem.Builder().setMediaId("mediaId2").build()),
            new TimelineWindowDefinition(
                /* periodCount= */ 3,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 3,
                /* defaultPositionUs= */ 33,
                /* windowOffsetInFirstPeriodUs= */ 333,
                AdPlaybackState.NONE,
                new MediaItem.Builder().setMediaId("mediaId3").build()));

    Timeline restoredTimeline = Timeline.CREATOR.fromBundle(timeline.toBundle());

    TimelineAsserts.assertEqualsExceptIdsAndManifest(
        /* expectedTimeline= */ timeline, /* actualTimeline= */ restoredTimeline);
  }

  @Test
  public void roundTripViaBundle_ofTimeline_preservesWindowIndices() {
    int windowCount = 10;
    FakeTimeline timeline = new FakeTimeline(windowCount);

    Timeline restoredTimeline = Timeline.CREATOR.fromBundle(timeline.toBundle());

    assertThat(restoredTimeline.getLastWindowIndex(/* shuffleModeEnabled= */ false))
        .isEqualTo(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ false));
    assertThat(restoredTimeline.getLastWindowIndex(/* shuffleModeEnabled= */ true))
        .isEqualTo(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ true));
    assertThat(restoredTimeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false))
        .isEqualTo(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false));
    assertThat(restoredTimeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true))
        .isEqualTo(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true));
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true);
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualNextWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false);
    TimelineAsserts.assertEqualPreviousWindowIndices(
        timeline, restoredTimeline, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true);
  }

  @Test
  public void roundTripViaBundle_ofEmptyTimeline_returnsEmptyTimeline() {
    TimelineAsserts.assertEmpty(Timeline.CREATOR.fromBundle(Timeline.EMPTY.toBundle()));
  }

  @Test
  public void roundTripViaBundle_ofWindow_yieldsEqualInstanceExceptUidAndManifest() {
    Timeline.Window window = new Timeline.Window();
    window.uid = new Object();
    window.mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    window.manifest = new Object();
    window.presentationStartTimeMs = 111;
    window.windowStartTimeMs = 222;
    window.elapsedRealtimeEpochOffsetMs = 333;
    window.isSeekable = true;
    window.isDynamic = true;
    window.liveConfiguration =
        new LiveConfiguration(
            /* targetOffsetMs= */ 1,
            /* minOffsetMs= */ 2,
            /* maxOffsetMs= */ 3,
            /* minPlaybackSpeed= */ 0.5f,
            /* maxPlaybackSpeed= */ 1.5f);
    window.isPlaceholder = true;
    window.defaultPositionUs = 444;
    window.durationUs = 555;
    window.firstPeriodIndex = 6;
    window.lastPeriodIndex = 7;
    window.positionInFirstPeriodUs = 888;

    Timeline.Window restoredWindow = Timeline.Window.CREATOR.fromBundle(window.toBundle());

    assertThat(restoredWindow.manifest).isNull();
    TimelineAsserts.assertWindowEqualsExceptUidAndManifest(
        /* expectedWindow= */ window, /* actualWindow= */ restoredWindow);
  }

  @Test
  public void roundTripViaBundle_ofPeriod_yieldsEqualInstanceExceptIds() {
    Timeline.Period period = new Timeline.Period();
    period.id = new Object();
    period.uid = new Object();
    period.windowIndex = 1;
    period.durationUs = 123_000;
    period.positionInWindowUs = 4_000;
    period.isPlaceholder = true;

    Timeline.Period restoredPeriod = Timeline.Period.CREATOR.fromBundle(period.toBundle());

    assertThat(restoredPeriod.id).isNull();
    assertThat(restoredPeriod.uid).isNull();
    TimelineAsserts.assertPeriodEqualsExceptIds(
        /* expectedPeriod= */ period, /* actualPeriod= */ restoredPeriod);
  }

  @SuppressWarnings("deprecation") // Populates the deprecated window.tag property.
  private static Timeline.Window populateWindow(
      @Nullable MediaItem mediaItem, @Nullable Object tag) {
    Timeline.Window window = new Timeline.Window();
    window.uid = new Object();
    window.tag = tag;
    window.mediaItem = mediaItem;
    window.manifest = new Object();
    window.presentationStartTimeMs = C.TIME_UNSET;
    window.windowStartTimeMs = C.TIME_UNSET;
    window.isSeekable = true;
    window.isDynamic = true;
    window.isLive = true;
    window.defaultPositionUs = C.TIME_UNSET;
    window.durationUs = C.TIME_UNSET;
    window.firstPeriodIndex = 1;
    window.lastPeriodIndex = 1;
    window.positionInFirstPeriodUs = C.TIME_UNSET;
    return window;
  }
}
