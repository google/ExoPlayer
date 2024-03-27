/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.muxer;

import static com.google.android.exoplayer2.muxer.AnnexBUtils.doesSampleContainAnnexBNalUnits;
import static com.google.android.exoplayer2.muxer.Boxes.BOX_HEADER_SIZE;
import static com.google.android.exoplayer2.muxer.Boxes.MFHD_BOX_CONTENT_SIZE;
import static com.google.android.exoplayer2.muxer.Boxes.TFHD_BOX_CONTENT_SIZE;
import static com.google.android.exoplayer2.muxer.Boxes.getTrunBoxContentSize;
import static com.google.android.exoplayer2.muxer.Mp4Utils.UNSIGNED_INT_MAX_VALUE;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.muxer.Muxer.TrackToken;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Writes media samples into multiple fragments as per the fragmented MP4 (ISO/IEC 14496-12)
 * standard.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class FragmentedMp4Writer {
  /** Provides a limited set of sample metadata. */
  public static class SampleMetadata {
    public final long durationVu;
    public final int size;
    public final int flags;

    public SampleMetadata(long durationsVu, int size, int flags) {
      this.durationVu = durationsVu;
      this.size = size;
      this.flags = flags;
    }
  }

  private final FileOutputStream outputStream;
  private final FileChannel output;
  private final Mp4MoovStructure moovGenerator;
  private final AnnexBToAvccConverter annexBToAvccConverter;
  private final List<Track> tracks;
  private final int fragmentDurationUs;

  private @MonotonicNonNull Track videoTrack;
  private int currentFragmentSequenceNumber;
  private boolean headerCreated;
  private long minInputPresentationTimeUs;
  private long maxTrackDurationUs;

  public FragmentedMp4Writer(
      FileOutputStream outputStream,
      Mp4MoovStructure moovGenerator,
      AnnexBToAvccConverter annexBToAvccConverter,
      int fragmentDurationUs) {
    this.outputStream = outputStream;
    this.output = outputStream.getChannel();
    this.moovGenerator = moovGenerator;
    this.annexBToAvccConverter = annexBToAvccConverter;
    tracks = new ArrayList<>();
    this.fragmentDurationUs = fragmentDurationUs;
    minInputPresentationTimeUs = Long.MAX_VALUE;
    currentFragmentSequenceNumber = 1;
  }

  public TrackToken addTrack(int sortKey, Format format) {
    Track track = new Track(format);
    tracks.add(track);
    if (MimeTypes.isVideo(format.sampleMimeType)) {
      videoTrack = track;
    }
    return track;
  }

  public void writeSampleData(
      TrackToken token, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
      throws IOException {
    checkArgument(token instanceof Track);
    if (!headerCreated) {
      createHeader();
      headerCreated = true;
    }
    Track track = (Track) token;
    if (shouldFlushPendingSamples(track, bufferInfo)) {
      createFragment();
    }
    track.writeSampleData(byteBuffer, bufferInfo);
    BufferInfo firstPendingSample = checkNotNull(track.pendingSamplesBufferInfo.peekFirst());
    BufferInfo lastPendingSample = checkNotNull(track.pendingSamplesBufferInfo.peekLast());
    minInputPresentationTimeUs =
        min(minInputPresentationTimeUs, firstPendingSample.presentationTimeUs);
    maxTrackDurationUs =
        max(
            maxTrackDurationUs,
            lastPendingSample.presentationTimeUs - firstPendingSample.presentationTimeUs);
  }

  public void close() throws IOException {
    try {
      createFragment();
    } finally {
      output.close();
      outputStream.close();
    }
  }

  private static ImmutableList<ByteBuffer> createTrafBoxes(
      List<ProcessedTrackInfo> trackInfos, long moofBoxStartPosition) {
    ImmutableList.Builder<ByteBuffer> trafBoxes = new ImmutableList.Builder<>();
    int moofBoxSize = calculateMoofBoxSize(trackInfos);
    int mdatBoxHeaderSize = BOX_HEADER_SIZE;
    // dataOffset denotes the relative position of the first sample of the track from the
    // moofBoxStartPosition.
    int dataOffset = moofBoxSize + mdatBoxHeaderSize;
    for (int i = 0; i < trackInfos.size(); i++) {
      ProcessedTrackInfo currentTrackInfo = trackInfos.get(i);
      trafBoxes.add(
          Boxes.traf(
              Boxes.tfhd(currentTrackInfo.trackId, /* baseDataOffset= */ moofBoxStartPosition),
              Boxes.trun(currentTrackInfo.pendingSamplesMetadata, dataOffset)));
      dataOffset += currentTrackInfo.totalSamplesSize;
    }
    return trafBoxes.build();
  }

  private static int calculateMoofBoxSize(List<ProcessedTrackInfo> trackInfos) {
    /* moof box looks like:
    moof
        mfhd
        traf
           tfhd
           trun
        traf
           tfhd
           trun
     */
    int moofBoxHeaderSize = BOX_HEADER_SIZE;
    int mfhdBoxSize = BOX_HEADER_SIZE + MFHD_BOX_CONTENT_SIZE;
    int trafBoxHeaderSize = BOX_HEADER_SIZE;
    int tfhdBoxSize = BOX_HEADER_SIZE + TFHD_BOX_CONTENT_SIZE;
    int trunBoxHeaderFixedSize = BOX_HEADER_SIZE;
    int trafBoxesSize = 0;
    for (int i = 0; i < trackInfos.size(); i++) {
      ProcessedTrackInfo trackInfo = trackInfos.get(i);
      int trunBoxSize =
          trunBoxHeaderFixedSize + getTrunBoxContentSize(trackInfo.pendingSamplesMetadata.size());
      trafBoxesSize += trafBoxHeaderSize + tfhdBoxSize + trunBoxSize;
    }

    return moofBoxHeaderSize + mfhdBoxSize + trafBoxesSize;
  }

  private void createHeader() throws IOException {
    output.position(0L);
    output.write(Boxes.ftyp());
    // The minInputPtsUs is actually ignored as there are no pending samples to write.
    output.write(
        moovGenerator.moovMetadataHeader(
            tracks, /* minInputPtsUs= */ 0L, /* isFragmentedMp4= */ true));
  }

  private boolean shouldFlushPendingSamples(
      Track track, MediaCodec.BufferInfo nextSampleBufferInfo) {
    // If video track is present then fragment will be created based on group of pictures and
    // track's duration so far.
    if (videoTrack != null) {
      // Video samples can be written only when complete group of pictures are present.
      if (track.equals(videoTrack)
          && track.hadKeyframe
          && ((nextSampleBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0)) {
        BufferInfo firstPendingSample = checkNotNull(track.pendingSamplesBufferInfo.peekFirst());
        BufferInfo lastPendingSample = checkNotNull(track.pendingSamplesBufferInfo.peekLast());
        return lastPendingSample.presentationTimeUs - firstPendingSample.presentationTimeUs
            >= fragmentDurationUs;
      }
      return false;
    } else {
      return maxTrackDurationUs >= fragmentDurationUs;
    }
  }

  private void createFragment() throws IOException {
    /* Each fragment looks like:
    moof
        mfhd
        traf
           tfhd
           trun
        traf
           tfhd
           trun
     mdat
     */
    ImmutableList<ProcessedTrackInfo> trackInfos = processAllTracks();
    ImmutableList<ByteBuffer> trafBoxes =
        createTrafBoxes(trackInfos, /* moofBoxStartPosition= */ output.position());
    if (trafBoxes.isEmpty()) {
      return;
    }
    output.write(Boxes.moof(Boxes.mfhd(currentFragmentSequenceNumber), trafBoxes));

    writeMdatBox(trackInfos);

    currentFragmentSequenceNumber++;
  }

  private void writeMdatBox(List<ProcessedTrackInfo> trackInfos) throws IOException {
    long mdatStartPosition = output.position();
    int mdatHeaderSize = 8; // 4 bytes (box size) + 4 bytes (box name)
    ByteBuffer header = ByteBuffer.allocate(mdatHeaderSize);
    header.putInt(mdatHeaderSize); // The total box size so far.
    header.put(Util.getUtf8Bytes("mdat"));
    header.flip();
    output.write(header);

    long bytesWritten = 0;
    for (int trackInfoIndex = 0; trackInfoIndex < trackInfos.size(); trackInfoIndex++) {
      ProcessedTrackInfo currentTrackInfo = trackInfos.get(trackInfoIndex);
      for (int sampleIndex = 0;
          sampleIndex < currentTrackInfo.pendingSamplesByteBuffer.size();
          sampleIndex++) {
        bytesWritten += output.write(currentTrackInfo.pendingSamplesByteBuffer.get(sampleIndex));
      }
    }

    long currentPosition = output.position();

    output.position(mdatStartPosition);
    ByteBuffer mdatSizeByteBuffer = ByteBuffer.allocate(4);
    long mdatSize = bytesWritten + mdatHeaderSize;
    checkArgument(
        mdatSize <= UNSIGNED_INT_MAX_VALUE,
        "Only 32-bit long mdat size supported in the fragmented MP4");
    mdatSizeByteBuffer.putInt((int) mdatSize);
    mdatSizeByteBuffer.flip();
    output.write(mdatSizeByteBuffer);
    output.position(currentPosition);
  }

  private ImmutableList<ProcessedTrackInfo> processAllTracks() {
    ImmutableList.Builder<ProcessedTrackInfo> trackInfos = new ImmutableList.Builder<>();
    for (int i = 0; i < tracks.size(); i++) {
      if (!tracks.get(i).pendingSamplesBufferInfo.isEmpty()) {
        trackInfos.add(processTrack(/* trackId= */ i + 1, tracks.get(i)));
      }
    }
    return trackInfos.build();
  }

  private ProcessedTrackInfo processTrack(int trackId, Track track) {
    checkState(track.pendingSamplesByteBuffer.size() == track.pendingSamplesBufferInfo.size());

    ImmutableList.Builder<ByteBuffer> pendingSamplesByteBuffer = new ImmutableList.Builder<>();
    ImmutableList.Builder<BufferInfo> pendingSamplesBufferInfoBuilder =
        new ImmutableList.Builder<>();
    if (doesSampleContainAnnexBNalUnits(checkNotNull(track.format.sampleMimeType))) {
      while (!track.pendingSamplesByteBuffer.isEmpty()) {
        ByteBuffer currentSampleByteBuffer = track.pendingSamplesByteBuffer.removeFirst();
        currentSampleByteBuffer = annexBToAvccConverter.process(currentSampleByteBuffer);
        pendingSamplesByteBuffer.add(currentSampleByteBuffer);
        BufferInfo currentSampleBufferInfo = track.pendingSamplesBufferInfo.removeFirst();
        currentSampleBufferInfo.set(
            currentSampleByteBuffer.position(),
            currentSampleByteBuffer.remaining(),
            currentSampleBufferInfo.presentationTimeUs,
            currentSampleBufferInfo.flags);
        pendingSamplesBufferInfoBuilder.add(currentSampleBufferInfo);
      }
    } else {
      pendingSamplesByteBuffer.addAll(track.pendingSamplesByteBuffer);
      track.pendingSamplesByteBuffer.clear();
      pendingSamplesBufferInfoBuilder.addAll(track.pendingSamplesBufferInfo);
      track.pendingSamplesBufferInfo.clear();
    }

    ImmutableList<BufferInfo> pendingSamplesBufferInfo = pendingSamplesBufferInfoBuilder.build();
    List<Long> sampleDurations =
        Boxes.convertPresentationTimestampsToDurationsVu(
            pendingSamplesBufferInfo,
            /* firstSamplePresentationTimeUs= */ currentFragmentSequenceNumber == 1
                ? minInputPresentationTimeUs
                : pendingSamplesBufferInfo.get(0).presentationTimeUs,
            track.videoUnitTimebase(),
            Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION);

    ImmutableList.Builder<SampleMetadata> pendingSamplesMetadata = new ImmutableList.Builder<>();
    int totalSamplesSize = 0;
    for (int i = 0; i < pendingSamplesBufferInfo.size(); i++) {
      totalSamplesSize += pendingSamplesBufferInfo.get(i).size;
      pendingSamplesMetadata.add(
          new SampleMetadata(
              sampleDurations.get(i),
              pendingSamplesBufferInfo.get(i).size,
              pendingSamplesBufferInfo.get(i).flags));
    }

    return new ProcessedTrackInfo(
        trackId,
        totalSamplesSize,
        pendingSamplesByteBuffer.build(),
        pendingSamplesMetadata.build());
  }

  private static class ProcessedTrackInfo {
    public final int trackId;
    public final int totalSamplesSize;
    public final ImmutableList<ByteBuffer> pendingSamplesByteBuffer;
    public final ImmutableList<SampleMetadata> pendingSamplesMetadata;

    public ProcessedTrackInfo(
        int trackId,
        int totalSamplesSize,
        ImmutableList<ByteBuffer> pendingSamplesByteBuffer,
        ImmutableList<SampleMetadata> pendingSamplesMetadata) {
      this.trackId = trackId;
      this.totalSamplesSize = totalSamplesSize;
      this.pendingSamplesByteBuffer = pendingSamplesByteBuffer;
      this.pendingSamplesMetadata = pendingSamplesMetadata;
    }
  }
}
