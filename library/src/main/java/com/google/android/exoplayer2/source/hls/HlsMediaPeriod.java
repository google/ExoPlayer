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
import android.os.Handler;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A {@link MediaPeriod} that loads an HLS stream.
 */
/* package */ final class HlsMediaPeriod implements MediaPeriod,
    Loader.Callback<ParsingLoadable<HlsPlaylist>>, HlsSampleStreamWrapper.Callback  {

  private final DataSource.Factory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final MediaSource.Listener sourceListener;
  private final Callback callback;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final HlsPlaylistParser manifestParser;
  private final Handler continueLoadingHandler;
  private final Loader manifestFetcher;
  private final long preparePositionUs;

  private int pendingPrepareCount;
  private HlsPlaylist playlist;
  private boolean seenFirstTrackSelection;
  private long durationUs;
  private boolean isLive;
  private TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  private CompositeSequenceableLoader sequenceableLoader;
  private Runnable continueLoadingRunnable;

  public HlsMediaPeriod(Uri manifestUri, DataSource.Factory dataSourceFactory,
      int minLoadableRetryCount, EventDispatcher eventDispatcher,
      MediaSource.Listener sourceListener, final Callback callback, Allocator allocator,
      long positionUs) {
    this.dataSourceFactory = dataSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.sourceListener = sourceListener;
    this.callback = callback;
    this.allocator = allocator;
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    manifestParser = new HlsPlaylistParser();
    continueLoadingHandler = new Handler();
    manifestFetcher = new Loader("Loader:ManifestFetcher");
    preparePositionUs = positionUs;
    continueLoadingRunnable = new Runnable() {
      @Override
      public void run() {
        callback.onContinueLoadingRequested(HlsMediaPeriod.this);
      }
    };

    ParsingLoadable<HlsPlaylist> loadable = new ParsingLoadable<>(
        dataSourceFactory.createDataSource(), manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
    long elapsedRealtimeMs = manifestFetcher.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

  public void release() {
    continueLoadingHandler.removeCallbacksAndMessages(null);
    manifestFetcher.release();
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    if (sampleStreamWrappers == null) {
      manifestFetcher.maybeThrowError();
    } else {
      for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
        sampleStreamWrapper.maybeThrowPrepareError();
      }
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    // Map each selection and stream onto a child period index.
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      streamChildIndices[i] = streams[i] == null ? C.INDEX_UNSET
          : streamWrapperIndices.get(streams[i]);
      selectionChildIndices[i] = C.INDEX_UNSET;
      if (selections[i] != null) {
        TrackGroup trackGroup = selections[i].getTrackGroup();
        for (int j = 0; j < sampleStreamWrappers.length; j++) {
          if (sampleStreamWrappers[j].getTrackGroups().indexOf(trackGroup) != C.INDEX_UNSET) {
            selectionChildIndices[i] = j;
            break;
          }
        }
      }
    }
    boolean selectedNewTracks = false;
    streamWrapperIndices.clear();
    // Select tracks for each child, copying the resulting streams back into the streams array.
    SampleStream[] childStreams = new SampleStream[selections.length];
    TrackSelection[] childSelections = new TrackSelection[selections.length];
    ArrayList<HlsSampleStreamWrapper> enabledSampleStreamWrapperList = new ArrayList<>(
        sampleStreamWrappers.length);
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      selectedNewTracks |= sampleStreamWrappers[i].selectTracks(childSelections,
          mayRetainStreamFlags, childStreams, streamResetFlags, !seenFirstTrackSelection);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        if (selectionChildIndices[j] == i
            || (selectionChildIndices[j] == C.INDEX_UNSET && streamChildIndices[j] == i)) {
          streams[j] = childStreams[j];
          if (childStreams[j] != null) {
            wrapperEnabled = true;
            streamWrapperIndices.put(childStreams[j], i);
          }
        }
      }
      if (wrapperEnabled) {
        enabledSampleStreamWrapperList.add(sampleStreamWrappers[i]);
      }
    }
    // Update the local state.
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[enabledSampleStreamWrapperList.size()];
    enabledSampleStreamWrapperList.toArray(enabledSampleStreamWrappers);
    sequenceableLoader = new CompositeSequenceableLoader(enabledSampleStreamWrappers);
    if (seenFirstTrackSelection && selectedNewTracks) {
      seekToUs(positionUs);
      // We'll need to reset renderers consuming from all streams due to the seek.
      for (int i = 0; i < selections.length; i++) {
        if (streams[i] != null) {
          streamResetFlags[i] = true;
        }
      }
    }
    seenFirstTrackSelection = true;
    return positionUs;
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
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      long rendererBufferedPositionUs = sampleStreamWrapper.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = isLive ? 0 : positionUs;
    timestampAdjusterProvider.reset();
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.seekTo(positionUs);
    }
    return positionUs;
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    playlist = loadable.getResult();
    List<HlsSampleStreamWrapper> sampleStreamWrapperList = buildSampleStreamWrappers();
    sampleStreamWrappers = new HlsSampleStreamWrapper[sampleStreamWrapperList.size()];
    sampleStreamWrapperList.toArray(sampleStreamWrappers);
    pendingPrepareCount = sampleStreamWrappers.length;
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.prepare();
    }
  }

  @Override
  public void onLoadCanceled(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  @Override
  public int onLoadError(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    boolean isFatal = error instanceof ParserException;
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded(), error, isFatal);
    return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
  }

  // HlsSampleStreamWrapper.Callback implementation.

  @Override
  public void onPrepared() {
    if (--pendingPrepareCount > 0) {
      return;
    }

    // The wrapper at index 0 is the one of type TRACK_TYPE_DEFAULT.
    durationUs = sampleStreamWrappers[0].getDurationUs();
    isLive = sampleStreamWrappers[0].isLive();

    int totalTrackGroupCount = 0;
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      totalTrackGroupCount += sampleStreamWrapper.getTrackGroups().length;
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      int wrapperTrackGroupCount = sampleStreamWrapper.getTrackGroups().length;
      for (int j = 0; j < wrapperTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = sampleStreamWrapper.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    callback.onPrepared(this);

    // TODO[playlists]: Calculate the window.
    Timeline timeline = new SinglePeriodTimeline(durationUs, durationUs, 0, 0, !isLive, isLive);
    sourceListener.onSourceInfoRefreshed(timeline, playlist);
  }

  @Override
  public void onContinueLoadingRequiredInMs(final HlsSampleStreamWrapper sampleStreamWrapper,
      long delayMs) {
    continueLoadingHandler.postDelayed(continueLoadingRunnable, delayMs);
  }

  @Override
  public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
    if (trackGroups == null) {
      // Still preparing.
      return;
    }
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private List<HlsSampleStreamWrapper> buildSampleStreamWrappers() {
    ArrayList<HlsSampleStreamWrapper> sampleStreamWrappers = new ArrayList<>();
    String baseUri = playlist.baseUri;

    if (playlist instanceof HlsMediaPlaylist) {
      HlsMasterPlaylist.HlsUrl[] variants = new HlsMasterPlaylist.HlsUrl[] {
          HlsMasterPlaylist.HlsUrl.createMediaPlaylistHlsUrl(playlist.baseUri)};
      sampleStreamWrappers.add(buildSampleStreamWrapper(C.TRACK_TYPE_DEFAULT, baseUri, variants,
          null, null));
      return sampleStreamWrappers;
    }

    HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;

    // Build the default stream wrapper.
    List<HlsMasterPlaylist.HlsUrl> selectedVariants = new ArrayList<>(masterPlaylist.variants);
    ArrayList<HlsMasterPlaylist.HlsUrl> definiteVideoVariants = new ArrayList<>();
    ArrayList<HlsMasterPlaylist.HlsUrl> definiteAudioOnlyVariants = new ArrayList<>();
    for (int i = 0; i < selectedVariants.size(); i++) {
      HlsMasterPlaylist.HlsUrl variant = selectedVariants.get(i);
      if (variant.format.height > 0 || variantHasExplicitCodecWithPrefix(variant, "avc")) {
        definiteVideoVariants.add(variant);
      } else if (variantHasExplicitCodecWithPrefix(variant, "mp4a")) {
        definiteAudioOnlyVariants.add(variant);
      }
    }
    if (!definiteVideoVariants.isEmpty()) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // master playlist are marked consistently, and hence that we have the full set. Filter out
      // any other variants, which are likely to be audio only.
      selectedVariants = definiteVideoVariants;
    } else if (definiteAudioOnlyVariants.size() < selectedVariants.size()) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      selectedVariants.removeAll(definiteAudioOnlyVariants);
    } else {
      // Leave the enabled variants unchanged. They're likely either all video or all audio.
    }
    if (!selectedVariants.isEmpty()) {
      HlsMasterPlaylist.HlsUrl[] variants = new HlsMasterPlaylist.HlsUrl[selectedVariants.size()];
      selectedVariants.toArray(variants);
      sampleStreamWrappers.add(buildSampleStreamWrapper(C.TRACK_TYPE_DEFAULT, baseUri, variants,
          masterPlaylist.muxedAudioFormat, masterPlaylist.muxedCaptionFormat));
    }

    // Build the audio stream wrapper if applicable.
    List<HlsMasterPlaylist.HlsUrl> audioVariants = masterPlaylist.audios;
    if (!audioVariants.isEmpty()) {
      HlsMasterPlaylist.HlsUrl[] variants = new HlsMasterPlaylist.HlsUrl[audioVariants.size()];
      audioVariants.toArray(variants);
      sampleStreamWrappers.add(buildSampleStreamWrapper(C.TRACK_TYPE_AUDIO, baseUri, variants, null,
          null));
    }

    // Build the text stream wrapper if applicable.
    List<HlsMasterPlaylist.HlsUrl> subtitleVariants = masterPlaylist.subtitles;
    if (!subtitleVariants.isEmpty()) {
      HlsMasterPlaylist.HlsUrl[] variants = new HlsMasterPlaylist.HlsUrl[subtitleVariants.size()];
      subtitleVariants.toArray(variants);
      sampleStreamWrappers.add(buildSampleStreamWrapper(C.TRACK_TYPE_TEXT, baseUri, variants, null,
          null));
    }

    return sampleStreamWrappers;
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(int trackType, String baseUri,
      HlsMasterPlaylist.HlsUrl[] variants, Format muxedAudioFormat, Format muxedCaptionFormat) {
    DataSource dataSource = dataSourceFactory.createDataSource();
    HlsChunkSource defaultChunkSource = new HlsChunkSource(baseUri, variants, dataSource,
        timestampAdjusterProvider);
    return new HlsSampleStreamWrapper(trackType, this, defaultChunkSource, allocator,
        preparePositionUs, muxedAudioFormat, muxedCaptionFormat, minLoadableRetryCount,
        eventDispatcher);
  }

  private static boolean variantHasExplicitCodecWithPrefix(HlsMasterPlaylist.HlsUrl variant,
      String prefix) {
    String codecs = variant.format.codecs;
    if (TextUtils.isEmpty(codecs)) {
      return false;
    }
    String[] codecArray = codecs.split("(\\s*,\\s*)|(\\s*$)");
    for (String codec : codecArray) {
      if (codec.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

}
