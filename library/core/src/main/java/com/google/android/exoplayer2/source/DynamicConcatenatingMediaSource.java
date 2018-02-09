/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource.MediaSourceHolder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. Access to this class is thread-safe.
 */
public final class DynamicConcatenatingMediaSource extends CompositeMediaSource<MediaSourceHolder>
    implements PlayerMessage.Target {

  private static final int MSG_ADD = 0;
  private static final int MSG_ADD_MULTIPLE = 1;
  private static final int MSG_REMOVE = 2;
  private static final int MSG_MOVE = 3;
  private static final int MSG_ON_COMPLETION = 4;

  // Accessed on the app thread.
  private final List<MediaSource> mediaSourcesPublic;

  // Accessed on the playback thread.
  private final List<MediaSourceHolder> mediaSourceHolders;
  private final MediaSourceHolder query;
  private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final List<DeferredMediaPeriod> deferredMediaPeriods;
  private final boolean isAtomic;

  private ExoPlayer player;
  private Listener listener;
  private ShuffleOrder shuffleOrder;
  private boolean preventListenerNotification;
  private int windowCount;
  private int periodCount;

  /**
   * Creates a new dynamic concatenating media source.
   */
  public DynamicConcatenatingMediaSource() {
    this(/* isAtomic= */ false, new DefaultShuffleOrder(0));
  }

  /**
   * Creates a new dynamic concatenating media source.
   *
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   */
  public DynamicConcatenatingMediaSource(boolean isAtomic) {
    this(isAtomic, new DefaultShuffleOrder(0));
  }

  /**
   * Creates a new dynamic concatenating media source with a custom shuffle order.
   *
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   *     This shuffle order must be empty.
   */
  public DynamicConcatenatingMediaSource(boolean isAtomic, ShuffleOrder shuffleOrder) {
    this.shuffleOrder = shuffleOrder;
    this.mediaSourceByMediaPeriod = new IdentityHashMap<>();
    this.mediaSourcesPublic = new ArrayList<>();
    this.mediaSourceHolders = new ArrayList<>();
    this.deferredMediaPeriods = new ArrayList<>(1);
    this.query = new MediaSourceHolder(null, null, -1, -1, -1);
    this.isAtomic = isAtomic;
  }

  /**
   * Appends a {@link MediaSource} to the playlist.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public synchronized void addMediaSource(MediaSource mediaSource) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, null);
  }

  /**
   * Appends a {@link MediaSource} to the playlist and executes a custom action on completion.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public synchronized void addMediaSource(MediaSource mediaSource,
      @Nullable Runnable actionOnCompletion) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, actionOnCompletion);
  }

  /**
   * Adds a {@link MediaSource} to the playlist.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public synchronized void addMediaSource(int index, MediaSource mediaSource) {
    addMediaSource(index, mediaSource, null);
  }

  /**
   * Adds a {@link MediaSource} to the playlist and executes a custom action on completion.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public synchronized void addMediaSource(int index, MediaSource mediaSource,
      @Nullable Runnable actionOnCompletion) {
    Assertions.checkNotNull(mediaSource);
    Assertions.checkArgument(!mediaSourcesPublic.contains(mediaSource));
    mediaSourcesPublic.add(index, mediaSource);
    if (player != null) {
      player
          .createMessage(this)
          .setType(MSG_ADD)
          .setPayload(new MessageData<>(index, mediaSource, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public synchronized void addMediaSources(Collection<MediaSource> mediaSources) {
    addMediaSources(mediaSourcesPublic.size(), mediaSources, null);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist and executes a custom action on
   * completion.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public synchronized void addMediaSources(Collection<MediaSource> mediaSources,
      @Nullable Runnable actionOnCompletion) {
    addMediaSources(mediaSourcesPublic.size(), mediaSources, actionOnCompletion);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources) {
    addMediaSources(index, mediaSources, null);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist and executes a custom action on completion.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used. If you want to add the same
   * piece of media multiple times, use a new instance each time.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources,
      @Nullable Runnable actionOnCompletion) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
      Assertions.checkArgument(!mediaSourcesPublic.contains(mediaSource));
    }
    mediaSourcesPublic.addAll(index, mediaSources);
    if (player != null && !mediaSources.isEmpty()) {
      player
          .createMessage(this)
          .setType(MSG_ADD_MULTIPLE)
          .setPayload(new MessageData<>(index, mediaSources, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null){
      actionOnCompletion.run();
    }
  }

  /**
   * Removes a {@link MediaSource} from the playlist.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used, and so the instance being
   * removed should not be re-added. If you want to move the instance use
   * {@link #moveMediaSource(int, int)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public synchronized void removeMediaSource(int index) {
    removeMediaSource(index, null);
  }

  /**
   * Removes a {@link MediaSource} from the playlist and executes a custom action on completion.
   * <p>
   * Note: {@link MediaSource} instances are not designed to be re-used, and so the instance being
   * removed should not be re-added. If you want to move the instance use
   * {@link #moveMediaSource(int, int)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been removed from the playlist.
   */
  public synchronized void removeMediaSource(int index, @Nullable Runnable actionOnCompletion) {
    mediaSourcesPublic.remove(index);
    if (player != null) {
      player
          .createMessage(this)
          .setType(MSG_REMOVE)
          .setPayload(new MessageData<>(index, null, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public synchronized void moveMediaSource(int currentIndex, int newIndex) {
    moveMediaSource(currentIndex, newIndex, null);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist and executes a custom action on
   * completion.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param actionOnCompletion A {@link Runnable} which is executed immediately after the media
   *     source has been moved.
   */
  public synchronized void moveMediaSource(int currentIndex, int newIndex,
      @Nullable Runnable actionOnCompletion) {
    if (currentIndex == newIndex) {
      return;
    }
    mediaSourcesPublic.add(newIndex, mediaSourcesPublic.remove(currentIndex));
    if (player != null) {
      player
          .createMessage(this)
          .setType(MSG_MOVE)
          .setPayload(new MessageData<>(currentIndex, newIndex, actionOnCompletion))
          .send();
    } else if (actionOnCompletion != null) {
      actionOnCompletion.run();
    }
  }

  /**
   * Returns the number of media sources in the playlist.
   */
  public synchronized int getSize() {
    return mediaSourcesPublic.size();
  }

  /**
   * Returns the {@link MediaSource} at a specified index.
   *
   * @param index An index in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @return The {@link MediaSource} at this index.
   */
  public synchronized MediaSource getMediaSource(int index) {
    return mediaSourcesPublic.get(index);
  }

  @Override
  public synchronized void prepareSource(ExoPlayer player, boolean isTopLevelSource,
      Listener listener) {
    super.prepareSource(player, isTopLevelSource, listener);
    this.player = player;
    this.listener = listener;
    preventListenerNotification = true;
    shuffleOrder = shuffleOrder.cloneAndInsert(0, mediaSourcesPublic.size());
    addMediaSourcesInternal(0, mediaSourcesPublic);
    preventListenerNotification = false;
    maybeNotifyListener(null);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    int mediaSourceHolderIndex = findMediaSourceHolderByPeriodIndex(id.periodIndex);
    MediaSourceHolder holder = mediaSourceHolders.get(mediaSourceHolderIndex);
    MediaPeriodId idInSource = id.copyWithPeriodIndex(
        id.periodIndex - holder.firstPeriodIndexInChild);
    MediaPeriod mediaPeriod;
    if (!holder.isPrepared) {
      mediaPeriod = new DeferredMediaPeriod(holder.mediaSource, idInSource, allocator);
      deferredMediaPeriods.add((DeferredMediaPeriod) mediaPeriod);
    } else {
      mediaPeriod = holder.mediaSource.createPeriod(idInSource, allocator);
    }
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    holder.activeMediaPeriods++;
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder = mediaSourceByMediaPeriod.remove(mediaPeriod);
    if (mediaPeriod instanceof DeferredMediaPeriod) {
      deferredMediaPeriods.remove(mediaPeriod);
      ((DeferredMediaPeriod) mediaPeriod).releasePeriod();
    } else {
      holder.mediaSource.releasePeriod(mediaPeriod);
    }
    holder.activeMediaPeriods--;
    if (holder.activeMediaPeriods == 0 && holder.isRemoved) {
      releaseChildSource(holder);
    }
  }

  @Override
  public void releaseSource() {
    super.releaseSource();
    mediaSourceHolders.clear();
    player = null;
    listener = null;
    shuffleOrder = shuffleOrder.cloneAndClear();
    windowCount = 0;
    periodCount = 0;
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      MediaSourceHolder mediaSourceHolder,
      MediaSource mediaSource,
      Timeline timeline,
      @Nullable Object manifest) {
    updateMediaSourceInternal(mediaSourceHolder, timeline);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
    if (messageType == MSG_ON_COMPLETION) {
      ((EventDispatcher) message).dispatchEvent();
      return;
    }
    preventListenerNotification = true;
    EventDispatcher actionOnCompletion;
    switch (messageType) {
      case MSG_ADD: {
        MessageData<MediaSource> messageData = (MessageData<MediaSource>) message;
        shuffleOrder = shuffleOrder.cloneAndInsert(messageData.index, 1);
        addMediaSourceInternal(messageData.index, messageData.customData);
        actionOnCompletion = messageData.actionOnCompletion;
        break;
      }
      case MSG_ADD_MULTIPLE: {
        MessageData<Collection<MediaSource>> messageData =
            (MessageData<Collection<MediaSource>>) message;
        shuffleOrder = shuffleOrder.cloneAndInsert(messageData.index,
            messageData.customData.size());
        addMediaSourcesInternal(messageData.index, messageData.customData);
        actionOnCompletion = messageData.actionOnCompletion;
        break;
      }
      case MSG_REMOVE: {
        MessageData<Void> messageData = (MessageData<Void>) message;
        shuffleOrder = shuffleOrder.cloneAndRemove(messageData.index);
        removeMediaSourceInternal(messageData.index);
        actionOnCompletion = messageData.actionOnCompletion;
        break;
      }
      case MSG_MOVE: {
        MessageData<Integer> messageData = (MessageData<Integer>) message;
        shuffleOrder = shuffleOrder.cloneAndRemove(messageData.index);
        shuffleOrder = shuffleOrder.cloneAndInsert(messageData.customData, 1);
        moveMediaSourceInternal(messageData.index, messageData.customData);
        actionOnCompletion = messageData.actionOnCompletion;
        break;
      }
      default: {
        throw new IllegalStateException();
      }
    }
    preventListenerNotification = false;
    maybeNotifyListener(actionOnCompletion);
  }

  private void maybeNotifyListener(@Nullable EventDispatcher actionOnCompletion) {
    if (!preventListenerNotification) {
      listener.onSourceInfoRefreshed(
          this,
          new ConcatenatedTimeline(
              mediaSourceHolders, windowCount, periodCount, shuffleOrder, isAtomic),
          null);
      if (actionOnCompletion != null) {
        player.createMessage(this).setType(MSG_ON_COMPLETION).setPayload(actionOnCompletion).send();
      }
    }
  }

  private void addMediaSourceInternal(int newIndex, MediaSource newMediaSource) {
    final MediaSourceHolder newMediaSourceHolder;
    DeferredTimeline newTimeline = new DeferredTimeline();
    if (newIndex > 0) {
      MediaSourceHolder previousHolder = mediaSourceHolders.get(newIndex - 1);
      newMediaSourceHolder =
          new MediaSourceHolder(
              newMediaSource,
              newTimeline,
              newIndex,
              previousHolder.firstWindowIndexInChild + previousHolder.timeline.getWindowCount(),
              previousHolder.firstPeriodIndexInChild + previousHolder.timeline.getPeriodCount());
    } else {
      newMediaSourceHolder = new MediaSourceHolder(newMediaSource, newTimeline, 0, 0, 0);
    }
    correctOffsets(
        newIndex,
        /* childIndexUpdate= */ 1,
        newTimeline.getWindowCount(),
        newTimeline.getPeriodCount());
    mediaSourceHolders.add(newIndex, newMediaSourceHolder);
    prepareChildSource(newMediaSourceHolder, newMediaSourceHolder.mediaSource);
  }

  private void addMediaSourcesInternal(int index, Collection<MediaSource> mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      addMediaSourceInternal(index++, mediaSource);
    }
  }

  private void updateMediaSourceInternal(MediaSourceHolder mediaSourceHolder, Timeline timeline) {
    if (mediaSourceHolder == null) {
      throw new IllegalArgumentException();
    }
    DeferredTimeline deferredTimeline = mediaSourceHolder.timeline;
    if (deferredTimeline.getTimeline() == timeline) {
      return;
    }
    int windowOffsetUpdate = timeline.getWindowCount() - deferredTimeline.getWindowCount();
    int periodOffsetUpdate = timeline.getPeriodCount() - deferredTimeline.getPeriodCount();
    if (windowOffsetUpdate != 0 || periodOffsetUpdate != 0) {
      correctOffsets(
          mediaSourceHolder.childIndex + 1,
          /* childIndexUpdate= */ 0,
          windowOffsetUpdate,
          periodOffsetUpdate);
    }
    mediaSourceHolder.timeline = deferredTimeline.cloneWithNewTimeline(timeline);
    if (!mediaSourceHolder.isPrepared) {
      for (int i = deferredMediaPeriods.size() - 1; i >= 0; i--) {
        if (deferredMediaPeriods.get(i).mediaSource == mediaSourceHolder.mediaSource) {
          deferredMediaPeriods.get(i).createPeriod();
          deferredMediaPeriods.remove(i);
        }
      }
    }
    mediaSourceHolder.isPrepared = true;
    maybeNotifyListener(null);
  }

  private void removeMediaSourceInternal(int index) {
    MediaSourceHolder holder = mediaSourceHolders.get(index);
    mediaSourceHolders.remove(index);
    Timeline oldTimeline = holder.timeline;
    correctOffsets(
        index,
        /* childIndexUpdate= */ -1,
        -oldTimeline.getWindowCount(),
        -oldTimeline.getPeriodCount());
    holder.isRemoved = true;
    if (holder.activeMediaPeriods == 0) {
      releaseChildSource(holder);
    }
  }

  private void moveMediaSourceInternal(int currentIndex, int newIndex) {
    int startIndex = Math.min(currentIndex, newIndex);
    int endIndex = Math.max(currentIndex, newIndex);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    int periodOffset = mediaSourceHolders.get(startIndex).firstPeriodIndexInChild;
    mediaSourceHolders.add(newIndex, mediaSourceHolders.remove(currentIndex));
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.firstWindowIndexInChild = windowOffset;
      holder.firstPeriodIndexInChild = periodOffset;
      windowOffset += holder.timeline.getWindowCount();
      periodOffset += holder.timeline.getPeriodCount();
    }
  }

  private void correctOffsets(
      int startIndex, int childIndexUpdate, int windowOffsetUpdate, int periodOffsetUpdate) {
    windowCount += windowOffsetUpdate;
    periodCount += periodOffsetUpdate;
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      mediaSourceHolders.get(i).childIndex += childIndexUpdate;
      mediaSourceHolders.get(i).firstWindowIndexInChild += windowOffsetUpdate;
      mediaSourceHolders.get(i).firstPeriodIndexInChild += periodOffsetUpdate;
    }
  }

  private int findMediaSourceHolderByPeriodIndex(int periodIndex) {
    query.firstPeriodIndexInChild = periodIndex;
    int index = Collections.binarySearch(mediaSourceHolders, query);
    if (index < 0) {
      return -index - 2;
    }
    while (index < mediaSourceHolders.size() - 1
        && mediaSourceHolders.get(index + 1).firstPeriodIndexInChild == periodIndex) {
      index++;
    }
    return index;
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder implements Comparable<MediaSourceHolder> {

    public final MediaSource mediaSource;
    public final int uid;

    public DeferredTimeline timeline;
    public int childIndex;
    public int firstWindowIndexInChild;
    public int firstPeriodIndexInChild;
    public boolean isPrepared;
    public boolean isRemoved;
    public int activeMediaPeriods;

    public MediaSourceHolder(
        MediaSource mediaSource,
        DeferredTimeline timeline,
        int childIndex,
        int window,
        int period) {
      this.mediaSource = mediaSource;
      this.timeline = timeline;
      this.childIndex = childIndex;
      this.firstWindowIndexInChild = window;
      this.firstPeriodIndexInChild = period;
      this.uid = System.identityHashCode(this);
    }

    @Override
    public int compareTo(@NonNull MediaSourceHolder other) {
      return this.firstPeriodIndexInChild - other.firstPeriodIndexInChild;
    }
  }

  /**
   * Can be used to dispatch a runnable on the thread the object was created on.
   */
  private static final class EventDispatcher {

    public final Handler eventHandler;
    public final Runnable runnable;

    public EventDispatcher(Runnable runnable) {
      this.runnable = runnable;
      this.eventHandler = new Handler(Looper.myLooper() != null ? Looper.myLooper()
          : Looper.getMainLooper());
    }

    public void dispatchEvent() {
      eventHandler.post(runnable);
    }

  }

  /** Message used to post actions from app thread to playback thread. */
  private static final class MessageData<T> {

    public final int index;
    public final T customData;
    public final @Nullable EventDispatcher actionOnCompletion;

    public MessageData(int index, T customData, @Nullable Runnable actionOnCompletion) {
      this.index = index;
      this.actionOnCompletion = actionOnCompletion != null
          ? new EventDispatcher(actionOnCompletion) : null;
      this.customData = customData;
    }

  }

  /**
   * Timeline exposing concatenated timelines of playlist media sources.
   */
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final int windowCount;
    private final int periodCount;
    private final int[] firstPeriodInChildIndices;
    private final int[] firstWindowInChildIndices;
    private final Timeline[] timelines;
    private final int[] uids;
    private final SparseIntArray childIndexByUid;

    public ConcatenatedTimeline(
        Collection<MediaSourceHolder> mediaSourceHolders,
        int windowCount,
        int periodCount,
        ShuffleOrder shuffleOrder,
        boolean isAtomic) {
      super(isAtomic, shuffleOrder);
      this.windowCount = windowCount;
      this.periodCount = periodCount;
      int childCount = mediaSourceHolders.size();
      firstPeriodInChildIndices = new int[childCount];
      firstWindowInChildIndices = new int[childCount];
      timelines = new Timeline[childCount];
      uids = new int[childCount];
      childIndexByUid = new SparseIntArray();
      int index = 0;
      for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
        timelines[index] = mediaSourceHolder.timeline;
        firstPeriodInChildIndices[index] = mediaSourceHolder.firstPeriodIndexInChild;
        firstWindowInChildIndices[index] = mediaSourceHolder.firstWindowIndexInChild;
        uids[index] = mediaSourceHolder.uid;
        childIndexByUid.put(uids[index], index++);
      }
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
      if (!(childUid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int index = childIndexByUid.get((int) childUid, -1);
      return index == -1 ? C.INDEX_UNSET : index;
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

  /**
   * Timeline used as placeholder for an unprepared media source. After preparation, a copy of the
   * DeferredTimeline is used to keep the originally assigned first period ID.
   */
  private static final class DeferredTimeline extends ForwardingTimeline {

    private static final Object DUMMY_ID = new Object();
    private static final Period period = new Period();
    private static final DummyTimeline dummyTimeline = new DummyTimeline();

    private final Object replacedId;

    public DeferredTimeline() {
      this(dummyTimeline, /* replacedId= */ null);
    }

    private DeferredTimeline(Timeline timeline, Object replacedId) {
      super(timeline);
      this.replacedId = replacedId;
    }

    public DeferredTimeline cloneWithNewTimeline(Timeline timeline) {
      return new DeferredTimeline(
          timeline,
          replacedId == null && timeline.getPeriodCount() > 0
              ? timeline.getPeriod(0, period, true).uid
              : replacedId);
    }

    public Timeline getTimeline() {
      return timeline;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(periodIndex, period, setIds);
      if (Util.areEqual(period.uid, replacedId)) {
        period.uid = DUMMY_ID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(DUMMY_ID.equals(uid) ? replacedId : uid);
    }
  }

  /** Dummy placeholder timeline with one dynamic window with a period of indeterminate duration. */
  private static final class DummyTimeline extends Timeline {

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      // Dynamic window to indicate pending timeline updates.
      return window.set(
          /* id= */ null,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* isSeekable= */ false,
          /* isDynamic= */ true,
          /* defaultPositionUs= */ 0,
          /* durationUs= */ C.TIME_UNSET,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return period.set(
          /* id= */ null,
          /* uid= */ null,
          /* windowIndex= */ 0,
          /* durationUs = */ C.TIME_UNSET,
          /* positionInWindowUs= */ C.TIME_UNSET);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return uid == null ? 0 : C.INDEX_UNSET;
    }
  }
}

