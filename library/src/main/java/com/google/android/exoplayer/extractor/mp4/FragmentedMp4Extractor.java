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
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.extractor.mp4.Atom.ContainerAtom;
import com.google.android.exoplayer.extractor.mp4.Atom.LeafAtom;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.NalUnitUtil;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

/**
 * Facilitates the extraction of data from the fragmented mp4 container format.
 * <p>
 * This implementation only supports de-muxed (i.e. single track) streams.
 */
public final class FragmentedMp4Extractor implements Extractor {

  /**
   * Flag to work around an issue in some video streams where every frame is marked as a sync frame.
   * The workaround overrides the sync frame flags in the stream, forcing them to false except for
   * the first sample in each segment.
   * <p>
   * This flag does nothing if the stream is not a video stream.
   */
  public static final int WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME = 1;

  /**
   * Flag to ignore any tfdt boxes in the stream.
   */
  public static final int WORKAROUND_IGNORE_TFDT_BOX = 2;

  private static final byte[] PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE =
      new byte[] {-94, 57, 79, 82, 90, -101, 79, 20, -94, 68, 108, 66, 124, 100, -115, -12};

  // Parser states
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_ENCRYPTION_DATA = 2;
  private static final int STATE_READING_SAMPLE_START = 3;
  private static final int STATE_READING_SAMPLE_CONTINUE = 4;

  private final int workaroundFlags;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private final ParsableByteArray encryptionSignalByte;

  // Parser state
  private final ParsableByteArray atomHeader;
  private final byte[] extendedTypeScratch;
  private final Stack<ContainerAtom> containerAtoms;
  private final TrackFragment fragmentRun;

  private int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  private ParsableByteArray atomData;
  private long endOfMdatPosition;

  private int sampleIndex;
  private int sampleSize;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;

  // Data parsed from moov atom.
  private Track track;
  private DefaultSampleValues extendsDefaults;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  // Whether extractorOutput.seekMap has been invoked.
  private boolean haveOutputSeekMap;

  public FragmentedMp4Extractor() {
    this(0);
  }

  /**
   * @param workaroundFlags Flags to allow parsing of faulty streams.
   *     {@link #WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME} is currently the only flag defined.
   */
  public FragmentedMp4Extractor(int workaroundFlags) {
    this.workaroundFlags = workaroundFlags;
    atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
    encryptionSignalByte = new ParsableByteArray(1);
    extendedTypeScratch = new byte[16];
    containerAtoms = new Stack<>();
    fragmentRun = new TrackFragment();
    enterReadingAtomHeaderState();
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return Sniffer.sniffFragmented(input);
  }

  /**
   * Sideloads track information into the extractor.
   * <p>
   * Should be called before {@link #read(ExtractorInput, PositionHolder)} in the case that the
   * extractor will not receive a moov atom in the input data, from which track information would
   * normally be parsed.
   *
   * @param track The track to sideload.
   */
  public void setTrack(Track track) {
    this.extendsDefaults = new DefaultSampleValues(0, 0, 0, 0);
    this.track = track;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0);
    extractorOutput.endTracks();
  }

  @Override
  public void seek() {
    containerAtoms.clear();
    enterReadingAtomHeaderState();
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    while (true) {
      switch (parserState) {
        case STATE_READING_ATOM_HEADER:
          if (!readAtomHeader(input)) {
            return Extractor.RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_ATOM_PAYLOAD:
          readAtomPayload(input);
          break;
        case STATE_READING_ENCRYPTION_DATA:
          readEncryptionData(input);
          break;
        default:
          if (readSample(input)) {
            return RESULT_CONTINUE;
          }
      }
    }
  }

  private void enterReadingAtomHeaderState() {
    parserState = STATE_READING_ATOM_HEADER;
    atomHeaderBytesRead = 0;
  }

  private boolean readAtomHeader(ExtractorInput input) throws IOException, InterruptedException {
    if (atomHeaderBytesRead == 0) {
      // Read the standard length atom header.
      if (!input.readFully(atomHeader.data, 0, Atom.HEADER_SIZE, true)) {
        return false;
      }
      atomHeaderBytesRead = Atom.HEADER_SIZE;
      atomHeader.setPosition(0);
      atomSize = atomHeader.readUnsignedInt();
      atomType = atomHeader.readInt();
    }

    if (atomSize == Atom.LONG_SIZE_PREFIX) {
      // Read the extended atom size.
      int headerBytesRemaining = Atom.LONG_HEADER_SIZE - Atom.HEADER_SIZE;
      input.readFully(atomHeader.data, Atom.HEADER_SIZE, headerBytesRemaining);
      atomHeaderBytesRead += headerBytesRemaining;
      atomSize = atomHeader.readUnsignedLongToLong();
    }

    long atomPosition = input.getPosition() - atomHeaderBytesRead;
    if (atomType == Atom.TYPE_moof) {
      // The data positions may be updated when parsing the tfhd/trun.
      fragmentRun.auxiliaryDataPosition = atomPosition;
      fragmentRun.dataPosition = atomPosition;
    }

    if (atomType == Atom.TYPE_mdat) {
      endOfMdatPosition = atomPosition + atomSize;
      if (!haveOutputSeekMap) {
        extractorOutput.seekMap(SeekMap.UNSEEKABLE);
        haveOutputSeekMap = true;
      }
      if (fragmentRun.sampleEncryptionDataNeedsFill) {
        parserState = STATE_READING_ENCRYPTION_DATA;
      } else {
        parserState = STATE_READING_SAMPLE_START;
      }
      return true;
    }

    if (shouldParseAtom(atomType)) {
      if (shouldParseContainerAtom(atomType)) {
        long endPosition = input.getPosition() + atomSize - Atom.HEADER_SIZE;
        containerAtoms.add(new ContainerAtom(atomType, endPosition));
        enterReadingAtomHeaderState();
      } else {
        // We don't support parsing of leaf atoms that define extended atom sizes, or that have
        // lengths greater than Integer.MAX_VALUE.
        Assertions.checkState(atomHeaderBytesRead == Atom.HEADER_SIZE);
        Assertions.checkState(atomSize <= Integer.MAX_VALUE);
        atomData = new ParsableByteArray((int) atomSize);
        System.arraycopy(atomHeader.data, 0, atomData.data, 0, Atom.HEADER_SIZE);
        parserState = STATE_READING_ATOM_PAYLOAD;
      }
    } else {
      // We don't support skipping of atoms that have lengths greater than Integer.MAX_VALUE.
      Assertions.checkState(atomSize <= Integer.MAX_VALUE);
      atomData = null;
      parserState = STATE_READING_ATOM_PAYLOAD;
    }

    return true;
  }

  private void readAtomPayload(ExtractorInput input) throws IOException, InterruptedException {
    int atomPayloadSize = (int) atomSize - atomHeaderBytesRead;
    if (atomData != null) {
      input.readFully(atomData.data, Atom.HEADER_SIZE, atomPayloadSize);
      onLeafAtomRead(new LeafAtom(atomType, atomData), input.getPosition());
    } else {
      input.skipFully(atomPayloadSize);
    }
    long currentPosition = input.getPosition();
    while (!containerAtoms.isEmpty() && containerAtoms.peek().endPosition == currentPosition) {
      onContainerAtomRead(containerAtoms.pop());
    }
    enterReadingAtomHeaderState();
  }

  private void onLeafAtomRead(LeafAtom leaf, long inputPosition) {
    if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(leaf);
    } else if (leaf.type == Atom.TYPE_sidx) {
      ChunkIndex segmentIndex = parseSidx(leaf.data, inputPosition);
      extractorOutput.seekMap(segmentIndex);
      haveOutputSeekMap = true;
    }
  }

  private void onContainerAtomRead(ContainerAtom container) {
    if (container.type == Atom.TYPE_moov) {
      onMoovContainerAtomRead(container);
    } else if (container.type == Atom.TYPE_moof) {
      onMoofContainerAtomRead(container);
    } else if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(container);
    }
  }

  private void onMoovContainerAtomRead(ContainerAtom moov) {
    List<Atom.LeafAtom> moovChildren = moov.leafChildren;
    int moovChildrenSize = moovChildren.size();

    DrmInitData.Mapped drmInitData = null;
    for (int i = 0; i < moovChildrenSize; i++) {
      LeafAtom child = moovChildren.get(i);
      if (child.type == Atom.TYPE_pssh) {
        if (drmInitData == null) {
          drmInitData = new DrmInitData.Mapped(MimeTypes.VIDEO_MP4);
        }
        byte[] psshData = child.data.data;
        drmInitData.put(PsshAtomUtil.parseUuid(psshData), psshData);
      }
    }
    if (drmInitData != null) {
      extractorOutput.drmInitData(drmInitData);
    }

    ContainerAtom mvex = moov.getContainerAtomOfType(Atom.TYPE_mvex);
    extendsDefaults = parseTrex(mvex.getLeafAtomOfType(Atom.TYPE_trex).data);
    track = AtomParsers.parseTrak(moov.getContainerAtomOfType(Atom.TYPE_trak),
        moov.getLeafAtomOfType(Atom.TYPE_mvhd));
    Assertions.checkState(track != null);
    trackOutput.format(track.mediaFormat);
  }

  private void onMoofContainerAtomRead(ContainerAtom moof) {
    fragmentRun.reset();
    parseMoof(track, extendsDefaults, moof, fragmentRun, workaroundFlags, extendedTypeScratch);
    sampleIndex = 0;
  }

  /**
   * Parses a trex atom (defined in 14496-12).
   */
  private static DefaultSampleValues parseTrex(ParsableByteArray trex) {
    trex.setPosition(Atom.FULL_HEADER_SIZE + 4);
    int defaultSampleDescriptionIndex = trex.readUnsignedIntToInt() - 1;
    int defaultSampleDuration = trex.readUnsignedIntToInt();
    int defaultSampleSize = trex.readUnsignedIntToInt();
    int defaultSampleFlags = trex.readInt();
    return new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration,
        defaultSampleSize, defaultSampleFlags);
  }

  private static void parseMoof(Track track, DefaultSampleValues extendsDefaults,
      ContainerAtom moof, TrackFragment out, int workaroundFlags, byte[] extendedTypeScratch) {
    // This extractor only supports one traf per moof.
    Assertions.checkArgument(1 == moof.getChildAtomOfTypeCount(Atom.TYPE_traf));
    parseTraf(track, extendsDefaults, moof.getContainerAtomOfType(Atom.TYPE_traf),
        out, workaroundFlags, extendedTypeScratch);
  }

  /**
   * Parses a traf atom (defined in 14496-12).
   */
  private static void parseTraf(Track track, DefaultSampleValues extendsDefaults,
      ContainerAtom traf, TrackFragment out, int workaroundFlags, byte[] extendedTypeScratch) {
    // This extractor only supports one trun per traf.
    Assertions.checkArgument(1 == traf.getChildAtomOfTypeCount(Atom.TYPE_trun));
    LeafAtom tfdtAtom = traf.getLeafAtomOfType(Atom.TYPE_tfdt);
    long decodeTime;
    if (tfdtAtom == null || (workaroundFlags & WORKAROUND_IGNORE_TFDT_BOX) != 0) {
      decodeTime = 0;
    } else {
      decodeTime = parseTfdt(traf.getLeafAtomOfType(Atom.TYPE_tfdt).data);
    }

    LeafAtom tfhd = traf.getLeafAtomOfType(Atom.TYPE_tfhd);
    parseTfhd(extendsDefaults, tfhd.data, out);

    LeafAtom trun = traf.getLeafAtomOfType(Atom.TYPE_trun);
    parseTrun(track, out.header, decodeTime, workaroundFlags, trun.data, out);

    LeafAtom saiz = traf.getLeafAtomOfType(Atom.TYPE_saiz);
    if (saiz != null) {
      TrackEncryptionBox trackEncryptionBox =
          track.sampleDescriptionEncryptionBoxes[out.header.sampleDescriptionIndex];
      parseSaiz(trackEncryptionBox, saiz.data, out);
    }

    LeafAtom saio = traf.getLeafAtomOfType(Atom.TYPE_saio);
    if (saio != null) {
      parseSaio(saio.data, out);
    }

    LeafAtom senc = traf.getLeafAtomOfType(Atom.TYPE_senc);
    if (senc != null) {
      parseSenc(senc.data, out);
    }

    int childrenSize = traf.leafChildren.size();
    for (int i = 0; i < childrenSize; i++) {
      LeafAtom atom = traf.leafChildren.get(i);
      if (atom.type == Atom.TYPE_uuid) {
        parseUuid(atom.data, out, extendedTypeScratch);
      }
    }
  }

  private static void parseSaiz(TrackEncryptionBox encryptionBox, ParsableByteArray saiz,
      TrackFragment out) {
    int vectorSize = encryptionBox.initializationVectorSize;
    saiz.setPosition(Atom.HEADER_SIZE);
    int fullAtom = saiz.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saiz.skipBytes(8);
    }
    int defaultSampleInfoSize = saiz.readUnsignedByte();

    int sampleCount = saiz.readUnsignedIntToInt();
    if (sampleCount != out.length) {
      throw new IllegalStateException("Length mismatch: " + sampleCount + ", " + out.length);
    }

    int totalSize = 0;
    if (defaultSampleInfoSize == 0) {
      boolean[] sampleHasSubsampleEncryptionTable = out.sampleHasSubsampleEncryptionTable;
      for (int i = 0; i < sampleCount; i++) {
        int sampleInfoSize = saiz.readUnsignedByte();
        totalSize += sampleInfoSize;
        sampleHasSubsampleEncryptionTable[i] = sampleInfoSize > vectorSize;
      }
    } else {
      boolean subsampleEncryption = defaultSampleInfoSize > vectorSize;
      totalSize += defaultSampleInfoSize * sampleCount;
      Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
    }
    out.initEncryptionData(totalSize);
  }

  /**
   * Parses a saio atom (defined in 14496-12).
   *
   * @param saio The saio atom to parse.
   * @param out The track fragment to populate with data from the saio atom.
   */
  private static void parseSaio(ParsableByteArray saio, TrackFragment out) {
    saio.setPosition(Atom.HEADER_SIZE);
    int fullAtom = saio.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saio.skipBytes(8);
    }

    int entryCount = saio.readUnsignedIntToInt();
    if (entryCount != 1) {
      // We only support one trun element currently, so always expect one entry.
      throw new IllegalStateException("Unexpected saio entry count: " + entryCount);
    }

    int version = Atom.parseFullAtomVersion(fullAtom);
    out.auxiliaryDataPosition +=
        version == 0 ? saio.readUnsignedInt() : saio.readUnsignedLongToLong();
  }

  /**
   * Parses a tfhd atom (defined in 14496-12).
   *
   * @param extendsDefaults Default sample values from the trex atom.
   * @param tfhd The tfhd atom to parse.
   * @param out The track fragment to populate with data from the tfhd atom.
   */
  private static void parseTfhd(DefaultSampleValues extendsDefaults, ParsableByteArray tfhd,
      TrackFragment out) {
    tfhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tfhd.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);

    tfhd.skipBytes(4); // trackId
    if ((flags & 0x01 /* base_data_offset_present */) != 0) {
      long baseDataPosition = tfhd.readUnsignedLongToLong();
      out.dataPosition = baseDataPosition;
      out.auxiliaryDataPosition = baseDataPosition;
    }

    int defaultSampleDescriptionIndex =
        ((flags & 0x02 /* default_sample_description_index_present */) != 0)
        ? tfhd.readUnsignedIntToInt() - 1 : extendsDefaults.sampleDescriptionIndex;
    int defaultSampleDuration = ((flags & 0x08 /* default_sample_duration_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : extendsDefaults.duration;
    int defaultSampleSize = ((flags & 0x10 /* default_sample_size_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : extendsDefaults.size;
    int defaultSampleFlags = ((flags & 0x20 /* default_sample_flags_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : extendsDefaults.flags;
    out.header = new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration,
        defaultSampleSize, defaultSampleFlags);
  }

  /**
   * Parses a tfdt atom (defined in 14496-12).
   *
   * @return baseMediaDecodeTime The sum of the decode durations of all earlier samples in the
   *     media, expressed in the media's timescale.
   */
  private static long parseTfdt(ParsableByteArray tfdt) {
    tfdt.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tfdt.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    return version == 1 ? tfdt.readUnsignedLongToLong() : tfdt.readUnsignedInt();
  }

  /**
   * Parses a trun atom (defined in 14496-12).
   *
   * @param track The corresponding track.
   * @param defaultSampleValues Default sample values.
   * @param decodeTime The decode time.
   * @param trun The trun atom to parse.
   * @param out The {@TrackFragment} into which parsed data should be placed.
   */
  private static void parseTrun(Track track, DefaultSampleValues defaultSampleValues,
      long decodeTime, int workaroundFlags, ParsableByteArray trun, TrackFragment out) {
    trun.setPosition(Atom.HEADER_SIZE);
    int fullAtom = trun.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);

    int sampleCount = trun.readUnsignedIntToInt();
    if ((flags & 0x01 /* data_offset_present */) != 0) {
      out.dataPosition += trun.readInt();
    }

    boolean firstSampleFlagsPresent = (flags & 0x04 /* first_sample_flags_present */) != 0;
    int firstSampleFlags = defaultSampleValues.flags;
    if (firstSampleFlagsPresent) {
      firstSampleFlags = trun.readUnsignedIntToInt();
    }

    boolean sampleDurationsPresent = (flags & 0x100 /* sample_duration_present */) != 0;
    boolean sampleSizesPresent = (flags & 0x200 /* sample_size_present */) != 0;
    boolean sampleFlagsPresent = (flags & 0x400 /* sample_flags_present */) != 0;
    boolean sampleCompositionTimeOffsetsPresent =
        (flags & 0x800 /* sample_composition_time_offsets_present */) != 0;

    out.initTables(sampleCount);
    int[] sampleSizeTable = out.sampleSizeTable;
    int[] sampleCompositionTimeOffsetTable = out.sampleCompositionTimeOffsetTable;
    long[] sampleDecodingTimeTable = out.sampleDecodingTimeTable;
    boolean[] sampleIsSyncFrameTable = out.sampleIsSyncFrameTable;

    long timescale = track.timescale;
    long cumulativeTime = decodeTime;
    boolean workaroundEveryVideoFrameIsSyncFrame = track.type == Track.TYPE_vide
        && (workaroundFlags & WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME) != 0;
    for (int i = 0; i < sampleCount; i++) {
      // Use trun values if present, otherwise tfhd, otherwise trex.
      int sampleDuration = sampleDurationsPresent ? trun.readUnsignedIntToInt()
          : defaultSampleValues.duration;
      int sampleSize = sampleSizesPresent ? trun.readUnsignedIntToInt() : defaultSampleValues.size;
      int sampleFlags = (i == 0 && firstSampleFlagsPresent) ? firstSampleFlags
          : sampleFlagsPresent ? trun.readInt() : defaultSampleValues.flags;
      if (sampleCompositionTimeOffsetsPresent) {
        // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers in
        // version 0 trun boxes, however a significant number of streams violate the spec and use
        // signed integers instead. It's safe to always parse sample offsets as signed integers
        // here, because unsigned integers will still be parsed correctly (unless their top bit is
        // set, which is never true in practice because sample offsets are always small).
        int sampleOffset = trun.readInt();
        sampleCompositionTimeOffsetTable[i] = (int) ((sampleOffset * 1000) / timescale);
      } else {
        sampleCompositionTimeOffsetTable[i] = 0;
      }
      sampleDecodingTimeTable[i] = Util.scaleLargeTimestamp(cumulativeTime, 1000, timescale);
      sampleSizeTable[i] = sampleSize;
      sampleIsSyncFrameTable[i] = ((sampleFlags >> 16) & 0x1) == 0
          && (!workaroundEveryVideoFrameIsSyncFrame || i == 0);
      cumulativeTime += sampleDuration;
    }
  }

  private static void parseUuid(ParsableByteArray uuid, TrackFragment out,
      byte[] extendedTypeScratch) {
    uuid.setPosition(Atom.HEADER_SIZE);
    uuid.readBytes(extendedTypeScratch, 0, 16);

    // Currently this parser only supports Microsoft's PIFF SampleEncryptionBox.
    if (!Arrays.equals(extendedTypeScratch, PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE)) {
      return;
    }

    // Except for the extended type, this box is identical to a SENC box. See "Portable encoding of
    // audio-video objects: The Protected Interoperable File Format (PIFF), John A. Bocharov et al,
    // Section 5.3.2.1."
    parseSenc(uuid, 16, out);
  }

  private static void parseSenc(ParsableByteArray senc, TrackFragment out) {
    parseSenc(senc, 0, out);
  }

  private static void parseSenc(ParsableByteArray senc, int offset, TrackFragment out) {
    senc.setPosition(Atom.HEADER_SIZE + offset);
    int fullAtom = senc.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);

    if ((flags & 0x01 /* override_track_encryption_box_parameters */) != 0) {
      // TODO: Implement this.
      throw new IllegalStateException("Overriding TrackEncryptionBox parameters is unsupported");
    }

    boolean subsampleEncryption = (flags & 0x02 /* use_subsample_encryption */) != 0;
    int sampleCount = senc.readUnsignedIntToInt();
    if (sampleCount != out.length) {
      throw new IllegalStateException("Length mismatch: " + sampleCount + ", " + out.length);
    }

    Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
    out.initEncryptionData(senc.bytesLeft());
    out.fillEncryptionData(senc);
  }

  /**
   * Parses a sidx atom (defined in 14496-12).
   */
  private static ChunkIndex parseSidx(ParsableByteArray atom, long inputPosition) {
    atom.setPosition(Atom.HEADER_SIZE);
    int fullAtom = atom.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    atom.skipBytes(4);
    long timescale = atom.readUnsignedInt();
    long earliestPresentationTime;
    long offset = inputPosition;
    if (version == 0) {
      earliestPresentationTime = atom.readUnsignedInt();
      offset += atom.readUnsignedInt();
    } else {
      earliestPresentationTime = atom.readUnsignedLongToLong();
      offset += atom.readUnsignedLongToLong();
    }

    atom.skipBytes(2);

    int referenceCount = atom.readUnsignedShort();
    int[] sizes = new int[referenceCount];
    long[] offsets = new long[referenceCount];
    long[] durationsUs = new long[referenceCount];
    long[] timesUs = new long[referenceCount];

    long time = earliestPresentationTime;
    long timeUs = Util.scaleLargeTimestamp(time, C.MICROS_PER_SECOND, timescale);
    for (int i = 0; i < referenceCount; i++) {
      int firstInt = atom.readInt();

      int type = 0x80000000 & firstInt;
      if (type != 0) {
        throw new IllegalStateException("Unhandled indirect reference");
      }
      long referenceDuration = atom.readUnsignedInt();

      sizes[i] = 0x7fffffff & firstInt;
      offsets[i] = offset;

      // Calculate time and duration values such that any rounding errors are consistent. i.e. That
      // timesUs[i] + durationsUs[i] == timesUs[i + 1].
      timesUs[i] = timeUs;
      time += referenceDuration;
      timeUs = Util.scaleLargeTimestamp(time, C.MICROS_PER_SECOND, timescale);
      durationsUs[i] = timeUs - timesUs[i];

      atom.skipBytes(4);
      offset += sizes[i];
    }

    return new ChunkIndex(sizes, offsets, durationsUs, timesUs);
  }

  private void readEncryptionData(ExtractorInput input) throws IOException, InterruptedException {
    int bytesToSkip = (int) (fragmentRun.auxiliaryDataPosition - input.getPosition());
    Assertions.checkState(bytesToSkip >= 0, "Offset to encryption data was negative.");
    input.skipFully(bytesToSkip);
    fragmentRun.fillEncryptionData(input);
    parserState = STATE_READING_SAMPLE_START;
  }

  /**
   * Attempts to extract the next sample in the current mdat atom.
   * <p>
   * If there are no more samples in the current mdat atom then the parser state is transitioned
   * to {@link #STATE_READING_ATOM_HEADER} and {@code false} is returned.
   * <p>
   * It is possible for a sample to be extracted in part in the case that an exception is thrown. In
   * this case the method can be called again to extract the remainder of the sample.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @return True if a sample was extracted. False otherwise.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private boolean readSample(ExtractorInput input) throws IOException, InterruptedException {
    if (sampleIndex == 0) {
      int bytesToSkip = (int) (fragmentRun.dataPosition - input.getPosition());
      Assertions.checkState(bytesToSkip >= 0, "Offset to sample data was negative.");
      input.skipFully(bytesToSkip);
    }

    if (sampleIndex >= fragmentRun.length) {
      int bytesToSkip = (int) (endOfMdatPosition - input.getPosition());
      Assertions.checkState(bytesToSkip >= 0, "Offset to end of mdat was negative.");
      input.skipFully(bytesToSkip);
      // We've run out of samples in the current mdat atom.
      enterReadingAtomHeaderState();
      return false;
    }

    if (parserState == STATE_READING_SAMPLE_START) {
      sampleSize = fragmentRun.sampleSizeTable[sampleIndex];
      if (fragmentRun.definesEncryptionData) {
        sampleBytesWritten = appendSampleEncryptionData(fragmentRun.sampleEncryptionData);
        sampleSize += sampleBytesWritten;
      } else {
        sampleBytesWritten = 0;
      }
      sampleCurrentNalBytesRemaining = 0;
      parserState = STATE_READING_SAMPLE_CONTINUE;
    }

    if (track.nalUnitLengthFieldLength != -1) {
      // Zero the top three bytes of the array that we'll use to parse nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.data;
      nalLengthData[0] = 0;
      nalLengthData[1] = 0;
      nalLengthData[2] = 0;
      int nalUnitLengthFieldLength = track.nalUnitLengthFieldLength;
      int nalUnitLengthFieldLengthDiff = 4 - track.nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      while (sampleBytesWritten < sampleSize) {
        if (sampleCurrentNalBytesRemaining == 0) {
          // Read the NAL length so that we know where we find the next one.
          input.readFully(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
          nalLength.setPosition(0);
          sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          trackOutput.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
          sampleSize += nalUnitLengthFieldLengthDiff;
        } else {
          // Write the payload of the NAL unit.
          int writtenBytes = trackOutput.sampleData(input, sampleCurrentNalBytesRemaining, false);
          sampleBytesWritten += writtenBytes;
          sampleCurrentNalBytesRemaining -= writtenBytes;
        }
      }
    } else {
      while (sampleBytesWritten < sampleSize) {
        int writtenBytes = trackOutput.sampleData(input, sampleSize - sampleBytesWritten, false);
        sampleBytesWritten += writtenBytes;
      }
    }

    long sampleTimeUs = fragmentRun.getSamplePresentationTime(sampleIndex) * 1000L;
    int sampleFlags = (fragmentRun.definesEncryptionData ? C.SAMPLE_FLAG_ENCRYPTED : 0)
        | (fragmentRun.sampleIsSyncFrameTable[sampleIndex] ? C.SAMPLE_FLAG_SYNC : 0);
    int sampleDescriptionIndex = fragmentRun.header.sampleDescriptionIndex;
    byte[] encryptionKey = fragmentRun.definesEncryptionData
        ? track.sampleDescriptionEncryptionBoxes[sampleDescriptionIndex].keyId : null;
    trackOutput.sampleMetadata(sampleTimeUs, sampleFlags, sampleSize, 0, encryptionKey);

    sampleIndex++;
    parserState = STATE_READING_SAMPLE_START;
    return true;
  }

  private int appendSampleEncryptionData(ParsableByteArray sampleEncryptionData) {
    int sampleDescriptionIndex = fragmentRun.header.sampleDescriptionIndex;
    TrackEncryptionBox encryptionBox =
        track.sampleDescriptionEncryptionBoxes[sampleDescriptionIndex];
    int vectorSize = encryptionBox.initializationVectorSize;
    boolean subsampleEncryption = fragmentRun.sampleHasSubsampleEncryptionTable[sampleIndex];

    // Write the signal byte, containing the vector size and the subsample encryption flag.
    encryptionSignalByte.data[0] = (byte) (vectorSize | (subsampleEncryption ? 0x80 : 0));
    encryptionSignalByte.setPosition(0);
    trackOutput.sampleData(encryptionSignalByte, 1);
    // Write the vector.
    trackOutput.sampleData(sampleEncryptionData, vectorSize);
    // If we don't have subsample encryption data, we're done.
    if (!subsampleEncryption) {
      return 1 + vectorSize;
    }
    // Write the subsample encryption data.
    int subsampleCount = sampleEncryptionData.readUnsignedShort();
    sampleEncryptionData.skipBytes(-2);
    int subsampleDataLength = 2 + 6 * subsampleCount;
    trackOutput.sampleData(sampleEncryptionData, subsampleDataLength);
    return 1 + vectorSize + subsampleDataLength;
  }

  /** Returns whether the extractor should parse an atom with type {@code atom}. */
  private static boolean shouldParseAtom(int atom) {
    return atom == Atom.TYPE_avc1 || atom == Atom.TYPE_avc3 || atom == Atom.TYPE_esds
        || atom == Atom.TYPE_hdlr || atom == Atom.TYPE_mdat || atom == Atom.TYPE_mdhd
        || atom == Atom.TYPE_moof || atom == Atom.TYPE_moov || atom == Atom.TYPE_mp4a
        || atom == Atom.TYPE_mvhd || atom == Atom.TYPE_sidx || atom == Atom.TYPE_stsd
        || atom == Atom.TYPE_tfdt || atom == Atom.TYPE_tfhd || atom == Atom.TYPE_tkhd
        || atom == Atom.TYPE_traf || atom == Atom.TYPE_trak || atom == Atom.TYPE_trex
        || atom == Atom.TYPE_trun || atom == Atom.TYPE_mvex || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_pssh
        || atom == Atom.TYPE_saiz || atom == Atom.TYPE_saio || atom == Atom.TYPE_uuid
        || atom == Atom.TYPE_senc || atom == Atom.TYPE_pasp || atom == Atom.TYPE_s263;
  }

  /** Returns whether the extractor should parse a container atom with type {@code atom}. */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_avcC
        || atom == Atom.TYPE_moof || atom == Atom.TYPE_traf || atom == Atom.TYPE_mvex;
  }

}
