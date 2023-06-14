/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A {@link MediaSource} that filters the available {@linkplain C.TrackType track types}.
 *
 * <p>Media sources loading muxed media, e.g. progressive streams with muxed video and audio, are
 * still likely to parse all of these streams even if the tracks are not made available to the
 * player.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class FilteringMediaSource extends WrappingMediaSource {

  private final ImmutableSet<@C.TrackType Integer> trackTypes;

  /**
   * Creates a filtering {@link MediaSource} that only publishes tracks of one type.
   *
   * @param mediaSource The wrapped {@link MediaSource}.
   * @param trackType The only {@link C.TrackType} to provide from this source.
   */
  public FilteringMediaSource(MediaSource mediaSource, @C.TrackType int trackType) {
    this(mediaSource, ImmutableSet.of(trackType));
  }

  /**
   * Creates a filtering {@link MediaSource} that only publishes tracks of the given types.
   *
   * @param mediaSource The wrapped {@link MediaSource}.
   * @param trackTypes The {@linkplain C.TrackType track types} to provide from this source.
   */
  public FilteringMediaSource(MediaSource mediaSource, Set<@C.TrackType Integer> trackTypes) {
    super(mediaSource);
    this.trackTypes = ImmutableSet.copyOf(trackTypes);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaPeriod wrappedPeriod = super.createPeriod(id, allocator, startPositionUs);
    return new FilteringMediaPeriod(wrappedPeriod, trackTypes);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaPeriod wrappedPeriod = ((FilteringMediaPeriod) mediaPeriod).mediaPeriod;
    super.releasePeriod(wrappedPeriod);
  }

  private static final class FilteringMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

    public final MediaPeriod mediaPeriod;

    private final ImmutableSet<@C.TrackType Integer> trackTypes;

    @Nullable private Callback callback;
    @Nullable private TrackGroupArray filteredTrackGroups;

    public FilteringMediaPeriod(
        MediaPeriod mediaPeriod, ImmutableSet<@C.TrackType Integer> trackTypes) {
      this.mediaPeriod = mediaPeriod;
      this.trackTypes = trackTypes;
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
      this.callback = callback;
      mediaPeriod.prepare(/* callback= */ this, positionUs);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
      mediaPeriod.maybeThrowPrepareError();
    }

    @Override
    public TrackGroupArray getTrackGroups() {
      return checkNotNull(filteredTrackGroups);
    }

    @Override
    public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
      return mediaPeriod.getStreamKeys(trackSelections);
    }

    @Override
    public long selectTracks(
        @NullableType ExoTrackSelection[] selections,
        boolean[] mayRetainStreamFlags,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long positionUs) {
      return mediaPeriod.selectTracks(
          selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {
      mediaPeriod.discardBuffer(positionUs, toKeyframe);
    }

    @Override
    public long readDiscontinuity() {
      return mediaPeriod.readDiscontinuity();
    }

    @Override
    public long seekToUs(long positionUs) {
      return mediaPeriod.seekToUs(positionUs);
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
      return mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters);
    }

    @Override
    public long getBufferedPositionUs() {
      return mediaPeriod.getBufferedPositionUs();
    }

    @Override
    public long getNextLoadPositionUs() {
      return mediaPeriod.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return mediaPeriod.continueLoading(positionUs);
    }

    @Override
    public boolean isLoading() {
      return mediaPeriod.isLoading();
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
      mediaPeriod.reevaluateBuffer(positionUs);
    }

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      TrackGroupArray trackGroups = mediaPeriod.getTrackGroups();
      ImmutableList.Builder<TrackGroup> trackGroupsBuilder = ImmutableList.builder();
      for (int i = 0; i < trackGroups.length; i++) {
        TrackGroup trackGroup = trackGroups.get(i);
        if (trackTypes.contains(trackGroup.type)) {
          trackGroupsBuilder.add(trackGroup);
        }
      }
      filteredTrackGroups =
          new TrackGroupArray(trackGroupsBuilder.build().toArray(new TrackGroup[0]));
      checkNotNull(callback).onPrepared(/* mediaPeriod= */ this);
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
      checkNotNull(callback).onContinueLoadingRequested(/* source= */ this);
    }
  }
}
