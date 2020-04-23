/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Util.binarySearchCeil;
import static com.google.android.exoplayer2.util.Util.binarySearchFloor;
import static com.google.android.exoplayer2.util.Util.escapeFileName;
import static com.google.android.exoplayer2.util.Util.getCodecsOfType;
import static com.google.android.exoplayer2.util.Util.parseXsDateTime;
import static com.google.android.exoplayer2.util.Util.parseXsDuration;
import static com.google.android.exoplayer2.util.Util.unescapeFileName;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link Util}. */
@RunWith(AndroidJUnit4.class)
public class UtilTest {

  @Test
  public void addWithOverflowDefault_withoutOverFlow_returnsSum() {
    long res = Util.addWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(15);

    res = Util.addWithOverflowDefault(Long.MAX_VALUE - 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE + 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void addWithOverflowDefault_withOverFlow_returnsOverflowDefault() {
    long res = Util.addWithOverflowDefault(Long.MAX_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.addWithOverflowDefault(Long.MIN_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void subtrackWithOverflowDefault_withoutUnderflow_returnsSubtract() {
    long res = Util.subtractWithOverflowDefault(5, 10, /* overflowResult= */ 0);
    assertThat(res).isEqualTo(-5);

    res = Util.subtractWithOverflowDefault(Long.MIN_VALUE + 1, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MIN_VALUE);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE - 1, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void subtrackWithOverflowDefault_withUnderflow_returnsOverflowDefault() {
    long res = Util.subtractWithOverflowDefault(Long.MIN_VALUE, 1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);

    res = Util.subtractWithOverflowDefault(Long.MAX_VALUE, -1, /* overflowResult= */ 12345);
    assertThat(res).isEqualTo(12345);
  }

  @Test
  public void inferContentType_returnsInferredResult() {
    assertThat(Util.inferContentType("http://a.b/c.ism")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.ism/Manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest(filter=x)")).isEqualTo(C.TYPE_SS);

    assertThat(Util.inferContentType("http://a.b/c.ism/prefix-manifest")).isEqualTo(C.TYPE_OTHER);
    assertThat(Util.inferContentType("http://a.b/c.ism/manifest-suffix")).isEqualTo(C.TYPE_OTHER);
  }

  @Test
  public void arrayBinarySearchFloor_emptyArrayAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchFloor_emptyArrayAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetInArrayAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      arrayBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                new int[] {1, 1, 1, 1, 1, 3, 5},
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void longArrayBinarySearchFloor_emptyArrayAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new LongArray(), /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void longArrayBinarySearchFloor_emptyArrayAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new LongArray(), /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void longArrayBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void longArrayBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetInArrayAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      longArrayBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                newLongArray(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void listBinarySearchFloor_emptyListAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchFloor_emptyListAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchFloor_targetSmallerThanValuesAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchFloor_targetBiggerThanValues_returnsLastIndex() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsFalse_returnsMinus1() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(-1);
  }

  @Test
  public void
      listBinarySearchFloor_targetEqualToFirstValueAndInclusiveFalseAndStayInBoundsTrue_returns0() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(0);
  }

  @Test
  public void
      listBinarySearchFloor_targetInListAndInclusiveTrue_returnsFirstIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 1,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void
      listBinarySearchFloor_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void
      listBinarySearchFloor_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchFloor(
                Arrays.asList(1, 1, 1, 1, 1, 3, 5),
                /* value= */ 2,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(4);
  }

  @Test
  public void arrayBinarySearchCeil_emptyArrayAndStayInBoundsFalse_returns0() {
    assertThat(
            binarySearchCeil(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchCeil_emptyArrayAndStayInBoundsTrue_returnsMinus1() {
    assertThat(
            binarySearchCeil(
                new int[0], /* value= */ 0, /* inclusive= */ false, /* stayInBounds= */ true))
        .isEqualTo(-1);
  }

  @Test
  public void arrayBinarySearchCeil_targetSmallerThanValues_returns0() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void arrayBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(3);
  }

  @Test
  public void arrayBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5},
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(7);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(6);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetInArrayAndInclusiveTrue_returnsLastIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 5,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(6);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 4,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      arrayBinarySearchCeil_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                new int[] {1, 3, 5, 5, 5, 5, 5},
                /* value= */ 4,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void listBinarySearchCeil_emptyListAndStayInBoundsFalse_returns0() {
    assertThat(
            binarySearchCeil(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchCeil_emptyListAndStayInBoundsTrue_returnsMinus1() {
    assertThat(
            binarySearchCeil(
                new ArrayList<>(),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(-1);
  }

  @Test
  public void listBinarySearchCeil_targetSmallerThanValues_returns0() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 0,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(0);
  }

  @Test
  public void listBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(3);
  }

  @Test
  public void listBinarySearchCeil_targetBiggerThanValuesAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5),
                /* value= */ 6,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsFalse_returnsLength() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(7);
  }

  @Test
  public void
      listBinarySearchCeil_targetEqualToLastValueAndInclusiveFalseAndStayInBoundsTrue_returnsLastIndex() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ false,
                /* stayInBounds= */ true))
        .isEqualTo(6);
  }

  @Test
  public void
      listBinarySearchCeil_targetInListAndInclusiveTrue_returnsLastIndexWithValueEqualToTarget() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 5,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(6);
  }

  @Test
  public void
      listBinarySearchCeil_targetBetweenValuesAndInclusiveFalse_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 4,
                /* inclusive= */ false,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void
      listBinarySearchCeil_targetBetweenValuesAndInclusiveTrue_returnsIndexWhereTargetShouldBeInserted() {
    assertThat(
            binarySearchCeil(
                Arrays.asList(1, 3, 5, 5, 5, 5, 5),
                /* value= */ 4,
                /* inclusive= */ true,
                /* stayInBounds= */ false))
        .isEqualTo(2);
  }

  @Test
  public void parseXsDuration_returnsParsedDurationInMillis() {
    assertThat(parseXsDuration("PT150.279S")).isEqualTo(150279L);
    assertThat(parseXsDuration("PT1.500S")).isEqualTo(1500L);
  }

  @Test
  public void parseXsDateTime_returnsParsedDateTimeInMillis() throws Exception {
    assertThat(parseXsDateTime("2014-06-19T23:07:42")).isEqualTo(1403219262000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00,000Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-08:00")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-800")).isEqualTo(1411161535000L);
  }

  @Test
  public void toUnsignedLong_withPositiveValue_returnsValue() {
    int x = 0x05D67F23;

    long result = Util.toUnsignedLong(x);

    assertThat(result).isEqualTo(0x05D67F23L);
  }

  @Test
  public void toUnsignedLong_withNegativeValue_returnsValue() {
    int x = 0xF5D67F23;

    long result = Util.toUnsignedLong(x);

    assertThat(result).isEqualTo(0xF5D67F23L);
  }

  @Test
  public void toLong_withZeroValue_returnsZero() {
    assertThat(Util.toLong(0, 0)).isEqualTo(0);
  }

  @Test
  public void toLong_withLongValue_returnsValue() {
    assertThat(Util.toLong(1, -4)).isEqualTo(0x1FFFFFFFCL);
  }

  @Test
  public void toLong_withBigValue_returnsValue() {
    assertThat(Util.toLong(0x7ABCDEF, 0x12345678)).isEqualTo(0x7ABCDEF_12345678L);
  }

  @Test
  public void toLong_withMaxValue_returnsValue() {
    assertThat(Util.toLong(0x0FFFFFFF, 0xFFFFFFFF)).isEqualTo(0x0FFFFFFF_FFFFFFFFL);
  }

  @Test
  public void toLong_withBigNegativeValue_returnsValue() {
    assertThat(Util.toLong(0xFEDCBA, 0x87654321)).isEqualTo(0xFEDCBA_87654321L);
  }

  @Test
  public void toHexString_returnsHexString() {
    byte[] bytes = TestUtil.createByteArray(0x12, 0xFC, 0x06);

    assertThat(Util.toHexString(bytes)).isEqualTo("12fc06");
  }

  @Test
  public void getCodecsOfType_withNull_returnsNull() {
    assertThat(getCodecsOfType(null, C.TRACK_TYPE_VIDEO)).isNull();
  }

  @Test
  public void getCodecsOfType_withInvalidTrackType_returnsNull() {
    assertThat(getCodecsOfType("avc1.64001e,vp9.63.1", C.TRACK_TYPE_AUDIO)).isNull();
  }

  @Test
  public void getCodecsOfType_withAudioTrack_returnsCodec() {
    assertThat(getCodecsOfType(" vp9.63.1, ec-3 ", C.TRACK_TYPE_AUDIO)).isEqualTo("ec-3");
  }

  @Test
  public void getCodecsOfType_withVideoTrack_returnsCodec() {
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
    assertThat(getCodecsOfType("avc1.61e, vp9.63.1, ec-3 ", C.TRACK_TYPE_VIDEO))
        .isEqualTo("avc1.61e,vp9.63.1");
  }

  @Test
  public void getCodecsOfType_withInvalidCodec_returnsNull() {
    assertThat(getCodecsOfType("invalidCodec1, invalidCodec2 ", C.TRACK_TYPE_AUDIO)).isNull();
  }

  @Test
  public void unescapeFileName_invalidFileName_returnsNull() {
    assertThat(Util.unescapeFileName("%a")).isNull();
    assertThat(Util.unescapeFileName("%xyz")).isNull();
  }

  @Test
  public void escapeUnescapeFileName_returnsEscapedString() {
    assertEscapeUnescapeFileName("just+a regular+fileName", "just+a regular+fileName");
    assertEscapeUnescapeFileName("key:value", "key%3avalue");
    assertEscapeUnescapeFileName("<>:\"/\\|?*%", "%3c%3e%3a%22%2f%5c%7c%3f%2a%25");

    Random random = new Random(0);
    for (int i = 0; i < 1000; i++) {
      String string = TestUtil.buildTestString(1000, random);
      assertEscapeUnescapeFileName(string);
    }
  }

  @Test
  public void crc32_returnsUpdatedCrc32() {
    byte[] bytes = {0x5F, 0x78, 0x04, 0x7B, 0x5F};
    int start = 1;
    int end = 4;
    int initialValue = 0xFFFFFFFF;

    int result = Util.crc32(bytes, start, end, initialValue);

    assertThat(result).isEqualTo(0x67CE9747);
  }

  @Test
  public void crc8_returnsUpdatedCrc8() {
    byte[] bytes = {0x5F, 0x78, 0x04, 0x7B, 0x5F};
    int start = 1;
    int end = 4;
    int initialValue = 0;

    int result = Util.crc8(bytes, start, end, initialValue);

    assertThat(result).isEqualTo(0x4);
  }

  @Test
  public void getBigEndianInt_fromBigEndian() {
    byte[] bytes = {0x1F, 0x2E, 0x3D, 0x4C};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 0)).isEqualTo(0x1F2E3D4C);
  }

  @Test
  public void getBigEndianInt_fromLittleEndian() {
    byte[] bytes = {(byte) 0xC2, (byte) 0xD3, (byte) 0xE4, (byte) 0xF5};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 0)).isEqualTo(0xC2D3E4F5);
  }

  @Test
  public void getBigEndianInt_unaligned() {
    byte[] bytes = {9, 8, 7, 6, 5};
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

    assertThat(Util.getBigEndianInt(byteBuffer, 1)).isEqualTo(0x08070605);
  }

  @Test
  public void inflate_withDeflatedData_success() {
    byte[] testData = TestUtil.buildTestData(/*arbitrary test data size*/ 256 * 1024);
    byte[] compressedData = new byte[testData.length * 2];
    Deflater compresser = new Deflater(9);
    compresser.setInput(testData);
    compresser.finish();
    int compressedDataLength = compresser.deflate(compressedData);
    compresser.end();

    ParsableByteArray input = new ParsableByteArray(compressedData, compressedDataLength);
    ParsableByteArray output = new ParsableByteArray();
    assertThat(Util.inflate(input, output, /* inflater= */ null)).isTrue();
    assertThat(output.limit()).isEqualTo(testData.length);
    assertThat(Arrays.copyOf(output.data, output.limit())).isEqualTo(testData);
  }

  // TODO: Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void normalizeLanguageCode_keepsUndefinedTagsUnchanged() {
    assertThat(Util.normalizeLanguageCode(null)).isNull();
    assertThat(Util.normalizeLanguageCode("")).isEmpty();
    assertThat(Util.normalizeLanguageCode("und")).isEqualTo("und");
    assertThat(Util.normalizeLanguageCode("DoesNotExist")).isEqualTo("doesnotexist");
  }

  // TODO: Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void normalizeLanguageCode_normalizesCodeToTwoLetterISOAndLowerCase_keepingAllSubtags() {
    assertThat(Util.normalizeLanguageCode("es")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("spa")).isEqualTo("es");
    assertThat(Util.normalizeLanguageCode("es-AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("SpA-ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es_AR")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("spa_ar")).isEqualTo("es-ar");
    assertThat(Util.normalizeLanguageCode("es-AR-dialect")).isEqualTo("es-ar-dialect");
    // Regional subtag (South America)
    assertThat(Util.normalizeLanguageCode("ES-419")).isEqualTo("es-419");
    // Script subtag (Simplified Taiwanese)
    assertThat(Util.normalizeLanguageCode("zh-hans-tw")).isEqualTo("zh-hans-tw");
    assertThat(Util.normalizeLanguageCode("zho-hans-tw")).isEqualTo("zh-hans-tw");
    // Non-spec compliant subtags.
    assertThat(Util.normalizeLanguageCode("sv-illegalSubtag")).isEqualTo("sv-illegalsubtag");
  }

  // TODO: Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void normalizeLanguageCode_iso6392BibliographicalAndTextualCodes_areNormalizedToSameTag() {
    // See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
    assertThat(Util.normalizeLanguageCode("alb")).isEqualTo(Util.normalizeLanguageCode("sqi"));
    assertThat(Util.normalizeLanguageCode("arm")).isEqualTo(Util.normalizeLanguageCode("hye"));
    assertThat(Util.normalizeLanguageCode("baq")).isEqualTo(Util.normalizeLanguageCode("eus"));
    assertThat(Util.normalizeLanguageCode("bur")).isEqualTo(Util.normalizeLanguageCode("mya"));
    assertThat(Util.normalizeLanguageCode("chi")).isEqualTo(Util.normalizeLanguageCode("zho"));
    assertThat(Util.normalizeLanguageCode("cze")).isEqualTo(Util.normalizeLanguageCode("ces"));
    assertThat(Util.normalizeLanguageCode("dut")).isEqualTo(Util.normalizeLanguageCode("nld"));
    assertThat(Util.normalizeLanguageCode("fre")).isEqualTo(Util.normalizeLanguageCode("fra"));
    assertThat(Util.normalizeLanguageCode("geo")).isEqualTo(Util.normalizeLanguageCode("kat"));
    assertThat(Util.normalizeLanguageCode("ger")).isEqualTo(Util.normalizeLanguageCode("deu"));
    assertThat(Util.normalizeLanguageCode("gre")).isEqualTo(Util.normalizeLanguageCode("ell"));
    assertThat(Util.normalizeLanguageCode("ice")).isEqualTo(Util.normalizeLanguageCode("isl"));
    assertThat(Util.normalizeLanguageCode("mac")).isEqualTo(Util.normalizeLanguageCode("mkd"));
    assertThat(Util.normalizeLanguageCode("mao")).isEqualTo(Util.normalizeLanguageCode("mri"));
    assertThat(Util.normalizeLanguageCode("may")).isEqualTo(Util.normalizeLanguageCode("msa"));
    assertThat(Util.normalizeLanguageCode("per")).isEqualTo(Util.normalizeLanguageCode("fas"));
    assertThat(Util.normalizeLanguageCode("rum")).isEqualTo(Util.normalizeLanguageCode("ron"));
    assertThat(Util.normalizeLanguageCode("slo")).isEqualTo(Util.normalizeLanguageCode("slk"));
    assertThat(Util.normalizeLanguageCode("scc")).isEqualTo(Util.normalizeLanguageCode("srp"));
    assertThat(Util.normalizeLanguageCode("tib")).isEqualTo(Util.normalizeLanguageCode("bod"));
    assertThat(Util.normalizeLanguageCode("wel")).isEqualTo(Util.normalizeLanguageCode("cym"));
  }

  // TODO: Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void
      normalizeLanguageCode_deprecatedLanguageTagsAndModernReplacement_areNormalizedToSameTag() {
    // See https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes, "ISO 639:1988"
    assertThat(Util.normalizeLanguageCode("in")).isEqualTo(Util.normalizeLanguageCode("id"));
    assertThat(Util.normalizeLanguageCode("in")).isEqualTo(Util.normalizeLanguageCode("ind"));
    assertThat(Util.normalizeLanguageCode("iw")).isEqualTo(Util.normalizeLanguageCode("he"));
    assertThat(Util.normalizeLanguageCode("iw")).isEqualTo(Util.normalizeLanguageCode("heb"));
    assertThat(Util.normalizeLanguageCode("ji")).isEqualTo(Util.normalizeLanguageCode("yi"));
    assertThat(Util.normalizeLanguageCode("ji")).isEqualTo(Util.normalizeLanguageCode("yid"));

    // Grandfathered tags
    assertThat(Util.normalizeLanguageCode("i-lux")).isEqualTo(Util.normalizeLanguageCode("lb"));
    assertThat(Util.normalizeLanguageCode("i-lux")).isEqualTo(Util.normalizeLanguageCode("ltz"));
    assertThat(Util.normalizeLanguageCode("i-hak")).isEqualTo(Util.normalizeLanguageCode("hak"));
    assertThat(Util.normalizeLanguageCode("i-hak")).isEqualTo(Util.normalizeLanguageCode("zh-hak"));
    assertThat(Util.normalizeLanguageCode("i-navajo")).isEqualTo(Util.normalizeLanguageCode("nv"));
    assertThat(Util.normalizeLanguageCode("i-navajo")).isEqualTo(Util.normalizeLanguageCode("nav"));
    assertThat(Util.normalizeLanguageCode("no-bok")).isEqualTo(Util.normalizeLanguageCode("nb"));
    assertThat(Util.normalizeLanguageCode("no-bok")).isEqualTo(Util.normalizeLanguageCode("nob"));
    assertThat(Util.normalizeLanguageCode("no-nyn")).isEqualTo(Util.normalizeLanguageCode("nn"));
    assertThat(Util.normalizeLanguageCode("no-nyn")).isEqualTo(Util.normalizeLanguageCode("nno"));
    assertThat(Util.normalizeLanguageCode("zh-guoyu")).isEqualTo(Util.normalizeLanguageCode("cmn"));
    assertThat(Util.normalizeLanguageCode("zh-guoyu"))
        .isEqualTo(Util.normalizeLanguageCode("zh-cmn"));
    assertThat(Util.normalizeLanguageCode("zh-hakka")).isEqualTo(Util.normalizeLanguageCode("hak"));
    assertThat(Util.normalizeLanguageCode("zh-hakka"))
        .isEqualTo(Util.normalizeLanguageCode("zh-hak"));
    assertThat(Util.normalizeLanguageCode("zh-min-nan"))
        .isEqualTo(Util.normalizeLanguageCode("nan"));
    assertThat(Util.normalizeLanguageCode("zh-min-nan"))
        .isEqualTo(Util.normalizeLanguageCode("zh-nan"));
    assertThat(Util.normalizeLanguageCode("zh-xiang")).isEqualTo(Util.normalizeLanguageCode("hsn"));
    assertThat(Util.normalizeLanguageCode("zh-xiang"))
        .isEqualTo(Util.normalizeLanguageCode("zh-hsn"));
  }

  // TODO: Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void normalizeLanguageCode_macrolanguageTags_areFullyMaintained() {
    // See https://en.wikipedia.org/wiki/ISO_639_macrolanguage
    assertThat(Util.normalizeLanguageCode("zh-cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("zho-cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("ar-ayl")).isEqualTo("ar-ayl");
    assertThat(Util.normalizeLanguageCode("ara-ayl")).isEqualTo("ar-ayl");

    // Special case of short codes that are actually part of a macrolanguage.
    assertThat(Util.normalizeLanguageCode("nb")).isEqualTo("no-nob");
    assertThat(Util.normalizeLanguageCode("nn")).isEqualTo("no-nno");
    assertThat(Util.normalizeLanguageCode("nob")).isEqualTo("no-nob");
    assertThat(Util.normalizeLanguageCode("nno")).isEqualTo("no-nno");
    assertThat(Util.normalizeLanguageCode("tw")).isEqualTo("ak-twi");
    assertThat(Util.normalizeLanguageCode("twi")).isEqualTo("ak-twi");
    assertThat(Util.normalizeLanguageCode("bs")).isEqualTo("hbs-bos");
    assertThat(Util.normalizeLanguageCode("bos")).isEqualTo("hbs-bos");
    assertThat(Util.normalizeLanguageCode("hr")).isEqualTo("hbs-hrv");
    assertThat(Util.normalizeLanguageCode("hrv")).isEqualTo("hbs-hrv");
    assertThat(Util.normalizeLanguageCode("sr")).isEqualTo("hbs-srp");
    assertThat(Util.normalizeLanguageCode("srp")).isEqualTo("hbs-srp");
    assertThat(Util.normalizeLanguageCode("id")).isEqualTo("ms-ind");
    assertThat(Util.normalizeLanguageCode("ind")).isEqualTo("ms-ind");
    assertThat(Util.normalizeLanguageCode("cmn")).isEqualTo("zh-cmn");
    assertThat(Util.normalizeLanguageCode("hak")).isEqualTo("zh-hak");
    assertThat(Util.normalizeLanguageCode("nan")).isEqualTo("zh-nan");
    assertThat(Util.normalizeLanguageCode("hsn")).isEqualTo("zh-hsn");
  }

  @Test
  public void toList() {
    assertThat(Util.toList(0, 3, 4)).containsExactly(0, 3, 4).inOrder();
  }

  @Test
  public void toList_nullPassed_returnsEmptyList() {
    assertThat(Util.toList(null)).isEmpty();
  }

  @Test
  public void toList_emptyArrayPassed_returnsEmptyList() {
    assertThat(Util.toList(new int[0])).isEmpty();
  }

  private static void assertEscapeUnescapeFileName(String fileName, String escapedFileName) {
    assertThat(escapeFileName(fileName)).isEqualTo(escapedFileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static void assertEscapeUnescapeFileName(String fileName) {
    String escapedFileName = Util.escapeFileName(fileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static LongArray newLongArray(long... values) {
    LongArray longArray = new LongArray();
    for (long value : values) {
      longArray.add(value);
    }
    return longArray;
  }
}
