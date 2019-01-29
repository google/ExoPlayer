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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A {@link MediaPeriod} that loads an HLS stream.
 */
public final class HlsMediaPeriod implements MediaPeriod, HlsSampleStreamWrapper.Callback,
    HlsPlaylistTracker.PlaylistEventListener {

  private final HlsExtractorFactory extractorFactory;
  private final HlsPlaylistTracker playlistTracker;
  private final HlsDataSourceFactory dataSourceFactory;
  private final @Nullable TransferListener mediaTransferListener;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final boolean allowChunklessPreparation;

  private @Nullable Callback callback;
  private int pendingPrepareCount;
  private TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  private int[] selectedVariantIndices;
  private SequenceableLoader compositeSequenceableLoader;
  private boolean notifiedReadingStarted;

  /**
   * Creates an HLS media period.
   *
   * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the segments.
   * @param playlistTracker A tracker for HLS playlists.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for segments
   *     and keys.
   * @param mediaTransferListener The transfer listener to inform of any media data transfers. May
   *     be null if no listener is available.
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param compositeSequenceableLoaderFactory A factory to create composite {@link
   *     SequenceableLoader}s for when this media source loads data from multiple streams.
   * @param allowChunklessPreparation Whether chunkless preparation is allowed.
   */
  public HlsMediaPeriod(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      boolean allowChunklessPreparation) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.dataSourceFactory = dataSourceFactory;
    this.mediaTransferListener = mediaTransferListener;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.allowChunklessPreparation = allowChunklessPreparation;
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader();
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    sampleStreamWrappers = new HlsSampleStreamWrapper[0];
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[0];
    selectedVariantIndices = new int[0];
    eventDispatcher.mediaPeriodCreated();
  }

  public void release() {
    playlistTracker.removeListener(this);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
    callback = null;
    eventDispatcher.mediaPeriodReleased();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    playlistTracker.addListener(this);
    buildAndPrepareSampleStreamWrappers(positionUs);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.maybeThrowPrepareError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
    // See HlsMasterPlaylist.copy for interpretation of StreamKeys.
    HlsMasterPlaylist masterPlaylist = Assertions.checkNotNull(playlistTracker.getMasterPlaylist());
    boolean hasVariants = !masterPlaylist.variants.isEmpty();
    int audioWrapperOffset = hasVariants ? 1 : 0;
    int subtitleWrapperOffset = audioWrapperOffset + masterPlaylist.audios.size();

    TrackGroupArray mainWrapperTrackGroups;
    int mainWrapperPrimaryGroupIndex;
    if (hasVariants) {
      HlsSampleStreamWrapper mainWrapper = sampleStreamWrappers[0];
      mainWrapperTrackGroups = mainWrapper.getTrackGroups();
      mainWrapperPrimaryGroupIndex = mainWrapper.getPrimaryTrackGroupIndex();
    } else {
      mainWrapperTrackGroups = TrackGroupArray.EMPTY;
      mainWrapperPrimaryGroupIndex = 0;
    }

    List<StreamKey> streamKeys = new ArrayList<>();
    boolean needsPrimaryTrackGroupSelection = false;
    boolean hasPrimaryTrackGroupSelection = false;
    for (TrackSelection trackSelection : trackSelections) {
      TrackGroup trackSelectionGroup = trackSelection.getTrackGroup();
      int mainWrapperTrackGroupIndex = mainWrapperTrackGroups.indexOf(trackSelectionGroup);
      if (mainWrapperTrackGroupIndex != C.INDEX_UNSET) {
        if (mainWrapperTrackGroupIndex == mainWrapperPrimaryGroupIndex) {
          // Primary group in main wrapper.
          hasPrimaryTrackGroupSelection = true;
          for (int i = 0; i < trackSelection.length(); i++) {
            int variantIndex = selectedVariantIndices[trackSelection.getIndexInTrackGroup(i)];
            streamKeys.add(new StreamKey(HlsMasterPlaylist.GROUP_INDEX_VARIANT, variantIndex));
          }
        } else {
          // Embedded group in main wrapper.
          needsPrimaryTrackGroupSelection = true;
        }
      } else {
        // Audio or subtitle group.
        for (int i = audioWrapperOffset; i < sampleStreamWrappers.length; i++) {
          TrackGroupArray wrapperTrackGroups = sampleStreamWrappers[i].getTrackGroups();
          if (wrapperTrackGroups.indexOf(trackSelectionGroup) != C.INDEX_UNSET) {
            if (i < subtitleWrapperOffset) {
              streamKeys.add(
                  new StreamKey(HlsMasterPlaylist.GROUP_INDEX_AUDIO, i - audioWrapperOffset));
            } else {
              streamKeys.add(
                  new StreamKey(HlsMasterPlaylist.GROUP_INDEX_SUBTITLE, i - subtitleWrapperOffset));
            }
            break;
          }
        }
      }
    }
    if (needsPrimaryTrackGroupSelection && !hasPrimaryTrackGroupSelection) {
      // A track selection includes a variant-embedded track, but no variant is added yet. We use
      // the valid variant with the lowest bitrate to reduce overhead.
      int lowestBitrateIndex = selectedVariantIndices[0];
      int lowestBitrate = masterPlaylist.variants.get(selectedVariantIndices[0]).format.bitrate;
      for (int i = 1; i < selectedVariantIndices.length; i++) {
        int variantBitrate = masterPlaylist.variants.get(selectedVariantIndices[i]).format.bitrate;
        if (variantBitrate < lowestBitrate) {
          lowestBitrate = variantBitrate;
          lowestBitrateIndex = selectedVariantIndices[i];
        }
      }
      streamKeys.add(new StreamKey(HlsMasterPlaylist.GROUP_INDEX_VARIANT, lowestBitrateIndex));
    }
    return streamKeys;
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

    boolean forceReset = false;
    streamWrapperIndices.clear();
    // Select tracks for each child, copying the resulting streams back into a new streams array.
    SampleStream[] newStreams = new SampleStream[selections.length];
    SampleStream[] childStreams = new SampleStream[selections.length];
    TrackSelection[] childSelections = new TrackSelection[selections.length];
    int newEnabledSampleStreamWrapperCount = 0;
    HlsSampleStreamWrapper[] newEnabledSampleStreamWrappers =
        new HlsSampleStreamWrapper[sampleStreamWrappers.length];
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      HlsSampleStreamWrapper sampleStreamWrapper = sampleStreamWrappers[i];
      boolean wasReset = sampleStreamWrapper.selectTracks(childSelections, mayRetainStreamFlags,
          childStreams, streamResetFlags, positionUs, forceReset);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        if (selectionChildIndices[j] == i) {
          // Assert that the child provided a stream for the selection.
          Assertions.checkState(childStreams[j] != null);
          newStreams[j] = childStreams[j];
          wrapperEnabled = true;
          streamWrapperIndices.put(childStreams[j], i);
        } else if (streamChildIndices[j] == i) {
          // Assert that the child cleared any previous stream.
          Assertions.checkState(childStreams[j] == null);
        }
      }
      if (wrapperEnabled) {
        newEnabledSampleStreamWrappers[newEnabledSampleStreamWrapperCount] = sampleStreamWrapper;
        if (newEnabledSampleStreamWrapperCount++ == 0) {
          // The first enabled wrapper is responsible for initializing timestamp adjusters. This
          // way, if enabled, variants are responsible. Else audio renditions. Else text renditions.
          sampleStreamWrapper.setIsTimestampMaster(true);
          if (wasReset || enabledSampleStreamWrappers.length == 0
              || sampleStreamWrapper != enabledSampleStreamWrappers[0]) {
            // The wrapper responsible for initializing the timestamp adjusters was reset or
            // changed. We need to reset the timestamp adjuster provider and all other wrappers.
            timestampAdjusterProvider.reset();
            forceReset = true;
          }
        } else {
          sampleStreamWrapper.setIsTimestampMaster(false);
        }
      }
    }
    // Copy the new streams back into the streams array.
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // Update the local state.
    enabledSampleStreamWrappers = Arrays.copyOf(newEnabledSampleStreamWrappers,
        newEnabledSampleStreamWrapperCount);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(
            enabledSampleStreamWrappers);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (trackGroups == null) {
      // Preparation is still going on.
      for (HlsSampleStreamWrapper wrapper : sampleStreamWrappers) {
        wrapper.continuePreparing();
      }
      return false;
    } else {
      return compositeSequenceableLoader.continueLoading(positionUs);
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    if (enabledSampleStreamWrappers.length > 0) {
      // We need to reset all wrappers if the one responsible for initializing timestamp adjusters
      // is reset. Else each wrapper can decide whether to reset independently.
      boolean forceReset = enabledSampleStreamWrappers[0].seekToUs(positionUs, false);
      for (int i = 1; i < enabledSampleStreamWrappers.length; i++) {
        enabledSampleStreamWrappers[i].seekToUs(positionUs, forceReset);
      }
      if (forceReset) {
        timestampAdjusterProvider.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  // HlsSampleStreamWrapper.Callback implementation.

  @Override
  public void onPrepared() {
    if (--pendingPrepareCount > 0) {
      return;
    }

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
  }

  @Override
  public void onPlaylistRefreshRequired(HlsUrl url) {
    playlistTracker.refreshPlaylist(url);
  }

  @Override
  public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
    callback.onContinueLoadingRequested(this);
  }

  // PlaylistListener implementation.

  @Override
  public void onPlaylistChanged() {
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public boolean onPlaylistError(HlsUrl url, long blacklistDurationMs) {
    boolean noBlacklistingFailure = true;
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      noBlacklistingFailure &= streamWrapper.onPlaylistError(url, blacklistDurationMs);
    }
    callback.onContinueLoadingRequested(this);
    return noBlacklistingFailure;
  }

  // Internal methods.

  private void buildAndPrepareSampleStreamWrappers(long positionUs) {
    HlsMasterPlaylist masterPlaylist = playlistTracker.getMasterPlaylist();
    boolean hasVariants = !masterPlaylist.variants.isEmpty();
    List<HlsUrl> audioRenditions = masterPlaylist.audios;
    List<HlsUrl> subtitleRenditions = masterPlaylist.subtitles;

    int wrapperCount = (hasVariants ? 1 : 0) + audioRenditions.size() + subtitleRenditions.size();
    sampleStreamWrappers = new HlsSampleStreamWrapper[wrapperCount];
    pendingPrepareCount = wrapperCount;

    int currentWrapperIndex = 0;
    if (hasVariants) {
      buildAndPrepareMainSampleStreamWrapper(masterPlaylist, positionUs);
      currentWrapperIndex++;
    }

    // TODO: Build video stream wrappers here.

    // Audio sample stream wrappers.
    for (int i = 0; i < audioRenditions.size(); i++) {
      HlsUrl audioRendition = audioRenditions.get(i);
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              C.TRACK_TYPE_AUDIO,
              new HlsUrl[] {audioRendition},
              null,
              Collections.emptyList(),
              positionUs);
      sampleStreamWrappers[currentWrapperIndex++] = sampleStreamWrapper;
      Format renditionFormat = audioRendition.format;
      if (allowChunklessPreparation && renditionFormat.codecs != null) {
        sampleStreamWrapper.prepareWithMasterPlaylistInfo(
            new TrackGroupArray(new TrackGroup(audioRendition.format)), 0, TrackGroupArray.EMPTY);
      }
    }

    // Subtitle stream wrappers. We can always use master playlist information to prepare these.
    for (int i = 0; i < subtitleRenditions.size(); i++) {
      HlsUrl url = subtitleRenditions.get(i);
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              C.TRACK_TYPE_TEXT, new HlsUrl[] {url}, null, Collections.emptyList(), positionUs);
      sampleStreamWrappers[currentWrapperIndex++] = sampleStreamWrapper;
      sampleStreamWrapper.prepareWithMasterPlaylistInfo(
          new TrackGroupArray(new TrackGroup(url.format)), 0, TrackGroupArray.EMPTY);
    }

    // Set timestamp master and trigger preparation (if not already prepared)
    sampleStreamWrappers[0].setIsTimestampMaster(true);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.continuePreparing();
    }

    // All wrappers are enabled during preparation.
    enabledSampleStreamWrappers = sampleStreamWrappers;
  }

  /**
   * This method creates and starts preparation of the main {@link HlsSampleStreamWrapper}.
   *
   * <p>The main sample stream wrapper is the first element of {@link #sampleStreamWrappers}. It
   * provides {@link SampleStream}s for the variant urls in the master playlist. It may be adaptive
   * and may contain multiple muxed tracks.
   *
   * <p>If chunkless preparation is allowed, the media period will try preparation without segment
   * downloads. This is only possible if variants contain the CODECS attribute. If not, traditional
   * preparation with segment downloads will take place. The following points apply to chunkless
   * preparation:
   *
   * <ul>
   *   <li>A muxed audio track will be exposed if the codecs list contain an audio entry and the
   *       master playlist either contains an EXT-X-MEDIA tag without the URI attribute or does not
   *       contain any EXT-X-MEDIA tag.
   *   <li>Closed captions will only be exposed if they are declared by the master playlist.
   *   <li>An ID3 track is exposed preemptively, in case the segments contain an ID3 track.
   * </ul>
   *
   * @param masterPlaylist The HLS master playlist.
   * @param positionUs If preparation requires any chunk downloads, the position in microseconds at
   *     which downloading should start. Ignored otherwise.
   */
  private void buildAndPrepareMainSampleStreamWrapper(
      HlsMasterPlaylist masterPlaylist, long positionUs) {
    int[] variantTypes = new int[masterPlaylist.variants.size()];
    int videoVariantCount = 0;
    int audioVariantCount = 0;
    for (int i = 0; i < masterPlaylist.variants.size(); i++) {
      HlsUrl variant = masterPlaylist.variants.get(i);
      Format format = variant.format;
      if (format.height > 0 || Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_VIDEO) != null) {
        variantTypes[i] = C.TRACK_TYPE_VIDEO;
        videoVariantCount++;
      } else if (Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_AUDIO) != null) {
        variantTypes[i] = C.TRACK_TYPE_AUDIO;
        audioVariantCount++;
      } else {
        variantTypes[i] = C.TRACK_TYPE_UNKNOWN;
      }
    }
    boolean useVideoVariantsOnly = false;
    boolean useNonAudioVariantsOnly = false;
    int selectedVariantsCount = variantTypes.length;
    if (videoVariantCount > 0) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // master playlist are marked consistently, and hence that we have the full set. Filter out
      // any other variants, which are likely to be audio only.
      useVideoVariantsOnly = true;
      selectedVariantsCount = videoVariantCount;
    } else if (audioVariantCount < variantTypes.length) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      useNonAudioVariantsOnly = true;
      selectedVariantsCount = variantTypes.length - audioVariantCount;
    }
    HlsUrl[] selectedVariants = new HlsUrl[selectedVariantsCount];
    selectedVariantIndices = new int[selectedVariantsCount];
    int outIndex = 0;
    for (int i = 0; i < masterPlaylist.variants.size(); i++) {
      if ((!useVideoVariantsOnly || variantTypes[i] == C.TRACK_TYPE_VIDEO)
          && (!useNonAudioVariantsOnly || variantTypes[i] != C.TRACK_TYPE_AUDIO)) {
        selectedVariants[outIndex] = masterPlaylist.variants.get(i);
        selectedVariantIndices[outIndex++] = i;
      }
    }
    String codecs = selectedVariants[0].format.codecs;
    HlsSampleStreamWrapper sampleStreamWrapper =
        buildSampleStreamWrapper(
            C.TRACK_TYPE_DEFAULT,
            selectedVariants,
            masterPlaylist.muxedAudioFormat,
            masterPlaylist.muxedCaptionFormats,
            positionUs);
    sampleStreamWrappers[0] = sampleStreamWrapper;
    if (allowChunklessPreparation && codecs != null) {
      boolean variantsContainVideoCodecs = Util.getCodecsOfType(codecs, C.TRACK_TYPE_VIDEO) != null;
      boolean variantsContainAudioCodecs = Util.getCodecsOfType(codecs, C.TRACK_TYPE_AUDIO) != null;
      List<TrackGroup> muxedTrackGroups = new ArrayList<>();
      if (variantsContainVideoCodecs) {
        Format[] videoFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < videoFormats.length; i++) {
          videoFormats[i] = deriveVideoFormat(selectedVariants[i].format);
        }
        muxedTrackGroups.add(new TrackGroup(videoFormats));

        if (variantsContainAudioCodecs
            && (masterPlaylist.muxedAudioFormat != null || masterPlaylist.audios.isEmpty())) {
          muxedTrackGroups.add(
              new TrackGroup(
                  deriveAudioFormat(
                      selectedVariants[0].format,
                      masterPlaylist.muxedAudioFormat,
                      /* isPrimaryTrackInVariant= */ false)));
        }
        List<Format> ccFormats = masterPlaylist.muxedCaptionFormats;
        if (ccFormats != null) {
          for (int i = 0; i < ccFormats.size(); i++) {
            muxedTrackGroups.add(new TrackGroup(ccFormats.get(i)));
          }
        }
      } else if (variantsContainAudioCodecs) {
        // Variants only contain audio.
        Format[] audioFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < audioFormats.length; i++) {
          Format variantFormat = selectedVariants[i].format;
          audioFormats[i] =
              deriveAudioFormat(
                  variantFormat,
                  masterPlaylist.muxedAudioFormat,
                  /* isPrimaryTrackInVariant= */ true);
        }
        muxedTrackGroups.add(new TrackGroup(audioFormats));
      } else {
        // Variants contain codecs but no video or audio entries could be identified.
        throw new IllegalArgumentException("Unexpected codecs attribute: " + codecs);
      }

      TrackGroup id3TrackGroup =
          new TrackGroup(
              Format.createSampleFormat(
                  /* id= */ "ID3",
                  MimeTypes.APPLICATION_ID3,
                  /* codecs= */ null,
                  /* bitrate= */ Format.NO_VALUE,
                  /* drmInitData= */ null));
      muxedTrackGroups.add(id3TrackGroup);

      sampleStreamWrapper.prepareWithMasterPlaylistInfo(
          new TrackGroupArray(muxedTrackGroups.toArray(new TrackGroup[0])),
          0,
          new TrackGroupArray(id3TrackGroup));
    }
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(int trackType, HlsUrl[] variants,
      Format muxedAudioFormat, List<Format> muxedCaptionFormats, long positionUs) {
    HlsChunkSource defaultChunkSource =
        new HlsChunkSource(
            extractorFactory,
            playlistTracker,
            variants,
            dataSourceFactory,
            mediaTransferListener,
            timestampAdjusterProvider,
            muxedCaptionFormats);
    return new HlsSampleStreamWrapper(
        trackType,
        /* callback= */ this,
        defaultChunkSource,
        allocator,
        positionUs,
        muxedAudioFormat,
        loadErrorHandlingPolicy,
        eventDispatcher);
  }

  private static Format deriveVideoFormat(Format variantFormat) {
    String codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_VIDEO);
    String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    return Format.createVideoContainerFormat(
        variantFormat.id,
        variantFormat.label,
        variantFormat.containerMimeType,
        sampleMimeType,
        codecs,
        variantFormat.bitrate,
        variantFormat.width,
        variantFormat.height,
        variantFormat.frameRate,
        /* initializationData= */ null,
        variantFormat.selectionFlags);
  }

  private static Format deriveAudioFormat(
      Format variantFormat, Format mediaTagFormat, boolean isPrimaryTrackInVariant) {
    String codecs;
    int channelCount = Format.NO_VALUE;
    int selectionFlags = 0;
    String language = null;
    String label = null;
    if (mediaTagFormat != null) {
      codecs = mediaTagFormat.codecs;
      channelCount = mediaTagFormat.channelCount;
      selectionFlags = mediaTagFormat.selectionFlags;
      language = mediaTagFormat.language;
      label = mediaTagFormat.label;
    } else {
      codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_AUDIO);
      if (isPrimaryTrackInVariant) {
        channelCount = variantFormat.channelCount;
        selectionFlags = variantFormat.selectionFlags;
        language = variantFormat.language;
        label = variantFormat.label;
      }
    }
    String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    int bitrate = isPrimaryTrackInVariant ? variantFormat.bitrate : Format.NO_VALUE;
    return Format.createAudioContainerFormat(
        variantFormat.id,
        label,
        variantFormat.containerMimeType,
        sampleMimeType,
        codecs,
        bitrate,
        channelCount,
        /* sampleRate= */ Format.NO_VALUE,
        /* initializationData= */ null,
        selectionFlags,
        language);
  }

}
