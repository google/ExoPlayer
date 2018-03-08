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

import com.google.android.exoplayer2.DefaultLoadControl.Builder;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit test for {@link DefaultLoadControl#shouldContinueLoading(long, float)}. */
@RunWith(RobolectricTestRunner.class)
public class DefaultLoadControlShouldContinueLoadingTest {

  private static final int PLAYBACK_SPEED = 1;
  private static final int MIN_BUFFER_MS = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
  private static final int MAX_BUFFER_MS = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
  private static final int TARGET_BUFFER_BYTES = C.DEFAULT_BUFFER_SEGMENT_SIZE * 2;
  private Builder builder;
  private DefaultAllocator allocator;
  private DefaultLoadControl defaultLoadControl;

  @Before
  public void setUp() throws Exception {
    builder = new Builder();
    allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
  }

  @Test
  public void testReturnsTrueUntilMaximumBufferExceeded() throws Exception {
    createDefaultLoadControl();
    assertThatShouldContinueLoadingReturnsTrue(0);
    assertThatShouldContinueLoadingReturnsTrue(MIN_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsTrue(MAX_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsFalse(MAX_BUFFER_MS + 1);
  }

  @Test
  public void testReturnsFalseUntilFallBelowMinimumBufferOnceBufferingStopped() throws Exception {
    createDefaultLoadControl();
    assertThatShouldContinueLoadingReturnsFalse(MAX_BUFFER_MS + 1);
    assertThatShouldContinueLoadingReturnsFalse(MAX_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsFalse(MIN_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsTrue(MIN_BUFFER_MS - 1);
  }

  @Test
  public void testReturnsTrueUntilMinimumBufferReachedAlthoughTargetBufferBytesReached()
      throws Exception {
    createDefaultLoadControl();
    makeSureTargetBufferBytesReached();

    assertThatShouldContinueLoadingReturnsTrue(0);
    assertThatShouldContinueLoadingReturnsTrue(MIN_BUFFER_MS - 1);
    assertThatShouldContinueLoadingReturnsFalse(MIN_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsFalse(MAX_BUFFER_MS + 1);
  }

  @Test
  public void testAlwaysReturnsFalseIfMaximumBufferReachedAndNotPrioritizeTimeOverSizeThresholds()
      throws Exception {
    builder.setPrioritizeTimeOverSizeThresholds(false);
    createDefaultLoadControl();
    // Put defaultLoadControl in buffering state.
    assertThatShouldContinueLoadingReturnsTrue(0);
    makeSureTargetBufferBytesReached();

    assertThatShouldContinueLoadingReturnsFalse(0);
    assertThatShouldContinueLoadingReturnsFalse(MIN_BUFFER_MS);
    assertThatShouldContinueLoadingReturnsFalse(MAX_BUFFER_MS + 1);
  }

  private void createDefaultLoadControl() {
    builder.setAllocator(allocator);
    builder.setTargetBufferBytes(TARGET_BUFFER_BYTES);
    defaultLoadControl = builder.createDefaultLoadControl();
    defaultLoadControl.onTracksSelected(new Renderer[0], null, null);
  }

  private void makeSureTargetBufferBytesReached() {
    while (allocator.getTotalBytesAllocated() < TARGET_BUFFER_BYTES) {
      allocator.allocate();
    }
  }

  private void assertThatShouldContinueLoadingReturnsFalse(int bufferedDurationMs) {
    assertThat(defaultLoadControl.shouldContinueLoading(bufferedDurationMs * 1000, PLAYBACK_SPEED))
        .isFalse();
  }

  private void assertThatShouldContinueLoadingReturnsTrue(int bufferedDurationMs) {
    assertThat(defaultLoadControl.shouldContinueLoading(bufferedDurationMs * 1000, PLAYBACK_SPEED))
        .isTrue();
  }
}
