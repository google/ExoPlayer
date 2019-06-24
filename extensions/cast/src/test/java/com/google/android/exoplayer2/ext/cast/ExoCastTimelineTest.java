/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExoCastTimeline}. */
@RunWith(AndroidJUnit4.class)
public class ExoCastTimelineTest {

  private MediaItem mediaItem1;
  private MediaItem mediaItem2;
  private MediaItem mediaItem3;
  private MediaItem mediaItem4;
  private MediaItem mediaItem5;

  @Before
  public void setUp() {
    MediaItem.Builder builder = new MediaItem.Builder();
    mediaItem1 = builder.setUuid(asUUID(1)).build();
    mediaItem2 = builder.setUuid(asUUID(2)).build();
    mediaItem3 = builder.setUuid(asUUID(3)).build();
    mediaItem4 = builder.setUuid(asUUID(4)).build();
    mediaItem5 = builder.setUuid(asUUID(5)).build();
  }

  @Test
  public void getWindowCount_withNoItems_producesExpectedCount() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Collections.emptyList(), Collections.emptyMap(), new DefaultShuffleOrder(0));

    assertThat(timeline.getWindowCount()).isEqualTo(0);
  }

  @Test
  public void getWindowCount_withFiveItems_producesExpectedCount() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(5));

    assertThat(timeline.getWindowCount()).isEqualTo(5);
  }

  @Test
  public void getWindow_withNoMediaItemInfo_returnsEmptyWindow() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(5));
    Timeline.Window window = timeline.getWindow(2, new Timeline.Window(), /* setTag= */ true);

    assertThat(window.tag).isNull();
    assertThat(window.presentationStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.windowStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.isSeekable).isFalse();
    assertThat(window.isDynamic).isTrue();
    assertThat(window.defaultPositionUs).isEqualTo(0L);
    assertThat(window.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(window.firstPeriodIndex).isEqualTo(2);
    assertThat(window.lastPeriodIndex).isEqualTo(2);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(0L);
  }

  @Test
  public void getWindow_withMediaItemInfo_returnsPopulatedWindow() {
    MediaItem populatedMediaItem = new MediaItem.Builder().setAttachment("attachment").build();
    HashMap<UUID, MediaItemInfo> mediaItemInfos = new HashMap<>();
    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period("id1", /* durationUs= */ 1000000L, /* positionInWindowUs= */ 0L);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 1000000L);
    mediaItemInfos.put(
        populatedMediaItem.uuid,
        new MediaItemInfo(
            /* windowDurationUs= */ 4000000L,
            /* defaultStartPositionUs= */ 20L,
            Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 500L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, populatedMediaItem),
            mediaItemInfos,
            new DefaultShuffleOrder(5));
    Timeline.Window window = timeline.getWindow(4, new Timeline.Window(), /* setTag= */ true);

    assertThat(window.tag).isSameInstanceAs(populatedMediaItem.attachment);
    assertThat(window.presentationStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.windowStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.isSeekable).isTrue();
    assertThat(window.isDynamic).isFalse();
    assertThat(window.defaultPositionUs).isEqualTo(20L);
    assertThat(window.durationUs).isEqualTo(4000000L);
    assertThat(window.firstPeriodIndex).isEqualTo(4);
    assertThat(window.lastPeriodIndex).isEqualTo(5);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(500L);
  }

  @Test
  public void getPeriodCount_producesExpectedOutput() {
    HashMap<UUID, MediaItemInfo> mediaItemInfos = new HashMap<>();
    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period(
            "id1", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 6000000L);
    mediaItemInfos.put(
        asUUID(2),
        new MediaItemInfo(
            /* windowDurationUs= */ 7000000L,
            /* defaultStartPositionUs= */ 20L,
            Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            mediaItemInfos,
            new DefaultShuffleOrder(5));

    assertThat(timeline.getPeriodCount()).isEqualTo(6);
  }

  @Test
  public void getPeriod_forPopulatedPeriod_producesExpectedOutput() {
    HashMap<UUID, MediaItemInfo> mediaItemInfos = new HashMap<>();
    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period(
            "id1", /* durationUs= */ 4000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 6000000L);
    mediaItemInfos.put(
        asUUID(5),
        new MediaItemInfo(
            /* windowDurationUs= */ 7000000L,
            /* defaultStartPositionUs= */ 20L,
            Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            mediaItemInfos,
            new DefaultShuffleOrder(5));
    Timeline.Period period =
        timeline.getPeriod(/* periodIndex= */ 5, new Timeline.Period(), /* setIds= */ true);
    Object periodUid = timeline.getUidOfPeriod(/* periodIndex= */ 5);

    assertThat(period.durationUs).isEqualTo(5000000L);
    assertThat(period.windowIndex).isEqualTo(4);
    assertThat(period.id).isEqualTo("id2");
    assertThat(period.uid).isEqualTo(periodUid);
  }

  @Test
  public void getPeriod_forEmptyPeriod_producesExpectedOutput() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(5));
    Timeline.Period period = timeline.getPeriod(2, new Timeline.Period(), /* setIds= */ true);
    Object uid = timeline.getUidOfPeriod(/* periodIndex= */ 2);

    assertThat(period.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(period.windowIndex).isEqualTo(2);
    assertThat(period.id).isEqualTo(MediaItemInfo.EMPTY.periods.get(0).id);
    assertThat(period.uid).isEqualTo(uid);
  }

  @Test
  public void getIndexOfPeriod_worksAcrossDifferentTimelines() {
    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period(
            "id1", /* durationUs= */ 4000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 1000000L);

    HashMap<UUID, MediaItemInfo> mediaItemInfos1 = new HashMap<>();
    mediaItemInfos1.put(
        asUUID(1),
        new MediaItemInfo(
            /* windowDurationUs= */ 5000000L,
            /* defaultStartPositionUs= */ 20L,
            Collections.singletonList(period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline1 =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2), mediaItemInfos1, new DefaultShuffleOrder(2));

    HashMap<UUID, MediaItemInfo> mediaItemInfos2 = new HashMap<>();
    mediaItemInfos2.put(
        asUUID(1),
        new MediaItemInfo(
            /* windowDurationUs= */ 7000000L,
            /* defaultStartPositionUs= */ 20L,
            Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline2 =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem2, mediaItem1, mediaItem3, mediaItem4, mediaItem5),
            mediaItemInfos2,
            new DefaultShuffleOrder(5));
    Object uidOfFirstPeriod = timeline1.getUidOfPeriod(0);

    assertThat(timeline1.getIndexOfPeriod(uidOfFirstPeriod)).isEqualTo(0);
    assertThat(timeline2.getIndexOfPeriod(uidOfFirstPeriod)).isEqualTo(2);
  }

  @Test
  public void getIndexOfPeriod_forLastPeriod_producesExpectedOutput() {
    MediaItemInfo.Period period1 =
        new MediaItemInfo.Period(
            "id1", /* durationUs= */ 4000000L, /* positionInWindowUs= */ 1000000L);
    MediaItemInfo.Period period2 =
        new MediaItemInfo.Period(
            "id2", /* durationUs= */ 5000000L, /* positionInWindowUs= */ 1000000L);

    HashMap<UUID, MediaItemInfo> mediaItemInfos1 = new HashMap<>();
    mediaItemInfos1.put(
        asUUID(5),
        new MediaItemInfo(
            /* windowDurationUs= */ 4000000L,
            /* defaultStartPositionUs= */ 20L,
            Collections.singletonList(period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline singlePeriodTimeline =
        ExoCastTimeline.createTimelineFor(
            Collections.singletonList(mediaItem5), mediaItemInfos1, new DefaultShuffleOrder(1));
    Object periodUid = singlePeriodTimeline.getUidOfPeriod(0);

    HashMap<UUID, MediaItemInfo> mediaItemInfos2 = new HashMap<>();
    mediaItemInfos2.put(
        asUUID(5),
        new MediaItemInfo(
            /* windowDurationUs= */ 7000000L,
            /* defaultStartPositionUs= */ 20L,
            Arrays.asList(period1, period2),
            /* positionInFirstPeriodUs= */ 0L,
            /* isSeekable= */ true,
            /* isDynamic= */ false));
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            mediaItemInfos2,
            new DefaultShuffleOrder(5));

    assertThat(timeline.getIndexOfPeriod(periodUid)).isEqualTo(5);
  }

  @Test
  public void getUidOfPeriod_withInvalidUid_returnsUnsetIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(/* length= */ 5));

    assertThat(timeline.getIndexOfPeriod(new Object())).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void getFirstWindowIndex_returnsIndexAccordingToShuffleMode() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    assertThat(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false)).isEqualTo(0);
    assertThat(timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true)).isEqualTo(1);
  }

  @Test
  public void getLastWindowIndex_returnsIndexAccordingToShuffleMode() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    assertThat(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ false)).isEqualTo(4);
    assertThat(timeline.getLastWindowIndex(/* shuffleModeEnabled= */ true)).isEqualTo(3);
  }

  @Test
  public void getNextWindowIndex_repeatModeOne_returnsSameIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(5));

    for (int i = 0; i < 5; i++) {
      assertThat(
              timeline.getNextWindowIndex(
                  i, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true))
          .isEqualTo(i);
      assertThat(
              timeline.getNextWindowIndex(
                  i, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false))
          .isEqualTo(i);
    }
  }

  @Test
  public void getNextWindowIndex_onLastIndex_returnsExpectedIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    // Shuffle mode disabled:
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 4, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 4, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false))
        .isEqualTo(0);
    // Shuffle mode enabled:
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 3, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 3, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true))
        .isEqualTo(1);
  }

  @Test
  public void getNextWindowIndex_inMiddleOfQueue_returnsNextIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    // Shuffle mode disabled:
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 2, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false))
        .isEqualTo(3);
    // Shuffle mode enabled:
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 2, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true))
        .isEqualTo(0);
  }

  @Test
  public void getPreviousWindowIndex_repeatModeOne_returnsSameIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    for (int i = 0; i < 5; i++) {
      assertThat(
              timeline.getPreviousWindowIndex(
                  /* windowIndex= */ i, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ true))
          .isEqualTo(i);
      assertThat(
              timeline.getPreviousWindowIndex(
                  /* windowIndex= */ i, Player.REPEAT_MODE_ONE, /* shuffleModeEnabled= */ false))
          .isEqualTo(i);
    }
  }

  @Test
  public void getPreviousWindowIndex_onFirstIndex_returnsExpectedIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    // Shuffle mode disabled:
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 0, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 0, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false))
        .isEqualTo(4);
    // Shuffle mode enabled:
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 1, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true))
        .isEqualTo(C.INDEX_UNSET);
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 1, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ true))
        .isEqualTo(3);
  }

  @Test
  public void getPreviousWindowIndex_inMiddleOfQueue_returnsPreviousIndex() {
    ExoCastTimeline timeline =
        ExoCastTimeline.createTimelineFor(
            Arrays.asList(mediaItem1, mediaItem2, mediaItem3, mediaItem4, mediaItem5),
            Collections.emptyMap(),
            new DefaultShuffleOrder(new int[] {1, 2, 0, 4, 3}, /* randomSeed= */ 0));

    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 4, Player.REPEAT_MODE_ALL, /* shuffleModeEnabled= */ false))
        .isEqualTo(3);
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ 4, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true))
        .isEqualTo(0);
  }

  private static UUID asUUID(long number) {
    return new UUID(/* mostSigBits= */ 0L, number);
  }
}
