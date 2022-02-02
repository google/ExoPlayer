/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

public class UnboundedIntArrayTest {
  @Test
  public void add_givenInt() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray();
    unboundedIntArray.add(4);
    Assert.assertEquals(1, unboundedIntArray.getSize());
    Assert.assertEquals(unboundedIntArray.getArray()[0], 4);
  }

  @Test
  public void indexOf_givenOrderSet() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray();
    unboundedIntArray.add(2);
    unboundedIntArray.add(4);
    unboundedIntArray.add(5);
    unboundedIntArray.add(8);
    Assert.assertEquals(2, unboundedIntArray.indexOf(5));
    Assert.assertTrue(unboundedIntArray.indexOf(6) < 0);
  }

  @Test
  public void grow_givenSizeOfOne() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray(1);
    unboundedIntArray.add(0);
    Assert.assertEquals(1, unboundedIntArray.getSize());
    unboundedIntArray.add(1);
    Assert.assertTrue(unboundedIntArray.getSize() > 1);
  }

  @Test
  public void pack_givenSizeOfOne() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray(8);
    unboundedIntArray.add(1);
    unboundedIntArray.add(2);
    Assert.assertEquals(8, unboundedIntArray.array.length);
    unboundedIntArray.pack();
    Assert.assertEquals(2, unboundedIntArray.array.length);
  }

  @Test
  public void illegalArgument_givenNegativeSize() {
    try {
      new UnboundedIntArray(-1);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      //Intentionally blank
    }
  }

  @Test
  public void get_givenValidIndex() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray(4);
    unboundedIntArray.add(1);
    unboundedIntArray.add(2);
    Assert.assertEquals(1, unboundedIntArray.get(0));
  }

  @Test
  public void get_givenOutOfBounds() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray(4);
    try {
      unboundedIntArray.get(0);
      Assert.fail();
    } catch (ArrayIndexOutOfBoundsException e) {
      //Intentionally blank
    }
  }
}
