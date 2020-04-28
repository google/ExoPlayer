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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Assertion methods for {@link Extractor}.
 */
public final class ExtractorAsserts {

  /**
   * Returns a list of arrays containing {@link Config} objects to exercise different extractor
   * paths.
   *
   * <p>This is intended to be used from tests using {@code ParameterizedRobolectricTestRunner} or
   * {@code org.junit.runners.Parameterized}.
   */
  public static List<Object[]> configs() {
    return Arrays.asList(
        new Object[] {new Config(true, false, false, false)},
        new Object[] {new Config(true, false, false, true)},
        new Object[] {new Config(true, false, true, false)},
        new Object[] {new Config(true, false, true, true)},
        new Object[] {new Config(true, true, false, false)},
        new Object[] {new Config(true, true, false, true)},
        new Object[] {new Config(true, true, true, false)},
        new Object[] {new Config(true, true, true, true)},
        new Object[] {new Config(false, false, false, false)});
  }

  /** A config of different environments to simulate and extractor behaviour to test. */
  public static class Config {

    /**
     * Whether to sniff the data by calling {@link Extractor#sniff(ExtractorInput)} prior to
     * consuming it.
     */
    public final boolean sniffFirst;
    /** Whether to simulate IO errors. */
    public final boolean simulateIOErrors;
    /** Whether to simulate unknown input length. */
    public final boolean simulateUnknownLength;
    /** Whether to simulate partial reads. */
    public final boolean simulatePartialReads;

    private Config(
        boolean sniffFirst,
        boolean simulateIOErrors,
        boolean simulateUnknownLength,
        boolean simulatePartialReads) {
      this.sniffFirst = sniffFirst;
      this.simulateIOErrors = simulateIOErrors;
      this.simulateUnknownLength = simulateUnknownLength;
      this.simulatePartialReads = simulatePartialReads;
    }

    @Override
    public String toString() {
      return Util.formatInvariant(
          "sniff=%s,ioErr=%s,unknownLen=%s,partRead=%s",
          sniffFirst, simulateIOErrors, simulateUnknownLength, simulatePartialReads);
    }
  }

  /** A factory for {@link Extractor} instances. */
  public interface ExtractorFactory {
    Extractor create();
  }

  private static final String DUMP_EXTENSION = ".dump";
  private static final String UNKNOWN_LENGTH_EXTENSION = ".unknown_length" + DUMP_EXTENSION;

  /**
   * Asserts that {@link Extractor#sniff(ExtractorInput)} returns the {@code expectedResult} for a
   * given {@code input}, retrying repeatedly when {@link SimulatedIOException} is thrown.
   *
   * @param extractor The extractor to test.
   * @param input The extractor input.
   * @param expectedResult The expected return value.
   * @throws IOException If reading from the input fails.
   */
  public static void assertSniff(
      Extractor extractor, FakeExtractorInput input, boolean expectedResult) throws IOException {
    while (true) {
      try {
        assertThat(extractor.sniff(input)).isEqualTo(expectedResult);
        return;
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

  /**
   * Asserts that an extractor behaves correctly given valid input data.
   *
   * <ul>
   *   <li>Calls {@link Extractor#seek(long, long)} and {@link Extractor#release()} without calling
   *       {@link Extractor#init(ExtractorOutput)} to check these calls do not fail.
   *   <li>Calls {@link #assertOutput(Extractor, String, byte[], Context, boolean, boolean, boolean,
   *       boolean)} with all possible combinations of "simulate" parameters.
   * </ul>
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param file The path to the input sample.
   * @throws IOException If reading from the input fails.
   */
  public static void assertAllBehaviors(ExtractorFactory factory, String file) throws IOException {
    assertAllBehaviors(factory, file, file);
  }

  /**
   * Asserts that an extractor behaves correctly given valid input data:
   *
   * <ul>
   *   <li>Calls {@link Extractor#seek(long, long)} and {@link Extractor#release()} without calling
   *       {@link Extractor#init(ExtractorOutput)} to check these calls do not fail.
   *   <li>Calls {@link #assertOutput(Extractor, String, byte[], Context, boolean, boolean, boolean,
   *       boolean)} with all possible combinations of "simulate" parameters.
   * </ul>
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param file The path to the input sample.
   * @param dumpFilesPrefix The dump files prefix appended to the dump files path.
   * @throws IOException If reading from the input fails.
   */
  public static void assertAllBehaviors(
      ExtractorFactory factory, String file, String dumpFilesPrefix) throws IOException {
    // Check behavior prior to initialization.
    Extractor extractor = factory.create();
    extractor.seek(0, 0);
    extractor.release();
    // Assert output.
    Context context = ApplicationProvider.getApplicationContext();
    byte[] fileData = TestUtil.getByteArray(context, file);
    assertOutput(factory, dumpFilesPrefix, fileData, context);
  }

  /**
   * Asserts that an extractor consumes valid input data successfully under the conditions specified
   * by {@code config}.
   *
   * <p>The output of the extractor is compared against prerecorded dump files whose names are
   * derived from the {@code file} parameter.
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param file The path to the input sample.
   * @param config Details on the environment to simulate and behaviours to assert.
   * @throws IOException If reading from the input fails.
   */
  public static void assertBehavior(ExtractorFactory factory, String file, Config config)
      throws IOException {
    assertBehavior(factory, file, config, file);
  }

  /**
   * Asserts that an extractor consumes valid input data successfully successfully under the
   * conditions specified by {@code config}.
   *
   * <p>The output of the extractor is compared against prerecorded dump files.
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param file The path to the input sample.
   * @param config Details on the environment to simulate and behaviours to assert.
   * @param dumpFilesPrefix The dump files prefix prepended to the dump files path.
   * @throws IOException If reading from the input fails.
   */
  public static void assertBehavior(
      ExtractorFactory factory, String file, Config config, String dumpFilesPrefix)
      throws IOException {
    // Check behavior prior to initialization.
    Extractor extractor = factory.create();
    extractor.seek(0, 0);
    extractor.release();
    // Assert output.
    Context context = ApplicationProvider.getApplicationContext();
    byte[] fileData = TestUtil.getByteArray(context, file);
    assertOutput(
        factory.create(),
        dumpFilesPrefix,
        fileData,
        context,
        config.sniffFirst,
        config.simulateIOErrors,
        config.simulateUnknownLength,
        config.simulatePartialReads);
  }

  /**
   * Calls {@link #assertOutput(Extractor, String, byte[], Context, boolean, boolean, boolean,
   * boolean)} with all possible combinations of "simulate" parameters with {@code sniffFirst} set
   * to true, and makes one additional call with the "simulate" and {@code sniffFirst} parameters
   * all set to false.
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param dumpFilesPrefix The dump files prefix appended to the dump files path.
   * @param data Content of the input file.
   * @param context To be used to load the sample file.
   * @throws IOException If reading from the input fails.
   */
  private static void assertOutput(
      ExtractorFactory factory, String dumpFilesPrefix, byte[] data, Context context)
      throws IOException {
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, false, false, false);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, false, false, true);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, false, true, false);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, false, true, true);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, true, false, false);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, true, false, true);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, true, true, false);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, true, true, true, true);
    assertOutput(factory.create(), dumpFilesPrefix, data, context, false, false, false, false);
  }

  /**
   * Asserts that an extractor consumes valid input data successfully under the specified
   * conditions.
   *
   * @param extractor The {@link Extractor} to be tested.
   * @param dumpFilesPrefix The dump files prefix prepended to the dump files path.
   * @param data Content of the input file.
   * @param context To be used to load the sample file.
   * @param sniffFirst Whether to sniff the data by calling {@link Extractor#sniff(ExtractorInput)}
   *     prior to consuming it.
   * @param simulateIOErrors Whether to simulate IO errors.
   * @param simulateUnknownLength Whether to simulate unknown input length.
   * @param simulatePartialReads Whether to simulate partial reads.
   * @return The {@link FakeExtractorOutput} used in the test.
   * @throws IOException If reading from the input fails.
   */
  private static FakeExtractorOutput assertOutput(
      Extractor extractor,
      String dumpFilesPrefix,
      byte[] data,
      Context context,
      boolean sniffFirst,
      boolean simulateIOErrors,
      boolean simulateUnknownLength,
      boolean simulatePartialReads)
      throws IOException {
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data)
        .setSimulateIOErrors(simulateIOErrors)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(simulatePartialReads).build();

    if (sniffFirst) {
      assertSniff(extractor, input, /* expectedResult= */ true);
      input.resetPeekPosition();
    }

    FakeExtractorOutput extractorOutput = consumeTestData(extractor, input, 0, true);
    if (simulateUnknownLength) {
      extractorOutput.assertOutput(context, dumpFilesPrefix + UNKNOWN_LENGTH_EXTENSION);
    } else {
      extractorOutput.assertOutput(context, dumpFilesPrefix + ".0" + DUMP_EXTENSION);
    }

    // Seeking to (timeUs=0, position=0) should always work, and cause the same data to be output.
    extractorOutput.clearTrackOutputs();
    input.reset();
    consumeTestData(extractor, input, /* timeUs= */ 0, extractorOutput, false);
    if (simulateUnknownLength) {
      extractorOutput.assertOutput(context, dumpFilesPrefix + UNKNOWN_LENGTH_EXTENSION);
    } else {
      extractorOutput.assertOutput(context, dumpFilesPrefix + ".0" + DUMP_EXTENSION);
    }

    // If the SeekMap is seekable, test seeking in the stream.
    SeekMap seekMap = Assertions.checkNotNull(extractorOutput.seekMap);
    if (seekMap.isSeekable()) {
      long durationUs = seekMap.getDurationUs();
      for (int j = 0; j < 4; j++) {
        extractorOutput.clearTrackOutputs();
        long timeUs = durationUs == C.TIME_UNSET ? 0 : (durationUs * j) / 3;
        long position = seekMap.getSeekPoints(timeUs).first.position;
        input.reset();
        input.setPosition((int) position);
        consumeTestData(extractor, input, timeUs, extractorOutput, false);
        extractorOutput.assertOutput(context, dumpFilesPrefix + '.' + j + DUMP_EXTENSION);
        if (durationUs == C.TIME_UNSET) {
          break;
        }
      }
    }

    return extractorOutput;
  }

  private ExtractorAsserts() {}

  private static FakeExtractorOutput consumeTestData(
      Extractor extractor, FakeExtractorInput input, long timeUs, boolean retryFromStartIfLive)
      throws IOException {
    FakeExtractorOutput output = new FakeExtractorOutput();
    extractor.init(output);
    consumeTestData(extractor, input, timeUs, output, retryFromStartIfLive);
    return output;
  }

  private static void consumeTestData(
      Extractor extractor,
      FakeExtractorInput input,
      long timeUs,
      FakeExtractorOutput output,
      boolean retryFromStartIfLive)
      throws IOException {
    extractor.seek(input.getPosition(), timeUs);
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      try {
        // Extractor.read should not read seekPositionHolder.position. Set it to a value that's
        // likely to cause test failure if a read does occur.
        seekPositionHolder.position = Long.MIN_VALUE;
        readResult = extractor.read(input, seekPositionHolder);
        if (readResult == Extractor.RESULT_SEEK) {
          long seekPosition = seekPositionHolder.position;
          Assertions.checkState(0 <= seekPosition && seekPosition <= Integer.MAX_VALUE);
          input.setPosition((int) seekPosition);
        }
      } catch (SimulatedIOException e) {
        if (!retryFromStartIfLive) {
          continue;
        }
        boolean isOnDemand = input.getLength() != C.LENGTH_UNSET
            || (output.seekMap != null && output.seekMap.getDurationUs() != C.TIME_UNSET);
        if (isOnDemand) {
          continue;
        }
        input.setPosition(0);
        for (int i = 0; i < output.numberOfTracks; i++) {
          output.trackOutputs.valueAt(i).clear();
        }
        extractor.seek(0, 0);
      }
    }
  }
}
