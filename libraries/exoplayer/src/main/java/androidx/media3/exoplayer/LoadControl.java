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
package androidx.media3.exoplayer;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

/** Controls buffering of media. */
@UnstableApi
public interface LoadControl {

  /**
   * @deprecated Used as a placeholder when MediaPeriodId is unknown. Only used when the deprecated
   *     methods {@link #onTracksSelected(Renderer[], TrackGroupArray, ExoTrackSelection[])} or
   *     {@link #shouldStartPlayback(long, float, boolean, long)} are called.
   */
  @Deprecated
  MediaPeriodId EMPTY_MEDIA_PERIOD_ID = new MediaPeriodId(/* periodUid= */ new Object());

  /**
   * Called by the player when prepared with a new source.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that prepared a new source.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onPrepared(PlayerId playerId) {
    onPrepared();
  }

  /**
   * @deprecated Use {@link #onPrepared(PlayerId)} instead.
   */
  @Deprecated
  default void onPrepared() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onPrepared not implemented");
  }

  /**
   * Called by the player when a track selection occurs.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that selected tracks.
   * @param timeline The current {@link Timeline} in ExoPlayer.
   * @param mediaPeriodId Identifies (in the current timeline) the {@link MediaPeriod} for which the
   *     selection was made. Will be {@link #EMPTY_MEDIA_PERIOD_ID} when {@code timeline} is empty.
   * @param renderers The renderers.
   * @param trackGroups The {@link TrackGroup}s from which the selection was made.
   * @param trackSelections The track selections that were made.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onTracksSelected(
      PlayerId playerId,
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      ExoTrackSelection[] trackSelections) {
    onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated Implement {@link #onTracksSelected(PlayerId, Timeline, MediaPeriodId, Renderer[],
   *     TrackGroupArray, ExoTrackSelection[])} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default void onTracksSelected(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      ExoTrackSelection[] trackSelections) {
    onTracksSelected(renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated Implement {@link #onTracksSelected(PlayerId, Timeline, MediaPeriodId, Renderer[],
   *     TrackGroupArray, ExoTrackSelection[])} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default void onTracksSelected(
      Renderer[] renderers, TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onTracksSelected not implemented");
  }

  /**
   * Called by the player when stopped.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that was stopped.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onStopped(PlayerId playerId) {
    onStopped();
  }

  /**
   * @deprecated Implement {@link #onStopped(PlayerId)} instead.
   */
  @Deprecated
  default void onStopped() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onStopped not implemented");
  }

  /**
   * Called by the player when released.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that was released.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onReleased(PlayerId playerId) {
    onReleased();
  }

  /**
   * @deprecated Implement {@link #onReleased(PlayerId)} instead.
   */
  @Deprecated
  default void onReleased() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onReleased not implemented");
  }

  /** Returns the {@link Allocator} that should be used to obtain media buffer allocations. */
  Allocator getAllocator();

  /**
   * Returns the duration of media to retain in the buffer prior to the current playback position,
   * for fast backward seeking.
   *
   * <p>Note: If {@link #retainBackBufferFromKeyframe()} is false then seeking in the back-buffer
   * will only be fast if the back-buffer contains a keyframe prior to the seek position.
   *
   * <p>Note: Implementations should return a single value. Dynamic changes to the back-buffer are
   * not currently supported.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that requests the back buffer
   *     duration.
   * @return The duration of media to retain in the buffer prior to the current playback position,
   *     in microseconds.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default long getBackBufferDurationUs(PlayerId playerId) {
    return getBackBufferDurationUs();
  }

  /**
   * @deprecated Implements {@link #getBackBufferDurationUs(PlayerId)} instead.
   */
  @Deprecated
  default long getBackBufferDurationUs() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("getBackBufferDurationUs not implemented");
  }

  /**
   * Returns whether media should be retained from the keyframe before the current playback position
   * minus {@link #getBackBufferDurationUs()}, rather than any sample before or at that position.
   *
   * <p>Warning: Returning true will cause the back-buffer size to depend on the spacing of
   * keyframes in the media being played. Returning true is not recommended unless you control the
   * media and are comfortable with the back-buffer size exceeding {@link
   * #getBackBufferDurationUs()} by as much as the maximum duration between adjacent keyframes in
   * the media.
   *
   * <p>Note: Implementations should return a single value. Dynamic changes to the back-buffer are
   * not currently supported.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that requests whether to retain the
   *     back buffer from key frame.
   * @return Whether media should be retained from the keyframe before the current playback position
   *     minus {@link #getBackBufferDurationUs()}, rather than any sample before or at that
   *     position.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default boolean retainBackBufferFromKeyframe(PlayerId playerId) {
    return retainBackBufferFromKeyframe();
  }

  /**
   * @deprecated Implements {@link #retainBackBufferFromKeyframe(PlayerId)} instead.
   */
  @Deprecated
  default boolean retainBackBufferFromKeyframe() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("retainBackBufferFromKeyframe not implemented");
  }

  /**
   * Called by the player to determine whether it should continue to load the source. If this method
   * returns true, the {@link MediaPeriod} identified in the most recent {@link #onTracksSelected}
   * call will continue being loaded.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that wants to continue loading.
   * @param timeline The current {@link Timeline} in ExoPlayer.
   * @param mediaPeriodId Identifies (in the current timeline) the {@link MediaPeriod} that is
   *     currently loading.
   * @param playbackPositionUs The current playback position in microseconds, relative to the start
   *     of the {@link Timeline.Period period} that will continue to be loaded if this method
   *     returns {@code true}. If playback of this period has not yet started, the value will be
   *     negative and equal in magnitude to the duration of any media in previous periods still to
   *     be played.
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @param playbackSpeed The current factor by which playback is sped up.
   * @return Whether the loading should continue.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default boolean shouldContinueLoading(
      PlayerId playerId,
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      long playbackPositionUs,
      long bufferedDurationUs,
      float playbackSpeed) {
    return shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed);
  }

  /**
   * @deprecated Implement {@link #shouldContinueLoading(PlayerId, Timeline, MediaPeriodId, long,
   *     long, float)} instead.
   */
  @Deprecated
  default boolean shouldContinueLoading(
      long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("shouldContinueLoading not implemented");
  }

  /**
   * Called repeatedly by the player when it's loading the source, has yet to start playback, and
   * has the minimum amount of data necessary for playback to be started. The value returned
   * determines whether playback is actually started. The load control may opt to return {@code
   * false} until some condition has been met (e.g. a certain amount of media is buffered).
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that wants to start playback.
   * @param timeline The current {@link Timeline} in ExoPlayer. Can be {@link Timeline#EMPTY} only
   *     when the deprecated {@link #shouldStartPlayback(long, float, boolean, long)} was called.
   * @param mediaPeriodId Identifies (in the current timeline) the {@link MediaPeriod} for which
   *     playback will start. Will be {@link #EMPTY_MEDIA_PERIOD_ID} when {@code timeline} is empty.
   * @param bufferedDurationUs The duration of media that's currently buffered.
   * @param playbackSpeed The current factor by which playback is sped up.
   * @param rebuffering Whether the player is rebuffering. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action. Hence this parameter is false during initial
   *     buffering and when buffering as a result of a seek operation.
   * @param targetLiveOffsetUs The desired playback position offset to the live edge in
   *     microseconds, or {@link C#TIME_UNSET} if the media is not a live stream or no offset is
   *     configured.
   * @return Whether playback should be allowed to start or resume.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default boolean shouldStartPlayback(
      PlayerId playerId,
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      long bufferedDurationUs,
      float playbackSpeed,
      boolean rebuffering,
      long targetLiveOffsetUs) {
    return shouldStartPlayback(
        timeline,
        mediaPeriodId,
        bufferedDurationUs,
        playbackSpeed,
        rebuffering,
        targetLiveOffsetUs);
  }

  /**
   * @deprecated Implement {@link #shouldStartPlayback(PlayerId, Timeline, MediaPeriodId, long,
   *     float, boolean, long)} instead.
   */
  @Deprecated
  default boolean shouldStartPlayback(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      long bufferedDurationUs,
      float playbackSpeed,
      boolean rebuffering,
      long targetLiveOffsetUs) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("shouldStartPlayback not implemented");
  }

  /**
   * @deprecated Implement {@link #shouldStartPlayback(PlayerId, Timeline, MediaPeriodId, long,
   *     float, boolean, long)} instead.
   */
  @Deprecated
  default boolean shouldStartPlayback(
      long bufferedDurationUs, float playbackSpeed, boolean rebuffering, long targetLiveOffsetUs) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("shouldStartPlayback not implemented");
  }
}
