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
package androidx.media3.exoplayer.source;

import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.IOException;

/**
 * Defines and provides media to be played by an {@link ExoPlayer}. A MediaSource has two main
 * responsibilities:
 *
 * <ul>
 *   <li>To provide the player with a {@link Timeline} defining the structure of its media, and to
 *       provide a new timeline whenever the structure of the media changes. The MediaSource
 *       provides these timelines by calling {@link MediaSourceCaller#onSourceInfoRefreshed} on the
 *       {@link MediaSourceCaller}s passed to {@link #prepareSource(MediaSourceCaller,
 *       TransferListener, PlayerId)}.
 *   <li>To provide {@link MediaPeriod} instances for the periods in its timeline. MediaPeriods are
 *       obtained by calling {@link #createPeriod(MediaPeriodId, Allocator, long)}, and provide a
 *       way for the player to load and read the media.
 * </ul>
 *
 * <p>{@code MediaSource} methods should not be called from application code. Instead, the playback
 * logic in {@link ExoPlayer} will trigger methods at the right time.
 *
 * <p>Instances can be re-used, but only for one {@link ExoPlayer} instance simultaneously.
 *
 * <p>MediaSource methods will be called on one of two threads, an application thread or a playback
 * thread. Each method is documented with the thread it is called on.
 */
public interface MediaSource {

  /** Factory for creating {@link MediaSource MediaSources} from {@link MediaItem MediaItems}. */
  interface Factory {

    /**
     * An instance that throws {@link UnsupportedOperationException} from {@link #createMediaSource}
     * and {@link #getSupportedTypes()}.
     */
    @UnstableApi
    @SuppressWarnings("deprecation")
    Factory UNSUPPORTED = MediaSourceFactory.UNSUPPORTED;

    /**
     * Sets the {@link CmcdConfiguration.Factory} used to obtain a {@link CmcdConfiguration} for a
     * {@link MediaItem}.
     *
     * @return This factory, for convenience.
     */
    @UnstableApi
    default Factory setCmcdConfigurationFactory(
        CmcdConfiguration.Factory cmcdConfigurationFactory) {
      // do nothing
      return this;
    }

    /**
     * Sets the {@link DrmSessionManagerProvider} used to obtain a {@link DrmSessionManager} for a
     * {@link MediaItem}.
     *
     * @return This factory, for convenience.
     */
    @UnstableApi
    Factory setDrmSessionManagerProvider(DrmSessionManagerProvider drmSessionManagerProvider);

    /**
     * Sets an optional {@link LoadErrorHandlingPolicy}.
     *
     * @return This factory, for convenience.
     */
    @UnstableApi
    Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy);

    /**
     * Sets whether subtitles should be parsed as part of extraction (before being added to the
     * sample queue) or as part of rendering (when being taken from the sample queue). Defaults to
     * {@code false} (i.e. subtitles will be parsed as part of rendering).
     *
     * <p>This method is experimental and will be renamed or removed in a future release.
     *
     * @param parseSubtitlesDuringExtraction Whether to parse subtitles during extraction or
     *     rendering.
     * @return This factory, for convenience.
     */
    // TODO: b/289916598 - Flip the default of this to true.
    @UnstableApi
    default Factory experimentalParseSubtitlesDuringExtraction(
        boolean parseSubtitlesDuringExtraction) {
      return this;
    }

    /**
     * Sets the {@link SubtitleParser.Factory} to be used for parsing subtitles during extraction if
     * {@link #experimentalParseSubtitlesDuringExtraction} is enabled.
     *
     * @param subtitleParserFactory The {@link SubtitleParser.Factory} for parsing subtitles during
     *     extraction.
     * @return This factory, for convenience.
     */
    @UnstableApi
    default Factory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
      return this;
    }

    /**
     * Returns the {@link C.ContentType content types} supported by media sources created by this
     * factory.
     */
    @UnstableApi
    @C.ContentType
    int[] getSupportedTypes();

    /**
     * Creates a new {@link MediaSource} with the specified {@link MediaItem}.
     *
     * @param mediaItem The media item to play.
     * @return The new {@link MediaSource media source}.
     */
    @UnstableApi
    MediaSource createMediaSource(MediaItem mediaItem);
  }

  /** A caller of media sources, which will be notified of source events. */
  @UnstableApi
  interface MediaSourceCaller {

    /**
     * Called when the {@link Timeline} has been refreshed.
     *
     * <p>Called on the playback thread.
     *
     * @param source The {@link MediaSource} whose info has been refreshed.
     * @param timeline The source's timeline.
     */
    void onSourceInfoRefreshed(MediaSource source, Timeline timeline);
  }

  /** Identifier for a {@link MediaPeriod}. */
  @UnstableApi
  final class MediaPeriodId {

    /** The unique id of the timeline period. */
    public final Object periodUid;

    /**
     * If the media period is in an ad group, the index of the ad group in the period. {@link
     * C#INDEX_UNSET} otherwise.
     */
    public final int adGroupIndex;

    /**
     * If the media period is in an ad group, the index of the ad in its ad group in the period.
     * {@link C#INDEX_UNSET} otherwise.
     */
    public final int adIndexInAdGroup;

    /**
     * The sequence number of the window in the buffered sequence of windows this media period is
     * part of. {@link C#INDEX_UNSET} if the media period id is not part of a buffered sequence of
     * windows.
     */
    public final long windowSequenceNumber;

    /**
     * The index of the next ad group to which the media period's content is clipped, or {@link
     * C#INDEX_UNSET} if there is no following ad group or if this media period is an ad.
     */
    public final int nextAdGroupIndex;

    /**
     * Creates a media period identifier for a period which is not part of a buffered sequence of
     * windows.
     *
     * @param periodUid The unique id of the timeline period.
     */
    public MediaPeriodId(Object periodUid) {
      this(periodUid, /* windowSequenceNumber= */ C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier for the specified period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber) {
      this(
          periodUid,
          /* adGroupIndex= */ C.INDEX_UNSET,
          /* adIndexInAdGroup= */ C.INDEX_UNSET,
          windowSequenceNumber,
          /* nextAdGroupIndex= */ C.INDEX_UNSET);
    }

    /**
     * Creates a media period identifier for the specified clipped period in the timeline.
     *
     * @param periodUid The unique id of the timeline period.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     * @param nextAdGroupIndex The index of the next ad group to which the media period's content is
     *     clipped.
     */
    public MediaPeriodId(Object periodUid, long windowSequenceNumber, int nextAdGroupIndex) {
      this(
          periodUid,
          /* adGroupIndex= */ C.INDEX_UNSET,
          /* adIndexInAdGroup= */ C.INDEX_UNSET,
          windowSequenceNumber,
          nextAdGroupIndex);
    }

    /**
     * Creates a media period identifier that identifies an ad within an ad group at the specified
     * timeline period.
     *
     * @param periodUid The unique id of the timeline period that contains the ad group.
     * @param adGroupIndex The index of the ad group.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
     *     windows this media period is part of.
     */
    public MediaPeriodId(
        Object periodUid, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber) {
      this(
          periodUid,
          adGroupIndex,
          adIndexInAdGroup,
          windowSequenceNumber,
          /* nextAdGroupIndex= */ C.INDEX_UNSET);
    }

    private MediaPeriodId(
        Object periodUid,
        int adGroupIndex,
        int adIndexInAdGroup,
        long windowSequenceNumber,
        int nextAdGroupIndex) {
      this.periodUid = periodUid;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
      this.windowSequenceNumber = windowSequenceNumber;
      this.nextAdGroupIndex = nextAdGroupIndex;
    }

    /** Returns a copy of this period identifier but with {@code newPeriodUid} as its period uid. */
    public MediaPeriodId copyWithPeriodUid(Object newPeriodUid) {
      return periodUid.equals(newPeriodUid)
          ? this
          : new MediaPeriodId(
              newPeriodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, nextAdGroupIndex);
    }

    /** Returns a copy of this period identifier with a new {@code windowSequenceNumber}. */
    public MediaPeriodId copyWithWindowSequenceNumber(long windowSequenceNumber) {
      return this.windowSequenceNumber == windowSequenceNumber
          ? this
          : new MediaPeriodId(
              periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, nextAdGroupIndex);
    }

    /** Returns whether this period identifier identifies an ad in an ad group in a period. */
    public boolean isAd() {
      return adGroupIndex != C.INDEX_UNSET;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof MediaPeriodId)) {
        return false;
      }

      MediaPeriodId periodId = (MediaPeriodId) obj;
      return periodUid.equals(periodId.periodUid)
          && adGroupIndex == periodId.adGroupIndex
          && adIndexInAdGroup == periodId.adIndexInAdGroup
          && windowSequenceNumber == periodId.windowSequenceNumber
          && nextAdGroupIndex == periodId.nextAdGroupIndex;
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + periodUid.hashCode();
      result = 31 * result + adGroupIndex;
      result = 31 * result + adIndexInAdGroup;
      result = 31 * result + (int) windowSequenceNumber;
      result = 31 * result + nextAdGroupIndex;
      return result;
    }
  }

  /**
   * Adds a {@link MediaSourceEventListener} to the list of listeners which are notified of media
   * source events.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  @UnstableApi
  void addEventListener(Handler handler, MediaSourceEventListener eventListener);

  /**
   * Removes a {@link MediaSourceEventListener} from the list of listeners which are notified of
   * media source events.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param eventListener The listener to be removed.
   */
  @UnstableApi
  void removeEventListener(MediaSourceEventListener eventListener);

  /**
   * Adds a {@link DrmSessionEventListener} to the list of listeners which are notified of DRM
   * events for this media source.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param handler A handler on the which listener events will be posted.
   * @param eventListener The listener to be added.
   */
  @UnstableApi
  void addDrmEventListener(Handler handler, DrmSessionEventListener eventListener);

  /**
   * Removes a {@link DrmSessionEventListener} from the list of listeners which are notified of DRM
   * events for this media source.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param eventListener The listener to be removed.
   */
  @UnstableApi
  void removeDrmEventListener(DrmSessionEventListener eventListener);

  /**
   * Returns the initial placeholder timeline that is returned immediately when the real timeline is
   * not yet known, or null to let the player create an initial timeline.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>The initial timeline must use the same uids for windows and periods that the real timeline
   * will use. It also must provide windows which are marked as dynamic to indicate that the window
   * is expected to change when the real timeline arrives.
   *
   * <p>Any media source which has multiple windows should typically provide such an initial
   * timeline to make sure the player reports the correct number of windows immediately.
   *
   * <p>This method must be called on the application thread.
   */
  @UnstableApi
  @Nullable
  default Timeline getInitialTimeline() {
    return null;
  }

  /**
   * Returns true if the media source is guaranteed to never have zero or more than one window.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>The default implementation returns {@code true}.
   *
   * <p>This method must be called on the application thread.
   *
   * @return true if the source has exactly one window.
   */
  @UnstableApi
  default boolean isSingleWindow() {
    return true;
  }

  /**
   * Returns the {@link MediaItem} whose media is provided by the source.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the application thread.
   */
  @UnstableApi
  MediaItem getMediaItem();

  /**
   * Returns whether the {@link MediaItem} for this source can be updated with the provided item.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the application thread.
   *
   * @param mediaItem The new {@link MediaItem}.
   * @return Whether the source can be updated using this item.
   */
  @UnstableApi
  default boolean canUpdateMediaItem(MediaItem mediaItem) {
    return false;
  }

  /**
   * Updates the {@link MediaItem} for this source.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only if {@link #canUpdateMediaItem}
   * returns {@code true} for the new {@link MediaItem}.
   *
   * @param mediaItem The new {@link MediaItem}.
   */
  @UnstableApi
  default void updateMediaItem(MediaItem mediaItem) {}

  /**
   * @deprecated Implement {@link #prepareSource(MediaSourceCaller, TransferListener, PlayerId)}
   *     instead.
   */
  @UnstableApi
  @Deprecated
  default void prepareSource(
      MediaSourceCaller caller, @Nullable TransferListener mediaTransferListener) {
    prepareSource(caller, mediaTransferListener, PlayerId.UNSET);
  }

  /**
   * Registers a {@link MediaSourceCaller}. Starts source preparation if needed and enables the
   * source for the creation of {@link MediaPeriod MediaPerods}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>{@link MediaSourceCaller#onSourceInfoRefreshed(MediaSource, Timeline)} will be called once
   * the source has a {@link Timeline}.
   *
   * <p>For each call to this method, a call to {@link #releaseSource(MediaSourceCaller)} is needed
   * to remove the caller and to release the source if no longer required.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param caller The {@link MediaSourceCaller} to be registered.
   * @param mediaTransferListener The transfer listener which should be informed of any media data
   *     transfers. May be null if no listener is available. Note that this listener should be only
   *     informed of transfers related to the media loads and not of auxiliary loads for manifests
   *     and other data.
   * @param playerId The {@link PlayerId} of the player using this media source.
   */
  @UnstableApi
  void prepareSource(
      MediaSourceCaller caller,
      @Nullable TransferListener mediaTransferListener,
      PlayerId playerId);

  /**
   * Throws any pending error encountered while loading or refreshing source information.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only after {@link
   * #prepareSource(MediaSourceCaller, TransferListener, PlayerId)}.
   */
  @UnstableApi
  void maybeThrowSourceInfoRefreshError() throws IOException;

  /**
   * Enables the source for the creation of {@link MediaPeriod MediaPeriods}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only after {@link
   * #prepareSource(MediaSourceCaller, TransferListener, PlayerId)}.
   *
   * @param caller The {@link MediaSourceCaller} enabling the source.
   */
  @UnstableApi
  void enable(MediaSourceCaller caller);

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only if the source is enabled.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param startPositionUs The expected start position, in microseconds.
   * @return A new {@link MediaPeriod}.
   */
  @UnstableApi
  MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs);

  /**
   * Releases the period.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread.
   *
   * @param mediaPeriod The period to release.
   */
  @UnstableApi
  void releasePeriod(MediaPeriod mediaPeriod);

  /**
   * Disables the source for the creation of {@link MediaPeriod MediaPeriods}. The implementation
   * should not hold onto limited resources used for the creation of media periods.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only after all {@link MediaPeriod
   * MediaPeriods} previously created by {@link #createPeriod(MediaPeriodId, Allocator, long)} have
   * been released by {@link #releasePeriod(MediaPeriod)}.
   *
   * @param caller The {@link MediaSourceCaller} disabling the source.
   */
  @UnstableApi
  void disable(MediaSourceCaller caller);

  /**
   * Unregisters a caller, and disables and releases the source if no longer required.
   *
   * <p>Should not be called directly from application code.
   *
   * <p>This method must be called on the playback thread and only if all created {@link MediaPeriod
   * MediaPeriods} have been released by {@link #releasePeriod(MediaPeriod)}.
   *
   * @param caller The {@link MediaSourceCaller} to be unregistered.
   */
  @UnstableApi
  void releaseSource(MediaSourceCaller caller);
}
