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
package com.google.android.exoplayer.extractor.webm;

import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.testutil.FakeExtractorInput;
import com.google.android.exoplayer.testutil.TestUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests {@link DefaultEbmlReader}.
 */
public class DefaultEbmlReaderTest extends TestCase {

  public void testMasterElement() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x1A, 0x45, 0xDF, 0xA3, 0x84, 0x42, 0x85, 0x81, 0x01);
    TestOutput expected = new TestOutput();
    expected.startMasterElement(TestOutput.ID_EBML, 5, 4);
    expected.integerElement(TestOutput.ID_DOC_TYPE_READ_VERSION, 1);
    expected.endMasterElement(TestOutput.ID_EBML);
    assertEvents(input, expected.events);
  }

  public void testMasterElementEmpty() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x18, 0x53, 0x80, 0x67, 0x80);
    TestOutput expected = new TestOutput();
    expected.startMasterElement(TestOutput.ID_SEGMENT, 5, 0);
    expected.endMasterElement(TestOutput.ID_SEGMENT);
    assertEvents(input, expected.events);
  }

  public void testUnsignedIntegerElement() throws IOException, InterruptedException {
    // 0xFE is chosen because for signed integers it should be interpreted as -2
    ExtractorInput input = createTestInput(0x42, 0xF7, 0x81, 0xFE);
    TestOutput expected = new TestOutput();
    expected.integerElement(TestOutput.ID_EBML_READ_VERSION, 254);
    assertEvents(input, expected.events);
  }

  public void testUnsignedIntegerElementLarge() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x42, 0xF7, 0x88, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    TestOutput expected = new TestOutput();
    expected.integerElement(TestOutput.ID_EBML_READ_VERSION, Long.MAX_VALUE);
    assertEvents(input, expected.events);
  }

  public void testUnsignedIntegerElementTooLargeBecomesNegative()
      throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x42, 0xF7, 0x88, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    TestOutput expected = new TestOutput();
    expected.integerElement(TestOutput.ID_EBML_READ_VERSION, -1);
    assertEvents(input, expected.events);
  }

  public void testStringElement() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x42, 0x82, 0x86, 0x41, 0x62, 0x63, 0x31, 0x32, 0x33);
    TestOutput expected = new TestOutput();
    expected.stringElement(TestOutput.ID_DOC_TYPE, "Abc123");
    assertEvents(input, expected.events);
  }

  public void testStringElementEmpty() throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(0x42, 0x82, 0x80);
    TestOutput expected = new TestOutput();
    expected.stringElement(TestOutput.ID_DOC_TYPE, "");
    assertEvents(input, expected.events);
  }

  public void testFloatElementFourBytes() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x44, 0x89, 0x84, 0x3F, 0x80, 0x00, 0x00);
    TestOutput expected = new TestOutput();
    expected.floatElement(TestOutput.ID_DURATION, 1.0);
    assertEvents(input, expected.events);
  }

  public void testFloatElementEightBytes() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0x44, 0x89, 0x88, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    TestOutput expected = new TestOutput();
    expected.floatElement(TestOutput.ID_DURATION, -2.0);
    assertEvents(input, expected.events);
  }

  public void testBinaryElement() throws IOException, InterruptedException {
    ExtractorInput input =
        createTestInput(0xA3, 0x88, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
    TestOutput expected = new TestOutput();
    expected.binaryElement(TestOutput.ID_SIMPLE_BLOCK, 8,
        createTestInput(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08));
    assertEvents(input, expected.events);
  }

  private static void assertEvents(ExtractorInput input, List<String> expectedEvents)
      throws IOException, InterruptedException {
    DefaultEbmlReader reader = new DefaultEbmlReader();
    TestOutput output = new TestOutput();
    reader.init(output);

    // We expect the number of successful reads to equal the number of expected events.
    for (int i = 0; i < expectedEvents.size(); i++) {
      assertTrue(reader.read(input));
    }
    // The next read should be unsuccessful.
    assertFalse(reader.read(input));
    // Check that we really did get to the end of input.
    assertFalse(input.readFully(new byte[1], 0, 1, true));

    assertEquals(expectedEvents.size(), output.events.size());
    for (int i = 0; i < expectedEvents.size(); i++) {
      assertEquals(expectedEvents.get(i), output.events.get(i));
    }
  }

  /**
   * Helper to build an {@link ExtractorInput} from byte data.
   *
   * @param data Zero or more integers with values between {@code 0x00} and {@code 0xFF}.
   * @return An {@link ExtractorInput} from which the data can be read.
   */
  private static ExtractorInput createTestInput(int... data) {
    return new FakeExtractorInput.Builder()
        .setData(TestUtil.createByteArray(data))
        .setSimulateUnknownLength(true)
        .build();
  }

  /**
   * An {@link EbmlReaderOutput} that records each event callback.
   */
  private static final class TestOutput implements EbmlReaderOutput {

    // Element IDs
    private static final int ID_EBML = 0x1A45DFA3;
    private static final int ID_EBML_READ_VERSION = 0x42F7;
    private static final int ID_DOC_TYPE = 0x4282;
    private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;

    private static final int ID_SEGMENT = 0x18538067;
    private static final int ID_DURATION = 0x4489;
    private static final int ID_SIMPLE_BLOCK = 0xA3;

    private final List<String> events = new ArrayList<>();

    @Override
    public int getElementType(int id) {
      switch (id) {
        case ID_EBML:
        case ID_SEGMENT:
          return EbmlReader.TYPE_MASTER;
        case ID_EBML_READ_VERSION:
        case ID_DOC_TYPE_READ_VERSION:
          return EbmlReader.TYPE_UNSIGNED_INT;
        case ID_DOC_TYPE:
          return EbmlReader.TYPE_STRING;
        case ID_SIMPLE_BLOCK:
          return EbmlReader.TYPE_BINARY;
        case ID_DURATION:
          return EbmlReader.TYPE_FLOAT;
        default:
          return EbmlReader.TYPE_UNKNOWN;
      }
    }

    @Override
    public boolean isLevel1Element(int id) {
      return false;
    }

    @Override
    public void startMasterElement(int id, long contentPosition, long contentSize) {
      events.add(formatEvent(id, "start contentPosition=" + contentPosition
          + " contentSize=" + contentSize));
    }

    @Override
    public void endMasterElement(int id) {
      events.add(formatEvent(id, "end"));
    }

    @Override
    public void integerElement(int id, long value) {
      events.add(formatEvent(id, "integer=" + String.valueOf(value)));
    }

    @Override
    public void floatElement(int id, double value) {
      events.add(formatEvent(id, "float=" + String.valueOf(value)));
    }

    @Override
    public void stringElement(int id, String value) {
      events.add(formatEvent(id, "string=" + value));
    }

    @Override
    public void binaryElement(int id, int contentSize, ExtractorInput input)
        throws IOException, InterruptedException {
      byte[] bytes = new byte[contentSize];
      input.readFully(bytes, 0, contentSize);
      events.add(formatEvent(id, "bytes=" + Arrays.toString(bytes)));
    }

    private static String formatEvent(int id, String event) {
      return "[" + Integer.toHexString(id) + "] " + event;
    }

  }

}
