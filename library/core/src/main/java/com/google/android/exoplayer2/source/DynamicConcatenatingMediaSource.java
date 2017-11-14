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
import com.google.android.exoplayer2.ExoPlayer.ExoPlayerComponent;
import com.google.android.exoplayer2.ExoPlayer.ExoPlayerMessage;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
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
public final class DynamicConcatenatingMediaSource implements MediaSource, ExoPlayerComponent {

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
  private final Map<MediaPeriod, MediaSource> mediaSourceByMediaPeriod;
  private final List<DeferredMediaPeriod> deferredMediaPeriods;

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
    this(new DefaultShuffleOrder(0));
  }

  /**
   * Creates a new dynamic concatenating media source with a custom shuffle order.
   *
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   *     This shuffle order must be empty.
   */
  public DynamicConcatenatingMediaSource(ShuffleOrder shuffleOrder) {
    this.shuffleOrder = shuffleOrder;
    this.mediaSourceByMediaPeriod = new IdentityHashMap<>();
    this.mediaSourcesPublic = new ArrayList<>();
    this.mediaSourceHolders = new ArrayList<>();
    this.deferredMediaPeriods = new ArrayList<>(1);
    this.query = new MediaSourceHolder(null, null, -1, -1, -1);
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
      player.sendMessages(new ExoPlayerMessage(this, MSG_ADD,
          new MessageData<>(index, mediaSource, actionOnCompletion)));
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
      player.sendMessages(new ExoPlayerMessage(this, MSG_ADD_MULTIPLE,
          new MessageData<>(index, mediaSources, actionOnCompletion)));
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
      player.sendMessages(new ExoPlayerMessage(this, MSG_REMOVE,
          new MessageData<>(index, null, actionOnCompletion)));
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
      player.sendMessages(new ExoPlayerMessage(this, MSG_MOVE,
          new MessageData<>(currentIndex, newIndex, actionOnCompletion)));
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
    this.player = player;
    this.listener = listener;
    preventListenerNotification = true;
    shuffleOrder = shuffleOrder.cloneAndInsert(0, mediaSourcesPublic.size());
    addMediaSourcesInternal(0, mediaSourcesPublic);
    preventListenerNotification = false;
    maybeNotifyListener(null);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      mediaSourceHolders.get(i).mediaSource.maybeThrowSourceInfoRefreshError();
    }
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
    mediaSourceByMediaPeriod.put(mediaPeriod, holder.mediaSource);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSource mediaSource = mediaSourceByMediaPeriod.get(mediaPeriod);
    mediaSourceByMediaPeriod.remove(mediaPeriod);
    if (mediaPeriod instanceof DeferredMediaPeriod) {
      deferredMediaPeriods.remove(mediaPeriod);
      ((DeferredMediaPeriod) mediaPeriod).releasePeriod();
    } else {
      mediaSource.releasePeriod(mediaPeriod);
    }
  }

  @Override
  public void releaseSource() {
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      mediaSourceHolders.get(i).mediaSource.releaseSource();
    }
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
      listener.onSourceInfoRefreshed(this,
          new ConcatenatedTimeline(mediaSourceHolders, windowCount, periodCount, shuffleOrder),
          null);
      if (actionOnCompletion != null) {
        player.sendMessages(
            new ExoPlayerMessage(this, MSG_ON_COMPLETION, actionOnCompletion));
      }
    }
  }

  private void addMediaSourceInternal(int newIndex, MediaSource newMediaSource) {
    final MediaSourceHolder newMediaSourceHolder;
    Object newUid = System.identityHashCode(newMediaSource);
    DeferredTimeline newTimeline = new DeferredTimeline();
    if (newIndex > 0) {
      MediaSourceHolder previousHolder = mediaSourceHolders.get(newIndex - 1);
      newMediaSourceHolder = new MediaSourceHolder(newMediaSource, newTimeline,
          previousHolder.firstWindowIndexInChild + previousHolder.timeline.getWindowCount(),
          previousHolder.firstPeriodIndexInChild + previousHolder.timeline.getPeriodCount(),
          newUid);
    } else {
      newMediaSourceHolder = new MediaSourceHolder(newMediaSource, newTimeline, 0, 0, newUid);
    }
    correctOffsets(newIndex, newTimeline.getWindowCount(), newTimeline.getPeriodCount());
    mediaSourceHolders.add(newIndex, newMediaSourceHolder);
    newMediaSourceHolder.mediaSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(MediaSource source, Timeline newTimeline, Object manifest) {
        updateMediaSourceInternal(newMediaSourceHolder, newTimeline);
      }
    });
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
      int index = findMediaSourceHolderByPeriodIndex(mediaSourceHolder.firstPeriodIndexInChild);
      correctOffsets(index + 1, windowOffsetUpdate, periodOffsetUpdate);
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
    correctOffsets(index, -oldTimeline.getWindowCount(), -oldTimeline.getPeriodCount());
    holder.mediaSource.releaseSource();
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

  private void correctOffsets(int startIndex, int windowOffsetUpdate, int periodOffsetUpdate) {
    windowCount += windowOffsetUpdate;
    periodCount += periodOffsetUpdate;
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
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

  /**
   * Data class to hold playlist media sources together with meta data needed to process them.
   */
  private static final class MediaSourceHolder implements Comparable<MediaSourceHolder> {

    public final MediaSource mediaSource;
    public final Object uid;

    public DeferredTimeline timeline;
    public int firstWindowIndexInChild;
    public int firstPeriodIndexInChild;
    public boolean isPrepared;

    public MediaSourceHolder(MediaSource mediaSource, DeferredTimeline timeline, int window,
        int period, Object uid) {
      this.mediaSource = mediaSource;
      this.timeline = timeline;
      this.firstWindowIndexInChild = window;
      this.firstPeriodIndexInChild = period;
      this.uid = uid;
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

  /**
   * Message used to post actions from app thread to playback thread.
   */
  private static final class MessageData<CustomType> {

    public final int index;
    public final CustomType customData;
    public final @Nullable EventDispatcher actionOnCompletion;

    public MessageData(int index, CustomType customData, @Nullable Runnable actionOnCompletion) {
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

    public ConcatenatedTimeline(Collection<MediaSourceHolder> mediaSourceHolders, int windowCount,
        int periodCount, ShuffleOrder shuffleOrder) {
      super(shuffleOrder);
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
        uids[index] = (int) mediaSourceHolder.uid;
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
  private static final class DeferredTimeline extends Timeline {

    private static final Object DUMMY_ID = new Object();
    private static final Period period = new Period();

    private final Timeline timeline;
    private final Object replacedID;

    public DeferredTimeline() {
      timeline = null;
      replacedID = null;
    }

    private DeferredTimeline(Timeline timeline, Object replacedID) {
      this.timeline = timeline;
      this.replacedID = replacedID;
    }

    public DeferredTimeline cloneWithNewTimeline(Timeline timeline) {
      return new DeferredTimeline(timeline, replacedID == null && timeline.getPeriodCount() > 0
          ? timeline.getPeriod(0, period, true).uid : replacedID);
    }

    public Timeline getTimeline() {
      return timeline;
    }

    @Override
    public int getWindowCount() {
      return timeline == null ? 1 : timeline.getWindowCount();
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      return timeline == null
          // Dynamic window to indicate pending timeline updates.
          ? window.set(setIds ? DUMMY_ID : null, C.TIME_UNSET, C.TIME_UNSET, false, true, 0,
              C.TIME_UNSET, 0, 0, 0)
          : timeline.getWindow(windowIndex, window, setIds, defaultPositionProjectionUs);
    }

    @Override
    public int getPeriodCount() {
      return timeline == null ? 1 : timeline.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      if (timeline == null) {
        return period.set(setIds ? DUMMY_ID : null, setIds ? DUMMY_ID : null, 0, C.TIME_UNSET,
            C.TIME_UNSET);
      }
      timeline.getPeriod(periodIndex, period, setIds);
      if (period.uid == replacedID) {
        period.uid = DUMMY_ID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline == null ? (uid == DUMMY_ID ? 0 : C.INDEX_UNSET)
          : timeline.getIndexOfPeriod(uid == DUMMY_ID ? replacedID : uid);
    }

  }

  /**
   * Media period used for periods created from unprepared media sources exposed through
   * {@link DeferredTimeline}. Period preparation is postponed until the actual media source becomes
   * available.
   */
  private static final class DeferredMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

    public final MediaSource mediaSource;

    private final MediaPeriodId id;
    private final Allocator allocator;

    private MediaPeriod mediaPeriod;
    private Callback callback;
    private long preparePositionUs;

    public DeferredMediaPeriod(MediaSource mediaSource, MediaPeriodId id, Allocator allocator) {
      this.id = id;
      this.allocator = allocator;
      this.mediaSource = mediaSource;
    }

    public void createPeriod() {
      mediaPeriod = mediaSource.createPeriod(id, allocator);
      if (callback != null) {
        mediaPeriod.prepare(this, preparePositionUs);
      }
    }

    public void releasePeriod() {
      if (mediaPeriod != null) {
        mediaSource.releasePeriod(mediaPeriod);
      }
    }

    @Override
    public void prepare(Callback callback, long preparePositionUs) {
      this.callback = callback;
      this.preparePositionUs = preparePositionUs;
      if (mediaPeriod != null) {
        mediaPeriod.prepare(this, preparePositionUs);
      }
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      if (mediaPeriod != null) {
        mediaPeriod.maybeThrowPrepareError();
      } else {
        mediaSource.maybeThrowSourceInfoRefreshError();
      }
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      return mediaPeriod.getTrackGroups();
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
        SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
      return mediaPeriod.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags,
          positionUs);
    }

    @Override
    public void discardBuffer(long positionUs) {
      mediaPeriod.discardBuffer(positionUs);
    }

    @Override
    public long readDiscontinuity() {
      return mediaPeriod.readDiscontinuity();
    }

    @Override
    public long getBufferedPositionUs() {
      return mediaPeriod.getBufferedPositionUs();
    }

    @Override
    public long seekToUs(long positionUs) {
      return mediaPeriod.seekToUs(positionUs);
    }

    @Override
    public long getNextLoadPositionUs() {
      return mediaPeriod.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return mediaPeriod != null && mediaPeriod.continueLoading(positionUs);
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
      callback.onContinueLoadingRequested(this);
    }

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      callback.onPrepared(this);
    }
  }

}

