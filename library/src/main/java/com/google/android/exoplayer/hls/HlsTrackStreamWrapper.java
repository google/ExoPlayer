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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.AdaptiveSourceEventListener.EventDispatcher;
import com.google.android.exoplayer.BufferingPolicy.LoadControl;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DecoderInputBuffer;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.FormatHolder;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.Chunk;
import com.google.android.exoplayer.chunk.ChunkHolder;
import com.google.android.exoplayer.extractor.DefaultTrackOutput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.util.SparseArray;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Loads {@link HlsMediaChunk}s obtained from a {@link HlsChunkSource}, and provides
 * {@link TrackStream}s from which the loaded media can be consumed.
 */
/* package */ final class HlsTrackStreamWrapper implements Loader.Callback<Chunk>, ExtractorOutput {

  private static final int PRIMARY_TYPE_NONE = 0;
  private static final int PRIMARY_TYPE_TEXT = 1;
  private static final int PRIMARY_TYPE_AUDIO = 2;
  private static final int PRIMARY_TYPE_VIDEO = 3;

  private final int trackType;
  private final HlsChunkSource chunkSource;
  private final LoadControl loadControl;
  private final Format muxedAudioFormat;
  private final Format muxedCaptionFormat;
  private final int minLoadableRetryCount;
  private final Loader loader;
  private final EventDispatcher eventDispatcher;
  private final ChunkHolder nextChunkHolder;
  private final SparseArray<DefaultTrackOutput> sampleQueues;
  private final LinkedList<HlsMediaChunk> mediaChunks;

  private volatile boolean sampleQueuesBuilt;

  private boolean prepared;
  private boolean readingEnabled;
  private int enabledTrackCount;
  private Format downstreamFormat;

  // Tracks are complicated in HLS. See documentation of buildTracks for details.
  // Indexed by track (as exposed by this source).
  private TrackGroupArray trackGroups;
  private int primaryTrackGroupIndex;
  // Indexed by group.
  private boolean[] groupEnabledStates;

  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;

  /**
   * @param trackType The type of the track. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param chunkSource A {@link HlsChunkSource} from which chunks to load are obtained.
   * @param loadControl Controls when the source is permitted to load data.
   * @param muxedAudioFormat If HLS master playlist indicates that the stream contains muxed audio,
   *     this is the audio {@link Format} as defined by the playlist.
   * @param muxedCaptionFormat If HLS master playlist indicates that the stream contains muxed
   *     captions, this is the audio {@link Format} as defined by the playlist.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public HlsTrackStreamWrapper(int trackType, HlsChunkSource chunkSource, LoadControl loadControl,
      Format muxedAudioFormat, Format muxedCaptionFormat, int minLoadableRetryCount,
      EventDispatcher eventDispatcher) {
    this.trackType = trackType;
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormat = muxedCaptionFormat;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    loader = new Loader("Loader:HlsTrackStreamWrapper");
    nextChunkHolder = new ChunkHolder();
    sampleQueues = new SparseArray<>();
    mediaChunks = new LinkedList<>();
    readingEnabled = true;
    pendingResetPositionUs = C.UNSET_TIME_US;
  }

  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    if (chunkSource.getTrackCount() == 0) {
      trackGroups = new TrackGroupArray();
      prepared = true;
      return true;
    }
    if (sampleQueuesBuilt) {
      boolean canBuildTracks = true;
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        if (sampleQueues.valueAt(i).getUpstreamFormat() == null) {
          canBuildTracks = false;
          break;
        }
      }
      if (canBuildTracks) {
        buildTracks();
        prepared = true;
        return true;
      }
    }
    // We're not prepared.
    maybeThrowError();
    if (!loader.isLoading()) {
      // We're going to have to start loading a chunk to get what we need for preparation. We should
      // attempt to load the chunk at positionUs, so that we'll already be loading the correct chunk
      // in the common case where the renderer is subsequently enabled at this position.
      pendingResetPositionUs = positionUs;
      downstreamPositionUs = positionUs;
      maybeStartLoading();
    }
    return false;
  }

  public long getDurationUs() {
    return chunkSource.getDurationUs();
  }

  public boolean isLive() {
    return chunkSource.isLive();
  }

  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, boolean isFirstTrackSelection) {
    Assertions.checkState(prepared);
    boolean tracksWereEnabled = enabledTrackCount > 0;
    // Unselect old tracks.
    for (int i = 0; i < oldStreams.size(); i++) {
      int group = ((TrackStreamImpl) oldStreams.get(i)).group;
      setTrackGroupEnabledState(group, false);
      sampleQueues.valueAt(group).disable();
    }
    // Select new tracks.
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    for (int i = 0; i < newStreams.length; i++) {
      TrackSelection selection = newSelections.get(i);
      int group = selection.group;
      int[] tracks = selection.getTracks();
      setTrackGroupEnabledState(group, true);
      if (group == primaryTrackGroupIndex) {
        chunkSource.selectTracks(tracks, isFirstTrackSelection);
      }
      newStreams[i] = new TrackStreamImpl(group);
    }
    // At the time of the first track selection all queues will be enabled, so we need to disable
    // any that are no longer required.
    if (isFirstTrackSelection) {
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        if (!groupEnabledStates[i]) {
          sampleQueues.valueAt(i).disable();
        }
      }
    }
    // Cancel requests if necessary.
    if (enabledTrackCount == 0) {
      chunkSource.reset();
      downstreamPositionUs = Long.MIN_VALUE;
      downstreamFormat = null;
      mediaChunks.clear();
      if (tracksWereEnabled) {
        loadControl.unregister(this);
      }
      if (loader.isLoading()) {
        loader.cancelLoading();
      }
    } else if (!tracksWereEnabled) {
      loadControl.register(this);
    }
    return newStreams;
  }

  public void restartFrom(long positionUs) {
    lastSeekPositionUs = positionUs;
    downstreamPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        sampleQueues.valueAt(i).reset(groupEnabledStates[i]);
      }
      maybeStartLoading();
    }
  }

  // TODO[REFACTOR]: Find a way to get rid of this.
  public void continueBuffering(long playbackPositionUs) {
    downstreamPositionUs = playbackPositionUs;
    if (!loader.isLoading()) {
      maybeStartLoading();
    }
  }

  public void setReadingEnabled(boolean readingEnabled) {
    this.readingEnabled = readingEnabled;
  }

  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.END_OF_SOURCE_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = downstreamPositionUs;
      HlsMediaChunk lastMediaChunk = mediaChunks.getLast();
      HlsMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        bufferedPositionUs = Math.max(bufferedPositionUs,
            sampleQueues.valueAt(i).getLargestQueuedTimestampUs());
      }
      return bufferedPositionUs;
    }
  }

  public void release() {
    int sampleQueueCount = sampleQueues.size();
    for (int i = 0; i < sampleQueueCount; i++) {
      sampleQueues.valueAt(i).disable();
    }
    if (enabledTrackCount > 0) {
      loadControl.unregister(this);
    }
    loader.release();
  }

  // TrackStream implementation.

  /* package */ boolean isReady(int group) {
    return loadingFinished || (!isPendingReset() && !sampleQueues.valueAt(group).isEmpty());
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  /* package */ int readData(int group, FormatHolder formatHolder, DecoderInputBuffer buffer) {
    if (!readingEnabled || isPendingReset()) {
      return TrackStream.NOTHING_READ;
    }

    while (mediaChunks.size() > 1 && mediaChunks.get(1).startTimeUs <= downstreamPositionUs) {
      mediaChunks.removeFirst();
    }
    HlsMediaChunk currentChunk = mediaChunks.getFirst();
    Format format = currentChunk.format;
    if (!format.equals(downstreamFormat)) {
      eventDispatcher.downstreamFormatChanged(trackType, format,
          currentChunk.formatEvaluatorTrigger, currentChunk.formatEvaluatorData,
          currentChunk.startTimeUs);
    }
    downstreamFormat = format;

    return sampleQueues.valueAt(group).readData(formatHolder, buffer, loadingFinished,
        lastSeekPositionUs);
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    if (!released && enabledTrackCount > 0) {
      restartFrom(pendingResetPositionUs);
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0;
    boolean canceled = false;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error)) {
      if (isMediaChunk) {
        HlsMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
      canceled = true;
    }
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded(), error,
        canceled);
    if (canceled) {
      maybeStartLoading();
      return Loader.DONT_RETRY;
    } else {
      return Loader.RETRY;
    }
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Indicates to all track outputs that they should splice in subsequently queued samples.
   */
  public void splice() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).splice();
    }
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public DefaultTrackOutput track(int id) {
    if (sampleQueues.indexOfKey(id) >= 0) {
      return sampleQueues.get(id);
    }
    DefaultTrackOutput trackOutput = new DefaultTrackOutput(loadControl.getAllocator());
    sampleQueues.put(id, trackOutput);
    return trackOutput;
  }

  @Override
  public void endTracks() {
    sampleQueuesBuilt = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

  // Internal methods.

  /**
   * Builds tracks that are exposed by this {@link HlsTrackStreamWrapper} instance, as well as
   * internal data-structures required for operation.
   * <p>
   * Tracks in HLS are complicated. A HLS master playlist contains a number of "variants". Each
   * variant stream typically contains muxed video, audio and (possibly) additional audio, metadata
   * and caption tracks. We wish to allow the user to select between an adaptive track that spans
   * all variants, as well as each individual variant. If multiple audio tracks are present within
   * each variant then we wish to allow the user to select between those also.
   * <p>
   * To do this, tracks are constructed as follows. The {@link HlsChunkSource} exposes (N+1) tracks,
   * where N is the number of variants defined in the HLS master playlist. These consist of one
   * adaptive track defined to span all variants and a track for each individual variant. The
   * adaptive track is initially selected. The extractor is then prepared to discover the tracks
   * inside of each variant stream. The two sets of tracks are then combined by this method to
   * create a third set, which is the set exposed by this {@link HlsTrackStreamWrapper}:
   * <ul>
   * <li>The extractor tracks are inspected to infer a "primary" track type. If a video track is
   * present then it is always the primary type. If not, audio is the primary type if present.
   * Else text is the primary type if present. Else there is no primary type.</li>
   * <li>If there is exactly one extractor track of the primary type, it's expanded into (N+1)
   * exposed tracks, all of which correspond to the primary extractor track and each of which
   * corresponds to a different chunk source track. Selecting one of these tracks has the effect
   * of switching the selected track on the chunk source.</li>
   * <li>All other extractor tracks are exposed directly. Selecting one of these tracks has the
   * effect of selecting an extractor track, leaving the selected track on the chunk source
   * unchanged.</li>
   * </ul>
   */
  private void buildTracks() {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = PRIMARY_TYPE_NONE;
    int primaryExtractorTrackIndex = -1;
    int extractorTrackCount = sampleQueues.size();
    for (int i = 0; i < extractorTrackCount; i++) {
      String sampleMimeType = sampleQueues.valueAt(i).getUpstreamFormat().sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = PRIMARY_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = PRIMARY_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = PRIMARY_TYPE_TEXT;
      } else {
        trackType = PRIMARY_TYPE_NONE;
      }
      if (trackType > primaryExtractorTrackType) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType && primaryExtractorTrackIndex != -1) {
        // We have multiple tracks of the primary type. We only want an index if there only
        // exists a single track of the primary type, so set the index back to -1.
        primaryExtractorTrackIndex = -1;
      }
    }

    // Calculate the number of tracks that will be exposed.
    int chunkSourceTrackCount = chunkSource.getTrackCount();

    // Instantiate the necessary internal data-structures.
    primaryTrackGroupIndex = -1;
    groupEnabledStates = new boolean[extractorTrackCount];

    // Construct the set of exposed track groups.
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = sampleQueues.valueAt(i).getUpstreamFormat();
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          formats[j] = getSampleFormat(chunkSource.getTrackFormat(j), sampleFormat);
        }
        trackGroups[i] = new TrackGroup(chunkSource.isAdaptive(), formats);
        primaryTrackGroupIndex = i;
      } else {
        Format trackFormat = null;
        if (primaryExtractorTrackType == PRIMARY_TYPE_VIDEO) {
          if (MimeTypes.isAudio(sampleFormat.sampleMimeType)) {
            trackFormat = muxedAudioFormat;
          } else if (MimeTypes.APPLICATION_EIA608.equals(sampleFormat.sampleMimeType)) {
            trackFormat = muxedCaptionFormat;
          }
        }
        trackGroups[i] = new TrackGroup(getSampleFormat(trackFormat, sampleFormat));
      }
    }
    this.trackGroups = new TrackGroupArray(trackGroups);
  }

  /**
   * Enables or disables a specified track group.
   *
   * @param group The index of the track group.
   * @param enabledState True if the group is being enabled, or false if it's being disabled.
   */
  private void setTrackGroupEnabledState(int group, boolean enabledState) {
    Assertions.checkState(groupEnabledStates[group] != enabledState);
    groupEnabledStates[group] = enabledState;
    enabledTrackCount = enabledTrackCount + (enabledState ? 1 : -1);
  }

  /**
   * Derives a sample format corresponding to a given container format, by combining it with sample
   * level information obtained from a second sample format.
   *
   * @param containerFormat The container format for which the sample format should be derived.
   * @param sampleFormat A sample format from which to obtain sample level information.
   * @return The derived sample format.
   */
  private static Format getSampleFormat(Format containerFormat, Format sampleFormat) {
    if (containerFormat == null) {
      return sampleFormat;
    }
    return sampleFormat.copyWithContainerInfo(containerFormat.id, containerFormat.bitrate,
        containerFormat.width, containerFormat.height, containerFormat.selectionFlags,
        containerFormat.language);
  }

  private void maybeStartLoading() {
    boolean shouldStartLoading = !prepared || (enabledTrackCount > 0
        && loadControl.update(this, getNextLoadPositionUs(), false));
    if (!shouldStartLoading) {
      return;
    }

    chunkSource.getNextChunk(mediaChunks.isEmpty() ? null : mediaChunks.getLast(),
        pendingResetPositionUs != C.UNSET_TIME_US ? pendingResetPositionUs : downstreamPositionUs,
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      if (prepared) {
        loadControl.update(this, C.UNSET_TIME_US, false);
      }
      return;
    }

    if (loadable == null) {
      return;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.UNSET_TIME_US;
      HlsMediaChunk mediaChunk = (HlsMediaChunk) loadable;
      mediaChunk.init(this);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, trackType, loadable.format,
        loadable.formatEvaluatorTrigger, loadable.formatEvaluatorData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs);
    if (prepared) {
      // Update the load control again to indicate that we're now loading.
      loadControl.update(this, getNextLoadPositionUs(), true);
    }
  }

  /**
   * Gets the next load time, assuming that the next load starts where the previous chunk ended (or
   * from the pending reset time, if there is one).
   */
  private long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.UNSET_TIME_US : mediaChunks.getLast().endTimeUs;
    }
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof HlsMediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.UNSET_TIME_US;
  }

  private final class TrackStreamImpl implements TrackStream {

    private final int group;

    public TrackStreamImpl(int group) {
      this.group = group;
    }

    @Override
    public boolean isReady() {
      return HlsTrackStreamWrapper.this.isReady(group);
    }

    @Override
    public void maybeThrowError() throws IOException {
      HlsTrackStreamWrapper.this.maybeThrowError();
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer) {
      return HlsTrackStreamWrapper.this.readData(group, formatHolder, buffer);
    }

  }

}
