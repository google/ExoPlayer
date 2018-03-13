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
import android.os.SystemClock;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** Interface for callbacks to be notified of {@link MediaSource} events. */
public interface MediaSourceEventListener {

  /** Media source load event information. */
  final class LoadEventInfo {

    /** Defines the data being loaded. */
    public final DataSpec dataSpec;
    /** The value of {@link SystemClock#elapsedRealtime} at the time of the load event. */
    public final long elapsedRealtimeMs;
    /** The duration of the load up to the event time. */
    public final long loadDurationMs;
    /** The number of bytes that were loaded up to the event time. */
    public final long bytesLoaded;

    /**
     * Creates load event info.
     *
     * @param dataSpec Defines the data being loaded.
     * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} at the time of the
     *     load event.
     * @param loadDurationMs The duration of the load up to the event time.
     * @param bytesLoaded The number of bytes that were loaded up to the event time.
     */
    public LoadEventInfo(
        DataSpec dataSpec, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
      this.dataSpec = dataSpec;
      this.elapsedRealtimeMs = elapsedRealtimeMs;
      this.loadDurationMs = loadDurationMs;
      this.bytesLoaded = bytesLoaded;
    }
  }

  /** Descriptor for data being loaded or selected by a media source. */
  final class MediaLoadData {

    /** The window index in the timeline of the media source this data belongs to. */
    public final int windowIndex;
    /**
     * The {@link MediaPeriodId} this data belongs to. Null if the data does not belong to a
     * specific media period.
     */
    public final @Nullable MediaPeriodId mediaPeriodId;
    /** One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data. */
    public final int dataType;
    /**
     * One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds to media of a
     * specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     */
    public final int trackType;
    /**
     * The format of the track to which the data belongs. Null if the data does not belong to a
     * specific track.
     */
    public final @Nullable Format trackFormat;
    /**
     * One of the {@link C} {@code SELECTION_REASON_*} constants if the data belongs to a track.
     * {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     */
    public final int trackSelectionReason;
    /**
     * Optional data associated with the selection of the track to which the data belongs. Null if
     * the data does not belong to a track.
     */
    public final @Nullable Object trackSelectionData;
    /**
     * The start time of the media, or {@link C#TIME_UNSET} if the data does not belong to a
     * specific media period.
     */
    public final long mediaStartTimeMs;
    /**
     * The end time of the media, or {@link C#TIME_UNSET} if the data does not belong to a specific
     * media period or the end time is unknown.
     */
    public final long mediaEndTimeMs;

    /**
     * Creates media load data.
     *
     * @param windowIndex The window index in the timeline of the media source this load belongs to.
     * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the data does
     *     not belong to a specific media period.
     * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data.
     * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
     *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
     * @param trackFormat The format of the track to which the data belongs. Null if the data does
     *     not belong to a track.
     * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
     *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
     * @param trackSelectionData Optional data associated with the selection of the track to which
     *     the data belongs. Null if the data does not belong to a track.
     * @param mediaStartTimeMs The start time of the media, or {@link C#TIME_UNSET} if the data does
     *     not belong to a specific media period.
     * @param mediaEndTimeMs The end time of the media, or {@link C#TIME_UNSET} if the data does not
     *     belong to a specific media period or the end time is unknown.
     */
    public MediaLoadData(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeMs,
        long mediaEndTimeMs) {
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
      this.dataType = dataType;
      this.trackType = trackType;
      this.trackFormat = trackFormat;
      this.trackSelectionReason = trackSelectionReason;
      this.trackSelectionData = trackSelectionData;
      this.mediaStartTimeMs = mediaStartTimeMs;
      this.mediaEndTimeMs = mediaEndTimeMs;
    }
  }

  /**
   * Called when a load begins.
   *
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData);

  /**
   * Called when a load ends.
   *
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData);

  /**
   * Called when a load is canceled.
   *
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  void onLoadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData);

  /**
   * Called when a load error occurs.
   *
   * <p>The error may or may not have resulted in the load being canceled, as indicated by the
   * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
   * <em>not</em> be called in addition to this method.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error and continue. Hence applications should
   * <em>not</em> implement this method to display a user visible error or initiate an application
   * level retry ({@link Player.EventListener#onPlayerError} is the appropriate place to implement
   * such behavior). This method is called to provide the application with an opportunity to log the
   * error if it wishes to do so.
   *
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  void onLoadError(
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled);

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param mediaLoadData The {@link MediaLoadData} defining the media being discarded.
   */
  void onUpstreamDiscarded(MediaLoadData mediaLoadData);

  /**
   * Called when a downstream format change occurs (i.e. when the format of the media being read
   * from one or more {@link SampleStream}s provided by the source changes).
   *
   * @param mediaLoadData The {@link MediaLoadData} defining the newly selected downstream data.
   */
  void onDownstreamFormatChanged(MediaLoadData mediaLoadData);

  /** Dispatches events to {@link MediaSourceEventListener}s. */
  final class EventDispatcher {

    private final CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers;
    private final int windowIndex;
    private final @Nullable MediaPeriodId mediaPeriodId;
    private final long mediaTimeOffsetMs;

    /** Creates an event dispatcher. */
    /* package */ EventDispatcher() {
      this(
          /* listenerAndHandlers= */ new CopyOnWriteArrayList<ListenerAndHandler>(),
          /* windowIndex= */ 0,
          /* mediaPeriodId= */ null,
          /* mediaTimeOffsetMs= */ 0);
    }

    private EventDispatcher(
        CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers,
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        long mediaTimeOffsetMs) {
      this.listenerAndHandlers = listenerAndHandlers;
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
      this.mediaTimeOffsetMs = mediaTimeOffsetMs;
    }

    /**
     * Creates a view of the event dispatcher with pre-configured window index, media period id, and
     * media time offset.
     *
     * @param windowIndex The timeline window index to be reported with the events.
     * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events.
     * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
     * @return A view of the event dispatcher with the pre-configured parameters.
     */
    @CheckResult
    /* package */ EventDispatcher withParameters(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
      return new EventDispatcher(
          listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    /**
     * Adds a listener to the event dispatcher.
     *
     * @param handler A handler on the which listener events will be posted.
     * @param eventListener The listener to be added.
     */
    public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
      Assertions.checkArgument(handler != null && eventListener != null);
      listenerAndHandlers.add(new ListenerAndHandler(handler, eventListener));
    }

    /**
     * Removes a listener from the event dispatcher.
     *
     * @param eventListener The listener to be removed.
     */
    public void removeEventListener(MediaSourceEventListener eventListener) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        if (listenerAndHandler.listener == eventListener) {
          listenerAndHandlers.remove(listenerAndHandler);
        }
      }
    }

    /** Dispatches {@link #onLoadStarted(LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs) {
      loadStarted(
          dataSpec,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs);
    }

    /** Dispatches {@link #onLoadStarted(LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(
        final DataSpec dataSpec,
        final int dataType,
        final int trackType,
        final @Nullable Format trackFormat,
        final int trackSelectionReason,
        final @Nullable Object trackSelectionData,
        final long mediaStartTimeUs,
        final long mediaEndTimeUs,
        final long elapsedRealtimeMs) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onLoadStarted(
                    new LoadEventInfo(
                        dataSpec, elapsedRealtimeMs, /* loadDurationMs= */ 0, /* bytesLoaded= */ 0),
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        dataType,
                        trackType,
                        trackFormat,
                        trackSelectionReason,
                        trackSelectionData,
                        adjustMediaTime(mediaStartTimeUs),
                        adjustMediaTime(mediaEndTimeUs)));
              }
            });
      }
    }

    /** Dispatches {@link #onLoadCompleted(LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(
        DataSpec dataSpec,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCompleted(
          dataSpec,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded);
    }

    /** Dispatches {@link #onLoadCompleted(LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(
        final DataSpec dataSpec,
        final int dataType,
        final int trackType,
        final @Nullable Format trackFormat,
        final int trackSelectionReason,
        final @Nullable Object trackSelectionData,
        final long mediaStartTimeUs,
        final long mediaEndTimeUs,
        final long elapsedRealtimeMs,
        final long loadDurationMs,
        final long bytesLoaded) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onLoadCompleted(
                    new LoadEventInfo(dataSpec, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        dataType,
                        trackType,
                        trackFormat,
                        trackSelectionReason,
                        trackSelectionData,
                        adjustMediaTime(mediaStartTimeUs),
                        adjustMediaTime(mediaEndTimeUs)));
              }
            });
      }
    }

    /** Dispatches {@link #onLoadCanceled(LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(
        DataSpec dataSpec,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded) {
      loadCanceled(
          dataSpec,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded);
    }

    /** Dispatches {@link #onLoadCanceled(LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(
        final DataSpec dataSpec,
        final int dataType,
        final int trackType,
        final @Nullable Format trackFormat,
        final int trackSelectionReason,
        final @Nullable Object trackSelectionData,
        final long mediaStartTimeUs,
        final long mediaEndTimeUs,
        final long elapsedRealtimeMs,
        final long loadDurationMs,
        final long bytesLoaded) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onLoadCanceled(
                    new LoadEventInfo(dataSpec, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        dataType,
                        trackType,
                        trackFormat,
                        trackSelectionReason,
                        trackSelectionData,
                        adjustMediaTime(mediaStartTimeUs),
                        adjustMediaTime(mediaEndTimeUs)));
              }
            });
      }
    }

    /** Dispatches {@link #onLoadError(LoadEventInfo, MediaLoadData, IOException, boolean)}. */
    public void loadError(
        DataSpec dataSpec,
        int dataType,
        long elapsedRealtimeMs,
        long loadDurationMs,
        long bytesLoaded,
        IOException error,
        boolean wasCanceled) {
      loadError(
          dataSpec,
          dataType,
          C.TRACK_TYPE_UNKNOWN,
          null,
          C.SELECTION_REASON_UNKNOWN,
          null,
          C.TIME_UNSET,
          C.TIME_UNSET,
          elapsedRealtimeMs,
          loadDurationMs,
          bytesLoaded,
          error,
          wasCanceled);
    }

    /** Dispatches {@link #onLoadError(LoadEventInfo, MediaLoadData, IOException, boolean)}. */
    public void loadError(
        final DataSpec dataSpec,
        final int dataType,
        final int trackType,
        final @Nullable Format trackFormat,
        final int trackSelectionReason,
        final @Nullable Object trackSelectionData,
        final long mediaStartTimeUs,
        final long mediaEndTimeUs,
        final long elapsedRealtimeMs,
        final long loadDurationMs,
        final long bytesLoaded,
        final IOException error,
        final boolean wasCanceled) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onLoadError(
                    new LoadEventInfo(dataSpec, elapsedRealtimeMs, loadDurationMs, bytesLoaded),
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        dataType,
                        trackType,
                        trackFormat,
                        trackSelectionReason,
                        trackSelectionData,
                        adjustMediaTime(mediaStartTimeUs),
                        adjustMediaTime(mediaEndTimeUs)),
                    error,
                    wasCanceled);
              }
            });
      }
    }

    /** Dispatches {@link #onUpstreamDiscarded(MediaLoadData)}. */
    public void upstreamDiscarded(
        final int trackType, final long mediaStartTimeUs, final long mediaEndTimeUs) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onUpstreamDiscarded(
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        C.DATA_TYPE_MEDIA,
                        trackType,
                        /* trackFormat= */ null,
                        C.SELECTION_REASON_ADAPTIVE,
                        /* trackSelectionData= */ null,
                        adjustMediaTime(mediaStartTimeUs),
                        adjustMediaTime(mediaEndTimeUs)));
              }
            });
      }
    }

    /** Dispatches {@link #onDownstreamFormatChanged(MediaLoadData)}. */
    public void downstreamFormatChanged(
        final int trackType,
        final @Nullable Format trackFormat,
        final int trackSelectionReason,
        final @Nullable Object trackSelectionData,
        final long mediaTimeUs) {
      for (final ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        listenerAndHandler.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listenerAndHandler.listener.onDownstreamFormatChanged(
                    new MediaLoadData(
                        windowIndex,
                        mediaPeriodId,
                        C.DATA_TYPE_MEDIA,
                        trackType,
                        trackFormat,
                        trackSelectionReason,
                        trackSelectionData,
                        adjustMediaTime(mediaTimeUs),
                        /* mediaEndTimeMs= */ C.TIME_UNSET));
              }
            });
      }
    }

    private long adjustMediaTime(long mediaTimeUs) {
      long mediaTimeMs = C.usToMs(mediaTimeUs);
      return mediaTimeMs == C.TIME_UNSET ? C.TIME_UNSET : mediaTimeOffsetMs + mediaTimeMs;
    }

    private static final class ListenerAndHandler {

      public final Handler handler;
      public final MediaSourceEventListener listener;

      public ListenerAndHandler(Handler handler, MediaSourceEventListener listener) {
        this.handler = handler;
        this.listener = listener;
      }
    }
  }
}
