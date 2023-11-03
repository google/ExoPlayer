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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.math.LongMath;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Parameterized tests for:
 *
 * <ul>
 *   <li>{@link Util#scaleLargeValue}
 *   <li>{@link Util#scaleLargeValues}
 *   <li>{@link Util#scaleLargeValuesInPlace}
 *   <li>{@link Util#scaleLargeTimestamp}
 *   <li>{@link Util#scaleLargeTimestamps}
 *   <li>{@link Util#scaleLargeTimestampsInPlace}
 * </ul>
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class UtilScaleLargeValueParameterizedTest {

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> implementations() {
    ImmutableList<Implementation> implementations =
        ImmutableList.of(
            new Implementation("single-value", Util::scaleLargeValue),
            new Implementation(
                "list",
                (value, multiplier, divisor, roundingMode) ->
                    Util.scaleLargeValues(
                        ImmutableList.of(value), multiplier, divisor, roundingMode)[0]),
            new Implementation(
                "array-in-place",
                (value, multiplier, divisor, roundingMode) -> {
                  long[] values = new long[] {value};
                  Util.scaleLargeValuesInPlace(values, multiplier, divisor, roundingMode);
                  return values[0];
                }),
            new Implementation(
                "single-timestamp",
                (long timestamp, long multiplier, long divisor, RoundingMode roundingMode) -> {
                  assumeTrue(
                      roundingMode == RoundingMode.UNNECESSARY
                          || roundingMode == RoundingMode.FLOOR);
                  return Util.scaleLargeTimestamp(timestamp, multiplier, divisor);
                }),
            new Implementation(
                "timestamp-list",
                (timestamp, multiplier, divisor, roundingMode) -> {
                  assumeTrue(
                      roundingMode == RoundingMode.UNNECESSARY
                          || roundingMode == RoundingMode.FLOOR);
                  return Util.scaleLargeTimestamps(
                      ImmutableList.of(timestamp), multiplier, divisor)[0];
                }),
            new Implementation(
                "timestamp-array-in-place",
                (timestamp, multiplier, divisor, roundingMode) -> {
                  assumeTrue(
                      roundingMode == RoundingMode.UNNECESSARY
                          || roundingMode == RoundingMode.FLOOR);
                  long[] timestamps = new long[] {timestamp};
                  Util.scaleLargeTimestampsInPlace(timestamps, multiplier, divisor);
                  return timestamps[0];
                }));

    List<Implementation> implementationsWithNegativeCases = new ArrayList<>();
    for (Implementation implementation : implementations) {
      implementationsWithNegativeCases.add(implementation);
      implementationsWithNegativeCases.add(
          new Implementation(
              implementation.name + "-negative-value",
              (value, multiplier, divisor, roundingMode) -> {
                assumeTrue(roundingMode == RoundingMode.UNNECESSARY);
                long result =
                    implementation.scaleFn.scaleLargeValue(
                        -value, multiplier, divisor, roundingMode);
                assumeFalse(result == Long.MIN_VALUE || result == Long.MAX_VALUE);
                return -result;
              }));
      implementationsWithNegativeCases.add(
          new Implementation(
              implementation.name + "-negative-multiplier",
              (value, multiplier, divisor, roundingMode) -> {
                assumeTrue(roundingMode == RoundingMode.UNNECESSARY);
                long result =
                    implementation.scaleFn.scaleLargeValue(
                        value, -multiplier, divisor, roundingMode);
                assumeFalse(result == Long.MIN_VALUE || result == Long.MAX_VALUE);
                return -result;
              }));
      implementationsWithNegativeCases.add(
          new Implementation(
              implementation.name + "-negative-divisor",
              (value, multiplier, divisor, roundingMode) -> {
                assumeTrue(roundingMode == RoundingMode.UNNECESSARY);
                long result =
                    implementation.scaleFn.scaleLargeValue(
                        value, multiplier, -divisor, roundingMode);
                assumeFalse(result == Long.MIN_VALUE || result == Long.MAX_VALUE);
                return -result;
              }));
    }

    ImmutableList.Builder<Object[]> implementationsAsObjectArray = ImmutableList.builder();
    for (Implementation implementation : implementationsWithNegativeCases) {
      implementationsAsObjectArray.add(new Object[] {implementation.name, implementation.scaleFn});
    }
    return implementationsAsObjectArray.build();
  }

  // Every parameter has to be assigned to a field, even if it's only used to name the test.
  @SuppressWarnings("unused")
  @ParameterizedRobolectricTestRunner.Parameter(0)
  public String name;

  @ParameterizedRobolectricTestRunner.Parameter(1)
  public ScaleLargeValueFn implementation;

  @Test
  public void zeroValue() {
    // Deliberately use prime multiplier and divisor so they can't cancel.
    long result =
        implementation.scaleLargeValue(
            /* value= */ 0, /* multiplier= */ 5, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(0);
  }

  @Test
  public void zeroMultiplier() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 10, /* multiplier= */ 0, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(0);
  }

  @Test
  public void zeroDivisor_throwsException() {
    assertThrows(
        ArithmeticException.class,
        () ->
            implementation.scaleLargeValue(
                /* value= */ 2, /* multiplier= */ 10, /* divisor= */ 0, RoundingMode.UNNECESSARY));
  }

  @Test
  public void divisorMultipleOfMultiplier() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 7, /* multiplier= */ 2, /* divisor= */ 4, RoundingMode.FLOOR);
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void multiplierMultipleOfDivisor() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 7, /* multiplier= */ 4, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(14);
  }

  @Test
  public void multiplierMultipleOfDivisor_resultOverflowsLong_clampedToMaxLong() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 7, /* multiplier= */ 1L << 62, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void multiplierMultipleOfDivisor_resultUnderflowsLong_clampedToMinLong() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ -7,
            /* multiplier= */ 1L << 62,
            /* divisor= */ 2,
            RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void divisorMultipleOfValue() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 2, /* multiplier= */ 7, /* divisor= */ 4, RoundingMode.FLOOR);
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void valueMultipleOfDivisor() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 4, /* multiplier= */ 7, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(14);
  }

  @Test
  public void valueMultipleOfDivisor_resultOverflowsLong_clampedToMaxLong() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 1L << 62, /* multiplier= */ 7, /* divisor= */ 2, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void valueMultipleOfDivisor_resultUnderflowsLong_clampedToMinLong() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ 1L << 62,
            /* multiplier= */ -7,
            /* divisor= */ 2,
            RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void numeratorDoesntOverflow() {
    // Deliberately choose value, multiplier and divisor so no pair trivially cancels to 1.
    long result =
        implementation.scaleLargeValue(
            /* value= */ 12, /* multiplier= */ 15, /* divisor= */ 20, RoundingMode.UNNECESSARY);
    assertThat(result).isEqualTo(9);
  }

  @Test
  public void numeratorWouldOverflowIfNotCancelled() {
    // Deliberately choose value, multiplier and divisor so that both value/divisor and
    // multiplier/divisor have to be cancelled otherwise multiplier*value will overflow a long.
    long value = LongMath.checkedMultiply(3, 1L << 61);
    long multiplier = LongMath.checkedMultiply(5, 1L << 60);
    long divisor = LongMath.checkedMultiply(15, 1L << 59);

    long result =
        implementation.scaleLargeValue(value, multiplier, divisor, RoundingMode.UNNECESSARY);

    assertThat(result).isEqualTo(1L << 62);
  }

  /**
   * This test uses real values from sample_ac4.mp4 that have an exact integer result, but the
   * floating-point branch of {@link Util#scaleLargeValue} produces an incorrect fractional result.
   *
   * <p>Here we scale them up to ensure multiplier*value would overflow a long, so the
   * implementation needs to simplify the fraction first to avoid falling through to the
   * floating-point branch (which will cause this test to fail because passing
   * RoundingMode.UNNECESSARY won't be allowed).
   */
  @Test
  public void cancelsRatherThanFallThroughToFloatingPoint() {
    long value = 24960;
    long multiplier = 1_000_000_000_000_000_000L;
    // Confirm that naively multiplying value and multiplier overflows.
    checkState(LongMath.saturatedMultiply(value, multiplier) == Long.MAX_VALUE);

    long result =
        implementation.scaleLargeValue(
            value, multiplier, /* divisor= */ 48_000_000_000_000_000L, RoundingMode.UNNECESSARY);

    assertThat(result).isEqualTo(520000);
  }

  @Test
  public void numeratorOverflowsAndCantBeCancelled() {
    // Use three Mersenne primes so nothing can cancel, and the numerator will (just) overflow 64
    // bits - forcing the implementation down the floating-point branch.
    long value = (1L << 61) - 1;
    long multiplier = (1L << 5) - 1;
    // Confirm that naively multiplying value and multiplier overflows.
    checkState(LongMath.saturatedMultiply(value, multiplier) == Long.MAX_VALUE);

    long result =
        implementation.scaleLargeValue(
            value, multiplier, /* divisor= */ (1L << 31) - 1, RoundingMode.FLOOR);

    assertThat(result).isEqualTo(33285996559L);
  }

  @Test
  public void resultOverflows_truncatedToMaxLong() {
    long result =
        implementation.scaleLargeValue(
            /* value= */ (1L << 61) - 1,
            /* multiplier= */ (1L << 61) - 1,
            /* divisor= */ (1L << 31) - 1,
            RoundingMode.FLOOR);

    assertThat(result).isEqualTo(Long.MAX_VALUE);
  }

  private static final class Implementation {
    public final String name;
    public final ScaleLargeValueFn scaleFn;

    private Implementation(String name, ScaleLargeValueFn scaleFn) {
      this.name = name;
      this.scaleFn = scaleFn;
    }
  }

  private interface ScaleLargeValueFn {
    long scaleLargeValue(long value, long multiplier, long divisor, RoundingMode roundingMode);
  }
}
