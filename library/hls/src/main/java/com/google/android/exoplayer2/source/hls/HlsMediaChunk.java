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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HLS {@link MediaChunk}.
 */
/* package */ final class HlsMediaChunk extends MediaChunk {

  /**
   * Creates a new instance.
   *
   * @param extractorFactory A {@link HlsExtractorFactory} from which the HLS media chunk extractor
   *     is obtained.
   * @param dataSource The source from which the data should be loaded.
   * @param startOfPlaylistInPeriodUs The position of the playlist in the period in microseconds.
   * @param mediaPlaylist The media playlist from which this chunk was obtained.
   * @param hlsUrl The url of the playlist from which this chunk was obtained.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjusterProvider The provider from which to obtain the {@link
   *     TimestampAdjuster}.
   * @param previousChunk The {@link HlsMediaChunk} that preceded this one. May be null.
   * @param fullSegmentEncryptionKey The key to decrypt the full segment, or null if the segment is
   *     not fully encrypted.
   * @param encryptionIv The AES initialization vector, or null if the segment is not fully
   *     encrypted.
   */
  public static HlsMediaChunk createInstance(
      HlsExtractorFactory extractorFactory,
      DataSource dataSource,
      long startOfPlaylistInPeriodUs,
      HlsMediaPlaylist mediaPlaylist,
      int segmentIndexInPlaylist,
      HlsUrl hlsUrl,
      List<Format> muxedCaptionFormats,
      int trackSelectionReason,
      Object trackSelectionData,
      boolean isMasterTimestampSource,
      TimestampAdjusterProvider timestampAdjusterProvider,
      HlsMediaChunk previousChunk,
      byte[] fullSegmentEncryptionKey,
      byte[] encryptionIv) {

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(segmentIndexInPlaylist);
    DataSpec dataSpec =
        new DataSpec(
            UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url),
            segment.byterangeOffset,
            segment.byterangeLength,
            /* key= */ null);

    DataSpec initDataSpec = null;
    HlsMediaPlaylist.Segment initSegment = segment.initializationSegment;
    if (initSegment != null) {
      Uri initSegmentUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, initSegment.url);
      initDataSpec =
          new DataSpec(
              initSegmentUri,
              initSegment.byterangeOffset,
              initSegment.byterangeLength,
              /* key= */ null);
    }

    long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + segment.relativeStartTimeUs;
    long segmentEndTimeInPeriodUs = segmentStartTimeInPeriodUs + segment.durationUs;
    int discontinuitySequenceNumber =
        mediaPlaylist.discontinuitySequence + segment.relativeDiscontinuitySequence;

    Extractor previousExtractor = null;
    Id3Decoder id3Decoder;
    ParsableByteArray scratchId3Data;
    boolean shouldSpliceIn;
    if (previousChunk != null) {
      id3Decoder = previousChunk.id3Decoder;
      scratchId3Data = previousChunk.scratchId3Data;
      shouldSpliceIn = previousChunk.hlsUrl != hlsUrl || !previousChunk.loadCompleted;
      previousExtractor =
          previousChunk.discontinuitySequenceNumber != discontinuitySequenceNumber || shouldSpliceIn
              ? null
              : previousChunk.extractor;
    } else {
      id3Decoder = new Id3Decoder();
      scratchId3Data = new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH);
      shouldSpliceIn = false;
    }

    return new HlsMediaChunk(
        extractorFactory,
        dataSource,
        dataSpec,
        initDataSpec,
        hlsUrl,
        muxedCaptionFormats,
        trackSelectionReason,
        trackSelectionData,
        segmentStartTimeInPeriodUs,
        segmentEndTimeInPeriodUs,
        /* chunkMediaSequence= */ mediaPlaylist.mediaSequence + segmentIndexInPlaylist,
        discontinuitySequenceNumber,
        segment.hasGapTag,
        isMasterTimestampSource,
        /* timestampAdjuster= */ timestampAdjusterProvider.getAdjuster(discontinuitySequenceNumber),
        segment.drmInitData,
        previousExtractor,
        id3Decoder,
        scratchId3Data,
        shouldSpliceIn,
        fullSegmentEncryptionKey,
        encryptionIv);
  }

  public static final String PRIV_TIMESTAMP_FRAME_OWNER =
      "com.apple.streaming.transportStreamTimestamp";

  private static final AtomicInteger uidSource = new AtomicInteger();

  /**
   * A unique identifier for the chunk.
   */
  public final int uid;

  /**
   * The discontinuity sequence number of the chunk.
   */
  public final int discontinuitySequenceNumber;

  /**
   * The url of the playlist from which this chunk was obtained.
   */
  public final HlsUrl hlsUrl;

  private final DataSource initDataSource;
  private final DataSpec initDataSpec;
  private final boolean isEncrypted;
  private final boolean isMasterTimestampSource;
  private final boolean hasGapTag;
  private final TimestampAdjuster timestampAdjuster;
  private final boolean shouldSpliceIn;
  private final HlsExtractorFactory extractorFactory;
  private final List<Format> muxedCaptionFormats;
  private final DrmInitData drmInitData;
  private final Extractor previousExtractor;
  private final Id3Decoder id3Decoder;
  private final ParsableByteArray scratchId3Data;

  private Extractor extractor;
  private HlsSampleStreamWrapper output;
  private int initSegmentBytesLoaded;
  private int nextLoadPosition;
  private boolean initLoadCompleted;
  private volatile boolean loadCanceled;
  private boolean loadCompleted;

  private HlsMediaChunk(
      HlsExtractorFactory extractorFactory,
      DataSource dataSource,
      DataSpec dataSpec,
      DataSpec initDataSpec,
      HlsUrl hlsUrl,
      List<Format> muxedCaptionFormats,
      int trackSelectionReason,
      Object trackSelectionData,
      long startTimeUs,
      long endTimeUs,
      long chunkMediaSequence,
      int discontinuitySequenceNumber,
      boolean hasGapTag,
      boolean isMasterTimestampSource,
      TimestampAdjuster timestampAdjuster,
      DrmInitData drmInitData,
      Extractor previousExtractor,
      Id3Decoder id3Decoder,
      ParsableByteArray scratchId3Data,
      boolean shouldSpliceIn,
      byte[] fullSegmentEncryptionKey,
      byte[] encryptionIv) {
    super(
        buildDataSource(dataSource, fullSegmentEncryptionKey, encryptionIv),
        dataSpec,
        hlsUrl.format,
        trackSelectionReason,
        trackSelectionData,
        startTimeUs,
        endTimeUs,
        chunkMediaSequence);
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.initDataSpec = initDataSpec;
    this.hlsUrl = hlsUrl;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.isEncrypted = fullSegmentEncryptionKey != null;
    this.hasGapTag = hasGapTag;
    this.extractorFactory = extractorFactory;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.drmInitData = drmInitData;
    this.previousExtractor = previousExtractor;
    this.id3Decoder = id3Decoder;
    this.scratchId3Data = scratchId3Data;
    this.shouldSpliceIn = shouldSpliceIn;
    initDataSource = dataSource;
    uid = uidSource.getAndIncrement();
  }

  /**
   * Initializes the chunk for loading, setting the {@link HlsSampleStreamWrapper} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded samples.
   */
  public void init(HlsSampleStreamWrapper output) {
    this.output = output;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    maybeLoadInitData();
    if (!loadCanceled) {
      if (!hasGapTag) {
        loadMedia();
      }
      loadCompleted = true;
    }
  }

  // Internal methods.

  private void maybeLoadInitData() throws IOException, InterruptedException {
    if (initLoadCompleted || initDataSpec == null) {
      // Note: The HLS spec forbids initialization segments for packed audio.
      return;
    }
    DataSpec initSegmentDataSpec = initDataSpec.subrange(initSegmentBytesLoaded);
    try {
      DefaultExtractorInput input = prepareExtraction(initDataSource, initSegmentDataSpec);
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        initSegmentBytesLoaded = (int) (input.getPosition() - initDataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(initDataSource);
    }
    initLoadCompleted = true;
  }

  private void loadMedia() throws IOException, InterruptedException {
    // If we previously fed part of this chunk to the extractor, we need to skip it this time. For
    // encrypted content we need to skip the data by reading it through the source, so as to ensure
    // correct decryption of the remainder of the chunk. For clear content, we can request the
    // remainder of the chunk directly.
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (isEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = nextLoadPosition != 0;
    } else {
      loadDataSpec = dataSpec.subrange(nextLoadPosition);
      skipLoadedBytes = false;
    }
    if (!isMasterTimestampSource) {
      timestampAdjuster.waitUntilInitialized();
    } else if (timestampAdjuster.getFirstSampleTimestampUs() == TimestampAdjuster.DO_NOT_OFFSET) {
      // We're the master and we haven't set the desired first sample timestamp yet.
      timestampAdjuster.setFirstSampleTimestampUs(startTimeUs);
    }
    try {
      ExtractorInput input = prepareExtraction(dataSource, loadDataSpec);
      if (skipLoadedBytes) {
        input.skipFully(nextLoadPosition);
      }
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        nextLoadPosition = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
  }

  private DefaultExtractorInput prepareExtraction(DataSource dataSource, DataSpec dataSpec)
      throws IOException, InterruptedException {
    long bytesToRead = dataSource.open(dataSpec);

    DefaultExtractorInput extractorInput =
        new DefaultExtractorInput(dataSource, dataSpec.absoluteStreamPosition, bytesToRead);

    if (extractor == null) {
      long id3Timestamp = peekId3PrivTimestamp(extractorInput);
      extractorInput.resetPeekPosition();

      Pair<Extractor, Boolean> extractorData =
          extractorFactory.createExtractor(
              previousExtractor,
              dataSpec.uri,
              trackFormat,
              muxedCaptionFormats,
              drmInitData,
              timestampAdjuster,
              dataSource.getResponseHeaders(),
              extractorInput);
      extractor = extractorData.first;
      boolean reusingExtractor = extractor == previousExtractor;
      boolean isPackedAudioExtractor = extractorData.second;
      if (isPackedAudioExtractor) {
        output.setSampleOffsetUs(
            id3Timestamp != C.TIME_UNSET
                ? timestampAdjuster.adjustTsTimestamp(id3Timestamp)
                : startTimeUs);
      }
      initLoadCompleted = reusingExtractor && initDataSpec != null;

      output.init(uid, shouldSpliceIn, reusingExtractor);
      if (!reusingExtractor) {
        extractor.init(output);
      }
    }

    return extractorInput;
  }

  /**
   * Peek the presentation timestamp of the first sample in the chunk from an ID3 PRIV as defined
   * in the HLS spec, version 20, Section 3.4. Returns {@link C#TIME_UNSET} if the frame is not
   * found. This method only modifies the peek position.
   *
   * @param input The {@link ExtractorInput} to obtain the PRIV frame from.
   * @return The parsed, adjusted timestamp in microseconds
   * @throws IOException If an error occurred peeking from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  private long peekId3PrivTimestamp(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    try {
      input.peekFully(scratchId3Data.data, 0, Id3Decoder.ID3_HEADER_LENGTH);
    } catch (EOFException e) {
      // The input isn't long enough for there to be any ID3 data.
      return C.TIME_UNSET;
    }
    scratchId3Data.reset(Id3Decoder.ID3_HEADER_LENGTH);
    int id = scratchId3Data.readUnsignedInt24();
    if (id != Id3Decoder.ID3_TAG) {
      return C.TIME_UNSET;
    }
    scratchId3Data.skipBytes(3); // version(2), flags(1).
    int id3Size = scratchId3Data.readSynchSafeInt();
    int requiredCapacity = id3Size + Id3Decoder.ID3_HEADER_LENGTH;
    if (requiredCapacity > scratchId3Data.capacity()) {
      byte[] data = scratchId3Data.data;
      scratchId3Data.reset(requiredCapacity);
      System.arraycopy(data, 0, scratchId3Data.data, 0, Id3Decoder.ID3_HEADER_LENGTH);
    }
    input.peekFully(scratchId3Data.data, Id3Decoder.ID3_HEADER_LENGTH, id3Size);
    Metadata metadata = id3Decoder.decode(scratchId3Data.data, id3Size);
    if (metadata == null) {
      return C.TIME_UNSET;
    }
    int metadataLength = metadata.length();
    for (int i = 0; i < metadataLength; i++) {
      Metadata.Entry frame = metadata.get(i);
      if (frame instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) frame;
        if (PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
          System.arraycopy(
              privFrame.privateData, 0, scratchId3Data.data, 0, 8 /* timestamp size */);
          scratchId3Data.reset(8);
          // The top 31 bits should be zeros, but explicitly zero them to wrap in the case that the
          // streaming provider forgot. See: https://github.com/google/ExoPlayer/pull/3495.
          return scratchId3Data.readLong() & 0x1FFFFFFFFL;
        }
      }
    }
    return C.TIME_UNSET;
  }

  // Internal factory methods.

  /**
   * If the segment is fully encrypted, returns an {@link Aes128DataSource} that wraps the original
   * in order to decrypt the loaded data. Else returns the original.
   */
  private static DataSource buildDataSource(DataSource dataSource, byte[] fullSegmentEncryptionKey,
      byte[] encryptionIv) {
    if (fullSegmentEncryptionKey != null) {
      return new Aes128DataSource(dataSource, fullSegmentEncryptionKey, encryptionIv);
    }
    return dataSource;
  }

}
