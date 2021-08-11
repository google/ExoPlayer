/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.source.ads.ServerSideInsertedAdsUtil.getAdCountInGroup;
import static com.google.android.exoplayer2.source.ads.ServerSideInsertedAdsUtil.getMediaPeriodPositionUs;
import static com.google.android.exoplayer2.source.ads.ServerSideInsertedAdsUtil.getMediaPeriodPositionUsForAd;
import static com.google.android.exoplayer2.source.ads.ServerSideInsertedAdsUtil.getMediaPeriodPositionUsForContent;
import static com.google.android.exoplayer2.source.ads.ServerSideInsertedAdsUtil.getStreamPositionUs;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Handler;
import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.ForwardingTimeline;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaSource} for server-side inserted ad breaks.
 *
 * <p>The media source publishes a {@link Timeline} for the wrapped {@link MediaSource} with the
 * server-side inserted ad breaks and ensures that playback continues seamlessly with the wrapped
 * media across all transitions.
 *
 * <p>The ad breaks need to be specified using {@link #setAdPlaybackState} and can be updated during
 * playback.
 */
public final class ServerSideInsertedAdsMediaSource extends BaseMediaSource
    implements MediaSource.MediaSourceCaller, MediaSourceEventListener, DrmSessionEventListener {

  private final MediaSource mediaSource;
  private final ListMultimap<Long, SharedMediaPeriod> mediaPeriods;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcherWithoutId;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcherWithoutId;

  @GuardedBy("this")
  @Nullable
  private Handler playbackHandler;

  @Nullable private SharedMediaPeriod lastUsedMediaPeriod;
  @Nullable private Timeline contentTimeline;
  private AdPlaybackState adPlaybackState;

  /**
   * Creates the media source.
   *
   * @param mediaSource The {@link MediaSource} to wrap.
   */
  // Calling BaseMediaSource.createEventDispatcher from the constructor.
  @SuppressWarnings("nullness:method.invocation")
  public ServerSideInsertedAdsMediaSource(MediaSource mediaSource) {
    this.mediaSource = mediaSource;
    mediaPeriods = ArrayListMultimap.create();
    adPlaybackState = AdPlaybackState.NONE;
    mediaSourceEventDispatcherWithoutId = createEventDispatcher(/* mediaPeriodId= */ null);
    drmEventDispatcherWithoutId = createDrmEventDispatcher(/* mediaPeriodId= */ null);
  }

  /**
   * Sets the {@link AdPlaybackState} published by this source.
   *
   * <p>May be called from any thread.
   *
   * <p>Must only contain server-side inserted ad groups. The number of ad groups and the number of
   * ads within an ad group may only increase. The durations of ads may change and the positions of
   * future ad groups may change. Post-roll ad groups with {@link C#TIME_END_OF_SOURCE} must be
   * empty and can be used as a placeholder for a future ad group.
   *
   * @param adPlaybackState The new {@link AdPlaybackState}.
   */
  public void setAdPlaybackState(AdPlaybackState adPlaybackState) {
    checkArgument(adPlaybackState.adGroupCount >= this.adPlaybackState.adGroupCount);
    for (int i = adPlaybackState.removedAdGroupCount; i < adPlaybackState.adGroupCount; i++) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(i);
      checkArgument(adGroup.isServerSideInserted);
      if (i < this.adPlaybackState.adGroupCount) {
        checkArgument(
            getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i)
                >= getAdCountInGroup(this.adPlaybackState, /* adGroupIndex= */ i));
      }
      if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
        checkArgument(getAdCountInGroup(adPlaybackState, /* adGroupIndex= */ i) == 0);
      }
    }
    synchronized (this) {
      if (playbackHandler == null) {
        this.adPlaybackState = adPlaybackState;
      } else {
        playbackHandler.post(
            () -> {
              for (SharedMediaPeriod mediaPeriod : mediaPeriods.values()) {
                mediaPeriod.updateAdPlaybackState(adPlaybackState);
              }
              if (lastUsedMediaPeriod != null) {
                lastUsedMediaPeriod.updateAdPlaybackState(adPlaybackState);
              }
              this.adPlaybackState = adPlaybackState;
              if (contentTimeline != null) {
                refreshSourceInfo(
                    new ServerSideInsertedAdsTimeline(contentTimeline, adPlaybackState));
              }
            });
      }
    }
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaSource.getMediaItem();
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    Handler handler = Util.createHandlerForCurrentLooper();
    synchronized (this) {
      playbackHandler = handler;
    }
    mediaSource.addEventListener(handler, /* eventListener= */ this);
    mediaSource.addDrmEventListener(handler, /* eventListener= */ this);
    mediaSource.prepareSource(/* caller= */ this, mediaTransferListener);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    mediaSource.maybeThrowSourceInfoRefreshError();
  }

  @Override
  protected void enableInternal() {
    mediaSource.enable(/* caller= */ this);
  }

  @Override
  protected void disableInternal() {
    releaseLastUsedMediaPeriod();
    mediaSource.disable(/* caller= */ this);
  }

  @Override
  public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
    this.contentTimeline = timeline;
    if (AdPlaybackState.NONE.equals(adPlaybackState)) {
      return;
    }
    refreshSourceInfo(new ServerSideInsertedAdsTimeline(timeline, adPlaybackState));
  }

  @Override
  protected void releaseSourceInternal() {
    releaseLastUsedMediaPeriod();
    contentTimeline = null;
    synchronized (this) {
      playbackHandler = null;
    }
    mediaSource.releaseSource(/* caller= */ this);
    mediaSource.removeEventListener(/* eventListener= */ this);
    mediaSource.removeDrmEventListener(/* eventListener= */ this);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    SharedMediaPeriod sharedPeriod;
    if (lastUsedMediaPeriod != null) {
      sharedPeriod = lastUsedMediaPeriod;
      lastUsedMediaPeriod = null;
      mediaPeriods.put(id.windowSequenceNumber, sharedPeriod);
    } else {
      @Nullable
      SharedMediaPeriod lastExistingPeriod =
          Iterables.getLast(mediaPeriods.get(id.windowSequenceNumber), /* defaultValue= */ null);
      if (lastExistingPeriod != null
          && lastExistingPeriod.canReuseMediaPeriod(id, startPositionUs)) {
        sharedPeriod = lastExistingPeriod;
      } else {
        long streamPositionUs = getStreamPositionUs(startPositionUs, id, adPlaybackState);
        sharedPeriod =
            new SharedMediaPeriod(
                mediaSource.createPeriod(
                    new MediaPeriodId(id.periodUid, id.windowSequenceNumber),
                    allocator,
                    streamPositionUs),
                adPlaybackState);
        mediaPeriods.put(id.windowSequenceNumber, sharedPeriod);
      }
    }
    MediaPeriodImpl mediaPeriod =
        new MediaPeriodImpl(
            sharedPeriod, id, createEventDispatcher(id), createDrmEventDispatcher(id));
    sharedPeriod.add(mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaPeriodImpl mediaPeriodImpl = (MediaPeriodImpl) mediaPeriod;
    mediaPeriodImpl.sharedPeriod.remove(mediaPeriodImpl);
    if (mediaPeriodImpl.sharedPeriod.isUnused()) {
      mediaPeriods.remove(
          mediaPeriodImpl.mediaPeriodId.windowSequenceNumber, mediaPeriodImpl.sharedPeriod);
      if (mediaPeriods.isEmpty()) {
        // Keep until disabled.
        lastUsedMediaPeriod = mediaPeriodImpl.sharedPeriod;
      } else {
        mediaPeriodImpl.sharedPeriod.release(mediaSource);
      }
    }
  }

  @Override
  public void onDrmSessionAcquired(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, @DrmSession.State int state) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ true);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmSessionAcquired(state);
    } else {
      mediaPeriod.drmEventDispatcher.drmSessionAcquired(state);
    }
  }

  @Override
  public void onDrmKeysLoaded(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmKeysLoaded();
    } else {
      mediaPeriod.drmEventDispatcher.drmKeysLoaded();
    }
  }

  @Override
  public void onDrmSessionManagerError(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, Exception error) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmSessionManagerError(error);
    } else {
      mediaPeriod.drmEventDispatcher.drmSessionManagerError(error);
    }
  }

  @Override
  public void onDrmKeysRestored(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmKeysRestored();
    } else {
      mediaPeriod.drmEventDispatcher.drmKeysRestored();
    }
  }

  @Override
  public void onDrmKeysRemoved(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmKeysRemoved();
    } else {
      mediaPeriod.drmEventDispatcher.drmKeysRemoved();
    }
  }

  @Override
  public void onDrmSessionReleased(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(
            mediaPeriodId, /* mediaLoadData= */ null, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      drmEventDispatcherWithoutId.drmSessionReleased();
    } else {
      mediaPeriod.drmEventDispatcher.drmSessionReleased();
    }
  }

  @Override
  public void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ true);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.loadStarted(loadEventInfo, mediaLoadData);
    } else {
      mediaPeriod.sharedPeriod.onLoadStarted(loadEventInfo, mediaLoadData);
      mediaPeriod.mediaSourceEventDispatcher.loadStarted(
          loadEventInfo, correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState));
    }
  }

  @Override
  public void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ true);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.loadCompleted(loadEventInfo, mediaLoadData);
    } else {
      mediaPeriod.sharedPeriod.onLoadFinished(loadEventInfo);
      mediaPeriod.mediaSourceEventDispatcher.loadCompleted(
          loadEventInfo, correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState));
    }
  }

  @Override
  public void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ true);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.loadCanceled(loadEventInfo, mediaLoadData);
    } else {
      mediaPeriod.sharedPeriod.onLoadFinished(loadEventInfo);
      mediaPeriod.mediaSourceEventDispatcher.loadCanceled(
          loadEventInfo, correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState));
    }
  }

  @Override
  public void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ true);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.loadError(
          loadEventInfo, mediaLoadData, error, wasCanceled);
    } else {
      if (wasCanceled) {
        mediaPeriod.sharedPeriod.onLoadFinished(loadEventInfo);
      }
      mediaPeriod.mediaSourceEventDispatcher.loadError(
          loadEventInfo,
          correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState),
          error,
          wasCanceled);
    }
  }

  @Override
  public void onUpstreamDiscarded(
      int windowIndex, MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.upstreamDiscarded(mediaLoadData);
    } else {
      mediaPeriod.mediaSourceEventDispatcher.upstreamDiscarded(
          correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState));
    }
  }

  @Override
  public void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
    @Nullable
    MediaPeriodImpl mediaPeriod =
        getMediaPeriodForEvent(mediaPeriodId, mediaLoadData, /* useLoadingPeriod= */ false);
    if (mediaPeriod == null) {
      mediaSourceEventDispatcherWithoutId.downstreamFormatChanged(mediaLoadData);
    } else {
      mediaPeriod.sharedPeriod.onDownstreamFormatChanged(mediaPeriod, mediaLoadData);
      mediaPeriod.mediaSourceEventDispatcher.downstreamFormatChanged(
          correctMediaLoadData(mediaPeriod, mediaLoadData, adPlaybackState));
    }
  }

  private void releaseLastUsedMediaPeriod() {
    if (lastUsedMediaPeriod != null) {
      lastUsedMediaPeriod.release(mediaSource);
      lastUsedMediaPeriod = null;
    }
  }

  @Nullable
  private MediaPeriodImpl getMediaPeriodForEvent(
      @Nullable MediaPeriodId mediaPeriodId,
      @Nullable MediaLoadData mediaLoadData,
      boolean useLoadingPeriod) {
    if (mediaPeriodId == null) {
      return null;
    }
    List<SharedMediaPeriod> periods = mediaPeriods.get(mediaPeriodId.windowSequenceNumber);
    if (periods.isEmpty()) {
      return null;
    }
    if (useLoadingPeriod) {
      SharedMediaPeriod loadingPeriod = Iterables.getLast(periods);
      return loadingPeriod.loadingPeriod != null
          ? loadingPeriod.loadingPeriod
          : Iterables.getLast(loadingPeriod.mediaPeriods);
    }
    for (int i = 0; i < periods.size(); i++) {
      @Nullable MediaPeriodImpl period = periods.get(i).getMediaPeriodForEvent(mediaLoadData);
      if (period != null) {
        return period;
      }
    }
    return periods.get(0).mediaPeriods.get(0);
  }

  private static long getMediaPeriodEndPositionUs(
      MediaPeriodImpl mediaPeriod, AdPlaybackState adPlaybackState) {
    MediaPeriodId id = mediaPeriod.mediaPeriodId;
    if (id.isAd()) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(id.adGroupIndex);
      return adGroup.count == C.LENGTH_UNSET ? 0 : adGroup.durationsUs[id.adIndexInAdGroup];
    }
    if (id.nextAdGroupIndex == C.INDEX_UNSET) {
      return Long.MAX_VALUE;
    }
    AdPlaybackState.AdGroup nextAdGroup = adPlaybackState.getAdGroup(id.nextAdGroupIndex);
    return nextAdGroup.timeUs == C.TIME_END_OF_SOURCE ? Long.MAX_VALUE : nextAdGroup.timeUs;
  }

  private static MediaLoadData correctMediaLoadData(
      MediaPeriodImpl mediaPeriod, MediaLoadData mediaLoadData, AdPlaybackState adPlaybackState) {
    return new MediaLoadData(
        mediaLoadData.dataType,
        mediaLoadData.trackType,
        mediaLoadData.trackFormat,
        mediaLoadData.trackSelectionReason,
        mediaLoadData.trackSelectionData,
        correctMediaLoadDataPositionMs(
            mediaLoadData.mediaStartTimeMs, mediaPeriod, adPlaybackState),
        correctMediaLoadDataPositionMs(mediaLoadData.mediaEndTimeMs, mediaPeriod, adPlaybackState));
  }

  private static long correctMediaLoadDataPositionMs(
      long mediaPositionMs, MediaPeriodImpl mediaPeriod, AdPlaybackState adPlaybackState) {
    if (mediaPositionMs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    long mediaPositionUs = C.msToUs(mediaPositionMs);
    MediaPeriodId id = mediaPeriod.mediaPeriodId;
    long correctedPositionUs =
        id.isAd()
            ? getMediaPeriodPositionUsForAd(
                mediaPositionUs, id.adGroupIndex, id.adIndexInAdGroup, adPlaybackState)
            // Ignore nextAdGroupIndex for content ids to correct timestamps that fall into future
            // content pieces (beyond nextAdGroupIndex).
            : getMediaPeriodPositionUsForContent(
                mediaPositionUs, /* nextAdGroupIndex= */ C.INDEX_UNSET, adPlaybackState);
    return C.usToMs(correctedPositionUs);
  }

  private static final class SharedMediaPeriod implements MediaPeriod.Callback {

    private final MediaPeriod actualMediaPeriod;
    private final List<MediaPeriodImpl> mediaPeriods;
    private final Map<Long, Pair<LoadEventInfo, MediaLoadData>> activeLoads;

    private AdPlaybackState adPlaybackState;
    @Nullable private MediaPeriodImpl loadingPeriod;
    private boolean hasStartedPreparing;
    private boolean isPrepared;
    public @NullableType ExoTrackSelection[] trackSelections;
    public @NullableType SampleStream[] sampleStreams;
    public @NullableType MediaLoadData[] lastDownstreamFormatChangeData;

    public SharedMediaPeriod(MediaPeriod actualMediaPeriod, AdPlaybackState adPlaybackState) {
      this.actualMediaPeriod = actualMediaPeriod;
      this.adPlaybackState = adPlaybackState;
      mediaPeriods = new ArrayList<>();
      activeLoads = new HashMap<>();
      trackSelections = new ExoTrackSelection[0];
      sampleStreams = new SampleStream[0];
      lastDownstreamFormatChangeData = new MediaLoadData[0];
    }

    public void updateAdPlaybackState(AdPlaybackState adPlaybackState) {
      this.adPlaybackState = adPlaybackState;
    }

    public void add(MediaPeriodImpl mediaPeriod) {
      mediaPeriods.add(mediaPeriod);
    }

    public void remove(MediaPeriodImpl mediaPeriod) {
      if (mediaPeriod.equals(loadingPeriod)) {
        loadingPeriod = null;
        activeLoads.clear();
      }
      mediaPeriods.remove(mediaPeriod);
    }

    public boolean isUnused() {
      return mediaPeriods.isEmpty();
    }

    public void release(MediaSource mediaSource) {
      mediaSource.releasePeriod(actualMediaPeriod);
    }

    public boolean canReuseMediaPeriod(MediaPeriodId id, long positionUs) {
      MediaPeriodImpl previousPeriod = Iterables.getLast(mediaPeriods);
      long previousEndPositionUs =
          getStreamPositionUs(
              getMediaPeriodEndPositionUs(previousPeriod, adPlaybackState),
              previousPeriod.mediaPeriodId,
              adPlaybackState);
      long startPositionUs = getStreamPositionUs(positionUs, id, adPlaybackState);
      return startPositionUs == previousEndPositionUs;
    }

    @Nullable
    public MediaPeriodImpl getMediaPeriodForEvent(@Nullable MediaLoadData mediaLoadData) {
      if (mediaLoadData != null && mediaLoadData.mediaStartTimeMs != C.TIME_UNSET) {
        for (int i = 0; i < mediaPeriods.size(); i++) {
          MediaPeriodImpl mediaPeriod = mediaPeriods.get(i);
          long startTimeInPeriodUs =
              getMediaPeriodPositionUs(
                  C.msToUs(mediaLoadData.mediaStartTimeMs),
                  mediaPeriod.mediaPeriodId,
                  adPlaybackState);
          long mediaPeriodEndPositionUs = getMediaPeriodEndPositionUs(mediaPeriod, adPlaybackState);
          if (startTimeInPeriodUs >= 0 && startTimeInPeriodUs < mediaPeriodEndPositionUs) {
            return mediaPeriod;
          }
        }
      }
      return null;
    }

    public void prepare(MediaPeriodImpl mediaPeriod, long positionUs) {
      mediaPeriod.lastStartPositionUs = positionUs;
      if (hasStartedPreparing) {
        if (isPrepared) {
          checkNotNull(mediaPeriod.callback).onPrepared(mediaPeriod);
        }
        return;
      }
      hasStartedPreparing = true;
      long preparePositionUs =
          getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      actualMediaPeriod.prepare(/* callback= */ this, preparePositionUs);
    }

    public void maybeThrowPrepareError() throws IOException {
      actualMediaPeriod.maybeThrowPrepareError();
    }

    public TrackGroupArray getTrackGroups() {
      return actualMediaPeriod.getTrackGroups();
    }

    public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
      return actualMediaPeriod.getStreamKeys(trackSelections);
    }

    public boolean continueLoading(MediaPeriodImpl mediaPeriod, long positionUs) {
      @Nullable MediaPeriodImpl loadingPeriod = this.loadingPeriod;
      if (loadingPeriod != null && !mediaPeriod.equals(loadingPeriod)) {
        for (Pair<LoadEventInfo, MediaLoadData> loadData : activeLoads.values()) {
          loadingPeriod.mediaSourceEventDispatcher.loadCompleted(
              loadData.first,
              correctMediaLoadData(loadingPeriod, loadData.second, adPlaybackState));
          mediaPeriod.mediaSourceEventDispatcher.loadStarted(
              loadData.first, correctMediaLoadData(mediaPeriod, loadData.second, adPlaybackState));
        }
      }
      this.loadingPeriod = mediaPeriod;
      long actualPlaybackPositionUs =
          getStreamPositionUsWithNotYetStartedHandling(mediaPeriod, positionUs);
      return actualMediaPeriod.continueLoading(actualPlaybackPositionUs);
    }

    public boolean isLoading(MediaPeriodImpl mediaPeriod) {
      return mediaPeriod.equals(loadingPeriod) && actualMediaPeriod.isLoading();
    }

    public long getBufferedPositionUs(MediaPeriodImpl mediaPeriod) {
      return getMediaPeriodPositionUsWithEndOfSourceHandling(
          mediaPeriod, actualMediaPeriod.getBufferedPositionUs());
    }

    public long getNextLoadPositionUs(MediaPeriodImpl mediaPeriod) {
      return getMediaPeriodPositionUsWithEndOfSourceHandling(
          mediaPeriod, actualMediaPeriod.getNextLoadPositionUs());
    }

    public long seekToUs(MediaPeriodImpl mediaPeriod, long positionUs) {
      long actualRequestedPositionUs =
          getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      long newActualPositionUs = actualMediaPeriod.seekToUs(actualRequestedPositionUs);
      return getMediaPeriodPositionUs(
          newActualPositionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
    }

    public long getAdjustedSeekPositionUs(
        MediaPeriodImpl mediaPeriod, long positionUs, SeekParameters seekParameters) {
      long actualRequestedPositionUs =
          getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      long adjustedActualPositionUs =
          actualMediaPeriod.getAdjustedSeekPositionUs(actualRequestedPositionUs, seekParameters);
      return getMediaPeriodPositionUs(
          adjustedActualPositionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
    }

    public void discardBuffer(MediaPeriodImpl mediaPeriod, long positionUs, boolean toKeyframe) {
      long actualPositionUs =
          getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      actualMediaPeriod.discardBuffer(actualPositionUs, toKeyframe);
    }

    public void reevaluateBuffer(MediaPeriodImpl mediaPeriod, long positionUs) {
      actualMediaPeriod.reevaluateBuffer(
          getStreamPositionUsWithNotYetStartedHandling(mediaPeriod, positionUs));
    }

    public long readDiscontinuity(MediaPeriodImpl mediaPeriod) {
      if (!mediaPeriod.equals(mediaPeriods.get(0))) {
        return C.TIME_UNSET;
      }
      long actualDiscontinuityPositionUs = actualMediaPeriod.readDiscontinuity();
      return actualDiscontinuityPositionUs == C.TIME_UNSET
          ? C.TIME_UNSET
          : getMediaPeriodPositionUs(
              actualDiscontinuityPositionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
    }

    public long selectTracks(
        MediaPeriodImpl mediaPeriod,
        @NullableType ExoTrackSelection[] selections,
        boolean[] mayRetainStreamFlags,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long positionUs) {
      mediaPeriod.lastStartPositionUs = positionUs;
      if (mediaPeriod.equals(mediaPeriods.get(0))) {
        // Do the real selection for the current first period in the list.
        trackSelections = Arrays.copyOf(selections, selections.length);
        long requestedPositionUs =
            getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
        @NullableType
        SampleStream[] realStreams =
            sampleStreams.length == 0
                ? new SampleStream[selections.length]
                : Arrays.copyOf(sampleStreams, sampleStreams.length);
        long startPositionUs =
            actualMediaPeriod.selectTracks(
                selections,
                mayRetainStreamFlags,
                realStreams,
                streamResetFlags,
                requestedPositionUs);
        this.sampleStreams = Arrays.copyOf(realStreams, realStreams.length);
        lastDownstreamFormatChangeData =
            Arrays.copyOf(lastDownstreamFormatChangeData, realStreams.length);
        for (int i = 0; i < realStreams.length; i++) {
          if (realStreams[i] == null) {
            streams[i] = null;
            lastDownstreamFormatChangeData[i] = null;
          } else if (streams[i] == null || streamResetFlags[i]) {
            streams[i] = new SampleStreamImpl(mediaPeriod, /* streamIndex= */ i);
            lastDownstreamFormatChangeData[i] = null;
          }
        }
        return getMediaPeriodPositionUs(
            startPositionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      }
      // All subsequent periods need to have the same selection. Ignore tracks or add empty tracks
      // if this isn't the case.
      for (int i = 0; i < selections.length; i++) {
        if (selections[i] != null) {
          streamResetFlags[i] = !mayRetainStreamFlags[i] || streams[i] == null;
          if (streamResetFlags[i]) {
            streams[i] =
                Util.areEqual(trackSelections[i], selections[i])
                    ? new SampleStreamImpl(mediaPeriod, /* streamIndex= */ i)
                    : new EmptySampleStream();
          }
        } else {
          streams[i] = null;
          streamResetFlags[i] = true;
        }
      }
      return positionUs;
    }

    @SampleStream.ReadDataResult
    public int readData(
        MediaPeriodImpl mediaPeriod,
        int streamIndex,
        FormatHolder formatHolder,
        DecoderInputBuffer buffer,
        @SampleStream.ReadFlags int readFlags) {
      @SampleStream.ReadFlags
      int peekingFlags = readFlags | SampleStream.FLAG_PEEK | SampleStream.FLAG_OMIT_SAMPLE_DATA;
      @SampleStream.ReadDataResult
      int result =
          castNonNull(sampleStreams[streamIndex]).readData(formatHolder, buffer, peekingFlags);
      long adjustedTimeUs =
          getMediaPeriodPositionUsWithEndOfSourceHandling(mediaPeriod, buffer.timeUs);
      if ((result == C.RESULT_BUFFER_READ && adjustedTimeUs == C.TIME_END_OF_SOURCE)
          || (result == C.RESULT_NOTHING_READ
              && getBufferedPositionUs(mediaPeriod) == C.TIME_END_OF_SOURCE
              && !buffer.waitingForKeys)) {
        maybeNotifyDownstreamFormatChanged(mediaPeriod, streamIndex);
        buffer.clear();
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      }
      if (result == C.RESULT_BUFFER_READ) {
        maybeNotifyDownstreamFormatChanged(mediaPeriod, streamIndex);
        castNonNull(sampleStreams[streamIndex]).readData(formatHolder, buffer, readFlags);
        buffer.timeUs = adjustedTimeUs;
      }
      return result;
    }

    public int skipData(MediaPeriodImpl mediaPeriod, int streamIndex, long positionUs) {
      long actualPositionUs =
          getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      return castNonNull(sampleStreams[streamIndex]).skipData(actualPositionUs);
    }

    public boolean isReady(int streamIndex) {
      return castNonNull(sampleStreams[streamIndex]).isReady();
    }

    public void maybeThrowError(int streamIndex) throws IOException {
      castNonNull(sampleStreams[streamIndex]).maybeThrowError();
    }

    public void onDownstreamFormatChanged(
        MediaPeriodImpl mediaPeriod, MediaLoadData mediaLoadData) {
      int streamIndex = findMatchingStreamIndex(mediaLoadData);
      if (streamIndex != C.INDEX_UNSET) {
        lastDownstreamFormatChangeData[streamIndex] = mediaLoadData;
        mediaPeriod.hasNotifiedDownstreamFormatChange[streamIndex] = true;
      }
    }

    public void onLoadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      activeLoads.put(loadEventInfo.loadTaskId, Pair.create(loadEventInfo, mediaLoadData));
    }

    public void onLoadFinished(LoadEventInfo loadEventInfo) {
      activeLoads.remove(loadEventInfo.loadTaskId);
    }

    @Override
    public void onPrepared(MediaPeriod actualMediaPeriod) {
      isPrepared = true;
      for (int i = 0; i < mediaPeriods.size(); i++) {
        MediaPeriodImpl mediaPeriod = mediaPeriods.get(i);
        if (mediaPeriod.callback != null) {
          mediaPeriod.callback.onPrepared(mediaPeriod);
        }
      }
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
      if (loadingPeriod == null) {
        return;
      }
      checkNotNull(loadingPeriod.callback).onContinueLoadingRequested(loadingPeriod);
    }

    private long getStreamPositionUsWithNotYetStartedHandling(
        MediaPeriodImpl mediaPeriod, long positionUs) {
      if (positionUs < mediaPeriod.lastStartPositionUs) {
        long actualStartPositionUs =
            getStreamPositionUs(
                mediaPeriod.lastStartPositionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
        return actualStartPositionUs - (mediaPeriod.lastStartPositionUs - positionUs);
      }
      return getStreamPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
    }

    private long getMediaPeriodPositionUsWithEndOfSourceHandling(
        MediaPeriodImpl mediaPeriod, long positionUs) {
      if (positionUs == C.TIME_END_OF_SOURCE) {
        return C.TIME_END_OF_SOURCE;
      }
      long mediaPeriodPositionUs =
          getMediaPeriodPositionUs(positionUs, mediaPeriod.mediaPeriodId, adPlaybackState);
      long endPositionUs = getMediaPeriodEndPositionUs(mediaPeriod, adPlaybackState);
      return mediaPeriodPositionUs >= endPositionUs ? C.TIME_END_OF_SOURCE : mediaPeriodPositionUs;
    }

    private int findMatchingStreamIndex(MediaLoadData mediaLoadData) {
      if (mediaLoadData.trackFormat == null) {
        return C.INDEX_UNSET;
      }
      for (int i = 0; i < trackSelections.length; i++) {
        if (trackSelections[i] != null) {
          TrackGroup trackGroup = trackSelections[i].getTrackGroup();
          // Muxed primary track group should be the first in the list. We need to match Formats on
          // their id only as the muxed format and the format in the track group won't match.
          boolean isPrimaryTrackGroup =
              mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT
                  && trackGroup.equals(getTrackGroups().get(0));
          for (int j = 0; j < trackGroup.length; j++) {
            Format format = trackGroup.getFormat(j);
            if (format.equals(mediaLoadData.trackFormat)
                || (isPrimaryTrackGroup
                    && format.id != null
                    && format.id.equals(mediaLoadData.trackFormat.id))) {
              return i;
            }
          }
        }
      }
      return C.INDEX_UNSET;
    }

    private void maybeNotifyDownstreamFormatChanged(MediaPeriodImpl mediaPeriod, int streamIndex) {
      if (!mediaPeriod.hasNotifiedDownstreamFormatChange[streamIndex]
          && lastDownstreamFormatChangeData[streamIndex] != null) {
        mediaPeriod.hasNotifiedDownstreamFormatChange[streamIndex] = true;
        mediaPeriod.mediaSourceEventDispatcher.downstreamFormatChanged(
            correctMediaLoadData(
                mediaPeriod, lastDownstreamFormatChangeData[streamIndex], adPlaybackState));
      }
    }
  }

  private static final class ServerSideInsertedAdsTimeline extends ForwardingTimeline {

    private final AdPlaybackState adPlaybackState;

    public ServerSideInsertedAdsTimeline(
        Timeline contentTimeline, AdPlaybackState adPlaybackState) {
      super(contentTimeline);
      Assertions.checkState(contentTimeline.getPeriodCount() == 1);
      Assertions.checkState(contentTimeline.getWindowCount() == 1);
      this.adPlaybackState = adPlaybackState;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      super.getWindow(windowIndex, window, defaultPositionProjectionUs);
      long positionInPeriodUs =
          getMediaPeriodPositionUsForContent(
              window.positionInFirstPeriodUs,
              /* nextAdGroupIndex= */ C.INDEX_UNSET,
              adPlaybackState);
      if (window.durationUs == C.TIME_UNSET) {
        if (adPlaybackState.contentDurationUs != C.TIME_UNSET) {
          window.durationUs = adPlaybackState.contentDurationUs - positionInPeriodUs;
        }
      } else {
        long actualWindowEndPositionInPeriodUs = window.positionInFirstPeriodUs + window.durationUs;
        long windowEndPositionInPeriodUs =
            getMediaPeriodPositionUsForContent(
                actualWindowEndPositionInPeriodUs,
                /* nextAdGroupIndex= */ C.INDEX_UNSET,
                adPlaybackState);
        window.durationUs = windowEndPositionInPeriodUs - positionInPeriodUs;
      }
      window.positionInFirstPeriodUs = positionInPeriodUs;
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      super.getPeriod(periodIndex, period, setIds);
      long durationUs = period.durationUs;
      if (durationUs == C.TIME_UNSET) {
        durationUs = adPlaybackState.contentDurationUs;
      } else {
        durationUs =
            getMediaPeriodPositionUsForContent(
                durationUs, /* nextAdGroupIndex= */ C.INDEX_UNSET, adPlaybackState);
      }
      long positionInWindowUs =
          -getMediaPeriodPositionUsForContent(
              -period.getPositionInWindowUs(),
              /* nextAdGroupIndex= */ C.INDEX_UNSET,
              adPlaybackState);
      period.set(
          period.id,
          period.uid,
          period.windowIndex,
          durationUs,
          positionInWindowUs,
          adPlaybackState,
          period.isPlaceholder);
      return period;
    }
  }

  private static final class MediaPeriodImpl implements MediaPeriod {

    public final SharedMediaPeriod sharedPeriod;
    public final MediaPeriodId mediaPeriodId;
    public final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
    public final DrmSessionEventListener.EventDispatcher drmEventDispatcher;

    public @MonotonicNonNull Callback callback;
    public long lastStartPositionUs;
    public boolean[] hasNotifiedDownstreamFormatChange;

    public MediaPeriodImpl(
        SharedMediaPeriod sharedPeriod,
        MediaPeriodId mediaPeriodId,
        MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
        DrmSessionEventListener.EventDispatcher drmEventDispatcher) {
      this.sharedPeriod = sharedPeriod;
      this.mediaPeriodId = mediaPeriodId;
      this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
      this.drmEventDispatcher = drmEventDispatcher;
      hasNotifiedDownstreamFormatChange = new boolean[0];
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
      this.callback = callback;
      sharedPeriod.prepare(/* mediaPeriod= */ this, positionUs);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      sharedPeriod.maybeThrowPrepareError();
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      return sharedPeriod.getTrackGroups();
    }

    @Override
    public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
      return sharedPeriod.getStreamKeys(trackSelections);
    }

    @Override
    public long selectTracks(
        @NullableType ExoTrackSelection[] selections,
        boolean[] mayRetainStreamFlags,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long positionUs) {
      if (hasNotifiedDownstreamFormatChange.length == 0) {
        hasNotifiedDownstreamFormatChange = new boolean[streams.length];
      }
      return sharedPeriod.selectTracks(
          /* mediaPeriod= */ this,
          selections,
          mayRetainStreamFlags,
          streams,
          streamResetFlags,
          positionUs);
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {
      sharedPeriod.discardBuffer(/* mediaPeriod= */ this, positionUs, toKeyframe);
    }

    @Override
    public long readDiscontinuity() {
      return sharedPeriod.readDiscontinuity(/* mediaPeriod= */ this);
    }

    @Override
    public long seekToUs(long positionUs) {
      return sharedPeriod.seekToUs(/* mediaPeriod= */ this, positionUs);
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
      return sharedPeriod.getAdjustedSeekPositionUs(
          /* mediaPeriod= */ this, positionUs, seekParameters);
    }

    @Override
    public long getBufferedPositionUs() {
      return sharedPeriod.getBufferedPositionUs(/* mediaPeriod= */ this);
    }

    @Override
    public long getNextLoadPositionUs() {
      return sharedPeriod.getNextLoadPositionUs(/* mediaPeriod= */ this);
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return sharedPeriod.continueLoading(/* mediaPeriod= */ this, positionUs);
    }

    @Override
    public boolean isLoading() {
      return sharedPeriod.isLoading(/* mediaPeriod= */ this);
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
      sharedPeriod.reevaluateBuffer(/* mediaPeriod= */ this, positionUs);
    }
  }

  private static final class SampleStreamImpl implements SampleStream {

    private final MediaPeriodImpl mediaPeriod;
    private final int streamIndex;

    public SampleStreamImpl(MediaPeriodImpl mediaPeriod, int streamIndex) {
      this.mediaPeriod = mediaPeriod;
      this.streamIndex = streamIndex;
    }

    @Override
    public boolean isReady() {
      return mediaPeriod.sharedPeriod.isReady(streamIndex);
    }

    @Override
    public void maybeThrowError() throws IOException {
      mediaPeriod.sharedPeriod.maybeThrowError(streamIndex);
    }

    @Override
    @ReadDataResult
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return mediaPeriod.sharedPeriod.readData(
          mediaPeriod, streamIndex, formatHolder, buffer, readFlags);
    }

    @Override
    public int skipData(long positionUs) {
      return mediaPeriod.sharedPeriod.skipData(mediaPeriod, streamIndex, positionUs);
    }
  }
}
