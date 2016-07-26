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
package com.google.android.exoplayer2.source.smoothstreaming;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.mp4.TrackEncryptionBox;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.ProtectionElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Base64;

import java.io.IOException;
import java.util.List;

/**
 * A SmoothStreaming {@link MediaSource}.
 */
public final class SsMediaSource implements MediaSource,
    Loader.Callback<ParsingLoadable<SsManifest>> {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final Uri manifestUri;
  private final DataSource.Factory dataSourceFactory;
  private final SsChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final SsManifestParser manifestParser;

  private MediaSource.InvalidationListener invalidationListener;
  private DataSource manifestDataSource;
  private Loader manifestLoader;

  private long manifestLoadStartTimestamp;
  private SsManifest manifest;

  private Handler manifestRefreshHandler;
  private SsMediaPeriod period;

  public SsMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
        DEFAULT_MIN_LOADABLE_RETRY_COUNT, eventHandler, eventListener);
  }

  public SsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      Handler eventHandler, AdaptiveMediaSourceEventListener eventListener) {
    this.manifestUri = Util.toLowerInvariant(manifestUri.getLastPathSegment()).equals("manifest")
        ? manifestUri : Uri.withAppendedPath(manifestUri, "Manifest");
    this.dataSourceFactory = dataSourceFactory;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    manifestParser = new SsManifestParser();
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(InvalidationListener listener) {
    this.invalidationListener = listener;
    manifestDataSource = dataSourceFactory.createDataSource();
    manifestLoader = new Loader("Loader:Manifest");
    manifestRefreshHandler = new Handler();
    startLoadingManifest();
  }

  @Override
  public int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldTimeline) {
    return oldPlayingPeriodIndex;
  }

  @Override
  public MediaPeriod createPeriod(int index) {
    Assertions.checkArgument(index == 0);
    return period;
  }

  @Override
  public void releaseSource() {
    period = null;
    manifest = null;
    manifestDataSource = null;
    manifestLoadStartTimestamp = 0;
    if (manifestLoader != null) {
      manifestLoader.release();
      manifestLoader = null;
    }
    if (manifestRefreshHandler != null) {
      manifestRefreshHandler.removeCallbacksAndMessages(null);
      manifestRefreshHandler = null;
    }
  }

  // MediaPeriod implementation.

  // TODO: Move into separate file.
  private static class SsMediaPeriod implements MediaPeriod,
      SequenceableLoader.Callback<ChunkSampleStream<SsChunkSource>> {

    private final SsChunkSource.Factory chunkSourceFactory;
    private final Loader manifestLoader;
    private final int minLoadableRetryCount;
    private final EventDispatcher eventDispatcher;
    private final long durationUs;
    private final TrackGroupArray trackGroups;
    private final TrackEncryptionBox[] trackEncryptionBoxes;

    private SsManifest manifest;
    private ChunkSampleStream<SsChunkSource>[] sampleStreams;
    private CompositeSequenceableLoader sequenceableLoader;
    private Callback callback;
    private Allocator allocator;

    public SsMediaPeriod(SsManifest manifest, SsChunkSource.Factory chunkSourceFactory,
        int minLoadableRetryCount, EventDispatcher eventDispatcher, Loader manifestLoader) {
      this.manifest = manifest;
      this.chunkSourceFactory = chunkSourceFactory;
      this.manifestLoader = manifestLoader;
      this.minLoadableRetryCount = minLoadableRetryCount;
      this.eventDispatcher = eventDispatcher;
      durationUs = manifest.durationUs;
      trackGroups = buildTrackGroups(manifest);
      ProtectionElement protectionElement = manifest.protectionElement;
      if (protectionElement != null) {
        byte[] keyId = getProtectionElementKeyId(protectionElement.data);
        trackEncryptionBoxes = new TrackEncryptionBox[] {
            new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId)};
      } else {
        trackEncryptionBoxes = null;
      }
    }

    public void updateManifest(SsManifest manifest) {
      this.manifest = manifest;
      if (sampleStreams != null) {
        for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
          sampleStream.getChunkSource().updateManifest(manifest);
        }
        callback.onContinueLoadingRequested(this);
      }
    }

    @Override
    public void preparePeriod(Callback callback, Allocator allocator, long positionUs) {
      this.callback = callback;
      this.allocator = allocator;
      sampleStreams = newSampleStreamArray(0);
      sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
      callback.onPeriodPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      manifestLoader.maybeThrowError();
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      return trackGroups;
    }

    @Override
    public SampleStream[] selectTracks(List<SampleStream> oldStreams,
        List<TrackSelection> newSelections, long positionUs) {
      int newEnabledSourceCount = sampleStreams.length + newSelections.size() - oldStreams.size();
      ChunkSampleStream<SsChunkSource>[] newSampleStreams =
          newSampleStreamArray(newEnabledSourceCount);
      int newEnabledSourceIndex = 0;

      // Iterate over currently enabled streams, either releasing them or adding them to the new
      // list.
      for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
        if (oldStreams.contains(sampleStream)) {
          sampleStream.release();
        } else {
          newSampleStreams[newEnabledSourceIndex++] = sampleStream;
        }
      }

      // Instantiate and return new streams.
      SampleStream[] streamsToReturn = new SampleStream[newSelections.size()];
      for (int i = 0; i < newSelections.size(); i++) {
        newSampleStreams[newEnabledSourceIndex] =
            buildSampleStream(newSelections.get(i), positionUs);
        streamsToReturn[i] = newSampleStreams[newEnabledSourceIndex];
        newEnabledSourceIndex++;
      }

      sampleStreams = newSampleStreams;
      sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
      return streamsToReturn;
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return sequenceableLoader.continueLoading(positionUs);
    }

    @Override
    public long getNextLoadPositionUs() {
      return sequenceableLoader.getNextLoadPositionUs();
    }

    @Override
    public long readDiscontinuity() {
      return C.UNSET_TIME_US;
    }

    @Override
    public long getBufferedPositionUs() {
      long bufferedPositionUs = Long.MAX_VALUE;
      for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
        long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
        if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
          bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
        }
      }
      return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
    }

    @Override
    public long seekToUs(long positionUs) {
      for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
        sampleStream.seekToUs(positionUs);
      }
      return positionUs;
    }

    @Override
    public void releasePeriod() {
      if (sampleStreams != null) {
        for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
          sampleStream.release();
        }
        sampleStreams = null;
      }
      sequenceableLoader = null;
      callback = null;
      allocator = null;
    }

    // SequenceableLoader.Callback implementation

    @Override
    public void onContinueLoadingRequested(ChunkSampleStream<SsChunkSource> sampleStream) {
      callback.onContinueLoadingRequested(this);
    }

    // Private methods.

    private ChunkSampleStream<SsChunkSource> buildSampleStream(TrackSelection selection,
        long positionUs) {
      int streamElementIndex = trackGroups.indexOf(selection.group);
      SsChunkSource chunkSource = chunkSourceFactory.createChunkSource(manifestLoader, manifest,
          streamElementIndex, selection, trackEncryptionBoxes);
      return new ChunkSampleStream<>(manifest.streamElements[streamElementIndex].type, chunkSource,
          this, allocator, positionUs, minLoadableRetryCount, eventDispatcher);
    }

    private static TrackGroupArray buildTrackGroups(SsManifest manifest) {
      TrackGroup[] trackGroupArray = new TrackGroup[manifest.streamElements.length];
      for (int i = 0; i < manifest.streamElements.length; i++) {
        trackGroupArray[i++] = new TrackGroup(manifest.streamElements[i].formats);
      }
      return new TrackGroupArray(trackGroupArray);
    }

    @SuppressWarnings("unchecked")
    private static ChunkSampleStream<SsChunkSource>[] newSampleStreamArray(int length) {
      return new ChunkSampleStream[length];
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

  // Loader.Callback implementation

  @Override
  public void onLoadCompleted(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    manifest = loadable.getResult();
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    if (period == null) {
      period = new SsMediaPeriod(manifest, chunkSourceFactory, minLoadableRetryCount,
          eventDispatcher, manifestLoader);
      Timeline timeline = manifest.durationUs == C.UNSET_TIME_US
          ? new SinglePeriodTimeline(this, manifest)
          : new SinglePeriodTimeline(this, manifest, manifest.durationUs / 1000);
      invalidationListener.onTimelineChanged(timeline);
    } else {
      period.updateManifest(manifest);
    }
    scheduleManifestRefresh();
  }

  @Override
  public void onLoadCanceled(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  @Override
  public int onLoadError(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    boolean isFatal = error instanceof ParserException;
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded(), error, isFatal);
    return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
  }

  // Internal methods

  private void scheduleManifestRefresh() {
    if (!manifest.isLive) {
      return;
    }
    long nextLoadTimestamp = manifestLoadStartTimestamp + MINIMUM_MANIFEST_REFRESH_PERIOD_MS;
    long delayUntilNextLoad = Math.max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
    manifestRefreshHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        startLoadingManifest();
      }
    }, delayUntilNextLoad);
  }

  private void startLoadingManifest() {
    ParsingLoadable<SsManifest> loadable = new ParsingLoadable<>(manifestDataSource,
        manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
    long elapsedRealtimeMs = manifestLoader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

}
