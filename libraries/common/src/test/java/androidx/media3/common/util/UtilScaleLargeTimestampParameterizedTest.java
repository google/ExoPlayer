/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.math.LongMath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Parameterized tests for:
 *
 * <ul>
 *   <li>{@link Util#scaleLargeTimestamp}
 *   <li>{@link Util#scaleLargeTimestamps}
 *   <li>{@link Util#scaleLargeTimestampsInPlace}
 * </ul>
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class UtilScaleLargeTimestampParameterizedTest {

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> implementations() {
    return ImmutableList.of(
        new Object[] {"single-timestamp", (ScaleLargeTimestampFn) Util::scaleLargeTimestamp},
        new Object[] {
          "timestamp-list",
          (ScaleLargeTimestampFn)
              (timestamp, multiplier, divisor) ->
                  Util.scaleLargeTimestamps(ImmutableList.of(timestamp), multiplier, divisor)[0]
        },
        new Object[] {
          "timestamp-array-in-place",
          (ScaleLargeTimestampFn)
              (timestamp, multiplier, divisor) -> {
                long[] timestamps = new long[] {timestamp};
                Util.scaleLargeTimestampsInPlace(timestamps, multiplier, divisor);
                return timestamps[0];
              }
        });
  }

  // Every parameter has to be assigned to a field, even if it's only used to name the test.
  @SuppressWarnings("unused")
  @ParameterizedRobolectricTestRunner.Parameter(0)
  public String name;

  @ParameterizedRobolectricTestRunner.Parameter(1)
  public ScaleLargeTimestampFn implementation;

  @Test
  public void zeroValue() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 0, /* multiplier= */ 10, /* divisor= */ 2);
    assertThat(result).isEqualTo(0);
  }

  @Test
  public void zeroDivisor_throwsException() {
    assertThrows(
        ArithmeticException.class,
        () ->
            implementation.scaleLargeTimestamp(
                /* timestamp= */ 2, /* multiplier= */ 10, /* divisor= */ 0));
  }

  @Test
  public void divisorMultipleOfMultiplier() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 7, /* multiplier= */ 2, /* divisor= */ 4);
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void multiplierMultipleOfDivisor() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 7, /* multiplier= */ 4, /* divisor= */ 2);
    assertThat(result).isEqualTo(14);
  }

  @Test
  public void multiplierMultipleOfDivisor_resultOverflowsLong() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 7, /* multiplier= */ 1L << 62, /* divisor= */ 2);
    assertThat(result).isEqualTo(-2305843009213693952L);
  }

  @Test
  public void multiplierMultipleOfDivisor_resultUnderflowsLong() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ -7, /* multiplier= */ 1L << 62, /* divisor= */ 2);
    assertThat(result).isEqualTo(2305843009213693952L);
  }

  @Test
  public void divisorMultipleOfValue() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 2, /* multiplier= */ 7, /* divisor= */ 4);
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void valueMultipleOfDivisor() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 4, /* multiplier= */ 7, /* divisor= */ 2);
    assertThat(result).isEqualTo(14);
  }

  @Test
  public void valueMultipleOfDivisor_resultOverflowsLong_clampedToMaxLong() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 1L << 62, /* multiplier= */ 7, /* divisor= */ 2);
    assertThat(result).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void valueMultipleOfDivisor_resultUnderflowsLong_clampedToMinLong() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 1L << 62, /* multiplier= */ -7, /* divisor= */ 2);
    assertThat(result).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void numeratorDoesntOverflow() {
    // Deliberately choose value, multiplier and divisor so no pair trivially cancels to 1.
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ 12, /* multiplier= */ 15, /* divisor= */ 20);
    assertThat(result).isEqualTo(9);
  }

  @Test
  public void numeratorWouldOverflowIfNotCancelled() {
    // Deliberately choose value, multiplier and divisor so that both value/divisor and
    // multiplier/divisor have to be cancelled otherwise multiplier*value will overflow a long.
    long value = LongMath.checkedMultiply(3, 1L << 61);
    long multiplier = LongMath.checkedMultiply(5, 1L << 60);
    long divisor = LongMath.checkedMultiply(15, 1L << 59);

    long result = implementation.scaleLargeTimestamp(value, multiplier, divisor);

    assertThat(result).isEqualTo(1L << 62);
  }

  // TODO(b/290045069): Remove this suppression when we depend on Guava 32+.
  @SuppressWarnings("UnstableApiUsage")
  @Test
  public void numeratorOverflowsAndCantBeCancelled() {
    // Use three Mersenne primes so nothing can cancel, and the numerator will (just) overflow 64
    // bits - forcing the implementation down the floating-point branch.
    long value = (1L << 61) - 1;
    long multiplier = (1L << 5) - 1;
    // Confirm that naively multiplying value and multiplier overflows.
    checkState(LongMath.saturatedMultiply(value, multiplier) == Long.MAX_VALUE);

    long result =
        implementation.scaleLargeTimestamp(value, multiplier, /* divisor= */ (1L << 31) - 1);

    assertThat(result).isEqualTo(33285996559L);
  }

  @Test
  public void resultOverflows_truncatedToMaxLong() {
    long result =
        implementation.scaleLargeTimestamp(
            /* timestamp= */ (1L << 61) - 1,
            /* multiplier= */ (1L << 61) - 1,
            /* divisor= */ (1L << 31) - 1);

    assertThat(result).isEqualTo(Long.MAX_VALUE);
  }

  private interface ScaleLargeTimestampFn {
    long scaleLargeTimestamp(long timestamp, long multiplier, long divisor);
  }
}
