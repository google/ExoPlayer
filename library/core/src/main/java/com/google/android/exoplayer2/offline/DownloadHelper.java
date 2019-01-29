/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Pair;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A helper for initializing and removing downloads.
 *
 * <p>The helper extracts track information from the media, selects tracks for downloading, and
 * creates {@link DownloadAction download actions} based on the selected tracks.
 *
 * <p>A typical usage of DownloadHelper follows these steps:
 *
 * <ol>
 *   <li>Build the helper using one of the {@code forXXX} methods.
 *   <li>Prepare the helper using {@link #prepare(Callback)} and wait for the callback.
 *   <li>Optional: Inspect the selected tracks using {@link #getMappedTrackInfo(int)} and {@link
 *       #getTrackSelections(int, int)}, and make adjustments using {@link
 *       #clearTrackSelections(int)}, {@link #replaceTrackSelections(int, Parameters)} and {@link
 *       #addTrackSelection(int, Parameters)}.
 *   <li>Create a download action for the selected track using {@link #getDownloadAction(byte[])}.
 *   <li>Release the helper using {@link #release()}.
 * </ol>
 */
public final class DownloadHelper {

  /**
   * The default parameters used for track selection for downloading. This default selects the
   * highest bitrate audio and video tracks which are supported by the renderers.
   */
  public static final DefaultTrackSelector.Parameters DEFAULT_TRACK_SELECTOR_PARAMETERS =
      new DefaultTrackSelector.ParametersBuilder().setForceHighestSupportedBitrate(true).build();

  /** A callback to be notified when the {@link DownloadHelper} is prepared. */
  public interface Callback {

    /**
     * Called when preparation completes.
     *
     * @param helper The reporting {@link DownloadHelper}.
     */
    void onPrepared(DownloadHelper helper);

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting {@link DownloadHelper}.
     * @param e The error.
     */
    void onPrepareError(DownloadHelper helper, IOException e);
  }

  @Nullable private static final Constructor<?> DASH_FACTORY_CONSTRUCTOR;
  @Nullable private static final Constructor<?> HLS_FACTORY_CONSTRUCTOR;
  @Nullable private static final Constructor<?> SS_FACTORY_CONSTRUCTOR;
  @Nullable private static final Method DASH_FACTORY_CREATE_METHOD;
  @Nullable private static final Method HLS_FACTORY_CREATE_METHOD;
  @Nullable private static final Method SS_FACTORY_CREATE_METHOD;

  static {
    Pair<@NullableType Constructor<?>, @NullableType Method> dashFactoryMethods =
        getMediaSourceFactoryMethods(
            "com.google.android.exoplayer2.source.dash.DashMediaSource$Factory");
    DASH_FACTORY_CONSTRUCTOR = dashFactoryMethods.first;
    DASH_FACTORY_CREATE_METHOD = dashFactoryMethods.second;
    Pair<@NullableType Constructor<?>, @NullableType Method> hlsFactoryMethods =
        getMediaSourceFactoryMethods(
            "com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory");
    HLS_FACTORY_CONSTRUCTOR = hlsFactoryMethods.first;
    HLS_FACTORY_CREATE_METHOD = hlsFactoryMethods.second;
    Pair<@NullableType Constructor<?>, @NullableType Method> ssFactoryMethods =
        getMediaSourceFactoryMethods(
            "com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource$Factory");
    SS_FACTORY_CONSTRUCTOR = ssFactoryMethods.first;
    SS_FACTORY_CREATE_METHOD = ssFactoryMethods.second;
  }

  /**
   * Creates a {@link DownloadHelper} for progressive streams.
   *
   * @param uri A stream {@link Uri}.
   * @return A {@link DownloadHelper} for progressive streams.
   */
  public static DownloadHelper forProgressive(Uri uri) {
    return forProgressive(uri, /* cacheKey= */ null);
  }

  /**
   * Creates a {@link DownloadHelper} for progressive streams.
   *
   * @param uri A stream {@link Uri}.
   * @param cacheKey An optional cache key.
   * @return A {@link DownloadHelper} for progressive streams.
   */
  public static DownloadHelper forProgressive(Uri uri, @Nullable String cacheKey) {
    return new DownloadHelper(
        DownloadAction.TYPE_PROGRESSIVE,
        uri,
        cacheKey,
        /* mediaSource= */ null,
        DEFAULT_TRACK_SELECTOR_PARAMETERS,
        /* rendererCapabilities= */ new RendererCapabilities[0]);
  }

  /**
   * Creates a {@link DownloadHelper} for DASH streams.
   *
   * @param uri A manifest {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @return A {@link DownloadHelper} for DASH streams.
   * @throws IllegalStateException If the DASH module is missing.
   */
  public static DownloadHelper forDash(
      Uri uri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
    return forDash(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        DEFAULT_TRACK_SELECTOR_PARAMETERS);
  }

  /**
   * Creates a {@link DownloadHelper} for DASH streams.
   *
   * @param uri A manifest {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @return A {@link DownloadHelper} for DASH streams.
   * @throws IllegalStateException If the DASH module is missing.
   */
  public static DownloadHelper forDash(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      DefaultTrackSelector.Parameters trackSelectorParameters) {
    return new DownloadHelper(
        DownloadAction.TYPE_DASH,
        uri,
        /* cacheKey= */ null,
        createMediaSource(
            uri, dataSourceFactory, DASH_FACTORY_CONSTRUCTOR, DASH_FACTORY_CREATE_METHOD),
        trackSelectorParameters,
        Util.getRendererCapabilities(renderersFactory, drmSessionManager));
  }

  /**
   * Creates a {@link DownloadHelper} for HLS streams.
   *
   * @param uri A playlist {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the playlist.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @return A {@link DownloadHelper} for HLS streams.
   * @throws IllegalStateException If the HLS module is missing.
   */
  public static DownloadHelper forHls(
      Uri uri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
    return forHls(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        DEFAULT_TRACK_SELECTOR_PARAMETERS);
  }

  /**
   * Creates a {@link DownloadHelper} for HLS streams.
   *
   * @param uri A playlist {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the playlist.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @return A {@link DownloadHelper} for HLS streams.
   * @throws IllegalStateException If the HLS module is missing.
   */
  public static DownloadHelper forHls(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      DefaultTrackSelector.Parameters trackSelectorParameters) {
    return new DownloadHelper(
        DownloadAction.TYPE_HLS,
        uri,
        /* cacheKey= */ null,
        createMediaSource(
            uri, dataSourceFactory, HLS_FACTORY_CONSTRUCTOR, HLS_FACTORY_CREATE_METHOD),
        trackSelectorParameters,
        Util.getRendererCapabilities(renderersFactory, drmSessionManager));
  }

  /**
   * Creates a {@link DownloadHelper} for SmoothStreaming streams.
   *
   * @param uri A manifest {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @return A {@link DownloadHelper} for SmoothStreaming streams.
   * @throws IllegalStateException If the SmoothStreaming module is missing.
   */
  public static DownloadHelper forSmoothStreaming(
      Uri uri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
    return forSmoothStreaming(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        DEFAULT_TRACK_SELECTOR_PARAMETERS);
  }

  /**
   * Creates a {@link DownloadHelper} for SmoothStreaming streams.
   *
   * @param uri A manifest {@link Uri}.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @return A {@link DownloadHelper} for SmoothStreaming streams.
   * @throws IllegalStateException If the SmoothStreaming module is missing.
   */
  public static DownloadHelper forSmoothStreaming(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      DefaultTrackSelector.Parameters trackSelectorParameters) {
    return new DownloadHelper(
        DownloadAction.TYPE_SS,
        uri,
        /* cacheKey= */ null,
        createMediaSource(uri, dataSourceFactory, SS_FACTORY_CONSTRUCTOR, SS_FACTORY_CREATE_METHOD),
        trackSelectorParameters,
        Util.getRendererCapabilities(renderersFactory, drmSessionManager));
  }

  private final String downloadType;
  private final Uri uri;
  @Nullable private final String cacheKey;
  @Nullable private final MediaSource mediaSource;
  private final DefaultTrackSelector trackSelector;
  private final RendererCapabilities[] rendererCapabilities;
  private final SparseIntArray scratchSet;

  private boolean isPreparedWithMedia;
  private @MonotonicNonNull Callback callback;
  private @MonotonicNonNull Handler callbackHandler;
  private @MonotonicNonNull MediaPreparer mediaPreparer;
  private TrackGroupArray @MonotonicNonNull [] trackGroupArrays;
  private MappedTrackInfo @MonotonicNonNull [] mappedTrackInfos;
  private List<TrackSelection> @MonotonicNonNull [][] trackSelectionsByPeriodAndRenderer;
  private List<TrackSelection> @MonotonicNonNull [][] immutableTrackSelectionsByPeriodAndRenderer;

  /**
   * Creates download helper.
   *
   * @param downloadType A download type. This value will be used as {@link DownloadAction#type}.
   * @param uri A {@link Uri}.
   * @param cacheKey An optional cache key.
   * @param mediaSource A {@link MediaSource} for which tracks are selected, or null if no track
   *     selection needs to be made.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers for which tracks
   *     are selected.
   */
  public DownloadHelper(
      String downloadType,
      Uri uri,
      @Nullable String cacheKey,
      @Nullable MediaSource mediaSource,
      DefaultTrackSelector.Parameters trackSelectorParameters,
      RendererCapabilities[] rendererCapabilities) {
    this.downloadType = downloadType;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.mediaSource = mediaSource;
    this.trackSelector = new DefaultTrackSelector(new DownloadTrackSelection.Factory());
    this.rendererCapabilities = rendererCapabilities;
    this.scratchSet = new SparseIntArray();
    trackSelector.setParameters(trackSelectorParameters);
    trackSelector.init(/* listener= */ () -> {}, new DummyBandwidthMeter());
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails. The callback
   *     will be invoked on the calling thread unless that thread does not have an associated {@link
   *     Looper}, in which case it will be called on the application's main thread.
   * @throws IllegalStateException If the download helper has already been prepared.
   */
  public void prepare(Callback callback) {
    Assertions.checkState(this.callback == null);
    this.callback = callback;
    callbackHandler =
        new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
    if (mediaSource != null) {
      mediaPreparer = new MediaPreparer(mediaSource, /* downloadHelper= */ this);
    } else {
      callbackHandler.post(() -> callback.onPrepared(this));
    }
  }

  /** Releases the helper and all resources it is holding. */
  public void release() {
    if (mediaPreparer != null) {
      mediaPreparer.release();
    }
  }

  /**
   * Returns the manifest, or null if no manifest is loaded. Must not be called until after
   * preparation completes.
   */
  @Nullable
  public Object getManifest() {
    if (mediaSource == null) {
      return null;
    }
    assertPreparedWithMedia();
    return mediaPreparer.manifest;
  }

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  public int getPeriodCount() {
    if (mediaSource == null) {
      return 0;
    }
    assertPreparedWithMedia();
    return trackGroupArrays.length;
  }

  /**
   * Returns the track groups for the given period. Must not be called until after preparation
   * completes.
   *
   * <p>Use {@link #getMappedTrackInfo(int)} to get the track groups mapped to renderers.
   *
   * @param periodIndex The period index.
   * @return The track groups for the period. May be {@link TrackGroupArray#EMPTY} for single stream
   *     content.
   */
  public TrackGroupArray getTrackGroups(int periodIndex) {
    assertPreparedWithMedia();
    return trackGroupArrays[periodIndex];
  }

  /**
   * Returns the mapped track info for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The {@link MappedTrackInfo} for the period.
   */
  public MappedTrackInfo getMappedTrackInfo(int periodIndex) {
    assertPreparedWithMedia();
    return mappedTrackInfos[periodIndex];
  }

  /**
   * Returns all {@link TrackSelection track selections} for a period and renderer. Must not be
   * called until after preparation completes.
   *
   * @param periodIndex The period index.
   * @param rendererIndex The renderer index.
   * @return A list of selected {@link TrackSelection track selections}.
   */
  public List<TrackSelection> getTrackSelections(int periodIndex, int rendererIndex) {
    assertPreparedWithMedia();
    return immutableTrackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex];
  }

  /**
   * Clears the selection of tracks for a period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which track selections are cleared.
   */
  public void clearTrackSelections(int periodIndex) {
    assertPreparedWithMedia();
    for (int i = 0; i < rendererCapabilities.length; i++) {
      trackSelectionsByPeriodAndRenderer[periodIndex][i].clear();
    }
  }

  /**
   * Replaces a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which the track selection is replaced.
   * @param trackSelectorParameters The {@link DefaultTrackSelector.Parameters} to obtain the new
   *     selection of tracks.
   */
  public void replaceTrackSelections(
      int periodIndex, DefaultTrackSelector.Parameters trackSelectorParameters) {
    clearTrackSelections(periodIndex);
    addTrackSelection(periodIndex, trackSelectorParameters);
  }

  /**
   * Adds a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index this track selection is added for.
   * @param trackSelectorParameters The {@link DefaultTrackSelector.Parameters} to obtain the new
   *     selection of tracks.
   */
  public void addTrackSelection(
      int periodIndex, DefaultTrackSelector.Parameters trackSelectorParameters) {
    assertPreparedWithMedia();
    trackSelector.setParameters(trackSelectorParameters);
    runTrackSelection(periodIndex);
  }

  /**
   * Convenience method to add selections of tracks for all specified audio languages. If an audio
   * track in one of the specified languages is not available, the default fallback audio track is
   * used instead. Must not be called until after preparation completes.
   *
   * @param languages A list of audio languages for which tracks should be added to the download
   *     selection, as ISO 639-1 two-letter or ISO 639-2 three-letter codes.
   */
  public void addAudioLanguagesToSelection(String... languages) {
    assertPreparedWithMedia();
    for (int periodIndex = 0; periodIndex < mappedTrackInfos.length; periodIndex++) {
      DefaultTrackSelector.ParametersBuilder parametersBuilder =
          DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon();
      MappedTrackInfo mappedTrackInfo = mappedTrackInfos[periodIndex];
      int rendererCount = mappedTrackInfo.getRendererCount();
      for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
        if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_AUDIO) {
          parametersBuilder.setRendererDisabled(rendererIndex, /* disabled= */ true);
        }
      }
      for (String language : languages) {
        parametersBuilder.setPreferredAudioLanguage(language);
        addTrackSelection(periodIndex, parametersBuilder.build());
      }
    }
  }

  /**
   * Convenience method to add selections of tracks for all specified text languages. Must not be
   * called until after preparation completes.
   *
   * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should be
   *     selected for downloading if no track with one of the specified {@code languages} is
   *     available.
   * @param languages A list of text languages for which tracks should be added to the download
   *     selection, as ISO 639-1 two-letter or ISO 639-2 three-letter codes.
   */
  public void addTextLanguagesToSelection(
      boolean selectUndeterminedTextLanguage, String... languages) {
    assertPreparedWithMedia();
    for (int periodIndex = 0; periodIndex < mappedTrackInfos.length; periodIndex++) {
      DefaultTrackSelector.ParametersBuilder parametersBuilder =
          DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon();
      MappedTrackInfo mappedTrackInfo = mappedTrackInfos[periodIndex];
      int rendererCount = mappedTrackInfo.getRendererCount();
      for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
        if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_TEXT) {
          parametersBuilder.setRendererDisabled(rendererIndex, /* disabled= */ true);
        }
      }
      parametersBuilder.setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage);
      for (String language : languages) {
        parametersBuilder.setPreferredTextLanguage(language);
        addTrackSelection(periodIndex, parametersBuilder.build());
      }
    }
  }

  /**
   * Builds a {@link DownloadAction} for downloading the selected tracks. Must not be called until
   * after preparation completes.
   *
   * @param data Application provided data to store in {@link DownloadAction#data}.
   * @return The built {@link DownloadAction}.
   */
  public DownloadAction getDownloadAction(@Nullable byte[] data) {
    if (mediaSource == null) {
      return DownloadAction.createDownloadAction(
          downloadType, uri, /* keys= */ Collections.emptyList(), cacheKey, data);
    }
    assertPreparedWithMedia();
    List<StreamKey> streamKeys = new ArrayList<>();
    List<TrackSelection> allSelections = new ArrayList<>();
    int periodCount = trackSelectionsByPeriodAndRenderer.length;
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      allSelections.clear();
      int rendererCount = trackSelectionsByPeriodAndRenderer[periodIndex].length;
      for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
        allSelections.addAll(trackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex]);
      }
      streamKeys.addAll(mediaPreparer.mediaPeriods[periodIndex].getStreamKeys(allSelections));
    }
    return DownloadAction.createDownloadAction(downloadType, uri, streamKeys, cacheKey, data);
  }

  /**
   * Builds a {@link DownloadAction} for removing the media. May be called in any state.
   *
   * @return The built {@link DownloadAction}.
   */
  public DownloadAction getRemoveAction() {
    return DownloadAction.createRemoveAction(downloadType, uri, cacheKey);
  }

  // Initialization of array of Lists.
  @SuppressWarnings("unchecked")
  private void onMediaPrepared() {
    Assertions.checkNotNull(mediaPreparer);
    Assertions.checkNotNull(mediaPreparer.mediaPeriods);
    Assertions.checkNotNull(mediaPreparer.timeline);
    int periodCount = mediaPreparer.mediaPeriods.length;
    int rendererCount = rendererCapabilities.length;
    trackSelectionsByPeriodAndRenderer =
        (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
    immutableTrackSelectionsByPeriodAndRenderer =
        (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
    for (int i = 0; i < periodCount; i++) {
      for (int j = 0; j < rendererCount; j++) {
        trackSelectionsByPeriodAndRenderer[i][j] = new ArrayList<>();
        immutableTrackSelectionsByPeriodAndRenderer[i][j] =
            Collections.unmodifiableList(trackSelectionsByPeriodAndRenderer[i][j]);
      }
    }
    trackGroupArrays = new TrackGroupArray[periodCount];
    mappedTrackInfos = new MappedTrackInfo[periodCount];
    for (int i = 0; i < periodCount; i++) {
      trackGroupArrays[i] = mediaPreparer.mediaPeriods[i].getTrackGroups();
      TrackSelectorResult trackSelectorResult = runTrackSelection(/* periodIndex= */ i);
      trackSelector.onSelectionActivated(trackSelectorResult.info);
      mappedTrackInfos[i] = Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
    }
    setPreparedWithMedia();
    Assertions.checkNotNull(callbackHandler)
        .post(() -> Assertions.checkNotNull(callback).onPrepared(this));
  }

  private void onMediaPreparationFailed(IOException error) {
    Assertions.checkNotNull(callbackHandler)
        .post(() -> Assertions.checkNotNull(callback).onPrepareError(this, error));
  }

  @RequiresNonNull({
    "trackGroupArrays",
    "mappedTrackInfos",
    "trackSelectionsByPeriodAndRenderer",
    "immutableTrackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline",
    "mediaPreparer.mediaPeriods"
  })
  private void setPreparedWithMedia() {
    isPreparedWithMedia = true;
  }

  @EnsuresNonNull({
    "trackGroupArrays",
    "mappedTrackInfos",
    "trackSelectionsByPeriodAndRenderer",
    "immutableTrackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline",
    "mediaPreparer.mediaPeriods"
  })
  @SuppressWarnings("nullness:contracts.postcondition.not.satisfied")
  private void assertPreparedWithMedia() {
    Assertions.checkState(isPreparedWithMedia);
  }

  /**
   * Runs the track selection for a given period index with the current parameters. The selected
   * tracks will be added to {@link #trackSelectionsByPeriodAndRenderer}.
   */
  // Intentional reference comparison of track group instances.
  @SuppressWarnings("ReferenceEquality")
  @RequiresNonNull({
    "trackGroupArrays",
    "trackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline"
  })
  private TrackSelectorResult runTrackSelection(int periodIndex) {
    try {
      TrackSelectorResult trackSelectorResult =
          trackSelector.selectTracks(
              rendererCapabilities,
              trackGroupArrays[periodIndex],
              new MediaPeriodId(mediaPreparer.timeline.getUidOfPeriod(periodIndex)),
              mediaPreparer.timeline);
      for (int i = 0; i < trackSelectorResult.length; i++) {
        TrackSelection newSelection = trackSelectorResult.selections.get(i);
        if (newSelection == null) {
          continue;
        }
        List<TrackSelection> existingSelectionList =
            trackSelectionsByPeriodAndRenderer[periodIndex][i];
        boolean mergedWithExistingSelection = false;
        for (int j = 0; j < existingSelectionList.size(); j++) {
          TrackSelection existingSelection = existingSelectionList.get(j);
          if (existingSelection.getTrackGroup() == newSelection.getTrackGroup()) {
            // Merge with existing selection.
            scratchSet.clear();
            for (int k = 0; k < existingSelection.length(); k++) {
              scratchSet.put(existingSelection.getIndexInTrackGroup(k), 0);
            }
            for (int k = 0; k < newSelection.length(); k++) {
              scratchSet.put(newSelection.getIndexInTrackGroup(k), 0);
            }
            int[] mergedTracks = new int[scratchSet.size()];
            for (int k = 0; k < scratchSet.size(); k++) {
              mergedTracks[k] = scratchSet.keyAt(k);
            }
            existingSelectionList.set(
                j, new DownloadTrackSelection(existingSelection.getTrackGroup(), mergedTracks));
            mergedWithExistingSelection = true;
            break;
          }
        }
        if (!mergedWithExistingSelection) {
          existingSelectionList.add(newSelection);
        }
      }
      return trackSelectorResult;
    } catch (ExoPlaybackException e) {
      // DefaultTrackSelector does not throw exceptions during track selection.
      throw new UnsupportedOperationException(e);
    }
  }

  private static Pair<@NullableType Constructor<?>, @NullableType Method>
      getMediaSourceFactoryMethods(String className) {
    Constructor<?> constructor = null;
    Method createMethod = null;
    try {
      // LINT.IfChange
      Class<?> factoryClazz = Class.forName(className);
      constructor = factoryClazz.getConstructor(DataSource.Factory.class);
      createMethod = factoryClazz.getMethod("createMediaSource", Uri.class);
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (Exception e) {
      // Expected if the app was built without the respective module.
    }
    return Pair.create(constructor, createMethod);
  }

  private static MediaSource createMediaSource(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      @Nullable Constructor<?> factoryConstructor,
      @Nullable Method createMediaSourceMethod) {
    if (factoryConstructor == null || createMediaSourceMethod == null) {
      throw new IllegalStateException("Module missing to create media source.");
    }
    try {
      Object factory = factoryConstructor.newInstance(dataSourceFactory);
      return (MediaSource) Assertions.checkNotNull(createMediaSourceMethod.invoke(factory, uri));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to instantiate media source.", e);
    }
  }

  private static final class MediaPreparer
      implements MediaSource.SourceInfoRefreshListener, MediaPeriod.Callback, Handler.Callback {

    private static final int MESSAGE_PREPARE_SOURCE = 0;
    private static final int MESSAGE_CHECK_FOR_FAILURE = 1;

    private final MediaSource mediaSource;
    private final DownloadHelper downloadHelper;
    private final Allocator allocator;
    private final HandlerThread mediaSourceThread;
    private final Handler mediaSourceHandler;

    @Nullable public Object manifest;
    public @MonotonicNonNull Timeline timeline;
    public MediaPeriod @MonotonicNonNull [] mediaPeriods;

    private int pendingPreparations;

    public MediaPreparer(MediaSource mediaSource, DownloadHelper downloadHelper) {
      this.mediaSource = mediaSource;
      this.downloadHelper = downloadHelper;
      allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
      mediaSourceThread = new HandlerThread("DownloadHelper");
      mediaSourceThread.start();
      mediaSourceHandler = Util.createHandler(mediaSourceThread.getLooper(), /* callback= */ this);
      mediaSourceHandler.sendEmptyMessage(MESSAGE_PREPARE_SOURCE);
    }

    public void release() {
      if (mediaPeriods != null) {
        for (MediaPeriod mediaPeriod : mediaPeriods) {
          mediaSource.releasePeriod(mediaPeriod);
        }
      }
      mediaSource.releaseSource(this);
      mediaSourceThread.quit();
    }

    // Handler.Callback

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_PREPARE_SOURCE:
          mediaSource.prepareSource(/* listener= */ this, /* mediaTransferListener= */ null);
          mediaSourceHandler.sendEmptyMessage(MESSAGE_CHECK_FOR_FAILURE);
          return true;
        case MESSAGE_CHECK_FOR_FAILURE:
          try {
            if (mediaPeriods == null) {
              mediaSource.maybeThrowSourceInfoRefreshError();
            } else {
              for (MediaPeriod mediaPeriod : mediaPeriods) {
                mediaPeriod.maybeThrowPrepareError();
              }
            }
            mediaSourceHandler.sendEmptyMessageDelayed(
                MESSAGE_CHECK_FOR_FAILURE, /* delayMillis= */ 100);
          } catch (IOException e) {
            downloadHelper.onMediaPreparationFailed(e);
          }
          return true;
        default:
          return false;
      }
    }

    // MediaSource.SourceInfoRefreshListener implementation.

    @Override
    public void onSourceInfoRefreshed(
        MediaSource source, Timeline timeline, @Nullable Object manifest) {
      if (this.timeline != null) {
        // Ignore dynamic updates.
        return;
      }
      this.timeline = timeline;
      this.manifest = manifest;
      mediaPeriods = new MediaPeriod[timeline.getPeriodCount()];
      pendingPreparations = mediaPeriods.length;
      for (int i = 0; i < mediaPeriods.length; i++) {
        mediaPeriods[i] =
            mediaSource.createPeriod(
                new MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ i)),
                allocator,
                /* startPositionUs= */ 0);
        mediaPeriods[i].prepare(/* callback= */ this, /* positionUs= */ 0);
      }
    }

    // MediaPeriod.Callback implementation.

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      pendingPreparations--;
      if (pendingPreparations == 0) {
        mediaSourceHandler.removeMessages(MESSAGE_CHECK_FOR_FAILURE);
        downloadHelper.onMediaPrepared();
      }
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
      // Ignore.
    }
  }

  private static final class DownloadTrackSelection extends BaseTrackSelection {

    private static final class Factory implements TrackSelection.Factory {

      @Override
      public @NullableType TrackSelection[] createTrackSelections(
          @NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {
        @NullableType TrackSelection[] selections = new TrackSelection[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
          selections[i] =
              definitions[i] == null
                  ? null
                  : new DownloadTrackSelection(definitions[i].group, definitions[i].tracks);
        }
        return selections;
      }
    }

    public DownloadTrackSelection(TrackGroup trackGroup, int[] tracks) {
      super(trackGroup, tracks);
    }

    @Override
    public int getSelectedIndex() {
      return 0;
    }

    @Override
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Nullable
    @Override
    public Object getSelectionData() {
      return null;
    }
  }

  private static final class DummyBandwidthMeter implements BandwidthMeter {

    @Override
    public long getBitrateEstimate() {
      return 0;
    }

    @Nullable
    @Override
    public TransferListener getTransferListener() {
      return null;
    }

    @Override
    public void addEventListener(Handler eventHandler, EventListener eventListener) {
      // Do nothing.
    }

    @Override
    public void removeEventListener(EventListener eventListener) {
      // Do nothing.
    }
  }
}
