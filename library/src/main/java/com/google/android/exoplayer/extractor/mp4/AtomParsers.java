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
package com.google.android.exoplayer.extractor.mp4;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.util.Ac3Util;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.H264Util;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility methods for parsing MP4 format atom payloads according to ISO 14496-12. */
/* package */ final class AtomParsers {

  /**
   * Parses a trak atom (defined in 14496-12).
   *
   * @param trak Atom to parse.
   * @param mvhd Movie header atom, used to get the timescale.
   * @return A {@link Track} instance, or {@code null} if the track's type isn't supported.
   */
  public static Track parseTrak(Atom.ContainerAtom trak, Atom.LeafAtom mvhd) {
    Atom.ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
    int trackType = parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data);
    if (trackType != Track.TYPE_AUDIO && trackType != Track.TYPE_VIDEO
        && trackType != Track.TYPE_TEXT && trackType != Track.TYPE_TIME_CODE) {
      return null;
    }

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
    StsdDataHolder stsdData = parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data, durationUs);
    return new Track(id, trackType, mediaTimescale, durationUs, stsdData.mediaFormat,
        stsdData.trackEncryptionBoxes, stsdData.nalUnitLengthFieldLength);
  }

  /**
   * Parses an stbl atom (defined in 14496-12).
   *
   * @param track Track to which this sample table corresponds.
   * @param stblAtom stbl (sample table) atom to parse.
   * @return Sample table described by the stbl atom.
   */
  public static TrackSampleTable parseStbl(Track track, Atom.ContainerAtom stblAtom) {
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
    stsz.setPosition(Atom.FULL_HEADER_SIZE);
    int fixedSampleSize = stsz.readUnsignedIntToInt();
    int sampleCount = stsz.readUnsignedIntToInt();

    int[] sizes = new int[sampleCount];
    long[] timestamps = new long[sampleCount];
    long[] offsets = new long[sampleCount];
    int[] flags = new int[sampleCount];

    // Prepare to read chunk offsets.
    chunkOffsets.setPosition(Atom.FULL_HEADER_SIZE);
    int chunkCount = chunkOffsets.readUnsignedIntToInt();

    stsc.setPosition(Atom.FULL_HEADER_SIZE);
    int remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt() - 1;
    Assertions.checkState(stsc.readInt() == 1, "stsc first chunk must be 1");
    int samplesPerChunk = stsc.readUnsignedIntToInt();
    stsc.skipBytes(4); // Skip the sample description index.
    int nextSamplesPerChunkChangeChunkIndex = -1;
    if (remainingSamplesPerChunkChanges > 0) {
      // Store the chunk index when the samples-per-chunk will next change.
      nextSamplesPerChunkChangeChunkIndex = stsc.readUnsignedIntToInt() - 1;
    }

    int chunkIndex = 0;
    int remainingSamplesInChunk = samplesPerChunk;

    // Prepare to read sample timestamps.
    stts.setPosition(Atom.FULL_HEADER_SIZE);
    int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
    int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
    int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();

    // Prepare to read sample timestamp offsets, if ctts is present.
    int remainingSamplesAtTimestampOffset = 0;
    int remainingTimestampOffsetChanges = 0;
    int timestampOffset = 0;
    if (ctts != null) {
      ctts.setPosition(Atom.FULL_HEADER_SIZE);
      remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt() - 1;
      remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
      // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers in
      // version 0 ctts boxes, however some streams violate the spec and use signed integers
      // instead. It's safe to always parse sample offsets as signed integers here, because
      // unsigned integers will still be parsed correctly (unless their top bit is set, which
      // is never true in practice because sample offsets are always small).
      timestampOffset = ctts.readInt();
    }

    int nextSynchronizationSampleIndex = -1;
    int remainingSynchronizationSamples = 0;
    if (stss != null) {
      stss.setPosition(Atom.FULL_HEADER_SIZE);
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
          // Read a signed offset even for version 0 ctts boxes (see comment above).
          timestampOffset = ctts.readInt();
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
          stsc.skipBytes(4); // Skip the sample description index.
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
    return new TrackSampleTable(offsets, sizes, timestamps, flags);
  }

  /**
   * Parses a mvhd atom (defined in 14496-12), returning the timescale for the movie.
   *
   * @param mvhd Contents of the mvhd atom to be parsed.
   * @return Timescale for the movie.
   */
  private static long parseMvhd(ParsableByteArray mvhd) {
    mvhd.setPosition(Atom.HEADER_SIZE);

    int fullAtom = mvhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    mvhd.skipBytes(version == 0 ? 8 : 16);

    return mvhd.readUnsignedInt();
  }

  /**
   * Parses a tkhd atom (defined in 14496-12).
   *
   * @return A {@link Pair} consisting of the track id and duration (in the timescale indicated in
   *     the movie header box). The duration is set to -1 if the duration is unspecified.
   */
  private static Pair<Integer, Long> parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    tkhd.skipBytes(version == 0 ? 8 : 16);

    int trackId = tkhd.readInt();
    tkhd.skipBytes(4);

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
      tkhd.skipBytes(durationByteCount);
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
    hdlr.setPosition(Atom.FULL_HEADER_SIZE + 4);
    return hdlr.readInt();
  }

  /**
   * Parses an mdhd atom (defined in 14496-12).
   *
   * @param mdhd The mdhd atom to parse.
   * @return The media timescale, defined as the number of time units that pass in one second.
   */
  private static long parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    mdhd.skipBytes(version == 0 ? 8 : 16);
    return mdhd.readUnsignedInt();
  }

  private static StsdDataHolder parseStsd(ParsableByteArray stsd, long durationUs) {
    stsd.setPosition(Atom.FULL_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    StsdDataHolder holder = new StsdDataHolder(numberOfEntries);
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = stsd.readInt();
      if (childAtomType == Atom.TYPE_avc1 || childAtomType == Atom.TYPE_avc3
          || childAtomType == Atom.TYPE_encv) {
        parseAvcFromParent(stsd, childStartPosition, childAtomSize, durationUs, holder, i);
      } else if (childAtomType == Atom.TYPE_mp4a || childAtomType == Atom.TYPE_enca
          || childAtomType == Atom.TYPE_ac_3) {
        parseAudioSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, durationUs,
            holder, i);
      } else if (childAtomType == Atom.TYPE_TTML) {
        holder.mediaFormat = MediaFormat.createTtmlFormat();
      } else if (childAtomType == Atom.TYPE_mp4v) {
        holder.mediaFormat = parseMp4vFromParent(stsd, childStartPosition, childAtomSize,
            durationUs);
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return holder;
  }

  /** Returns the media format for an avc1 box. */
  private static void parseAvcFromParent(ParsableByteArray parent, int position, int size,
      long durationUs, StsdDataHolder out, int entryIndex) {
    parent.setPosition(position + Atom.HEADER_SIZE);

    parent.skipBytes(24);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    float pixelWidthHeightRatio = 1;
    parent.skipBytes(50);

    List<byte[]> initializationData = null;
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
        Pair<List<byte[]>, Integer> avcCData = parseAvcCFromParent(parent, childStartPosition);
        initializationData = avcCData.first;
        out.nalUnitLengthFieldLength = avcCData.second;
      } else if (childAtomType == Atom.TYPE_sinf) {
        out.trackEncryptionBoxes[entryIndex] =
            parseSinfFromParent(parent, childStartPosition, childAtomSize);
      } else if (childAtomType == Atom.TYPE_pasp) {
        pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
      }
      childPosition += childAtomSize;
    }

    out.mediaFormat = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        durationUs, width, height, pixelWidthHeightRatio, initializationData);
  }

  private static Pair<List<byte[]>, Integer> parseAvcCFromParent(ParsableByteArray parent,
      int position) {
    parent.setPosition(position + Atom.HEADER_SIZE + 4);
    // Start of the AVCDecoderConfigurationRecord (defined in 14496-15)
    int nalUnitLengthFieldLength = (parent.readUnsignedByte() & 0x3) + 1;
    if (nalUnitLengthFieldLength == 3) {
      throw new IllegalStateException();
    }
    List<byte[]> initializationData = new ArrayList<byte[]>();
    // TODO: We should try and parse these using CodecSpecificDataUtil.parseSpsNalUnit, and
    // expose the AVC profile and level somewhere useful; Most likely in MediaFormat.
    int numSequenceParameterSets = parent.readUnsignedByte() & 0x1F;
    for (int j = 0; j < numSequenceParameterSets; j++) {
      initializationData.add(H264Util.parseChildNalUnit(parent));
    }
    int numPictureParameterSets = parent.readUnsignedByte();
    for (int j = 0; j < numPictureParameterSets; j++) {
      initializationData.add(H264Util.parseChildNalUnit(parent));
    }
    return Pair.create(initializationData, nalUnitLengthFieldLength);
  }

  private static TrackEncryptionBox parseSinfFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + Atom.HEADER_SIZE;

    TrackEncryptionBox trackEncryptionBox = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_frma) {
        parent.readInt(); // dataFormat.
      } else if (childAtomType == Atom.TYPE_schm) {
        parent.skipBytes(4);
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
    parent.setPosition(position + Atom.HEADER_SIZE);
    int hSpacing = parent.readUnsignedIntToInt();
    int vSpacing = parent.readUnsignedIntToInt();
    return (float) hSpacing / vSpacing;
  }

  private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + Atom.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_tenc) {
        parent.skipBytes(4);
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
  private static MediaFormat parseMp4vFromParent(ParsableByteArray parent, int position, int size,
      long durationUs) {
    parent.setPosition(position + Atom.HEADER_SIZE);

    parent.skipBytes(24);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    parent.skipBytes(50);

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
        MimeTypes.VIDEO_MP4V, MediaFormat.NO_VALUE, durationUs, width, height, initializationData);
  }

  private static void parseAudioSampleEntry(ParsableByteArray parent, int atomType, int position,
      int size, long durationUs, StsdDataHolder out, int entryIndex) {
    parent.setPosition(position + Atom.HEADER_SIZE);
    parent.skipBytes(16);
    int channelCount = parent.readUnsignedShort();
    int sampleSize = parent.readUnsignedShort();
    parent.skipBytes(4);
    int sampleRate = parent.readUnsignedFixedPoint1616();

    byte[] initializationData = null;
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
          out.trackEncryptionBoxes[entryIndex] = parseSinfFromParent(parent, childStartPosition,
              childAtomSize);
        }
      } else if (atomType == Atom.TYPE_ac_3 && childAtomType == Atom.TYPE_dac3) {
        // TODO: Choose the right AC-3 track based on the contents of dac3/dec3.
        // TODO: Add support for encryption (by setting out.trackEncryptionBoxes).
        parent.setPosition(Atom.HEADER_SIZE + childStartPosition);
        out.mediaFormat = Ac3Util.parseAnnexFAc3Format(parent);
        return;
      } else if  (atomType == Atom.TYPE_ec_3 && childAtomType == Atom.TYPE_dec3) {
        parent.setPosition(Atom.HEADER_SIZE + childStartPosition);
        out.mediaFormat = Ac3Util.parseAnnexFEAc3Format(parent);
        return;
      }
      childPosition += childAtomSize;
    }

    // Set the MIME type for ac-3/ec-3 atoms even if the dac3/dec3 child atom is missing.
    String mimeType;
    if (atomType == Atom.TYPE_ac_3) {
      mimeType = MimeTypes.AUDIO_AC3;
    } else if (atomType == Atom.TYPE_ec_3) {
      mimeType = MimeTypes.AUDIO_EC3;
    } else {
      mimeType = MimeTypes.AUDIO_AAC;
    }

    out.mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleSize, durationUs, channelCount,
        sampleRate,
        initializationData == null ? null : Collections.singletonList(initializationData));
  }

  /** Returns codec-specific initialization data contained in an esds box. */
  private static byte[] parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Atom.HEADER_SIZE + 4);
    // Start of the ES_Descriptor (defined in 14496-1)
    parent.skipBytes(1); // ES_Descriptor tag
    int varIntByte = parent.readUnsignedByte();
    while (varIntByte > 127) {
      varIntByte = parent.readUnsignedByte();
    }
    parent.skipBytes(2); // ES_ID

    int flags = parent.readUnsignedByte();
    if ((flags & 0x80 /* streamDependenceFlag */) != 0) {
      parent.skipBytes(2);
    }
    if ((flags & 0x40 /* URL_Flag */) != 0) {
      parent.skipBytes(parent.readUnsignedShort());
    }
    if ((flags & 0x20 /* OCRstreamFlag */) != 0) {
      parent.skipBytes(2);
    }

    // Start of the DecoderConfigDescriptor (defined in 14496-1)
    parent.skipBytes(1); // DecoderConfigDescriptor tag
    varIntByte = parent.readUnsignedByte();
    while (varIntByte > 127) {
      varIntByte = parent.readUnsignedByte();
    }
    parent.skipBytes(13);

    // Start of AudioSpecificConfig (defined in 14496-3)
    parent.skipBytes(1); // AudioSpecificConfig tag
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

  private AtomParsers() {
    // Prevent instantiation.
  }

  /**
   * Holds data parsed from an stsd atom and its children.
   */
  private static final class StsdDataHolder {

    public final TrackEncryptionBox[] trackEncryptionBoxes;

    public MediaFormat mediaFormat;
    public int nalUnitLengthFieldLength;

    public StsdDataHolder(int numberOfEntries) {
      trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
      nalUnitLengthFieldLength = -1;
    }

  }

}
