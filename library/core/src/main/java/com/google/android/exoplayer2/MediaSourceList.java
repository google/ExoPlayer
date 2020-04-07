/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MaskingMediaPeriod;
import com.google.android.exoplayer2.source.MaskingMediaSource;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. It is valid for the same {@link MediaSource} instance to be present more than
 * once in the playlist.
 *
 * <p>With the exception of the constructor, all methods are called on the playback thread.
 */
/* package */ class MediaSourceList {

  /** Listener for source events. */
  public interface MediaSourceListInfoRefreshListener {

    /**
     * Called when the timeline of a media item has changed and a new timeline that reflects the
     * current playlist state needs to be created by calling {@link #createTimeline()}.
     *
     * <p>Called on the playback thread.
     */
    void onPlaylistUpdateRequested();
  }

  private final List<MediaSourceHolder> mediaSourceHolders;
  private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final Map<Object, MediaSourceHolder> mediaSourceByUid;
  private final MediaSourceListInfoRefreshListener mediaSourceListInfoListener;
  private final MediaSourceEventListener.EventDispatcher eventDispatcher;
  private final HashMap<MediaSourceList.MediaSourceHolder, MediaSourceAndListener> childSources;
  private final Set<MediaSourceHolder> enabledMediaSourceHolders;

  private ShuffleOrder shuffleOrder;
  private boolean isPrepared;

  @Nullable private TransferListener mediaTransferListener;

  @SuppressWarnings("initialization")
  public MediaSourceList(MediaSourceListInfoRefreshListener listener) {
    mediaSourceListInfoListener = listener;
    shuffleOrder = new DefaultShuffleOrder(0);
    mediaSourceByMediaPeriod = new IdentityHashMap<>();
    mediaSourceByUid = new HashMap<>();
    mediaSourceHolders = new ArrayList<>();
    eventDispatcher = new MediaSourceEventListener.EventDispatcher();
    childSources = new HashMap<>();
    enabledMediaSourceHolders = new HashSet<>();
  }

  /**
   * Sets the media sources replacing any sources previously contained in the playlist.
   *
   * @param holders The list of {@link MediaSourceHolder}s to set.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   */
  public final Timeline setMediaSources(
      List<MediaSourceHolder> holders, ShuffleOrder shuffleOrder) {
    removeMediaSourcesInternal(/* fromIndex= */ 0, /* toIndex= */ mediaSourceHolders.size());
    return addMediaSources(/* index= */ this.mediaSourceHolders.size(), holders, shuffleOrder);
  }

  /**
   * Adds multiple {@link MediaSourceHolder}s to the playlist.
   *
   * @param index The index at which the new {@link MediaSourceHolder}s will be inserted. This index
   *     must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param holders A list of {@link MediaSourceHolder}s to be added.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   */
  public final Timeline addMediaSources(
      int index, List<MediaSourceHolder> holders, ShuffleOrder shuffleOrder) {
    if (!holders.isEmpty()) {
      this.shuffleOrder = shuffleOrder;
      for (int insertionIndex = index; insertionIndex < index + holders.size(); insertionIndex++) {
        MediaSourceHolder holder = holders.get(insertionIndex - index);
        if (insertionIndex > 0) {
          MediaSourceHolder previousHolder = mediaSourceHolders.get(insertionIndex - 1);
          Timeline previousTimeline = previousHolder.mediaSource.getTimeline();
          holder.reset(
              /* firstWindowIndexInChild= */ previousHolder.firstWindowIndexInChild
                  + previousTimeline.getWindowCount());
        } else {
          holder.reset(/* firstWindowIndexInChild= */ 0);
        }
        Timeline newTimeline = holder.mediaSource.getTimeline();
        correctOffsets(
            /* startIndex= */ insertionIndex,
            /* windowOffsetUpdate= */ newTimeline.getWindowCount());
        mediaSourceHolders.add(insertionIndex, holder);
        mediaSourceByUid.put(holder.uid, holder);
        if (isPrepared) {
          prepareChildSource(holder);
          if (mediaSourceByMediaPeriod.isEmpty()) {
            enabledMediaSourceHolders.add(holder);
          } else {
            disableChildSource(holder);
          }
        }
      }
    }
    return createTimeline();
  }

  /**
   * Removes a range of {@link MediaSourceHolder}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded).
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public final Timeline removeMediaSourceRange(
      int fromIndex, int toIndex, ShuffleOrder shuffleOrder) {
    Assertions.checkArgument(fromIndex >= 0 && fromIndex <= toIndex && toIndex <= getSize());
    this.shuffleOrder = shuffleOrder;
    removeMediaSourcesInternal(fromIndex, toIndex);
    return createTimeline();
  }

  /**
   * Moves an existing media source within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When an index is invalid, i.e. {@code currentIndex} &lt; 0,
   *     {@code currentIndex} &gt;= {@link #getSize()}, {@code newIndex} &lt; 0
   */
  public final Timeline moveMediaSource(int currentIndex, int newIndex, ShuffleOrder shuffleOrder) {
    return moveMediaSourceRange(currentIndex, currentIndex + 1, newIndex, shuffleOrder);
  }

  /**
   * Moves a range of media sources within the playlist.
   *
   * <p>Note: when specified range is empty or the from index equals the new from index, no actual
   * media source is moved and no exception is thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source of the range that
   *     will be moved. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be larger or equals than {@code fromIndex}.
   * @param newFromIndex The target index of the first media source of the range that will be moved.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &lt; {@code fromIndex}, {@code fromIndex} &gt; {@code toIndex}, {@code
   *     newFromIndex} &lt; 0
   */
  public Timeline moveMediaSourceRange(
      int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
    Assertions.checkArgument(
        fromIndex >= 0 && fromIndex <= toIndex && toIndex <= getSize() && newFromIndex >= 0);
    this.shuffleOrder = shuffleOrder;
    if (fromIndex == toIndex || fromIndex == newFromIndex) {
      return createTimeline();
    }
    int startIndex = Math.min(fromIndex, newFromIndex);
    int newEndIndex = newFromIndex + (toIndex - fromIndex) - 1;
    int endIndex = Math.max(newEndIndex, toIndex - 1);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    moveMediaSourceHolders(mediaSourceHolders, fromIndex, toIndex, newFromIndex);
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.firstWindowIndexInChild = windowOffset;
      windowOffset += holder.mediaSource.getTimeline().getWindowCount();
    }
    return createTimeline();
  }

  /** Clears the playlist. */
  public final Timeline clear(@Nullable ShuffleOrder shuffleOrder) {
    this.shuffleOrder = shuffleOrder != null ? shuffleOrder : this.shuffleOrder.cloneAndClear();
    removeMediaSourcesInternal(/* fromIndex= */ 0, /* toIndex= */ getSize());
    return createTimeline();
  }

  /** Whether the playlist is prepared. */
  public final boolean isPrepared() {
    return isPrepared;
  }

  /** Returns the number of media sources in the playlist. */
  public final int getSize() {
    return mediaSourceHolders.size();
  }

  /**
   * Sets the {@link AnalyticsCollector}.
   *
   * @param handler The handler on which to call the collector.
   * @param analyticsCollector The analytics collector.
   */
  public final void setAnalyticsCollector(Handler handler, AnalyticsCollector analyticsCollector) {
    eventDispatcher.addEventListener(handler, analyticsCollector, MediaSourceEventListener.class);
    eventDispatcher.addEventListener(handler, analyticsCollector, DrmSessionEventListener.class);
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   */
  public final Timeline setShuffleOrder(ShuffleOrder shuffleOrder) {
    int size = getSize();
    if (shuffleOrder.getLength() != size) {
      shuffleOrder =
          shuffleOrder
              .cloneAndClear()
              .cloneAndInsert(/* insertionIndex= */ 0, /* insertionCount= */ size);
    }
    this.shuffleOrder = shuffleOrder;
    return createTimeline();
  }

  /** Prepares the playlist. */
  public final void prepare(@Nullable TransferListener mediaTransferListener) {
    Assertions.checkState(!isPrepared);
    this.mediaTransferListener = mediaTransferListener;
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      prepareChildSource(mediaSourceHolder);
      enabledMediaSourceHolders.add(mediaSourceHolder);
    }
    isPrepared = true;
  }

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param startPositionUs The expected start position, in microseconds.
   * @return A new {@link MediaPeriod}.
   */
  public MediaPeriod createPeriod(
      MediaSource.MediaPeriodId id, Allocator allocator, long startPositionUs) {
    Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
    MediaSource.MediaPeriodId childMediaPeriodId =
        id.copyWithPeriodUid(getChildPeriodUid(id.periodUid));
    MediaSourceHolder holder = Assertions.checkNotNull(mediaSourceByUid.get(mediaSourceHolderUid));
    enableMediaSource(holder);
    holder.activeMediaPeriodIds.add(childMediaPeriodId);
    MediaPeriod mediaPeriod =
        holder.mediaSource.createPeriod(childMediaPeriodId, allocator, startPositionUs);
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    disableUnusedMediaSources();
    return mediaPeriod;
  }

  /**
   * Releases the period.
   *
   * @param mediaPeriod The period to release.
   */
  public final void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder =
        Assertions.checkNotNull(mediaSourceByMediaPeriod.remove(mediaPeriod));
    holder.mediaSource.releasePeriod(mediaPeriod);
    holder.activeMediaPeriodIds.remove(((MaskingMediaPeriod) mediaPeriod).id);
    if (!mediaSourceByMediaPeriod.isEmpty()) {
      disableUnusedMediaSources();
    }
    maybeReleaseChildSource(holder);
  }

  /** Releases the playlist. */
  public final void release() {
    for (MediaSourceAndListener childSource : childSources.values()) {
      childSource.mediaSource.releaseSource(childSource.caller);
      childSource.mediaSource.removeEventListener(childSource.eventListener);
    }
    childSources.clear();
    enabledMediaSourceHolders.clear();
    isPrepared = false;
  }

  /** Throws any pending error encountered while loading or refreshing. */
  public final void maybeThrowSourceInfoRefreshError() throws IOException {
    for (MediaSourceAndListener childSource : childSources.values()) {
      childSource.mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  /** Creates a timeline reflecting the current state of the playlist. */
  public final Timeline createTimeline() {
    if (mediaSourceHolders.isEmpty()) {
      return Timeline.EMPTY;
    }
    int windowOffset = 0;
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      mediaSourceHolder.firstWindowIndexInChild = windowOffset;
      windowOffset += mediaSourceHolder.mediaSource.getTimeline().getWindowCount();
    }
    return new PlaylistTimeline(mediaSourceHolders, shuffleOrder);
  }

  // Internal methods.

  private void enableMediaSource(MediaSourceHolder mediaSourceHolder) {
    enabledMediaSourceHolders.add(mediaSourceHolder);
    @Nullable MediaSourceAndListener enabledChild = childSources.get(mediaSourceHolder);
    if (enabledChild != null) {
      enabledChild.mediaSource.enable(enabledChild.caller);
    }
  }

  private void disableUnusedMediaSources() {
    Iterator<MediaSourceHolder> iterator = enabledMediaSourceHolders.iterator();
    while (iterator.hasNext()) {
      MediaSourceHolder holder = iterator.next();
      if (holder.activeMediaPeriodIds.isEmpty()) {
        disableChildSource(holder);
        iterator.remove();
      }
    }
  }

  private void disableChildSource(MediaSourceHolder holder) {
    @Nullable MediaSourceAndListener disabledChild = childSources.get(holder);
    if (disabledChild != null) {
      disabledChild.mediaSource.disable(disabledChild.caller);
    }
  }

  private void removeMediaSourcesInternal(int fromIndex, int toIndex) {
    for (int index = toIndex - 1; index >= fromIndex; index--) {
      MediaSourceHolder holder = mediaSourceHolders.remove(index);
      mediaSourceByUid.remove(holder.uid);
      Timeline oldTimeline = holder.mediaSource.getTimeline();
      correctOffsets(
          /* startIndex= */ index, /* windowOffsetUpdate= */ -oldTimeline.getWindowCount());
      holder.isRemoved = true;
      if (isPrepared) {
        maybeReleaseChildSource(holder);
      }
    }
  }

  private void correctOffsets(int startIndex, int windowOffsetUpdate) {
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      mediaSourceHolder.firstWindowIndexInChild += windowOffsetUpdate;
    }
  }

  // Internal methods to manage child sources.

  @Nullable
  private static MediaSource.MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaSourceHolder mediaSourceHolder, MediaSource.MediaPeriodId mediaPeriodId) {
    for (int i = 0; i < mediaSourceHolder.activeMediaPeriodIds.size(); i++) {
      // Ensure the reported media period id has the same window sequence number as the one created
      // by this media source. Otherwise it does not belong to this child source.
      if (mediaSourceHolder.activeMediaPeriodIds.get(i).windowSequenceNumber
          == mediaPeriodId.windowSequenceNumber) {
        Object periodUid = getPeriodUid(mediaSourceHolder, mediaPeriodId.periodUid);
        return mediaPeriodId.copyWithPeriodUid(periodUid);
      }
    }
    return null;
  }

  private static int getWindowIndexForChildWindowIndex(
      MediaSourceHolder mediaSourceHolder, int windowIndex) {
    return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
  }

  private void prepareChildSource(MediaSourceHolder holder) {
    MediaSource mediaSource = holder.mediaSource;
    MediaSource.MediaSourceCaller caller =
        (source, timeline) -> mediaSourceListInfoListener.onPlaylistUpdateRequested();
    ForwardingEventListener eventListener = new ForwardingEventListener(holder);
    childSources.put(holder, new MediaSourceAndListener(mediaSource, caller, eventListener));
    mediaSource.addEventListener(Util.createHandler(), eventListener);
    mediaSource.addDrmEventListener(Util.createHandler(), eventListener);
    mediaSource.prepareSource(caller, mediaTransferListener);
  }

  private void maybeReleaseChildSource(MediaSourceHolder mediaSourceHolder) {
    // Release if the source has been removed from the playlist and no periods are still active.
    if (mediaSourceHolder.isRemoved && mediaSourceHolder.activeMediaPeriodIds.isEmpty()) {
      MediaSourceAndListener removedChild =
          Assertions.checkNotNull(childSources.remove(mediaSourceHolder));
      removedChild.mediaSource.releaseSource(removedChild.caller);
      removedChild.mediaSource.removeEventListener(removedChild.eventListener);
      enabledMediaSourceHolders.remove(mediaSourceHolder);
    }
  }

  /** Return uid of media source holder from period uid of concatenated source. */
  private static Object getMediaSourceHolderUid(Object periodUid) {
    return PlaylistTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
  }

  /** Return uid of child period from period uid of concatenated source. */
  private static Object getChildPeriodUid(Object periodUid) {
    return PlaylistTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
  }

  private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
    return PlaylistTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
  }

  /* package */ static void moveMediaSourceHolders(
      List<MediaSourceHolder> mediaSourceHolders, int fromIndex, int toIndex, int newFromIndex) {
    MediaSourceHolder[] removedItems = new MediaSourceHolder[toIndex - fromIndex];
    for (int i = removedItems.length - 1; i >= 0; i--) {
      removedItems[i] = mediaSourceHolders.remove(fromIndex + i);
    }
    mediaSourceHolders.addAll(
        Math.min(newFromIndex, mediaSourceHolders.size()), Arrays.asList(removedItems));
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder {

    public final MaskingMediaSource mediaSource;
    public final Object uid;
    public final List<MediaSource.MediaPeriodId> activeMediaPeriodIds;

    public int firstWindowIndexInChild;
    public boolean isRemoved;

    public MediaSourceHolder(MediaSource mediaSource, boolean useLazyPreparation) {
      this.mediaSource = new MaskingMediaSource(mediaSource, useLazyPreparation);
      this.activeMediaPeriodIds = new ArrayList<>();
      this.uid = new Object();
    }

    public void reset(int firstWindowIndexInChild) {
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.isRemoved = false;
      this.activeMediaPeriodIds.clear();
    }
  }

  /** Timeline exposing concatenated timelines of playlist media sources. */
  /* package */ static final class PlaylistTimeline extends AbstractConcatenatedTimeline {

    private final int windowCount;
    private final int periodCount;
    private final int[] firstPeriodInChildIndices;
    private final int[] firstWindowInChildIndices;
    private final Timeline[] timelines;
    private final Object[] uids;
    private final HashMap<Object, Integer> childIndexByUid;

    public PlaylistTimeline(
        Collection<MediaSourceHolder> mediaSourceHolders, ShuffleOrder shuffleOrder) {
      super(/* isAtomic= */ false, shuffleOrder);
      int childCount = mediaSourceHolders.size();
      firstPeriodInChildIndices = new int[childCount];
      firstWindowInChildIndices = new int[childCount];
      timelines = new Timeline[childCount];
      uids = new Object[childCount];
      childIndexByUid = new HashMap<>();
      int index = 0;
      int windowCount = 0;
      int periodCount = 0;
      for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
        timelines[index] = mediaSourceHolder.mediaSource.getTimeline();
        firstWindowInChildIndices[index] = windowCount;
        firstPeriodInChildIndices[index] = periodCount;
        windowCount += timelines[index].getWindowCount();
        periodCount += timelines[index].getPeriodCount();
        uids[index] = mediaSourceHolder.uid;
        childIndexByUid.put(uids[index], index++);
      }
      this.windowCount = windowCount;
      this.periodCount = periodCount;
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(firstPeriodInChildIndices, periodIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(firstWindowInChildIndices, windowIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      Integer index = childIndexByUid.get(childUid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return firstPeriodInChildIndices[childIndex];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return firstWindowInChildIndices[childIndex];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return uids[childIndex];
    }

    @Override
    public int getWindowCount() {
      return windowCount;
    }

    @Override
    public int getPeriodCount() {
      return periodCount;
    }
  }

  private static final class MediaSourceAndListener {

    public final MediaSource mediaSource;
    public final MediaSource.MediaSourceCaller caller;
    public final MediaSourceEventListener eventListener;

    public MediaSourceAndListener(
        MediaSource mediaSource,
        MediaSource.MediaSourceCaller caller,
        MediaSourceEventListener eventListener) {
      this.mediaSource = mediaSource;
      this.caller = caller;
      this.eventListener = eventListener;
    }
  }

  private final class ForwardingEventListener
      implements MediaSourceEventListener, DrmSessionEventListener {

    private final MediaSourceList.MediaSourceHolder id;
    private EventDispatcher eventDispatcher;

    public ForwardingEventListener(MediaSourceList.MediaSourceHolder id) {
      eventDispatcher = MediaSourceList.this.eventDispatcher;
      this.id = id;
    }

    // MediaSourceEventListener implementation

    @Override
    public void onMediaPeriodCreated(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.mediaPeriodCreated();
      }
    }

    @Override
    public void onMediaPeriodReleased(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.mediaPeriodReleased();
      }
    }

    @Override
    public void onLoadStarted(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadStarted(loadEventData, mediaLoadData);
      }
    }

    @Override
    public void onLoadCompleted(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadCompleted(loadEventData, mediaLoadData);
      }
    }

    @Override
    public void onLoadCanceled(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadCanceled(loadEventData, mediaLoadData);
      }
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.loadError(loadEventData, mediaLoadData, error, wasCanceled);
      }
    }

    @Override
    public void onReadingStarted(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.readingStarted();
      }
    }

    @Override
    public void onUpstreamDiscarded(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.upstreamDiscarded(mediaLoadData);
      }
    }

    @Override
    public void onDownstreamFormatChanged(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        MediaLoadData mediaLoadData) {
      if (maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
        eventDispatcher.downstreamFormatChanged(mediaLoadData);
      }
    }

    // DrmSessionEventListener implementation

    @Override
    public void onDrmSessionAcquired() {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmSessionAcquired(),
          DrmSessionEventListener.class);
    }

    @Override
    public void onDrmKeysLoaded() {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmKeysLoaded(),
          DrmSessionEventListener.class);
    }

    @Override
    public void onDrmSessionManagerError(Exception error) {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmSessionManagerError(error),
          DrmSessionEventListener.class);
    }

    @Override
    public void onDrmKeysRestored() {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmKeysRestored(),
          DrmSessionEventListener.class);
    }

    @Override
    public void onDrmKeysRemoved() {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmKeysRemoved(),
          DrmSessionEventListener.class);
    }

    @Override
    public void onDrmSessionReleased() {
      eventDispatcher.dispatch(
          (listener, windowIndex, mediaPeriodId) -> listener.onDrmSessionReleased(),
          DrmSessionEventListener.class);
    }

    /** Updates the event dispatcher and returns whether the event should be dispatched. */
    private boolean maybeUpdateEventDispatcher(
        int childWindowIndex, @Nullable MediaSource.MediaPeriodId childMediaPeriodId) {
      @Nullable MediaSource.MediaPeriodId mediaPeriodId = null;
      if (childMediaPeriodId != null) {
        mediaPeriodId = getMediaPeriodIdForChildMediaPeriodId(id, childMediaPeriodId);
        if (mediaPeriodId == null) {
          // Media period not found. Ignore event.
          return false;
        }
      }
      int windowIndex = getWindowIndexForChildWindowIndex(id, childWindowIndex);
      if (eventDispatcher.windowIndex != windowIndex
          || !Util.areEqual(eventDispatcher.mediaPeriodId, mediaPeriodId)) {
        eventDispatcher =
            MediaSourceList.this.eventDispatcher.withParameters(
                windowIndex, mediaPeriodId, /* mediaTimeOffsetMs= */ 0L);
      }
      return true;
    }
  }
}
