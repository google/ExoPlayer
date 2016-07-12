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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
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
import java.util.ArrayList;
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

  private static final String TAG = "DashMediaSource";

  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final DashManifestParser manifestParser;
  private final ManifestCallback manifestCallback;

  private MediaSource.InvalidationListener invalidationListener;
  private DataSource dataSource;
  private Loader loader;

  private Uri manifestUri;
  private long manifestLoadStartTimestamp;
  private long manifestLoadEndTimestamp;
  private DashManifest manifest;
  private Handler manifestRefreshHandler;
  private ArrayList<DashMediaPeriod> periods;
  private long elapsedRealtimeOffset;

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
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(InvalidationListener listener) {
    invalidationListener = listener;
    dataSource = manifestDataSourceFactory.createDataSource();
    loader = new Loader("Loader:DashMediaSource");
    manifestRefreshHandler = new Handler();
    startLoadingManifest();
  }

  @Override
  public int getNewPlayingPeriodIndex(int oldPlayingPeriodIndex, Timeline oldTimeline) {
    int periodIndex = oldPlayingPeriodIndex;
    int oldPeriodCount = oldTimeline.getPeriodCount();
    while (oldPeriodCount == Timeline.UNKNOWN_PERIOD_COUNT || periodIndex < oldPeriodCount) {
      Object id = oldTimeline.getPeriodId(periodIndex);
      if (id == null) {
        break;
      }
      int index = periods.indexOf(id);
      if (index != -1) {
        return index;
      }
      periodIndex++;
    }
    return Timeline.NO_PERIOD_INDEX;
  }

  @Override
  public MediaPeriod createPeriod(int index) throws IOException {
    if (periods == null || periods.size() <= index) {
      loader.maybeThrowError();
      return null;
    }
    return periods.get(index);
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
    if (manifestRefreshHandler != null) {
      manifestRefreshHandler.removeCallbacksAndMessages(null);
      manifestRefreshHandler = null;
    }
    elapsedRealtimeOffset = 0;
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(ParsingLoadable<DashManifest> loadable,
      long elapsedRealtimeMs, long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    DashManifest newManifest = loadable.getResult();

    int periodsToRemoveCount = 0;
    if (periods != null) {
      int periodCount = periods.size();
      long newFirstPeriodStartTimeUs = newManifest.getPeriod(0).startMs * 1000;
      while (periodsToRemoveCount < periodCount
          && periods.get(periodsToRemoveCount).getStartUs() < newFirstPeriodStartTimeUs) {
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
    }

    manifest = newManifest;
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    manifestLoadEndTimestamp = elapsedRealtimeMs;
    if (manifest.location != null) {
      manifestUri = manifest.location;
    }

    if (periods == null) {
      if (manifest.utcTiming != null) {
        resolveUtcTimingElement(manifest.utcTiming);
      } else {
        finishManifestProcessing();
      }
    } else {
      // Remove old periods.
      while (periodsToRemoveCount-- > 0) {
        periods.remove(0);
      }

      // Update existing periods. Only the first and the last periods can change.
      int periodCount = periods.size();
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
    periods.get(index).updateManifest(manifest, index);
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
    this.elapsedRealtimeOffset = elapsedRealtimeOffsetMs;
    finishManifestProcessing();
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve UtcTiming element.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    finishManifestProcessing();
  }

  private void finishManifestProcessing() {
    if (periods == null) {
      periods = new ArrayList<>();
    }
    int periodCount = manifest.getPeriodCount();
    for (int i = periods.size(); i < periodCount; i++) {
      periods.add(new DashMediaPeriod(manifest, i, chunkSourceFactory, minLoadableRetryCount,
          eventDispatcher, elapsedRealtimeOffset, loader));
    }
    invalidationListener.onTimelineChanged(new DashTimeline(manifest,
        periods.toArray(new DashMediaPeriod[periods.size()])));
    scheduleManifestRefresh();
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
    manifestRefreshHandler.postDelayed(new Runnable() {
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

  private static final class DashTimeline implements Timeline {

    private final DashManifest manifest;
    private final DashMediaPeriod[] periods;

    public DashTimeline(DashManifest manifest, DashMediaPeriod[] periods) {
      this.manifest = manifest;
      this.periods = periods;
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
    public long getPeriodDuration(int index) {
      return manifest.getPeriodDuration(index);
    }

    @Override
    public Object getPeriodId(int index) {
      return index >= periods.length ? null : periods[index];
    }

    @Override
    public int getIndexOfPeriod(Object id) {
      for (int i = 0; i < periods.length; i++) {
        if (id == periods[i]) {
          return i;
        }
      }
      return Timeline.NO_PERIOD_INDEX;
    }

    @Override
    public Object getManifest() {
      return manifest;
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
