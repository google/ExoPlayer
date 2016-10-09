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
package com.google.android.exoplayer2.metadata.id3;

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.GaplessInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoder;
import com.google.android.exoplayer2.metadata.MetadataDecoderException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decodes individual TXXX text frames from raw ID3 data.
 */
public final class Id3Decoder implements MetadataDecoder<Metadata> {

  private static final int ID3_TEXT_ENCODING_ISO_8859_1 = 0;
  private static final int ID3_TEXT_ENCODING_UTF_16 = 1;
  private static final int ID3_TEXT_ENCODING_UTF_16BE = 2;
  private static final int ID3_TEXT_ENCODING_UTF_8 = 3;

  private int majorVersion;
  private int minorVersion;
  private boolean isUnsynchronized;
  private GaplessInfo gaplessInfo;

  @Override
  public boolean canDecode(String mimeType) {
    return mimeType.equals(MimeTypes.APPLICATION_ID3);
  }

  @Override
  public Metadata decode(byte[] data, int size) throws MetadataDecoderException {
    List<Id3Frame> id3Frames = new ArrayList<>();
    ParsableByteArray id3Data = new ParsableByteArray(data, size);
    int id3Size = decodeId3Header(id3Data);

    if (isUnsynchronized) {
      id3Data = removeUnsynchronization(id3Data, id3Size);
      id3Size = id3Data.bytesLeft();
    }

    while (id3Size > 0) {
      int frameId0 = id3Data.readUnsignedByte();
      int frameId1 = id3Data.readUnsignedByte();
      int frameId2 = id3Data.readUnsignedByte();
      int frameId3 = majorVersion > 2 ? id3Data.readUnsignedByte() : 0;
      int frameSize = majorVersion == 2 ? id3Data.readUnsignedInt24() :
          majorVersion == 3 ? id3Data.readInt() : id3Data.readSynchSafeInt();

      if (frameSize <= 1) {
        break;
      }

      // Frame flags.
      boolean isCompressed = false;
      boolean isEncrypted = false;
      boolean isUnsynchronized = false;
      boolean hasGroupIdentifier = false;
      boolean hasDataLength = false;

      if (majorVersion > 2) {
        int flags = id3Data.readShort();
        if (majorVersion == 3) {
          isCompressed = (flags & 0x0080) != 0;
          isEncrypted = (flags & 0x0040) != 0;
          hasDataLength = isCompressed;
        } else {
          isCompressed = (flags & 0x0008) != 0;
          isEncrypted = (flags & 0x0004) != 0;
          isUnsynchronized = (flags & 0x0002) != 0;
          hasGroupIdentifier = (flags & 0x0040) != 0;
          hasDataLength = (flags & 0x0001) != 0;
        }
      }

      int headerSize = majorVersion == 2 ? 6 : 10;

      if (hasGroupIdentifier) {
        ++headerSize;
        --frameSize;
        id3Data.skipBytes(1);
      }

      if (isEncrypted) {
        ++headerSize;
        --frameSize;
        id3Data.skipBytes(1);
      }

      if (hasDataLength) {
        headerSize += 4;
        frameSize -= 4;
        id3Data.skipBytes(4);
      }

      id3Size -= frameSize + headerSize;

      if (isCompressed || isEncrypted) {
        id3Data.skipBytes(frameSize);
      } else {
        try {
          Id3Frame frame;
          ParsableByteArray frameData = id3Data;
          if (isUnsynchronized) {
            frameData = removeUnsynchronization(id3Data, frameSize);
            frameSize = frameData.bytesLeft();
          }

          if (frameId0 == 'T' && frameId1 == 'X' && frameId2 == 'X' && frameId3 == 'X') {
            frame = decodeTxxxFrame(frameData, frameSize);
          } else if (frameId0 == 'P' && frameId1 == 'R' && frameId2 == 'I' && frameId3 == 'V') {
            frame = decodePrivFrame(frameData, frameSize);
          } else if (frameId0 == 'G' && frameId1 == 'E' && frameId2 == 'O' && frameId3 == 'B') {
            frame = decodeGeobFrame(frameData, frameSize);
          } else if (frameId0 == 'A' && frameId1 == 'P' && frameId2 == 'I' && frameId3 == 'C') {
            frame = decodeApicFrame(frameData, frameSize);
          } else if (frameId0 == 'T') {
            String id = frameId3 != 0 ?
                String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3) :
                String.format(Locale.US, "%c%c%c", frameId0, frameId1, frameId2);
            frame = decodeTextInformationFrame(frameData, frameSize, id);
          } else if (frameId0 == 'C' && frameId1 == 'O' && frameId2 == 'M' &&
              (frameId3 == 'M' || frameId3 == 0)) {
            CommentFrame commentFrame = decodeCommentFrame(frameData, frameSize);
            frame = commentFrame;
            if (gaplessInfo == null) {
              gaplessInfo = GaplessInfo.createFromComment(commentFrame.id, commentFrame.text);
            }
          } else {
            String id = frameId3 != 0 ?
                String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3) :
                String.format(Locale.US, "%c%c%c", frameId0, frameId1, frameId2);
            frame = decodeBinaryFrame(frameData, frameSize, id);
          }
          id3Frames.add(frame);
        } catch (UnsupportedEncodingException e) {
          throw new MetadataDecoderException("Unsupported character encoding");
        }
      }
    }

    return new Metadata(id3Frames, null);
  }

  private static int indexOfEos(byte[] data, int fromIndex, int encoding) {
    int terminationPos = indexOfZeroByte(data, fromIndex);

    // For single byte encoding charsets, we're done.
    if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
      return terminationPos;
    }

    // Otherwise ensure an even index and look for a second zero byte.
    while (terminationPos < data.length - 1) {
      if (terminationPos % 2 == 0 && data[terminationPos + 1] == (byte) 0) {
        return terminationPos;
      }
      terminationPos = indexOfZeroByte(data, terminationPos + 1);
    }

    return data.length;
  }

  private static int indexOfZeroByte(byte[] data, int fromIndex) {
    for (int i = fromIndex; i < data.length; i++) {
      if (data[i] == (byte) 0) {
        return i;
      }
    }
    return data.length;
  }

  private static int delimiterLength(int encodingByte) {
    return (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
        ? 1 : 2;
  }

  /**
   * @param id3Buffer A {@link ParsableByteArray} from which data should be read.
   * @return The size of ID3 frames in bytes, excluding the header and footer.
   * @throws ParserException If ID3 file identifier != "ID3".
   */
  private int decodeId3Header(ParsableByteArray id3Buffer) throws MetadataDecoderException {
    int id1 = id3Buffer.readUnsignedByte();
    int id2 = id3Buffer.readUnsignedByte();
    int id3 = id3Buffer.readUnsignedByte();
    if (id1 != 'I' || id2 != 'D' || id3 != '3') {
      throw new MetadataDecoderException(String.format(Locale.US,
          "Unexpected ID3 file identifier, expected \"ID3\", actual \"%c%c%c\".", id1, id2, id3));
    }

    majorVersion = id3Buffer.readUnsignedByte();
    minorVersion = id3Buffer.readUnsignedByte();

    int flags = id3Buffer.readUnsignedByte();
    int id3Size = id3Buffer.readSynchSafeInt();

    if (majorVersion < 4) {
      // this flag is advisory in version 4, use the frame flags instead
      isUnsynchronized = (flags & 0x80) != 0;
    }

    if (majorVersion == 3) {
      // check for extended header
      if ((flags & 0x40) != 0) {
        int extendedHeaderSize = id3Buffer.readInt(); // size excluding size field
        if (extendedHeaderSize == 6 || extendedHeaderSize == 10) {
          id3Buffer.skipBytes(extendedHeaderSize);
          id3Size -= (extendedHeaderSize + 4);
        }
      }
    } else if (majorVersion >= 4) {
      // check for extended header
      if ((flags & 0x40) != 0) {
        int extendedHeaderSize = id3Buffer.readSynchSafeInt();  // size including size field
        if (extendedHeaderSize > 4) {
          id3Buffer.skipBytes(extendedHeaderSize - 4);
        }
        id3Size -= extendedHeaderSize;
      }

      // Check if footer presents.
      if ((flags & 0x10) != 0) {
        id3Size -= 10;
      }
    }

    return id3Size;
  }

  private static TxxxFrame decodeTxxxFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    int valueStartIndex = descriptionEndIndex + delimiterLength(encoding);
    int valueEndIndex = indexOfEos(data, valueStartIndex, encoding);
    String value = new String(data, valueStartIndex, valueEndIndex - valueStartIndex, charset);

    return new TxxxFrame(description, value);
  }

  private static PrivFrame decodePrivFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    byte[] data = new byte[frameSize];
    id3Data.readBytes(data, 0, frameSize);

    int ownerEndIndex = indexOfZeroByte(data, 0);
    String owner = new String(data, 0, ownerEndIndex, "ISO-8859-1");

    int privateDataStartIndex = ownerEndIndex + 1;
    byte[] privateData = Arrays.copyOfRange(data, privateDataStartIndex, data.length);

    return new PrivFrame(owner, privateData);
  }

  private static GeobFrame decodeGeobFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int mimeTypeEndIndex = indexOfZeroByte(data, 0);
    String mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1");

    int filenameStartIndex = mimeTypeEndIndex + 1;
    int filenameEndIndex = indexOfEos(data, filenameStartIndex, encoding);
    String filename = new String(data, filenameStartIndex, filenameEndIndex - filenameStartIndex,
        charset);

    int descriptionStartIndex = filenameEndIndex + delimiterLength(encoding);
    int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
    String description = new String(data, descriptionStartIndex,
        descriptionEndIndex - descriptionStartIndex, charset);

    int objectDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
    byte[] objectData = Arrays.copyOfRange(data, objectDataStartIndex, data.length);

    return new GeobFrame(mimeType, filename, description, objectData);
  }

  private static ApicFrame decodeApicFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int mimeTypeEndIndex = indexOfZeroByte(data, 0);
    String mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1");

    int pictureType = data[mimeTypeEndIndex + 1] & 0xFF;

    int descriptionStartIndex = mimeTypeEndIndex + 2;
    int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
    String description = new String(data, descriptionStartIndex,
        descriptionEndIndex - descriptionStartIndex, charset);

    int pictureDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
    byte[] pictureData = Arrays.copyOfRange(data, pictureDataStartIndex, data.length);

    return new ApicFrame(mimeType, description, pictureType, pictureData);
  }

  private static TextInformationFrame decodeTextInformationFrame(ParsableByteArray id3Data,
      int frameSize, String id) throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[frameSize - 1];
    id3Data.readBytes(data, 0, frameSize - 1);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    return new TextInformationFrame(id, description);
  }

  private static CommentFrame decodeCommentFrame(ParsableByteArray id3Data, int frameSize)
      throws UnsupportedEncodingException {
    int encoding = id3Data.readUnsignedByte();
    String charset = getCharsetName(encoding);

    byte[] data = new byte[3];
    id3Data.readBytes(data, 0, 3);
    String language = new String(data, 0, 3);

    data = new byte[frameSize - 4];
    id3Data.readBytes(data, 0, frameSize - 4);

    int descriptionEndIndex = indexOfEos(data, 0, encoding);
    String description = new String(data, 0, descriptionEndIndex, charset);

    int textStartIndex = descriptionEndIndex + delimiterLength(encoding);
    int textEndIndex = indexOfEos(data, textStartIndex, encoding);
    String text = new String(data, textStartIndex, textEndIndex - textStartIndex, charset);

    return new CommentFrame(language, description, text);
  }

  private static BinaryFrame decodeBinaryFrame(ParsableByteArray id3Data, int frameSize,
      String id) {
    byte[] frame = new byte[frameSize];
    id3Data.readBytes(frame, 0, frameSize);

    return new BinaryFrame(id, frame);
  }

  /**
   * Undo the unsynchronization applied to one or more frames.
   * @param dataSource The original data, positioned at the beginning of a frame.
   * @param count The number of valid bytes in the frames to be processed.
   * @return replacement data for the frames.
   */
  private static ParsableByteArray removeUnsynchronization(ParsableByteArray dataSource, int count) {
    byte[] source = dataSource.data;
    int sourceIndex = dataSource.getPosition();
    int limit = sourceIndex + count;
    byte[] dest = new byte[count];
    int destIndex = 0;

    while (sourceIndex < limit) {
      byte b = source[sourceIndex++];
      if ((b & 0xFF) == 0xFF) {
        int nextIndex = sourceIndex+1;
        if (nextIndex < limit) {
          int b2 = source[nextIndex];
          if (b2 == 0) {
            // skip the 0 byte
            ++sourceIndex;
          }
        }
      }
      dest[destIndex++] = b;
    }

    return new ParsableByteArray(dest, destIndex);
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

  private final static String[] standardGenres = new String[] {

      // These are the official ID3v1 genres.
      "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
      "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap",
      "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
      "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
      "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
      "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
      "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative",
      "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
      "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
      "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
      "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
      "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
      "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
      "Hard Rock",

      // These were made up by the authors of Winamp but backported into the ID3 spec.
      "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion",
      "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
      "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
      "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour",
      "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
      "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club",
      "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
      "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House",
      "Dance Hall",

      // These were also invented by the Winamp folks but ignored by the ID3 authors.
      "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie",
      "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
      "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
      "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop",
      "Synthpop"
  };

  public static String decodeGenre(int n)
  {
    n--;

    if (n < 0 || n >= standardGenres.length) {
      return null;
    }

    return standardGenres[n];
  }

}
