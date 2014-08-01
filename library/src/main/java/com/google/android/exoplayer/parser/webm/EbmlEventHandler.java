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

import java.nio.ByteBuffer;

/**
 * Defines EBML element IDs/types and reacts to events.
 */
/* package */ interface EbmlEventHandler {

  /**
   * Retrieves the type of an element ID.
   *
   * <p>If {@link EbmlReader#TYPE_UNKNOWN} is returned then the element is skipped.
   * Note that all children of a skipped master element are also skipped.
   *
   * @param id The integer ID of this element
   * @return One of the {@code TYPE_} constants defined in this class
   */
  public int getElementType(int id);

  /**
   * Called when a master element is encountered in the {@link NonBlockingInputStream}.
   *
   * <p>Following events should be considered as taking place "within" this element until a
   * matching call to {@link #onMasterElementEnd(int)} is made. Note that it is possible for
   * another master element of the same ID to be nested within itself.
   *
   * @param id The integer ID of this element
   * @param elementOffsetBytes The byte offset where this element starts
   * @param headerSizeBytes The byte length of this element's ID and size header
   * @param contentsSizeBytes The byte length of this element's children
   */
  public void onMasterElementStart(
      int id, long elementOffsetBytes, int headerSizeBytes, long contentsSizeBytes);

  /**
   * Called when a master element has finished reading in all of its children from the
   * {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element
   */
  public void onMasterElementEnd(int id);

  /**
   * Called when an integer element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element
   * @param value The integer value this element contains
   */
  public void onIntegerElement(int id, long value);

  /**
   * Called when a float element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element
   * @param value The float value this element contains
   */
  public void onFloatElement(int id, double value);

  /**
   * Called when a string element is encountered in the {@link NonBlockingInputStream}.
   *
   * @param id The integer ID of this element
   * @param value The string value this element contains
   */
  public void onStringElement(int id, String value);

  /**
   * Called when a binary element is encountered in the {@link NonBlockingInputStream}.
   *
   * <p>The element header (containing element ID and content size) will already have been read.
   * Subclasses must either read nothing and return {@code false}, or exactly read the entire
   * contents of the element, which is {@code contentsSizeBytes} in length, and return {@code true}.
   *
   * <p>It's guaranteed that the full element contents will be immediately available from
   * {@code inputStream}.
   *
   * <p>Several methods in {@link EbmlReader} are available for reading the contents of a
   * binary element:
   * <ul>
   * <li>{@link EbmlReader#readVarint(NonBlockingInputStream)}.
   * <li>{@link EbmlReader#readBytes(NonBlockingInputStream, byte[], int)}.
   * <li>{@link EbmlReader#readBytes(NonBlockingInputStream, ByteBuffer, int)}.
   * <li>{@link EbmlReader#skipBytes(NonBlockingInputStream, int)}.
   * <li>{@link EbmlReader#getBytesRead()}.
   * </ul>
   *
   * @param id The integer ID of this element
   * @param elementOffsetBytes The byte offset where this element starts
   * @param headerSizeBytes The byte length of this element's ID and size header
   * @param contentsSizeBytes The byte length of this element's contents
   * @param inputStream The {@link NonBlockingInputStream} from which this
   *        element's contents should be read
   * @return True if the element was read. False otherwise.
   */
  public boolean onBinaryElement(
      int id, long elementOffsetBytes, int headerSizeBytes, int contentsSizeBytes,
      NonBlockingInputStream inputStream);

}
