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
package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Script Data tags from an FLV stream and extracts metadata information.
 */
final class ScriptTagPayloadReader extends TagPayloadReader {

  // AMF object types
  private static final int AMF_TYPE_UNKNOWN = -1;
  private static final int AMF_TYPE_NUMBER = 0;
  private static final int AMF_TYPE_BOOLEAN = 1;
  private static final int AMF_TYPE_STRING = 2;
  private static final int AMF_TYPE_OBJECT = 3;
  private static final int AMF_TYPE_ECMA_ARRAY = 8;
  private static final int AMF_TYPE_END_MARKER = 9;
  private static final int AMF_TYPE_STRICT_ARRAY = 10;
  private static final int AMF_TYPE_DATE = 11;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public ScriptTagPayloadReader(TrackOutput output) {
    super(output);
  }

  @Override
  public void seek() {

  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedTrack {
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) {
    // Read message name (don't storing it as we are not going to give it any use)
    readAMFData(data, AMF_TYPE_UNKNOWN);
    Object obj = readAMFData(data, AMF_TYPE_UNKNOWN);

    if (obj instanceof Map) {
      Map<String, Object> extractedMetadata = (Map<String, Object>) obj;
      for (Map.Entry<String, Object> entry : extractedMetadata.entrySet()) {
        if (entry.getValue() == null) {
          continue;
        }

        switch (entry.getKey()) {
          case "duration":
            this.durationUs = (long)(C.MICROS_PER_SECOND * (Double)(entry.getValue()));
            break;

          default:
            break;
        }
      }
    }
  }

  private Object readAMFData(ParsableByteArray data, int type) {
    if (type == AMF_TYPE_UNKNOWN) {
      type = data.readUnsignedByte();
    }
    switch (type) {
      case AMF_TYPE_NUMBER:
        return readAMFDouble(data);
      case AMF_TYPE_BOOLEAN:
        return readAMFBoolean(data);
      case AMF_TYPE_STRING:
        return readAMFString(data);
      case AMF_TYPE_OBJECT:
        return readAMFObject(data);
      case AMF_TYPE_ECMA_ARRAY:
        return readAMFEcmaArray(data);
      case AMF_TYPE_STRICT_ARRAY:
        return readAMFStrictArray(data);
      case AMF_TYPE_DATE:
        return readAMFDate(data);
      default:
        return null;
    }
  }

  /**
   * Read a boolean from an AMF encoded buffer
   * @param data Buffer
   * @return Boolean value read from the buffer
   */
  private Boolean readAMFBoolean(ParsableByteArray data) {
    return data.readUnsignedByte() == 1;
  }

  /**
   * Read a double number from an AMF encoded buffer
   * @param data Buffer
   * @return Double number read from the buffer
   */
  private Double readAMFDouble(ParsableByteArray data) {
    byte []b = new byte[8];
    data.readBytes(b, 0, b.length);
    return ByteBuffer.wrap(b).getDouble();
  }

  /**
   * Read a string from an AMF encoded buffer
   * @param data Buffer
   * @return String read from the buffer
   */
  private String readAMFString(ParsableByteArray data) {
    int size = data.readUnsignedShort();
    byte []b = new byte[size];
    data.readBytes(b, 0, b.length);
    return new String(b);
  }

  /**
   * Read an array from an AMF encoded buffer
   * @param data Buffer
   * @return Array read from the buffer
   */
  private Object readAMFStrictArray(ParsableByteArray data) {
    long count = data.readUnsignedInt();
    ArrayList<Object> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(readAMFData(data, AMF_TYPE_UNKNOWN));
    }
    return list;
  }

  /**
   * Read an object from an AMF encoded buffer
   * @param data Buffer
   * @return Object read from the buffer
   */
  private Object readAMFObject(ParsableByteArray data) {
    HashMap<String, Object> array = new HashMap<>();
    while (true) {
      String key = readAMFString(data);
      int type = data.readUnsignedByte();
      if (type == AMF_TYPE_END_MARKER) {
        break;
      }
      array.put(key, readAMFData(data, type));
    }
    return array;
  }

  /**
   * Read am ecma array from an AMF encoded buffer
   * @param data Buffer
   * @return Ecma array read from the buffer
   */
  private Object readAMFEcmaArray(ParsableByteArray data) {
    long count = data.readUnsignedInt();
    HashMap<String, Object> array = new HashMap<>();
    for (int i = 0; i < count; i++) {
      String key = readAMFString(data);
      int type = data.readUnsignedByte();
      array.put(key, readAMFData(data, type));
    }
    return array;
  }

  /**
   * Read a date from an AMF encoded buffer
   * @param data Buffer
   * @return Date read from the buffer
   */
  private Date readAMFDate(ParsableByteArray data) {
    final Date date = new Date((long) readAMFDouble(data).doubleValue());
    data.readUnsignedShort();
    return date;
  }
}
