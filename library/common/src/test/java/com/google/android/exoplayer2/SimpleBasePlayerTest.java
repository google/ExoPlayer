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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Looper;
import android.os.SystemClock;
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
            .setAudioSessionId(78)
            .setSkipSilenceEnabled(true)
            .setSurfaceSize(new Size(480, 360))
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(new Metadata())
            .setPlaylist(
                ImmutableList.of(
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                            /* adsId= */ new Object(),
                                            /* adGroupTimesUs= */ 555,
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
  public void playlistItemBuildUpon_build_isEqual() {
    SimpleBasePlayer.PlaylistItem playlistItem =
        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
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

    SimpleBasePlayer.PlaylistItem newPlaylistItem = playlistItem.buildUpon().build();

    assertThat(newPlaylistItem).isEqualTo(playlistItem);
    assertThat(newPlaylistItem.hashCode()).isEqualTo(playlistItem.hashCode());
  }

  @Test
  public void periodDataBuildUpon_build_isEqual() {
    SimpleBasePlayer.PeriodData periodData =
        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
            .setIsPlaceholder(true)
            .setDurationUs(600_000)
            .setAdPlaybackState(
                new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs= */ 555, 666))
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
    ImmutableList<SimpleBasePlayer.PlaylistItem> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                    /* adsId= */ new Object(), /* adGroupTimesUs= */ 555, 666))
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
            .setAudioSessionId(78)
            .setSkipSilenceEnabled(true)
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
    assertThat(state.audioSessionId).isEqualTo(78);
    assertThat(state.skipSilenceEnabled).isTrue();
    assertThat(state.surfaceSize).isEqualTo(surfaceSize);
    assertThat(state.newlyRenderedFirstFrame).isTrue();
    assertThat(state.timedMetadata).isEqualTo(timedMetadata);
    assertThat(state.playlistItems).isEqualTo(playlist);
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
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build()))
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
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build()))
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
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build()))
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
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                            .setPeriods(
                                ImmutableList.of(
                                    new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                        .setAdPlaybackState(
                                            new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs= */ 123))
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
                        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                            .setPeriods(
                                ImmutableList.of(
                                    new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                        .setAdPlaybackState(
                                            new AdPlaybackState(
                                                    /* adsId= */ new Object(),
                                                    /* adGroupTimesUs= */ 123)
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
  public void stateBuilderBuild_multiplePlaylistItemsWithSameIds_throwsException() {
    Object uid = new Object();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.State.Builder()
                .setPlaylist(
                    ImmutableList.of(
                        new SimpleBasePlayer.PlaylistItem.Builder(uid).build(),
                        new SimpleBasePlayer.PlaylistItem.Builder(uid).build()))
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
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build()))
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
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build()))
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
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs= */ 123)
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
                    new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                        .setPeriods(
                            ImmutableList.of(
                                new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                                    .setAdPlaybackState(
                                        new AdPlaybackState(
                                                /* adsId= */ new Object(),
                                                /* adGroupTimesUs= */ 123)
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
  public void playlistItemBuilderBuild_setsCorrectValues() {
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

    SimpleBasePlayer.PlaylistItem playlistItem =
        new SimpleBasePlayer.PlaylistItem.Builder(uid)
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

    assertThat(playlistItem.uid).isEqualTo(uid);
    assertThat(playlistItem.tracks).isEqualTo(tracks);
    assertThat(playlistItem.mediaItem).isEqualTo(mediaItem);
    assertThat(playlistItem.mediaMetadata).isEqualTo(mediaMetadata);
    assertThat(playlistItem.manifest).isEqualTo(manifest);
    assertThat(playlistItem.liveConfiguration).isEqualTo(liveConfiguration);
    assertThat(playlistItem.presentationStartTimeMs).isEqualTo(12);
    assertThat(playlistItem.windowStartTimeMs).isEqualTo(23);
    assertThat(playlistItem.elapsedRealtimeEpochOffsetMs).isEqualTo(10234);
    assertThat(playlistItem.isSeekable).isTrue();
    assertThat(playlistItem.isDynamic).isTrue();
    assertThat(playlistItem.defaultPositionUs).isEqualTo(456_789);
    assertThat(playlistItem.durationUs).isEqualTo(500_000);
    assertThat(playlistItem.positionInFirstPeriodUs).isEqualTo(100_000);
    assertThat(playlistItem.isPlaceholder).isTrue();
    assertThat(playlistItem.periods).isEqualTo(periods);
  }

  @Test
  public void playlistItemBuilderBuild_presentationStartTimeIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setPresentationStartTimeMs(12)
                .build());
  }

  @Test
  public void playlistItemBuilderBuild_windowStartTimeIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setWindowStartTimeMs(12)
                .build());
  }

  @Test
  public void playlistItemBuilderBuild_elapsedEpochOffsetIfNotLive_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setElapsedRealtimeEpochOffsetMs(12)
                .build());
  }

  @Test
  public void
      playlistItemBuilderBuild_windowStartTimeLessThanPresentationStartTime_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
                .setWindowStartTimeMs(12)
                .setPresentationStartTimeMs(13)
                .build());
  }

  @Test
  public void playlistItemBuilderBuild_multiplePeriodsWithSameUid_throwsException() {
    Object uid = new Object();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(uid).build(),
                        new SimpleBasePlayer.PeriodData.Builder(uid).build()))
                .build());
  }

  @Test
  public void playlistItemBuilderBuild_defaultPositionGreaterThanDuration_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setDefaultPositionUs(16)
                .setDurationUs(15)
                .build());
  }

  @Test
  public void periodDataBuilderBuild_setsCorrectValues() {
    Object uid = new Object();
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs= */ 555, 666);

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
    Object playlistItemUid = new Object();
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
    ImmutableList<SimpleBasePlayer.PlaylistItem> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.PlaylistItem.Builder(playlistItemUid)
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
                                    /* adsId= */ new Object(), /* adGroupTimesUs= */ 555, 666))
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
            .setAudioSessionId(78)
            .setSkipSilenceEnabled(true)
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
    assertThat(window.uid).isEqualTo(playlistItemUid);
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
    ImmutableList<SimpleBasePlayer.PlaylistItem> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object())
                .setDurationUs(500_000)
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(/* uid= */ new Object())
                            .setIsPlaceholder(true)
                            .setDurationUs(600_000)
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                        /* adsId= */ new Object(), /* adGroupTimesUs= */ 555, 666)
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
    SimpleBasePlayer.PlaylistItem playlistItem0 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid0).setMediaItem(mediaItem0).build();
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
            .setPlaylist(ImmutableList.of(playlistItem0))
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
    SimpleBasePlayer.PlaylistItem playlistItem1 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1)
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
            .setAudioSessionId(78)
            .setSkipSilenceEnabled(true)
            .setSurfaceSize(surfaceSize)
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(timedMetadata)
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
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
    verify(listener).onAudioSessionIdChanged(78);
    verify(listener).onRenderedFirstFrame();
    verify(listener).onMetadata(timedMetadata);
    verify(listener).onSurfaceSizeChanged(surfaceSize.getWidth(), surfaceSize.getHeight());
    verify(listener).onSkipSilenceEnabledChanged(true);
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
                        Player.EVENT_AUDIO_SESSION_ID,
                        Player.EVENT_VOLUME_CHANGED,
                        Player.EVENT_SKIP_SILENCE_ENABLED_CHANGED,
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
      method.invoke(verify(listener), getAnyArguments(method));
    }
  }

  @Test
  public void invalidateState_withPlaylistItemDetailChange_reportsTimelineSourceUpdate() {
    Object mediaItemUid0 = new Object();
    SimpleBasePlayer.PlaylistItem playlistItem0 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid0).build();
    Object mediaItemUid1 = new Object();
    SimpleBasePlayer.PlaylistItem playlistItem1 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1).build();
    State state1 =
        new State.Builder().setPlaylist(ImmutableList.of(playlistItem0, playlistItem1)).build();
    SimpleBasePlayer.PlaylistItem playlistItem1Updated =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1).setDurationUs(10_000).build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1Updated))
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
    SimpleBasePlayer.PlaylistItem playlistItem0 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid0).setMediaItem(mediaItem0).build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.PlaylistItem playlistItem1 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(5000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0))
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
    SimpleBasePlayer.PlaylistItem playlistItem0 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid0)
            .setMediaItem(mediaItem0)
            .setDurationUs(50_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.PlaylistItem playlistItem1 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(50)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
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
    SimpleBasePlayer.PlaylistItem playlistItem0 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid0)
            .setMediaItem(mediaItem0)
            .setDurationUs(50_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    SimpleBasePlayer.PlaylistItem playlistItem1 =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid1).setMediaItem(mediaItem1).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(20)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem0, playlistItem1))
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
    SimpleBasePlayer.PlaylistItem playlistItem =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid)
            .setMediaItem(mediaItem)
            .setDurationUs(5_000_000)
            .build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(5_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
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
    SimpleBasePlayer.PlaylistItem playlistItem =
        new SimpleBasePlayer.PlaylistItem.Builder(mediaItemUid)
            .setMediaItem(mediaItem)
            .setDurationUs(5_000_000)
            .build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(1_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
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
    SimpleBasePlayer.PlaylistItem playlistItem =
        new SimpleBasePlayer.PlaylistItem.Builder(/* uid= */ new Object()).build();
    State state1 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(1_000)
            .build();
    State state2 =
        new State.Builder()
            .setPlaylist(ImmutableList.of(playlistItem))
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
    AtomicBoolean stateUpdated = new AtomicBoolean();
    SimpleBasePlayer player =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return stateUpdated.get() ? updatedState : state;
          }

          @Override
          protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
            stateUpdated.set(true);
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
