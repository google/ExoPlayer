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
package com.google.android.exoplayer.mp4;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.chunk.parser.mp4.TrackEncryptionBox;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility methods for parsing MP4 format atom payloads according to ISO 14496-12. */
public final class CommonMp4AtomParsers {

  /** Channel counts for AC-3 audio, indexed by acmod. (See ETSI TS 102 366.) */
  private static final int[] AC3_CHANNEL_COUNTS = new int[] {2, 1, 2, 3, 3, 4, 4, 5};
  /** Nominal bitrates for AC-3 audio in kbps, indexed by bit_rate_code. (See ETSI TS 102 366.) */
  private static final int[] AC3_BITRATES = new int[] {32, 40, 48, 56, 64, 80, 96, 112, 128, 160,
      192, 224, 256, 320, 384, 448, 512, 576, 640};

  /**
   * Parses a trak atom (defined in 14496-12)
   *
   * @param trak Atom to parse.
   * @param mvhd Movie header atom, used to get the timescale.
   * @return A {@link Track} instance.
   */
  public static Track parseTrak(Atom.ContainerAtom trak, Atom.LeafAtom mvhd) {
    Atom.ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
    int trackType = parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data);
    Assertions.checkState(trackType == Track.TYPE_AUDIO || trackType == Track.TYPE_VIDEO
        || trackType == Track.TYPE_TEXT || trackType == Track.TYPE_TIME_CODE);

    Pair<Integer, Long> header = parseTkhd(trak.getLeafAtomOfType(Atom.TYPE_tkhd).data);
    int id = header.first;
    long duration = header.second;
    long movieTimescale = parseMvhd(mvhd.data);
    long durationUs;
    if (duration == -1) {
      durationUs = C.UNKNOWN_TIME_US;
    } else {
      durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, movieTimescale);
    }
    Atom.ContainerAtom stbl = mdia.getContainerAtomOfType(Atom.TYPE_minf)
        .getContainerAtomOfType(Atom.TYPE_stbl);

    long mediaTimescale = parseMdhd(mdia.getLeafAtomOfType(Atom.TYPE_mdhd).data);
    Pair<MediaFormat, TrackEncryptionBox[]> sampleDescriptions =
        parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data);
    return new Track(id, trackType, mediaTimescale, durationUs, sampleDescriptions.first,
          sampleDescriptions.second);
  }

  /**
   * Parses an stbl atom (defined in 14496-12).
   *
   * @param track Track to which this sample table corresponds.
   * @param stblAtom stbl (sample table) atom to parse.
   * @return Sample table described by the stbl atom.
   */
  public static Mp4TrackSampleTable parseStbl(Track track, Atom.ContainerAtom stblAtom) {
    // Array of sample sizes.
    ParsableByteArray stsz = stblAtom.getLeafAtomOfType(Atom.TYPE_stsz).data;

    // Entries are byte offsets of chunks.
    ParsableByteArray chunkOffsets;
    Atom.LeafAtom chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stco);
    if (chunkOffsetsAtom == null) {
      chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_co64);
    }
    chunkOffsets = chunkOffsetsAtom.data;
    // Entries are (chunk number, number of samples per chunk, sample description index).
    ParsableByteArray stsc = stblAtom.getLeafAtomOfType(Atom.TYPE_stsc).data;
    // Entries are (number of samples, timestamp delta between those samples).
    ParsableByteArray stts = stblAtom.getLeafAtomOfType(Atom.TYPE_stts).data;
    // Entries are the indices of samples that are synchronization samples.
    Atom.LeafAtom stssAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stss);
    ParsableByteArray stss = stssAtom != null ? stssAtom.data : null;
    // Entries are (number of samples, timestamp offset).
    Atom.LeafAtom cttsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_ctts);
    ParsableByteArray ctts = cttsAtom != null ? cttsAtom.data : null;

    // Skip full atom.
    stsz.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
    int fixedSampleSize = stsz.readUnsignedIntToInt();
    int sampleCount = stsz.readUnsignedIntToInt();

    int[] sizes = new int[sampleCount];
    long[] timestamps = new long[sampleCount];
    long[] offsets = new long[sampleCount];
    int[] flags = new int[sampleCount];

    // Prepare to read chunk offsets.
    chunkOffsets.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
    int chunkCount = chunkOffsets.readUnsignedIntToInt();

    stsc.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
    int remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt() - 1;
    Assertions.checkState(stsc.readInt() == 1, "stsc first chunk must be 1");
    int samplesPerChunk = stsc.readUnsignedIntToInt();
    stsc.skip(4); // Skip the sample description index.
    int nextSamplesPerChunkChangeChunkIndex = -1;
    if (remainingSamplesPerChunkChanges > 0) {
      // Store the chunk index when the samples-per-chunk will next change.
      nextSamplesPerChunkChangeChunkIndex = stsc.readUnsignedIntToInt() - 1;
    }

    int chunkIndex = 0;
    int remainingSamplesInChunk = samplesPerChunk;

    // Prepare to read sample timestamps.
    stts.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
    int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
    int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
    int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();

    // Prepare to read sample timestamp offsets, if ctts is present.
    boolean cttsHasSignedOffsets = false;
    int remainingSamplesAtTimestampOffset = 0;
    int remainingTimestampOffsetChanges = 0;
    int timestampOffset = 0;
    if (ctts != null) {
      ctts.setPosition(Mp4Util.ATOM_HEADER_SIZE);
      cttsHasSignedOffsets = Mp4Util.parseFullAtomVersion(ctts.readInt()) == 1;
      remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt() - 1;
      remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
      timestampOffset = cttsHasSignedOffsets ? ctts.readInt() : ctts.readUnsignedIntToInt();
    }

    int nextSynchronizationSampleIndex = -1;
    int remainingSynchronizationSamples = 0;
    if (stss != null) {
      stss.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
      remainingSynchronizationSamples = stss.readUnsignedIntToInt();
      nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
    }

    // Calculate the chunk offsets
    long offsetBytes;
    if (chunkOffsetsAtom.type == Atom.TYPE_stco) {
      offsetBytes = chunkOffsets.readUnsignedInt();
    } else {
      offsetBytes = chunkOffsets.readUnsignedLongToLong();
    }

    long timestampTimeUnits = 0;
    for (int i = 0; i < sampleCount; i++) {
      offsets[i] = offsetBytes;
      sizes[i] = fixedSampleSize == 0 ? stsz.readUnsignedIntToInt() : fixedSampleSize;
      timestamps[i] = timestampTimeUnits + timestampOffset;

      // All samples are synchronization samples if the stss is not present.
      flags[i] = stss == null ? C.SAMPLE_FLAG_SYNC : 0;
      if (i == nextSynchronizationSampleIndex) {
        flags[i] = C.SAMPLE_FLAG_SYNC;
        remainingSynchronizationSamples--;
        if (remainingSynchronizationSamples > 0) {
          nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
        }
      }

      // Add on the duration of this sample.
      timestampTimeUnits += timestampDeltaInTimeUnits;
      remainingSamplesAtTimestampDelta--;
      if (remainingSamplesAtTimestampDelta == 0 && remainingTimestampDeltaChanges > 0) {
        remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
        timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();
        remainingTimestampDeltaChanges--;
      }

      // Add on the timestamp offset if ctts is present.
      if (ctts != null) {
        remainingSamplesAtTimestampOffset--;
        if (remainingSamplesAtTimestampOffset == 0 && remainingTimestampOffsetChanges > 0) {
          remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
          timestampOffset = cttsHasSignedOffsets ? ctts.readInt() : ctts.readUnsignedIntToInt();
          remainingTimestampOffsetChanges--;
        }
      }

      // If we're at the last sample in this chunk, move to the next chunk.
      remainingSamplesInChunk--;
      if (remainingSamplesInChunk == 0) {
        chunkIndex++;
        if (chunkIndex < chunkCount) {
          if (chunkOffsetsAtom.type == Atom.TYPE_stco) {
            offsetBytes = chunkOffsets.readUnsignedInt();
          } else {
            offsetBytes = chunkOffsets.readUnsignedLongToLong();
          }
        }

        // Change the samples-per-chunk if required.
        if (chunkIndex == nextSamplesPerChunkChangeChunkIndex) {
          samplesPerChunk = stsc.readUnsignedIntToInt();
          stsc.skip(4); // Skip the sample description index.
          remainingSamplesPerChunkChanges--;
          if (remainingSamplesPerChunkChanges > 0) {
            nextSamplesPerChunkChangeChunkIndex = stsc.readUnsignedIntToInt() - 1;
          }
        }

        // Expect samplesPerChunk samples in the following chunk, if it's before the end.
        if (chunkIndex < chunkCount) {
          remainingSamplesInChunk = samplesPerChunk;
        }
      } else {
        // The next sample follows the current one.
        offsetBytes += sizes[i];
      }
    }

    Util.scaleLargeTimestampsInPlace(timestamps, 1000000, track.timescale);

    // Check all the expected samples have been seen.
    Assertions.checkArgument(remainingSynchronizationSamples == 0);
    Assertions.checkArgument(remainingSamplesAtTimestampDelta == 0);
    Assertions.checkArgument(remainingSamplesInChunk == 0);
    Assertions.checkArgument(remainingTimestampDeltaChanges == 0);
    Assertions.checkArgument(remainingTimestampOffsetChanges == 0);
    return new Mp4TrackSampleTable(offsets, sizes, timestamps, flags);
  }

  /**
   * Parses a mvhd atom (defined in 14496-12), returning the timescale for the movie.
   *
   * @param mvhd Contents of the mvhd atom to be parsed.
   * @return Timescale for the movie.
   */
  private static long parseMvhd(ParsableByteArray mvhd) {
    mvhd.setPosition(Mp4Util.ATOM_HEADER_SIZE);

    int fullAtom = mvhd.readInt();
    int version = Mp4Util.parseFullAtomVersion(fullAtom);

    mvhd.skip(version == 0 ? 8 : 16);

    return mvhd.readUnsignedInt();
  }

  /**
   * Parses a tkhd atom (defined in 14496-12).
   *
   * @return A {@link Pair} consisting of the track id and duration (in the timescale indicated in
   *     the movie header box). The duration is set to -1 if the duration is unspecified.
   */
  private static Pair<Integer, Long> parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(Mp4Util.ATOM_HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = Mp4Util.parseFullAtomVersion(fullAtom);

    tkhd.skip(version == 0 ? 8 : 16);

    int trackId = tkhd.readInt();
    tkhd.skip(4);

    boolean durationUnknown = true;
    int durationPosition = tkhd.getPosition();
    int durationByteCount = version == 0 ? 4 : 8;
    for (int i = 0; i < durationByteCount; i++) {
      if (tkhd.data[durationPosition + i] != -1) {
        durationUnknown = false;
        break;
      }
    }
    long duration;
    if (durationUnknown) {
      tkhd.skip(durationByteCount);
      duration = -1;
    } else {
      duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();
    }

    return Pair.create(trackId, duration);
  }

  /**
   * Parses an hdlr atom.
   *
   * @param hdlr The hdlr atom to parse.
   * @return The track type.
   */
  private static int parseHdlr(ParsableByteArray hdlr) {
    hdlr.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE + 4);
    return hdlr.readInt();
  }

  /**
   * Parses an mdhd atom (defined in 14496-12).
   *
   * @param mdhd The mdhd atom to parse.
   * @return The media timescale, defined as the number of time units that pass in one second.
   */
  private static long parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(Mp4Util.ATOM_HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = Mp4Util.parseFullAtomVersion(fullAtom);

    mdhd.skip(version == 0 ? 8 : 16);
    return mdhd.readUnsignedInt();
  }

  private static Pair<MediaFormat, TrackEncryptionBox[]> parseStsd(ParsableByteArray stsd) {
    stsd.setPosition(Mp4Util.FULL_ATOM_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    MediaFormat mediaFormat = null;
    TrackEncryptionBox[] trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = stsd.readInt();
      if (childAtomType == Atom.TYPE_avc1 || childAtomType == Atom.TYPE_avc3
          || childAtomType == Atom.TYPE_encv) {
        Pair<MediaFormat, TrackEncryptionBox> avc =
            parseAvcFromParent(stsd, childStartPosition, childAtomSize);
        mediaFormat = avc.first;
        trackEncryptionBoxes[i] = avc.second;
      } else if (childAtomType == Atom.TYPE_mp4a || childAtomType == Atom.TYPE_enca
          || childAtomType == Atom.TYPE_ac_3) {
        Pair<MediaFormat, TrackEncryptionBox> audioSampleEntry =
            parseAudioSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize);
        mediaFormat = audioSampleEntry.first;
        trackEncryptionBoxes[i] = audioSampleEntry.second;
      } else if (childAtomType == Atom.TYPE_TTML) {
        mediaFormat = MediaFormat.createTtmlFormat();
      } else if (childAtomType == Atom.TYPE_mp4v) {
        mediaFormat = parseMp4vFromParent(stsd, childStartPosition, childAtomSize);
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return Pair.create(mediaFormat, trackEncryptionBoxes);
  }

  /** Returns the media format for an avc1 box. */
  private static Pair<MediaFormat, TrackEncryptionBox> parseAvcFromParent(ParsableByteArray parent,
      int position, int size) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);

    parent.skip(24);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    float pixelWidthHeightRatio = 1;
    parent.skip(50);

    List<byte[]> initializationData = null;
    TrackEncryptionBox trackEncryptionBox = null;
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      if (childAtomSize == 0 && parent.getPosition() - position == size) {
        // Handle optional terminating four zero bytes in MOV files.
        break;
      }
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_avcC) {
        initializationData = parseAvcCFromParent(parent, childStartPosition);
      } else if (childAtomType == Atom.TYPE_sinf) {
        trackEncryptionBox = parseSinfFromParent(parent, childStartPosition, childAtomSize);
      } else if (childAtomType == Atom.TYPE_pasp) {
        pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
      }
      childPosition += childAtomSize;
    }

    MediaFormat format = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        width, height, pixelWidthHeightRatio, initializationData);
    return Pair.create(format, trackEncryptionBox);
  }

  private static List<byte[]> parseAvcCFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE + 4);
    // Start of the AVCDecoderConfigurationRecord (defined in 14496-15)
    int nalUnitLength = (parent.readUnsignedByte() & 0x3) + 1;
    if (nalUnitLength != 4) {
      // readSample currently relies on a nalUnitLength of 4.
      // TODO: Consider handling the case where it isn't.
      throw new IllegalStateException();
    }
    List<byte[]> initializationData = new ArrayList<byte[]>();
    // TODO: We should try and parse these using CodecSpecificDataUtil.parseSpsNalUnit, and
    // expose the AVC profile and level somewhere useful; Most likely in MediaFormat.
    int numSequenceParameterSets = parent.readUnsignedByte() & 0x1F;
    for (int j = 0; j < numSequenceParameterSets; j++) {
      initializationData.add(Mp4Util.parseChildNalUnit(parent));
    }
    int numPictureParameterSets = parent.readUnsignedByte();
    for (int j = 0; j < numPictureParameterSets; j++) {
      initializationData.add(Mp4Util.parseChildNalUnit(parent));
    }
    return initializationData;
  }

  private static TrackEncryptionBox parseSinfFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + Mp4Util.ATOM_HEADER_SIZE;

    TrackEncryptionBox trackEncryptionBox = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_frma) {
        parent.readInt(); // dataFormat.
      } else if (childAtomType == Atom.TYPE_schm) {
        parent.skip(4);
        parent.readInt(); // schemeType. Expect cenc
        parent.readInt(); // schemeVersion. Expect 0x00010000
      } else if (childAtomType == Atom.TYPE_schi) {
        trackEncryptionBox = parseSchiFromParent(parent, childPosition, childAtomSize);
      }
      childPosition += childAtomSize;
    }

    return trackEncryptionBox;
  }

  private static float parsePaspFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);
    int hSpacing = parent.readUnsignedIntToInt();
    int vSpacing = parent.readUnsignedIntToInt();
    return (float) hSpacing / vSpacing;
  }

  private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + Mp4Util.ATOM_HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_tenc) {
        parent.skip(4);
        int firstInt = parent.readInt();
        boolean defaultIsEncrypted = (firstInt >> 8) == 1;
        int defaultInitVectorSize = firstInt & 0xFF;
        byte[] defaultKeyId = new byte[16];
        parent.readBytes(defaultKeyId, 0, defaultKeyId.length);
        return new TrackEncryptionBox(defaultIsEncrypted, defaultInitVectorSize, defaultKeyId);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /** Returns the media format for an mp4v box. */
  private static MediaFormat parseMp4vFromParent(ParsableByteArray parent,
      int position, int size) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);

    parent.skip(24);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    parent.skip(50);

    List<byte[]> initializationData = new ArrayList<byte[]>(1);
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_esds) {
        initializationData.add(parseEsdsFromParent(parent, childStartPosition));
      }
      childPosition += childAtomSize;
    }

    return MediaFormat.createVideoFormat(
        MimeTypes.VIDEO_MP4V, MediaFormat.NO_VALUE, width, height, initializationData);
  }

  private static Pair<MediaFormat, TrackEncryptionBox> parseAudioSampleEntry(
      ParsableByteArray parent, int atomType, int position, int size) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);
    parent.skip(16);
    int channelCount = parent.readUnsignedShort();
    int sampleSize = parent.readUnsignedShort();
    parent.skip(4);
    int sampleRate = parent.readUnsignedFixedPoint1616();
    int bitrate = MediaFormat.NO_VALUE;

    byte[] initializationData = null;
    TrackEncryptionBox trackEncryptionBox = null;
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (atomType == Atom.TYPE_mp4a || atomType == Atom.TYPE_enca) {
        if (childAtomType == Atom.TYPE_esds) {
          initializationData = parseEsdsFromParent(parent, childStartPosition);
          // TODO: Do we really need to do this? See [Internal: b/10903778]
          // Update sampleRate and channelCount from the AudioSpecificConfig initialization data.
          Pair<Integer, Integer> audioSpecificConfig =
              CodecSpecificDataUtil.parseAudioSpecificConfig(initializationData);
          sampleRate = audioSpecificConfig.first;
          channelCount = audioSpecificConfig.second;
        } else if (childAtomType == Atom.TYPE_sinf) {
          trackEncryptionBox = parseSinfFromParent(parent, childStartPosition, childAtomSize);
        }
      } else if (atomType == Atom.TYPE_ac_3 && childAtomType == Atom.TYPE_dac3) {
        // TODO: Choose the right AC-3 track based on the contents of dac3/dec3.
        Ac3Format ac3Format =
            parseAc3SpecificBoxFromParent(parent, childStartPosition);
        if (ac3Format != null) {
          sampleRate = ac3Format.sampleRate;
          channelCount = ac3Format.channelCount;
          bitrate = ac3Format.bitrate;
        }

        // TODO: Add support for encrypted AC-3.
        trackEncryptionBox = null;
      } else if (atomType == Atom.TYPE_ec_3 && childAtomType == Atom.TYPE_dec3) {
        sampleRate = parseEc3SpecificBoxFromParent(parent, childStartPosition);
        trackEncryptionBox = null;
      }
      childPosition += childAtomSize;
    }

    String mimeType;
    if (atomType == Atom.TYPE_ac_3) {
      mimeType = MimeTypes.AUDIO_AC3;
    } else if (atomType == Atom.TYPE_ec_3) {
      mimeType = MimeTypes.AUDIO_EC3;
    } else {
      mimeType = MimeTypes.AUDIO_AAC;
    }

    MediaFormat format = MediaFormat.createAudioFormat(
        mimeType, sampleSize, channelCount, sampleRate, bitrate,
        initializationData == null ? null : Collections.singletonList(initializationData));
    return Pair.create(format, trackEncryptionBox);
  }

  /** Returns codec-specific initialization data contained in an esds box. */
  private static byte[] parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE + 4);
    // Start of the ES_Descriptor (defined in 14496-1)
    parent.skip(1); // ES_Descriptor tag
    int varIntByte = parent.readUnsignedByte();
    while (varIntByte > 127) {
      varIntByte = parent.readUnsignedByte();
    }
    parent.skip(2); // ES_ID

    int flags = parent.readUnsignedByte();
    if ((flags & 0x80 /* streamDependenceFlag */) != 0) {
      parent.skip(2);
    }
    if ((flags & 0x40 /* URL_Flag */) != 0) {
      parent.skip(parent.readUnsignedShort());
    }
    if ((flags & 0x20 /* OCRstreamFlag */) != 0) {
      parent.skip(2);
    }

    // Start of the DecoderConfigDescriptor (defined in 14496-1)
    parent.skip(1); // DecoderConfigDescriptor tag
    varIntByte = parent.readUnsignedByte();
    while (varIntByte > 127) {
      varIntByte = parent.readUnsignedByte();
    }
    parent.skip(13);

    // Start of AudioSpecificConfig (defined in 14496-3)
    parent.skip(1); // AudioSpecificConfig tag
    varIntByte = parent.readUnsignedByte();
    int varInt = varIntByte & 0x7F;
    while (varIntByte > 127) {
      varIntByte = parent.readUnsignedByte();
      varInt = varInt << 8;
      varInt |= varIntByte & 0x7F;
    }
    byte[] initializationData = new byte[varInt];
    parent.readBytes(initializationData, 0, varInt);
    return initializationData;
  }

  private static Ac3Format parseAc3SpecificBoxFromParent(ParsableByteArray parent, int position) {
    // Start of the dac3 atom (defined in ETSI TS 102 366)
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);

    // fscod (sample rate code)
    int fscod = (parent.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate;
    switch (fscod) {
      case 0:
        sampleRate = 48000;
        break;
      case 1:
        sampleRate = 44100;
        break;
      case 2:
        sampleRate = 32000;
        break;
      default:
        // TODO: The decoder should not use this stream.
        return null;
    }

    int nextByte = parent.readUnsignedByte();

    // Map acmod (audio coding mode) onto a channel count.
    int channelCount = AC3_CHANNEL_COUNTS[(nextByte & 0x38) >> 3];

    // lfeon (low frequency effects on)
    if ((nextByte & 0x04) != 0) {
      channelCount++;
    }

    // Map bit_rate_code onto a bitrate in kbit/s.
    int bitrate = AC3_BITRATES[((nextByte & 0x03) << 3) + (parent.readUnsignedByte() >> 5)];

    return new Ac3Format(channelCount, sampleRate, bitrate);
  }

  private static int parseEc3SpecificBoxFromParent(ParsableByteArray parent, int position) {
    // Start of the dec3 atom (defined in ETSI TS 102 366)
    parent.setPosition(position + Mp4Util.ATOM_HEADER_SIZE);
    // TODO: Implement parsing for enhanced AC-3 with multiple sub-streams.
    return 0;
  }

  private CommonMp4AtomParsers() {
    // Prevent instantiation.
  }

  /** Represents the format for AC-3 audio. */
  private static final class Ac3Format {

    public final int channelCount;
    public final int sampleRate;
    public final int bitrate;

    public Ac3Format(int channelCount, int sampleRate, int bitrate) {
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.bitrate = bitrate;
    }

  }

}
