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
package com.google.android.exoplayer.metadata;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.BitArray;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts individual TXXX text frames from raw ID3 data.
 */
public class Id3Parser implements MetadataParser<Map<String, Object>> {

  @Override
  public boolean canParse(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_ID3);
  }

  @Override
  public Map<String, Object> parse(byte[] data, int size)
      throws UnsupportedEncodingException, ParserException {
    BitArray id3Buffer = new BitArray(data, size);
    int id3Size = parseId3Header(id3Buffer);

    Map<String, Object> metadata = new HashMap<String, Object>();

    while (id3Size > 0) {
      int frameId0 = id3Buffer.readUnsignedByte();
      int frameId1 = id3Buffer.readUnsignedByte();
      int frameId2 = id3Buffer.readUnsignedByte();
      int frameId3 = id3Buffer.readUnsignedByte();

      int frameSize = id3Buffer.readSynchSafeInt();
      if (frameSize <= 1) {
        break;
      }

      id3Buffer.skipBytes(2); // Skip frame flags.

      // Check Frame ID == TXXX.
      if (frameId0 == 'T' && frameId1 == 'X' && frameId2 == 'X' && frameId3 == 'X') {
        int encoding = id3Buffer.readUnsignedByte();
        String charset = getCharsetName(encoding);
        byte[] frame = new byte[frameSize - 1];
        id3Buffer.readBytes(frame, 0, frameSize - 1);

        int firstZeroIndex = indexOf(frame, 0, (byte) 0);
        String description = new String(frame, 0, firstZeroIndex, charset);
        int valueStartIndex = indexOfNot(frame, firstZeroIndex, (byte) 0);
        int valueEndIndex = indexOf(frame, valueStartIndex, (byte) 0);
        String value = new String(frame, valueStartIndex, valueEndIndex - valueStartIndex,
            charset);
        metadata.put(TxxxMetadata.TYPE, new TxxxMetadata(description, value));
      } else {
        String type = String.format("%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
        byte[] frame = new byte[frameSize];
        id3Buffer.readBytes(frame, 0, frameSize);
        metadata.put(type, frame);
      }

      id3Size -= frameSize + 10 /* header size */;
    }

    return Collections.unmodifiableMap(metadata);
  }

  private static int indexOf(byte[] data, int fromIndex, byte key) {
    for (int i = fromIndex; i < data.length; i++) {
      if (data[i] == key) {
        return i;
      }
    }
    return data.length;
  }

  private static int indexOfNot(byte[] data, int fromIndex, byte key) {
    for (int i = fromIndex; i < data.length; i++) {
      if (data[i] != key) {
        return i;
      }
    }
    return data.length;
  }

  /**
   * Parses ID3 header.
   * @param id3Buffer A {@link BitArray} with raw ID3 data.
   * @return The size of data that contains ID3 frames without header and footer.
   * @throws ParserException If ID3 file identifier != "ID3".
   */
  private static int parseId3Header(BitArray id3Buffer) throws ParserException {
    int id1 = id3Buffer.readUnsignedByte();
    int id2 = id3Buffer.readUnsignedByte();
    int id3 = id3Buffer.readUnsignedByte();
    if (id1 != 'I' || id2 != 'D' || id3 != '3') {
      throw new ParserException(String.format(
          "Unexpected ID3 file identifier, expected \"ID3\", actual \"%c%c%c\".", id1, id2, id3));
    }
    id3Buffer.skipBytes(2); // Skip version.

    int flags = id3Buffer.readUnsignedByte();
    int id3Size = id3Buffer.readSynchSafeInt();

    // Check if extended header presents.
    if ((flags & 0x2) != 0) {
      int extendedHeaderSize = id3Buffer.readSynchSafeInt();
      if (extendedHeaderSize > 4) {
        id3Buffer.skipBytes(extendedHeaderSize - 4);
      }
      id3Size -= extendedHeaderSize;
    }

    // Check if footer presents.
    if ((flags & 0x8) != 0) {
      id3Size -= 10;
    }

    return id3Size;
  }

  /**
   * Maps encoding byte from ID3v2 frame to a Charset.
   * @param encodingByte The value of encoding byte from ID3v2 frame.
   * @return Charset name.
   */
  private static String getCharsetName(int encodingByte) {
    switch (encodingByte) {
      case 0:
        return "ISO-8859-1";
      case 1:
        return "UTF-16";
      case 2:
        return "UTF-16BE";
      case 3:
        return "UTF-8";
      default:
        return "ISO-8859-1";
    }
  }

}
