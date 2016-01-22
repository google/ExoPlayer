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
import com.google.android.exoplayer.extractor.DefaultExtractorInput;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Util;

import android.app.Instrumentation;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/**
 * Utility methods for tests.
 */
public class TestUtil {

  private TestUtil() {}

  public static void consumeTestData(Extractor extractor, byte[] data)
      throws IOException, InterruptedException {
    ExtractorInput input = createTestExtractorInput(data);
    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = extractor.read(input, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input = createTestExtractorInput(data, (int) seekPositionHolder.position);
      }
    }
  }

  public static ExtractorInput createTestExtractorInput(byte[] data) throws IOException {
    return createTestExtractorInput(data, 0);
  }

  public static ExtractorInput createTestExtractorInput(byte[] data, int offset)
      throws IOException {
    if (offset != 0) {
      data = Arrays.copyOfRange(data, offset, data.length);
    }
    FakeDataSource dataSource = new FakeDataSource.Builder().appendReadData(data).build();
    dataSource.open(new DataSpec(Uri.parse("http://www.google.com")));
    ExtractorInput input = new DefaultExtractorInput(dataSource, offset, C.LENGTH_UNBOUNDED);
    return input;
  }

  public static byte[] buildTestData(int length) {
    return buildTestData(length, length);
  }

  public static byte[] buildTestData(int length, int seed) {
    Random random = new Random(seed);
    byte[] source = new byte[length];
    random.nextBytes(source);
    return source;
  }

  public static byte[] createByteArray(String hexBytes) {
    byte[] result = new byte[hexBytes.length() / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) ((Character.digit(hexBytes.charAt(i * 2), 16) << 4)
          + Character.digit(hexBytes.charAt(i * 2 + 1), 16));
    }
    return result;
  }

  public static byte[] createByteArray(int... intArray) {
    byte[] byteArray = new byte[intArray.length];
    for (int i = 0; i < byteArray.length; i++) {
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

  public static byte[] getByteArray(Instrumentation instrumentation, String fileName)
      throws IOException {
    InputStream is = instrumentation.getContext().getResources().getAssets().open(fileName);
    return Util.toByteArray(is);
  }

}
