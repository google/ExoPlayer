/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.muxer.Mp4Muxer.TrackToken;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes MP4 data to the disk.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class Mp4Writer {
  private static final long INTERLEAVE_DURATION_US = 1_000_000L;

  private final AtomicBoolean hasWrittenSamples;
  private final Mp4MoovStructure moovGenerator;
  private final List<Track> tracks;
  private final AnnexBToAvccConverter annexBToAvccConverter;
  private final FileOutputStream outputStream;
  private final FileChannel output;
  private long mdatStart;
  private long mdatEnd;
  private long mdatDataEnd; // Always <= mdatEnd

  // Typically written from the end of the mdat box to the end of the file.
  private Range<Long> lastMoovWritten;

  /**
   * Creates an instance.
   *
   * @param outputStream The {@link FileOutputStream} to write the data to.
   * @param moovGenerator An {@link Mp4MoovStructure} instance to generate the moov box.
   * @param annexBToAvccConverter The {@link AnnexBToAvccConverter} to be used to convert H.264 and
   *     H.265 NAL units from the Annex-B format (using start codes to delineate NAL units) to the
   *     AVCC format (which uses length prefixes).
   */
  public Mp4Writer(
      FileOutputStream outputStream,
      Mp4MoovStructure moovGenerator,
      AnnexBToAvccConverter annexBToAvccConverter) {
    this.moovGenerator = moovGenerator;
    this.outputStream = outputStream;
    this.output = outputStream.getChannel();
    this.annexBToAvccConverter = annexBToAvccConverter;
    hasWrittenSamples = new AtomicBoolean(false);
    tracks = new ArrayList<>();
    lastMoovWritten = Range.closed(0L, 0L);
  }

  public TrackToken addTrack(int sortKey, Format format) {
    Track track = new Track(format, sortKey);
    tracks.add(track);
    Collections.sort(tracks, (a, b) -> Integer.compare(a.sortKey, b.sortKey));
    return track;
  }

  public void writeSampleData(TrackToken token, ByteBuffer byteBuf, BufferInfo bufferInfo)
      throws IOException {
    checkState(token instanceof Track);
    ((Track) token).writeSampleData(byteBuf, bufferInfo);
  }

  public void close() throws IOException {
    try {
      for (int i = 0; i < tracks.size(); i++) {
        flushPending(tracks.get(i));
      }

      // Leave the file empty if no samples are written.
      if (hasWrittenSamples.get()) {
        writeMoovAndTrim();
      }
    } finally {
      output.close();
      outputStream.close();
    }
  }

  private void writeHeader() throws IOException {
    output.position(0L);
    output.write(Boxes.ftyp());

    // Start with an empty mdat box.
    mdatStart = output.position();

    ByteBuffer header = ByteBuffer.allocate(4 + 4 + 8);
    header.putInt(1); // 4 bytes, indicating a 64-bit length field
    header.put(Util.getUtf8Bytes("mdat")); // 4 bytes
    header.putLong(16); // 8 bytes (the actual length)
    header.flip();
    output.write(header);

    // The box includes only its type and length.
    mdatDataEnd = mdatStart + 16;
    mdatEnd = mdatDataEnd;
  }

  private ByteBuffer assembleCurrentMoovData() {
    long minInputPtsUs = Long.MAX_VALUE;

    // Recalculate the min timestamp every time, in case some new samples have smaller timestamps.
    for (int i = 0; i < tracks.size(); i++) {
      Track track = tracks.get(i);
      if (!track.writtenSamples.isEmpty()) {
        minInputPtsUs = Math.min(track.writtenSamples.get(0).presentationTimeUs, minInputPtsUs);
      }
    }

    ByteBuffer moovHeader;
    if (minInputPtsUs != Long.MAX_VALUE) {
      moovHeader = moovGenerator.moovMetadataHeader(tracks, minInputPtsUs);
    } else {
      // Skip moov box, if there are no samples.
      moovHeader = ByteBuffer.allocate(0);
    }

    return moovHeader;
  }

  /**
   * Replaces old moov box with the new one.
   *
   * <p>It doesn't really replace the existing moov box, rather it adds a new moov box at the end of
   * the file. Even if this operation fails, the output MP4 file still has a valid moov box.
   *
   * <p>After this operation, the mdat box might have some extra space containing garbage value of
   * the old moov box. This extra space gets trimmed before closing the file (in {@link
   * #writeMoovAndTrim()}).
   *
   * @param newMoovBoxPosition The new position for the moov box.
   * @param newMoovBoxData The new moov box data.
   * @throws IOException If there is any error while writing data to the disk.
   */
  private void safelyReplaceMoov(long newMoovBoxPosition, ByteBuffer newMoovBoxData)
      throws IOException {
    checkState(newMoovBoxPosition >= lastMoovWritten.upperEndpoint());
    checkState(newMoovBoxPosition >= mdatEnd);

    // Write a free box to the end of the file, with the new moov box wrapped into it.
    output.position(newMoovBoxPosition);
    output.write(BoxUtils.wrapIntoBox("free", newMoovBoxData.duplicate()));

    // The current state is:
    // | ftyp | mdat .. .. .. | previous moov | free (new moov)|

    // Increase the length of the mdat box so that it now extends to
    // the previous moov box and the header of the free box.
    mdatEnd = newMoovBoxPosition + 8;
    updateMdatSize();

    lastMoovWritten =
        Range.closed(newMoovBoxPosition, newMoovBoxPosition + newMoovBoxData.remaining());
  }

  /**
   * Writes the final moov box and trims extra space from the mdat box.
   *
   * <p>This is done right before closing the file.
   *
   * @throws IOException If there is any error while writing data to the disk.
   */
  private void writeMoovAndTrim() throws IOException {
    // The current state is:
    // | ftyp | mdat .. .. .. (00 00 00) | moov |

    // To keep the trimming safe, first write the final moov box into the gap at the end of the mdat
    // box, and only then trim the extra space.
    ByteBuffer currentMoovData = assembleCurrentMoovData();

    int moovBytesNeeded = currentMoovData.remaining();

    // Write a temporary free box wrapping the new moov box.
    int moovAndFreeBytesNeeded = moovBytesNeeded + 8;

    if (mdatEnd - mdatDataEnd < moovAndFreeBytesNeeded) {
      // If the gap is not big enough for the moov box, then extend the mdat box once again. This
      // involves writing moov box farther away one more time.
      safelyReplaceMoov(lastMoovWritten.upperEndpoint() + moovAndFreeBytesNeeded, currentMoovData);
      checkState(mdatEnd - mdatDataEnd >= moovAndFreeBytesNeeded);
    }

    // Write out the new moov box into the gap.
    long newMoovLocation = mdatDataEnd;
    output.position(mdatDataEnd);
    output.write(currentMoovData);

    // Add a free box to account for the actual remaining length of the file.
    long remainingLength = lastMoovWritten.upperEndpoint() - (newMoovLocation + moovBytesNeeded);

    // Moov boxes shouldn't be too long; they can fit into a free box with a 32-bit length field.
    checkState(remainingLength < Integer.MAX_VALUE);

    ByteBuffer freeHeader = ByteBuffer.allocate(4 + 4);
    freeHeader.putInt((int) remainingLength);
    freeHeader.put((byte) 'f');
    freeHeader.put((byte) 'r');
    freeHeader.put((byte) 'e');
    freeHeader.put((byte) 'e');
    freeHeader.flip();
    output.write(freeHeader);

    // The moov box is actually written inside mdat box so the current state is:
    // | ftyp | mdat .. .. .. (new moov) (free header ) (00 00 00) | old moov |

    // Now change this to:
    // | ftyp | mdat .. .. .. | new moov | free (00 00 00) (old moov) |
    mdatEnd = newMoovLocation;
    updateMdatSize();
    lastMoovWritten = Range.closed(newMoovLocation, newMoovLocation + currentMoovData.limit());

    // Remove the free box.
    output.truncate(newMoovLocation + moovBytesNeeded);
  }

  /**
   * Rewrites the moov box after accommodating extra bytes needed for the mdat box.
   *
   * @param bytesNeeded The extra bytes needed for the mdat box.
   * @throws IOException If there is any error while writing data to the disk.
   */
  private void rewriteMoovWithMdatEmptySpace(long bytesNeeded) throws IOException {
    long newMoovStart = Math.max(mdatEnd + bytesNeeded, lastMoovWritten.upperEndpoint());

    ByteBuffer currentMoovData = assembleCurrentMoovData();

    safelyReplaceMoov(newMoovStart, currentMoovData);
  }

  /** Writes out any pending samples to the file. */
  private void flushPending(Track track) throws IOException {
    if (track.pendingSamples.isEmpty()) {
      return;
    }

    if (!hasWrittenSamples.getAndSet(true)) {
      writeHeader();
    }

    // Calculate the additional space required.
    long bytesNeededInMdat = 0L;
    for (Pair<BufferInfo, ByteBuffer> sample : track.pendingSamples) {
      bytesNeededInMdat += sample.second.limit();
    }

    // If the required number of bytes doesn't fit in the gap between the actual data and the moov
    // box, extend the file and write out the moov box to the end again.
    if (mdatDataEnd + bytesNeededInMdat >= mdatEnd) {
      // Reserve some extra space than required, so that mdat box extension is less frequent.
      rewriteMoovWithMdatEmptySpace(
          /* bytesNeeded= */ getMdatExtensionAmount(mdatDataEnd) + bytesNeededInMdat);
    }

    track.writtenChunkOffsets.add(mdatDataEnd);
    track.writtenChunkSampleCounts.add(track.pendingSamples.size());

    do {
      Pair<BufferInfo, ByteBuffer> pendingPacket = track.pendingSamples.removeFirst();
      BufferInfo info = pendingPacket.first;
      ByteBuffer buffer = pendingPacket.second;

      track.writtenSamples.add(info);

      // Convert the H.264/H.265 samples from Annex-B format (output by MediaCodec) to
      // Avcc format (required by MP4 container).
      if (MimeTypes.isVideo(track.format.sampleMimeType)) {
        annexBToAvccConverter.process(buffer);
      }

      buffer.rewind();

      mdatDataEnd += output.write(buffer, mdatDataEnd);
    } while (!track.pendingSamples.isEmpty());

    checkState(mdatDataEnd <= mdatEnd);
  }

  private void updateMdatSize() throws IOException {
    // Assuming that the mdat box has a 64-bit length, skip the box type (4 bytes) and
    // the 32-bit box length field (4 bytes).
    output.position(mdatStart + 8);

    ByteBuffer mdatSize = ByteBuffer.allocate(8); // one long
    mdatSize.putLong(mdatEnd - mdatStart);
    mdatSize.flip();
    output.write(mdatSize);
  }

  private void doInterleave() throws IOException {
    for (int i = 0; i < tracks.size(); i++) {
      Track track = tracks.get(i);
      // TODO: b/270583563 - check if we need to consider the global timestamp instead.
      if (track.pendingSamples.size() > 2) {
        BufferInfo firstSampleInfo = checkNotNull(track.pendingSamples.peekFirst()).first;
        BufferInfo lastSampleInfo = checkNotNull(track.pendingSamples.peekLast()).first;

        if (lastSampleInfo.presentationTimeUs - firstSampleInfo.presentationTimeUs
            > INTERLEAVE_DURATION_US) {
          flushPending(track);
        }
      }
    }
  }

  /**
   * Returns the number of bytes by which to extend the mdat box.
   *
   * @param currentFileLength The length of current file in bytes (except moov box).
   * @return The mdat box extension amount in bytes.
   */
  private long getMdatExtensionAmount(long currentFileLength) {
    long minBytesToExtend = 500_000L;
    float extensionRatio = 0.2f;
    return max(minBytesToExtend, (long) (extensionRatio * currentFileLength));
  }

  private class Track implements TrackToken, Mp4MoovStructure.TrackMetadataProvider {
    private final Format format;
    private final int sortKey;
    private final List<BufferInfo> writtenSamples;
    private final List<Long> writtenChunkOffsets;
    private final List<Integer> writtenChunkSampleCounts;
    private final Deque<Pair<BufferInfo, ByteBuffer>> pendingSamples;

    private boolean hadKeyframe = false;

    private Track(Format format, int sortKey) {
      this.format = format;
      this.sortKey = sortKey;
      writtenSamples = new ArrayList<>();
      writtenChunkOffsets = new ArrayList<>();
      writtenChunkSampleCounts = new ArrayList<>();
      pendingSamples = new ArrayDeque<>();
    }

    public void writeSampleData(ByteBuffer byteBuffer, BufferInfo bufferInfo) throws IOException {
      if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0) {
        hadKeyframe = true;
      }

      if (!hadKeyframe && MimeTypes.isVideo(format.sampleMimeType)) {
        return;
      }

      if (bufferInfo.size == 0) {
        return;
      }

      // Skip empty samples.
      // TODO: b/279931840 - Confirm whether muxer should throw when writing empty samples.
      if (byteBuffer.remaining() > 0) {
        // Copy sample data and release the original buffer.
        ByteBuffer byteBufferCopy = ByteBuffer.allocateDirect(byteBuffer.remaining());
        byteBufferCopy.put(byteBuffer);
        byteBufferCopy.rewind();

        BufferInfo bufferInfoCopy = new BufferInfo();
        bufferInfoCopy.set(
            /* newOffset= */ byteBufferCopy.position(),
            /* newSize= */ byteBufferCopy.remaining(),
            bufferInfo.presentationTimeUs,
            bufferInfo.flags);

        pendingSamples.addLast(Pair.create(bufferInfoCopy, byteBufferCopy));
        doInterleave();
      }
    }

    @Override
    public int videoUnitTimebase() {
      return MimeTypes.isAudio(format.sampleMimeType)
          ? 48_000 // TODO: b/270583563 - Update these with actual values from mediaFormat.
          : 90_000;
    }

    @Override
    public int sortKey() {
      return sortKey;
    }

    @Override
    public ImmutableList<BufferInfo> writtenSamples() {
      return ImmutableList.copyOf(writtenSamples);
    }

    @Override
    public ImmutableList<Long> writtenChunkOffsets() {
      return ImmutableList.copyOf(writtenChunkOffsets);
    }

    @Override
    public ImmutableList<Integer> writtenChunkSampleCounts() {
      return ImmutableList.copyOf(writtenChunkSampleCounts);
    }

    @Override
    public Format format() {
      return format;
    }
  }
}
