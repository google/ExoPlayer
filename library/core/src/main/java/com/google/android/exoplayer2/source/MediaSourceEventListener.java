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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.CopyOnWriteMultiset;
import com.google.android.exoplayer2.util.MediaSourceEventDispatcher;
import java.io.IOException;

/** Interface for callbacks to be notified of {@link MediaSource} events. */
public interface MediaSourceEventListener {

  /**
   * Called when a media period is created by the media source.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the created media period.
   */
  default void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {}

  /**
   * Called when a media period is released by the media source.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the released media period.
   */
  default void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {}

  /**
   * Called when a load begins.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The value of {@link
   *     LoadEventInfo#uri} won't reflect potential redirection yet and {@link
   *     LoadEventInfo#responseHeaders} will be empty.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

  /**
   * Called when a load ends.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

  /**
   * Called when a load is canceled.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

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
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  default void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {}

  /**
   * Called when a media period is first being read from.
   *
   * @param windowIndex The window index in the timeline this media period belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} of the media period being read from.
   */
  default void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {}

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the media being discarded.
   */
  default void onUpstreamDiscarded(
      int windowIndex, MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {}

  /**
   * Called when a downstream format change occurs (i.e. when the format of the media being read
   * from one or more {@link SampleStream}s provided by the source changes).
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the newly selected downstream data.
   */
  default void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {}

  /** @deprecated Use {@link MediaSourceEventDispatcher} directly instead. */
  @Deprecated
  final class EventDispatcher extends MediaSourceEventDispatcher {

    public EventDispatcher() {
      super();
    }

    private EventDispatcher(
        CopyOnWriteMultiset<ListenerInfo> listeners,
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        long mediaTimeOffsetMs) {
      super(listeners, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    @Override
    public EventDispatcher withParameters(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
      return new EventDispatcher(listenerInfos, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    public void mediaPeriodCreated() {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onMediaPeriodCreated(windowIndex, Assertions.checkNotNull(mediaPeriodId)),
          MediaSourceEventListener.class);
    }

    public void mediaPeriodReleased() {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onMediaPeriodReleased(windowIndex, Assertions.checkNotNull(mediaPeriodId)),
          MediaSourceEventListener.class);
    }

    public void loadStarted(LoadEventInfo loadEventInfo, int dataType) {
      loadStarted(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    public void loadStarted(
        LoadEventInfo loadEventInfo,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadStarted(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    public void loadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onLoadStarted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData),
          MediaSourceEventListener.class);
    }

    public void loadCompleted(LoadEventInfo loadEventInfo, int dataType) {
      loadCompleted(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    public void loadCompleted(
        LoadEventInfo loadEventInfo,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadCompleted(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    public void loadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onLoadCompleted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData),
          MediaSourceEventListener.class);
    }

    public void loadCanceled(LoadEventInfo loadEventInfo, int dataType) {
      loadCanceled(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    public void loadCanceled(
        LoadEventInfo loadEventInfo,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadCanceled(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    public void loadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onLoadCanceled(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData),
          MediaSourceEventListener.class);
    }

    public void loadError(
        LoadEventInfo loadEventInfo, int dataType, IOException error, boolean wasCanceled) {
      loadError(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET,
          error,
          wasCanceled);
    }

    public void loadError(
        LoadEventInfo loadEventInfo,
        int dataType,
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        IOException error,
        boolean wasCanceled) {
      loadError(
          loadEventInfo,
          new MediaLoadData(
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

    public void loadError(
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onLoadError(
                  windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData, error, wasCanceled),
          MediaSourceEventListener.class);
    }

    public void readingStarted() {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onReadingStarted(windowIndex, Assertions.checkNotNull(mediaPeriodId)),
          MediaSourceEventListener.class);
    }

    public void upstreamDiscarded(int trackType, long mediaStartTimeUs, long mediaEndTimeUs) {
      upstreamDiscarded(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              /* trackFormat= */ null,
              C.SELECTION_REASON_ADAPTIVE,
              /* trackSelectionData= */ null,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    public void upstreamDiscarded(MediaLoadData mediaLoadData) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onUpstreamDiscarded(
                  windowIndex, Assertions.checkNotNull(mediaPeriodId), mediaLoadData),
          MediaSourceEventListener.class);
    }

    public void downstreamFormatChanged(
        int trackType,
        @Nullable Format trackFormat,
        int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaTimeUs) {
      downstreamFormatChanged(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaTimeUs),
              /* mediaEndTimeMs= */ C.TIME_UNSET));
    }

    public void downstreamFormatChanged(MediaLoadData mediaLoadData) {
      dispatch(
          (listener, windowIndex, mediaPeriodId) ->
              listener.onDownstreamFormatChanged(windowIndex, mediaPeriodId, mediaLoadData),
          MediaSourceEventListener.class);
    }

    private long adjustMediaTime(long mediaTimeUs) {
      return adjustMediaTime(mediaTimeUs, mediaTimeOffsetMs);
    }
  }
}
