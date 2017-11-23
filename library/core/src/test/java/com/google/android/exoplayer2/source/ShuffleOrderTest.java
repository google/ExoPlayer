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

import static com.google.android.exoplayer2.C.INDEX_UNSET;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.source.ShuffleOrder.UnshuffledShuffleOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit test for {@link ShuffleOrder}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class ShuffleOrderTest {

  public static final long RANDOM_SEED = 1234567890L;

  @Test
  public void testDefaultShuffleOrder() {
    assertShuffleOrderCorrectness(new DefaultShuffleOrder(0, RANDOM_SEED), 0);
    assertShuffleOrderCorrectness(new DefaultShuffleOrder(1, RANDOM_SEED), 1);
    assertShuffleOrderCorrectness(new DefaultShuffleOrder(5, RANDOM_SEED), 5);
    for (int initialLength = 0; initialLength < 4; initialLength++) {
      for (int insertionPoint = 0; insertionPoint <= initialLength; insertionPoint += 2) {
        testCloneAndInsert(new DefaultShuffleOrder(initialLength, RANDOM_SEED), insertionPoint, 0);
        testCloneAndInsert(new DefaultShuffleOrder(initialLength, RANDOM_SEED), insertionPoint, 1);
        testCloneAndInsert(new DefaultShuffleOrder(initialLength, RANDOM_SEED), insertionPoint, 5);
      }
    }
    testCloneAndRemove(new DefaultShuffleOrder(5, RANDOM_SEED), 0);
    testCloneAndRemove(new DefaultShuffleOrder(5, RANDOM_SEED), 2);
    testCloneAndRemove(new DefaultShuffleOrder(5, RANDOM_SEED), 4);
    testCloneAndRemove(new DefaultShuffleOrder(1, RANDOM_SEED), 0);
  }

  @Test
  public void testUnshuffledShuffleOrder() {
    assertShuffleOrderCorrectness(new UnshuffledShuffleOrder(0), 0);
    assertShuffleOrderCorrectness(new UnshuffledShuffleOrder(1), 1);
    assertShuffleOrderCorrectness(new UnshuffledShuffleOrder(5), 5);
    for (int initialLength = 0; initialLength < 4; initialLength++) {
      for (int insertionPoint = 0; insertionPoint <= initialLength; insertionPoint += 2) {
        testCloneAndInsert(new UnshuffledShuffleOrder(initialLength), insertionPoint, 0);
        testCloneAndInsert(new UnshuffledShuffleOrder(initialLength), insertionPoint, 1);
        testCloneAndInsert(new UnshuffledShuffleOrder(initialLength), insertionPoint, 5);
      }
    }
    testCloneAndRemove(new UnshuffledShuffleOrder(5), 0);
    testCloneAndRemove(new UnshuffledShuffleOrder(5), 2);
    testCloneAndRemove(new UnshuffledShuffleOrder(5), 4);
    testCloneAndRemove(new UnshuffledShuffleOrder(1), 0);
  }

  @Test
  public void testUnshuffledShuffleOrderIsUnshuffled() {
    ShuffleOrder shuffleOrder = new UnshuffledShuffleOrder(5);
    assertThat(shuffleOrder.getFirstIndex()).isEqualTo(0);
    assertThat(shuffleOrder.getLastIndex()).isEqualTo(4);
    for (int i = 0; i < 4; i++) {
      assertThat(shuffleOrder.getNextIndex(i)).isEqualTo(i + 1);
    }
  }

  private static void assertShuffleOrderCorrectness(ShuffleOrder shuffleOrder, int length) {
    assertThat(shuffleOrder.getLength()).isEqualTo(length);
    if (length == 0) {
      assertThat(shuffleOrder.getFirstIndex()).isEqualTo(INDEX_UNSET);
      assertThat(shuffleOrder.getLastIndex()).isEqualTo(INDEX_UNSET);
    } else {
      int[] indices = new int[length];
      indices[0] = shuffleOrder.getFirstIndex();
      assertThat(shuffleOrder.getPreviousIndex(indices[0])).isEqualTo(INDEX_UNSET);
      for (int i = 1; i < length; i++) {
        indices[i] = shuffleOrder.getNextIndex(indices[i - 1]);
        assertThat(shuffleOrder.getPreviousIndex(indices[i])).isEqualTo(indices[i - 1]);
        for (int j = 0; j < i; j++) {
          assertThat(indices[i] != indices[j]).isTrue();
        }
      }
      assertThat(shuffleOrder.getLastIndex()).isEqualTo(indices[length - 1]);
      assertThat(shuffleOrder.getNextIndex(indices[length - 1])).isEqualTo(INDEX_UNSET);
      for (int i = 0; i < length; i++) {
        assertThat(indices[i] >= 0 && indices[i] < length).isTrue();
      }
    }
  }

  private static void testCloneAndInsert(ShuffleOrder shuffleOrder, int position, int count) {
    ShuffleOrder newOrder = shuffleOrder.cloneAndInsert(position, count);
    assertShuffleOrderCorrectness(newOrder, shuffleOrder.getLength() + count);
    // Assert all elements still have the relative same order
    for (int i = 0; i < shuffleOrder.getLength(); i++) {
      int expectedNextIndex = shuffleOrder.getNextIndex(i);
      if (expectedNextIndex != C.INDEX_UNSET && expectedNextIndex >= position) {
        expectedNextIndex += count;
      }
      int newNextIndex = newOrder.getNextIndex(i < position ? i : i + count);
      while (newNextIndex >= position && newNextIndex < position + count) {
        newNextIndex = newOrder.getNextIndex(newNextIndex);
      }
      assertThat(newNextIndex).isEqualTo(expectedNextIndex);
    }
  }

  private static void testCloneAndRemove(ShuffleOrder shuffleOrder, int position) {
    ShuffleOrder newOrder = shuffleOrder.cloneAndRemove(position);
    assertShuffleOrderCorrectness(newOrder, shuffleOrder.getLength() - 1);
    // Assert all elements still have the relative same order
    for (int i = 0; i < shuffleOrder.getLength(); i++) {
      if (i == position) {
        continue;
      }
      int expectedNextIndex = shuffleOrder.getNextIndex(i);
      if (expectedNextIndex == position) {
        expectedNextIndex = shuffleOrder.getNextIndex(expectedNextIndex);
      }
      if (expectedNextIndex != C.INDEX_UNSET && expectedNextIndex >= position) {
        expectedNextIndex--;
      }
      int newNextIndex = newOrder.getNextIndex(i < position ? i : i - 1);
      assertThat(newNextIndex).isEqualTo(expectedNextIndex);
    }
  }

}
