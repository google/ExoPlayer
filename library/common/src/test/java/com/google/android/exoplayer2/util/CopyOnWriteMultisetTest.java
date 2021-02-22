/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Iterator;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CopyOnWriteMultiset}. */
@RunWith(AndroidJUnit4.class)
public final class CopyOnWriteMultisetTest {

  @Test
  public void multipleEqualObjectsCountedAsExpected() {
    String item1 = "a string";
    String item2 = "a string";
    String item3 = "different string";

    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();

    multiset.add(item1);
    multiset.add(item2);
    multiset.add(item3);

    assertThat(multiset).containsExactly("a string", "a string", "different string");
    assertThat(multiset.elementSet()).containsExactly("a string", "different string");
  }

  @Test
  public void removingObjectDecrementsCount() {
    String item1 = "a string";
    String item2 = "a string";
    String item3 = "different string";

    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();

    multiset.add(item1);
    multiset.add(item2);
    multiset.add(item3);

    multiset.remove("a string");

    assertThat(multiset).containsExactly("a string", "different string");
    assertThat(multiset.elementSet()).containsExactly("a string", "different string");
  }

  @Test
  public void removingLastObjectRemovesCompletely() {
    String item1 = "a string";
    String item2 = "a string";
    String item3 = "different string";

    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();

    multiset.add(item1);
    multiset.add(item2);
    multiset.add(item3);

    multiset.remove("different string");

    assertThat(multiset).containsExactly("a string", "a string");
    assertThat(multiset.elementSet()).containsExactly("a string");
  }

  @Test
  public void removingNonexistentElementSucceeds() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();

    multiset.remove("a string");
  }

  @Test
  public void modifyingIteratorFails() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();
    multiset.add("a string");

    Iterator<String> iterator = multiset.iterator();

    assertThrows(UnsupportedOperationException.class, iterator::remove);
  }

  @Test
  public void modifyingElementSetFails() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();
    multiset.add("a string");

    Set<String> elementSet = multiset.elementSet();

    assertThrows(UnsupportedOperationException.class, () -> elementSet.remove("a string"));
  }

  @Test
  public void count() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();
    multiset.add("a string");
    multiset.add("a string");

    assertThat(multiset.count("a string")).isEqualTo(2);
    assertThat(multiset.count("another string")).isEqualTo(0);
  }

  @Test
  public void modifyingWhileIteratingElements_succeeds() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();
    multiset.add("a string");
    multiset.add("a string");
    multiset.add("another string");

    // A traditional collection would throw a ConcurrentModificationException here.
    for (String element : multiset) {
      multiset.remove(element);
    }

    assertThat(multiset).isEmpty();
  }

  @Test
  public void modifyingWhileIteratingElementSet_succeeds() {
    CopyOnWriteMultiset<String> multiset = new CopyOnWriteMultiset<>();
    multiset.add("a string");
    multiset.add("a string");
    multiset.add("another string");

    // A traditional collection would throw a ConcurrentModificationException here.
    for (String element : multiset.elementSet()) {
      multiset.remove(element);
    }

    assertThat(multiset).containsExactly("a string");
  }
}
