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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.DefaultLoadControl.Builder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DefaultLoadControl}. */
@RunWith(AndroidJUnit4.class)
public class DefaultLoadControlTest {

  private static final float SPEED = 1f;
  private static final long MAX_BUFFER_US = C.msToUs(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS);
  private static final long MIN_BUFFER_US = MAX_BUFFER_US / 2;
  private static final int TARGET_BUFFER_BYTES = C.DEFAULT_BUFFER_SEGMENT_SIZE * 2;

  private Builder builder;
  private DefaultAllocator allocator;
  private DefaultLoadControl loadControl;

  @Before
  public void setUp() throws Exception {
    builder = new Builder();
    allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
  }

  @Test
  public void shouldContinueLoading_untilMaxBufferExceeded() {
    createDefaultLoadControl();

    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, /* bufferedDurationUs= */ 0, SPEED))
        .isTrue();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MAX_BUFFER_US - 1, SPEED))
        .isTrue();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MAX_BUFFER_US, SPEED))
        .isFalse();
  }

  @Test
  public void shouldNotContinueLoadingOnceBufferingStopped_untilBelowMinBuffer() {
    builder.setBufferDurationsMs(
        /* minBufferMs= */ (int) C.usToMs(MIN_BUFFER_US),
        /* maxBufferMs= */ (int) C.usToMs(MAX_BUFFER_US),
        /* bufferForPlaybackMs= */ 0,
        /* bufferForPlaybackAfterRebufferMs= */ 0);
    createDefaultLoadControl();

    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MAX_BUFFER_US, SPEED))
        .isFalse();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MAX_BUFFER_US - 1, SPEED))
        .isFalse();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MIN_BUFFER_US, SPEED))
        .isFalse();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MIN_BUFFER_US - 1, SPEED))
        .isTrue();
  }

  @Test
  public void continueLoadingOnceBufferingStopped_andBufferAlmostEmpty_evenIfMinBufferNotReached() {
    builder.setBufferDurationsMs(
        /* minBufferMs= */ 0,
        /* maxBufferMs= */ (int) C.usToMs(MAX_BUFFER_US),
        /* bufferForPlaybackMs= */ 0,
        /* bufferForPlaybackAfterRebufferMs= */ 0);
    createDefaultLoadControl();

    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MAX_BUFFER_US, SPEED))
        .isFalse();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, 5 * C.MICROS_PER_SECOND, SPEED))
        .isFalse();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, 500L, SPEED))
        .isTrue();
  }

  @Test
  public void shouldContinueLoadingWithTargetBufferBytesReached_untilMinBufferReached() {
    builder.setPrioritizeTimeOverSizeThresholds(true);
    builder.setBufferDurationsMs(
        /* minBufferMs= */ (int) C.usToMs(MIN_BUFFER_US),
        /* maxBufferMs= */ (int) C.usToMs(MAX_BUFFER_US),
        /* bufferForPlaybackMs= */ 0,
        /* bufferForPlaybackAfterRebufferMs= */ 0);
    createDefaultLoadControl();
    makeSureTargetBufferBytesReached();

    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, /* bufferedDurationUs= */ 0, SPEED))
        .isTrue();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MIN_BUFFER_US - 1, SPEED))
        .isTrue();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MIN_BUFFER_US, SPEED))
        .isFalse();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MAX_BUFFER_US, SPEED))
        .isFalse();
  }

  @Test
  public void
      shouldContinueLoading_withTargetBufferBytesReachedAndNotPrioritizeTimeOverSize_returnsTrueAsSoonAsTargetBufferReached() {
    builder.setPrioritizeTimeOverSizeThresholds(false);
    createDefaultLoadControl();

    // Put loadControl in buffering state.
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, /* bufferedDurationUs= */ 0, SPEED))
        .isTrue();
    makeSureTargetBufferBytesReached();

    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, /* bufferedDurationUs= */ 0, SPEED))
        .isFalse();
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MIN_BUFFER_US - 1, SPEED))
        .isFalse();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MIN_BUFFER_US, SPEED))
        .isFalse();
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MAX_BUFFER_US, SPEED))
        .isFalse();
  }

  @Test
  public void shouldContinueLoadingWithMinBufferReached_inFastPlayback() {
    builder.setBufferDurationsMs(
        /* minBufferMs= */ (int) C.usToMs(MIN_BUFFER_US),
        /* maxBufferMs= */ (int) C.usToMs(MAX_BUFFER_US),
        /* bufferForPlaybackMs= */ 0,
        /* bufferForPlaybackAfterRebufferMs= */ 0);
    createDefaultLoadControl();

    // At normal playback speed, we stop buffering when the buffer reaches the minimum.
    assertThat(loadControl.shouldContinueLoading(/* playbackPositionUs= */ 0, MIN_BUFFER_US, SPEED))
        .isFalse();
    // At double playback speed, we continue loading.
    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MIN_BUFFER_US, /* playbackSpeed= */ 2f))
        .isTrue();
  }

  @Test
  public void shouldNotContinueLoadingWithMaxBufferReached_inFastPlayback() {
    createDefaultLoadControl();

    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, MAX_BUFFER_US, /* playbackSpeed= */ 100f))
        .isFalse();
  }

  @Test
  public void startsPlayback_whenMinBufferSizeReached() {
    createDefaultLoadControl();

    assertThat(loadControl.shouldStartPlayback(MIN_BUFFER_US, SPEED, /* rebuffering= */ false))
        .isTrue();
  }

  @Test
  public void shouldContinueLoading_withNoSelectedTracks_returnsTrue() {
    loadControl = builder.createDefaultLoadControl();
    loadControl.onTracksSelected(new Renderer[0], TrackGroupArray.EMPTY, new TrackSelectionArray());

    assertThat(
            loadControl.shouldContinueLoading(
                /* playbackPositionUs= */ 0, /* bufferedDurationUs= */ 0, /* playbackSpeed= */ 1f))
        .isTrue();
  }

  private void createDefaultLoadControl() {
    builder.setAllocator(allocator).setTargetBufferBytes(TARGET_BUFFER_BYTES);
    loadControl = builder.createDefaultLoadControl();
    loadControl.onTracksSelected(new Renderer[0], null, null);
  }

  private void makeSureTargetBufferBytesReached() {
    while (allocator.getTotalBytesAllocated() < TARGET_BUFFER_BYTES) {
      allocator.allocate();
    }
  }

}
