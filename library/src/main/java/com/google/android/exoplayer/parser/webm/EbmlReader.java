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
package com.google.android.exoplayer.parser.webm;

import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Stack;

/**
 * An event-driven incremental EBML reader base class.
 *
 * <p>EBML can be summarized as a binary XML format somewhat similar to Protocol Buffers.
 * It was originally designed for the Matroska container format. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 */
public abstract class EbmlReader {

  // Element Types
  protected static final int TYPE_UNKNOWN = 0; // Undefined element.
  protected static final int TYPE_MASTER = 1;  // Contains child elements.
  protected static final int TYPE_UNSIGNED_INT = 2;
  protected static final int TYPE_STRING = 3;
  protected static final int TYPE_BINARY = 4;
  protected static final int TYPE_FLOAT = 5;

  // Return values for methods read, readElementId, readElementSize, readVarintBytes, and readBytes.
  protected static final int RESULT_CONTINUE = 0;
  protected static final int RESULT_NEED_MORE_DATA = 1;
  protected static final int RESULT_END_OF_FILE = 2;

  // State values used in variables state, elementIdState, elementContentSizeState, and
  // varintBytesState.
  private static final int STATE_BEGIN_READING = 0;
  private static final int STATE_READ_CONTENTS = 1;
  private static final int STATE_FINISHED_READING = 2;

  /**
   * The first byte of a variable-length integer (varint) will have one of these bit masks
   * indicating the total length in bytes. {@code 0x80} is a one-byte integer,
   * {@code 0x40} is two bytes, and so on up to eight bytes.
   */
  private static final int[] VARINT_LENGTH_MASKS = new int[] {
    0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
  };

  private final Stack<MasterElement> masterElementsStack = new Stack<MasterElement>();
  private final byte[] tempByteArray = new byte[8];

  private int state;
  private long bytesRead;
  private long elementOffset;
  private int elementId;
  private int elementIdState;
  private long elementContentSize;
  private int elementContentSizeState;
  private int varintBytesState;
  private int varintBytesLength;
  private int bytesState;
  private byte[] stringBytes;

  /**
   * Called to retrieve the type of an element ID. If {@link #TYPE_UNKNOWN} is returned then
   * the element is skipped. Note that all children of a skipped master element are also skipped.
   *
   * @param id The integer ID of this element.
   * @return One of the {@code TYPE_} constants defined in this class.
   */
  protected abstract int getElementType(int id);

  /**
   * Called when a master element is encountered in the {@link NonBlockingInputStream}.
   * Following events should be considered as taking place "within" this element until a
   * matching call to {@link #onMasterElementEnd(int)} is made. Note that it
   * is possible for the same master element to be nested within itself.
   *
   * @param id The integer ID of this element.
   * @param elementOffset The byte offset where this element starts.
   * @param headerSize The byte length of this element's ID and size header.
   * @param contentsSize The byte length of this element's children.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onMasterElementStart(
      int id, long elementOffset, int headerSize, int contentsSize);

  /**
   * Called when a master element has finished reading in all of its children from the
   * {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onMasterElementEnd(int id);

  /**
   * Called when an integer element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element.
   * @param value The integer value this element contains.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onIntegerElement(int id, long value);

  /**
   * Called when a float element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element.
   * @param value The float value this element contains.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onFloatElement(int id, double value);

  /**
   * Called when a string element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element.
   * @param value The string value this element contains.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onStringElement(int id, String value);

  /**
   * Called when a binary element is encountered in the {@link NonBlockingInputStream}.
   * The element header (containing element ID and content size) will already have been read.
   * Subclasses must exactly read the entire contents of the element, which is {@code contentsSize}
   * bytes in length. It's guaranteed that the full element contents will be immediately available
   * from {@code inputStream}.
   *
   * <p>Several methods are available for reading the contents of a binary element:
   * <ul>
   * <li>{@link #readVarint(NonBlockingInputStream)}.
   * <li>{@link #readBytes(NonBlockingInputStream, byte[], int)}.
   * <li>{@link #readBytes(NonBlockingInputStream, ByteBuffer, int)}.
   * <li>{@link #skipBytes(NonBlockingInputStream, int)}.
   * <li>{@link #getBytesRead()}.
   *
   * @param inputStream The {@link NonBlockingInputStream} from which this
   *        element's contents should be read.
   * @param id The integer ID of this element.
   * @param elementOffset The byte offset where this element starts.
   * @param headerSize The byte length of this element's ID and size header.
   * @param contentsSize The byte length of this element's contents.
   * @return {@code true} if parsing should continue or {@code false} if it should stop right away.
   */
  protected abstract boolean onBinaryElement(NonBlockingInputStream inputStream,
      int id, long elementOffset, int headerSize, int contentsSize);

  /**
   * Reads from a {@link NonBlockingInputStream} and calls event callbacks as needed.
   *
   * @param inputStream The input stream from which data should be read.
   * @return One of the {@code RESULT_*} flags defined in this class.
   */
  protected final int read(NonBlockingInputStream inputStream) {
    while (true) {
      while (masterElementsStack.size() > 0
          && bytesRead >= masterElementsStack.peek().elementEndOffset) {
        if (!onMasterElementEnd(masterElementsStack.pop().elementId)) {
          return RESULT_CONTINUE;
        }
      }

      if (state == STATE_BEGIN_READING) {
        final int resultId = readElementId(inputStream);
        if (resultId != RESULT_CONTINUE) {
          return resultId;
        }
        final int resultSize = readElementContentSize(inputStream);
        if (resultSize != RESULT_CONTINUE) {
          return resultSize;
        }
        state = STATE_READ_CONTENTS;
        bytesState = 0;
      }

      final int type = getElementType(elementId);
      switch (type) {

        case TYPE_MASTER:
          final int masterHeaderSize = (int) (bytesRead - elementOffset);
          masterElementsStack.add(new MasterElement(elementId, bytesRead + elementContentSize));
          if (!onMasterElementStart(
              elementId, elementOffset, masterHeaderSize, (int) elementContentSize)) {
            prepareForNextElement();
            return RESULT_CONTINUE;
          }
          break;

        case TYPE_UNSIGNED_INT:
          Assertions.checkState(elementContentSize <= 8);
          final int resultInt =
              readBytes(inputStream, null, tempByteArray, (int) elementContentSize);
          if (resultInt != RESULT_CONTINUE) {
            return resultInt;
          }
          final long intValue = parseTempByteArray((int) elementContentSize, false);
          if (!onIntegerElement(elementId, intValue)) {
            prepareForNextElement();
            return RESULT_CONTINUE;
          }
          break;

        case TYPE_FLOAT:
          Assertions.checkState(elementContentSize == 4 || elementContentSize == 8);
          final int resultFloat =
              readBytes(inputStream, null, tempByteArray, (int) elementContentSize);
          if (resultFloat != RESULT_CONTINUE) {
            return resultFloat;
          }
          final long valueBits = parseTempByteArray((int) elementContentSize, false);
          final double floatValue;
          if (elementContentSize == 4) {
            floatValue = Float.intBitsToFloat((int) valueBits);
          } else {
            floatValue = Double.longBitsToDouble(valueBits);
          }
          if (!onFloatElement(elementId, floatValue)) {
            prepareForNextElement();
            return RESULT_CONTINUE;
          }
          break;

        case TYPE_STRING:
          if (stringBytes == null) {
            stringBytes = new byte[(int) elementContentSize];
          }
          final int resultString =
              readBytes(inputStream, null, stringBytes, (int) elementContentSize);
          if (resultString != RESULT_CONTINUE) {
            return resultString;
          }
          final String stringValue = new String(stringBytes, Charset.forName("UTF-8"));
          stringBytes = null;
          if (!onStringElement(elementId, stringValue)) {
            prepareForNextElement();
            return RESULT_CONTINUE;
          }
          break;

        case TYPE_BINARY:
          if (inputStream.getAvailableByteCount() < elementContentSize) {
            return RESULT_NEED_MORE_DATA;
          }
          final int binaryHeaderSize = (int) (bytesRead - elementOffset);
          final boolean keepGoing = onBinaryElement(
              inputStream, elementId, elementOffset, binaryHeaderSize, (int) elementContentSize);
          Assertions.checkState(elementOffset + binaryHeaderSize + elementContentSize == bytesRead);
          if (!keepGoing) {
            prepareForNextElement();
            return RESULT_CONTINUE;
          }
          break;

        case TYPE_UNKNOWN:
          // Unknown elements should be skipped.
          Assertions.checkState(
              readBytes(inputStream, null, null, (int) elementContentSize) == RESULT_CONTINUE);
          break;

        default:
          throw new IllegalStateException("Invalid element type " + type);

      }
      prepareForNextElement();
    }
  }

  /**
   * @return The total number of bytes consumed by the reader since first created
   *        or last {@link #reset()}.
   */
  protected final long getBytesRead() {
    return bytesRead;
  }

  /**
   * Resets the entire state of the reader so that it will read a new EBML structure from scratch.
   * This includes resetting {@link #bytesRead} back to 0 and discarding all pending
   * {@link #onMasterElementEnd(int)} events.
   */
  protected final void reset() {
    prepareForNextElement();
    masterElementsStack.clear();
    bytesRead = 0;
  }

  /**
   * Reads, parses, and returns an EBML variable-length integer (varint) from the contents
   * of a binary element.
   *
   * @param inputStream The input stream from which data should be read.
   * @return The varint value at the current position of the contents of a binary element.
   */
  protected final long readVarint(NonBlockingInputStream inputStream) {
    varintBytesState = STATE_BEGIN_READING;
    Assertions.checkState(readVarintBytes(inputStream) == RESULT_CONTINUE);
    return parseTempByteArray(varintBytesLength, true);
  }

  /**
   * Reads a fixed number of bytes from the contents of a binary element into a {@link ByteBuffer}.
   *
   * @param inputStream The input stream from which data should be read.
   * @param byteBuffer The {@link ByteBuffer} to which data should be written.
   * @param totalBytes The fixed number of bytes to be read and written.
   */
  protected final void readBytes(
      NonBlockingInputStream inputStream, ByteBuffer byteBuffer, int totalBytes) {
    bytesState = 0;
    Assertions.checkState(readBytes(inputStream, byteBuffer, null, totalBytes) == RESULT_CONTINUE);
  }

  /**
   * Reads a fixed number of bytes from the contents of a binary element into a {@code byte[]}.
   *
   * @param inputStream The input stream from which data should be read.
   * @param byteArray The byte array to which data should be written.
   * @param totalBytes The fixed number of bytes to be read and written.
   */
  protected final void readBytes(
      NonBlockingInputStream inputStream, byte[] byteArray, int totalBytes) {
    bytesState = 0;
    Assertions.checkState(readBytes(inputStream, null, byteArray, totalBytes) == RESULT_CONTINUE);
  }

  /**
   * Skips a fixed number of bytes from the contents of a binary element.
   *
   * @param inputStream The input stream from which data should be skipped.
   * @param totalBytes The fixed number of bytes to be skipped.
   */
  protected final void skipBytes(NonBlockingInputStream inputStream, int totalBytes) {
    bytesState = 0;
    Assertions.checkState(readBytes(inputStream, null, null, totalBytes) == RESULT_CONTINUE);
  }

  /**
   * Resets the internal state of {@link #read(NonBlockingInputStream)} so that it can start
   * reading a new element from scratch.
   */
  private final void prepareForNextElement() {
    state = STATE_BEGIN_READING;
    elementIdState = STATE_BEGIN_READING;
    elementContentSizeState = STATE_BEGIN_READING;
    elementOffset = bytesRead;
  }

  /**
   * Reads an element ID such that reading can be stopped and started again in a later call
   * if not enough bytes are available. Returns {@link #RESULT_CONTINUE} if a full element ID
   * has been read into {@link #elementId}. Reset {@link #elementIdState} to
   * {@link #STATE_BEGIN_READING} before calling to indicate a new element ID should be read.
   *
   * @param inputStream The input stream from which an element ID should be read.
   * @return One of the {@code RESULT_*} flags defined in this class.
   */
  private int readElementId(NonBlockingInputStream inputStream) {
    if (elementIdState == STATE_FINISHED_READING) {
      return RESULT_CONTINUE;
    }
    if (elementIdState == STATE_BEGIN_READING) {
      varintBytesState = STATE_BEGIN_READING;
      elementIdState = STATE_READ_CONTENTS;
    }
    final int result = readVarintBytes(inputStream);
    if (result != RESULT_CONTINUE) {
      return result;
    }
    elementId = (int) parseTempByteArray(varintBytesLength, false);
    elementIdState = STATE_FINISHED_READING;
    return RESULT_CONTINUE;
  }

  /**
   * Reads an element's content size such that reading can be stopped and started again in a later
   * call if not enough bytes are available. Returns {@link #RESULT_CONTINUE} if an entire element
   * size has been read into {@link #elementContentSize}. Reset {@link #elementContentSizeState} to
   * {@link #STATE_BEGIN_READING} before calling to indicate a new element size should be read.
   *
   * @param inputStream The input stream from which an element size should be read.
   * @return One of the {@code RESULT_*} flags defined in this class.
   */
  private int readElementContentSize(NonBlockingInputStream inputStream) {
    if (elementContentSizeState == STATE_FINISHED_READING) {
      return RESULT_CONTINUE;
    }
    if (elementContentSizeState == STATE_BEGIN_READING) {
      varintBytesState = STATE_BEGIN_READING;
      elementContentSizeState = STATE_READ_CONTENTS;
    }
    final int result = readVarintBytes(inputStream);
    if (result != RESULT_CONTINUE) {
      return result;
    }
    elementContentSize = parseTempByteArray(varintBytesLength, true);
    elementContentSizeState = STATE_FINISHED_READING;
    return RESULT_CONTINUE;
  }

  /**
   * Reads an EBML variable-length integer (varint) such that reading can be stopped and started
   * again in a later call if not enough bytes are available. Returns {@link #RESULT_CONTINUE} if
   * an entire varint has been read into {@link #tempByteArray} and the length of the varint is in
   * {@link #varintBytesLength}. Reset {@link #varintBytesState} to {@link #STATE_BEGIN_READING}
   * before calling to indicate a new varint should be read.
   *
   * @param inputStream The input stream from which a varint should be read.
   * @return One of the {@code RESULT_*} flags defined in this class.
   */
  private int readVarintBytes(NonBlockingInputStream inputStream) {
    if (varintBytesState == STATE_FINISHED_READING) {
      return RESULT_CONTINUE;
    }

    // Read first byte to get length.
    if (varintBytesState == STATE_BEGIN_READING) {
      bytesState = 0;
      final int result = readBytes(inputStream, null, tempByteArray, 1);
      if (result != RESULT_CONTINUE) {
        return result;
      }
      varintBytesState = STATE_READ_CONTENTS;

      final int firstByte = tempByteArray[0] & 0xff;
      varintBytesLength = -1;
      for (int i = 0; i < VARINT_LENGTH_MASKS.length; i++) {
        if ((VARINT_LENGTH_MASKS[i] & firstByte) != 0) {
          varintBytesLength = i + 1;
          break;
        }
      }
      if (varintBytesLength == -1) {
        throw new IllegalStateException(
            "No valid varint length mask found at bytesRead = " + bytesRead);
      }
    }

    // Read remaining bytes.
    final int result = readBytes(inputStream, null, tempByteArray, varintBytesLength);
    if (result != RESULT_CONTINUE) {
      return result;
    }

    // All bytes have been read.
    return RESULT_CONTINUE;
  }

  /**
   * Reads a set amount of bytes into a {@link ByteBuffer}, {@code byte[]}, or nowhere (skipping
   * the bytes) such that reading can be stopped and started again later if not enough bytes are
   * available. Returns {@link #RESULT_CONTINUE} if all bytes have been read. Reset
   * {@link #bytesState} to {@code 0} before calling to indicate a new set of bytes should be read.
   *
   * <p>If both {@code byteBuffer} and {@code byteArray} are not null then bytes are only read
   * into {@code byteBuffer}.
   *
   * @param inputStream The input stream from which bytes should be read.
   * @param byteBuffer The optional {@link ByteBuffer} into which bytes should be read.
   * @param byteArray The optional {@code byte[]} into which bytes should be read.
   * @param totalBytes The total size of bytes to be read or skipped.
   * @return One of the {@code RESULT_*} flags defined in this class.
   */
  private int readBytes(
      NonBlockingInputStream inputStream, ByteBuffer byteBuffer, byte[] byteArray, int totalBytes) {
    if (bytesState == STATE_BEGIN_READING
        && ((byteBuffer != null && totalBytes > byteBuffer.capacity())
            || (byteArray != null && totalBytes > byteArray.length))) {
      throw new IllegalStateException("Byte destination not large enough");
    }
    if (bytesState < totalBytes) {
      final int remainingBytes = totalBytes - bytesState;
      final int result;
      if (byteBuffer != null) {
        result = inputStream.read(byteBuffer, remainingBytes);
      } else if (byteArray != null) {
        result = inputStream.read(byteArray, bytesState, remainingBytes);
      } else {
        result = inputStream.skip(remainingBytes);
      }
      if (result == -1) {
        return RESULT_END_OF_FILE;
      }
      bytesState += result;
      bytesRead += result;
      if (bytesState < totalBytes) {
        return RESULT_NEED_MORE_DATA;
      }
    }
    return RESULT_CONTINUE;
  }

  /**
   * Parses and returns the integer value currently read into the first {@code byteLength} bytes
   * of {@link #tempByteArray}. EBML varint length masks can optionally be removed.
   *
   * @param byteLength The number of bytes to parse from {@link #tempByteArray}.
   * @param removeLengthMask Removes the variable-length integer length mask from the value.
   * @return The resulting integer value. This value could be up to 8-bytes so a Java long is used.
   */
  private long parseTempByteArray(int byteLength, boolean removeLengthMask) {
    if (removeLengthMask) {
      tempByteArray[0] &= ~VARINT_LENGTH_MASKS[varintBytesLength - 1];
    }
    long varint = 0;
    for (int i = 0; i < byteLength; i++) {
      // Shift all existing bits up one byte and add the next byte at the bottom.
      varint = (varint << 8) | (tempByteArray[i] & 0xff);
    }
    return varint;
  }

  /**
   * Used in {@link #masterElementsStack} to track when the current master element ends so that
   * {@link #onMasterElementEnd(int)} is called.
   */
  private static final class MasterElement {

    private final int elementId;
    private final long elementEndOffset;

    private MasterElement(int elementId, long elementEndOffset) {
      this.elementId = elementId;
      this.elementEndOffset = elementEndOffset;
    }

  }

}
