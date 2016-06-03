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
import com.google.android.exoplayer.chunk.ChunkTrackStreamEventListener;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer.hls.playlist.Variant;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.UriLoadable;
import com.google.android.exoplayer.util.MimeTypes;

import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A {@link SampleSource} for HLS streams.
 */
public final class HlsSampleSource implements SampleSource,
    Loader.Callback<UriLoadable<HlsPlaylist>> {

  /**
   * The minimum number of times to retry loading data prior to failing.
   */
  private static final int MIN_LOADABLE_RETRY_COUNT = 3;

  private final Uri manifestUri;
  private final DataSourceFactory dataSourceFactory;
  private final BandwidthMeter bandwidthMeter;
  private final Handler eventHandler;
  private final ChunkTrackStreamEventListener eventListener;
  private final LoadControl loadControl;
  private final IdentityHashMap<TrackStream, HlsTrackStreamWrapper> trackStreamSources;
  private final PtsTimestampAdjusterProvider timestampAdjusterProvider;
  private final Loader manifestFetcher;
  private final DataSource manifestDataSource;
  private final HlsPlaylistParser manifestParser;

  private boolean seenFirstTrackSelection;
  private long durationUs;
  private boolean isLive;
  private TrackGroupArray trackGroups;
  private int[] selectedTrackCounts;
  private HlsTrackStreamWrapper[] trackStreamWrappers;
  private HlsTrackStreamWrapper[] enabledTrackStreamWrappers;
  private boolean pendingReset;
  private long lastSeekPositionUs;

  public HlsSampleSource(Uri manifestUri, DataSourceFactory dataSourceFactory,
      BandwidthMeter bandwidthMeter, Handler eventHandler,
      ChunkTrackStreamEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.bandwidthMeter = bandwidthMeter;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;

    loadControl = new DefaultLoadControl(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
    timestampAdjusterProvider = new PtsTimestampAdjusterProvider();
    trackStreamSources = new IdentityHashMap<>();

    manifestDataSource = dataSourceFactory.createDataSource();
    manifestParser = new HlsPlaylistParser();
    manifestFetcher = new Loader("Loader:ManifestFetcher");
  }

  @Override
  public boolean prepare(long positionUs) throws IOException {
    if (trackGroups != null) {
      return true;
    }

    if (trackStreamWrappers == null) {
      manifestFetcher.maybeThrowError();
      if (!manifestFetcher.isLoading()) {
        manifestFetcher.startLoading(
            new UriLoadable<>(manifestUri, manifestDataSource, manifestParser), this,
            MIN_LOADABLE_RETRY_COUNT);
      }
      return false;
    }

    boolean trackStreamWrappersPrepared = true;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      trackStreamWrappersPrepared &= trackStreamWrapper.prepare(positionUs);
    }
    if (!trackStreamWrappersPrepared) {
      return false;
    }

    // The wrapper at index 0 is the one of type TRACK_TYPE_DEFAULT.
    durationUs = trackStreamWrappers[0].getDurationUs();
    isLive = trackStreamWrappers[0].isLive();

    int totalTrackGroupCount = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      totalTrackGroupCount += trackStreamWrapper.getTrackGroups().length;
    }
    TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
    int trackGroupIndex = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      int wrapperTrackGroupCount = trackStreamWrapper.getTrackGroups().length;
      for (int j = 0; j < wrapperTrackGroupCount; j++) {
        trackGroupArray[trackGroupIndex++] = trackStreamWrapper.getTrackGroups().get(j);
      }
    }
    trackGroups = new TrackGroupArray(trackGroupArray);
    return true;
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
    TrackStream[] newStreams = new TrackStream[newSelections.size()];
    // Select tracks for each wrapper.
    int enabledTrackStreamWrapperCount = 0;
    for (int i = 0; i < trackStreamWrappers.length; i++) {
      selectedTrackCounts[i] += selectTracks(trackStreamWrappers[i], oldStreams, newSelections,
          newStreams);
      if (selectedTrackCounts[i] > 0) {
        enabledTrackStreamWrapperCount++;
      }
    }
    // Update the enabled wrappers.
    enabledTrackStreamWrappers = new HlsTrackStreamWrapper[enabledTrackStreamWrapperCount];
    enabledTrackStreamWrapperCount = 0;
    for (int i = 0; i < trackStreamWrappers.length; i++) {
      if (selectedTrackCounts[i] > 0) {
        enabledTrackStreamWrappers[enabledTrackStreamWrapperCount++] = trackStreamWrappers[i];
      }
    }
    if (enabledTrackStreamWrapperCount > 0 && seenFirstTrackSelection && !newSelections.isEmpty()) {
      seekToUs(positionUs);
    }
    seenFirstTrackSelection = true;
    return newStreams;
  }

  @Override
  public void continueBuffering(long positionUs) {
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      trackStreamWrapper.continueBuffering(positionUs);
    }
  }

  @Override
  public long readReset() {
    if (pendingReset) {
      pendingReset = false;
      for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
        trackStreamWrapper.setReadingEnabled(true);
      }
      return lastSeekPositionUs;
    }
    return C.UNSET_TIME_US;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      long rendererBufferedPositionUs = trackStreamWrapper.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.END_OF_SOURCE_US) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.END_OF_SOURCE_US : bufferedPositionUs;
  }

  @Override
  public void seekToUs(long positionUs) {
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = isLive ? 0 : positionUs;
    lastSeekPositionUs = positionUs;
    pendingReset = true;
    timestampAdjusterProvider.reset();
    for (HlsTrackStreamWrapper trackStreamWrapper : enabledTrackStreamWrappers) {
      trackStreamWrapper.setReadingEnabled(false);
      trackStreamWrapper.restartFrom(positionUs);
    }
  }

  @Override
  public void release() {
    manifestFetcher.release();
    if (trackStreamWrappers != null) {
      for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
        trackStreamWrapper.release();
      }
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(UriLoadable<HlsPlaylist> loadable, long elapsedMs) {
    HlsPlaylist playlist = loadable.getResult();
    List<HlsTrackStreamWrapper> trackStreamWrapperList = buildTrackStreamWrappers(playlist);
    trackStreamWrappers = new HlsTrackStreamWrapper[trackStreamWrapperList.size()];
    trackStreamWrapperList.toArray(trackStreamWrappers);
    selectedTrackCounts = new int[trackStreamWrappers.length];
  }

  @Override
  public void onLoadCanceled(UriLoadable<HlsPlaylist> loadable, long elapsedMs, boolean released) {
    // Do nothing.
  }

  @Override
  public int onLoadError(UriLoadable<HlsPlaylist> loadable, long elapsedMs, IOException exception) {
    return exception instanceof ParserException ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
  }

  // Internal methods.

  private List<HlsTrackStreamWrapper> buildTrackStreamWrappers(HlsPlaylist playlist) {
    ArrayList<HlsTrackStreamWrapper> trackStreamWrappers = new ArrayList<>();
    String baseUri = playlist.baseUri;

    if (playlist instanceof HlsMediaPlaylist) {
      Format format = Format.createContainerFormat("0", MimeTypes.APPLICATION_M3U8, null,
          Format.NO_VALUE);
      Variant[] variants = new Variant[] {new Variant(playlist.baseUri, format, null)};
      trackStreamWrappers.add(buildTrackStreamWrapper(C.TRACK_TYPE_DEFAULT, baseUri, variants,
          new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter), C.DEFAULT_MUXED_BUFFER_SIZE,
          null, null));
      return trackStreamWrappers;
    }

    HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;

    // Build the default stream wrapper.
    List<Variant> selectedVariants = new ArrayList<>(masterPlaylist.variants);
    ArrayList<Variant> definiteVideoVariants = new ArrayList<>();
    ArrayList<Variant> definiteAudioOnlyVariants = new ArrayList<>();
    for (int i = 0; i < selectedVariants.size(); i++) {
      Variant variant = selectedVariants.get(i);
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
    Variant[] variants = new Variant[selectedVariants.size()];
    selectedVariants.toArray(variants);
    trackStreamWrappers.add(buildTrackStreamWrapper(C.TRACK_TYPE_DEFAULT, baseUri, variants,
        new FormatEvaluator.AdaptiveEvaluator(bandwidthMeter), C.DEFAULT_MUXED_BUFFER_SIZE,
        masterPlaylist.muxedAudioFormat, masterPlaylist.muxedCaptionFormat));

    // Build the audio stream wrapper if applicable.
    List<Variant> audioVariants = masterPlaylist.audios;
    if (!audioVariants.isEmpty()) {
      variants = new Variant[audioVariants.size()];
      audioVariants.toArray(variants);
      trackStreamWrappers.add(buildTrackStreamWrapper(C.TRACK_TYPE_AUDIO, baseUri, variants, null,
          C.DEFAULT_AUDIO_BUFFER_SIZE, null, null));
    }

    // Build the text stream wrapper if applicable.
    List<Variant> subtitleVariants = masterPlaylist.subtitles;
    if (!subtitleVariants.isEmpty()) {
      variants = new Variant[subtitleVariants.size()];
      subtitleVariants.toArray(variants);
      trackStreamWrappers.add(buildTrackStreamWrapper(C.TRACK_TYPE_TEXT, baseUri, variants, null,
          C.DEFAULT_TEXT_BUFFER_SIZE, null, null));
    }

    return trackStreamWrappers;
  }

  private HlsTrackStreamWrapper buildTrackStreamWrapper(int trackType, String baseUri,
      Variant[] variants, FormatEvaluator formatEvaluator, int bufferSize, Format muxedAudioFormat,
      Format muxedCaptionFormat) {
    DataSource dataSource = dataSourceFactory.createDataSource(bandwidthMeter);
    HlsChunkSource defaultChunkSource = new HlsChunkSource(baseUri, variants, dataSource,
        timestampAdjusterProvider, formatEvaluator);
    return new HlsTrackStreamWrapper(defaultChunkSource, loadControl, bufferSize, muxedAudioFormat,
        muxedCaptionFormat, eventHandler, eventListener, trackType, MIN_LOADABLE_RETRY_COUNT);
  }

  private int selectTracks(HlsTrackStreamWrapper trackStreamWrapper,
      List<TrackStream> allOldStreams, List<TrackSelection> allNewSelections,
      TrackStream[] allNewStreams) {
    // Get the subset of the old streams for the source.
    ArrayList<TrackStream> oldStreams = new ArrayList<>();
    for (int i = 0; i < allOldStreams.size(); i++) {
      TrackStream stream = allOldStreams.get(i);
      if (trackStreamSources.get(stream) == trackStreamWrapper) {
        trackStreamSources.remove(stream);
        oldStreams.add(stream);
      }
    }
    // Get the subset of the new selections for the wrapper.
    ArrayList<TrackSelection> newSelections = new ArrayList<>();
    int[] newSelectionOriginalIndices = new int[allNewSelections.size()];
    for (int i = 0; i < allNewSelections.size(); i++) {
      TrackSelection selection = allNewSelections.get(i);
      Pair<HlsTrackStreamWrapper, Integer> sourceAndGroup = getSourceAndGroup(selection.group);
      if (sourceAndGroup.first == trackStreamWrapper) {
        newSelectionOriginalIndices[newSelections.size()] = i;
        newSelections.add(new TrackSelection(sourceAndGroup.second, selection.getTracks()));
      }
    }
    // Do nothing if nothing has changed, except during the first selection.
    if (seenFirstTrackSelection && oldStreams.isEmpty() && newSelections.isEmpty()) {
      return 0;
    }
    // Perform the selection.
    TrackStream[] newStreams = trackStreamWrapper.selectTracks(oldStreams, newSelections,
        !seenFirstTrackSelection);
    for (int j = 0; j < newStreams.length; j++) {
      allNewStreams[newSelectionOriginalIndices[j]] = newStreams[j];
      trackStreamSources.put(newStreams[j], trackStreamWrapper);
    }
    return newSelections.size() - oldStreams.size();
  }

  private Pair<HlsTrackStreamWrapper, Integer> getSourceAndGroup(int group) {
    int totalTrackGroupCount = 0;
    for (HlsTrackStreamWrapper trackStreamWrapper : trackStreamWrappers) {
      int sourceTrackGroupCount = trackStreamWrapper.getTrackGroups().length;
      if (group < totalTrackGroupCount + sourceTrackGroupCount) {
        return Pair.create(trackStreamWrapper, group - totalTrackGroupCount);
      }
      totalTrackGroupCount += sourceTrackGroupCount;
    }
    throw new IndexOutOfBoundsException();
  }

  private static boolean variantHasExplicitCodecWithPrefix(Variant variant, String prefix) {
    String codecs = variant.codecs;
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
