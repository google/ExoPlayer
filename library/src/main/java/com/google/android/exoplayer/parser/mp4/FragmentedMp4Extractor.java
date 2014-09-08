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
package com.google.android.exoplayer.parser.mp4;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.Extractor;
import com.google.android.exoplayer.parser.SegmentIndex;
import com.google.android.exoplayer.parser.mp4.Atom.ContainerAtom;
import com.google.android.exoplayer.parser.mp4.Atom.LeafAtom;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

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

  private static final int READ_TERMINATING_RESULTS = RESULT_NEED_MORE_DATA | RESULT_END_OF_STREAM
      | RESULT_READ_SAMPLE | RESULT_NEED_SAMPLE_HOLDER;
  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};
  private static final byte[] PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE =
      new byte[] {-94, 57, 79, 82, 90, -101, 79, 20, -94, 68, 108, 66, 124, 100, -115, -12};

  // Parser states
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_ENCRYPTION_DATA = 2;
  private static final int STATE_READING_SAMPLE = 3;

  // Atom data offsets
  private static final int ATOM_HEADER_SIZE = 8;
  private static final int FULL_ATOM_HEADER_SIZE = 12;

  // Atoms that the parser cares about
  private static final Set<Integer> PARSED_ATOMS;
  static {
    HashSet<Integer> parsedAtoms = new HashSet<Integer>();
    parsedAtoms.add(Atom.TYPE_avc1);
    parsedAtoms.add(Atom.TYPE_avc3);
    parsedAtoms.add(Atom.TYPE_esds);
    parsedAtoms.add(Atom.TYPE_hdlr);
    parsedAtoms.add(Atom.TYPE_mdat);
    parsedAtoms.add(Atom.TYPE_mdhd);
    parsedAtoms.add(Atom.TYPE_moof);
    parsedAtoms.add(Atom.TYPE_moov);
    parsedAtoms.add(Atom.TYPE_mp4a);
    parsedAtoms.add(Atom.TYPE_sidx);
    parsedAtoms.add(Atom.TYPE_stsd);
    parsedAtoms.add(Atom.TYPE_tfdt);
    parsedAtoms.add(Atom.TYPE_tfhd);
    parsedAtoms.add(Atom.TYPE_tkhd);
    parsedAtoms.add(Atom.TYPE_traf);
    parsedAtoms.add(Atom.TYPE_trak);
    parsedAtoms.add(Atom.TYPE_trex);
    parsedAtoms.add(Atom.TYPE_trun);
    parsedAtoms.add(Atom.TYPE_mvex);
    parsedAtoms.add(Atom.TYPE_mdia);
    parsedAtoms.add(Atom.TYPE_minf);
    parsedAtoms.add(Atom.TYPE_stbl);
    parsedAtoms.add(Atom.TYPE_pssh);
    parsedAtoms.add(Atom.TYPE_saiz);
    parsedAtoms.add(Atom.TYPE_uuid);
    parsedAtoms.add(Atom.TYPE_senc);
    PARSED_ATOMS = Collections.unmodifiableSet(parsedAtoms);
  }

  // Atoms that the parser considers to be containers
  private static final Set<Integer> CONTAINER_TYPES;
  static {
    HashSet<Integer> atomContainerTypes = new HashSet<Integer>();
    atomContainerTypes.add(Atom.TYPE_moov);
    atomContainerTypes.add(Atom.TYPE_trak);
    atomContainerTypes.add(Atom.TYPE_mdia);
    atomContainerTypes.add(Atom.TYPE_minf);
    atomContainerTypes.add(Atom.TYPE_stbl);
    atomContainerTypes.add(Atom.TYPE_avcC);
    atomContainerTypes.add(Atom.TYPE_moof);
    atomContainerTypes.add(Atom.TYPE_traf);
    atomContainerTypes.add(Atom.TYPE_mvex);
    CONTAINER_TYPES = Collections.unmodifiableSet(atomContainerTypes);
  }

  private final int workaroundFlags;

  // Parser state
  private final ParsableByteArray atomHeader;
  private final byte[] extendedTypeScratch;
  private final Stack<ContainerAtom> containerAtoms;
  private final Stack<Integer> containerAtomEndPoints;
  private final TrackFragment fragmentRun;

  private int parserState;
  private int atomBytesRead;
  private int rootAtomBytesRead;
  private int atomType;
  private int atomSize;
  private ParsableByteArray atomData;

  private int pendingSeekTimeMs;
  private int sampleIndex;
  private int pendingSeekSyncSampleIndex;
  private int lastSyncSampleIndex;

  // Data parsed from moov and sidx atoms
  private final HashMap<UUID, byte[]> psshData;
  private SegmentIndex segmentIndex;
  private Track track;
  private DefaultSampleValues extendsDefaults;

  public FragmentedMp4Extractor() {
    this(0);
  }

  /**
   * @param workaroundFlags Flags to allow parsing of faulty streams.
   *     {@link #WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME} is currently the only flag defined.
   */
  public FragmentedMp4Extractor(int workaroundFlags) {
    this.workaroundFlags = workaroundFlags;
    parserState = STATE_READING_ATOM_HEADER;
    atomHeader = new ParsableByteArray(ATOM_HEADER_SIZE);
    extendedTypeScratch = new byte[16];
    containerAtoms = new Stack<ContainerAtom>();
    containerAtomEndPoints = new Stack<Integer>();
    fragmentRun = new TrackFragment();
    psshData = new HashMap<UUID, byte[]>();
  }

  /**
   * Sideloads track information into the extractor.
   *
   * @param track The track to sideload.
   */
  public void setTrack(Track track) {
    this.extendsDefaults = new DefaultSampleValues(0, 0, 0, 0);
    this.track = track;
  }

  /**
   * Sideloads pssh information into the extractor, so that it can be read through
   * {@link #getPsshInfo()}.
   *
   * @param uuid The UUID of the scheme for which information is being sideloaded.
   * @param data The corresponding data.
   */
  public void putPsshInfo(UUID uuid, byte[] data) {
    // TODO: This is for SmoothStreaming. Consider using something other than
    // FragmentedMp4Extractor.getPsshInfo to obtain the pssh data for that use case, so that we can
    // remove this method.
    psshData.put(uuid, data);
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    return psshData.isEmpty() ? null : psshData;
  }

  @Override
  public SegmentIndex getIndex() {
    return segmentIndex;
  }

  @Override
  public boolean hasRelativeIndexOffsets() {
    return true;
  }

  @Override
  public MediaFormat getFormat() {
    return track == null ? null : track.mediaFormat;
  }

  @Override
  public int read(NonBlockingInputStream inputStream, SampleHolder out)
      throws ParserException {
    try {
      int results = 0;
      while ((results & READ_TERMINATING_RESULTS) == 0) {
        switch (parserState) {
          case STATE_READING_ATOM_HEADER:
            results |= readAtomHeader(inputStream);
            break;
          case STATE_READING_ATOM_PAYLOAD:
            results |= readAtomPayload(inputStream);
            break;
          case STATE_READING_ENCRYPTION_DATA:
            results |= readEncryptionData(inputStream);
            break;
          default:
              results |= readOrSkipSample(inputStream, out);
            break;
        }
      }
      return results;
    } catch (Exception e) {
      throw new ParserException(e);
    }
  }

  @Override
  public boolean seekTo(long seekTimeUs, boolean allowNoop) {
    pendingSeekTimeMs = (int) (seekTimeUs / 1000);
    if (allowNoop && fragmentRun != null
        && pendingSeekTimeMs >= fragmentRun.getSamplePresentationTime(0)
        && pendingSeekTimeMs <= fragmentRun.getSamplePresentationTime(fragmentRun.length - 1)) {
      int sampleIndexFound = 0;
      int syncSampleIndexFound = 0;
      for (int i = 0; i < fragmentRun.length; i++) {
        if (fragmentRun.getSamplePresentationTime(i) <= pendingSeekTimeMs) {
          if (fragmentRun.sampleIsSyncFrameTable[i]) {
            syncSampleIndexFound = i;
          }
          sampleIndexFound = i;
        }
      }
      if (syncSampleIndexFound == lastSyncSampleIndex && sampleIndexFound >= sampleIndex) {
        pendingSeekTimeMs = 0;
        return false;
      }
    }
    containerAtoms.clear();
    containerAtomEndPoints.clear();
    enterState(STATE_READING_ATOM_HEADER);
    return true;
  }

  private void enterState(int state) {
    switch (state) {
      case STATE_READING_ATOM_HEADER:
        atomBytesRead = 0;
        if (containerAtomEndPoints.isEmpty()) {
          rootAtomBytesRead = 0;
        }
        break;
    }
    parserState = state;
  }

  private int readAtomHeader(NonBlockingInputStream inputStream) {
    int remainingBytes = ATOM_HEADER_SIZE - atomBytesRead;
    int bytesRead = inputStream.read(atomHeader.data, atomBytesRead, remainingBytes);
    if (bytesRead == -1) {
      return RESULT_END_OF_STREAM;
    }
    rootAtomBytesRead += bytesRead;
    atomBytesRead += bytesRead;
    if (atomBytesRead != ATOM_HEADER_SIZE) {
      return RESULT_NEED_MORE_DATA;
    }

    atomHeader.setPosition(0);
    atomSize = atomHeader.readInt();
    atomType = atomHeader.readInt();

    if (atomType == Atom.TYPE_mdat) {
      if (fragmentRun.sampleEncryptionDataNeedsFill) {
        enterState(STATE_READING_ENCRYPTION_DATA);
      } else {
        enterState(STATE_READING_SAMPLE);
      }
      return 0;
    }

    if (PARSED_ATOMS.contains(atomType)) {
      if (CONTAINER_TYPES.contains(atomType)) {
        enterState(STATE_READING_ATOM_HEADER);
        containerAtoms.add(new ContainerAtom(atomType));
        containerAtomEndPoints.add(rootAtomBytesRead + atomSize - ATOM_HEADER_SIZE);
      } else {
        atomData = new ParsableByteArray(atomSize);
        System.arraycopy(atomHeader.data, 0, atomData.data, 0, ATOM_HEADER_SIZE);
        enterState(STATE_READING_ATOM_PAYLOAD);
      }
    } else {
      atomData = null;
      enterState(STATE_READING_ATOM_PAYLOAD);
    }

    return 0;
  }

  private int readAtomPayload(NonBlockingInputStream inputStream) {
    int bytesRead;
    if (atomData != null) {
      bytesRead = inputStream.read(atomData.data, atomBytesRead, atomSize - atomBytesRead);
    } else {
      bytesRead = inputStream.skip(atomSize - atomBytesRead);
    }
    if (bytesRead == -1) {
      return RESULT_END_OF_STREAM;
    }
    rootAtomBytesRead += bytesRead;
    atomBytesRead += bytesRead;
    if (atomBytesRead != atomSize) {
      return RESULT_NEED_MORE_DATA;
    }

    int results = 0;
    if (atomData != null) {
      results |= onLeafAtomRead(new LeafAtom(atomType, atomData));
    }

    while (!containerAtomEndPoints.isEmpty()
        && containerAtomEndPoints.peek() == rootAtomBytesRead) {
      containerAtomEndPoints.pop();
      results |= onContainerAtomRead(containerAtoms.pop());
    }

    enterState(STATE_READING_ATOM_HEADER);
    return results;
  }

  private int onLeafAtomRead(LeafAtom leaf) {
    if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(leaf);
    } else if (leaf.type == Atom.TYPE_sidx) {
      segmentIndex = parseSidx(leaf.data);
      return RESULT_READ_INDEX;
    }
    return 0;
  }

  private int onContainerAtomRead(ContainerAtom container) {
    if (container.type == Atom.TYPE_moov) {
      onMoovContainerAtomRead(container);
      return RESULT_READ_INIT;
    } else if (container.type == Atom.TYPE_moof) {
      onMoofContainerAtomRead(container);
    } else if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(container);
    }
    return 0;
  }

  private void onMoovContainerAtomRead(ContainerAtom moov) {
    List<Atom> moovChildren = moov.children;
    int moovChildrenSize = moovChildren.size();
    for (int i = 0; i < moovChildrenSize; i++) {
      Atom child = moovChildren.get(i);
      if (child.type == Atom.TYPE_pssh) {
        ParsableByteArray psshAtom = ((LeafAtom) child).data;
        psshAtom.setPosition(FULL_ATOM_HEADER_SIZE);
        UUID uuid = new UUID(psshAtom.readLong(), psshAtom.readLong());
        int dataSize = psshAtom.readInt();
        byte[] data = new byte[dataSize];
        psshAtom.readBytes(data, 0, dataSize);
        psshData.put(uuid, data);
      }
    }
    ContainerAtom mvex = moov.getContainerAtomOfType(Atom.TYPE_mvex);
    extendsDefaults = parseTrex(mvex.getLeafAtomOfType(Atom.TYPE_trex).data);
    track = parseTrak(moov.getContainerAtomOfType(Atom.TYPE_trak));
  }

  private void onMoofContainerAtomRead(ContainerAtom moof) {
    fragmentRun.reset();
    parseMoof(track, extendsDefaults, moof, fragmentRun, workaroundFlags, extendedTypeScratch);
    sampleIndex = 0;
    lastSyncSampleIndex = 0;
    pendingSeekSyncSampleIndex = 0;
    if (pendingSeekTimeMs != 0) {
      for (int i = 0; i < fragmentRun.length; i++) {
        if (fragmentRun.sampleIsSyncFrameTable[i]) {
          if (fragmentRun.getSamplePresentationTime(i) <= pendingSeekTimeMs) {
            pendingSeekSyncSampleIndex = i;
          }
        }
      }
      pendingSeekTimeMs = 0;
    }
  }

  /**
   * Parses a trex atom (defined in 14496-12).
   */
  private static DefaultSampleValues parseTrex(ParsableByteArray trex) {
    trex.setPosition(FULL_ATOM_HEADER_SIZE + 4);
    int defaultSampleDescriptionIndex = trex.readUnsignedIntToInt() - 1;
    int defaultSampleDuration = trex.readUnsignedIntToInt();
    int defaultSampleSize = trex.readUnsignedIntToInt();
    int defaultSampleFlags = trex.readInt();
    return new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration,
        defaultSampleSize, defaultSampleFlags);
  }

  /**
   * Parses a trak atom (defined in 14496-12).
   */
  private static Track parseTrak(ContainerAtom trak) {
    ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
    int trackType = parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data);
    Assertions.checkState(trackType == Track.TYPE_AUDIO || trackType == Track.TYPE_VIDEO);

    Pair<Integer, Long> header = parseTkhd(trak.getLeafAtomOfType(Atom.TYPE_tkhd).data);
    int id = header.first;
    // TODO: This value should be used to set a duration field on the Track object
    // instantiated below, however we've found examples where the value is 0. Revisit whether we
    // should set it anyway (and just have it be wrong for bad media streams).
    // long duration = header.second;
    long timescale = parseMdhd(mdia.getLeafAtomOfType(Atom.TYPE_mdhd).data);
    ContainerAtom stbl = mdia.getContainerAtomOfType(Atom.TYPE_minf)
        .getContainerAtomOfType(Atom.TYPE_stbl);

    Pair<MediaFormat, TrackEncryptionBox[]> sampleDescriptions =
        parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data);
    return new Track(id, trackType, timescale, sampleDescriptions.first, sampleDescriptions.second);
  }

  /**
   * Parses a tkhd atom (defined in 14496-12).
   *
   * @return A {@link Pair} consisting of the track id and duration.
   */
  private static Pair<Integer, Long> parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = parseFullAtomVersion(fullAtom);

    tkhd.skip(version == 0 ? 8 : 16);

    int trackId = tkhd.readInt();
    tkhd.skip(4);
    long duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();

    return Pair.create(trackId, duration);
  }

  /**
   * Parses an hdlr atom (defined in 14496-12).
   *
   * @param hdlr The hdlr atom to parse.
   * @return The track type.
   */
  private static int parseHdlr(ParsableByteArray hdlr) {
    hdlr.setPosition(FULL_ATOM_HEADER_SIZE + 4);
    return hdlr.readInt();
  }

  /**
   * Parses an mdhd atom (defined in 14496-12).
   *
   * @param mdhd The mdhd atom to parse.
   * @return The media timescale, defined as the number of time units that pass in one second.
   */
  private static long parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = parseFullAtomVersion(fullAtom);

    mdhd.skip(version == 0 ? 8 : 16);
    return mdhd.readUnsignedInt();
  }

  private static Pair<MediaFormat, TrackEncryptionBox[]> parseStsd(ParsableByteArray stsd) {
    stsd.setPosition(FULL_ATOM_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    MediaFormat mediaFormat = null;
    TrackEncryptionBox[] trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      int childAtomType = stsd.readInt();
      if (childAtomType == Atom.TYPE_avc1 || childAtomType == Atom.TYPE_avc3
          || childAtomType == Atom.TYPE_encv) {
        Pair<MediaFormat, TrackEncryptionBox> avc =
            parseAvcFromParent(stsd, childStartPosition, childAtomSize);
        mediaFormat = avc.first;
        trackEncryptionBoxes[i] = avc.second;
      } else if (childAtomType == Atom.TYPE_mp4a || childAtomType == Atom.TYPE_enca) {
        Pair<MediaFormat, TrackEncryptionBox> mp4a =
            parseMp4aFromParent(stsd, childStartPosition, childAtomSize);
        mediaFormat = mp4a.first;
        trackEncryptionBoxes[i] = mp4a.second;
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return Pair.create(mediaFormat, trackEncryptionBoxes);
  }

  private static Pair<MediaFormat, TrackEncryptionBox> parseAvcFromParent(ParsableByteArray parent,
      int position, int size) {
    parent.setPosition(position + ATOM_HEADER_SIZE);

    parent.skip(24);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    parent.skip(50);

    List<byte[]> initializationData = null;
    TrackEncryptionBox trackEncryptionBox = null;
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_avcC) {
        initializationData = parseAvcCFromParent(parent, childStartPosition);
      } else if (childAtomType == Atom.TYPE_sinf) {
        trackEncryptionBox = parseSinfFromParent(parent, childStartPosition, childAtomSize);
      }
      childPosition += childAtomSize;
    }

    MediaFormat format = MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
        width, height, initializationData);
    return Pair.create(format, trackEncryptionBox);
  }

  private static Pair<MediaFormat, TrackEncryptionBox> parseMp4aFromParent(ParsableByteArray parent,
      int position, int size) {
    parent.setPosition(position + ATOM_HEADER_SIZE);
    // Start of the mp4a atom (defined in 14496-14)
    parent.skip(16);
    int channelCount = parent.readUnsignedShort();
    int sampleSize = parent.readUnsignedShort();
    parent.skip(4);
    int sampleRate = parent.readUnsignedFixedPoint1616();

    byte[] initializationData = null;
    TrackEncryptionBox trackEncryptionBox = null;
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_esds) {
        initializationData = parseEsdsFromParent(parent, childStartPosition);
        // TODO: Do we really need to do this? See [redacted]
        // Update sampleRate and channelCount from the AudioSpecificConfig initialization data.
        Pair<Integer, Integer> audioSpecificConfig =
            CodecSpecificDataUtil.parseAudioSpecificConfig(initializationData);
        sampleRate = audioSpecificConfig.first;
        channelCount = audioSpecificConfig.second;
      } else if (childAtomType == Atom.TYPE_sinf) {
        trackEncryptionBox = parseSinfFromParent(parent, childStartPosition, childAtomSize);
      }
      childPosition += childAtomSize;
    }

    MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleSize, channelCount,
        sampleRate, Collections.singletonList(initializationData));
    return Pair.create(format, trackEncryptionBox);
  }

  private static List<byte[]> parseAvcCFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + ATOM_HEADER_SIZE + 4);
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
      initializationData.add(parseChildNalUnit(parent));
    }
    int numPictureParamterSets = parent.readUnsignedByte();
    for (int j = 0; j < numPictureParamterSets; j++) {
      initializationData.add(parseChildNalUnit(parent));
    }
    return initializationData;
  }

  private static byte[] parseChildNalUnit(ParsableByteArray atom) {
    int length = atom.readUnsignedShort();
    int offset = atom.getPosition();
    atom.skip(length);
    return CodecSpecificDataUtil.buildNalUnit(atom.data, offset, length);
  }

  private static TrackEncryptionBox parseSinfFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + ATOM_HEADER_SIZE;

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

  private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + ATOM_HEADER_SIZE;
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

  private static byte[] parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + ATOM_HEADER_SIZE + 4);
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

  private static void parseMoof(Track track, DefaultSampleValues extendsDefaults,
      ContainerAtom moof, TrackFragment out, int workaroundFlags, byte[] extendedTypeScratch) {
    parseTraf(track, extendsDefaults, moof.getContainerAtomOfType(Atom.TYPE_traf),
        out, workaroundFlags, extendedTypeScratch);
  }

  /**
   * Parses a traf atom (defined in 14496-12).
   */
  private static void parseTraf(Track track, DefaultSampleValues extendsDefaults,
      ContainerAtom traf, TrackFragment out, int workaroundFlags, byte[] extendedTypeScratch) {
    LeafAtom tfdtAtom = traf.getLeafAtomOfType(Atom.TYPE_tfdt);
    long decodeTime = tfdtAtom == null ? 0 : parseTfdt(traf.getLeafAtomOfType(Atom.TYPE_tfdt).data);

    LeafAtom tfhd = traf.getLeafAtomOfType(Atom.TYPE_tfhd);
    DefaultSampleValues fragmentHeader = parseTfhd(extendsDefaults, tfhd.data);
    out.sampleDescriptionIndex = fragmentHeader.sampleDescriptionIndex;

    LeafAtom trun = traf.getLeafAtomOfType(Atom.TYPE_trun);
    parseTrun(track, fragmentHeader, decodeTime, workaroundFlags, trun.data, out);

    LeafAtom saiz = traf.getLeafAtomOfType(Atom.TYPE_saiz);
    if (saiz != null) {
      TrackEncryptionBox trackEncryptionBox =
          track.sampleDescriptionEncryptionBoxes[fragmentHeader.sampleDescriptionIndex];
      parseSaiz(trackEncryptionBox, saiz.data, out);
    }

    LeafAtom senc = traf.getLeafAtomOfType(Atom.TYPE_senc);
    if (senc != null) {
      parseSenc(senc.data, out);
    }

    LeafAtom uuid = traf.getLeafAtomOfType(Atom.TYPE_uuid);
    if (uuid != null) {
      parseUuid(uuid.data, out, extendedTypeScratch);
    }
  }

  private static void parseSaiz(TrackEncryptionBox encryptionBox, ParsableByteArray saiz,
      TrackFragment out) {
    int vectorSize = encryptionBox.initializationVectorSize;
    saiz.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = saiz.readInt();
    int flags = parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saiz.skip(8);
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
   * Parses a tfhd atom (defined in 14496-12).
   *
   * @param extendsDefaults Default sample values from the trex atom.
   * @return The parsed default sample values.
   */
  private static DefaultSampleValues parseTfhd(DefaultSampleValues extendsDefaults,
      ParsableByteArray tfhd) {
    tfhd.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = tfhd.readInt();
    int flags = parseFullAtomFlags(fullAtom);

    tfhd.skip(4); // trackId
    if ((flags & 0x01 /* base_data_offset_present */) != 0) {
      tfhd.skip(8);
    }

    int defaultSampleDescriptionIndex =
        ((flags & 0x02 /* default_sample_description_index_present */) != 0) ?
        tfhd.readUnsignedIntToInt() - 1 : extendsDefaults.sampleDescriptionIndex;
    int defaultSampleDuration = ((flags & 0x08 /* default_sample_duration_present */) != 0) ?
        tfhd.readUnsignedIntToInt() : extendsDefaults.duration;
    int defaultSampleSize = ((flags & 0x10 /* default_sample_size_present */) != 0) ?
        tfhd.readUnsignedIntToInt() : extendsDefaults.size;
    int defaultSampleFlags = ((flags & 0x20 /* default_sample_flags_present */) != 0) ?
        tfhd.readUnsignedIntToInt() : extendsDefaults.flags;
    return new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration,
        defaultSampleSize, defaultSampleFlags);
  }

  /**
   * Parses a tfdt atom (defined in 14496-12).
   *
   * @return baseMediaDecodeTime. The sum of the decode durations of all earlier samples in the
   *     media, expressed in the media's timescale.
   */
  private static long parseTfdt(ParsableByteArray tfdt) {
    tfdt.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = tfdt.readInt();
    int version = parseFullAtomVersion(fullAtom);
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
    trun.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = trun.readInt();
    int flags = parseFullAtomFlags(fullAtom);

    int sampleCount = trun.readUnsignedIntToInt();
    if ((flags & 0x01 /* data_offset_present */) != 0) {
      trun.skip(4);
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
    int[] sampleDecodingTimeTable = out.sampleDecodingTimeTable;
    int[] sampleCompositionTimeOffsetTable = out.sampleCompositionTimeOffsetTable;
    boolean[] sampleIsSyncFrameTable = out.sampleIsSyncFrameTable;

    long timescale = track.timescale;
    long cumulativeTime = decodeTime;
    boolean workaroundEveryVideoFrameIsSyncFrame = track.type == Track.TYPE_VIDEO
        && ((workaroundFlags & WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME)
        == WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME);
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
      sampleDecodingTimeTable[i] = (int) ((cumulativeTime * 1000) / timescale);
      sampleSizeTable[i] = sampleSize;
      sampleIsSyncFrameTable[i] = ((sampleFlags >> 16) & 0x1) == 0
          && (!workaroundEveryVideoFrameIsSyncFrame || i == 0);
      cumulativeTime += sampleDuration;
    }
  }

  private static void parseUuid(ParsableByteArray uuid, TrackFragment out,
      byte[] extendedTypeScratch) {
    uuid.setPosition(ATOM_HEADER_SIZE);
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
    senc.setPosition(ATOM_HEADER_SIZE + offset);
    int fullAtom = senc.readInt();
    int flags = parseFullAtomFlags(fullAtom);

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
    out.initEncryptionData(senc.length() - senc.getPosition());
    out.fillEncryptionData(senc);
  }

  /**
   * Parses a sidx atom (defined in 14496-12).
   */
  private static SegmentIndex parseSidx(ParsableByteArray atom) {
    atom.setPosition(ATOM_HEADER_SIZE);
    int fullAtom = atom.readInt();
    int version = parseFullAtomVersion(fullAtom);

    atom.skip(4);
    long timescale = atom.readUnsignedInt();
    long earliestPresentationTime;
    long firstOffset;
    if (version == 0) {
      earliestPresentationTime = atom.readUnsignedInt();
      firstOffset = atom.readUnsignedInt();
    } else {
      earliestPresentationTime = atom.readUnsignedLongToLong();
      firstOffset = atom.readUnsignedLongToLong();
    }

    atom.skip(2);

    int referenceCount = atom.readUnsignedShort();
    int[] sizes = new int[referenceCount];
    long[] offsets = new long[referenceCount];
    long[] durationsUs = new long[referenceCount];
    long[] timesUs = new long[referenceCount];

    long offset = firstOffset;
    long time = earliestPresentationTime;
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
      timesUs[i] = (time * 1000000L) / timescale;
      long nextTimeUs = ((time + referenceDuration) * 1000000L) / timescale;
      durationsUs[i] = nextTimeUs - timesUs[i];
      time += referenceDuration;

      atom.skip(4);
      offset += sizes[i];
    }

    return new SegmentIndex(atom.length(), sizes, offsets, durationsUs, timesUs);
  }

  private int readEncryptionData(NonBlockingInputStream inputStream) {
    boolean success = fragmentRun.fillEncryptionData(inputStream);
    if (!success) {
      return RESULT_NEED_MORE_DATA;
    }
    enterState(STATE_READING_SAMPLE);
    return 0;
  }

  /**
   * Attempts to read or skip the next sample in the current mdat atom.
   * <p>
   * If there are no more samples in the current mdat atom then the parser state is transitioned
   * to {@link #STATE_READING_ATOM_HEADER} and 0 is returned.
   * <p>
   * If there's a pending seek to a sync frame, and if the next sample is before that frame, then
   * the sample is skipped. Otherwise it is read.
   * <p>
   * It is possible for a sample to be read or skipped in part if there is insufficent data
   * available from the {@link NonBlockingInputStream}. In this case the remainder of the sample
   * can be read in a subsequent call passing the same {@link SampleHolder}.
   *
   * @param inputStream The stream from which to read the sample.
   * @param out The holder into which to write the sample.
   * @return A combination of RESULT_* flags indicating the result of the call.
   */
  private int readOrSkipSample(NonBlockingInputStream inputStream, SampleHolder out) {
    if (sampleIndex >= fragmentRun.length) {
      // We've run out of samples in the current mdat atom.
      enterState(STATE_READING_ATOM_HEADER);
      return 0;
    }
    int sampleSize = fragmentRun.sampleSizeTable[sampleIndex];
    if (inputStream.getAvailableByteCount() < sampleSize) {
      return RESULT_NEED_MORE_DATA;
    }
    if (sampleIndex < pendingSeekSyncSampleIndex) {
      return skipSample(inputStream, sampleSize);
    }
    return readSample(inputStream, sampleSize, out);
  }

  private int skipSample(NonBlockingInputStream inputStream, int sampleSize) {
    if (fragmentRun.definesEncryptionData) {
      ParsableByteArray sampleEncryptionData = fragmentRun.sampleEncryptionData;
      TrackEncryptionBox encryptionBox =
          track.sampleDescriptionEncryptionBoxes[fragmentRun.sampleDescriptionIndex];
      int vectorSize = encryptionBox.initializationVectorSize;
      boolean subsampleEncryption = fragmentRun.sampleHasSubsampleEncryptionTable[sampleIndex];
      sampleEncryptionData.skip(vectorSize);
      int subsampleCount = subsampleEncryption ? sampleEncryptionData.readUnsignedShort() : 1;
      if (subsampleEncryption) {
        sampleEncryptionData.skip((2 + 4) * subsampleCount);
      }
    }

    inputStream.skip(sampleSize);

    sampleIndex++;
    enterState(STATE_READING_SAMPLE);
    return 0;
  }

  @SuppressLint("InlinedApi")
  private int readSample(NonBlockingInputStream inputStream, int sampleSize, SampleHolder out) {
    if (out == null) {
      return RESULT_NEED_SAMPLE_HOLDER;
    }
    ByteBuffer outputData = out.data;
    out.timeUs = fragmentRun.getSamplePresentationTime(sampleIndex) * 1000L;
    out.flags = 0;
    if (fragmentRun.sampleIsSyncFrameTable[sampleIndex]) {
      out.flags |= MediaExtractor.SAMPLE_FLAG_SYNC;
      lastSyncSampleIndex = sampleIndex;
    }
    if (out.allowDataBufferReplacement && (out.data == null || out.data.capacity() < sampleSize)) {
      outputData = ByteBuffer.allocate(sampleSize);
      out.data = outputData;
    }
    if (fragmentRun.definesEncryptionData) {
      readSampleEncryptionData(fragmentRun.sampleEncryptionData, out);
    }

    if (outputData == null) {
      inputStream.skip(sampleSize);
      out.size = 0;
    } else {
      inputStream.read(outputData, sampleSize);
      if (track.type == Track.TYPE_VIDEO) {
        // The mp4 file contains length-prefixed NAL units, but the decoder wants start code
        // delimited content. Replace length prefixes with start codes.
        int sampleOffset = outputData.position() - sampleSize;
        int position = sampleOffset;
        while (position < sampleOffset + sampleSize) {
          outputData.position(position);
          int length = readUnsignedIntToInt(outputData);
          outputData.position(position);
          outputData.put(NAL_START_CODE);
          position += length + 4;
        }
        outputData.position(sampleOffset + sampleSize);
      }
      out.size = sampleSize;
    }

    sampleIndex++;
    enterState(STATE_READING_SAMPLE);
    return RESULT_READ_SAMPLE;
  }

  @SuppressLint("InlinedApi")
  private void readSampleEncryptionData(ParsableByteArray sampleEncryptionData, SampleHolder out) {
    TrackEncryptionBox encryptionBox =
        track.sampleDescriptionEncryptionBoxes[fragmentRun.sampleDescriptionIndex];
    byte[] keyId = encryptionBox.keyId;
    boolean isEncrypted = encryptionBox.isEncrypted;
    int vectorSize = encryptionBox.initializationVectorSize;
    boolean subsampleEncryption = fragmentRun.sampleHasSubsampleEncryptionTable[sampleIndex];

    byte[] vector = out.cryptoInfo.iv;
    if (vector == null || vector.length != 16) {
      vector = new byte[16];
    }
    sampleEncryptionData.readBytes(vector, 0, vectorSize);

    int subsampleCount = subsampleEncryption ? sampleEncryptionData.readUnsignedShort() : 1;
    int[] clearDataSizes = out.cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    int[] encryptedDataSizes = out.cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = sampleEncryptionData.readUnsignedShort();
        encryptedDataSizes[i] = sampleEncryptionData.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = fragmentRun.sampleSizeTable[sampleIndex];
    }
    out.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes, keyId, vector,
        isEncrypted ? MediaCodec.CRYPTO_MODE_AES_CTR : MediaCodec.CRYPTO_MODE_UNENCRYPTED);
    if (isEncrypted) {
      out.flags |= MediaExtractor.SAMPLE_FLAG_ENCRYPTED;
    }
  }

  /**
   * Parses the version number out of the additional integer component of a full atom.
   */
  private static int parseFullAtomVersion(int fullAtomInt) {
    return 0x000000FF & (fullAtomInt >> 24);
  }

  /**
   * Parses the atom flags out of the additional integer component of a full atom.
   */
  private static int parseFullAtomFlags(int fullAtomInt) {
    return 0x00FFFFFF & fullAtomInt;
  }

  /**
   * Reads an unsigned integer into an integer. This method is suitable for use when it can be
   * assumed that the top bit will always be set to zero.
   *
   * @throws IllegalArgumentException If the top bit of the input data is set.
   */
  private static int readUnsignedIntToInt(ByteBuffer data) {
    int result = 0xFF & data.get();
    for (int i = 1; i < 4; i++) {
      result <<= 8;
      result |= 0xFF & data.get();
    }
    if (result < 0) {
      throw new IllegalArgumentException("Top bit not zero: " + result);
    }
    return result;
  }

}
