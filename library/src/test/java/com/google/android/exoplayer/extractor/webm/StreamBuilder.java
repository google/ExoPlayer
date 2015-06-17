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

import com.google.android.exoplayer.util.Assertions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides byte arrays containing WebM data for {@link WebmExtractorTest}.
 */
/* package */ final class StreamBuilder {

  /** Used by {@link #addVp9Track} to create a Track header with Encryption. */
  public static final class ContentEncodingSettings {

    private final int order;
    private final int scope;
    private final int type;
    private final int algorithm;
    private final int aesCipherMode;

    public ContentEncodingSettings(int order, int scope, int type, int algorithm,
        int aesCipherMode) {
      this.order = order;
      this.scope = scope;
      this.type = type;
      this.algorithm = algorithm;
      this.aesCipherMode = aesCipherMode;
    }

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

  public static final byte[] TEST_ENCRYPTION_KEY_ID = { 0x00, 0x01, 0x02, 0x03 };
  public static final byte[] TEST_INITIALIZATION_VECTOR = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
  };

  private static final int NO_VALUE = -1;

  private EbmlElement header;
  private EbmlElement info;
  private List<EbmlElement> trackEntries;
  private List<EbmlElement> mediaSegments;

  public StreamBuilder() {
    trackEntries = new LinkedList<>();
    mediaSegments = new LinkedList<>();
  }

  public StreamBuilder setHeader(String docType) {
    header = createEbmlElement(1, docType, 2);
    return this;
  }

  public StreamBuilder setInfo(int timecodeScale, long durationUs) {
    info = createInfoElement(timecodeScale, durationUs);
    return this;
  }

  public StreamBuilder addVp9Track(int width, int height,
      ContentEncodingSettings contentEncodingSettings) {
    trackEntries.add(createVideoTrackEntry("V_VP9", width, height, contentEncodingSettings, null));
    return this;
  }

  public StreamBuilder addOpusTrack(int channelCount, int sampleRate, int codecDelay,
      int seekPreRoll, byte[] codecPrivate) {
    trackEntries.add(createAudioTrackEntry("A_OPUS", channelCount, sampleRate, codecPrivate,
        codecDelay, seekPreRoll));
    return this;
  }

  public StreamBuilder addVorbisTrack(int channelCount, int sampleRate, byte[] codecPrivate) {
    trackEntries.add(createAudioTrackEntry("A_VORBIS", channelCount, sampleRate, codecPrivate,
        NO_VALUE, NO_VALUE));
    return this;
  }

  public StreamBuilder addUnsupportedTrack() {
    trackEntries.add(element(0xAE, // TrackEntry
        element(0x86, "D_WEBVTT/metadata".getBytes()), // CodecID
        element(0xD7, (byte) 0x03), // TrackNumber
        element(0x83, (byte) 0x11))); // TrackType
    return this;
  }

  public StreamBuilder addSimpleBlockEncryptedMedia(int trackNumber, int clusterTimecode,
      int blockTimecode, boolean keyframe, boolean invisible, boolean validSignalByte,
      byte[] data) {
    byte flags = (byte) ((keyframe ? 0x80 : 0x00) | (invisible ? 0x08 : 0x00));
    EbmlElement simpleBlockElement = createSimpleBlock(trackNumber, blockTimecode, flags,
        true, validSignalByte, data);
    mediaSegments.add(createCluster(clusterTimecode, simpleBlockElement));
    return this;
  }


  public StreamBuilder addSimpleBlockMedia(int trackNumber, int clusterTimecode,
      int blockTimecode, boolean keyframe, boolean invisible, byte[] data) {
    byte flags = (byte) ((keyframe ? 0x80 : 0x00) | (invisible ? 0x08 : 0x00));
    EbmlElement simpleBlockElement = createSimpleBlock(trackNumber, blockTimecode, flags,
        false, true, data);
    mediaSegments.add(createCluster(clusterTimecode, simpleBlockElement));
    return this;
  }

  public StreamBuilder addBlockMedia(int trackNumber, int clusterTimecode, int blockTimecode,
      boolean keyframe, boolean invisible, byte[] data) {
    byte flags = (byte) (invisible ? 0x08 : 0x00);
    EbmlElement blockElement =
        createBlock(trackNumber, blockTimecode, keyframe, flags, data);
    mediaSegments.add(createCluster(clusterTimecode, blockElement));
    return this;
  }

  /**
   * Serializes the constructed stream to a {@code byte[]} using the specified number of cue points.
   */
  public byte[] build(int cuePointCount) {
    Assertions.checkNotNull(header);
    Assertions.checkNotNull(info);

    EbmlElement tracks = element(0x1654AE6B, trackEntries.toArray(new EbmlElement[0]));

    // Get the size of the initialization segment.
    EbmlElement[] cuePointElements = new EbmlElement[cuePointCount];
    for (int i = 0; i < cuePointCount; i++) {
      cuePointElements[i] = createCuePointElement(10 * i, 0);
    }
    EbmlElement cues = element(0x1C53BB6B, cuePointElements); // Cues
    long initializationSegmentSize = info.getSize() + tracks.getSize() + cues.getSize();

    // Recreate the initialization segment using its size as an offset.
    for (int i = 0; i < cuePointCount; i++) {
      cuePointElements[i] = createCuePointElement(10 * i, (int) initializationSegmentSize);
    }
    cues = element(0x1C53BB6B, cuePointElements); // Cues

    // Build the top-level segment element.
    EbmlElement[] children = new EbmlElement[3 + mediaSegments.size()];
    System.arraycopy(mediaSegments.toArray(new EbmlElement[0]), 0, children, 3,
        mediaSegments.size());
    children[0] = info;
    children[1] = tracks;
    children[2] = cues;
    EbmlElement segmentElement = element(0x18538067, children); // Segment

    // Serialize the EBML header and the top-level segment element.
    return EbmlElement.serialize(header, segmentElement);
  }

  private static EbmlElement createCuePointElement(int cueTime, int cueClusterPosition) {
    byte[] positionBytes = getLongBytes(cueClusterPosition);
    return element(0xBB, // CuePoint
        element(0xB3, (byte) (cueTime & 0xFF)), // CueTime
        element(0xB7, // CueTrackPositions
            element(0xF1, positionBytes))); // CueClusterPosition
  }

  private static EbmlElement createEbmlElement(int ebmlReadVersion, String docType,
      int docTypeReadVersion) {
    return element(0x1A45DFA3, // EBML
        element(0x42F7, (byte) (ebmlReadVersion & 0xFF)), // EBMLReadVersion
        element(0x4282, docType.getBytes()), // DocType
        element(0x4285, (byte) (docTypeReadVersion & 0xFF))); // DocTypeReadVersion
  }

  private EbmlElement createInfoElement(int timecodeScale, long durationUs) {
    byte[] timecodeScaleBytes = getIntegerBytes(timecodeScale);
    byte[] durationBytes = getLongBytes(Double.doubleToLongBits(durationUs / 1000.0));
    return element(0x1549A966, // Info
        element(0x2AD7B1, timecodeScaleBytes), // TimecodeScale
        element(0x4489, durationBytes)); // Duration
  }

  private static EbmlElement createVideoTrackEntry(String codecId, int pixelWidth, int pixelHeight,
      ContentEncodingSettings contentEncodingSettings, byte[] codecPrivate) {
    byte[] widthBytes = getIntegerBytes(pixelWidth);
    byte[] heightBytes = getIntegerBytes(pixelHeight);
    EbmlElement contentEncodingSettingsElement;
    if (contentEncodingSettings != null) {
      contentEncodingSettingsElement =
          element(0x6D80, // ContentEncodings
            element(0x6240, // ContentEncoding
                // ContentEncodingOrder
                element(0x5031, (byte) (contentEncodingSettings.order & 0xFF)),
                // ContentEncodingScope
                element(0x5032, (byte) (contentEncodingSettings.scope & 0xFF)),
                // ContentEncodingType
                element(0x5033, (byte) (contentEncodingSettings.type & 0xFF)),
                element(0x5035, // ContentEncryption
                    // ContentEncAlgo
                    element(0x47E1, (byte) (contentEncodingSettings.algorithm & 0xFF)),
                    element(0x47E2, TEST_ENCRYPTION_KEY_ID), // ContentEncKeyID
                    element(0x47E7, // ContentEncAESSettings
                        // AESSettingsCipherMode
                        element(0x47E8, (byte) (contentEncodingSettings.aesCipherMode & 0xFF))))));
    } else {
      contentEncodingSettingsElement = empty();
    }
    EbmlElement codecPrivateElement;
    if (codecPrivate != null) {
      codecPrivateElement = element(0x63A2, codecPrivate); // CodecPrivate
    } else {
      codecPrivateElement = empty();
    }

    return element(0xAE, // TrackEntry
        element(0x86, codecId.getBytes()), // CodecID
        element(0xD7, (byte) 0x01), // TrackNumber
        element(0x83, (byte) 0x01), // TrackType
        contentEncodingSettingsElement,
        element(0xE0, // Video
            element(0xB0, widthBytes[2], widthBytes[3]),
            element(0xBA, heightBytes[2], heightBytes[3])),
        codecPrivateElement);
  }

  private static EbmlElement createAudioTrackEntry(String codecId, int channelCount, int sampleRate,
      byte[] codecPrivate, int codecDelay, int seekPreRoll) {
    byte channelCountByte = (byte) (channelCount & 0xFF);
    byte[] sampleRateDoubleBytes = getLongBytes(Double.doubleToLongBits(sampleRate));
    return element(0xAE, // TrackEntry
        element(0x86, codecId.getBytes()), // CodecID
        element(0xD7, (byte) 0x02), // TrackNumber
        element(0x83, (byte) 0x02), // TrackType
        // CodecDelay
        codecDelay != NO_VALUE ? element(0x56AA, getIntegerBytes(codecDelay)) : empty(),
        // SeekPreRoll
        seekPreRoll != NO_VALUE ? element(0x56BB, getIntegerBytes(seekPreRoll)) : empty(),
        element(0xE1, // Audio
            element(0x9F, channelCountByte), // Channels
            element(0xB5, sampleRateDoubleBytes)), // SamplingFrequency
        element(0x63A2, codecPrivate)); // CodecPrivate
  }

  private static EbmlElement createCluster(int timecode, EbmlElement blockGroupOrSimpleBlock) {
    return element(0x1F43B675, // Cluster
        element(0xE7, getIntegerBytes(timecode)), // Timecode
        blockGroupOrSimpleBlock);
  }

  private static EbmlElement createSimpleBlock(int trackNumber, int timecode, int flags,
      boolean encrypted, boolean validSignalByte, byte[] data) {
    byte[] trackNumberBytes = getIntegerBytes(trackNumber);
    byte[] timeBytes = getIntegerBytes(timecode);
    byte[] simpleBlockBytes = createByteArray(
          0x40, trackNumberBytes[3], // Track number size=2
          timeBytes[2], timeBytes[3], flags); // Timecode and flags
    if (encrypted) {
      simpleBlockBytes = joinByteArrays(
          simpleBlockBytes, createByteArray(validSignalByte ? 0x01 : 0x80),
          Arrays.copyOfRange(TEST_INITIALIZATION_VECTOR, 0, 8));
    }
    return element(0xA3, // SimpleBlock
        joinByteArrays(simpleBlockBytes, data));
  }

  private static EbmlElement createBlock(int trackNumber, int timecode, boolean keyframe, int flags,
      byte[] data) {
    byte[] trackNumberBytes = getIntegerBytes(trackNumber);
    byte[] timeBytes = getIntegerBytes(timecode);
    byte[] blockBytes = createByteArray(
        0x40, trackNumberBytes[3], // Track number size=2
        timeBytes[2], timeBytes[3], flags); // Timecode and flags
    EbmlElement block = element(0xA1, // Block
        joinByteArrays(blockBytes, data));
    EbmlElement referenceBlock = keyframe ? empty() : element(0xFB, (byte) 0x00); // ReferenceBlock
    return element(0xA0, // BlockGroup
        referenceBlock,
        block);
  }

  private static byte[] createByteArray(int... intArray) {
    byte[] byteArray = new byte[intArray.length];
    for (int i = 0; i < byteArray.length; i++) {
      byteArray[i] = (byte) intArray[i];
    }
    return byteArray;
  }

  private static byte[] getIntegerBytes(int value) {
    return createByteArray(
        (value & 0xFF000000) >> 24,
        (value & 0x00FF0000) >> 16,
        (value & 0x0000FF00) >> 8,
        (value & 0x000000FF));
  }

  private static byte[] getLongBytes(long value) {
    byte[] result = new byte[8];
    for (int i = 0; i < 8; i++) {
      result[7 - i] = (byte) ((value >> (8 * i)) & 0xFF);
    }
    return result;
  }

  /** @see EbmlElement#EbmlElement(int, EbmlElement...) */
  private static EbmlElement element(int type, EbmlElement... childElements) {
    return new EbmlElement(type, childElements);
  }

  /** @see EbmlElement#EbmlElement(int, byte...) */
  private static EbmlElement element(int type, byte... payload) {
    return new EbmlElement(type, payload);
  }

  /** @see EbmlElement#EbmlElement() */
  private static EbmlElement empty() {
    return new EbmlElement();
  }

  /** Represents a WebM EBML element that can be serialized as a byte array. */
  private static final class EbmlElement {

    /** Returns a byte[] containing the concatenation of the data from all {@code elements}. */
    public static byte[] serialize(EbmlElement... elements) {
      int size = 0;
      for (EbmlElement element : elements) {
        size += element.getSize();
      }
      ByteBuffer buffer = ByteBuffer.allocate(size);
      for (EbmlElement element : elements) {
        element.getData(buffer);
      }
      return buffer.array();
    }

    private final int id;
    private final EbmlElement[] childElements;
    private final byte[] payload;

    /** Creates an element containing the specified {@code childElements}. */
    EbmlElement(int id, EbmlElement... childElements) {
      this.id = id;
      this.childElements = childElements;
      payload = null;
    }

    /** Creates an element containing the specified {@code payload}. */
    EbmlElement(int id, byte... payload) {
      this.id = id;
      this.payload = payload;
      childElements = null;
    }

    /** Creates a completely empty element that will contribute no bytes to the output. */
    EbmlElement() {
      id = NO_VALUE;
      payload = null;
      childElements = null;
    }

    private long getSize() {
      if (id == NO_VALUE) {
        return 0;
      }

      long payloadSize = getPayloadSize();
      return getIdLength() + getVIntLength(payloadSize) + payloadSize;
    }

    private long getPayloadSize() {
      if (payload != null) {
        return payload.length;
      }

      int payloadSize = 0;
      for (EbmlElement element : childElements) {
        payloadSize += element.getSize();
      }
      return payloadSize;
    }

    private void getData(ByteBuffer byteBuffer) {
      if (id == NO_VALUE) {
        return;
      }

      putId(byteBuffer);
      putVInt(byteBuffer, getPayloadSize());
      if (payload != null) {
        byteBuffer.put(payload);
      } else {
        for (EbmlElement atom : childElements) {
          atom.getData(byteBuffer);
        }
      }
    }

    private int getIdLength() {
      if (id == NO_VALUE) {
        return 0;
      }

      for (int i = 0; i < 4; i++) {
        if (id < 1 << (7 * i + 8)) {
          return i + 1;
        }
      }
      throw new IllegalArgumentException();
    }

    private static int getVIntLength(long vInt) {
      for (int i = 1; i < 9; i++) {
        if (vInt < 1 << (7 * i)) {
          return i;
        }
      }
      throw new IllegalArgumentException();
    }

    private void putId(ByteBuffer byteBuffer) {
      int length = getIdLength();
      for (int i = length - 1; i >= 0; i--) {
        byteBuffer.put((byte) ((id >> (i * 8)) & 0xFF));
      }
    }

    private static void putVInt(ByteBuffer byteBuffer, long vInt) {
      int vIntLength = getVIntLength(vInt);
      vInt |= 1 << (vIntLength * 7);
      for (int i = vIntLength - 1; i >= 0; i--) {
        byteBuffer.put((byte) ((vInt >> (i * 8)) & 0xFF));
      }
    }

  }

}
