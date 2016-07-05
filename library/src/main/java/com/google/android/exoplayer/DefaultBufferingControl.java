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

import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.util.Util;

import android.os.Handler;

/**
 * The default {@link BufferingControl} implementation.
 */
public final class DefaultBufferingControl implements BufferingControl {

  /**
   * Interface definition for a callback to be notified of {@link DefaultBufferingControl} events.
   */
  public interface EventListener {

    /**
     * Invoked when the control transitions from a buffering to a draining state or vice versa.
     *
     * @param buffering Whether the control is now in the buffering state.
     */
    void onBufferingChanged(boolean buffering);

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
  private final Handler eventHandler;
  private final EventListener eventListener;

  private final long minBufferUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private final long bufferForPlaybackAfterRebufferUs;

  private int targetBufferSize;
  private boolean isBuffering;

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   */
  public DefaultBufferingControl() {
    this(new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE));
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   */
  public DefaultBufferingControl(DefaultAllocator allocator) {
    this(allocator, null, null);
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultBufferingControl(DefaultAllocator allocator, Handler eventHandler,
      EventListener eventListener) {
    this(allocator, DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, eventHandler, eventListener);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a player-invoked rebuffer (i.e. a rebuffer that occurs due to
   *     buffer depletion rather than a user action), in milliseconds.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultBufferingControl(DefaultAllocator allocator, int minBufferMs, int maxBufferMs,
      long bufferForPlaybackMs, long bufferForPlaybackAfterRebufferMs, Handler eventHandler,
      EventListener eventListener) {
    this.allocator = allocator;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    minBufferUs = minBufferMs * 1000L;
    maxBufferUs = maxBufferMs * 1000L;
    bufferForPlaybackUs = bufferForPlaybackMs * 1000L;
    bufferForPlaybackAfterRebufferUs = bufferForPlaybackAfterRebufferMs * 1000L;
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
  }

  @Override
  public void reset() {
    targetBufferSize = 0;
    setBuffering(false);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering) {
    long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    return minBufferDurationUs <= 0 || bufferedDurationUs >= minBufferDurationUs;
  }

  @Override
  public boolean shouldContinueBuffering(long bufferedDurationUs) {
    int bufferTimeState = getBufferTimeState(bufferedDurationUs);
    boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    boolean shouldBuffer = bufferTimeState == BELOW_LOW_WATERMARK
        || (bufferTimeState == BETWEEN_WATERMARKS && isBuffering && !targetBufferSizeReached);
    setBuffering(shouldBuffer);
    return shouldBuffer;
  }

  private void setBuffering(boolean isBuffering) {
    if (this.isBuffering != isBuffering) {
      this.isBuffering = isBuffering;
      notifyBufferingChanged(isBuffering);
    }
  }

  private int getBufferTimeState(long bufferedDurationUs) {
    return bufferedDurationUs > maxBufferUs ? ABOVE_HIGH_WATERMARK
        : (bufferedDurationUs < minBufferUs ? BELOW_LOW_WATERMARK : BETWEEN_WATERMARKS);
  }

  private void notifyBufferingChanged(final boolean buffering) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onBufferingChanged(buffering);
        }
      });
    }
  }

}
