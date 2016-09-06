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
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
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
   * A constant indicating that the presentation delay for live streams should be set to
   * {@link DashManifest#suggestedPresentationDelay} if specified by the manifest, or
   * {@link #DEFAULT_LIVE_PRESENTATION_DELAY_FIXED_MS} otherwise. The presentation delay is the
   * duration by which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS = -1;
  /**
   * A fixed default presentation delay for live streams. The presentation delay is the duration
   * by which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_FIXED_MS = 30000;

  /**
   * The interval in milliseconds between invocations of
   * {@link MediaSource.Listener#onSourceInfoRefreshed(Timeline, Object)} when the source's
   * {@link Timeline} is changing dynamically (for example, for incomplete live streams).
   */
  private static final int NOTIFY_MANIFEST_INTERVAL_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;

  private static final String TAG = "DashMediaSource";

  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final long livePresentationDelayMs;
  private final EventDispatcher eventDispatcher;
  private final DashManifestParser manifestParser;
  private final ManifestCallback manifestCallback;
  private final Object manifestUriLock;
  private final SparseArray<DashMediaPeriod> periodsById;
  private final Runnable refreshManifestRunnable;
  private final Runnable simulateManifestRefreshRunnable;

  private MediaSource.Listener sourceListener;
  private DataSource dataSource;
  private Loader loader;

  private Uri manifestUri;
  private long manifestLoadStartTimestamp;
  private long manifestLoadEndTimestamp;
  private DashManifest manifest;
  private Handler handler;
  private long elapsedRealtimeOffsetMs;

  private int firstPeriodId;

  public DashMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
        DEFAULT_MIN_LOADABLE_RETRY_COUNT, DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,
        eventHandler, eventListener);
  }

  public DashMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      DashChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      long livePresentationDelayMs, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.livePresentationDelayMs = livePresentationDelayMs;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    manifestParser = new DashManifestParser(generateContentId());
    manifestCallback = new ManifestCallback();
    manifestUriLock = new Object();
    periodsById = new SparseArray<>();
    refreshManifestRunnable = new Runnable() {
      @Override
      public void run() {
        startLoadingManifest();
      }
    };
    simulateManifestRefreshRunnable = new Runnable() {
      @Override
      public void run() {
        processManifest();
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
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    loader.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Callback callback, Allocator allocator,
      long positionUs) {
    DashMediaPeriod mediaPeriod = new DashMediaPeriod(firstPeriodId + index, manifest, index,
        chunkSourceFactory, minLoadableRetryCount, eventDispatcher, elapsedRealtimeOffsetMs, loader,
        callback, allocator);
    periodsById.put(mediaPeriod.id, mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    DashMediaPeriod dashMediaPeriod = (DashMediaPeriod) mediaPeriod;
    dashMediaPeriod.release();
    periodsById.remove(dashMediaPeriod.id);
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
    int removedPeriodCount = 0;
    long newFirstPeriodStartTimeMs = newManifest.getPeriod(0).startMs;
    while (removedPeriodCount < periodCount
        && manifest.getPeriod(removedPeriodCount).startMs < newFirstPeriodStartTimeMs) {
      removedPeriodCount++;
    }

    // After discarding old periods, we should never have more periods than listed in the new
    // manifest. That would mean that a previously announced period is no longer advertised. If
    // this condition occurs, assume that we are hitting a manifest server that is out of sync and
    // behind, discard this manifest, and try again later.
    if (periodCount - removedPeriodCount > newManifest.getPeriodCount()) {
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
        processManifestAndScheduleRefresh();
      }
    } else {
      firstPeriodId += removedPeriodCount;
      processManifestAndScheduleRefresh();
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
    processManifestAndScheduleRefresh();
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve UtcTiming element.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    processManifestAndScheduleRefresh();
  }

  private void processManifestAndScheduleRefresh() {
    processManifest();
    scheduleManifestRefresh();
  }

  private void processManifest() {
    // Update any periods.
    for (int i = 0; i < periodsById.size(); i++) {
      int id = periodsById.keyAt(i);
      if (id >= firstPeriodId) {
        periodsById.valueAt(i).updateManifest(manifest, id - firstPeriodId);
      } else {
        // This period has been removed from the manifest so it doesn't need to be updated.
      }
    }
    // Remove any pending simulated updates.
    handler.removeCallbacks(simulateManifestRefreshRunnable);
    // Update the window.
    int lastPeriodIndex = manifest.getPeriodCount() - 1;
    PeriodSeekInfo firstPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(manifest.getPeriod(0),
        manifest.getPeriodDurationUs(0));
    PeriodSeekInfo lastPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
        manifest.getPeriod(lastPeriodIndex), manifest.getPeriodDurationUs(lastPeriodIndex));
    // Get the period-relative start/end times.
    long currentStartTimeUs = firstPeriodSeekInfo.availableStartTimeUs;
    long currentEndTimeUs = lastPeriodSeekInfo.availableEndTimeUs;
    if (manifest.dynamic && !lastPeriodSeekInfo.isIndexExplicit) {
      // The manifest describes an incomplete live stream. Update the start/end times to reflect the
      // live stream duration and the manifest's time shift buffer depth.
      long liveStreamDurationUs = getNowUnixTimeUs() - C.msToUs(manifest.availabilityStartTime);
      long liveStreamEndPositionInLastPeriodUs = liveStreamDurationUs
          - C.msToUs(manifest.getPeriod(lastPeriodIndex).startMs);
      currentEndTimeUs = Math.min(liveStreamEndPositionInLastPeriodUs, currentEndTimeUs);
      if (manifest.timeShiftBufferDepth != C.TIME_UNSET) {
        long timeShiftBufferDepthUs = C.msToUs(manifest.timeShiftBufferDepth);
        long offsetInPeriodUs = currentEndTimeUs - timeShiftBufferDepthUs;
        int periodIndex = lastPeriodIndex;
        while (offsetInPeriodUs < 0 && periodIndex > 0) {
          offsetInPeriodUs += manifest.getPeriodDurationUs(--periodIndex);
        }
        if (periodIndex == 0) {
          currentStartTimeUs = Math.max(currentStartTimeUs, offsetInPeriodUs);
        } else {
          // The time shift buffer starts after the earliest period.
          // TODO: Does this ever happen?
          currentStartTimeUs = manifest.getPeriodDurationUs(0);
        }
      }
      // The window is changing implicitly. Post a simulated manifest refresh to update it.
      handler.postDelayed(simulateManifestRefreshRunnable, NOTIFY_MANIFEST_INTERVAL_MS);
    }
    long windowDurationUs = currentEndTimeUs - currentStartTimeUs;
    for (int i = 0; i < manifest.getPeriodCount() - 1; i++) {
      windowDurationUs += manifest.getPeriodDurationUs(i);
    }
    long windowDefaultStartPositionUs = 0;
    if (manifest.dynamic) {
      long presentationDelayForManifestMs = livePresentationDelayMs;
      if (presentationDelayForManifestMs == DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS) {
        presentationDelayForManifestMs = manifest.suggestedPresentationDelay != C.TIME_UNSET
            ? manifest.suggestedPresentationDelay : DEFAULT_LIVE_PRESENTATION_DELAY_FIXED_MS;
      }
      // Snap the default position to the start of the segment containing it.
      long defaultStartPositionUs = windowDurationUs - C.msToUs(presentationDelayForManifestMs);
      if (defaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
        // The default start position is too close to the start of the live window. Set it to the
        // minimum default start position provided the window is at least twice as big. Else set
        // it to the middle of the window.
        defaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US, windowDurationUs / 2);
      }

      int periodIndex = 0;
      long defaultStartPositionInPeriodUs = currentStartTimeUs + defaultStartPositionUs;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      while (periodIndex < manifest.getPeriodCount() - 1
          && defaultStartPositionInPeriodUs >= periodDurationUs) {
        defaultStartPositionInPeriodUs -= periodDurationUs;
        periodIndex++;
        periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      }
      Period period = manifest.getPeriod(periodIndex);
      int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
      if (videoAdaptationSetIndex != C.INDEX_UNSET) {
        // If there are multiple video adaptation sets with unaligned segments, the initial time may
        // not correspond to the start of a segment in both, but this is an edge case.
        DashSegmentIndex index =
            period.adaptationSets.get(videoAdaptationSetIndex).representations.get(0).getIndex();
        int segmentNum = index.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
        windowDefaultStartPositionUs =
            defaultStartPositionUs - defaultStartPositionInPeriodUs + index.getTimeUs(segmentNum);
      } else {
        windowDefaultStartPositionUs = defaultStartPositionUs;
      }
    }
    long windowStartTimeMs = manifest.availabilityStartTime
        + manifest.getPeriod(0).startMs + C.usToMs(currentStartTimeUs);
    DashTimeline timeline = new DashTimeline(manifest.availabilityStartTime, windowStartTimeMs,
        firstPeriodId, currentStartTimeUs, windowDurationUs, windowDefaultStartPositionUs,
        manifest);
    sourceListener.onSourceInfoRefreshed(timeline, manifest);
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
    handler.postDelayed(refreshManifestRunnable, delayUntilNextLoad);
  }

  private <T> void startLoading(ParsingLoadable<T> loadable,
      Loader.Callback<ParsingLoadable<T>> callback, int minRetryCount) {
    long elapsedRealtimeMs = loader.startLoading(loadable, callback, minRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetMs != 0) {
      return C.msToUs(SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs);
    } else {
      return C.msToUs(System.currentTimeMillis());
    }
  }

  private String generateContentId() {
    return Util.sha1(manifestUri.toString());
  }

  private static final class PeriodSeekInfo {

    public static PeriodSeekInfo createPeriodSeekInfo(
        com.google.android.exoplayer2.source.dash.manifest.Period period, long durationUs) {
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

  private static final class DashTimeline extends Timeline {

    private final long presentationStartTimeMs;
    private final long windowStartTimeMs;

    private final int firstPeriodId;
    private final long offsetInFirstPeriodUs;
    private final long windowDurationUs;
    private final long windowDefaultStartPositionUs;
    private final DashManifest manifest;

    public DashTimeline(long presentationStartTimeMs, long windowStartTimeMs,
        int firstPeriodId, long offsetInFirstPeriodUs, long windowDurationUs,
        long windowDefaultStartPositionUs, DashManifest manifest) {
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.firstPeriodId = firstPeriodId;
      this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
      this.windowDurationUs = windowDurationUs;
      this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
      this.manifest = manifest;
    }

    @Override
    public int getPeriodCount() {
      return manifest.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIdentifiers) {
      Assertions.checkIndex(periodIndex, 0, manifest.getPeriodCount());
      Object id = setIdentifiers ? manifest.getPeriod(periodIndex).id : null;
      Object uid = setIdentifiers ? firstPeriodId
          + Assertions.checkIndex(periodIndex, 0, manifest.getPeriodCount()) : null;
      return period.set(id, uid, 0, manifest.getPeriodDurationUs(periodIndex),
          C.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
              - offsetInFirstPeriodUs);
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIdentifier) {
      Assertions.checkIndex(windowIndex, 0, 1);
      return window.set(null, presentationStartTimeMs, windowStartTimeMs, true /* isSeekable */,
          manifest.dynamic, windowDefaultStartPositionUs, windowDurationUs, 0,
          manifest.getPeriodCount() - 1, offsetInFirstPeriodUs);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int periodId = (int) uid;
      return periodId < firstPeriodId || periodId >= firstPeriodId + getPeriodCount()
          ? C.INDEX_UNSET : (periodId - firstPeriodId);
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
