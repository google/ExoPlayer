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
 * Default version of a basic event-driven incremental EBML parser which needs an
 * {@link EbmlEventHandler} to define IDs/types and react to events.
 *
 * <p>EBML can be summarized as a binary XML format somewhat similar to Protocol Buffers.
 * It was originally designed for the Matroska container format. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 */
/* package */ final class DefaultEbmlReader implements EbmlReader {

  // State values used in variables state, elementIdState, elementContentSizeState, and
  // varintBytesState.
  private static final int STATE_BEGIN_READING = 0;
  private static final int STATE_READ_CONTENTS = 1;
  private static final int STATE_FINISHED_READING = 2;

  /**
   * The first byte of a variable-length integer (varint) will have one of these bit masks
   * indicating the total length in bytes.
   *
   * <p>{@code 0x80} is a one-byte integer, {@code 0x40} is two bytes, and so on up to eight bytes.
   */
  private static final int[] VARINT_LENGTH_MASKS = new int[] {
    0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
  };

  private static final int MAX_INTEGER_ELEMENT_SIZE_BYTES = 8;
  private static final int VALID_FLOAT32_ELEMENT_SIZE_BYTES = 4;
  private static final int VALID_FLOAT64_ELEMENT_SIZE_BYTES = 8;

  /**
   * Scratch space to read in EBML varints, unsigned ints, and floats - each of which can be
   * up to 8 bytes.
   */
  private final byte[] tempByteArray = new byte[8];
  private final Stack<MasterElement> masterElementsStack = new Stack<MasterElement>();

  /**
   * Current {@link EbmlEventHandler} which is queried for element types
   * and informed of element events.
   */
  private EbmlEventHandler eventHandler;

  /**
   * Overall state for the current element. Must be one of the {@code STATE_*} constants.
   */
  private int state;

  /**
   * Total bytes read since starting or the last {@link #reset()}.
   */
  private long bytesRead;

  /**
   * The starting byte offset of the current element being parsed.
   */
  private long elementOffset;

  /**
   * Holds the current element ID after {@link #elementIdState} is {@link #STATE_FINISHED_READING}.
   */
  private int elementId;

  /**
   * State for the ID of the current element. Must be one of the {@code STATE_*} constants.
   */
  private int elementIdState;

  /**
   * Holds the current element content size after {@link #elementContentSizeState}
   * is {@link #STATE_FINISHED_READING}.
   */
  private long elementContentSize;

  /**
   * State for the content size of the current element.
   * Must be one of the {@code STATE_*} constants.
   */
  private int elementContentSizeState;

  /**
   * State for the current variable-length integer (varint) being read into
   * {@link #tempByteArray}. Must be one of the {@code STATE_*} constants.
   */
  private int varintBytesState;

  /**
   * Length in bytes of the current variable-length integer (varint) being read into
   * {@link #tempByteArray}.
   */
  private int varintBytesLength;

  /**
   * Counts the number of bytes being contiguously read into either {@link #tempByteArray} or
   * {@link #stringBytes}. Used to determine when all required bytes have been read across
   * multiple calls.
   */
  private int bytesState;

  /**
   * Holds string element bytes as they're being read in. Allocated after the element content
   * size is known and released after calling {@link EbmlEventHandler#onStringElement(int, String)}.
   */
  private byte[] stringBytes;

  @Override
  public void setEventHandler(EbmlEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public int read(NonBlockingInputStream inputStream) {
    Assertions.checkState(eventHandler != null);
    while (true) {
      while (!masterElementsStack.isEmpty()
          && bytesRead >= masterElementsStack.peek().elementEndOffsetBytes) {
        eventHandler.onMasterElementEnd(masterElementsStack.pop().elementId);
        return READ_RESULT_CONTINUE;
      }

      if (state == STATE_BEGIN_READING) {
        int idResult = readElementId(inputStream);
        if (idResult != READ_RESULT_CONTINUE) {
          return idResult;
        }
        int sizeResult = readElementContentSize(inputStream);
        if (sizeResult != READ_RESULT_CONTINUE) {
          return sizeResult;
        }
        state = STATE_READ_CONTENTS;
        bytesState = 0;
      }

      int type = eventHandler.getElementType(elementId);
      switch (type) {
        case TYPE_MASTER:
          int masterHeaderSize = (int) (bytesRead - elementOffset); // Header size is 12 bytes max.
          masterElementsStack.add(new MasterElement(elementId, bytesRead + elementContentSize));
          eventHandler.onMasterElementStart(elementId, elementOffset, masterHeaderSize,
              elementContentSize);
          prepareForNextElement();
          return READ_RESULT_CONTINUE;
        case TYPE_UNSIGNED_INT:
          if (elementContentSize > MAX_INTEGER_ELEMENT_SIZE_BYTES) {
            throw new IllegalStateException("Invalid integer size " + elementContentSize);
          }
          int intResult =
              readBytesInternal(inputStream, tempByteArray, (int) elementContentSize);
          if (intResult != READ_RESULT_CONTINUE) {
            return intResult;
          }
          long intValue = getTempByteArrayValue((int) elementContentSize, false);
          eventHandler.onIntegerElement(elementId, intValue);
          prepareForNextElement();
          return READ_RESULT_CONTINUE;
        case TYPE_FLOAT:
          if (elementContentSize != VALID_FLOAT32_ELEMENT_SIZE_BYTES
              && elementContentSize != VALID_FLOAT64_ELEMENT_SIZE_BYTES) {
            throw new IllegalStateException("Invalid float size " + elementContentSize);
          }
          int floatResult =
              readBytesInternal(inputStream, tempByteArray, (int) elementContentSize);
          if (floatResult != READ_RESULT_CONTINUE) {
            return floatResult;
          }
          long valueBits = getTempByteArrayValue((int) elementContentSize, false);
          double floatValue;
          if (elementContentSize == VALID_FLOAT32_ELEMENT_SIZE_BYTES) {
            floatValue = Float.intBitsToFloat((int) valueBits);
          } else {
            floatValue = Double.longBitsToDouble(valueBits);
          }
          eventHandler.onFloatElement(elementId, floatValue);
          prepareForNextElement();
          return READ_RESULT_CONTINUE;
        case TYPE_STRING:
          if (elementContentSize > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                "String element size " + elementContentSize + " is larger than MAX_INT");
          }
          if (stringBytes == null) {
            stringBytes = new byte[(int) elementContentSize];
          }
          int stringResult =
              readBytesInternal(inputStream, stringBytes, (int) elementContentSize);
          if (stringResult != READ_RESULT_CONTINUE) {
            return stringResult;
          }
          String stringValue = new String(stringBytes, Charset.forName("UTF-8"));
          stringBytes = null;
          eventHandler.onStringElement(elementId, stringValue);
          prepareForNextElement();
          return READ_RESULT_CONTINUE;
        case TYPE_BINARY:
          if (elementContentSize > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                "Binary element size " + elementContentSize + " is larger than MAX_INT");
          }
          if (inputStream.getAvailableByteCount() < elementContentSize) {
            return READ_RESULT_NEED_MORE_DATA;
          }
          int binaryHeaderSize = (int) (bytesRead - elementOffset); // Header size is 12 bytes max.
          boolean consumed = eventHandler.onBinaryElement(
              elementId, elementOffset, binaryHeaderSize, (int) elementContentSize, inputStream);
          if (consumed) {
            long expectedBytesRead = elementOffset + binaryHeaderSize + elementContentSize;
            if (expectedBytesRead != bytesRead) {
              throw new IllegalStateException("Incorrect total bytes read. Expected "
                  + expectedBytesRead + " but actually " + bytesRead);
            }
            prepareForNextElement();
          }
          return READ_RESULT_CONTINUE;
        case TYPE_UNKNOWN:
          if (elementContentSize > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                "Unknown element size " + elementContentSize + " is larger than MAX_INT");
          }
          int skipResult = skipBytesInternal(inputStream, (int) elementContentSize);
          if (skipResult != READ_RESULT_CONTINUE) {
            return skipResult;
          }
          prepareForNextElement();
          break;
        default:
          throw new IllegalStateException("Invalid element type " + type);
      }
    }
  }

  @Override
  public long getBytesRead() {
    return bytesRead;
  }

  @Override
  public void reset() {
    prepareForNextElement();
    masterElementsStack.clear();
    bytesRead = 0;
  }

  @Override
  public long readVarint(NonBlockingInputStream inputStream) {
    varintBytesState = STATE_BEGIN_READING;
    int result = readVarintBytes(inputStream);
    if (result != READ_RESULT_CONTINUE) {
      throw new IllegalStateException("Couldn't read varint");
    }
    return getTempByteArrayValue(varintBytesLength, true);
  }

  @Override
  public void readBytes(NonBlockingInputStream inputStream, ByteBuffer byteBuffer, int totalBytes) {
    bytesState = 0;
    int result = readBytesInternal(inputStream, byteBuffer, totalBytes);
    if (result != READ_RESULT_CONTINUE) {
      throw new IllegalStateException("Couldn't read bytes into buffer");
    }
  }

  @Override
  public void readBytes(NonBlockingInputStream inputStream, byte[] byteArray, int totalBytes) {
    bytesState = 0;
    int result = readBytesInternal(inputStream, byteArray, totalBytes);
    if (result != READ_RESULT_CONTINUE) {
      throw new IllegalStateException("Couldn't read bytes into array");
    }
  }

  @Override
  public void skipBytes(NonBlockingInputStream inputStream, int totalBytes) {
    bytesState = 0;
    int result = skipBytesInternal(inputStream, totalBytes);
    if (result != READ_RESULT_CONTINUE) {
      throw new IllegalStateException("Couldn't skip bytes");
    }
  }

  /**
   * Resets the internal state of {@link #read(NonBlockingInputStream)} so that it can start
   * reading a new element from scratch.
   */
  private void prepareForNextElement() {
    state = STATE_BEGIN_READING;
    elementIdState = STATE_BEGIN_READING;
    elementContentSizeState = STATE_BEGIN_READING;
    elementOffset = bytesRead;
  }

  /**
   * Reads an element ID such that reading can be stopped and started again in a later call
   * if not enough bytes are available. Returns {@link #READ_RESULT_CONTINUE} if a full element ID
   * has been read into {@link #elementId}. Reset {@link #elementIdState} to
   * {@link #STATE_BEGIN_READING} before calling to indicate a new element ID should be read.
   *
   * @param inputStream The input stream from which an element ID should be read
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int readElementId(NonBlockingInputStream inputStream) {
    if (elementIdState == STATE_FINISHED_READING) {
      return READ_RESULT_CONTINUE;
    }
    if (elementIdState == STATE_BEGIN_READING) {
      varintBytesState = STATE_BEGIN_READING;
      elementIdState = STATE_READ_CONTENTS;
    }
    int result = readVarintBytes(inputStream);
    if (result != READ_RESULT_CONTINUE) {
      return result;
    }
    // Element IDs are at most 4 bytes so cast to int now.
    elementId = (int) getTempByteArrayValue(varintBytesLength, false);
    elementIdState = STATE_FINISHED_READING;
    return READ_RESULT_CONTINUE;
  }

  /**
   * Reads an element's content size such that reading can be stopped and started again in a later
   * call if not enough bytes are available.
   *
   * <p>Returns {@link #READ_RESULT_CONTINUE} if an entire element size has been
   * read into {@link #elementContentSize}. Reset {@link #elementContentSizeState} to
   * {@link #STATE_BEGIN_READING} before calling to indicate a new element size should be read.
   *
   * @param inputStream The input stream from which an element size should be read
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int readElementContentSize(NonBlockingInputStream inputStream) {
    if (elementContentSizeState == STATE_FINISHED_READING) {
      return READ_RESULT_CONTINUE;
    }
    if (elementContentSizeState == STATE_BEGIN_READING) {
      varintBytesState = STATE_BEGIN_READING;
      elementContentSizeState = STATE_READ_CONTENTS;
    }
    int result = readVarintBytes(inputStream);
    if (result != READ_RESULT_CONTINUE) {
      return result;
    }
    elementContentSize = getTempByteArrayValue(varintBytesLength, true);
    elementContentSizeState = STATE_FINISHED_READING;
    return READ_RESULT_CONTINUE;
  }

  /**
   * Reads an EBML variable-length integer (varint) such that reading can be stopped and started
   * again in a later call if not enough bytes are available.
   *
   * <p>Returns {@link #READ_RESULT_CONTINUE} if an entire varint has been read into
   * {@link #tempByteArray} and the length of the varint is in {@link #varintBytesLength}.
   * Reset {@link #varintBytesState} to {@link #STATE_BEGIN_READING} before calling to indicate
   * a new varint should be read.
   *
   * @param inputStream The input stream from which a varint should be read
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int readVarintBytes(NonBlockingInputStream inputStream) {
    if (varintBytesState == STATE_FINISHED_READING) {
      return READ_RESULT_CONTINUE;
    }

    // Read first byte to get length.
    if (varintBytesState == STATE_BEGIN_READING) {
      bytesState = 0;
      int result = readBytesInternal(inputStream, tempByteArray, 1);
      if (result != READ_RESULT_CONTINUE) {
        return result;
      }
      varintBytesState = STATE_READ_CONTENTS;

      int firstByte = tempByteArray[0] & 0xff;
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
    int result = readBytesInternal(inputStream, tempByteArray, varintBytesLength);
    if (result != READ_RESULT_CONTINUE) {
      return result;
    }

    // All bytes have been read.
    return READ_RESULT_CONTINUE;
  }

  /**
   * Reads a set amount of bytes into a {@link ByteBuffer} such that reading can be stopped
   * and started again later if not enough bytes are available.
   *
   * <p>Returns {@link #READ_RESULT_CONTINUE} if all bytes have been read. Reset
   * {@link #bytesState} to {@code 0} before calling to indicate a new set of bytes should be read.
   *
   * @param inputStream The input stream from which bytes should be read
   * @param byteBuffer The {@link ByteBuffer} into which bytes should be read
   * @param totalBytes The total size of bytes to be read
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int readBytesInternal(
      NonBlockingInputStream inputStream, ByteBuffer byteBuffer, int totalBytes) {
    if (bytesState == STATE_BEGIN_READING && totalBytes > byteBuffer.capacity()) {
      throw new IllegalArgumentException("Byte buffer not large enough");
    }
    if (bytesState >= totalBytes) {
      return READ_RESULT_CONTINUE;
    }
    int remainingBytes = totalBytes - bytesState;
    int additionalBytesRead = inputStream.read(byteBuffer, remainingBytes);
    return updateBytesState(additionalBytesRead, totalBytes);
  }

  /**
   * Reads a set amount of bytes into a {@code byte[]} such that reading can be stopped
   * and started again later if not enough bytes are available.
   *
   * <p>Returns {@link #READ_RESULT_CONTINUE} if all bytes have been read. Reset
   * {@link #bytesState} to {@code 0} before calling to indicate a new set of bytes should be read.
   *
   * @param inputStream The input stream from which bytes should be read
   * @param byteArray The {@code byte[]} into which bytes should be read
   * @param totalBytes The total size of bytes to be read
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int readBytesInternal(
      NonBlockingInputStream inputStream, byte[] byteArray, int totalBytes) {
    if (bytesState == STATE_BEGIN_READING && totalBytes > byteArray.length) {
      throw new IllegalArgumentException("Byte array not large enough");
    }
    if (bytesState >= totalBytes) {
      return READ_RESULT_CONTINUE;
    }
    int remainingBytes = totalBytes - bytesState;
    int additionalBytesRead = inputStream.read(byteArray, bytesState, remainingBytes);
    return updateBytesState(additionalBytesRead, totalBytes);
  }

  /**
   * Skips a set amount of bytes such that reading can be stopped and started again later if
   * not enough bytes are available.
   *
   * <p>Returns {@link #READ_RESULT_CONTINUE} if all bytes have been skipped. Reset
   * {@link #bytesState} to {@code 0} before calling to indicate a new set of bytes
   * should be skipped.
   *
   * @param inputStream The input stream from which bytes should be skipped
   * @param totalBytes The total size of bytes to be skipped
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int skipBytesInternal(NonBlockingInputStream inputStream, int totalBytes) {
    if (bytesState >= totalBytes) {
      return READ_RESULT_CONTINUE;
    }
    int remainingBytes = totalBytes - bytesState;
    int additionalBytesRead = inputStream.skip(remainingBytes);
    return updateBytesState(additionalBytesRead, totalBytes);
  }

  /**
   * Updates {@link #bytesState} and {@link #bytesRead} after reading bytes in one of the
   * {@code verbBytesInternal} methods.
   *
   * @param additionalBytesRead The number of additional bytes read to be accounted for
   * @param totalBytes The total size of bytes to be read or skipped
   * @return One of the {@code RESULT_*} flags defined in this class
   */
  private int updateBytesState(int additionalBytesRead, int totalBytes) {
    if (additionalBytesRead == -1) {
      return READ_RESULT_END_OF_STREAM;
    }
    bytesState += additionalBytesRead;
    bytesRead += additionalBytesRead;
    if (bytesState < totalBytes) {
      return READ_RESULT_NEED_MORE_DATA;
    } else {
      return READ_RESULT_CONTINUE;
    }
  }

  /**
   * Parses and returns the integer value currently read into the first {@code byteLength} bytes
   * of {@link #tempByteArray}. EBML varint length masks can optionally be removed.
   *
   * @param byteLength The number of bytes to parse from {@link #tempByteArray}
   * @param removeLengthMask Removes the variable-length integer length mask from the value
   * @return The resulting integer value. This value could be up to 8-bytes so a Java long is used
   */
  private long getTempByteArrayValue(int byteLength, boolean removeLengthMask) {
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
   * {@link EbmlEventHandler#onMasterElementEnd(int)} is called.
   */
  private static final class MasterElement {

    private final int elementId;
    private final long elementEndOffsetBytes;

    private MasterElement(int elementId, long elementEndOffsetBytes) {
      this.elementId = elementId;
      this.elementEndOffsetBytes = elementEndOffsetBytes;
    }

  }

}
