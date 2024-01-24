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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.muxer.Boxes.BOX_HEADER_SIZE;
import static androidx.media3.muxer.Boxes.MFHD_BOX_CONTENT_SIZE;
import static androidx.media3.muxer.Boxes.TFHD_BOX_CONTENT_SIZE;
import static androidx.media3.muxer.Boxes.getTrunBoxContentSize;
import static androidx.media3.muxer.Mp4Utils.UNSIGNED_INT_MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.muxer.Mp4Muxer.TrackToken;
import com.google.common.collect.ImmutableList;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An {@link Mp4Writer} implementation which writes samples into multiple fragments as per the
 * fragmented MP4 (ISO/IEC 14496-12) standard.
 */
/* package */ final class FragmentedMp4Writer extends Mp4Writer {
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
    super(outputStream, moovGenerator, annexBToAvccConverter);
    this.fragmentDurationUs = fragmentDurationUs;
    minInputPresentationTimeUs = Long.MAX_VALUE;
    currentFragmentSequenceNumber = 1;
  }

  @Override
  public TrackToken addTrack(int sortKey, Format format) {
    Track track = new Track(format);
    tracks.add(track);
    if (MimeTypes.isVideo(format.sampleMimeType)) {
      videoTrack = track;
    }
    return track;
  }

  @Override
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

  @Override
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
    // TODO: b/262704382 - Add some free space in the moov box to fit any newly added metadata and
    //  write moov box again in the close() method.
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

    writeMdatBox();

    currentFragmentSequenceNumber++;
  }

  private void writeMdatBox() throws IOException {
    long mdatStartPosition = output.position();
    int mdatHeaderSize = 8; // 4 bytes (box size) + 4 bytes (box name)
    ByteBuffer header = ByteBuffer.allocate(mdatHeaderSize);
    header.putInt(mdatHeaderSize); // The total box size so far.
    header.put(Util.getUtf8Bytes("mdat"));
    header.flip();
    output.write(header);

    long bytesWritten = 0;
    for (int i = 0; i < tracks.size(); i++) {
      Track currentTrack = tracks.get(i);
      while (!currentTrack.pendingSamplesByteBuffer.isEmpty()) {
        ByteBuffer currentSampleByteBuffer = currentTrack.pendingSamplesByteBuffer.removeFirst();

        // Convert the H.264/H.265 samples from Annex-B format (output by MediaCodec) to
        // Avcc format (required by MP4 container).
        if (MimeTypes.isVideo(currentTrack.format.sampleMimeType)) {
          annexBToAvccConverter.process(currentSampleByteBuffer);
        }
        bytesWritten += output.write(currentSampleByteBuffer);
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
    List<BufferInfo> sampleBufferInfos = new ArrayList<>(track.pendingSamplesBufferInfo);

    List<Long> sampleDurations =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            /* firstSamplePresentationTimeUs= */ currentFragmentSequenceNumber == 1
                ? minInputPresentationTimeUs
                : sampleBufferInfos.get(0).presentationTimeUs,
            track.videoUnitTimebase(),
            Mp4Muxer.LAST_FRAME_DURATION_BEHAVIOR_DUPLICATE_PREV_DURATION);

    ImmutableList.Builder<SampleMetadata> pendingSamplesMetadata = new ImmutableList.Builder<>();
    int totalSamplesSize = 0;
    for (int i = 0; i < sampleBufferInfos.size(); i++) {
      totalSamplesSize += sampleBufferInfos.get(i).size;
      pendingSamplesMetadata.add(
          new SampleMetadata(
              sampleDurations.get(i),
              sampleBufferInfos.get(i).size,
              sampleBufferInfos.get(i).flags));
    }

    // Clear the queue.
    track.pendingSamplesBufferInfo.clear();
    return new ProcessedTrackInfo(trackId, totalSamplesSize, pendingSamplesMetadata.build());
  }

  private static class ProcessedTrackInfo {
    public final int trackId;
    public final int totalSamplesSize;
    public final ImmutableList<SampleMetadata> pendingSamplesMetadata;

    public ProcessedTrackInfo(
        int trackId, int totalSamplesSize, ImmutableList<SampleMetadata> pendingSamplesMetadata) {
      this.trackId = trackId;
      this.totalSamplesSize = totalSamplesSize;
      this.pendingSamplesMetadata = pendingSamplesMetadata;
    }
  }
}
