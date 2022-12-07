/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player.Commands;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.SimpleBasePlayer.State;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.FakeMetadataEntry;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.FlagSet;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link SimpleBasePlayer}. */
@RunWith(AndroidJUnit4.class)
public class SimpleBasePlayerTest {

  @Test
  public void allPlayerInterfaceMethods_declaredFinal() throws Exception {
    for (Method method : Player.class.getDeclaredMethods()) {
      assertThat(
              SimpleBasePlayer.class
                      .getMethod(method.getName(), method.getParameterTypes())
                      .getModifiers()
                  & Modifier.FINAL)
          .isNotEqualTo(0);
    }
  }

  @Test
  public void stateBuildUpon_build_isEqual() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(
                new PlaybackException(
                    /* message= */ null,
                    /* cause= */ null,
                    PlaybackException.ERROR_CODE_DECODING_FAILED))
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f))
            .setTrackSelectionParameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
            .setAudioAttributes(
                new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build())
            .setVolume(0.5f)
            .setVideoSize(new VideoSize(/* width= */ 200, /* height= */ 400))
            .setCurrentCues(
                new CueGroup(
                    ImmutableList.of(new Cue.Builder().setText("text").build()),
                    /* presentationTimeUs= */ 123))
            .setDeviceInfo(
                new DeviceInfo(
                    DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 3, /* maxVolume= */ 7))
            .setIsDeviceMuted(true)
            .setSurfaceSize(new Size(480, 360))
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(new Metadata())
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                            /* adsId= */ new Object(),
                                            /* adGroupTimesUs...= */ 555,
                                            666))
                                    .build()))
                        .build()))
            .setPlaylistMetadata(new MediaMetadata.Builder().setArtist("artist").build())
            .setCurrentMediaItemIndex(1)
            .setCurrentPeriodIndex(1)
            .setCurrentAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2)
            .setContentPositionMs(() -> 456)
            .setAdPositionMs(() -> 6678)
            .setContentBufferedPositionMs(() -> 999)
            .setAdBufferedPositionMs(() -> 888)
            .setTotalBufferedDurationMs(() -> 567)
            .setPositionDiscontinuity(
                Player.DISCONTINUITY_REASON_SEEK, /* discontinuityPositionMs= */ 400)
            .build();

    State newState = state.buildUpon().build();

    assertThat(newState).isEqualTo(state);
    assertThat(newState.hashCode()).isEqualTo(state.hashCode());
  }

  @Test
  public void mediaItemDataBuildUpon_build_isEqual() {
    SimpleBasePlayer.MediaItemData mediaItemData =
        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
            .setTracks(
                new Tracks(
                    ImmutableList.of(
                        new Tracks.Group(
                            new TrackGroup(new Format.Builder().build()),
                            /* adaptiveSupported= */ true,
                            /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                            /* trackSelected= */ new boolean[] {true}))))
            .setMediaItem(new MediaItem.Builder().setMediaId("id").build())
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setManifest(new Object())
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build())
            .setPresentationStartTimeMs(12)
            .setWindowStartTimeMs(23)
            .setElapsedRealtimeEpochOffsetMs(10234)
            .setIsSeekable(true)
            .setIsDynamic(true)
            .setDefaultPositionUs(456_789)
            .setDurationUs(500_000)
            .setPositionInFirstPeriodUs(100_000)
            .setIsPlaceholder(true)
            .setPeriods(
                ImmutableList.of(
                    new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object()).build()))
            .build();

    SimpleBasePlayer.MediaItemData newMediaItemData = mediaItemData.buildUpon().build();

    assertThat(newMediaItemData).isEqualTo(mediaItemData);
    assertThat(newMediaItemData.hashCode()).isEqualTo(mediaItemData.hashCode());
  }

  @Test
  public void periodDataBuildUpon_build_isEqual() {
    SimpleBasePlayer.PeriodData periodData =
        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
            .setIsPlaceholder(true)
            .setDurationUs(600_000)
            .setAdPlaybackState(
                new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 555, 666))
            .build();

    SimpleBasePlayer.PeriodData newPeriodData = periodData.buildUpon().build();

    assertThat(newPeriodData).isEqualTo(periodData);
    assertThat(newPeriodData.hashCode()).isEqualTo(periodData.hashCode());
  }

  @Test
  public void stateBuilderBuild_setsCorrectValues() {
    Commands commands =
        new Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    PlaybackException error =
        new PlaybackException(
            /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 2f);
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            .setMaxVideoBitrate(1000)
            .build();
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
    VideoSize videoSize = new VideoSize(/* width= */ 200, /* height= */ 400);
    CueGroup cueGroup =
        new CueGroup(
            ImmutableList.of(new Cue.Builder().setText("text").build()),
            /* presentationTimeUs= */ 123);
    Metadata timedMetadata = new Metadata(new FakeMetadataEntry("data"));
    Size surfaceSize = new Size(480, 360);
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 3, /* maxVolume= */ 7);
    ImmutableList<SimpleBasePlayer.MediaItemData> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                    /* adsId= */ new Object(), /* adGroupTimesUs...= */ 555, 666))
                            .build()))
                .build());
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    SimpleBasePlayer.PositionSupplier contentPositionSupplier = () -> 456;
    SimpleBasePlayer.PositionSupplier adPositionSupplier = () -> 6678;
    SimpleBasePlayer.PositionSupplier contentBufferedPositionSupplier = () -> 999;
    SimpleBasePlayer.PositionSupplier adBufferedPositionSupplier = () -> 888;
    SimpleBasePlayer.PositionSupplier totalBufferedPositionSupplier = () -> 567;

    State state =
        new State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(error)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setAudioAttributes(audioAttributes)
            .setVolume(0.5f)
            .setVideoSize(videoSize)
            .setCurrentCues(cueGroup)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(5)
            .setIsDeviceMuted(true)
            .setSurfaceSize(surfaceSize)
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(timedMetadata)
            .setPlaylist(playlist)
            .setPlaylistMetadata(playlistMetadata)
            .setCurrentMediaItemIndex(1)
            .setCurrentPeriodIndex(1)
            .setCurrentAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2)
            .setContentPositionMs(contentPositionSupplier)
            .setAdPositionMs(adPositionSupplier)
            .setContentBufferedPositionMs(contentBufferedPositionSupplier)
            .setAdBufferedPositionMs(adBufferedPositionSupplier)
            .setTotalBufferedDurationMs(totalBufferedPositionSupplier)
            .setPositionDiscontinuity(
                Player.DISCONTINUITY_REASON_SEEK, /* discontinuityPositionMs= */ 400)
            .build();

    assertThat(state.availableCommands).isEqualTo(commands);
    assertThat(state.playWhenReady).isTrue();
    assertThat(state.playWhenReadyChangeReason)
        .isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
    assertThat(state.playbackState).isEqualTo(Player.STATE_IDLE);
    assertThat(state.playbackSuppressionReason)
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    assertThat(state.playerError).isEqualTo(error);
    assertThat(state.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL);
    assertThat(state.shuffleModeEnabled).isTrue();
    assertThat(state.isLoading).isFalse();
    assertThat(state.seekBackIncrementMs).isEqualTo(5000);
    assertThat(state.seekForwardIncrementMs).isEqualTo(4000);
    assertThat(state.maxSeekToPreviousPositionMs).isEqualTo(3000);
    assertThat(state.playbackParameters).isEqualTo(playbackParameters);
    assertThat(state.trackSelectionParameters).isEqualTo(trackSelectionParameters);
    assertThat(state.audioAttributes).isEqualTo(audioAttributes);
    assertThat(state.volume).isEqualTo(0.5f);
    assertThat(state.videoSize).isEqualTo(videoSize);
    assertThat(state.currentCues).isEqualTo(cueGroup);
    assertThat(state.deviceInfo).isEqualTo(deviceInfo);
    assertThat(state.deviceVolume).isEqualTo(5);
    assertThat(state.isDeviceMuted).isTrue();
    assertThat(state.surfaceSize).isEqualTo(surfaceSize);
    assertThat(state.newlyRenderedFirstFrame).isTrue();
    assertThat(state.timedMetadata).isEqualTo(timedMetadata);
    assertThat(state.playlist).isEqualTo(playlist);
    assertThat(state.playlistMetadata).isEqualTo(playlistMetadata);
    assertThat(state.currentMediaItemIndex).isEqualTo(1);
    assertThat(state.currentPeriodIndex).isEqualTo(1);
    assertThat(state.currentAdGroupIndex).isEqualTo(1);
    assertThat(state.currentAdIndexInAdGroup).isEqualTo(2);
    assertThat(state.contentPositionMsSupplier).isEqualTo(contentPositionSupplier);
    assertThat(state.adPositionMsSupplier).isEqualTo(adPositionSupplier);
    assertThat(state.contentBufferedPositionMsSupplier).isEqualTo(contentBufferedPositionSupplier);
    assertThat(state.adBufferedPositionMsSupplier).isEqualTo(adBufferedPositionSupplier);
    assertThat(state.totalBufferedDurationMsSupplier).isEqualTo(totalBufferedPositionSupplier);
    assertThat(state.hasPositionDiscontinuity).isTrue();
    assertThat(state.positionDiscontinuityReason).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);
    assertThat(state.discontinuityPositionMs).isEqualTo(400);
  }

  @Test
  public void stateBuilderBuild_emptyTimelineWithReadyState_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(ImmutableList.of())
                .setPlaybackState(Player.STATE_READY)
                .build());
  }

  @Test
  public void stateBuilderBuild_emptyTimelineWithBufferingState_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(ImmutableList.of())
                .setPlaybackState(Player.STATE_BUFFERING)
                .build());
  }

  @Test
  public void stateBuilderBuild_idleStateWithIsLoading_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaybackState(Player.STATE_IDLE)
                .setIsLoading(true)
                .build());
  }

  @Test
  public void stateBuilderBuild_currentWindowIndexExceedsPlaylistLength_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                            .build()))
                .setCurrentMediaItemIndex(2)
                .build());
  }

  @Test
  public void stateBuilderBuild_currentPeriodIndexExceedsPlaylistLength_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                            .build()))
                .setCurrentPeriodIndex(2)
                .build());
  }

  @Test
  public void stateBuilderBuild_currentPeriodIndexInOtherMediaItem_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                            .build()))
                .setCurrentMediaItemIndex(0)
                .setCurrentPeriodIndex(1)
                .build());
  }

  @Test
  public void stateBuilderBuild_currentAdGroupIndexExceedsAdGroupCount_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                            .setPeriods(
                                ImmutableList.of(
                                    new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                        .setAdPlaybackState(
                                            new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs...= */ 123))
                                        .build()))
                            .build()))
                .setCurrentAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 2)
                .build());
  }

  @Test
  public void stateBuilderBuild_currentAdIndexExceedsAdCountInAdGroup_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                            .setPeriods(
                                ImmutableList.of(
                                    new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                        .setAdPlaybackState(
                                            new AdPlaybackState(
                                                    /* adsId= */ new Object(),
                                                    /* adGroupTimesUs...= */ 123)
                                                .withAdCount(
                                                    /* adGroupIndex= */ 0, /* adCount= */ 2))
                                        .build()))
                            .build()))
                .setCurrentAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 2)
                .build());
  }

  @Test
  public void stateBuilderBuild_playerErrorInNonIdleState_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaybackState(Player.STATE_READY)
                .setPlayerError(
                    new PlaybackException(
                        /* message= */ null,
                        /* cause= */ null,
                        PlaybackException.ERROR_CODE_DECODING_FAILED))
                .build());
  }

  @Test
  public void stateBuilderBuild_multipleMediaItemsWithSameIds_throwsException() {
    Object uid = new Object();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.MediaItemData.Builder(uid).build(),
                        new SimpleBasePlayer.MediaItemData.Builder(uid).build()))
                .build());
  }

  @Test
  public void stateBuilderBuild_adGroupIndexWithUnsetAdIndex_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setCurrentAd(/* adGroupIndex= */ C.INDEX_UNSET, /* adIndexInAdGroup= */ 0));
  }

  @Test
  public void stateBuilderBuild_unsetAdGroupIndexWithSetAdIndex_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setCurrentAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ C.INDEX_UNSET));
  }

  @Test
  public void stateBuilderBuild_unsetAdGroupIndexAndAdIndex_doesNotThrow() {
    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setCurrentAd(/* adGroupIndex= */ C.INDEX_UNSET, /* adIndexInAdGroup= */ C.INDEX_UNSET)
            .build();

    assertThat(state.currentAdGroupIndex).isEqualTo(C.INDEX_UNSET);
    assertThat(state.currentAdIndexInAdGroup).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void stateBuilderBuild_returnsAdvancingContentPositionWhenPlaying() {
    SystemClock.setCurrentTimeMillis(10000);

    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .setContentPositionMs(4000)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f))
            .build();
    long position1 = state.contentPositionMsSupplier.get();
    SystemClock.setCurrentTimeMillis(12000);
    long position2 = state.contentPositionMsSupplier.get();

    assertThat(position1).isEqualTo(4000);
    assertThat(position2).isEqualTo(8000);
  }

  @Test
  public void stateBuilderBuild_returnsConstantContentPositionWhenNotPlaying() {
    SystemClock.setCurrentTimeMillis(10000);

    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .setContentPositionMs(4000)
            .setPlaybackState(Player.STATE_BUFFERING)
            .build();
    long position1 = state.contentPositionMsSupplier.get();
    SystemClock.setCurrentTimeMillis(12000);
    long position2 = state.contentPositionMsSupplier.get();

    assertThat(position1).isEqualTo(4000);
    assertThat(position2).isEqualTo(4000);
  }

  @Test
  public void stateBuilderBuild_returnsAdvancingAdPositionWhenPlaying() {
    SystemClock.setCurrentTimeMillis(10000);

    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs...= */ 123)
                                            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2))
                                    .build()))
                        .build()))
            .setCurrentAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1)
            .setAdPositionMs(4000)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            // This should be ignored as ads are assumed to be played with unit speed.
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f))
            .build();
    long position1 = state.adPositionMsSupplier.get();
    SystemClock.setCurrentTimeMillis(12000);
    long position2 = state.adPositionMsSupplier.get();

    assertThat(position1).isEqualTo(4000);
    assertThat(position2).isEqualTo(6000);
  }

  @Test
  public void stateBuilderBuild_returnsConstantAdPositionWhenNotPlaying() {
    SystemClock.setCurrentTimeMillis(10000);

    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs...= */ 123)
                                            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2))
                                    .build()))
                        .build()))
            .setCurrentAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1)
            .setAdPositionMs(4000)
            .setPlaybackState(Player.STATE_BUFFERING)
            .build();
    long position1 = state.adPositionMsSupplier.get();
    SystemClock.setCurrentTimeMillis(12000);
    long position2 = state.adPositionMsSupplier.get();

    assertThat(position1).isEqualTo(4000);
    assertThat(position2).isEqualTo(4000);
  }

  @Test
  public void mediaItemDataBuilderBuild_setsCorrectValues() {
    Object uid = new Object();
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().build()),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                    /* trackSelected= */ new boolean[] {true})));
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("id").build();
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Object manifest = new Object();
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build();
    ImmutableList<SimpleBasePlayer.PeriodData> periods =
        ImmutableList.of(new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object()).build());

    SimpleBasePlayer.MediaItemData mediaItemData =
        new SimpleBasePlayer.MediaItemData.Builder(uid)
            .setTracks(tracks)
            .setMediaItem(mediaItem)
            .setMediaMetadata(mediaMetadata)
            .setManifest(manifest)
            .setLiveConfiguration(liveConfiguration)
            .setPresentationStartTimeMs(12)
            .setWindowStartTimeMs(23)
            .setElapsedRealtimeEpochOffsetMs(10234)
            .setIsSeekable(true)
            .setIsDynamic(true)
            .setDefaultPositionUs(456_789)
            .setDurationUs(500_000)
            .setPositionInFirstPeriodUs(100_000)
            .setIsPlaceholder(true)
            .setPeriods(periods)
            .build();

    assertThat(mediaItemData.uid).isEqualTo(uid);
    assertThat(mediaItemData.tracks).isEqualTo(tracks);
    assertThat(mediaItemData.mediaItem).isEqualTo(mediaItem);
    assertThat(mediaItemData.mediaMetadata).isEqualTo(mediaMetadata);
    assertThat(mediaItemData.manifest).isEqualTo(manifest);
    assertThat(mediaItemData.liveConfiguration).isEqualTo(liveConfiguration);
    assertThat(mediaItemData.presentationStartTimeMs).isEqualTo(12);
    assertThat(mediaItemData.windowStartTimeMs).isEqualTo(23);
    assertThat(mediaItemData.elapsedRealtimeEpochOffsetMs).isEqualTo(10234);
    assertThat(mediaItemData.isSeekable).isTrue();
    assertThat(mediaItemData.isDynamic).isTrue();
    assertThat(mediaItemData.defaultPositionUs).isEqualTo(456_789);
    assertThat(mediaItemData.durationUs).isEqualTo(500_000);
    assertThat(mediaItemData.positionInFirstPeriodUs).isEqualTo(100_000);
    assertThat(mediaItemData.isPlaceholder).isTrue();
    assertThat(mediaItemData.periods).isEqualTo(periods);
  }

  @Test
  public void mediaItemDataBuilderBuild_presentationStartTimeIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setPresentationStartTimeMs(12)
                .build());
  }

  @Test
  public void mediaItemDataBuilderBuild_windowStartTimeIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setWindowStartTimeMs(12)
                .build());
  }

  @Test
  public void mediaItemDataBuilderBuild_elapsedEpochOffsetIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setElapsedRealtimeEpochOffsetMs(12)
                .build());
  }

  @Test
  public void
      mediaItemDataBuilderBuild_windowStartTimeLessThanPresentationStartTime_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
                .setWindowStartTimeMs(12)
                .setPresentationStartTimeMs(13)
                .build());
  }

  @Test
  public void mediaItemDataBuilderBuild_multiplePeriodsWithSameUid_throwsException() {
    Object uid = new Object();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(uid).build(),
                        new SimpleBasePlayer.PeriodData.Builder(uid).build()))
                .build());
  }

  @Test
  public void mediaItemDataBuilderBuild_defaultPositionGreaterThanDuration_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setDefaultPositionUs(16)
                .setDurationUs(15)
                .build());
  }

  @Test
  public void periodDataBuilderBuild_setsCorrectValues() {
    Object uid = new Object();
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 555, 666);

    SimpleBasePlayer.PeriodData periodData =
        new SimpleBasePlayer.PeriodData.Builder(uid)
            .setIsPlaceholder(true)
            .setDurationUs(600_000)
            .setAdPlaybackState(adPlaybackState)
            .build();

    assertThat(periodData.uid).isEqualTo(uid);
    assertThat(periodData.isPlaceholder).isTrue();
    assertThat(periodData.durationUs).isEqualTo(600_000);
    assertThat(periodData.adPlaybackState).isEqualTo(adPlaybackState);
  }

  @Test
  public void getterMethods_noOtherMethodCalls_returnCurrentState() {
    Commands commands =
        new Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    PlaybackException error =
        new PlaybackException(
            /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 2f);
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            .setMaxVideoBitrate(1000)
            .build();
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
    VideoSize videoSize = new VideoSize(/* width= */ 200, /* height= */ 400);
    CueGroup cueGroup =
        new CueGroup(
            ImmutableList.of(new Cue.Builder().setText("text").build()),
            /* presentationTimeUs= */ 123);
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 3, /* maxVolume= */ 7);
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    SimpleBasePlayer.PositionSupplier contentPositionSupplier = () -> 456;
    SimpleBasePlayer.PositionSupplier contentBufferedPositionSupplier = () -> 499;
    SimpleBasePlayer.PositionSupplier totalBufferedPositionSupplier = () -> 567;
    Object mediaItemUid = new Object();
    Object periodUid = new Object();
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().build()),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                    /* trackSelected= */ new boolean[] {true})));
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("id").build();
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Object manifest = new Object();
    Size surfaceSize = new Size(480, 360);
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build();
    ImmutableList<SimpleBasePlayer.MediaItemData> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid)
                .setTracks(tracks)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaMetadata)
                .setManifest(manifest)
                .setLiveConfiguration(liveConfiguration)
                .setPresentationStartTimeMs(12)
                .setWindowStartTimeMs(23)
                .setElapsedRealtimeEpochOffsetMs(10234)
                .setIsSeekable(true)
                .setIsDynamic(true)
                .setDefaultPositionUs(456_789)
                .setDurationUs(500_000)
                .setPositionInFirstPeriodUs(100_000)
                .setIsPlaceholder(true)
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(periodUid)
                            .setIsPlaceholder(true)
                            .setDurationUs(600_000)
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                    /* adsId= */ new Object(), /* adGroupTimesUs...= */ 555, 666))
                            .build()))
                .build());
    State state =
        new State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(error)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setAudioAttributes(audioAttributes)
            .setVolume(0.5f)
            .setVideoSize(videoSize)
            .setCurrentCues(cueGroup)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(5)
            .setIsDeviceMuted(true)
            .setSurfaceSize(surfaceSize)
            .setPlaylist(playlist)
            .setPlaylistMetadata(playlistMetadata)
            .setCurrentMediaItemIndex(1)
            .setCurrentPeriodIndex(1)
            .setContentPositionMs(contentPositionSupplier)
            .setContentBufferedPositionMs(contentBufferedPositionSupplier)
            .setTotalBufferedDurationMs(totalBufferedPositionSupplier)
            .build();

    Player player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }
        };

    assertThat(player.getApplicationLooper()).isEqualTo(Looper.myLooper());
    assertThat(player.getAvailableCommands()).isEqualTo(commands);
    assertThat(player.getPlayWhenReady()).isTrue();
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    assertThat(player.getPlaybackSuppressionReason())
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    assertThat(player.getPlayerError()).isEqualTo(error);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    assertThat(player.getShuffleModeEnabled()).isTrue();
    assertThat(player.isLoading()).isFalse();
    assertThat(player.getSeekBackIncrement()).isEqualTo(5000);
    assertThat(player.getSeekForwardIncrement()).isEqualTo(4000);
    assertThat(player.getMaxSeekToPreviousPosition()).isEqualTo(3000);
    assertThat(player.getPlaybackParameters()).isEqualTo(playbackParameters);
    assertThat(player.getCurrentTracks()).isEqualTo(tracks);
    assertThat(player.getTrackSelectionParameters()).isEqualTo(trackSelectionParameters);
    assertThat(player.getMediaMetadata()).isEqualTo(mediaMetadata);
    assertThat(player.getPlaylistMetadata()).isEqualTo(playlistMetadata);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
    assertThat(player.getDuration()).isEqualTo(500);
    assertThat(player.getCurrentPosition()).isEqualTo(456);
    assertThat(player.getBufferedPosition()).isEqualTo(499);
    assertThat(player.getTotalBufferedDuration()).isEqualTo(567);
    assertThat(player.isPlayingAd()).isFalse();
    assertThat(player.getCurrentAdGroupIndex()).isEqualTo(C.INDEX_UNSET);
    assertThat(player.getCurrentAdIndexInAdGroup()).isEqualTo(C.INDEX_UNSET);
    assertThat(player.getContentPosition()).isEqualTo(456);
    assertThat(player.getContentBufferedPosition()).isEqualTo(499);
    assertThat(player.getAudioAttributes()).isEqualTo(audioAttributes);
    assertThat(player.getVolume()).isEqualTo(0.5f);
    assertThat(player.getVideoSize()).isEqualTo(videoSize);
    assertThat(player.getCurrentCues()).isEqualTo(cueGroup);
    assertThat(player.getDeviceInfo()).isEqualTo(deviceInfo);
    assertThat(player.getDeviceVolume()).isEqualTo(5);
    assertThat(player.isDeviceMuted()).isTrue();
    assertThat(player.getSurfaceSize()).isEqualTo(surfaceSize);
    Timeline timeline = player.getCurrentTimeline();
    assertThat(timeline.getPeriodCount()).isEqualTo(2);
    assertThat(timeline.getWindowCount()).isEqualTo(2);
    Timeline.Window window = timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
    assertThat(window.defaultPositionUs).isEqualTo(0);
    assertThat(window.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(window.elapsedRealtimeEpochOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.firstPeriodIndex).isEqualTo(0);
    assertThat(window.isDynamic).isFalse();
    assertThat(window.isPlaceholder).isFalse();
    assertThat(window.isSeekable).isFalse();
    assertThat(window.lastPeriodIndex).isEqualTo(0);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(0);
    assertThat(window.presentationStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.windowStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.liveConfiguration).isNull();
    assertThat(window.manifest).isNull();
    assertThat(window.mediaItem).isEqualTo(MediaItem.EMPTY);
    window = timeline.getWindow(/* windowIndex= */ 1, new Timeline.Window());
    assertThat(window.defaultPositionUs).isEqualTo(456_789);
    assertThat(window.durationUs).isEqualTo(500_000);
    assertThat(window.elapsedRealtimeEpochOffsetMs).isEqualTo(10234);
    assertThat(window.firstPeriodIndex).isEqualTo(1);
    assertThat(window.isDynamic).isTrue();
    assertThat(window.isPlaceholder).isTrue();
    assertThat(window.isSeekable).isTrue();
    assertThat(window.lastPeriodIndex).isEqualTo(1);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(100_000);
    assertThat(window.presentationStartTimeMs).isEqualTo(12);
    assertThat(window.windowStartTimeMs).isEqualTo(23);
    assertThat(window.liveConfiguration).isEqualTo(liveConfiguration);
    assertThat(window.manifest).isEqualTo(manifest);
    assertThat(window.mediaItem).isEqualTo(mediaItem);
    assertThat(window.uid).isEqualTo(mediaItemUid);
    Timeline.Period period =
        timeline.getPeriod(/* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true);
    assertThat(period.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(period.isPlaceholder).isFalse();
    assertThat(period.positionInWindowUs).isEqualTo(0);
    assertThat(period.windowIndex).isEqualTo(0);
    assertThat(period.getAdGroupCount()).isEqualTo(0);
    period = timeline.getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true);
    assertThat(period.durationUs).isEqualTo(600_000);
    assertThat(period.isPlaceholder).isTrue();
    assertThat(period.positionInWindowUs).isEqualTo(-100_000);
    assertThat(period.windowIndex).isEqualTo(1);
    assertThat(period.id).isEqualTo(periodUid);
    assertThat(period.getAdGroupCount()).isEqualTo(2);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(555);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 1)).isEqualTo(666);
  }

  @Test
  public void getterMethods_duringAd_returnAdState() {
    SimpleBasePlayer.PositionSupplier contentPositionSupplier = () -> 456;
    SimpleBasePlayer.PositionSupplier contentBufferedPositionSupplier = () -> 499;
    SimpleBasePlayer.PositionSupplier totalBufferedPositionSupplier = () -> 567;
    SimpleBasePlayer.PositionSupplier adPositionSupplier = () -> 321;
    SimpleBasePlayer.PositionSupplier adBufferedPositionSupplier = () -> 345;
    ImmutableList<SimpleBasePlayer.MediaItemData> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object())
                .setDurationUs(500_000)
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                            .setIsPlaceholder(true)
                            .setDurationUs(600_000)
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                        /* adsId= */ new Object(), /* adGroupTimesUs...= */
                                        555,
                                        666)
                                    .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                                    .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
                                    .withAdDurationsUs(
                                        /* adGroupIndex= */ 0, /* adDurationsUs... */ 700_000)
                                    .withAdDurationsUs(
                                        /* adGroupIndex= */ 1, /* adDurationsUs... */ 800_000))
                            .build()))
                .build());
    State state =
        new State.Builder()
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(1)
            .setCurrentAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)
            .setContentPositionMs(contentPositionSupplier)
            .setContentBufferedPositionMs(contentBufferedPositionSupplier)
            .setTotalBufferedDurationMs(totalBufferedPositionSupplier)
            .setAdPositionMs(adPositionSupplier)
            .setAdBufferedPositionMs(adBufferedPositionSupplier)
            .build();

    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }
        };

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(1);
    assertThat(player.getDuration()).isEqualTo(800);
    assertThat(player.getCurrentPosition()).isEqualTo(321);
    assertThat(player.getBufferedPosition()).isEqualTo(345);
    assertThat(player.getTotalBufferedDuration()).isEqualTo(567);
    assertThat(player.isPlayingAd()).isTrue();
    assertThat(player.getCurrentAdGroupIndex()).isEqualTo(1);
    assertThat(player.getCurrentAdIndexInAdGroup()).isEqualTo(0);
    assertThat(player.getContentPosition()).isEqualTo(456);
    assertThat(player.getContentBufferedPosition()).isEqualTo(499);
  }

  @Test
  public void getterMethods_withEmptyTimeline_returnPlaceholderValues() {
    State state = new State.Builder().setCurrentMediaItemIndex(4).build();

    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }
        };

    assertThat(player.getCurrentTracks()).isEqualTo(Tracks.EMPTY);
    assertThat(player.getMediaMetadata()).isEqualTo(MediaMetadata.EMPTY);
    assertThat(player.getCurrentPeriodIndex()).isEqualTo(4);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(4);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void invalidateState_updatesStateAndInformsListeners() throws Exception {
    Object mediaItemUid0 = new Object();
    MediaItem mediaItem0 = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0).setMediaItem(mediaItem0).build();
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_READY)
            .setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setPlayerError(null)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .setShuffleModeEnabled(false)
            .setIsLoading(true)
            .setSeekBackIncrementMs(7000)
            .setSeekForwardIncrementMs(2000)
            .setMaxSeekToPreviousPositionMs(8000)
            .setPlaybackParameters(PlaybackParameters.DEFAULT)
            .setTrackSelectionParameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
            .setAudioAttributes(AudioAttributes.DEFAULT)
            .setVolume(1f)
            .setVideoSize(VideoSize.UNKNOWN)
            .setCurrentCues(CueGroup.EMPTY_TIME_ZERO)
            .setDeviceInfo(DeviceInfo.UNKNOWN)
            .setDeviceVolume(0)
            .setIsDeviceMuted(false)
            .setPlaylist(ImmutableList.of(mediaItemData0))
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(8_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().build()),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                    /* trackSelected= */ new boolean[] {true})));
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1)
            .setMediaItem(mediaItem1)
            .setMediaMetadata(mediaMetadata)
            .setTracks(tracks)
            .build();
    Commands commands =
        new Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    PlaybackException error =
        new PlaybackException(
            /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 2f);
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            .setMaxVideoBitrate(1000)
            .build();
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
    VideoSize videoSize = new VideoSize(/* width= */ 200, /* height= */ 400);
    CueGroup cueGroup =
        new CueGroup(
            ImmutableList.of(new Cue.Builder().setText("text").build()),
            /* presentationTimeUs= */ 123);
    Metadata timedMetadata =
        new Metadata(/* presentationTimeUs= */ 42, new FakeMetadataEntry("data"));
    Size surfaceSize = new Size(480, 360);
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 3, /* maxVolume= */ 7);
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    State state2 =
        new State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(error)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setAudioAttributes(audioAttributes)
            .setVolume(0.5f)
            .setVideoSize(videoSize)
            .setCurrentCues(cueGroup)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(5)
            .setIsDeviceMuted(true)
            .setSurfaceSize(surfaceSize)
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(timedMetadata)
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setPlaylistMetadata(playlistMetadata)
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(12_000)
            .setPositionDiscontinuity(
                Player.DISCONTINUITY_REASON_SEEK, /* discontinuityPositionMs= */ 11_500)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used.
    assertThat(player.getPlayWhenReady()).isTrue();

    returnState2.set(true);
    player.invalidateState();
    // Verify state2 is used.
    assertThat(player.getPlayWhenReady()).isFalse();
    // Idle Looper to ensure all callbacks (including onEvents) are delivered.
    ShadowLooper.idleMainLooper();

    // Assert listener calls.
    verify(listener).onAvailableCommandsChanged(commands);
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verify(listener).onPlaybackStateChanged(Player.STATE_IDLE);
    verify(listener)
        .onPlaybackSuppressionReasonChanged(
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    verify(listener).onIsPlayingChanged(false);
    verify(listener).onPlayerError(error);
    verify(listener).onPlayerErrorChanged(error);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verify(listener).onShuffleModeEnabledChanged(true);
    verify(listener).onLoadingChanged(false);
    verify(listener).onIsLoadingChanged(false);
    verify(listener).onSeekBackIncrementChanged(5000);
    verify(listener).onSeekForwardIncrementChanged(4000);
    verify(listener).onMaxSeekToPreviousPositionChanged(3000);
    verify(listener).onPlaybackParametersChanged(playbackParameters);
    verify(listener).onTrackSelectionParametersChanged(trackSelectionParameters);
    verify(listener).onAudioAttributesChanged(audioAttributes);
    verify(listener).onVolumeChanged(0.5f);
    verify(listener).onVideoSizeChanged(videoSize);
    verify(listener).onCues(cueGroup.cues);
    verify(listener).onCues(cueGroup);
    verify(listener).onDeviceInfoChanged(deviceInfo);
    verify(listener).onDeviceVolumeChanged(/* volume= */ 5, /* muted= */ true);
    verify(listener)
        .onTimelineChanged(state2.timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    verify(listener).onMediaMetadataChanged(mediaMetadata);
    verify(listener).onTracksChanged(tracks);
    verify(listener).onPlaylistMetadataChanged(playlistMetadata);
    verify(listener).onRenderedFirstFrame();
    verify(listener).onMetadata(timedMetadata);
    verify(listener).onSurfaceSizeChanged(surfaceSize.getWidth(), surfaceSize.getHeight());
    verify(listener).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid0,
                /* mediaItemIndex= */ 0,
                mediaItem0,
                /* periodUid= */ mediaItemUid0,
                /* periodIndex= */ 0,
                /* positionMs= */ 8_000,
                /* contentPositionMs= */ 8_000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid1,
                /* mediaItemIndex= */ 1,
                mediaItem1,
                /* periodUid= */ mediaItemUid1,
                /* periodIndex= */ 1,
                /* positionMs= */ 11_500,
                /* contentPositionMs= */ 11_500,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_SEEK);
    verify(listener).onMediaItemTransition(mediaItem1, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
    verify(listener)
        .onEvents(
            player,
            new Player.Events(
                new FlagSet.Builder()
                    .addAll(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TRACKS_CHANGED,
                        Player.EVENT_IS_LOADING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_PLAYER_ERROR,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                        Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_PLAYLIST_METADATA_CHANGED,
                        Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
                        Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
                        Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
                        Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
                        Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
                        Player.EVENT_VOLUME_CHANGED,
                        Player.EVENT_SURFACE_SIZE_CHANGED,
                        Player.EVENT_VIDEO_SIZE_CHANGED,
                        Player.EVENT_RENDERED_FIRST_FRAME,
                        Player.EVENT_CUES,
                        Player.EVENT_METADATA,
                        Player.EVENT_DEVICE_INFO_CHANGED,
                        Player.EVENT_DEVICE_VOLUME_CHANGED)
                    .build()));
    verifyNoMoreInteractions(listener);
    // Assert that we actually called all listeners.
    for (Method method : Player.Listener.class.getDeclaredMethods()) {
      if (method.getName().equals("onSeekProcessed")) {
        continue;
      }
      if (method.getName().equals("onAudioSessionIdChanged")
          || method.getName().equals("onSkipSilenceEnabledChanged")) {
        // Skip listeners for ExoPlayer-specific states
        continue;
      }
      method.invoke(verify(listener), getAnyArguments(method));
    }
  }

  @Test
  public void invalidateState_withMediaItemDetailChange_reportsTimelineSourceUpdate() {
    Object mediaItemUid0 = new Object();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0).build();
    Object mediaItemUid1 = new Object();
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1).build();
    State state1 =
        new State.Builder().setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1)).build();
    SimpleBasePlayer.MediaItemData mediaItemData1Updated =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1).setDurationUs(10_000).build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1Updated))
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onTimelineChanged(state2.timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void invalidateState_withCurrentMediaItemRemoval_reportsDiscontinuityReasonRemoved() {
    Object mediaItemUid0 = new Object();
    MediaItem mediaItem0 = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0).setMediaItem(mediaItem0).build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(5000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(2000)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid1,
                /* mediaItemIndex= */ 1,
                mediaItem1,
                /* periodUid= */ mediaItemUid1,
                /* periodIndex= */ 1,
                /* positionMs= */ 5000,
                /* contentPositionMs= */ 5000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid0,
                /* mediaItemIndex= */ 0,
                mediaItem0,
                /* periodUid= */ mediaItemUid0,
                /* periodIndex= */ 0,
                /* positionMs= */ 2000,
                /* contentPositionMs= */ 2000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_REMOVE);
    verify(listener)
        .onMediaItemTransition(mediaItem0, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void
      invalidateState_withTransitionFromEndOfItem_reportsDiscontinuityReasonAutoTransition() {
    Object mediaItemUid0 = new Object();
    MediaItem mediaItem0 = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0)
            .setMediaItem(mediaItem0)
            .setDurationUs(50_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(50)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(10)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid0,
                /* mediaItemIndex= */ 0,
                mediaItem0,
                /* periodUid= */ mediaItemUid0,
                /* periodIndex= */ 0,
                /* positionMs= */ 50,
                /* contentPositionMs= */ 50,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid1,
                /* mediaItemIndex= */ 1,
                mediaItem1,
                /* periodUid= */ mediaItemUid1,
                /* periodIndex= */ 1,
                /* positionMs= */ 10,
                /* contentPositionMs= */ 10,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    verify(listener).onMediaItemTransition(mediaItem1, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
  }

  @Test
  public void invalidateState_withTransitionFromMiddleOfItem_reportsDiscontinuityReasonSkip() {
    Object mediaItemUid0 = new Object();
    MediaItem mediaItem0 = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0)
            .setMediaItem(mediaItem0)
            .setDurationUs(50_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(20)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(10)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid0,
                /* mediaItemIndex= */ 0,
                mediaItem0,
                /* periodUid= */ mediaItemUid0,
                /* periodIndex= */ 0,
                /* positionMs= */ 20,
                /* contentPositionMs= */ 20,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid1,
                /* mediaItemIndex= */ 1,
                mediaItem1,
                /* periodUid= */ mediaItemUid1,
                /* periodIndex= */ 1,
                /* positionMs= */ 10,
                /* contentPositionMs= */ 10,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_SKIP);
    verify(listener)
        .onMediaItemTransition(mediaItem1, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void invalidateState_withRepeatingItem_reportsDiscontinuityReasonAutoTransition() {
    Object mediaItemUid = new Object();
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid)
            .setMediaItem(mediaItem)
            .setDurationUs(5_000_000)
            .build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(5_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid,
                /* mediaItemIndex= */ 0,
                mediaItem,
                /* periodUid= */ mediaItemUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 5_000,
                /* contentPositionMs= */ 5_000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid,
                /* mediaItemIndex= */ 0,
                mediaItem,
                /* periodUid= */ mediaItemUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 0,
                /* contentPositionMs= */ 0,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    verify(listener).onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT);
  }

  @Test
  public void invalidateState_withDiscontinuityInsideItem_reportsDiscontinuityReasonInternal() {
    Object mediaItemUid = new Object();
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid)
            .setMediaItem(mediaItem)
            .setDurationUs(5_000_000)
            .build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(1_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(3_000)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid,
                /* mediaItemIndex= */ 0,
                mediaItem,
                /* periodUid= */ mediaItemUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 1_000,
                /* contentPositionMs= */ 1_000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid,
                /* mediaItemIndex= */ 0,
                mediaItem,
                /* periodUid= */ mediaItemUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 3_000,
                /* contentPositionMs= */ 3_000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_INTERNAL);
    verify(listener, never()).onMediaItemTransition(any(), anyInt());
  }

  @Test
  public void invalidateState_withMinorPositionDrift_doesNotReportsDiscontinuity() {
    SimpleBasePlayer.MediaItemData mediaItemData =
        new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(1_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(1_500)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    player.invalidateState();
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    returnState2.set(true);
    player.invalidateState();

    // Assert listener call.
    verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    verify(listener, never()).onMediaItemTransition(any(), anyInt());
  }

  @Test
  public void invalidateState_duringAsyncMethodHandling_isIgnored() {
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State state2 =
        state1
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicReference<State> currentState = new AtomicReference<>(state1);
    SettableFuture<?> asyncFuture = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return currentState.get();
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            return asyncFuture;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used trigger async method.
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);

    currentState.set(state2);
    player.invalidateState();

    // Verify placeholder state is used (and not state2).
    assertThat(player.getPlayWhenReady()).isTrue();

    // Finish async operation and verify no listeners are informed.
    currentState.set(state1);
    asyncFuture.set(null);

    assertThat(player.getPlayWhenReady()).isTrue();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void overlappingAsyncMethodHandling_onlyUpdatesStateAfterAllDone() {
    State state1 =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State state2 =
        state1
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    AtomicReference<State> currentState = new AtomicReference<>(state1);
    ArrayList<SettableFuture<?>> asyncFutures = new ArrayList<>();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return currentState.get();
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            SettableFuture<?> future = SettableFuture.create();
            asyncFutures.add(future);
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);
    // Verify state1 is used.
    assertThat(player.getPlayWhenReady()).isTrue();

    // Trigger multiple parallel async calls and set state2 (which should never be used).
    player.setPlayWhenReady(true);
    currentState.set(state2);
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);
    assertThat(player.getPlayWhenReady()).isTrue();
    player.setPlayWhenReady(true);
    assertThat(player.getPlayWhenReady()).isTrue();

    // Finish async operation and verify state2 is not used while operations are pending.
    asyncFutures.get(1).set(null);
    assertThat(player.getPlayWhenReady()).isTrue();
    asyncFutures.get(2).set(null);
    assertThat(player.getPlayWhenReady()).isTrue();
    verifyNoMoreInteractions(listener);

    // Finish last async operation and verify updated state and listener calls.
    asyncFutures.get(0).set(null);
    assertThat(player.getPlayWhenReady()).isFalse();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void setPlayWhenReady_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State updatedState =
        state
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    // Intentionally use parameter that doesn't match final result.
    player.setPlayWhenReady(false);

    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void setPlayWhenReady_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(
                /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build();
    State updatedState =
        state
            .buildUpon()
            .setPlayWhenReady(
                /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setPlayWhenReady(true);

    // Verify placeholder state and listener calls.
    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ true, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getPlayWhenReady()).isTrue();
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlayWhenReady_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder().addAllCommands().remove(Player.COMMAND_PLAY_PAUSE).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setPlayWhenReady(true);

    assertThat(callForwarded.get()).isFalse();
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void prepare_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    State updatedState = state.buildUpon().setPlaybackState(Player.STATE_READY).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handlePrepare() {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.prepare();

    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_READY);
    verify(listener).onPlaybackStateChanged(Player.STATE_READY);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_READY);
    verifyNoMoreInteractions(listener);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void prepare_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    State updatedState = state.buildUpon().setPlaybackState(Player.STATE_READY).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handlePrepare() {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.prepare();

    // Verify placeholder state and listener calls.
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_BUFFERING);
    verify(listener).onPlaybackStateChanged(Player.STATE_BUFFERING);
    verify(listener)
        .onPlayerStateChanged(
            /* playWhenReady= */ false, /* playbackState= */ Player.STATE_BUFFERING);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_READY);
    verify(listener).onPlaybackStateChanged(Player.STATE_READY);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_READY);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void prepare_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder().addAllCommands().remove(Player.COMMAND_PREPARE).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handlePrepare() {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.prepare();

    assertThat(callForwarded.get()).isFalse();
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void stop_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    State updatedState = state.buildUpon().setPlaybackState(Player.STATE_IDLE).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleStop() {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.stop();

    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    verify(listener).onPlaybackStateChanged(Player.STATE_IDLE);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void stop_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    // Additionally set the repeat mode to see a difference between the placeholder and new state.
    State updatedState =
        state
            .buildUpon()
            .setPlaybackState(Player.STATE_IDLE)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleStop() {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.stop();

    // Verify placeholder state and listener calls.
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);
    verify(listener).onPlaybackStateChanged(Player.STATE_IDLE);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void stop_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder().addAllCommands().remove(Player.COMMAND_STOP).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleStop() {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.stop();

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void release_immediateHandling_updatesStateInformsListenersAndReturnsIdle() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    State updatedState = state.buildUpon().setRepeatMode(Player.REPEAT_MODE_ALL).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleRelease() {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.release();

    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verify(listener).onEvents(eq(player), any());
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void release_asyncHandling_returnsIdleAndIgnoredAsyncStateUpdate() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build()))
            .build();
    // Additionally set the repeat mode to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setRepeatMode(Player.REPEAT_MODE_ALL).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleRelease() {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.release();

    // Verify initial change to IDLE without listener call.
    assertThat(player.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify no further update happened.
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_OFF);
    verifyNoMoreInteractions(listener);
  }

  @Ignore("b/261158047: Ignore test while Player.COMMAND_RELEASE doesn't exist.")
  @Test
  public void release_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            // TODO(b/261158047): Uncomment once test is no longer ignored.
            // .setAvailableCommands(
            //    new Commands.Builder().addAllCommands().remove(Player.COMMAND_RELEASE).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleRelease() {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.release();

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void release_withSubsequentPlayerAction_ignoresSubsequentAction() {
    AtomicBoolean releaseCalled = new AtomicBoolean();
    AtomicBoolean getStateCalledAfterRelease = new AtomicBoolean();
    AtomicBoolean handlePlayWhenReadyCalledAfterRelease = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            if (releaseCalled.get()) {
              getStateCalledAfterRelease.set(true);
            }
            return new State.Builder()
                .setAvailableCommands(new Commands.Builder().addAllCommands().build())
                .build();
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            if (releaseCalled.get()) {
              handlePlayWhenReadyCalledAfterRelease.set(true);
            }
            return Futures.immediateVoidFuture();
          }

          @Override
          protected ListenableFuture<?> handleRelease() {
            return Futures.immediateVoidFuture();
          }
        };

    player.release();
    releaseCalled.set(true);
    // Try triggering a regular player action and to invalidate the state manually.
    player.setPlayWhenReady(true);
    player.invalidateState();

    assertThat(getStateCalledAfterRelease.get()).isFalse();
    assertThat(handlePlayWhenReadyCalledAfterRelease.get()).isFalse();
  }

  @Test
  public void setRepeatMode_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState = state.buildUpon().setRepeatMode(Player.REPEAT_MODE_ALL).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setRepeatMode(Player.REPEAT_MODE_ONE);

    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setRepeatMode_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a new repeat mode to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setRepeatMode(Player.REPEAT_MODE_ALL).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setRepeatMode(Player.REPEAT_MODE_ONE);

    // Verify placeholder state and listener calls.
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setRepeatMode_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_REPEAT_MODE)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setRepeatMode(Player.REPEAT_MODE_ONE);

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setShuffleModeEnabled_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Also change the repeat mode to ensure the updated state is used.
    State updatedState =
        state.buildUpon().setShuffleModeEnabled(true).setRepeatMode(Player.REPEAT_MODE_ALL).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setShuffleModeEnabled(true);

    assertThat(player.getShuffleModeEnabled()).isTrue();
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    verify(listener).onShuffleModeEnabledChanged(true);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setShuffleModeEnabled_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            // Always return the same state to revert the shuffle mode change. This allows to see a
            // difference between the placeholder and new state.
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setShuffleModeEnabled(true);

    // Verify placeholder state and listener calls.
    assertThat(player.getShuffleModeEnabled()).isTrue();
    verify(listener).onShuffleModeEnabledChanged(true);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getShuffleModeEnabled()).isFalse();
    verify(listener).onShuffleModeEnabledChanged(false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setShuffleModeEnabled_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setShuffleModeEnabled(true);

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setPlaybackParameters_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState =
        state.buildUpon().setPlaybackParameters(new PlaybackParameters(/* speed= */ 3f)).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaybackParameters(
              PlaybackParameters playbackParameters) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f));

    assertThat(player.getPlaybackParameters()).isEqualTo(new PlaybackParameters(/* speed= */ 3f));
    verify(listener).onPlaybackParametersChanged(new PlaybackParameters(/* speed= */ 3f));
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlaybackParameters_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a new repeat mode to see a difference between the placeholder and new state.
    State updatedState =
        state.buildUpon().setPlaybackParameters(new PlaybackParameters(/* speed= */ 3f)).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaybackParameters(
              PlaybackParameters playbackParameters) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f));

    // Verify placeholder state and listener calls.
    assertThat(player.getPlaybackParameters()).isEqualTo(new PlaybackParameters(/* speed= */ 2f));
    verify(listener).onPlaybackParametersChanged(new PlaybackParameters(/* speed= */ 2f));
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getPlaybackParameters()).isEqualTo(new PlaybackParameters(/* speed= */ 3f));
    verify(listener).onPlaybackParametersChanged(new PlaybackParameters(/* speed= */ 3f));
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlaybackParameters_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaybackParameters(
              PlaybackParameters playbackParameters) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f));

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setTrackSelectionParameters_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    TrackSelectionParameters updatedParameters =
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(3000)
            .build();
    State updatedState = state.buildUpon().setTrackSelectionParameters(updatedParameters).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetTrackSelectionParameters(
              TrackSelectionParameters trackSelectionParameters) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setTrackSelectionParameters(
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(1000)
            .build());

    assertThat(player.getTrackSelectionParameters()).isEqualTo(updatedParameters);
    verify(listener).onTrackSelectionParametersChanged(updatedParameters);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setTrackSelectionParameters_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set new parameters to see a difference between the placeholder and new state.
    TrackSelectionParameters updatedParameters =
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(3000)
            .build();
    State updatedState = state.buildUpon().setTrackSelectionParameters(updatedParameters).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetTrackSelectionParameters(
              TrackSelectionParameters trackSelectionParameters) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    TrackSelectionParameters requestedParameters =
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(3000)
            .build();
    player.setTrackSelectionParameters(requestedParameters);

    // Verify placeholder state and listener calls.
    assertThat(player.getTrackSelectionParameters()).isEqualTo(requestedParameters);
    verify(listener).onTrackSelectionParametersChanged(requestedParameters);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getTrackSelectionParameters()).isEqualTo(updatedParameters);
    verify(listener).onTrackSelectionParametersChanged(updatedParameters);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setTrackSelectionParameters_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetTrackSelectionParameters(
              TrackSelectionParameters trackSelectionParameters) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setTrackSelectionParameters(
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(1000)
            .build());

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setPlaylistMetadata_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    MediaMetadata updatedMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    State updatedState = state.buildUpon().setPlaylistMetadata(updatedMetadata).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setPlaylistMetadata(new MediaMetadata.Builder().setTitle("title").build());

    assertThat(player.getPlaylistMetadata()).isEqualTo(updatedMetadata);
    verify(listener).onPlaylistMetadataChanged(updatedMetadata);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlaylistMetadata_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set new metadata to see a difference between the placeholder and new state.
    MediaMetadata updatedMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    State updatedState = state.buildUpon().setPlaylistMetadata(updatedMetadata).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    MediaMetadata requestedMetadata = new MediaMetadata.Builder().setTitle("title").build();
    player.setPlaylistMetadata(requestedMetadata);

    // Verify placeholder state and listener calls.
    assertThat(player.getPlaylistMetadata()).isEqualTo(requestedMetadata);
    verify(listener).onPlaylistMetadataChanged(requestedMetadata);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getPlaylistMetadata()).isEqualTo(updatedMetadata);
    verify(listener).onPlaylistMetadataChanged(updatedMetadata);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setPlaylistMetadata_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_MEDIA_ITEMS_METADATA)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setPlaylistMetadata(new MediaMetadata.Builder().setTitle("title").build());

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setVolume_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState = state.buildUpon().setVolume(.8f).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetVolume(float volume) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setVolume(.5f);

    assertThat(player.getVolume()).isEqualTo(.8f);
    verify(listener).onVolumeChanged(.8f);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setVolume_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a new volume to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setVolume(.8f).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetVolume(float volume) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setVolume(.5f);

    // Verify placeholder state and listener calls.
    assertThat(player.getVolume()).isEqualTo(.5f);
    verify(listener).onVolumeChanged(.5f);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getVolume()).isEqualTo(.8f);
    verify(listener).onVolumeChanged(.8f);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setVolume_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder().addAllCommands().remove(Player.COMMAND_SET_VOLUME).build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetVolume(float volume) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setVolume(.5f);

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setDeviceVolume_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState = state.buildUpon().setDeviceVolume(6).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceVolume(int volume) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setDeviceVolume(3);

    assertThat(player.getDeviceVolume()).isEqualTo(6);
    verify(listener).onDeviceVolumeChanged(6, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setDeviceVolume_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Set a new volume to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setDeviceVolume(6).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceVolume(int volume) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setDeviceVolume(3);

    // Verify placeholder state and listener calls.
    assertThat(player.getDeviceVolume()).isEqualTo(3);
    verify(listener).onDeviceVolumeChanged(3, /* muted= */ false);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getDeviceVolume()).isEqualTo(6);
    verify(listener).onDeviceVolumeChanged(6, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setDeviceVolume_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_DEVICE_VOLUME)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceVolume(int volume) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setDeviceVolume(3);

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void increaseDeviceVolume_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setDeviceVolume(3)
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState = state.buildUpon().setDeviceVolume(6).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleIncreaseDeviceVolume() {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.increaseDeviceVolume();

    assertThat(player.getDeviceVolume()).isEqualTo(6);
    verify(listener).onDeviceVolumeChanged(6, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void increaseDeviceVolume_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setDeviceVolume(3)
            .build();
    // Set a new volume to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setDeviceVolume(6).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleIncreaseDeviceVolume() {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.increaseDeviceVolume();

    // Verify placeholder state and listener calls.
    assertThat(player.getDeviceVolume()).isEqualTo(4);
    verify(listener).onDeviceVolumeChanged(4, /* muted= */ false);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getDeviceVolume()).isEqualTo(6);
    verify(listener).onDeviceVolumeChanged(6, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void increaseDeviceVolume_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleIncreaseDeviceVolume() {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.increaseDeviceVolume();

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void decreaseDeviceVolume_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setDeviceVolume(3)
            .build();
    // Set a different one to the one requested to ensure the updated state is used.
    State updatedState = state.buildUpon().setDeviceVolume(1).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleDecreaseDeviceVolume() {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.decreaseDeviceVolume();

    assertThat(player.getDeviceVolume()).isEqualTo(1);
    verify(listener).onDeviceVolumeChanged(1, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void decreaseDeviceVolume_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setDeviceVolume(3)
            .build();
    // Set a new volume to see a difference between the placeholder and new state.
    State updatedState = state.buildUpon().setDeviceVolume(1).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleDecreaseDeviceVolume() {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.decreaseDeviceVolume();

    // Verify placeholder state and listener calls.
    assertThat(player.getDeviceVolume()).isEqualTo(2);
    verify(listener).onDeviceVolumeChanged(2, /* muted= */ false);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getDeviceVolume()).isEqualTo(1);
    verify(listener).onDeviceVolumeChanged(1, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void decreaseDeviceVolume_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleDecreaseDeviceVolume() {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.decreaseDeviceVolume();

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setDeviceMuted_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    // Also change the volume to ensure the updated state is used.
    State updatedState = state.buildUpon().setIsDeviceMuted(true).setDeviceVolume(6).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceMuted(boolean muted) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setDeviceMuted(true);

    assertThat(player.isDeviceMuted()).isTrue();
    assertThat(player.getDeviceVolume()).isEqualTo(6);
    verify(listener).onDeviceVolumeChanged(6, /* muted= */ true);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setDeviceMuted_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            // Always return the same state to revert the muted change. This allows to see a
            // difference between the placeholder and new state.
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceMuted(boolean muted) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setDeviceMuted(true);

    // Verify placeholder state and listener calls.
    assertThat(player.isDeviceMuted()).isTrue();
    verify(listener).onDeviceVolumeChanged(0, /* muted= */ true);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.isDeviceMuted()).isFalse();
    verify(listener).onDeviceVolumeChanged(0, /* muted= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setDeviceMuted_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetDeviceMuted(boolean muted) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setDeviceMuted(true);

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void setVideoSurface_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setSurfaceSize(Size.ZERO)
            .build();
    Size updatedSize = new Size(/* width= */ 300, /* height= */ 200);
    State updatedState = state.buildUpon().setSurfaceSize(updatedSize).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setVideoSurface(new Surface(new SurfaceTexture(0)));

    assertThat(player.getSurfaceSize()).isEqualTo(updatedSize);
    verify(listener).onSurfaceSizeChanged(updatedSize.getWidth(), updatedSize.getHeight());
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setVideoSurface_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setSurfaceSize(Size.ZERO)
            .build();
    SettableFuture<?> future = SettableFuture.create();
    Size updatedSize = new Size(/* width= */ 300, /* height= */ 200);
    State updatedState = state.buildUpon().setSurfaceSize(updatedSize).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.setVideoSurface(new Surface(new SurfaceTexture(0)));

    // Verify placeholder state and listener calls.
    assertThat(player.getSurfaceSize()).isEqualTo(Size.UNKNOWN);
    verify(listener)
        .onSurfaceSizeChanged(/* width= */ C.LENGTH_UNSET, /* height= */ C.LENGTH_UNSET);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getSurfaceSize()).isEqualTo(updatedSize);
    verify(listener).onSurfaceSizeChanged(updatedSize.getWidth(), updatedSize.getHeight());
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void setVideoSurface_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_VIDEO_SURFACE)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.setVideoSurface(new Surface(new SurfaceTexture(0)));

    assertThat(callForwarded.get()).isFalse();
  }

  @Test
  public void clearVideoSurface_immediateHandling_updatesStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setSurfaceSize(new Size(/* width= */ 300, /* height= */ 200))
            .build();
    // Change something else in addition to ensure we actually use the updated state.
    State updatedState =
        state.buildUpon().setSurfaceSize(Size.ZERO).setRepeatMode(Player.REPEAT_MODE_ONE).build();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          private State playerState = state;

          @Override
          protected State getState() {
            return playerState;
          }

          @Override
          protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
            playerState = updatedState;
            return Futures.immediateVoidFuture();
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.clearVideoSurface();

    assertThat(player.getSurfaceSize()).isEqualTo(Size.ZERO);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
    verify(listener).onSurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void clearVideoSurface_asyncHandling_usesPlaceholderStateAndInformsListeners() {
    State state =
        new State.Builder()
            .setAvailableCommands(new Commands.Builder().addAllCommands().build())
            .setSurfaceSize(new Size(/* width= */ 300, /* height= */ 200))
            .build();
    // Change something else in addition to ensure we actually use the updated state.
    State updatedState =
        state.buildUpon().setSurfaceSize(Size.ZERO).setRepeatMode(Player.REPEAT_MODE_ONE).build();
    SettableFuture<?> future = SettableFuture.create();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return future.isDone() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
            return future;
          }
        };
    Listener listener = mock(Listener.class);
    player.addListener(listener);

    player.clearVideoSurface();

    // Verify placeholder state and listener calls.
    assertThat(player.getSurfaceSize()).isEqualTo(Size.ZERO);
    verify(listener).onSurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    verifyNoMoreInteractions(listener);

    future.set(null);

    // Verify actual state update.
    assertThat(player.getSurfaceSize()).isEqualTo(Size.ZERO);
    assertThat(player.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ONE);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ONE);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void clearVideoSurface_withoutAvailableCommand_isNotForwarded() {
    State state =
        new State.Builder()
            .setAvailableCommands(
                new Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_VIDEO_SURFACE)
                    .build())
            .build();
    AtomicBoolean callForwarded = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }

          @Override
          protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
            callForwarded.set(true);
            return Futures.immediateVoidFuture();
          }
        };

    player.clearVideoSurface();

    assertThat(callForwarded.get()).isFalse();
  }

  private static Object[] getAnyArguments(Method method) {
    Object[] arguments = new Object[method.getParameterCount()];
    Class<?>[] argumentTypes = method.getParameterTypes();
    for (int i = 0; i < arguments.length; i++) {
      if (argumentTypes[i].equals(Integer.TYPE)) {
        arguments[i] = anyInt();
      } else if (argumentTypes[i].equals(Long.TYPE)) {
        arguments[i] = anyLong();
      } else if (argumentTypes[i].equals(Float.TYPE)) {
        arguments[i] = anyFloat();
      } else if (argumentTypes[i].equals(Boolean.TYPE)) {
        arguments[i] = anyBoolean();
      } else {
        arguments[i] = any();
      }
    }
    return arguments;
  }
}
