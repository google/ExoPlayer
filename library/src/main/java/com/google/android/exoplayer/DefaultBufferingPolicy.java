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
package com.google.android.exoplayer;

import com.google.android.exoplayer.BufferingPolicy.LoadControl;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.NetworkLock;
import com.google.android.exoplayer.util.Util;

import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A {@link LoadControl} implementation that allows loads to continue in a sequence that prevents
 * any loader from getting too far ahead or behind any of the other loaders.
 * <p>
 * Loads are scheduled so as to fill the available buffer space as rapidly as possible. Once the
 * duration of buffered media and the buffer utilization both exceed respective thresholds, the
 * control switches to a draining state during which no loads are permitted to start. During
 * draining periods, resources such as the device radio have an opportunity to switch into low
 * power modes. The control reverts back to the loading state when either the duration of buffered
 * media or the buffer utilization fall below respective thresholds.
 * <p>
 * This implementation of {@link LoadControl} integrates with {@link NetworkLock}, by registering
 * itself as a task with priority {@link NetworkLock#STREAMING_PRIORITY} during loading periods,
 * and unregistering itself during draining periods.
 */
public final class DefaultBufferingPolicy implements BufferingPolicy, LoadControl {

  /**
   * Interface definition for a callback to be notified of {@link DefaultBufferingPolicy} events.
   */
  public interface EventListener {

    /**
     * Invoked when the control transitions from a loading to a draining state, or vice versa.
     *
     * @param loading Whether the control is now in a loading state.
     */
    void onLoadingChanged(boolean loading);

  }

  /**
   * The default minimum duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 15000;

  /**
   * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 30000;

  /**
   * The default duration of media that must be buffered for playback to start or resume following a
   * user action such as a seek, in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

  /**
   * The default duration of media that must be buffered for playback to resume after a
   * player-invoked rebuffer (i.e. a rebuffer that occurs due to buffer depletion rather than a user
   * action), in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS  = 5000;

  private static final int ABOVE_HIGH_WATERMARK = 0;
  private static final int BETWEEN_WATERMARKS = 1;
  private static final int BELOW_LOW_WATERMARK = 2;

  private final DefaultAllocator allocator;
  private final List<Object> loaders;
  private final HashMap<Object, LoaderState> loaderStates;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private final long minBufferUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private final long bufferForPlaybackAfterRebufferUs;

  private int targetBufferSize;
  private boolean targetBufferSizeReached;
  private boolean fillingBuffers;
  private boolean streamingPrioritySet;

  private long playbackPositionUs;
  private long maxLoadStartPositionUs;

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   */
  public DefaultBufferingPolicy() {
    this(null, null);
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultBufferingPolicy(Handler eventHandler, EventListener eventListener) {
    this(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE), eventHandler, eventListener);
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultBufferingPolicy(DefaultAllocator allocator, Handler eventHandler,
      EventListener eventListener) {
    this(allocator, eventHandler, eventListener, DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_MS, DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a player-invoked rebuffer (i.e. a rebuffer that occurs due to
   *     buffer depletion rather than a user action), in milliseconds.
   */
  public DefaultBufferingPolicy(DefaultAllocator allocator, Handler eventHandler,
      EventListener eventListener, int minBufferMs, int maxBufferMs, long bufferForPlaybackMs,
      long bufferForPlaybackAfterRebufferMs) {
    this.allocator = allocator;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.minBufferUs = minBufferMs * 1000L;
    this.maxBufferUs = maxBufferMs * 1000L;
    this.bufferForPlaybackUs = bufferForPlaybackMs * 1000L;
    this.bufferForPlaybackAfterRebufferUs = bufferForPlaybackAfterRebufferMs * 1000L;
    loaders = new ArrayList<>();
    loaderStates = new HashMap<>();
  }

  // BufferingPolicy implementation.

  @Override
  public void setPlaybackPosition(long playbackPositionUs) {
    this.playbackPositionUs = playbackPositionUs;
  }

  @Override
  public boolean haveSufficientBuffer(long bufferedPositionUs, boolean rebuffering) {
    long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    return minBufferDurationUs <= 0
        || bufferedPositionUs == C.END_OF_SOURCE_US
        || bufferedPositionUs >= playbackPositionUs + minBufferDurationUs;
  }

  @Override
  public void onTrackSelections(TrackRenderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections) {
    targetBufferSize = 0;
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelections.get(i) != null) {
        targetBufferSize += Util.getDefaultBufferSize(renderers[i].getTrackType());
      }
    }
    allocator.setTargetBufferSize(targetBufferSize);
    targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    updateControlState();
  }

  @Override
  public void reset() {
    targetBufferSize = 0;
  }

  @Override
  public LoadControl getLoadControl() {
    return this;
  }

  // LoadControl implementation.

  @Override
  public void register(Object loader) {
    loaders.add(loader);
    loaderStates.put(loader, new LoaderState());
  }

  @Override
  public void unregister(Object loader) {
    loaders.remove(loader);
    loaderStates.remove(loader);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public boolean update(Object loader, long nextLoadPositionUs, boolean loading) {
    // Update the loader state.
    int loaderBufferState = getLoaderBufferState(playbackPositionUs, nextLoadPositionUs);
    LoaderState loaderState = loaderStates.get(loader);
    boolean loaderStateChanged = loaderState.bufferState != loaderBufferState
        || loaderState.nextLoadPositionUs != nextLoadPositionUs || loaderState.loading != loading;
    if (loaderStateChanged) {
      loaderState.bufferState = loaderBufferState;
      loaderState.nextLoadPositionUs = nextLoadPositionUs;
      loaderState.loading = loading;
    }

    // Update the buffer state.
    boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    boolean bufferStateChanged = this.targetBufferSizeReached != targetBufferSizeReached;
    if (bufferStateChanged) {
      this.targetBufferSizeReached = targetBufferSizeReached;
    }

    // If either of the individual states have changed, update the shared control state.
    if (loaderStateChanged || bufferStateChanged) {
      updateControlState();
    }

    return nextLoadPositionUs != C.UNSET_TIME_US && nextLoadPositionUs <= maxLoadStartPositionUs;
  }

  private int getLoaderBufferState(long playbackPositionUs, long nextLoadPositionUs) {
    if (nextLoadPositionUs == C.UNSET_TIME_US) {
      return ABOVE_HIGH_WATERMARK;
    } else {
      long timeUntilNextLoadPosition = nextLoadPositionUs - playbackPositionUs;
      return timeUntilNextLoadPosition > maxBufferUs ? ABOVE_HIGH_WATERMARK :
          timeUntilNextLoadPosition < minBufferUs ? BELOW_LOW_WATERMARK :
          BETWEEN_WATERMARKS;
    }
  }

  private void updateControlState() {
    boolean loading = false;
    boolean haveNextLoadPosition = false;
    int worstLoaderState = ABOVE_HIGH_WATERMARK;
    for (int i = 0; i < loaders.size(); i++) {
      LoaderState loaderState = loaderStates.get(loaders.get(i));
      loading |= loaderState.loading;
      haveNextLoadPosition |= loaderState.nextLoadPositionUs != C.UNSET_TIME_US;
      worstLoaderState = Math.max(worstLoaderState, loaderState.bufferState);
    }

    fillingBuffers = !loaders.isEmpty() && (loading || haveNextLoadPosition)
        && (worstLoaderState == BELOW_LOW_WATERMARK
        || (worstLoaderState == BETWEEN_WATERMARKS && fillingBuffers && !targetBufferSizeReached));
    if (fillingBuffers && !streamingPrioritySet) {
      NetworkLock.instance.add(NetworkLock.STREAMING_PRIORITY);
      streamingPrioritySet = true;
      notifyLoadingChanged(true);
    } else if (!fillingBuffers && streamingPrioritySet && !loading) {
      NetworkLock.instance.remove(NetworkLock.STREAMING_PRIORITY);
      streamingPrioritySet = false;
      notifyLoadingChanged(false);
    }

    maxLoadStartPositionUs = C.UNSET_TIME_US;
    if (fillingBuffers) {
      for (int i = 0; i < loaders.size(); i++) {
        Object loader = loaders.get(i);
        LoaderState loaderState = loaderStates.get(loader);
        long loaderTime = loaderState.nextLoadPositionUs;
        if (loaderTime != C.UNSET_TIME_US
            && (maxLoadStartPositionUs == C.UNSET_TIME_US || loaderTime < maxLoadStartPositionUs)) {
          maxLoadStartPositionUs = loaderTime;
        }
      }
    }
  }

  private void notifyLoadingChanged(final boolean loading) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadingChanged(loading);
        }
      });
    }
  }

  private static class LoaderState {

    public int bufferState;
    public boolean loading;
    public long nextLoadPositionUs;

    public LoaderState() {
      bufferState = ABOVE_HIGH_WATERMARK;
      loading = false;
      nextLoadPositionUs = C.UNSET_TIME_US;
    }

  }

}
