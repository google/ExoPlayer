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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.BehindLiveWindowException;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer.chunk.ChunkOperationHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.ContainerMediaChunk;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.drm.DrmInitData.SchemeInitData;
import com.google.android.exoplayer.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.extractor.mp4.Track;
import com.google.android.exoplayer.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * An {@link ChunkSource} for SmoothStreaming.
 */
// TODO[REFACTOR]: Handle multiple stream elements of the same type (at a higher level).
public class SmoothStreamingChunkSource implements ChunkSource {

  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final int streamElementType;
  private final DataSource dataSource;
  private final Evaluation evaluation;
  private final long liveEdgeLatencyUs;
  private final ManifestFetcher<SmoothStreamingManifest> manifestFetcher;
  private final FormatEvaluator adaptiveFormatEvaluator;

  private boolean manifestFetcherEnabled;
  private boolean live;
  private long durationUs;
  private TrackEncryptionBox[] trackEncryptionBoxes;
  private DrmInitData.Mapped drmInitData;
  private SmoothStreamingManifest currentManifest;
  private int currentManifestChunkOffset;
  private boolean needManifestRefresh;

  // Properties of exposed tracks.
  private int elementIndex;
  private TrackGroup trackGroup;
  private ChunkExtractorWrapper[] extractorWrappers;

  // Properties of enabled tracks.
  private Format[] enabledFormats;
  private boolean[] adaptiveFormatBlacklistFlags;

  private IOException fatalError;

  /**
   * @param manifestFetcher A fetcher for the manifest.
   * @param streamElementType The type of stream element exposed by this source. One of
   *     {@link StreamElement#TYPE_VIDEO}, {@link StreamElement#TYPE_AUDIO} and
   *     {@link StreamElement#TYPE_TEXT}.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   * @param liveEdgeLatencyMs For live streams, the number of milliseconds that the playback should
   *     lag behind the "live edge" (i.e. the end of the most recently defined media in the
   *     manifest). Choosing a small value will minimize latency introduced by the player, however
   *     note that the value sets an upper bound on the length of media that the player can buffer.
   *     Hence a small value may increase the probability of rebuffering and playback failures.
   */
  public SmoothStreamingChunkSource(ManifestFetcher<SmoothStreamingManifest> manifestFetcher,
      int streamElementType, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator,
      long liveEdgeLatencyMs) {
    this.manifestFetcher = manifestFetcher;
    this.streamElementType = streamElementType;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.liveEdgeLatencyUs = liveEdgeLatencyMs * 1000;
    evaluation = new Evaluation();
  }

  // ChunkSource implementation.

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else if (live) {
      manifestFetcher.maybeThrowError();
    }
  }

  @Override
  public boolean prepare() throws IOException {
    if (!manifestFetcherEnabled) {
      // TODO[REFACTOR]: We need to disable this at some point.
      manifestFetcher.enable();
      manifestFetcherEnabled = true;
    }
    if (currentManifest == null) {
      currentManifest = manifestFetcher.getManifest();
      if (currentManifest == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      } else {
        live = currentManifest.isLive;
        durationUs = currentManifest.durationUs;
        ProtectionElement protectionElement = currentManifest.protectionElement;
        if (protectionElement != null) {
          byte[] keyId = getProtectionElementKeyId(protectionElement.data);
          trackEncryptionBoxes = new TrackEncryptionBox[1];
          trackEncryptionBoxes[0] = new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId);
          drmInitData = new DrmInitData.Mapped();
          drmInitData.put(protectionElement.uuid,
              new SchemeInitData(MimeTypes.VIDEO_MP4, protectionElement.data));
        } else {
          trackEncryptionBoxes = null;
          drmInitData = null;
        }
        initForManifest(currentManifest);
      }
    }
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public final TrackGroup getTracks() {
    return trackGroup;
  }

  @Override
  public void enable(int[] tracks) {
    enabledFormats = new Format[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      enabledFormats[i] = trackGroup.getFormat(tracks[i]);
    }
    Arrays.sort(enabledFormats, new DecreasingBandwidthComparator());
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.enable(enabledFormats);
      adaptiveFormatBlacklistFlags = new boolean[tracks.length];
    }
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (!currentManifest.isLive || fatalError != null) {
      return;
    }

    SmoothStreamingManifest newManifest = manifestFetcher.getManifest();
    if (currentManifest != newManifest && newManifest != null) {
      StreamElement currentElement = currentManifest.streamElements[elementIndex];
      int currentElementChunkCount = currentElement.chunkCount;
      StreamElement newElement = newManifest.streamElements[elementIndex];
      if (currentElementChunkCount == 0 || newElement.chunkCount == 0) {
        // There's no overlap between the old and new elements because at least one is empty.
        currentManifestChunkOffset += currentElementChunkCount;
      } else {
        long currentElementEndTimeUs = currentElement.getStartTimeUs(currentElementChunkCount - 1)
            + currentElement.getChunkDurationUs(currentElementChunkCount - 1);
        long newElementStartTimeUs = newElement.getStartTimeUs(0);
        if (currentElementEndTimeUs <= newElementStartTimeUs) {
          // There's no overlap between the old and new elements.
          currentManifestChunkOffset += currentElementChunkCount;
        } else {
          // The new element overlaps with the old one.
          currentManifestChunkOffset += currentElement.getChunkIndex(newElementStartTimeUs);
        }
      }
      currentManifest = newManifest;
      needManifestRefresh = false;
    }

    if (needManifestRefresh && (SystemClock.elapsedRealtime()
        > manifestFetcher.getManifestLoadStartTimestamp() + MINIMUM_MANIFEST_REFRESH_PERIOD_MS)) {
      manifestFetcher.requestRefresh();
    }
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long playbackPositionUs,
      ChunkOperationHolder out) {
    if (fatalError != null) {
      out.chunk = null;
      return;
    }

    evaluation.queueSize = queue.size();
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.evaluate(queue, playbackPositionUs, 0, adaptiveFormatBlacklistFlags,
          evaluation);
    } else {
      evaluation.format = enabledFormats[0];
      evaluation.trigger = Chunk.TRIGGER_MANUAL;
    }

    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format == selectedFormat) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    // In all cases where we return before instantiating a new chunk, we want out.chunk to be null.
    out.chunk = null;

    StreamElement streamElement = currentManifest.streamElements[elementIndex];
    if (streamElement.chunkCount == 0) {
      if (currentManifest.isLive) {
        needManifestRefresh = true;
      } else {
        out.endOfStream = true;
      }
      return;
    }

    int chunkIndex;
    if (queue.isEmpty()) {
      if (live) {
        playbackPositionUs = getLiveSeekPosition(currentManifest, liveEdgeLatencyUs);
      }
      chunkIndex = streamElement.getChunkIndex(playbackPositionUs);
    } else {
      MediaChunk previous = queue.get(out.queueSize - 1);
      chunkIndex = previous.chunkIndex + 1 - currentManifestChunkOffset;
    }

    if (live && chunkIndex < 0) {
      // This is before the first chunk in the current manifest.
      fatalError = new BehindLiveWindowException();
      return;
    } else if (currentManifest.isLive) {
      if (chunkIndex >= streamElement.chunkCount) {
        // This is beyond the last chunk in the current manifest.
        needManifestRefresh = true;
        return;
      } else if (chunkIndex == streamElement.chunkCount - 1) {
        // This is the last chunk in the current manifest. Mark the manifest as being finished,
        // but continue to return the final chunk.
        needManifestRefresh = true;
      }
    } else if (chunkIndex >= streamElement.chunkCount) {
      out.endOfStream = true;
      return;
    }

    boolean isLastChunk = !currentManifest.isLive && chunkIndex == streamElement.chunkCount - 1;
    long chunkStartTimeUs = streamElement.getStartTimeUs(chunkIndex);
    long chunkEndTimeUs = isLastChunk ? -1
        : chunkStartTimeUs + streamElement.getChunkDurationUs(chunkIndex);
    int currentAbsoluteChunkIndex = chunkIndex + currentManifestChunkOffset;

    int trackGroupTrackIndex = getTrackGroupTrackIndex(trackGroup, selectedFormat);
    ChunkExtractorWrapper extractorWrapper = extractorWrappers[trackGroupTrackIndex];

    int manifestTrackIndex = getManifestTrackIndex(streamElement, selectedFormat);
    Uri uri = streamElement.buildRequestUri(manifestTrackIndex, chunkIndex);

    Chunk mediaChunk = newMediaChunk(selectedFormat, dataSource, uri, null,
        currentAbsoluteChunkIndex, chunkStartTimeUs, chunkEndTimeUs, evaluation.trigger,
        extractorWrapper, drmInitData, selectedFormat);
    out.chunk = mediaChunk;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  @Override
  public void disable() {
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.disable();
    }
    evaluation.clear();
    fatalError = null;
  }

  // Private methods.

  private void initForManifest(SmoothStreamingManifest manifest) {
    for (int i = 0; i < manifest.streamElements.length; i++) {
      if (manifest.streamElements[i].type == streamElementType) {
        Format[] formats = manifest.streamElements[i].formats;
        if (formats.length > 0) {
          // We've found an element of the desired type.
          long timescale = manifest.streamElements[i].timescale;
          extractorWrappers = new ChunkExtractorWrapper[formats.length];
          for (int j = 0; j < formats.length; j++) {
            int nalUnitLengthFieldLength = streamElementType == StreamElement.TYPE_VIDEO ? 4 : -1;
            Track track = new Track(j, streamElementType, timescale, C.UNKNOWN_TIME_US, durationUs,
                formats[j], trackEncryptionBoxes, nalUnitLengthFieldLength, null, null);
            FragmentedMp4Extractor extractor = new FragmentedMp4Extractor(
                FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME
                | FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX, track);
            extractorWrappers[j] = new ChunkExtractorWrapper(extractor);
          }
          elementIndex = i;
          trackGroup = new TrackGroup(adaptiveFormatEvaluator != null, formats);
          return;
        }
      }
    }
    extractorWrappers = null;
    trackGroup = null;
  }

  /**
   * For live playbacks, determines the seek position that snaps playback to be
   * {@code liveEdgeLatencyUs} behind the live edge of the provided manifest.
   *
   * @param manifest The manifest.
   * @param liveEdgeLatencyUs The live edge latency, in microseconds.
   * @return The seek position in microseconds.
   */
  private static long getLiveSeekPosition(SmoothStreamingManifest manifest,
      long liveEdgeLatencyUs) {
    long liveEdgeTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < manifest.streamElements.length; i++) {
      StreamElement streamElement = manifest.streamElements[i];
      if (streamElement.chunkCount > 0) {
        long elementLiveEdgeTimestampUs =
            streamElement.getStartTimeUs(streamElement.chunkCount - 1)
            + streamElement.getChunkDurationUs(streamElement.chunkCount - 1);
        liveEdgeTimestampUs = Math.max(liveEdgeTimestampUs, elementLiveEdgeTimestampUs);
      }
    }
    return liveEdgeTimestampUs - liveEdgeLatencyUs;
  }

  /**
   * Gets the index of a format in a track group, using referential equality.
   */
  private static int getTrackGroupTrackIndex(TrackGroup trackGroup, Format format) {
    for (int i = 0; i < trackGroup.length; i++) {
      if (trackGroup.getFormat(i) == format) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  /**
   * Gets the index of a format in an element, using format.id equality.
   * <p>
   * This method will return the same index as {@link #getTrackGroupTrackIndex(TrackGroup, Format)}
   * except in the case where a live manifest is refreshed and the ordering of the tracks in the
   * manifest has changed.
   */
  private static int getManifestTrackIndex(StreamElement element, Format format) {
    Format[] formats = element.formats;
    for (int i = 0; i < formats.length; i++) {
      if (TextUtils.equals(formats[i].id, format.id)) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private static MediaChunk newMediaChunk(Format format, DataSource dataSource, Uri uri,
      String cacheKey, int chunkIndex, long chunkStartTimeUs, long chunkEndTimeUs, int trigger,
      ChunkExtractorWrapper extractorWrapper, DrmInitData drmInitData, Format sampleFormat) {
    DataSpec dataSpec = new DataSpec(uri, 0, -1, cacheKey);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to chunkStartTimeUs.
    long sampleOffsetUs = chunkStartTimeUs;
    return new ContainerMediaChunk(dataSource, dataSpec, trigger, format, chunkStartTimeUs,
        chunkEndTimeUs, chunkIndex, sampleOffsetUs, extractorWrapper, sampleFormat, drmInitData,
        true, Chunk.NO_PARENT_ID);
  }

  private static byte[] getProtectionElementKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

}
