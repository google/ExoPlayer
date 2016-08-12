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
package com.google.android.exoplayer2.source.dash;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SeekWindow;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A DASH {@link MediaSource}.
 */
public final class DashMediaSource implements MediaSource {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
  /**
   * The interval in milliseconds between invocations of
   * {@link MediaSource.Listener#onSourceInfoRefreshed(Timeline, Object)} when the source's
   * {@link SeekWindow} is changing dynamically (for example, for incomplete live streams).
   */
  private static final int NOTIFY_MANIFEST_INTERVAL_MS = 5000;
  /**
   * The offset in milliseconds subtracted from the live edge position when calculating the default
   * position returned by {@link #getDefaultStartPosition(int)}.
   */
  private static final long LIVE_EDGE_OFFSET_MS = 30000;

  private static final String TAG = "DashMediaSource";

  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final DashManifestParser manifestParser;
  private final ManifestCallback manifestCallback;
  private final Object manifestUriLock;
  private final SparseArray<DashMediaPeriod> periodsById;
  private final Runnable refreshSourceInfoRunnable;

  private MediaSource.Listener sourceListener;
  private DataSource dataSource;
  private Loader loader;

  private Uri manifestUri;
  private long manifestLoadStartTimestamp;
  private long manifestLoadEndTimestamp;
  private DashManifest manifest;
  private Handler handler;
  private SeekWindow seekWindow;
  private long elapsedRealtimeOffsetMs;

  private int firstPeriodId;

  public DashMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
        DEFAULT_MIN_LOADABLE_RETRY_COUNT, eventHandler, eventListener);
  }

  public DashMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      Handler eventHandler, AdaptiveMediaSourceEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    manifestParser = new DashManifestParser();
    manifestCallback = new ManifestCallback();
    manifestUriLock = new Object();
    periodsById = new SparseArray<>();
    refreshSourceInfoRunnable = new Runnable() {
      @Override
      public void run() {
        refreshSourceInfo();
      }
    };
  }

  /**
   * Manually replaces the manifest {@link Uri}.
   *
   * @param manifestUri The replacement manifest {@link Uri}.
   */
  public void replaceManifestUri(Uri manifestUri) {
    synchronized (manifestUriLock) {
      this.manifestUri = manifestUri;
    }
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(MediaSource.Listener listener) {
    sourceListener = listener;
    dataSource = manifestDataSourceFactory.createDataSource();
    loader = new Loader("Loader:DashMediaSource");
    handler = new Handler();
    startLoadingManifest();
  }

  @Override
  public int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldTimeline) {
    // Seek to the default position, which is the live edge for live sources.
    return 0;
  }

  @Override
  public Position getDefaultStartPosition(int index) {
    if (seekWindow == null) {
      return null;
    }

    if (index == 0 && manifest.dynamic) {
      // The stream is live, so return a position a position offset from the live edge.
      int periodIndex = seekWindow.endPeriodIndex;
      long positionMs = seekWindow.endTimeMs - LIVE_EDGE_OFFSET_MS;
      while (positionMs < 0 && periodIndex > seekWindow.startPeriodIndex) {
        periodIndex--;
        positionMs += manifest.getPeriodDurationMs(periodIndex);
      }
      positionMs = Math.max(positionMs,
          periodIndex == seekWindow.startPeriodIndex ? seekWindow.startTimeMs : 0);
      return new Position(periodIndex, positionMs * 1000);
    }
    return new Position(index, 0);
  }

  @Override
  public MediaPeriod createPeriod(int index) throws IOException {
    if (index >= manifest.getPeriodCount()) {
      loader.maybeThrowError();
      return null;
    }
    DashMediaPeriod mediaPeriod = new DashMediaPeriod(manifest, index, chunkSourceFactory,
        minLoadableRetryCount, eventDispatcher, elapsedRealtimeOffsetMs, loader);
    periodsById.put(firstPeriodId + index, mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releaseSource() {
    dataSource = null;
    if (loader != null) {
      loader.release();
      loader = null;
    }
    manifestLoadStartTimestamp = 0;
    manifestLoadEndTimestamp = 0;
    manifest = null;
    if (handler != null) {
      handler.removeCallbacksAndMessages(null);
      handler = null;
    }
    elapsedRealtimeOffsetMs = 0;
    periodsById.clear();
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(ParsingLoadable<DashManifest> loadable,
      long elapsedRealtimeMs, long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    DashManifest newManifest = loadable.getResult();

    int periodCount = manifest == null ? 0 : manifest.getPeriodCount();
    int periodsToRemoveCount = 0;
    long newFirstPeriodStartTimeMs = newManifest.getPeriod(0).startMs;
    while (periodsToRemoveCount < periodCount
        && manifest.getPeriod(periodsToRemoveCount).startMs < newFirstPeriodStartTimeMs) {
      periodsToRemoveCount++;
    }

    // After discarding old periods, we should never have more periods than listed in the new
    // manifest. That would mean that a previously announced period is no longer advertised. If
    // this condition occurs, assume that we are hitting a manifest server that is out of sync and
    // behind, discard this manifest, and try again later.
    if (periodCount - periodsToRemoveCount > newManifest.getPeriodCount()) {
      Log.w(TAG, "Out of sync manifest");
      scheduleManifestRefresh();
      return;
    }

    manifest = newManifest;
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    manifestLoadEndTimestamp = elapsedRealtimeMs;
    if (manifest.location != null) {
      synchronized (manifestUriLock) {
        // This condition checks that replaceManifestUri wasn't called between the start and end of
        // this load. If it was, we ignore the manifest location and prefer the manual replacement.
        if (loadable.dataSpec.uri == manifestUri) {
          manifestUri = manifest.location;
        }
      }
    }

    if (periodCount == 0) {
      if (manifest.utcTiming != null) {
        resolveUtcTimingElement(manifest.utcTiming);
      } else {
        finishManifestProcessing();
      }
    } else {
      // Remove old periods.
      while (periodsToRemoveCount-- > 0) {
        periodsById.remove(firstPeriodId);
        firstPeriodId++;
        periodCount--;
      }

      // Update existing periods. Only the first and the last periods can change.
      if (periodCount > 0) {
        updatePeriod(0);
        if (periodCount > 1) {
          updatePeriod(periodCount - 1);
        }
      }

      finishManifestProcessing();
    }
  }

  private void updatePeriod(int index) {
    DashMediaPeriod period = periodsById.get(firstPeriodId + index);
    if (period != null) {
      period.updateManifest(manifest, index);
    }
  }

  /* package */ int onManifestLoadError(ParsingLoadable<DashManifest> loadable,
      long elapsedRealtimeMs, long loadDurationMs, IOException error) {
    boolean isFatal = error instanceof ParserException;
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded(), error, isFatal);
    return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
  }

  /* package */ void onUtcTimestampLoadCompleted(ParsingLoadable<Long> loadable,
      long elapsedRealtimeMs, long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    onUtcTimestampResolved(loadable.getResult() - elapsedRealtimeMs);
  }

  /* package */ int onUtcTimestampLoadError(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded(), error, true);
    onUtcTimestampResolutionError(error);
    return Loader.DONT_RETRY;
  }

  /* package */ void onLoadCanceled(ParsingLoadable<?> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  // Internal methods.

  private void startLoadingManifest() {
    Uri manifestUri;
    synchronized (manifestUriLock) {
      manifestUri = this.manifestUri;
    }
    startLoading(new ParsingLoadable<>(dataSource, manifestUri, C.DATA_TYPE_MANIFEST,
        manifestParser), manifestCallback, minLoadableRetryCount);
  }

  private void resolveUtcTimingElement(UtcTimingElement timingElement) {
    String scheme = timingElement.schemeIdUri;
    if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")) {
      resolveUtcTimingElementDirect(timingElement);
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")) {
      resolveUtcTimingElementHttp(timingElement, new Iso8601Parser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")) {
      resolveUtcTimingElementHttp(timingElement, new XsDateTimeParser());
    } else {
      // Unsupported scheme.
      onUtcTimestampResolutionError(new IOException("Unsupported UTC timing scheme"));
    }
  }

  private void resolveUtcTimingElementDirect(UtcTimingElement timingElement) {
    try {
      long utcTimestamp = Util.parseXsDateTime(timingElement.value);
      onUtcTimestampResolved(utcTimestamp - manifestLoadEndTimestamp);
    } catch (ParseException e) {
      onUtcTimestampResolutionError(new ParserException(e));
    }
  }

  private void resolveUtcTimingElementHttp(UtcTimingElement timingElement,
      ParsingLoadable.Parser<Long> parser) {
    startLoading(new ParsingLoadable<>(dataSource, Uri.parse(timingElement.value),
        C.DATA_TYPE_TIME_SYNCHRONIZATION, parser), new UtcTimestampCallback(), 1);
  }

  private void onUtcTimestampResolved(long elapsedRealtimeOffsetMs) {
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    finishManifestProcessing();
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve UtcTiming element.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    finishManifestProcessing();
  }

  private void finishManifestProcessing() {
    handler.removeCallbacks(refreshSourceInfoRunnable);
    refreshSourceInfo();
    scheduleManifestRefresh();
  }

  private void refreshSourceInfo() {
    // Update the seek window.
    int lastPeriodIndex = manifest.getPeriodCount() - 1;
    PeriodSeekInfo firstPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(manifest.getPeriod(0),
        manifest.getPeriodDurationUs(0));
    PeriodSeekInfo lastPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
        manifest.getPeriod(lastPeriodIndex), manifest.getPeriodDurationUs(lastPeriodIndex));
    long currentStartTimeUs;
    long currentEndTimeUs;
    if (manifest.dynamic && !lastPeriodSeekInfo.isIndexExplicit) {
      // The seek window is changing so post a Runnable to update it.
      handler.postDelayed(refreshSourceInfoRunnable, NOTIFY_MANIFEST_INTERVAL_MS);

      long minStartPositionUs = firstPeriodSeekInfo.availableStartTimeUs;
      long maxEndPositionUs = lastPeriodSeekInfo.availableEndTimeUs;
      long timeShiftBufferDepthUs = manifest.timeShiftBufferDepth == -1 ? -1
          : manifest.timeShiftBufferDepth * 1000;
      currentEndTimeUs = Math.min(maxEndPositionUs,
          getNowUnixTimeUs() - manifest.availabilityStartTime * 1000);
      currentStartTimeUs = timeShiftBufferDepthUs == -1 ? minStartPositionUs
          : Math.max(minStartPositionUs, currentEndTimeUs - timeShiftBufferDepthUs);
    } else {
      handler.removeCallbacks(refreshSourceInfoRunnable);
      currentStartTimeUs = firstPeriodSeekInfo.availableStartTimeUs;
      currentEndTimeUs = lastPeriodSeekInfo.availableEndTimeUs;
    }
    seekWindow = SeekWindow.createWindow(0, currentStartTimeUs, lastPeriodIndex, currentEndTimeUs);
    sourceListener.onSourceInfoRefreshed(new DashTimeline(firstPeriodId, manifest, seekWindow),
        manifest);
  }

  private void scheduleManifestRefresh() {
    if (!manifest.dynamic) {
      return;
    }
    long minUpdatePeriod = manifest.minUpdatePeriod;
    if (minUpdatePeriod == 0) {
      // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
      // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
      // signaling in the stream, according to:
      // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
      minUpdatePeriod = 5000;
    }
    long nextLoadTimestamp = manifestLoadStartTimestamp + minUpdatePeriod;
    long delayUntilNextLoad = Math.max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        startLoadingManifest();
      }
    }, delayUntilNextLoad);
  }

  private <T> void startLoading(ParsingLoadable<T> loadable,
      Loader.Callback<ParsingLoadable<T>> callback, int minRetryCount) {
    long elapsedRealtimeMs = loader.startLoading(loadable, callback, minRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetMs != 0) {
      return (SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs) * 1000;
    } else {
      return System.currentTimeMillis() * 1000;
    }
  }

  private static final class PeriodSeekInfo {

    public static PeriodSeekInfo createPeriodSeekInfo(Period period, long durationUs) {
      int adaptationSetCount = period.adaptationSets.size();
      long availableStartTimeUs = 0;
      long availableEndTimeUs = Long.MAX_VALUE;
      boolean isIndexExplicit = false;
      for (int i = 0; i < adaptationSetCount; i++) {
        DashSegmentIndex index = period.adaptationSets.get(i).representations.get(0).getIndex();
        if (index == null) {
          return new PeriodSeekInfo(true, 0, durationUs);
        }
        int firstSegmentNum = index.getFirstSegmentNum();
        int lastSegmentNum = index.getLastSegmentNum(durationUs);
        isIndexExplicit |= index.isExplicit();
        long adaptationSetAvailableStartTimeUs = index.getTimeUs(firstSegmentNum);
        availableStartTimeUs = Math.max(availableStartTimeUs, adaptationSetAvailableStartTimeUs);
        if (lastSegmentNum != DashSegmentIndex.INDEX_UNBOUNDED) {
          long adaptationSetAvailableEndTimeUs = index.getTimeUs(lastSegmentNum)
              + index.getDurationUs(lastSegmentNum, durationUs);
          availableEndTimeUs = Math.min(availableEndTimeUs, adaptationSetAvailableEndTimeUs);
        } else {
          // The available end time is unmodified, because this index is unbounded.
        }
      }
      return new PeriodSeekInfo(isIndexExplicit, availableStartTimeUs, availableEndTimeUs);
    }

    public final boolean isIndexExplicit;
    public final long availableStartTimeUs;
    public final long availableEndTimeUs;

    private PeriodSeekInfo(boolean isIndexExplicit, long availableStartTimeUs,
        long availableEndTimeUs) {
      this.isIndexExplicit = isIndexExplicit;
      this.availableStartTimeUs = availableStartTimeUs;
      this.availableEndTimeUs = availableEndTimeUs;
    }

  }

  private static final class DashTimeline implements Timeline {

    private final int firstPeriodId;
    private final DashManifest manifest;
    private final SeekWindow seekWindow;

    public DashTimeline(int firstPeriodId, DashManifest manifest, SeekWindow seekWindow) {
      this.firstPeriodId = firstPeriodId;
      this.manifest = manifest;
      this.seekWindow = seekWindow;
    }

    @Override
    public int getPeriodCount() {
      return manifest.getPeriodCount();
    }

    @Override
    public boolean isFinal() {
      return !manifest.dynamic;
    }

    @Override
    public long getAbsoluteStartTime() {
      return manifest.availabilityStartTime + manifest.getPeriod(0).startMs;
    }

    @Override
    public long getPeriodDurationMs(int index) {
      if (index < 0 || index >= manifest.getPeriodCount()) {
        throw new IndexOutOfBoundsException();
      }
      return manifest.getPeriodDurationMs(index);
    }

    @Override
    public long getPeriodDurationUs(int index) {
      if (index < 0 || index >= manifest.getPeriodCount()) {
        throw new IndexOutOfBoundsException();
      }
      return manifest.getPeriodDurationUs(index);
    }

    @Override
    public Object getPeriodId(int index) {
      return firstPeriodId + index;
    }

    @Override
    public int getIndexOfPeriod(Object id) {
      return ((Integer) id) - firstPeriodId;
    }

    @Override
    public int getSeekWindowCount() {
      return 1;
    }

    @Override
    public SeekWindow getSeekWindow(int index) {
      return seekWindow;
    }

  }

  private final class ManifestCallback implements
      Loader.Callback<ParsingLoadable<DashManifest>> {

    @Override
    public void onLoadCompleted(ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs, long loadDurationMs) {
      onManifestLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs, long loadDurationMs, boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public int onLoadError(ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs, long loadDurationMs, IOException error) {
      return onManifestLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }

  }

  private final class UtcTimestampCallback implements Loader.Callback<ParsingLoadable<Long>> {

    @Override
    public void onLoadCompleted(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
        long loadDurationMs) {
      onUtcTimestampLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
        long loadDurationMs, boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public int onLoadError(ParsingLoadable<Long> loadable, long elapsedRealtimeMs,
        long loadDurationMs, IOException error) {
      return onUtcTimestampLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }

  }

  private static final class XsDateTimeParser implements ParsingLoadable.Parser<Long> {

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      try {
        return Util.parseXsDateTime(firstLine);
      } catch (ParseException e) {
        throw new ParserException(e);
      }
    }

  }

  private static final class Iso8601Parser implements ParsingLoadable.Parser<Long> {

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      try {
        // TODO: It may be necessary to handle timestamp offsets from UTC.
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(firstLine).getTime();
      } catch (ParseException e) {
        throw new ParserException(e);
      }
    }

  }

}
