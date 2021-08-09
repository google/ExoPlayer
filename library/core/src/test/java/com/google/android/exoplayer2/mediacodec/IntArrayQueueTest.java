/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.NoSuchElementException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link IntArrayQueue}. */
@RunWith(AndroidJUnit4.class)
public class IntArrayQueueTest {

  @Test
  public void add_willDoubleCapacity() {
    IntArrayQueue queue = new IntArrayQueue();
    int capacity = queue.capacity();

    for (int i = 0; i <= capacity; i++) {
      queue.add(i);
    }

    assertThat(queue.capacity()).isEqualTo(2 * capacity);
    assertThat(queue.size()).isEqualTo(capacity + 1);
  }

  @Test
  public void isEmpty_returnsTrueAfterConstruction() {
    IntArrayQueue queue = new IntArrayQueue();

    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  public void isEmpty_returnsFalseAfterAddition() {
    IntArrayQueue queue = new IntArrayQueue();
    queue.add(0);

    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  public void isEmpty_returnsFalseAfterRemoval() {
    IntArrayQueue queue = new IntArrayQueue();
    queue.add(0);
    queue.remove();

    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  public void remove_onEmptyQueue_throwsException() {
    IntArrayQueue queue = new IntArrayQueue();

    try {
      queue.remove();
      fail();
    } catch (NoSuchElementException expected) {
      // expected
    }
  }

  @Test
  public void remove_returnsCorrectItem() {
    IntArrayQueue queue = new IntArrayQueue();
    int value = 20;
    queue.add(value);

    assertThat(queue.remove()).isEqualTo(value);
  }

  @Test
  public void remove_untilIsEmpty() {
    IntArrayQueue queue = new IntArrayQueue();
    for (int i = 0; i < 1024; i++) {
      queue.add(i);
    }

    int expectedRemoved = 0;
    while (!queue.isEmpty()) {
      if (expectedRemoved == 15) {
        System.out.println("foo");
      }
      int removed = queue.remove();
      assertThat(removed).isEqualTo(expectedRemoved++);
    }
  }

  @Test
  public void remove_withResize_returnsCorrectItem() {
    IntArrayQueue queue = new IntArrayQueue();
    int nextToAdd = 0;

    while (queue.size() < queue.capacity()) {
      queue.add(nextToAdd++);
    }

    queue.remove();
    queue.remove();

    // This will force the queue to wrap-around and then resize
    int howManyToResize = queue.capacity() - queue.size() + 1;
    for (int i = 0; i < howManyToResize; i++) {
      queue.add(nextToAdd++);
    }

    assertThat(queue.remove()).isEqualTo(2);
  }

  @Test
  public void clear_resetsQueue() {
    IntArrayQueue queue = new IntArrayQueue();

    // Add items until array re-sizes twice (capacity grows by 4)
    for (int i = 0; i < 1024; i++) {
      queue.add(i);
    }

    queue.clear();

    assertThat(queue.size()).isEqualTo(0);
    assertThat(queue.isEmpty()).isTrue();
  }
}
