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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.source.ShuffleOrder.UnshuffledShuffleOrder;
import junit.framework.TestCase;

/**
 * Unit test for {@link ShuffleOrder}.
 */
public final class ShuffleOrderTest extends TestCase {

  public static final long RANDOM_SEED = 1234567890L;

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

  public void testUnshuffledShuffleOrderIsUnshuffled() {
    ShuffleOrder shuffleOrder = new UnshuffledShuffleOrder(5);
    assertEquals(0, shuffleOrder.getFirstIndex());
    assertEquals(4, shuffleOrder.getLastIndex());
    for (int i = 0; i < 4; i++) {
      assertEquals(i + 1, shuffleOrder.getNextIndex(i));
    }
  }

  private static void assertShuffleOrderCorrectness(ShuffleOrder shuffleOrder, int length) {
    assertEquals(length, shuffleOrder.getLength());
    if (length == 0) {
      assertEquals(C.INDEX_UNSET, shuffleOrder.getFirstIndex());
      assertEquals(C.INDEX_UNSET, shuffleOrder.getLastIndex());
    } else {
      int[] indices = new int[length];
      indices[0] = shuffleOrder.getFirstIndex();
      assertEquals(C.INDEX_UNSET, shuffleOrder.getPreviousIndex(indices[0]));
      for (int i = 1; i < length; i++) {
        indices[i] = shuffleOrder.getNextIndex(indices[i - 1]);
        assertEquals(indices[i - 1], shuffleOrder.getPreviousIndex(indices[i]));
        for (int j = 0; j < i; j++) {
          assertTrue(indices[i] != indices[j]);
        }
      }
      assertEquals(indices[length - 1], shuffleOrder.getLastIndex());
      assertEquals(C.INDEX_UNSET, shuffleOrder.getNextIndex(indices[length - 1]));
      for (int i = 0; i < length; i++) {
        assertTrue(indices[i] >= 0 && indices[i] < length);
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
      assertEquals(expectedNextIndex, newNextIndex);
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
      assertEquals(expectedNextIndex, newNextIndex);
    }
  }

}
