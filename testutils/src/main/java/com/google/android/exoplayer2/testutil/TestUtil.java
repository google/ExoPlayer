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
package com.google.android.exoplayer2.testutil;

import android.app.Instrumentation;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.Listener;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import junit.framework.Assert;
import org.mockito.MockitoAnnotations;

/**
 * Utility methods for tests.
 */
public class TestUtil {

  private TestUtil() {}

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

  public static byte[] readToEnd(DataSource dataSource) throws IOException {
    byte[] data = new byte[1024];
    int position = 0;
    int bytesRead = 0;
    while (bytesRead != C.RESULT_END_OF_INPUT) {
      if (position == data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      bytesRead = dataSource.read(data, position, data.length - position);
      if (bytesRead != C.RESULT_END_OF_INPUT) {
        position += bytesRead;
      }
    }
    return Arrays.copyOf(data, position);
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

  public static String buildTestString(int maxLength, Random random) {
    int length = random.nextInt(maxLength);
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append((char) random.nextInt());
    }
    return builder.toString();
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

  public static byte[] getByteArray(Instrumentation instrumentation, String fileName)
      throws IOException {
    return Util.toByteArray(getInputStream(instrumentation, fileName));
  }

  public static InputStream getInputStream(Instrumentation instrumentation, String fileName)
      throws IOException {
    return instrumentation.getContext().getResources().getAssets().open(fileName);
  }

  public static String getString(Instrumentation instrumentation, String fileName)
      throws IOException {
    return new String(getByteArray(instrumentation, fileName));
  }

  /**
   * Extracts the timeline from a media source.
   */
  public static Timeline extractTimelineFromMediaSource(MediaSource mediaSource) {
    class TimelineListener implements Listener {
      private Timeline timeline;
      @Override
      public synchronized void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        this.timeline = timeline;
        this.notify();
      }
    }
    TimelineListener listener = new TimelineListener();
    mediaSource.prepareSource(null, true, listener);
    synchronized (listener) {
      while (listener.timeline == null) {
        try {
          listener.wait();
        } catch (InterruptedException e) {
          Assert.fail(e.getMessage());
        }
      }
    }
    return listener.timeline;
  }

  /**
   * Asserts that data read from a {@link DataSource} matches {@code expected}.
   *
   * @param dataSource The {@link DataSource} through which to read.
   * @param dataSpec The {@link DataSpec} to use when opening the {@link DataSource}.
   * @param expectedData The expected data.
   * @throws IOException If an error occurs reading fom the {@link DataSource}.
   */
  public static void assertDataSourceContent(DataSource dataSource, DataSpec dataSpec,
      byte[] expectedData) throws IOException {
    try {
      long length = dataSource.open(dataSpec);
      Assert.assertEquals(length, expectedData.length);
      byte[] readData = TestUtil.readToEnd(dataSource);
      MoreAsserts.assertEquals(expectedData, readData);
    } finally {
      dataSource.close();
    }
  }

}
