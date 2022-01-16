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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SinglePeriodTimeline}. */
@RunWith(AndroidJUnit4.class)
public final class SinglePeriodTimelineTest {

  private Window window;
  private Period period;

  @Before
  public void setUp() throws Exception {
    window = new Window();
    period = new Period();
  }

  @Test
  public void getPeriodPositionDynamicWindowUnknownDuration() {
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ true,
            /* isLive= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    // Should return null with any positive position projection.
    Pair<Object, Long> positionUs =
        timeline.getPeriodPositionUs(
            window,
            period,
            /* windowIndex= */ 0,
            /* windowPositionUs= */ C.TIME_UNSET,
            /* defaultPositionProjectionUs= */ 1);
    assertThat(positionUs).isNull();
    // Should return (0, 0) without a position projection.
    positionUs =
        timeline.getPeriodPositionUs(
            window,
            period,
            /* windowIndex= */ 0,
            /* windowPositionUs= */ C.TIME_UNSET,
            /* defaultPositionProjectionUs= */ 0);
    assertThat(positionUs.first).isEqualTo(timeline.getUidOfPeriod(0));
    assertThat(positionUs.second).isEqualTo(0);
  }

  @Test
  public void getPeriodPositionDynamicWindowKnownDuration() {
    long windowDurationUs = 1000;
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            windowDurationUs,
            windowDurationUs,
            /* windowPositionInPeriodUs= */ 0,
            /* windowDefaultStartPositionUs= */ 0,
            /* isSeekable= */ false,
            /* isDynamic= */ true,
            /* useLiveConfiguration= */ true,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    // Should return null with a positive position projection beyond window duration.
    Pair<Object, Long> positionUs =
        timeline.getPeriodPositionUs(
            window,
            period,
            /* windowIndex= */ 0,
            /* windowPositionUs= */ C.TIME_UNSET,
            /* defaultPositionProjectionUs= */ windowDurationUs + 1);
    assertThat(positionUs).isNull();
    // Should return (0, duration) with a projection equal to window duration.
    positionUs =
        timeline.getPeriodPositionUs(
            window,
            period,
            /* windowIndex= */ 0,
            /* windowPositionUs= */ C.TIME_UNSET,
            /* defaultPositionProjectionUs= */ windowDurationUs - 1);
    assertThat(positionUs.first).isEqualTo(timeline.getUidOfPeriod(0));
    assertThat(positionUs.second).isEqualTo(windowDurationUs - 1);
    // Should return (0, 0) without a position projection.
    positionUs =
        timeline.getPeriodPositionUs(
            window,
            period,
            /* windowIndex= */ 0,
            /* windowPositionUs= */ C.TIME_UNSET,
            /* defaultPositionProjectionUs= */ 0);
    assertThat(positionUs.first).isEqualTo(timeline.getUidOfPeriod(0));
    assertThat(positionUs.second).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated Window.tag is still populated correctly.
  public void setNullTag_returnsNullTag_butUsesDefaultUid() {
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* manifest= */ null,
            new MediaItem.Builder().setUri(Uri.EMPTY).setTag(null).build());

    assertThat(timeline.getWindow(/* windowIndex= */ 0, window).tag).isNull();
    assertThat(timeline.getWindow(/* windowIndex= */ 0, window).mediaItem.localConfiguration.tag)
        .isNull();
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ false).id).isNull();
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ true).id).isNull();
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ false).uid).isNull();
    assertThat(timeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ true).uid)
        .isNotNull();
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated Window.tag is still populated correctly.
  public void getWindow_setsTag() {
    Object tag = new Object();
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* manifest= */ null,
            new MediaItem.Builder().setUri(Uri.EMPTY).setTag(tag).build());

    assertThat(timeline.getWindow(/* windowIndex= */ 0, window).tag).isEqualTo(tag);
  }

  // Tests backward compatibility.
  @SuppressWarnings("deprecation")
  @Test
  public void getWindow_setsMediaItemAndTag() {
    MediaItem mediaItem = new MediaItem.Builder().setUri(Uri.EMPTY).setTag(new Object()).build();
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* manifest= */ null,
            mediaItem);

    Window window = timeline.getWindow(/* windowIndex= */ 0, this.window);

    assertThat(window.mediaItem).isEqualTo(mediaItem);
    assertThat(window.tag).isEqualTo(mediaItem.localConfiguration.tag);
  }

  @Test
  public void getIndexOfPeriod_returnsPeriod() {
    SinglePeriodTimeline timeline =
        new SinglePeriodTimeline(
            /* durationUs= */ C.TIME_UNSET,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* manifest= */ null,
            MediaItem.fromUri(Uri.EMPTY));
    Object uid = timeline.getPeriod(/* periodIndex= */ 0, period, /* setIds= */ true).uid;

    assertThat(timeline.getIndexOfPeriod(uid)).isEqualTo(0);
    assertThat(timeline.getIndexOfPeriod(/* uid= */ null)).isEqualTo(C.INDEX_UNSET);
    assertThat(timeline.getIndexOfPeriod(/* uid= */ new Object())).isEqualTo(C.INDEX_UNSET);
  }
}
