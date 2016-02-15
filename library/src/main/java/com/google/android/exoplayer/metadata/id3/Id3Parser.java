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
package com.google.android.exoplayer.metadata.id3;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.metadata.MetadataParser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Extracts individual TXXX text frames from raw ID3 data.
 */
public final class Id3Parser implements MetadataParser<List<Id3Frame>> {

  private static final int ID3_TEXT_ENCODING_ISO_8859_1 = 0;
  private static final int ID3_TEXT_ENCODING_UTF_16 = 1;
  private static final int ID3_TEXT_ENCODING_UTF_16BE = 2;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  @Override
  public boolean canParse(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_ID3);
  }

  @Override
  public List<Id3Frame> parse(byte[] data, int size) throws UnsupportedEncodingException,
      ParserException {
    List<Id3Frame> id3Frames = new ArrayList<>();
    ParsableByteArray id3Data = new ParsableByteArray(data, size);
    int id3Size = parseId3Header(id3Data);

    while (id3Size > 0) {
      int frameId0 = id3Data.readUnsignedByte();
      int frameId1 = id3Data.readUnsignedByte();
      int frameId2 = id3Data.readUnsignedByte();
      int frameId3 = id3Data.readUnsignedByte();
      int frameSize = id3Data.readSynchSafeInt();
      if (frameSize <= 1) {
        break;
      }

      // Skip frame flags.
      id3Data.skipBytes(2);
      // Check Frame ID == TXXX.
      if (frameId0 == 'T' && frameId1 == 'X' && frameId2 == 'X' && frameId3 == 'X') {
        int encoding = id3Data.readUnsignedByte();
        String charset = getCharsetName(encoding);
        byte[] frame = new byte[frameSize - 1];
        id3Data.readBytes(frame, 0, frameSize - 1);

        int firstZeroIndex = indexOfEOS(frame, 0, encoding);
        String description = new String(frame, 0, firstZeroIndex, charset);
        int valueStartIndex = firstZeroIndex + delimiterLength(encoding);
        int valueEndIndex = indexOfEOS(frame, valueStartIndex, encoding);
        String value = new String(frame, valueStartIndex, valueEndIndex - valueStartIndex, charset);
        id3Frames.add(new TxxxFrame(description, value));
      } else if (frameId0 == 'P' && frameId1 == 'R' && frameId2 == 'I' && frameId3 == 'V') {
        // Check frame ID == PRIV
        byte[] frame = new byte[frameSize];
        id3Data.readBytes(frame, 0, frameSize);

        int firstZeroIndex = indexOf(frame, 0, (byte) 0);
        String owner = new String(frame, 0, firstZeroIndex, "ISO-8859-1");
        byte[] privateData = new byte[frameSize - firstZeroIndex - 1];
        System.arraycopy(frame, firstZeroIndex + 1, privateData, 0, frameSize - firstZeroIndex - 1);
        id3Frames.add(new PrivFrame(owner, privateData));
      } else if (frameId0 == 'G' && frameId1 == 'E' && frameId2 == 'O' && frameId3 == 'B') {
        // Check frame ID == GEOB
        int encoding = id3Data.readUnsignedByte();
        String charset = getCharsetName(encoding);
        byte[] frame = new byte[frameSize - 1];
        id3Data.readBytes(frame, 0, frameSize - 1);

        int firstZeroIndex = indexOf(frame, 0, (byte) 0);
        String mimeType = new String(frame, 0, firstZeroIndex, "ISO-8859-1");
        int filenameStartIndex = firstZeroIndex + 1;
        int filenameEndIndex = indexOfEOS(frame, filenameStartIndex, encoding);
        String filename = new String(frame, filenameStartIndex,
            filenameEndIndex - filenameStartIndex, charset);
        int descriptionStartIndex = filenameEndIndex + delimiterLength(encoding);
        int descriptionEndIndex = indexOfEOS(frame, descriptionStartIndex, encoding);
        String description = new String(frame, descriptionStartIndex,
            descriptionEndIndex - descriptionStartIndex, charset);

        int objectDataSize = frameSize - 1 /* encoding byte */ - descriptionEndIndex
            - delimiterLength(encoding);
        byte[] objectData = new byte[objectDataSize];
        System.arraycopy(frame, descriptionEndIndex + delimiterLength(encoding), objectData, 0,
            objectDataSize);
        id3Frames.add(new GeobFrame(mimeType, filename, description, objectData));
      } else {
        String type = String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
        byte[] frame = new byte[frameSize];
        id3Data.readBytes(frame, 0, frameSize);
        id3Frames.add(new BinaryFrame(type, frame));
      }

      id3Size -= frameSize + 10 /* header size */;
    }

    return Collections.unmodifiableList(id3Frames);
  }

  private static int indexOf(byte[] data, int fromIndex, byte key) {
    for (int i = fromIndex; i < data.length; i++) {
      if (data[i] == key) {
        return i;
      }
    }
    return data.length;
  }

  private static int indexOfEOS(byte[] data, int fromIndex, int encodingByte) {
    int terminationPos = indexOf(data, fromIndex, (byte) 0);

    // For single byte encoding charsets, we are done
    if (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8) {
      return terminationPos;
    }

    // Otherwise, look for a two zero bytes
    while (terminationPos < data.length - 1) {
      if (data[terminationPos + 1] == (byte) 0) {
        return terminationPos;
      }
      terminationPos = indexOf(data, terminationPos + 1, (byte) 0);
    }

    return data.length;
  }

  private static int delimiterLength(int encodingByte) {
    return (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
        ? 1 : 2;
  }

  /**
   * Parses an ID3 header.
   *
   * @param id3Buffer A {@link ParsableByteArray} from which data should be read.
   * @return The size of ID3 frames in bytes, excluding the header and footer.
   * @throws ParserException If ID3 file identifier != "ID3".
   */
  private static int parseId3Header(ParsableByteArray id3Buffer) throws ParserException {
    int id1 = id3Buffer.readUnsignedByte();
    int id2 = id3Buffer.readUnsignedByte();
    int id3 = id3Buffer.readUnsignedByte();
    if (id1 != 'I' || id2 != 'D' || id3 != '3') {
      throw new ParserException(String.format(Locale.US,
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
      case ID3_TEXT_ENCODING_ISO_8859_1:
        return "ISO-8859-1";
      case ID3_TEXT_ENCODING_UTF_16:
        return "UTF-16";
      case ID3_TEXT_ENCODING_UTF_16BE:
        return "UTF-16BE";
      case ID3_TEXT_ENCODING_UTF_8:
        return "UTF-8";
      default:
        return "ISO-8859-1";
    }
  }

}
