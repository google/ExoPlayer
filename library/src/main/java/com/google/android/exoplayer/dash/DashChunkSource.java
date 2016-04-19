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
package com.google.android.exoplayer.dash;

import com.google.android.exoplayer.BehindLiveWindowException;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.Format.DecreasingBandwidthComparator;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer.chunk.ChunkHolder;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.ContainerMediaChunk;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import com.google.android.exoplayer.chunk.InitializationChunk;
import com.google.android.exoplayer.chunk.MediaChunk;
import com.google.android.exoplayer.chunk.SingleSampleMediaChunk;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.ContentProtection;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.RangedUri;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * An {@link ChunkSource} for DASH streams.
 * <p>
 * This implementation currently supports fMP4, webm, webvtt and ttml.
 * <p>
 * This implementation makes the following assumptions about multi-period manifests:
 * <ol>
 * <li>that new periods will contain the same representations as previous periods (i.e. no new or
 * missing representations) and</li>
 * <li>that representations are contiguous across multiple periods</li>
 * </ol>
 */
// TODO: handle cases where the above assumption are false
// TODO[REFACTOR]: Handle multiple adaptation sets of the same type (at a higher level).
public class DashChunkSource implements ChunkSource {

  private final int adaptationSetType;
  private final DataSource dataSource;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final Evaluation evaluation;
  private final ManifestFetcher<MediaPresentationDescription> manifestFetcher;

  // Properties of the initial manifest.
  private boolean live;
  private long durationUs;

  private MediaPresentationDescription currentManifest;
  private DrmInitData drmInitData;

  private boolean lastChunkWasInitialization;
  private IOException fatalError;

  // Properties of exposed tracks.
  private int adaptationSetIndex;
  private TrackGroup trackGroup;
  private RepresentationHolder[] representationHolders;

  // Properties of enabled tracks.
  private Format[] enabledFormats;
  private boolean[] adaptiveFormatBlacklistFlags;

  /**
   * @param manifestFetcher A fetcher for the manifest.
   * @param adaptationSetType The type of the adaptation set exposed by this source. One of
   *     {@link C#TRACK_TYPE_AUDIO}, {@link C#TRACK_TYPE_VIDEO} and {@link C#TRACK_TYPE_TEXT}.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   */
  public DashChunkSource(ManifestFetcher<MediaPresentationDescription> manifestFetcher,
      int adaptationSetType, DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator) {
    this.manifestFetcher = manifestFetcher;
    this.adaptationSetType = adaptationSetType;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.evaluation = new Evaluation();
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
    if (currentManifest == null) {
      currentManifest = manifestFetcher.getManifest();
      if (currentManifest == null) {
        manifestFetcher.maybeThrowError();
        manifestFetcher.requestRefresh();
        return false;
      } else {
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
    processManifest(manifestFetcher.getManifest());
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (!currentManifest.dynamic || fatalError != null) {
      return;
    }

    MediaPresentationDescription newManifest = manifestFetcher.getManifest();
    if (newManifest != null && newManifest != currentManifest) {
      processManifest(newManifest);
    }

    // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
    // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
    // signaling in the stream, according to:
    // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
    long minUpdatePeriod = currentManifest.minUpdatePeriod;
    if (minUpdatePeriod == 0) {
      minUpdatePeriod = 5000;
    }

    if (android.os.SystemClock.elapsedRealtime()
        > manifestFetcher.getManifestLoadStartTimestamp() + minUpdatePeriod) {
      manifestFetcher.requestRefresh();
    }
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || enabledFormats.length < 2) {
      return queue.size();
    }
    return adaptiveFormatEvaluator.evaluateQueueSize(playbackPositionUs, queue,
        adaptiveFormatBlacklistFlags);
  }

  @Override
  public final void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    if (evaluation.format == null || !lastChunkWasInitialization) {
      if (enabledFormats.length > 1) {
        long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
        adaptiveFormatEvaluator.evaluateFormat(bufferedDurationUs, adaptiveFormatBlacklistFlags,
            evaluation);
      } else {
        evaluation.format = enabledFormats[0];
        evaluation.trigger = Chunk.TRIGGER_MANUAL;
      }
    }

    Format selectedFormat = evaluation.format;
    if (selectedFormat == null) {
      return;
    }

    RepresentationHolder representationHolder =
        representationHolders[getTrackIndex(selectedFormat)];
    Representation selectedRepresentation = representationHolder.representation;
    DashSegmentIndex segmentIndex = representationHolder.segmentIndex;

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;
    Format sampleFormat = representationHolder.sampleFormat;
    if (sampleFormat == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (segmentIndex == null) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }
    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, representationHolder.extractorWrapper, dataSource,
          evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    // TODO[REFACTOR]: Bring back UTC timing element support.
    long nowUs = System.currentTimeMillis() * 1000;

    int firstAvailableSegmentNum = representationHolder.getFirstSegmentNum();
    int lastAvailableSegmentNum = representationHolder.getLastSegmentNum();
    boolean indexUnbounded = lastAvailableSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED;
    if (indexUnbounded) {
      // The index is itself unbounded. We need to use the current time to calculate the range of
      // available segments.
      long liveEdgeTimestampUs = nowUs - currentManifest.availabilityStartTime * 1000;
      if (currentManifest.timeShiftBufferDepth != -1) {
        long bufferDepthUs = currentManifest.timeShiftBufferDepth * 1000;
        firstAvailableSegmentNum = Math.max(firstAvailableSegmentNum,
            representationHolder.getSegmentNum(liveEdgeTimestampUs - bufferDepthUs));
      }
      // getSegmentNum(liveEdgeTimestampUs) will not be completed yet, so subtract one to get the
      // index of the last completed segment.
      lastAvailableSegmentNum = representationHolder.getSegmentNum(liveEdgeTimestampUs) - 1;
    }

    int segmentNum;
    if (previous == null) {
      segmentNum = Util.constrainValue(representationHolder.getSegmentNum(playbackPositionUs),
          firstAvailableSegmentNum, lastAvailableSegmentNum);
    } else {
      segmentNum = previous.getNextChunkIndex();
      if (segmentNum < firstAvailableSegmentNum) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      }
    }

    if (segmentNum > lastAvailableSegmentNum) {
      // This is beyond the last chunk in the current manifest.
      out.endOfStream = !currentManifest.dynamic;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(representationHolder, dataSource, selectedFormat,
        sampleFormat, segmentNum, evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      RepresentationHolder representationHolder =
          representationHolders[getTrackIndex(initializationChunk.format)];
      Format sampleFormat = initializationChunk.getSampleFormat();
      if (sampleFormat != null) {
        representationHolder.sampleFormat = sampleFormat;
      }
      // The null check avoids overwriting an index obtained from the manifest with one obtained
      // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
      // where it does we should ignore it.
      if (representationHolder.segmentIndex == null) {
        SeekMap seekMap = initializationChunk.getSeekMap();
        if (seekMap != null) {
          representationHolder.segmentIndex = new DashWrappingSegmentIndex((ChunkIndex) seekMap,
              initializationChunk.dataSpec.uri.toString());
        }
      }
      // The null check avoids overwriting drmInitData obtained from the manifest with drmInitData
      // obtained from the stream, as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
      if (drmInitData == null) {
        drmInitData = initializationChunk.getDrmInitData();
      }
    }
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    // TODO: Consider implementing representation blacklisting.
    return false;
  }

  @Override
  public void disable() {
    if (enabledFormats.length > 1) {
      adaptiveFormatEvaluator.disable();
    }
    evaluation.clear();
    fatalError = null;
    enabledFormats = null;
  }

  // Private methods.

  private void initForManifest(MediaPresentationDescription manifest) {
    Period period = manifest.getPeriod(0);
    live = currentManifest.dynamic;
    durationUs = live ? C.UNSET_TIME_US : currentManifest.duration * 1000;

    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      if (adaptationSet.type == adaptationSetType) {
        adaptationSetIndex = i;
        List<Representation> representations = adaptationSet.representations;
        if (!representations.isEmpty()) {
          // We've found a non-empty adaptation set of the exposed type.
          long periodDurationUs = getPeriodDurationUs(manifest, 0);
          representationHolders = new RepresentationHolder[representations.size()];
          Format[] trackFormats = new Format[representations.size()];
          for (int j = 0; j < trackFormats.length; j++) {
            Representation representation = representations.get(j);
            representationHolders[j] = new RepresentationHolder(periodDurationUs, representation);
            trackFormats[j] = representation.format;
          }
          trackGroup = new TrackGroup(adaptiveFormatEvaluator != null, trackFormats);
          drmInitData = getDrmInitData(adaptationSet);
          return;
        }
      }
    }
    trackGroup = null;
  }

  private void processManifest(MediaPresentationDescription newManifest) {
    try {
      currentManifest = newManifest;
      long periodDurationUs = getPeriodDurationUs(currentManifest, 0);
      List<Representation> representations = currentManifest.getPeriod(0).adaptationSets
          .get(adaptationSetIndex).representations;
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(i);
        representationHolders[i].updateRepresentation(periodDurationUs, representation);
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
      return;
    }
  }

  private Chunk newInitializationChunk(RangedUri initializationUri, RangedUri indexUri,
      Representation representation, ChunkExtractorWrapper extractor, DataSource dataSource,
      int trigger) {
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
    }
    DataSpec dataSpec = new DataSpec(requestUri.getUri(), requestUri.start, requestUri.length,
        representation.getCacheKey());
    return new InitializationChunk(dataSource, dataSpec, trigger, representation.format,
        extractor);
  }

  private Chunk newMediaChunk(RepresentationHolder representationHolder, DataSource dataSource,
      Format trackFormat, Format sampleFormat, int segmentNum, int trigger) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(segmentNum);
    long endTimeUs = representationHolder.getSegmentEndTimeUs(segmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());

    if (representationHolder.extractorWrapper == null) {
      return new SingleSampleMediaChunk(dataSource, dataSpec, Chunk.TRIGGER_INITIAL, trackFormat,
          startTimeUs, endTimeUs, segmentNum, trackFormat, null);
    } else {
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      return new ContainerMediaChunk(dataSource, dataSpec, trigger, trackFormat, startTimeUs,
          endTimeUs, segmentNum, sampleOffsetUs, representationHolder.extractorWrapper,
          sampleFormat, drmInitData);
    }
  }

  private int getTrackIndex(Format format) {
    for (int i = 0; i < trackGroup.length; i++) {
      if (trackGroup.getFormat(i) == format) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private static DrmInitData getDrmInitData(AdaptationSet adaptationSet) {
    DrmInitData.Mapped drmInitData = null;
    for (int i = 0; i < adaptationSet.contentProtections.size(); i++) {
      ContentProtection contentProtection = adaptationSet.contentProtections.get(i);
      if (contentProtection.uuid != null && contentProtection.data != null) {
        if (drmInitData == null) {
          drmInitData = new DrmInitData.Mapped();
        }
        drmInitData.put(contentProtection.uuid, contentProtection.data);
      }
    }
    return drmInitData;
  }

  private static long getPeriodDurationUs(MediaPresentationDescription manifest, int index) {
    long durationMs = manifest.getPeriodDuration(index);
    if (durationMs == -1) {
      return C.UNSET_TIME_US;
    } else {
      return durationMs * 1000;
    }
  }

  // Protected classes.

  protected static final class RepresentationHolder {

    public final ChunkExtractorWrapper extractorWrapper;

    public Representation representation;
    public DashSegmentIndex segmentIndex;
    public Format sampleFormat;

    private long periodDurationUs;
    private int segmentNumShift;

    public RepresentationHolder(long periodDurationUs, Representation representation) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      String containerMimeType = representation.format.containerMimeType;
      extractorWrapper = mimeTypeIsRawText(containerMimeType) ? null : new ChunkExtractorWrapper(
          mimeTypeIsWebm(containerMimeType) ? new MatroskaExtractor()
              : new FragmentedMp4Extractor());
      segmentIndex = representation.getIndex();
    }

    public void updateRepresentation(long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException{
      DashSegmentIndex oldIndex = representation.getIndex();
      DashSegmentIndex newIndex = newRepresentation.getIndex();

      periodDurationUs = newPeriodDurationUs;
      representation = newRepresentation;
      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return;
      }

      segmentIndex = newIndex;
      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return;
      }

      int oldIndexLastSegmentNum = oldIndex.getLastSegmentNum(periodDurationUs);
      long oldIndexEndTimeUs = oldIndex.getTimeUs(oldIndexLastSegmentNum)
          + oldIndex.getDurationUs(oldIndexLastSegmentNum, periodDurationUs);
      int newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // The new index continues where the old one ended, with no overlap.
        segmentNumShift += oldIndex.getLastSegmentNum(periodDurationUs) + 1
            - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // There's a gap between the old index and the new one which means we've slipped behind the
        // live window and can't proceed.
        throw new BehindLiveWindowException();
      } else {
        // The new index overlaps with the old one.
        segmentNumShift += oldIndex.getSegmentNum(newIndexStartTimeUs, periodDurationUs)
            - newIndexFirstSegmentNum;
      }
    }

    public int getFirstSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
    }

    public int getLastSegmentNum() {
      int lastSegmentNum = segmentIndex.getLastSegmentNum(periodDurationUs);
      if (lastSegmentNum == DashSegmentIndex.INDEX_UNBOUNDED) {
        return DashSegmentIndex.INDEX_UNBOUNDED;
      }
      return lastSegmentNum + segmentNumShift;
    }

    public long getSegmentStartTimeUs(int segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(int segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public int getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
    }

    public RangedUri getSegmentUrl(int segmentNum) {
      return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
    }

    private static boolean mimeTypeIsWebm(String mimeType) {
      return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
          || mimeType.startsWith(MimeTypes.APPLICATION_WEBM);
    }

    private static boolean mimeTypeIsRawText(String mimeType) {
      return MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType);
    }

  }

}
