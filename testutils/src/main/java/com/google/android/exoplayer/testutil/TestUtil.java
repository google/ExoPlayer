/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.testutil;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.app.Instrumentation;
import android.test.InstrumentationTestCase;
import junit.framework.Assert;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * Utility methods for tests.
 */
public class TestUtil {

  /**
   * A factory for {@link Extractor} instances.
   */
  public interface ExtractorFactory {
    Extractor create();
  }

  private static final String DUMP_EXTENSION = ".dump";
  private static final String UNKNOWN_LENGTH_EXTENSION = ".unklen" + DUMP_EXTENSION;

  private TestUtil() {}

  public static boolean sniffTestData(Extractor extractor, byte[] data)
      throws IOException, InterruptedException {
    return sniffTestData(extractor, newExtractorInput(data));
  }

  public static boolean sniffTestData(Extractor extractor, FakeExtractorInput input)
      throws IOException, InterruptedException {
    while (true) {
      try {
        return extractor.sniff(input);
      } catch (SimulatedIOException e) {
        // Ignore.
      }
    }
  }

  public static FakeExtractorOutput consumeTestData(Extractor extractor, byte[] data)
      throws IOException, InterruptedException {
    return consumeTestData(extractor, newExtractorInput(data));
  }

  public static FakeExtractorOutput consumeTestData(Extractor extractor, FakeExtractorInput input)
      throws IOException, InterruptedException {
    return consumeTestData(extractor, input, false);
  }

  public static FakeExtractorOutput consumeTestData(Extractor extractor, FakeExtractorInput input,
      boolean retryFromStartIfLive) throws IOException, InterruptedException {
    FakeExtractorOutput output = new FakeExtractorOutput();
    extractor.init(output);

    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      try {
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
        boolean isOnDemand = input.getLength() != C.LENGTH_UNBOUNDED
            || (output.seekMap != null && output.seekMap.getDurationUs() != C.UNSET_TIME_US);
        if (isOnDemand) {
          continue;
        }
        input.setPosition(0);
        for (int i = 0; i < output.numberOfTracks; i++) {
          output.trackOutputs.valueAt(i).clear();
        }
        extractor.seek(0);
      }
    }
    return output;
  }

  public static byte[] buildTestData(int length) {
    return buildTestData(length, length);
  }

  public static byte[] buildTestData(int length, int seed) {
    return buildTestData(length, new Random(seed));
  }

  public static byte[] buildTestData(int length, Random random) {
    byte[] source = new byte[length];
    random.nextBytes(source);
    return source;
  }

  /**
   * Converts an array of integers in the range [0, 255] into an equivalent byte array.
   *
   * @param intArray An array of integers, all of which must be in the range [0, 255].
   * @return The equivalent byte array.
   */
  public static byte[] createByteArray(int... intArray) {
    byte[] byteArray = new byte[intArray.length];
    for (int i = 0; i < byteArray.length; i++) {
      Assertions.checkState(0x00 <= intArray[i] && intArray[i] <= 0xFF);
      byteArray[i] = (byte) intArray[i];
    }
    return byteArray;
  }

  public static byte[] joinByteArrays(byte[]... byteArrays) {
    int length = 0;
    for (byte[] byteArray : byteArrays) {
      length += byteArray.length;
    }
    byte[] joined = new byte[length];
    length = 0;
    for (byte[] byteArray : byteArrays) {
      System.arraycopy(byteArray, 0, joined, length, byteArray.length);
      length += byteArray.length;
    }
    return joined;
  }

  public static void setUpMockito(InstrumentationTestCase instrumentationTestCase) {
    // Workaround for https://code.google.com/p/dexmaker/issues/detail?id=2.
    System.setProperty("dexmaker.dexcache",
        instrumentationTestCase.getInstrumentation().getTargetContext().getCacheDir().getPath());
    MockitoAnnotations.initMocks(instrumentationTestCase);
  }

  public static boolean assetExists(Instrumentation instrumentation, String fileName)
      throws IOException {
    int i = fileName.lastIndexOf('/');
    String path = i >= 0 ? fileName.substring(0, i) : "";
    String file = i >= 0 ? fileName.substring(i + 1) : fileName;
    return Arrays.asList(instrumentation.getContext().getResources().getAssets().list(path))
        .contains(file);
  }

  public static byte[] getByteArray(Instrumentation instrumentation, String fileName)
      throws IOException {
    InputStream is = instrumentation.getContext().getResources().getAssets().open(fileName);
    return Util.toByteArray(is);
  }

  public static String getString(Instrumentation instrumentation, String fileName)
      throws IOException {
    return new String(getByteArray(instrumentation, fileName));
  }

  private static FakeExtractorInput newExtractorInput(byte[] data) {
    return new FakeExtractorInput.Builder().setData(data).build();
  }

  /**
   * Calls {@link #assertOutput(Extractor, String, Instrumentation, boolean, boolean, boolean)} with
   * all possible combinations of "simulate" parameters.
   *
   * @param factory An {@link ExtractorFactory} which creates instances of the {@link Extractor}
   *     class which is to be tested.
   * @param sampleFile The path to the input sample.
   * @param instrumentation To be used to load the sample file.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If interrupted while reading from the input.
   * @see #assertOutput(Extractor, String, Instrumentation, boolean, boolean, boolean)
   */
  public static void assertOutput(ExtractorFactory factory, String sampleFile,
      Instrumentation instrumentation) throws IOException, InterruptedException {
    assertOutput(factory.create(), sampleFile, instrumentation, false, false, false);
    assertOutput(factory.create(), sampleFile, instrumentation,  true, false, false);
    assertOutput(factory.create(), sampleFile, instrumentation, false,  true, false);
    assertOutput(factory.create(), sampleFile, instrumentation,  true,  true, false);
    assertOutput(factory.create(), sampleFile, instrumentation, false, false,  true);
    assertOutput(factory.create(), sampleFile, instrumentation,  true, false,  true);
    assertOutput(factory.create(), sampleFile, instrumentation, false,  true,  true);
    assertOutput(factory.create(), sampleFile, instrumentation,  true,  true,  true);
  }

  /**
   * Asserts that {@code extractor} consumes {@code sampleFile} successfully and its output equals
   * to a prerecorded output dump file with the name {@code sampleFile} + "{@value
   * #DUMP_EXTENSION}". If {@code simulateUnknownLength} is true and {@code sampleFile} + "{@value
   * #UNKNOWN_LENGTH_EXTENSION}" exists, it's preferred.
   *
   * @param extractor The {@link Extractor} to be tested.
   * @param sampleFile The path to the input sample.
   * @param instrumentation To be used to load the sample file.
   * @param simulateIOErrors If true simulates IOErrors.
   * @param simulateUnknownLength If true simulates unknown input length.
   * @param simulatePartialReads If true simulates partial reads.
   * @return The {@link FakeExtractorOutput} used in the test.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If interrupted while reading from the input.
   */
  public static FakeExtractorOutput assertOutput(Extractor extractor, String sampleFile,
      Instrumentation instrumentation, boolean simulateIOErrors, boolean simulateUnknownLength,
      boolean simulatePartialReads) throws IOException, InterruptedException {
    byte[] fileData = getByteArray(instrumentation, sampleFile);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(fileData)
        .setSimulateIOErrors(simulateIOErrors)
        .setSimulateUnknownLength(simulateUnknownLength)
        .setSimulatePartialReads(simulatePartialReads).build();

    Assert.assertTrue(sniffTestData(extractor, input));
    input.resetPeekPosition();
    FakeExtractorOutput extractorOutput = consumeTestData(extractor, input, true);

    if (simulateUnknownLength
        && assetExists(instrumentation, sampleFile + UNKNOWN_LENGTH_EXTENSION)) {
      extractorOutput.assertOutput(instrumentation, sampleFile + UNKNOWN_LENGTH_EXTENSION);
    } else {
      extractorOutput.assertOutput(instrumentation, sampleFile + DUMP_EXTENSION);
    }

    return extractorOutput;
  }

}
