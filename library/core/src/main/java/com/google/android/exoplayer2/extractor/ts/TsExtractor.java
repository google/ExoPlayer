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
package com.google.android.exoplayer2.extractor.ts;

import android.support.annotation.IntDef;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.Flags;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.DvbSubtitleInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts data from the MPEG-2 TS container format.
 */
public final class TsExtractor implements Extractor, SeekMap {

  /**
   * Factory for {@link TsExtractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new TsExtractor()};
    }

  };

  /**
   * Modes for the extractor.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_MULTI_PMT, MODE_SINGLE_PMT, MODE_HLS})
  public @interface Mode {}

  /**
   * Behave as defined in ISO/IEC 13818-1.
   */
  public static final int MODE_MULTI_PMT = 0;
  /**
   * Assume only one PMT will be contained in the stream, even if more are declared by the PAT.
   */
  public static final int MODE_SINGLE_PMT = 1;
  /**
   * Enable single PMT mode, map {@link TrackOutput}s by their type (instead of PID) and ignore
   * continuity counters.
   */
  public static final int MODE_HLS = 2;

  public static final int TS_STREAM_TYPE_MPA = 0x03;
  public static final int TS_STREAM_TYPE_MPA_LSF = 0x04;
  public static final int TS_STREAM_TYPE_AAC_ADTS = 0x0F;
  public static final int TS_STREAM_TYPE_AAC_LATM = 0x11;
  public static final int TS_STREAM_TYPE_AC3 = 0x81;
  public static final int TS_STREAM_TYPE_DTS = 0x8A;
  public static final int TS_STREAM_TYPE_HDMV_DTS = 0x82;
  public static final int TS_STREAM_TYPE_E_AC3 = 0x87;
  public static final int TS_STREAM_TYPE_H262 = 0x02;
  public static final int TS_STREAM_TYPE_H264 = 0x1B;
  public static final int TS_STREAM_TYPE_H265 = 0x24;
  public static final int TS_STREAM_TYPE_ID3 = 0x15;
  public static final int TS_STREAM_TYPE_SPLICE_INFO = 0x86;
  public static final int TS_STREAM_TYPE_DVBSUBS = 0x59;

  private static final Comparator<SeekPoint> SEEK_POINT_COMPARATOR = new Comparator<SeekPoint>() {
    @Override
    public int compare(SeekPoint p1, SeekPoint p2) {
      int result = (p1.timeUs < p2.timeUs ? -1 : (p1.timeUs == p2.timeUs ? 0 : 1));
      if (result != 0) return result;
      return (p1.position < p2.position ? -1 : (p1.position == p2.position ? 0 : 1));
    }
  };

  private static class SortedSeekPointList extends ArrayList<SeekPoint>{
    @Override
    public boolean add(SeekPoint seekPoint) {
      // Make sure the list is always sorted, so we can use binary search
      // We potentially insert items out of order when we skip parts for fast seeking
      int index = Collections.binarySearch(this, seekPoint, SEEK_POINT_COMPARATOR);
      if (index < 0) index = ~index;
      else return false;  // The element is already contained in the list
      super.add(index, seekPoint);
      return true;
    }
  }

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.
  private static final int TS_PAT_PID = 0;
  private static final int MAX_PID_PLUS_ONE = 0x2000;

  private static final long AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("AC-3");
  private static final long E_AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("EAC3");
  private static final long HEVC_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("HEVC");

  private static final int BUFFER_SIZE = TS_PACKET_SIZE * 50;
  private static final int SNIFF_TS_PACKET_COUNT = 5;

  private static final int DURATION_READ_PACKETS = 100;
  private static final int MAX_SEEK_OFFSET_US = 200000; // Twice the max distance between two pcrs

  @Mode private final int mode;
  private final List<TimestampAdjuster> timestampAdjusters;
  private final ParsableByteArray tsPacketBuffer;
  private final SparseIntArray continuityCounters;
  private final TsPayloadReader.Factory payloadReaderFactory;
  private final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  private final SparseBooleanArray trackIds;

  // Accessed only by the loading thread.
  private ExtractorOutput output;
  private int remainingPmts;
  private boolean tracksEnded;
  private TsPayloadReader id3Reader;
  private int bytesSinceLastSync;

  // For seeking
  private SparseArray<SortedSeekPointList> passedSeekPoints;
  private long seekTimeUs = C.TIME_UNSET; // The time we are seeking to. Used to skip payloads.
  private int seekPid = -1; // The pid of the time we are seeking to. Used to skip payloads.
  private SeekPoint endSeekPoint = null;

  public TsExtractor() {
    this(0);
  }

  /**
   * @param defaultTsPayloadReaderFlags A combination of {@link DefaultTsPayloadReaderFactory}
   *     {@code FLAG_*} values that control the behavior of the payload readers.
   */
  public TsExtractor(@Flags int defaultTsPayloadReaderFlags) {
    this(MODE_SINGLE_PMT, defaultTsPayloadReaderFlags);
  }

  /**
   * @param mode Mode for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT}
   *     and {@link #MODE_HLS}.
   * @param defaultTsPayloadReaderFlags A combination of {@link DefaultTsPayloadReaderFactory}
   *     {@code FLAG_*} values that control the behavior of the payload readers.
   */
  public TsExtractor(@Mode int mode, @Flags int defaultTsPayloadReaderFlags) {
    this(mode, new TimestampAdjuster(0),
        new DefaultTsPayloadReaderFactory(defaultTsPayloadReaderFlags));
  }


  /**
   * @param mode Mode for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT}
   *     and {@link #MODE_HLS}.
   * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
   * @param payloadReaderFactory Factory for injecting a custom set of payload readers.
   */
  public TsExtractor(@Mode int mode, TimestampAdjuster timestampAdjuster,
      TsPayloadReader.Factory payloadReaderFactory) {
    this.payloadReaderFactory = Assertions.checkNotNull(payloadReaderFactory);
    this.mode = mode;
    if (mode == MODE_SINGLE_PMT || mode == MODE_HLS) {
      timestampAdjusters = Collections.singletonList(timestampAdjuster);
    } else {
      timestampAdjusters = new ArrayList<>();
      timestampAdjusters.add(timestampAdjuster);
    }
    tsPacketBuffer = new ParsableByteArray(new byte[BUFFER_SIZE], 0);
    trackIds = new SparseBooleanArray();
    tsPayloadReaders = new SparseArray<>();
    continuityCounters = new SparseIntArray();
    passedSeekPoints = new SparseArray<>();
    resetPayloadReaders();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] buffer = tsPacketBuffer.data;
    input.peekFully(buffer, 0, TS_PACKET_SIZE * SNIFF_TS_PACKET_COUNT);
    for (int j = 0; j < TS_PACKET_SIZE; j++) {
      for (int i = 0; true; i++) {
        if (i == SNIFF_TS_PACKET_COUNT) {
          input.skipFully(j);
          return true;
        }
        if (buffer[j + i * TS_PACKET_SIZE] != TS_SYNC_BYTE) {
          break;
        }
      }
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    output.seekMap(this);
  }

  @Override
  public void seek(long position, long timeUs) {

    // We don't reset the timestamp adjusters here

    tsPacketBuffer.reset();
    continuityCounters.clear();
    // Elementary stream readers' state should be cleared to get consistent behaviours when seeking.
    resetPayloadReaders();
    bytesSinceLastSync = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    byte[] data = tsPacketBuffer.data;

    // Shift bytes to the start of the buffer if there isn't enough space left at the end.
    if (BUFFER_SIZE - tsPacketBuffer.getPosition() < TS_PACKET_SIZE) {
      int bytesLeft = tsPacketBuffer.bytesLeft();
      if (bytesLeft > 0) {
        System.arraycopy(data, tsPacketBuffer.getPosition(), data, 0, bytesLeft);
      }
      tsPacketBuffer.reset(data, bytesLeft);
    }

    // Read more bytes until we have at least one packet.
    while (tsPacketBuffer.bytesLeft() < TS_PACKET_SIZE) {
      int limit = tsPacketBuffer.limit();
      int read = input.read(data, limit, BUFFER_SIZE - limit);
      if (read == C.RESULT_END_OF_INPUT) {
        return RESULT_END_OF_INPUT;
      }
      tsPacketBuffer.setLimit(limit + read);
    }

    // Note: See ISO/IEC 13818-1, section 2.4.3.2 for details of the header format.
    int limit = tsPacketBuffer.limit();
    int position = tsPacketBuffer.getPosition();
    int searchStart = position;
    while (position < limit && data[position] != TS_SYNC_BYTE) {
      position++;
    }
    tsPacketBuffer.setPosition(position);

    int endOfPacket = position + TS_PACKET_SIZE;
    if (endOfPacket > limit) {
      bytesSinceLastSync += position - searchStart;
      if (mode == MODE_HLS && bytesSinceLastSync > TS_PACKET_SIZE * 2) {
        throw new ParserException("Cannot find sync byte. Most likely not a Transport Stream.");
      }
      return RESULT_CONTINUE;
    }
    bytesSinceLastSync = 0;

    int tsPacketHeader = tsPacketBuffer.readInt();
    if ((tsPacketHeader & 0x800000) != 0) { // transport_error_indicator
      // There are uncorrectable errors in this packet.
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }
    boolean payloadUnitStartIndicator = (tsPacketHeader & 0x400000) != 0;
    // Ignoring transport_priority (tsPacketHeader & 0x200000)
    int pid = (tsPacketHeader & 0x1FFF00) >> 8;
    // Ignoring transport_scrambling_control (tsPacketHeader & 0xC0)
    boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;
    boolean payloadExists = (tsPacketHeader & 0x10) != 0;

    // Check if we're interested in the payload stream with this pid
    TsPayloadReader payloadReader = payloadExists ? tsPayloadReaders.get(pid) : null;
    if (payloadReader == null) {
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }

    // Discontinuity check.
    if (mode != MODE_HLS) {
      int continuityCounter = tsPacketHeader & 0xF;
      int previousCounter = continuityCounters.get(pid, continuityCounter - 1);
      continuityCounters.put(pid, continuityCounter);
      if (previousCounter == continuityCounter) {
        // Duplicate packet found.
        tsPacketBuffer.setPosition(endOfPacket);
        return RESULT_CONTINUE;
      } else if (continuityCounter != ((previousCounter + 1) & 0xF)) {
        // Discontinuity found.
        payloadReader.seek();
      }
    }

    // Manage seeking structure
    if (mode != MODE_HLS && adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
      if (adaptationFieldLength > 0) {
        int flags = tsPacketBuffer.readUnsignedByte();
        boolean pcrFlagSet = (flags & 0x10) == 0x10;
        if (pcrFlagSet && adaptationFieldLength >= 7) {
          byte[] pcrBytes = new byte[6];
          tsPacketBuffer.readBytes(pcrBytes, 0, pcrBytes.length);
          long positionUs = getPcrPositionUs(pcrBytes);

          SortedSeekPointList seekPointsForPid = passedSeekPoints.get(pid);
          if (seekPointsForPid == null){
            seekPointsForPid = new SortedSeekPointList();
            passedSeekPoints.put(pid, seekPointsForPid);
          }
          seekPointsForPid.add(new SeekPoint(positionUs, input.getPosition() - TS_PACKET_SIZE));

          if (seekTimeUs != C.TIME_UNSET && seekPid == pid &&
              seekTimeUs - positionUs > MAX_SEEK_OFFSET_US){
            // Skip rest of packet since we're too far from the seek point
            tsPacketBuffer.setPosition(endOfPacket);
            return RESULT_CONTINUE;
          } else {
            seekTimeUs = C.TIME_UNSET;
          }

          tsPacketBuffer.skipBytes(adaptationFieldLength - 7);
        } else {
          tsPacketBuffer.skipBytes(adaptationFieldLength - 1);
        }
      }
    }

    // We can skip consuming the payload data if we're seeking and the payload doesn't contain
    // metadata
    if (seekTimeUs != C.TIME_UNSET && !(payloadReader instanceof SectionReader)){
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }

    // Read the payload.
    tsPacketBuffer.setLimit(endOfPacket);
    payloadReader.consume(tsPacketBuffer, payloadUnitStartIndicator);
    tsPacketBuffer.setLimit(limit);

    tsPacketBuffer.setPosition(endOfPacket);
    return RESULT_CONTINUE;
  }

  /**
   * Tries to extract a ts file's duration by reading pcr values from the start and from the end
   * of the stream. The maximal difference between pcrs from the same pid is used as duration.
   * @param dataSource The data source to read the stream data from. ExtractorInputs from the start
   *                   and the end of the stream will be constructed on this dataSource, so it should
   *                   support efficient skipping.
   * @param originalDataSpec The data spec that is used for playback of the stream. Used for accessing
   *                         the stream and for restoring the dataSource once we're done.
   * @throws IOException From the underlying source or input
   * @throws InterruptedException From the underlying source or input
   */
  public void readDuration(DataSource dataSource, DataSpec originalDataSpec)
      throws IOException, InterruptedException {
    // Only read duration if this is not an HLS chunk
    if (mode == MODE_HLS) return;

    // Make sure we only try reading the duration once
    if (endSeekPoint != null) return;
    endSeekPoint = new SeekPoint(C.LENGTH_UNSET, C.LENGTH_UNSET);

    // Open a new ExtractorInput from the start of the stream
    dataSource.close();
    DataSpec startDataSpec = new DataSpec(originalDataSpec.uri, 0,
        originalDataSpec.length, originalDataSpec.key);
    long dataLength = dataSource.open(startDataSpec);
    ExtractorInput input = new DefaultExtractorInput(dataSource, 0, dataLength);
    long inputLength = input.getLength();

    ParsableByteArray packetBuffer = new ParsableByteArray(TS_PACKET_SIZE * DURATION_READ_PACKETS);

    input.readFully(packetBuffer.data, 0, packetBuffer.limit());
    SparseArray<Long> minPositionsUs = readPcrsFromPackets(packetBuffer, true);

    // Open a new ExtractorInput from the end of the stream
    dataSource.close();
    long skipTarget = input.getLength() - (packetBuffer.limit());
    DataSpec endDataSpec = new DataSpec(originalDataSpec.uri, skipTarget,
        packetBuffer.limit(), originalDataSpec.key);
    dataSource.open(endDataSpec);
    input = new DefaultExtractorInput(dataSource, skipTarget, dataLength);
    packetBuffer.setPosition(0);

    input.readFully(packetBuffer.data, 0, packetBuffer.limit());
    SparseArray<Long> maxPositionsUs = readPcrsFromPackets(packetBuffer, false);

    // Find the maximal difference between pcrs of the same pid
    long maxDuration = C.LENGTH_UNSET;
    for (int i = 0; i < minPositionsUs.size(); i++){
      int pid = minPositionsUs.keyAt(i);
      Long maxPositionForPid = maxPositionsUs.get(pid);
      if (maxPositionForPid != null){
        long durationForPid = maxPositionForPid - minPositionsUs.get(pid);
        if (durationForPid > maxDuration) maxDuration = durationForPid;
      }
    }

    if (maxDuration != C.LENGTH_UNSET){
      endSeekPoint = new SeekPoint(maxDuration, inputLength);
    }

    // Restore the data source to its original state
    dataSource.close();
    dataSource.open(originalDataSpec);
  }

  // SeekMap implementation

  @Override
  public boolean isSeekable() {
    return mode != MODE_HLS;
  }

  @Override
  public long getDurationUs() {
    return endSeekPoint == null ? C.LENGTH_UNSET : endSeekPoint.timeUs;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    if (mode == MODE_HLS){
      return null;
    }

    // Seeking to the very beginning if requested or if we don't know any seek points
    if (timeUs == 0 || passedSeekPoints.size() == 0){
      return new SeekPoints(SeekPoint.START);
    }

    // Assume all payload streams have the same duration, so just use the first one here
    int usedSeekPid = passedSeekPoints.keyAt(0);
    SortedSeekPointList seekPoints = passedSeekPoints.get(usedSeekPid);

    // Search for the closest position using binary search
    // The first time-position might not be aligned with 0 (live-streaming)
    timeUs += seekPoints.get(0).timeUs;
    int lower = 0, upper = seekPoints.size();
    int mid = -1;
    while (lower < upper) {
      mid = (lower + upper) / 2;
      if (seekPoints.get(mid).timeUs == timeUs) {
        break;
      } else if (seekPoints.get(mid).timeUs > timeUs) {
        upper = mid;
      } else if (seekPoints.get(mid).timeUs < timeUs) {
        lower = mid + 1;
      }
    }

    if (mid == -1) return null;

    SeekPoint seekPoint = seekPoints.get(mid);

    long timeDiff = timeUs - seekPoint.timeUs;
    if (Math.abs(timeDiff) > MAX_SEEK_OFFSET_US) {
      seekPid = usedSeekPid;

      if (timeDiff > 0) seekTimeUs = timeUs;

      // Fast Seeking: Interpolate a seek point using duration and input length.
      // This hugely improves seek performance, but it's based on heuristics.
      // In the worst case, seeking might jump to a position that is a bit after the
      // actual position for the requested time.
      if (endSeekPoint != null && endSeekPoint.timeUs != C.LENGTH_UNSET &&
          endSeekPoint.position != C.LENGTH_UNSET) {
        float bytesPerUs = ((float) endSeekPoint.position) / ((float) endSeekPoint.timeUs);
        long estimatedBytesDiff = (long) (bytesPerUs * ((float) timeDiff));

        // Make sure the estimated point is always before the actual point
        float reductionFactor = timeDiff > 0 ? 0.8f : 1.2f;
        estimatedBytesDiff = (long) (((float) estimatedBytesDiff) * reductionFactor);
        timeDiff = (long) (((float) timeDiff) * reductionFactor);

        seekPoint = new SeekPoint(seekPoint.timeUs + timeDiff,
            seekPoint.position + estimatedBytesDiff);

        // The interpolated seek point is before the actual point even if the nearest actual
        // seek point is after it.
        seekTimeUs = timeUs;
      }
    }
    return new SeekPoints(seekPoint);
  }

  // Internals.

  private SparseArray<Long> readPcrsFromPackets(ParsableByteArray packetBuffer, boolean collectMinValues) {
    SparseArray<Long> pcrs = new SparseArray<>();

    for (int i = 0; i < packetBuffer.limit() / TS_PACKET_SIZE &&
        packetBuffer.bytesLeft() >= TS_PACKET_SIZE; i++) {

      while (packetBuffer.peekUnsignedByte() != TS_SYNC_BYTE &&
          packetBuffer.bytesLeft() > TS_PACKET_SIZE) {
        packetBuffer.readUnsignedByte();
      }
      if (packetBuffer.peekUnsignedByte() != TS_SYNC_BYTE) {
        // We weren't able to find the start of a packet
        break;
      }

      int endOfPacket = packetBuffer.getPosition() + TS_PACKET_SIZE;

      int tsPacketHeader = packetBuffer.readInt();
      if ((tsPacketHeader & 0x800000) != 0) { // transport_error_indicator
        // There are uncorrectable errors in this packet.
        continue;
      }

      int pid = (tsPacketHeader & 0x1FFF00) >> 8;
      boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;

      if (adaptationFieldExists) {
        int adaptationFieldLength = packetBuffer.readUnsignedByte();
        if (adaptationFieldLength > 0) {
          int flags = packetBuffer.readUnsignedByte();
          boolean pcrFlagSet = (flags & 0x10) == 0x10;
          if (pcrFlagSet && adaptationFieldLength >= 7) {
            byte[] pcrBytes = new byte[6];
            packetBuffer.readBytes(pcrBytes, 0, pcrBytes.length);

            long positionUs = getPcrPositionUs(pcrBytes);
            long existingValueForPid = pcrs.get(pid, collectMinValues ?
                Long.MAX_VALUE : Long.MIN_VALUE);
            if (collectMinValues == positionUs < existingValueForPid) {
              pcrs.put(pid, positionUs);
            }
          }
        }
      }
      packetBuffer.setPosition(endOfPacket);
    }

    return pcrs;
  }

  // Calculate time position in microseconds from a pcr value
  private long getPcrPositionUs(byte[] pcrBytes) {
    long pcr = (((long)((pcrBytes[0] << 24)
        + (pcrBytes[1] << 16 & 0x00FF0000)
        + (pcrBytes[2] << 8 & 0x0000FF00)
        + (pcrBytes[3] & 0x000000FF))) << 1 & 0x00000001FFFFFFFFL)
        + (pcrBytes[4] >> 7 & 1);
    return (pcr / 90) * 1000;
  }

  private void resetPayloadReaders() {
    trackIds.clear();
    tsPayloadReaders.clear();
    SparseArray<TsPayloadReader> initialPayloadReaders =
        payloadReaderFactory.createInitialPayloadReaders();
    int initialPayloadReadersSize = initialPayloadReaders.size();
    for (int i = 0; i < initialPayloadReadersSize; i++) {
      tsPayloadReaders.put(initialPayloadReaders.keyAt(i), initialPayloadReaders.valueAt(i));
    }
    tsPayloadReaders.put(TS_PAT_PID, new SectionReader(new PatReader()));
    id3Reader = null;
  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader implements SectionPayloadReader {

    private final ParsableBitArray patScratch;

    public PatReader() {
      patScratch = new ParsableBitArray(new byte[4]);
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
        TrackIdGenerator idGenerator) {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x00 /* program_association_section */) {
        // See ISO/IEC 13818-1, section 2.4.4.4 for more information on table id assignment.
        return;
      }
      // section_syntax_indicator(1), '0'(1), reserved(2), section_length(12),
      // transport_stream_id (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8)
      sectionData.skipBytes(7);

      int programCount = sectionData.bytesLeft() / 4;
      for (int i = 0; i < programCount; i++) {
        sectionData.readBytes(patScratch, 4);
        int programNumber = patScratch.readBits(16);
        patScratch.skipBits(3); // reserved (3)
        if (programNumber == 0) {
          patScratch.skipBits(13); // network_PID (13)
        } else {
          int pid = patScratch.readBits(13);
          tsPayloadReaders.put(pid, new SectionReader(new PmtReader(pid)));
          remainingPmts++;
        }
      }
      if (mode != MODE_HLS) {
        tsPayloadReaders.remove(TS_PAT_PID);
      }
    }

  }

  /**
   * Parses Program Map Table.
   */
  private class PmtReader implements SectionPayloadReader {

    private static final int TS_PMT_DESC_REGISTRATION = 0x05;
    private static final int TS_PMT_DESC_ISO639_LANG = 0x0A;
    private static final int TS_PMT_DESC_AC3 = 0x6A;
    private static final int TS_PMT_DESC_EAC3 = 0x7A;
    private static final int TS_PMT_DESC_DTS = 0x7B;
    private static final int TS_PMT_DESC_DVBSUBS = 0x59;

    private final ParsableBitArray pmtScratch;
    private final SparseArray<TsPayloadReader> trackIdToReaderScratch;
    private final SparseIntArray trackIdToPidScratch;
    private final int pid;

    public PmtReader(int pid) {
      pmtScratch = new ParsableBitArray(new byte[5]);
      trackIdToReaderScratch = new SparseArray<>();
      trackIdToPidScratch = new SparseIntArray();
      this.pid = pid;
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
        TrackIdGenerator idGenerator) {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x02 /* TS_program_map_section */) {
        // See ISO/IEC 13818-1, section 2.4.4.4 for more information on table id assignment.
        return;
      }
      // TimestampAdjuster assignment.
      TimestampAdjuster timestampAdjuster;
      if (mode == MODE_SINGLE_PMT || mode == MODE_HLS || remainingPmts == 1) {
        timestampAdjuster = timestampAdjusters.get(0);
      } else {
        timestampAdjuster = new TimestampAdjuster(
            timestampAdjusters.get(0).getFirstSampleTimestampUs());
        timestampAdjusters.add(timestampAdjuster);
      }

      // section_syntax_indicator(1), '0'(1), reserved(2), section_length(12)
      sectionData.skipBytes(2);
      int programNumber = sectionData.readUnsignedShort();
      // reserved (2), version_number (5), current_next_indicator (1), section_number (8),
      // last_section_number (8), reserved (3), PCR_PID (13)
      sectionData.skipBytes(5);

      // Read program_info_length.
      sectionData.readBytes(pmtScratch, 2);
      pmtScratch.skipBits(4);
      int programInfoLength = pmtScratch.readBits(12);

      // Skip the descriptors.
      sectionData.skipBytes(programInfoLength);

      if (mode == MODE_HLS && id3Reader == null) {
        // Setup an ID3 track regardless of whether there's a corresponding entry, in case one
        // appears intermittently during playback. See [Internal: b/20261500].
        EsInfo dummyEsInfo = new EsInfo(TS_STREAM_TYPE_ID3, null, null, new byte[0]);
        id3Reader = payloadReaderFactory.createPayloadReader(TS_STREAM_TYPE_ID3, dummyEsInfo);
        id3Reader.init(timestampAdjuster, output,
            new TrackIdGenerator(programNumber, TS_STREAM_TYPE_ID3, MAX_PID_PLUS_ONE));
      }

      trackIdToReaderScratch.clear();
      trackIdToPidScratch.clear();
      int remainingEntriesLength = sectionData.bytesLeft();
      while (remainingEntriesLength > 0) {
        sectionData.readBytes(pmtScratch, 5);
        int streamType = pmtScratch.readBits(8);
        pmtScratch.skipBits(3); // reserved
        int elementaryPid = pmtScratch.readBits(13);
        pmtScratch.skipBits(4); // reserved
        int esInfoLength = pmtScratch.readBits(12); // ES_info_length.
        EsInfo esInfo = readEsInfo(sectionData, esInfoLength);
        if (streamType == 0x06) {
          streamType = esInfo.streamType;
        }
        remainingEntriesLength -= esInfoLength + 5;

        int trackId = mode == MODE_HLS ? streamType : elementaryPid;
        if (trackIds.get(trackId)) {
          continue;
        }

        TsPayloadReader reader = mode == MODE_HLS && streamType == TS_STREAM_TYPE_ID3 ? id3Reader
            : payloadReaderFactory.createPayloadReader(streamType, esInfo);
        if (mode != MODE_HLS
            || elementaryPid < trackIdToPidScratch.get(trackId, MAX_PID_PLUS_ONE)) {
          trackIdToPidScratch.put(trackId, elementaryPid);
          trackIdToReaderScratch.put(trackId, reader);
        }
      }

      int trackIdCount = trackIdToPidScratch.size();
      for (int i = 0; i < trackIdCount; i++) {
        int trackId = trackIdToPidScratch.keyAt(i);
        trackIds.put(trackId, true);
        TsPayloadReader reader = trackIdToReaderScratch.valueAt(i);
        if (reader != null) {
          if (reader != id3Reader) {
            reader.init(timestampAdjuster, output,
                new TrackIdGenerator(programNumber, trackId, MAX_PID_PLUS_ONE));
          }
          tsPayloadReaders.put(trackIdToPidScratch.valueAt(i), reader);
        }
      }

      if (mode == MODE_HLS) {
        if (!tracksEnded) {
          output.endTracks();
          remainingPmts = 0;
          tracksEnded = true;
        }
      } else {
        tsPayloadReaders.remove(pid);
        remainingPmts = mode == MODE_SINGLE_PMT ? 0 : remainingPmts - 1;
        if (remainingPmts == 0) {
          output.endTracks();
          tracksEnded = true;
        }
      }
    }

    /**
     * Returns the stream info read from the available descriptors. Sets {@code data}'s position to
     * the end of the descriptors.
     *
     * @param data A buffer with its position set to the start of the first descriptor.
     * @param length The length of descriptors to read from the current position in {@code data}.
     * @return The stream info read from the available descriptors.
     */
    private EsInfo readEsInfo(ParsableByteArray data, int length) {
      int descriptorsStartPosition = data.getPosition();
      int descriptorsEndPosition = descriptorsStartPosition + length;
      int streamType = -1;
      String language = null;
      List<DvbSubtitleInfo> dvbSubtitleInfos = null;
      while (data.getPosition() < descriptorsEndPosition) {
        int descriptorTag = data.readUnsignedByte();
        int descriptorLength = data.readUnsignedByte();
        int positionOfNextDescriptor = data.getPosition() + descriptorLength;
        if (descriptorTag == TS_PMT_DESC_REGISTRATION) { // registration_descriptor
          long formatIdentifier = data.readUnsignedInt();
          if (formatIdentifier == AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_AC3;
          } else if (formatIdentifier == E_AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_E_AC3;
          } else if (formatIdentifier == HEVC_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_H265;
          }
        } else if (descriptorTag == TS_PMT_DESC_AC3) { // AC-3_descriptor in DVB (ETSI EN 300 468)
          streamType = TS_STREAM_TYPE_AC3;
        } else if (descriptorTag == TS_PMT_DESC_EAC3) { // enhanced_AC-3_descriptor
          streamType = TS_STREAM_TYPE_E_AC3;
        } else if (descriptorTag == TS_PMT_DESC_DTS) { // DTS_descriptor
          streamType = TS_STREAM_TYPE_DTS;
        } else if (descriptorTag == TS_PMT_DESC_ISO639_LANG) {
          language = data.readString(3).trim();
          // Audio type is ignored.
        } else if (descriptorTag == TS_PMT_DESC_DVBSUBS) {
          streamType = TS_STREAM_TYPE_DVBSUBS;
          dvbSubtitleInfos = new ArrayList<>();
          while (data.getPosition() < positionOfNextDescriptor) {
            String dvbLanguage = data.readString(3).trim();
            int dvbSubtitlingType = data.readUnsignedByte();
            byte[] initializationData = new byte[4];
            data.readBytes(initializationData, 0, 4);
            dvbSubtitleInfos.add(new DvbSubtitleInfo(dvbLanguage, dvbSubtitlingType,
                initializationData));
          }
        }
        // Skip unused bytes of current descriptor.
        data.skipBytes(positionOfNextDescriptor - data.getPosition());
      }
      data.setPosition(descriptorsEndPosition);
      return new EsInfo(streamType, language, dvbSubtitleInfos,
          Arrays.copyOfRange(data.data, descriptorsStartPosition, descriptorsEndPosition));
    }

  }

}
