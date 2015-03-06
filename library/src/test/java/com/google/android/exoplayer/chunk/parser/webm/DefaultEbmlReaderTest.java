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
package com.google.android.exoplayer.chunk.parser.webm;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.upstream.ByteArrayNonBlockingInputStream;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link DefaultEbmlReader}.
 */
public class DefaultEbmlReaderTest extends TestCase {

  private final EventCapturingEbmlEventHandler eventHandler =
      new EventCapturingEbmlEventHandler();

  public void testNothing() {
    NonBlockingInputStream input = createTestInputStream();
    assertNoEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM);
  }

  public void testMasterElement() {
    NonBlockingInputStream input =
        createTestInputStream(0x1A, 0x45, 0xDF, 0xA3, 0x84, 0x42, 0x85, 0x81, 0x01);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onMasterElementStart(EventCapturingEbmlEventHandler.ID_EBML, 0, 5, 4);
    expected.onIntegerElement(EventCapturingEbmlEventHandler.ID_DOC_TYPE_READ_VERSION, 1);
    expected.onMasterElementEnd(EventCapturingEbmlEventHandler.ID_EBML);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testMasterElementEmpty() {
    NonBlockingInputStream input = createTestInputStream(0x18, 0x53, 0x80, 0x67, 0x80);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onMasterElementStart(EventCapturingEbmlEventHandler.ID_SEGMENT, 0, 5, 0);
    expected.onMasterElementEnd(EventCapturingEbmlEventHandler.ID_SEGMENT);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testUnsignedIntegerElement() {
    // 0xFE is chosen because for signed integers it should be interpreted as -2
    NonBlockingInputStream input = createTestInputStream(0x42, 0xF7, 0x81, 0xFE);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onIntegerElement(EventCapturingEbmlEventHandler.ID_EBML_READ_VERSION, 254);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testUnsignedIntegerElementLarge() {
    NonBlockingInputStream input =
        createTestInputStream(0x42, 0xF7, 0x88, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onIntegerElement(EventCapturingEbmlEventHandler.ID_EBML_READ_VERSION, Long.MAX_VALUE);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testUnsignedIntegerElementTooLargeBecomesNegative() {
    NonBlockingInputStream input =
        createTestInputStream(0x42, 0xF7, 0x88, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onIntegerElement(EventCapturingEbmlEventHandler.ID_EBML_READ_VERSION, -1);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testStringElement() {
    NonBlockingInputStream input =
        createTestInputStream(0x42, 0x82, 0x86, 0x41, 0x62, 0x63, 0x31, 0x32, 0x33);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onStringElement(EventCapturingEbmlEventHandler.ID_DOC_TYPE, "Abc123");
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testStringElementEmpty() {
    NonBlockingInputStream input = createTestInputStream(0x42, 0x82, 0x80);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onStringElement(EventCapturingEbmlEventHandler.ID_DOC_TYPE, "");
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testFloatElementThreeBytes() {
    try {
      eventHandler.read(createTestInputStream(0x44, 0x89, 0x83, 0x3F, 0x80, 0x00));
      fail();
    } catch (IllegalStateException exception) {
      // Expected
    }
    assertNoEvents();
  }

  public void testFloatElementFourBytes() {
    NonBlockingInputStream input =
        createTestInputStream(0x44, 0x89, 0x84, 0x3F, 0x80, 0x00, 0x00);
    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onFloatElement(EventCapturingEbmlEventHandler.ID_DURATION, 1.0);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testFloatElementEightBytes() {
    NonBlockingInputStream input =
        createTestInputStream(0x44, 0x89, 0x88, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);

    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.onFloatElement(EventCapturingEbmlEventHandler.ID_DURATION, -2.0);
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testBinaryElementReadBytes() {
    eventHandler.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_READ_BYTES;
    NonBlockingInputStream input =
        createTestInputStream(0xA3, 0x88, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);

    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_READ_BYTES;
    expected.onBinaryElement(
        EventCapturingEbmlEventHandler.ID_SIMPLE_BLOCK, 0, 0, 8,
        createTestInputStream(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08));
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testBinaryElementReadVarint() {
    eventHandler.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_READ_VARINT;
    NonBlockingInputStream input = createTestInputStream(0xA3, 0x82, 0x40, 0x2A);

    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_READ_VARINT;
    expected.onBinaryElement(
        EventCapturingEbmlEventHandler.ID_SIMPLE_BLOCK, 0, 0, 0,
        createTestInputStream(0x40, 0x2A));
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testBinaryElementSkipBytes() {
    eventHandler.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_SKIP_BYTES;
    NonBlockingInputStream input =
        createTestInputStream(0xA3, 0x88, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);

    EventCapturingEbmlEventHandler expected = new EventCapturingEbmlEventHandler();
    expected.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_SKIP_BYTES;
    expected.onBinaryElement(
        EventCapturingEbmlEventHandler.ID_SIMPLE_BLOCK, 0, 0, 8,
        createTestInputStream(0, 0, 0, 0, 0, 0, 0, 0));
    assertEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM, expected.events);
  }

  public void testBinaryElementDoNothing() {
    eventHandler.binaryElementHandler = EventCapturingEbmlEventHandler.HANDLER_DO_NOTHING;
    try {
      eventHandler.read(
          createTestInputStream(0xA3, 0x88, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08));
      fail();
    } catch (IllegalStateException exception) {
      // Expected
    }
    assertNoEvents();
  }

  public void testBinaryElementNotEnoughBytes() {
    NonBlockingInputStream input = createTestInputStream(0xA3, 0x88, 0x01, 0x02, 0x03);
    assertNoEvents(input, EbmlReader.READ_RESULT_NEED_MORE_DATA);
  }

  public void testUnknownElement() {
    NonBlockingInputStream input = createTestInputStream(0xEC, 0x81, 0x00);
    assertNoEvents(input, EbmlReader.READ_RESULT_END_OF_STREAM);
  }

  /**
   * Helper to build a {@link ByteArrayNonBlockingInputStream} quickly from zero or more
   * integer arguments.
   *
   * <p>Each argument must be able to cast to a byte value.
   *
   * @param data Zero or more integers with values between {@code 0x00} and {@code 0xFF}
   * @return A {@link ByteArrayNonBlockingInputStream} containing the given byte values
   */
  private NonBlockingInputStream createTestInputStream(int... data) {
    byte[] bytes = new byte[data.length];
    for (int i = 0; i < data.length; i++) {
      bytes[i] = (byte) data[i];
    }
    return new ByteArrayNonBlockingInputStream(bytes);
  }

  private void assertReads(NonBlockingInputStream input, int continues, int finalResult) {
    for (int i = 0; i < continues; i++) {
      assertEquals(EbmlReader.READ_RESULT_CONTINUE, eventHandler.read(input));
    }
    assertEquals(finalResult, eventHandler.read(input));
  }

  private void assertNoEvents() {
    assertEvents(Collections.<String>emptyList());
  }

  private void assertEvents(List<String> events) {
    assertEquals(events.size(), eventHandler.events.size());
    for (int i = 0; i < events.size(); i++) {
      assertEquals(events.get(i), eventHandler.events.get(i));
    }
  }

  private void assertNoEvents(NonBlockingInputStream input, int finalResult) {
    assertReads(input, 0, finalResult);
    assertNoEvents();
  }

  private void assertEvents(NonBlockingInputStream input, int finalResult, List<String> events) {
    assertReads(input, events.size(), finalResult);
    assertEvents(events);
  }

  /**
   * An {@link EbmlEventHandler} which captures all event callbacks made by
   * {@link DefaultEbmlReader} for testing purposes.
   */
  private static final class EventCapturingEbmlEventHandler implements EbmlEventHandler {

    // Element IDs
    private static final int ID_EBML = 0x1A45DFA3;
    private static final int ID_EBML_READ_VERSION = 0x42F7;
    private static final int ID_DOC_TYPE = 0x4282;
    private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;

    private static final int ID_SEGMENT = 0x18538067;
    private static final int ID_DURATION = 0x4489;
    private static final int ID_SIMPLE_BLOCK = 0xA3;

    // Various ways to handle things in onBinaryElement()
    private static final int HANDLER_DO_NOTHING = 0;
    private static final int HANDLER_READ_BYTES = 1;
    private static final int HANDLER_READ_VARINT = 2;
    private static final int HANDLER_SKIP_BYTES = 3;

    private final EbmlReader reader = new DefaultEbmlReader();
    private final List<String> events = new ArrayList<String>();

    private int binaryElementHandler;

    private EventCapturingEbmlEventHandler() {
      reader.setEventHandler(this);
    }

    private int read(NonBlockingInputStream inputStream) {
      try {
        return reader.read(inputStream);
      } catch (ParserException e) {
        // should never happen.
        fail();
        return -1;
      }
    }

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
    public void onMasterElementStart(
        int id, long elementOffset, int headerSize, long contentsSize) {
      events.add(formatEvent(id, "start elementOffset=" + elementOffset
          + " headerSize=" + headerSize + " contentsSize=" + contentsSize));
    }

    @Override
    public void onMasterElementEnd(int id) {
      events.add(formatEvent(id, "end"));
    }

    @Override
    public void onIntegerElement(int id, long value) {
      events.add(formatEvent(id, "integer=" + String.valueOf(value)));
    }

    @Override
    public void onFloatElement(int id, double value) {
      events.add(formatEvent(id, "float=" + String.valueOf(value)));
    }

    @Override
    public void onStringElement(int id, String value) {
      events.add(formatEvent(id, "string=" + value));
    }

    @Override
    public boolean onBinaryElement(
        int id, long elementOffset, int headerSize, int contentsSize,
        NonBlockingInputStream inputStream) {
      switch (binaryElementHandler) {
        case HANDLER_READ_BYTES:
          byte[] bytes = new byte[contentsSize];
          reader.readBytes(inputStream, bytes, contentsSize);
          events.add(formatEvent(id, "bytes=" + Arrays.toString(bytes)));
          break;
        case HANDLER_READ_VARINT:
          long value = reader.readVarint(inputStream);
          events.add(formatEvent(id, "varint=" + String.valueOf(value)));
          break;
        case HANDLER_SKIP_BYTES:
          reader.skipBytes(inputStream, contentsSize);
          events.add(formatEvent(id, "skipped " + contentsSize + " byte(s)"));
          break;
        case HANDLER_DO_NOTHING:
        default:
          // pass
      }
      return true;
    }

    private static String formatEvent(int id, String event) {
      return "[" + Integer.toHexString(id) + "] " + event;
    }

  }

}
