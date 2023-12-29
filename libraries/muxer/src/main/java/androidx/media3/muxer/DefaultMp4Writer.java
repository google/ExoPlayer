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
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.muxer.Mp4Muxer.TrackToken;
import com.google.common.collect.Range;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default implementation of {@link Mp4Writer} which writes all the samples in a single mdat
 * box.
 */
/* package */ final class DefaultMp4Writer extends Mp4Writer {
  private static final long INTERLEAVE_DURATION_US = 1_000_000L;

  private final AtomicBoolean hasWrittenSamples;

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
  public DefaultMp4Writer(
      FileOutputStream outputStream,
      Mp4MoovStructure moovGenerator,
      AnnexBToAvccConverter annexBToAvccConverter) {
    super(outputStream, moovGenerator, annexBToAvccConverter);
    hasWrittenSamples = new AtomicBoolean(false);
    lastMoovWritten = Range.closed(0L, 0L);
  }

  @Override
  public TrackToken addTrack(int sortKey, Format format) {
    Track track = new Track(format, sortKey);
    tracks.add(track);
    Collections.sort(tracks, (a, b) -> Integer.compare(a.sortKey, b.sortKey));
    return track;
  }

  @Override
  public void writeSampleData(TrackToken token, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws IOException {
    checkArgument(token instanceof Track);
    ((Track) token).writeSampleData(byteBuffer, bufferInfo);
    doInterleave();
  }

  @Override
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
      moovHeader =
          moovGenerator.moovMetadataHeader(tracks, minInputPtsUs, /* isFragmentedMp4= */ false);
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
    if (track.pendingSamplesBufferInfo.isEmpty()) {
      return;
    }

    if (!hasWrittenSamples.getAndSet(true)) {
      writeHeader();
    }

    // Calculate the additional space required.
    long bytesNeededInMdat = 0L;
    for (ByteBuffer sample : track.pendingSamplesByteBuffer) {
      bytesNeededInMdat += sample.limit();
    }

    // If the required number of bytes doesn't fit in the gap between the actual data and the moov
    // box, extend the file and write out the moov box to the end again.
    if (mdatDataEnd + bytesNeededInMdat >= mdatEnd) {
      // Reserve some extra space than required, so that mdat box extension is less frequent.
      rewriteMoovWithMdatEmptySpace(
          /* bytesNeeded= */ getMdatExtensionAmount(mdatDataEnd) + bytesNeededInMdat);
    }

    track.writtenChunkOffsets.add(mdatDataEnd);
    track.writtenChunkSampleCounts.add(track.pendingSamplesBufferInfo.size());

    do {
      BufferInfo currentSampleBufferInfo = track.pendingSamplesBufferInfo.removeFirst();
      ByteBuffer currentSampleByteBuffer = track.pendingSamplesByteBuffer.removeFirst();

      track.writtenSamples.add(currentSampleBufferInfo);

      // Convert the H.264/H.265 samples from Annex-B format (output by MediaCodec) to
      // Avcc format (required by MP4 container).
      if (MimeTypes.isVideo(track.format.sampleMimeType)) {
        annexBToAvccConverter.process(currentSampleByteBuffer);
      }

      currentSampleByteBuffer.rewind();

      mdatDataEnd += output.write(currentSampleByteBuffer, mdatDataEnd);
    } while (!track.pendingSamplesBufferInfo.isEmpty());

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
      // TODO: b/270583563 - Check if we need to consider the global timestamp instead.
      if (track.pendingSamplesBufferInfo.size() > 2) {
        BufferInfo firstSampleInfo = checkNotNull(track.pendingSamplesBufferInfo.peekFirst());
        BufferInfo lastSampleInfo = checkNotNull(track.pendingSamplesBufferInfo.peekLast());

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
    // Don't extend by more than 1 GB at a time because the final trimming creates a "free" box that
    // can be as big as this extension + the old "moov" box, but should be less than 2**31 - 1 bytes
    // (because it is a compact "free" box and for simplicity its size is written as a signed
    // integer). Therefore, to be conservative, a max extension of 1 GB was chosen.
    long minBytesToExtend = 500_000L;
    long maxBytesToExtend = 1_000_000_000L;
    float extensionRatio = 0.2f;

    return min(
        maxBytesToExtend, max(minBytesToExtend, (long) (extensionRatio * currentFileLength)));
  }
}
