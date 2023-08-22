/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.NoSuchElementException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link LongArrayQueue}. */
@RunWith(AndroidJUnit4.class)
public class LongArrayQueueTest {

  @Test
  public void capacity() {
    LongArrayQueue queue = new LongArrayQueue(2);

    assertThat(queue.capacity()).isEqualTo(2);
  }

  @Test
  public void capacity_setTo0_increasedTo1() {
    LongArrayQueue queue = new LongArrayQueue(0);

    assertThat(queue.capacity()).isEqualTo(1);
  }

  @Test
  public void capacity_setToNextPowerOf2() {
    LongArrayQueue queue = new LongArrayQueue(6);

    assertThat(queue.capacity()).isEqualTo(8);
  }

  @Test
  public void capacity_invalidMinCapacity_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LongArrayQueue(-1));
  }

  @Test
  public void add_beyondInitialCapacity_doublesCapacity() {
    LongArrayQueue queue = new LongArrayQueue(2);

    queue.add(0);
    queue.add(1);
    queue.add(2);

    assertThat(queue.size()).isEqualTo(3);
    assertThat(queue.capacity()).isEqualTo(4);
  }

  @Test
  public void isEmpty_afterConstruction_returnsTrue() {
    LongArrayQueue queue = new LongArrayQueue();

    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  public void isEmpty_afterAddition_returnsFalse() {
    LongArrayQueue queue = new LongArrayQueue();

    queue.add(0);

    assertThat(queue.isEmpty()).isFalse();
  }

  @Test
  public void isEmpty_afterRemoval_returnsTrue() {
    LongArrayQueue queue = new LongArrayQueue();

    queue.add(0);
    queue.remove();

    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  public void remove_onEmptyQueue_throwsException() {
    LongArrayQueue queue = new LongArrayQueue();

    assertThrows(NoSuchElementException.class, queue::remove);
  }

  @Test
  public void remove_returnsCorrectItem() {
    LongArrayQueue queue = new LongArrayQueue();

    queue.add(20);

    assertThat(queue.remove()).isEqualTo(20);
  }

  @Test
  public void element_withEmptyQueue_throws() {
    LongArrayQueue queue = new LongArrayQueue();

    assertThrows(NoSuchElementException.class, queue::element);
  }

  @Test
  public void element_returnsQueueHead() {
    LongArrayQueue queue = new LongArrayQueue();

    queue.add(5);

    assertThat(queue.element()).isEqualTo(5);
  }

  @Test
  public void clear_resetsQueue() {
    LongArrayQueue queue = new LongArrayQueue();

    queue.add(123);
    queue.clear();

    assertThat(queue.size()).isEqualTo(0);
    assertThat(queue.isEmpty()).isTrue();
  }
}
