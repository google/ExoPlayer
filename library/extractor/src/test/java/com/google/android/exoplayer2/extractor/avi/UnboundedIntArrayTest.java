package com.google.android.exoplayer2.extractor.avi;

import org.junit.Assert;
import org.junit.Test;

public class UnboundedIntArrayTest {
  @Test
  public void add_givenInt() {
    final UnboundedIntArray unboundedIntArray = new UnboundedIntArray();
    unboundedIntArray.add(4);
    Assert.assertEquals(1, unboundedIntArray.getSize());
    Assert.assertEquals(unboundedIntArray.array[0], 4);
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
}
