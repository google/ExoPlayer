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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroup;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.TrackSelection;
import com.google.android.exoplayer.TrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStream;
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionParser;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.dash.mpd.UtcTimingElement;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.UriLoadable;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A {@link SampleSource} for DASH media.
 */
public final class DashSampleSource implements SampleSource {

  private static final String TAG = "DashSampleSource";

  /**
   * The minimum number of times to retry loading data prior to failing.
   */
  private static final int MIN_LOADABLE_RETRY_COUNT = 3;

  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final Handler eventHandler;
  private final ChunkTrackStreamEventListener eventListener;
  private final LoadControl loadControl;
  private final Loader loader;
  private final DataSource dataSource;
  private final MediaPresentationDescriptionParser manifestParser;
  private final ManifestCallback manifestCallback;

  private Uri manifestUri;
  private long manifestLoadStartTimestamp;
  private long manifestLoadEndTimestamp;
  private MediaPresentationDescription manifest;

  private boolean prepared;
  private long durationUs;
  private long elapsedRealtimeOffset;
  private TrackGroupArray trackGroups;
  private int[] trackGroupAdaptationSetIndices;
  private boolean pendingReset;
  private long lastSeekPositionUs;
  private ChunkTrackStream<DashChunkSource>[] trackStreams;

  public DashSampleSource(Uri manifestUri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    loadControl = new DefaultLoadControl(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    loader = new Loader("Loader:DashSampleSource");
    dataSource = dataSourceFactory.createDataSource();
    manifestParser = new MediaPresentationDescriptionParser();
    manifestCallback = new ManifestCallback();
    trackStreams = newTrackStreamArray(0);
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (prepared) {
      return true;
    }
    loader.maybeThrowError();
    if (!loader.isLoading() && manifest == null) {
      startLoadingManifest();
    }
    return false;
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
  public TrackStream[] selectTracks(List<TrackStream> oldStreams,
      List<TrackSelection> newSelections, long positionUs) {
    int newEnabledSourceCount = trackStreams.length + newSelections.size() - oldStreams.size();
    ChunkTrackStream<DashChunkSource>[] newTrackStreams =
        newTrackStreamArray(newEnabledSourceCount);
    int newEnabledSourceIndex = 0;

    // Iterate over currently enabled streams, either releasing them or adding them to the new list.
    for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
      if (oldStreams.contains(trackStream)) {
        trackStream.release();
      } else {
        newTrackStreams[newEnabledSourceIndex++] = trackStream;
      }
    }

    // Instantiate and return new streams.
    TrackStream[] streamsToReturn = new TrackStream[newSelections.size()];
    for (int i = 0; i < newSelections.size(); i++) {
      newTrackStreams[newEnabledSourceIndex] = buildTrackStream(newSelections.get(i), positionUs);
      streamsToReturn[i] = newTrackStreams[newEnabledSourceIndex];
      newEnabledSourceIndex++;
    }

    trackStreams = newTrackStreams;
    return streamsToReturn;
  }

  @Override
  public void continueBuffering(long positionUs) {
    if (manifest.dynamic) {
      long minUpdatePeriod = manifest.minUpdatePeriod;
      if (minUpdatePeriod == 0) {
        // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
        // minUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is explicit
        // signaling in the stream, according to:
        // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service/
        minUpdatePeriod = 5000;
      }
      if (!loader.isLoading()
          && SystemClock.elapsedRealtime() > manifestLoadStartTimestamp + minUpdatePeriod) {
        startLoadingManifest();
      }
    }
    for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
      trackStream.continueBuffering(positionUs);
    }
  }

  @Override
  public long readReset() {
    if (pendingReset) {
      pendingReset = false;
      for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
        trackStream.setReadingEnabled(true);
      }
      return lastSeekPositionUs;
    }
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
      long rendererBufferedPositionUs = trackStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    pendingReset = true;
    for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
      trackStream.setReadingEnabled(false);
      trackStream.seekToUs(positionUs);
    }
  }

  @Override
  public void release() {
    loader.release();
    for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
      trackStream.release();
    }
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(MediaPresentationDescription manifest,
      long elapsedMs) {
    this.manifest = manifest;
    manifestLoadEndTimestamp = SystemClock.elapsedRealtime();
    manifestLoadStartTimestamp = manifestLoadEndTimestamp - elapsedMs;
    if (manifest.location != null) {
      manifestUri = manifest.location;
    }
    if (!prepared) {
      durationUs = manifest.dynamic ? C.UNSET_TIME_US : manifest.duration * 1000;
      buildTrackGroups(manifest);
      if (manifest.utcTiming != null) {
        resolveUtcTimingElement(manifest.utcTiming);
      } else {
        prepared = true;
      }
    } else {
      for (ChunkTrackStream<DashChunkSource> trackStream : trackStreams) {
        trackStream.getChunkSource().updateManifest(manifest);
      }
    }
  }

  /* package */ void onUtcTimestampLoadCompleted(long elapsedRealtimeOffset) {
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    prepared = true;
  }

  /* package */ void onUtcTimestampLoadError(IOException e) {
    Log.e(TAG, "Failed to resolve UtcTiming element.", e);
    // Be optimistic and continue in the hope that the device clock is correct.
    prepared = true;
  }

  // Internal methods.

  private void startLoadingManifest() {
    loader.startLoading(new UriLoadable<>(manifestUri, dataSource, manifestParser),
        manifestCallback, MIN_LOADABLE_RETRY_COUNT);
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
      onUtcTimestampLoadError(new IOException("Unsupported UTC timing scheme"));
    }
  }

  private void resolveUtcTimingElementDirect(UtcTimingElement timingElement) {
    try {
      long utcTimestamp = Util.parseXsDateTime(timingElement.value);
      long elapsedRealtimeOffset = utcTimestamp - manifestLoadEndTimestamp;
      onUtcTimestampLoadCompleted(elapsedRealtimeOffset);
    } catch (ParseException e) {
      onUtcTimestampLoadError(new ParserException(e));
    }
  }

  private void resolveUtcTimingElementHttp(UtcTimingElement timingElement,
      UriLoadable.Parser<Long> parser) {
    loader.startLoading(new UriLoadable<>(Uri.parse(timingElement.value), dataSource, parser),
        new UtcTimestampCallback(), 1);
  }

  private void buildTrackGroups(MediaPresentationDescription manifest) {
    Period period = manifest.getPeriod(0);
    int trackGroupCount = 0;
    trackGroupAdaptationSetIndices = new int[period.adaptationSets.size()];
    TrackGroup[] trackGroupArray = new TrackGroup[period.adaptationSets.size()];
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      int adaptationSetType = adaptationSet.type;
      List<Representation> representations = adaptationSet.representations;
      if (!representations.isEmpty() && (adaptationSetType == C.TRACK_TYPE_AUDIO
          || adaptationSetType == C.TRACK_TYPE_VIDEO || adaptationSetType == C.TRACK_TYPE_TEXT)) {
        Format[] formats = new Format[representations.size()];
        for (int j = 0; j < formats.length; j++) {
          formats[j] = representations.get(j).format;
        }
        trackGroupAdaptationSetIndices[trackGroupCount] = i;
        boolean adaptive = adaptationSetType == C.TRACK_TYPE_VIDEO;
        trackGroupArray[trackGroupCount++] = new TrackGroup(adaptive, formats);
      }
    }
    if (trackGroupCount < trackGroupArray.length) {
      trackGroupAdaptationSetIndices = Arrays.copyOf(trackGroupAdaptationSetIndices,
          trackGroupCount);
      trackGroupArray = Arrays.copyOf(trackGroupArray, trackGroupCount);
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
  }

  private ChunkTrackStream<DashChunkSource> buildTrackStream(TrackSelection selection,
      long positionUs) {
    int[] selectedTracks = selection.getTracks();
    FormatEvaluator adaptiveEvaluator = selectedTracks.length > 1
        ? new AdaptiveEvaluator(bandwidthMeter) : null;
    int adaptationSetIndex = trackGroupAdaptationSetIndices[selection.group];
    AdaptationSet adaptationSet = manifest.getPeriod(0).adaptationSets.get(
        adaptationSetIndex);
    int adaptationSetType = adaptationSet.type;
    int bufferSize = Util.getDefaultBufferSize(adaptationSetType);
    DataSource dataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    DashChunkSource chunkSource = new DashChunkSource(loader, manifest, adaptationSetIndex,
        trackGroups.get(selection.group), selectedTracks, dataSource, adaptiveEvaluator,
        elapsedRealtimeOffset);
    return new ChunkTrackStream<>(chunkSource, loadControl, bufferSize, positionUs, eventHandler,
        eventListener, adaptationSetType, MIN_LOADABLE_RETRY_COUNT);
  }

  @SuppressWarnings("unchecked")
  private static ChunkTrackStream<DashChunkSource>[] newTrackStreamArray(int length) {
    return new ChunkTrackStream[length];
  }

  private final class ManifestCallback implements
      Loader.Callback<UriLoadable<MediaPresentationDescription>> {

    @Override
    public void onLoadCompleted(UriLoadable<MediaPresentationDescription> loadable,
        long elapsedMs) {
      onManifestLoadCompleted(loadable.getResult(), elapsedMs);
    }

    @Override
    public void onLoadCanceled(UriLoadable<MediaPresentationDescription> loadable, long elapsedMs,
        boolean released) {
      // Do nothing.
    }

    @Override
    public int onLoadError(UriLoadable<MediaPresentationDescription> loadable, long elapsedMs,
        IOException exception) {
      return (exception instanceof ParserException) ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
    }

  }

  private final class UtcTimestampCallback implements Loader.Callback<UriLoadable<Long>> {

    @Override
    public void onLoadCompleted(UriLoadable<Long> loadable, long elapsedMs) {
      onUtcTimestampLoadCompleted(loadable.getResult() - SystemClock.elapsedRealtime());
    }

    @Override
    public void onLoadCanceled(UriLoadable<Long> loadable, long elapsedMs, boolean released) {
      // Do nothing.
    }

    @Override
    public int onLoadError(UriLoadable<Long> loadable, long elapsedMs, IOException exception) {
      onUtcTimestampLoadError(exception);
      return Loader.DONT_RETRY;
    }

  }

  private static final class XsDateTimeParser implements UriLoadable.Parser<Long> {

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

  private static final class Iso8601Parser implements UriLoadable.Parser<Long> {

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
