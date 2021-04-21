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

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.annotation.Nullable;
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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Assertion methods for {@link Extractor}. */
public final class ExtractorAsserts {

  /**
   * Returns a list of arrays containing {@link SimulationConfig} objects to exercise different
   * extractor paths.
   *
   * <p>This is intended to be used from tests using {@code ParameterizedRobolectricTestRunner} or
   * {@code org.junit.runners.Parameterized}.
   */
  public static ImmutableList<SimulationConfig> configs() {
    return ImmutableList.of(
        new SimulationConfig(true, false, false, false),
        new SimulationConfig(true, false, false, true),
        new SimulationConfig(true, false, true, false),
        new SimulationConfig(true, false, true, true),
        new SimulationConfig(true, true, false, false),
        new SimulationConfig(true, true, false, true),
        new SimulationConfig(true, true, true, false),
        new SimulationConfig(true, true, true, true),
        new SimulationConfig(false, false, false, false));
  }

  /**
   * Returns a list of arrays containing {@link SimulationConfig} objects to exercise different
   * extractor paths in which the input is not sniffed.
   *
   * <p>This is intended to be used from tests using {@code ParameterizedRobolectricTestRunner} or
   * {@code org.junit.runners.Parameterized}.
   */
  public static List<Object[]> configsNoSniffing() {
    return Arrays.asList(
        new Object[] {new SimulationConfig(false, false, false, false)},
        new Object[] {new SimulationConfig(false, false, false, true)},
        new Object[] {new SimulationConfig(false, false, true, false)},
        new Object[] {new SimulationConfig(false, false, true, true)},
        new Object[] {new SimulationConfig(false, true, false, false)},
        new Object[] {new SimulationConfig(false, true, false, true)},
        new Object[] {new SimulationConfig(false, true, true, false)},
        new Object[] {new SimulationConfig(false, true, true, true)});
  }

  /** A config of different environments to simulate and extractor behaviours to test. */
  public static class SimulationConfig {

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

    private SimulationConfig(
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

  /** A config for the assertions made (e.g. dump file location). */
  public static class AssertionConfig {

    /**
     * The prefix prepended to the dump files path. If not set, the path to the source data will be
     * used to derive this assuming the following path structure:
     *
     * <ul>
     *   <li>Media: {@code media/$mediapath}
     *   <li>Dumps: {@code extractordumps/$mediapath}
     * </ul>
     */
    @Nullable public final String dumpFilesPrefix;

    /**
     * Controls how consecutive formats with no intervening samples are handled. If true, only the
     * last format received is retained. If false, consecutive formats with no samples cause the
     * test to fail.
     */
    public final boolean deduplicateConsecutiveFormats;

    private AssertionConfig(
        @Nullable String dumpFilesPrefix, boolean deduplicateConsecutiveFormats) {
      this.dumpFilesPrefix = dumpFilesPrefix;
      this.deduplicateConsecutiveFormats = deduplicateConsecutiveFormats;
    }

    /** Builder for {@link AssertionConfig} instances. */
    public static class Builder {
      private @MonotonicNonNull String dumpFilesPrefix;
      private boolean deduplicateConsecutiveFormats;

      public Builder setDumpFilesPrefix(String dumpFilesPrefix) {
        this.dumpFilesPrefix = dumpFilesPrefix;
        return this;
      }

      public Builder setDeduplicateConsecutiveFormats(boolean deduplicateConsecutiveFormats) {
        this.deduplicateConsecutiveFormats = deduplicateConsecutiveFormats;
        return this;
      }

      public AssertionConfig build() {
        return new AssertionConfig(dumpFilesPrefix, deduplicateConsecutiveFormats);
      }
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
    long originalPosition = input.getPosition();
    while (true) {
      try {
        assertThat(extractor.sniff(input)).isEqualTo(expectedResult);
        if (!expectedResult) {
          assertThat(input.getPosition()).isEqualTo(originalPosition);
        }
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
   *       boolean, boolean)} with all possible combinations of "simulate" parameters.
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
   *       boolean, boolean)} with all possible combinations of "simulate" parameters.
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
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, false, false, false);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, false, false, true);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, false, true, false);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, false, true, true);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, true, false, false);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, true, false, true);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, true, true, false);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, true, true, true, true);
    assertOutput(
        factory.create(), dumpFilesPrefix, fileData, context, false, false, false, false, false);
  }

  /**
   * Asserts that an extractor consumes valid input data successfully under the conditions specified
   * by {@code simulationConfig}.
   *
   * <p>The output of the extractor is compared against prerecorded dump files whose names are
   * derived from the {@code file} parameter as specified in the docs for {@link
   * AssertionConfig#dumpFilesPrefix}.
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param file The path to the input sample.
   * @param simulationConfig Details on the environment to simulate and behaviours to assert.
   * @throws IOException If reading from the input fails.
   */
  public static void assertBehavior(
      ExtractorFactory factory, String file, SimulationConfig simulationConfig) throws IOException {
    assertBehavior(factory, file, new AssertionConfig.Builder().build(), simulationConfig);
  }

  /**
   * Asserts that an extractor consumes valid input data successfully successfully under the
   * conditions specified by {@code simulationConfig}.
   *
   * <p>The output of the extractor is compared against prerecorded dump files.
   *
   * @param assertionConfig Details of how to read and process the source and dump files.
   * @param simulationConfig Details on the environment to simulate and behaviours to assert.
   * @throws IOException If reading from the input fails.
   */
  public static void assertBehavior(
      ExtractorFactory factory,
      String file,
      AssertionConfig assertionConfig,
      SimulationConfig simulationConfig)
      throws IOException {
    // Check behavior prior to initialization.
    Extractor extractor = factory.create();
    extractor.seek(0, 0);
    extractor.release();
    // Assert output.
    Context context = ApplicationProvider.getApplicationContext();
    byte[] fileData = TestUtil.getByteArray(context, file);
    String dumpFilesPrefix;
    if (assertionConfig.dumpFilesPrefix != null) {
      dumpFilesPrefix = assertionConfig.dumpFilesPrefix;
    } else {
      String[] path = file.split("/");
      checkState(
          path.length > 0 && path[0].equals("media"),
          "AssertionConfig.dumpFilesPrefix == null but file isn't in a media/ sub-directory.\n"
              + "Expected : 'media/<path-to-file>'\n"
              + "Found    : '"
              + file
              + "'\n"
              + "You need to set AssertionConfig.dumpFilesPrefix explicitly if your media and dump"
              + " file aren't located in the expected structure (see docs on"
              + " AssertionConfig.dumpFilesPrefix)");
      path[0] = "extractordumps";
      dumpFilesPrefix = Joiner.on('/').join(path);
    }
    assertOutput(
        factory.create(),
        dumpFilesPrefix,
        fileData,
        context,
        assertionConfig.deduplicateConsecutiveFormats,
        simulationConfig.sniffFirst,
        simulationConfig.simulateIOErrors,
        simulationConfig.simulateUnknownLength,
        simulationConfig.simulatePartialReads);
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
   * @throws IOException If reading from the input fails.
   */
  private static void assertOutput(
      Extractor extractor,
      String dumpFilesPrefix,
      byte[] data,
      Context context,
      boolean deduplicateConsecutiveFormats,
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

    FakeExtractorOutput extractorOutput =
        consumeTestData(extractor, input, 0, true, deduplicateConsecutiveFormats);
    if (simulateUnknownLength) {
      DumpFileAsserts.assertOutput(
          context, extractorOutput, dumpFilesPrefix + UNKNOWN_LENGTH_EXTENSION);
    } else {
      DumpFileAsserts.assertOutput(
          context, extractorOutput, dumpFilesPrefix + ".0" + DUMP_EXTENSION);
    }

    // Seeking to (timeUs=0, position=0) should always work, and cause the same data to be output.
    extractorOutput.clearTrackOutputs();
    input.reset();
    consumeTestData(extractor, input, /* timeUs= */ 0, extractorOutput, false);
    if (simulateUnknownLength) {
      DumpFileAsserts.assertOutput(
          context, extractorOutput, dumpFilesPrefix + UNKNOWN_LENGTH_EXTENSION);
    } else {
      DumpFileAsserts.assertOutput(
          context, extractorOutput, dumpFilesPrefix + ".0" + DUMP_EXTENSION);
    }

    SeekMap seekMap = Assertions.checkNotNull(extractorOutput.seekMap);
    long durationUs = seekMap.getDurationUs();
    // Only seek to the timeUs=0 if the SeekMap is unseekable or the duration is unknown.
    int numberSeekTests = seekMap.isSeekable() && durationUs != C.TIME_UNSET ? 4 : 1;
    for (int j = 0; j < numberSeekTests; j++) {
      long timeUs = durationUs * j / 3;
      long position = seekMap.getSeekPoints(timeUs).first.position;
      if (timeUs == 0 && position == 0) {
        // Already tested.
        continue;
      }
      input.reset();
      input.setPosition((int) position);
      extractorOutput.clearTrackOutputs();
      consumeTestData(extractor, input, timeUs, extractorOutput, false);
      if (simulateUnknownLength && timeUs == 0) {
        DumpFileAsserts.assertOutput(
            context, extractorOutput, dumpFilesPrefix + UNKNOWN_LENGTH_EXTENSION);
      } else {
        DumpFileAsserts.assertOutput(
            context, extractorOutput, dumpFilesPrefix + '.' + j + DUMP_EXTENSION);
      }
    }
  }

  private ExtractorAsserts() {}

  private static FakeExtractorOutput consumeTestData(
      Extractor extractor,
      FakeExtractorInput input,
      long timeUs,
      boolean retryFromStartIfLive,
      boolean deduplicateConsecutiveFormats)
      throws IOException {
    FakeExtractorOutput output =
        new FakeExtractorOutput((id, type) -> new FakeTrackOutput(deduplicateConsecutiveFormats));
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
          checkState(0 <= seekPosition && seekPosition <= Integer.MAX_VALUE);
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
