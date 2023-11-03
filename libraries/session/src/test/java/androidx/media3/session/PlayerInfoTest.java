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
package androidx.media3.session;

import static androidx.media3.common.MimeTypes.AUDIO_AAC;
import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.test.utils.FakeTimeline;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link PlayerInfo}. */
@RunWith(AndroidJUnit4.class)
public class PlayerInfoTest {

  @Test
  public void bundlingExclusionEquals_equalInstances() {
    PlayerInfo.BundlingExclusions bundlingExclusions1 =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ false);
    PlayerInfo.BundlingExclusions bundlingExclusions2 =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ false);

    assertThat(bundlingExclusions1).isEqualTo(bundlingExclusions2);
  }

  @Test
  public void bundlingExclusionFromBundle_toBundleRoundTrip_equalInstances() {
    PlayerInfo.BundlingExclusions bundlingExclusions =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ true);
    Bundle bundle = bundlingExclusions.toBundle();

    PlayerInfo.BundlingExclusions resultingBundlingExclusions =
        PlayerInfo.BundlingExclusions.fromBundle(bundle);

    assertThat(resultingBundlingExclusions).isEqualTo(bundlingExclusions);
  }

  @Test
  public void toBundleFromBundle_restoresAllData() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setOldPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 5,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id1").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 4,
                    /* positionMs= */ 4000,
                    /* contentPositionMs= */ 5000,
                    /* adGroupIndex= */ 3,
                    /* adIndexInAdGroup= */ 2))
            .setNewPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 6,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id2").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 7,
                    /* positionMs= */ 8000,
                    /* contentPositionMs= */ 9000,
                    /* adGroupIndex= */ 5,
                    /* adIndexInAdGroup= */ 1))
            .setSessionPositionInfo(
                new SessionPositionInfo(
                    new Player.PositionInfo(
                        /* windowUid= */ null,
                        /* mediaItemIndex= */ 8,
                        /* mediaItem= */ new MediaItem.Builder().setMediaId("id3").build(),
                        /* periodUid= */ null,
                        /* periodIndex= */ 9,
                        /* positionMs= */ 2000,
                        /* contentPositionMs= */ 7000,
                        /* adGroupIndex= */ 9,
                        /* adIndexInAdGroup= */ 1),
                    /* isPlayingAd= */ true,
                    /* eventTimeMs= */ 123456789,
                    /* durationMs= */ 30000,
                    /* bufferedPositionMs= */ 20000,
                    /* bufferedPercentage= */ 50,
                    /* totalBufferedDurationMs= */ 25000,
                    /* currentLiveOffsetMs= */ 3000,
                    /* contentDurationMs= */ 27000,
                    /* contentBufferedPositionMs= */ 15000))
            .setTimeline(new FakeTimeline(/* windowCount= */ 10))
            .setTimelineChangeReason(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setPlaylistMetadata(new MediaMetadata.Builder().setArtist("artist").build())
            .setVolume(0.5f)
            .setDeviceVolume(10)
            .setDeviceMuted(true)
            .setAudioAttributes(
                new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build())
            .setCues(new CueGroup(/* cues= */ ImmutableList.of(), /* presentationTimeUs= */ 1234))
            .setCurrentTracks(
                new Tracks(
                    ImmutableList.of(
                        new Tracks.Group(
                            new TrackGroup(
                                new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                            /* adaptiveSupported= */ false,
                            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                            /* trackSelected= */ new boolean[] {true}))))
            .setDeviceInfo(
                new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(10).build())
            .setDiscontinuityReason(Player.DISCONTINUITY_REASON_REMOVE)
            .setIsLoading(true)
            .setIsPlaying(true)
            .setMaxSeekToPreviousPositionMs(5000)
            .setMediaItemTransitionReason(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            .setPlaybackParameters(new PlaybackParameters(2f))
            .setPlaybackState(Player.STATE_BUFFERING)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(
                new PlaybackException(
                    /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_TIMEOUT))
            .setPlayWhenReady(true)
            .setPlayWhenReadyChangeReason(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .setSeekBackIncrement(7000)
            .setSeekForwardIncrement(6000)
            .setShuffleModeEnabled(true)
            .setTrackSelectionParameters(
                new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
                    .setMaxAudioBitrate(5000)
                    .build())
            .setVideoSize(new VideoSize(/* width= */ 1024, /* height= */ 768))
            .build();

    PlayerInfo infoAfterBundling = PlayerInfo.fromBundle(playerInfo.toBundle());

    assertThat(infoAfterBundling.oldPositionInfo.mediaItemIndex).isEqualTo(5);
    assertThat(infoAfterBundling.oldPositionInfo.periodIndex).isEqualTo(4);
    assertThat(infoAfterBundling.oldPositionInfo.mediaItem.mediaId).isEqualTo("id1");
    assertThat(infoAfterBundling.oldPositionInfo.positionMs).isEqualTo(4000);
    assertThat(infoAfterBundling.oldPositionInfo.contentPositionMs).isEqualTo(5000);
    assertThat(infoAfterBundling.oldPositionInfo.adGroupIndex).isEqualTo(3);
    assertThat(infoAfterBundling.oldPositionInfo.adIndexInAdGroup).isEqualTo(2);
    assertThat(infoAfterBundling.newPositionInfo.mediaItemIndex).isEqualTo(6);
    assertThat(infoAfterBundling.newPositionInfo.periodIndex).isEqualTo(7);
    assertThat(infoAfterBundling.newPositionInfo.mediaItem.mediaId).isEqualTo("id2");
    assertThat(infoAfterBundling.newPositionInfo.positionMs).isEqualTo(8000);
    assertThat(infoAfterBundling.newPositionInfo.contentPositionMs).isEqualTo(9000);
    assertThat(infoAfterBundling.newPositionInfo.adGroupIndex).isEqualTo(5);
    assertThat(infoAfterBundling.newPositionInfo.adIndexInAdGroup).isEqualTo(1);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItemIndex).isEqualTo(8);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.periodIndex).isEqualTo(9);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItem.mediaId)
        .isEqualTo("id3");
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.positionMs).isEqualTo(2000);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.contentPositionMs)
        .isEqualTo(7000);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adGroupIndex).isEqualTo(9);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adIndexInAdGroup).isEqualTo(1);
    assertThat(infoAfterBundling.sessionPositionInfo.isPlayingAd).isTrue();
    assertThat(infoAfterBundling.sessionPositionInfo.eventTimeMs).isEqualTo(123456789);
    assertThat(infoAfterBundling.sessionPositionInfo.durationMs).isEqualTo(30000);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPositionMs).isEqualTo(20000);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPercentage).isEqualTo(50);
    assertThat(infoAfterBundling.sessionPositionInfo.totalBufferedDurationMs).isEqualTo(25000);
    assertThat(infoAfterBundling.sessionPositionInfo.currentLiveOffsetMs).isEqualTo(3000);
    assertThat(infoAfterBundling.sessionPositionInfo.contentDurationMs).isEqualTo(27000);
    assertThat(infoAfterBundling.sessionPositionInfo.contentBufferedPositionMs).isEqualTo(15000);
    assertThat(infoAfterBundling.timeline.getWindowCount()).isEqualTo(10);
    assertThat(infoAfterBundling.timelineChangeReason)
        .isEqualTo(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(infoAfterBundling.mediaMetadata.title.toString()).isEqualTo("title");
    assertThat(infoAfterBundling.playlistMetadata.artist.toString()).isEqualTo("artist");
    assertThat(infoAfterBundling.volume).isEqualTo(0.5f);
    assertThat(infoAfterBundling.deviceVolume).isEqualTo(10);
    assertThat(infoAfterBundling.deviceMuted).isTrue();
    assertThat(infoAfterBundling.audioAttributes.contentType)
        .isEqualTo(C.AUDIO_CONTENT_TYPE_SPEECH);
    assertThat(infoAfterBundling.cueGroup.presentationTimeUs).isEqualTo(1234);
    assertThat(infoAfterBundling.currentTracks.getGroups()).hasSize(1);
    assertThat(infoAfterBundling.deviceInfo.maxVolume).isEqualTo(10);
    assertThat(infoAfterBundling.discontinuityReason).isEqualTo(Player.DISCONTINUITY_REASON_REMOVE);
    assertThat(infoAfterBundling.isLoading).isTrue();
    assertThat(infoAfterBundling.isPlaying).isTrue();
    assertThat(infoAfterBundling.maxSeekToPreviousPositionMs).isEqualTo(5000);
    assertThat(infoAfterBundling.mediaItemTransitionReason)
        .isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    assertThat(infoAfterBundling.playbackParameters.speed).isEqualTo(2f);
    assertThat(infoAfterBundling.playbackState).isEqualTo(Player.STATE_BUFFERING);
    assertThat(infoAfterBundling.playbackSuppressionReason)
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    assertThat(infoAfterBundling.playerError.errorCode)
        .isEqualTo(PlaybackException.ERROR_CODE_TIMEOUT);
    assertThat(infoAfterBundling.playWhenReady).isTrue();
    assertThat(infoAfterBundling.playWhenReadyChangeReason)
        .isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
    assertThat(infoAfterBundling.repeatMode).isEqualTo(Player.REPEAT_MODE_ONE);
    assertThat(infoAfterBundling.seekBackIncrementMs).isEqualTo(7000);
    assertThat(infoAfterBundling.seekForwardIncrementMs).isEqualTo(6000);
    assertThat(infoAfterBundling.shuffleModeEnabled).isTrue();
    assertThat(infoAfterBundling.trackSelectionParameters.maxAudioBitrate).isEqualTo(5000);
    assertThat(infoAfterBundling.videoSize.width).isEqualTo(1024);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetCurrentMediaItem_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setOldPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 5,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id1").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 4,
                    /* positionMs= */ 4000,
                    /* contentPositionMs= */ 5000,
                    /* adGroupIndex= */ 3,
                    /* adIndexInAdGroup= */ 2))
            .setNewPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 6,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id2").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 7,
                    /* positionMs= */ 8000,
                    /* contentPositionMs= */ 9000,
                    /* adGroupIndex= */ 5,
                    /* adIndexInAdGroup= */ 1))
            .setSessionPositionInfo(
                new SessionPositionInfo(
                    new Player.PositionInfo(
                        /* windowUid= */ null,
                        /* mediaItemIndex= */ 8,
                        /* mediaItem= */ new MediaItem.Builder().setMediaId("id3").build(),
                        /* periodUid= */ null,
                        /* periodIndex= */ 9,
                        /* positionMs= */ 2000,
                        /* contentPositionMs= */ 7000,
                        /* adGroupIndex= */ 9,
                        /* adIndexInAdGroup= */ 1),
                    /* isPlayingAd= */ true,
                    /* eventTimeMs= */ 123456789,
                    /* durationMs= */ 30000,
                    /* bufferedPositionMs= */ 20000,
                    /* bufferedPercentage= */ 50,
                    /* totalBufferedDurationMs= */ 25000,
                    /* currentLiveOffsetMs= */ 3000,
                    /* contentDurationMs= */ 25000,
                    /* contentBufferedPositionMs= */ 15000))
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.oldPositionInfo.mediaItemIndex).isEqualTo(5);
    assertThat(infoAfterBundling.oldPositionInfo.periodIndex).isEqualTo(4);
    assertThat(infoAfterBundling.oldPositionInfo.mediaItem).isEqualTo(null);
    assertThat(infoAfterBundling.oldPositionInfo.positionMs).isEqualTo(0);
    assertThat(infoAfterBundling.oldPositionInfo.contentPositionMs).isEqualTo(0);
    assertThat(infoAfterBundling.oldPositionInfo.adGroupIndex).isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.oldPositionInfo.adIndexInAdGroup).isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.newPositionInfo.mediaItemIndex).isEqualTo(6);
    assertThat(infoAfterBundling.newPositionInfo.periodIndex).isEqualTo(7);
    assertThat(infoAfterBundling.newPositionInfo.mediaItem).isEqualTo(null);
    assertThat(infoAfterBundling.newPositionInfo.positionMs).isEqualTo(0);
    assertThat(infoAfterBundling.newPositionInfo.contentPositionMs).isEqualTo(0);
    assertThat(infoAfterBundling.newPositionInfo.adGroupIndex).isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.newPositionInfo.adIndexInAdGroup).isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItemIndex).isEqualTo(8);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.periodIndex).isEqualTo(9);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItem).isEqualTo(null);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.positionMs).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.contentPositionMs).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adGroupIndex)
        .isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adIndexInAdGroup)
        .isEqualTo(C.INDEX_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.isPlayingAd).isFalse();
    assertThat(infoAfterBundling.sessionPositionInfo.eventTimeMs).isEqualTo(123456789);
    assertThat(infoAfterBundling.sessionPositionInfo.durationMs).isEqualTo(C.TIME_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPositionMs).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPercentage).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.totalBufferedDurationMs).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.currentLiveOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.contentDurationMs).isEqualTo(C.TIME_UNSET);
    assertThat(infoAfterBundling.sessionPositionInfo.contentBufferedPositionMs).isEqualTo(0);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetTimeline_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setOldPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 5,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id1").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 4,
                    /* positionMs= */ 4000,
                    /* contentPositionMs= */ 5000,
                    /* adGroupIndex= */ 3,
                    /* adIndexInAdGroup= */ 2))
            .setNewPositionInfo(
                new Player.PositionInfo(
                    /* windowUid= */ null,
                    /* mediaItemIndex= */ 6,
                    /* mediaItem= */ new MediaItem.Builder().setMediaId("id2").build(),
                    /* periodUid= */ null,
                    /* periodIndex= */ 7,
                    /* positionMs= */ 8000,
                    /* contentPositionMs= */ 9000,
                    /* adGroupIndex= */ 5,
                    /* adIndexInAdGroup= */ 1))
            .setSessionPositionInfo(
                new SessionPositionInfo(
                    new Player.PositionInfo(
                        /* windowUid= */ null,
                        /* mediaItemIndex= */ 8,
                        /* mediaItem= */ new MediaItem.Builder().setMediaId("id3").build(),
                        /* periodUid= */ null,
                        /* periodIndex= */ 9,
                        /* positionMs= */ 2000,
                        /* contentPositionMs= */ 7000,
                        /* adGroupIndex= */ 9,
                        /* adIndexInAdGroup= */ 1),
                    /* isPlayingAd= */ true,
                    /* eventTimeMs= */ 123456789,
                    /* durationMs= */ 30000,
                    /* bufferedPositionMs= */ 20000,
                    /* bufferedPercentage= */ 50,
                    /* totalBufferedDurationMs= */ 25000,
                    /* currentLiveOffsetMs= */ 3000,
                    /* contentDurationMs= */ 27000,
                    /* contentBufferedPositionMs= */ 15000))
            .setTimeline(
                new FakeTimeline(
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* periodCount= */ 2,
                        /* id= */ new Object(),
                        /* isSeekable= */ true,
                        /* isDynamic= */ true,
                        /* durationUs= */ 5000),
                    new FakeTimeline.TimelineWindowDefinition(
                        /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 1000)))
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_TIMELINE)
                        .build(),
                    /* excludeTimeline= */ true,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.oldPositionInfo.mediaItemIndex).isEqualTo(0);
    assertThat(infoAfterBundling.oldPositionInfo.periodIndex).isEqualTo(0);
    assertThat(infoAfterBundling.oldPositionInfo.mediaItem.mediaId).isEqualTo("id1");
    assertThat(infoAfterBundling.oldPositionInfo.positionMs).isEqualTo(4000);
    assertThat(infoAfterBundling.oldPositionInfo.contentPositionMs).isEqualTo(5000);
    assertThat(infoAfterBundling.oldPositionInfo.adGroupIndex).isEqualTo(3);
    assertThat(infoAfterBundling.oldPositionInfo.adIndexInAdGroup).isEqualTo(2);
    assertThat(infoAfterBundling.newPositionInfo.mediaItemIndex).isEqualTo(0);
    assertThat(infoAfterBundling.newPositionInfo.periodIndex).isEqualTo(0);
    assertThat(infoAfterBundling.newPositionInfo.mediaItem.mediaId).isEqualTo("id2");
    assertThat(infoAfterBundling.newPositionInfo.positionMs).isEqualTo(8000);
    assertThat(infoAfterBundling.newPositionInfo.contentPositionMs).isEqualTo(9000);
    assertThat(infoAfterBundling.newPositionInfo.adGroupIndex).isEqualTo(5);
    assertThat(infoAfterBundling.newPositionInfo.adIndexInAdGroup).isEqualTo(1);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItemIndex).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.periodIndex).isEqualTo(0);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.mediaItem.mediaId)
        .isEqualTo("id3");
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.positionMs).isEqualTo(2000);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.contentPositionMs)
        .isEqualTo(7000);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adGroupIndex).isEqualTo(9);
    assertThat(infoAfterBundling.sessionPositionInfo.positionInfo.adIndexInAdGroup).isEqualTo(1);
    assertThat(infoAfterBundling.sessionPositionInfo.isPlayingAd).isTrue();
    assertThat(infoAfterBundling.sessionPositionInfo.eventTimeMs).isEqualTo(123456789);
    assertThat(infoAfterBundling.sessionPositionInfo.durationMs).isEqualTo(30000);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPositionMs).isEqualTo(20000);
    assertThat(infoAfterBundling.sessionPositionInfo.bufferedPercentage).isEqualTo(50);
    assertThat(infoAfterBundling.sessionPositionInfo.totalBufferedDurationMs).isEqualTo(25000);
    assertThat(infoAfterBundling.sessionPositionInfo.currentLiveOffsetMs).isEqualTo(3000);
    assertThat(infoAfterBundling.sessionPositionInfo.contentDurationMs).isEqualTo(27000);
    assertThat(infoAfterBundling.sessionPositionInfo.contentBufferedPositionMs).isEqualTo(15000);
    assertThat(infoAfterBundling.timeline.getWindowCount()).isEqualTo(1);
    Timeline.Window window =
        infoAfterBundling.timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
    assertThat(window.durationUs).isEqualTo(5000);
    assertThat(window.firstPeriodIndex).isEqualTo(0);
    assertThat(window.lastPeriodIndex).isEqualTo(1);
    Timeline.Period period =
        infoAfterBundling.timeline.getPeriod(/* periodIndex= */ 0, new Timeline.Period());
    assertThat(period.durationUs)
        .isEqualTo(
            2500 + FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US);
    assertThat(period.windowIndex).isEqualTo(0);
    infoAfterBundling.timeline.getPeriod(/* periodIndex= */ 1, period);
    assertThat(period.durationUs).isEqualTo(2500);
    assertThat(period.windowIndex).isEqualTo(0);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetMediaItemsMetadata_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setPlaylistMetadata(new MediaMetadata.Builder().setArtist("artist").build())
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_METADATA)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.mediaMetadata).isEqualTo(MediaMetadata.EMPTY);
    assertThat(infoAfterBundling.playlistMetadata).isEqualTo(MediaMetadata.EMPTY);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetVolume_filtersInformation() {
    PlayerInfo playerInfo = new PlayerInfo.Builder(PlayerInfo.DEFAULT).setVolume(0.5f).build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_VOLUME)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.volume).isEqualTo(1f);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetDeviceVolume_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT).setDeviceVolume(10).setDeviceMuted(true).build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_DEVICE_VOLUME)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.deviceVolume).isEqualTo(0);
    assertThat(infoAfterBundling.deviceMuted).isFalse();
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetAudioAttributes_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setAudioAttributes(
                new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build())
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.audioAttributes).isEqualTo(AudioAttributes.DEFAULT);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetText_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setCues(new CueGroup(/* cues= */ ImmutableList.of(), /* presentationTimeUs= */ 1234))
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_TEXT)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ false)
                .toBundle());

    assertThat(infoAfterBundling.cueGroup).isEqualTo(CueGroup.EMPTY_TIME_ZERO);
  }

  @Test
  public void toBundleFromBundle_withoutCommandGetTracks_filtersInformation() {
    PlayerInfo playerInfo =
        new PlayerInfo.Builder(PlayerInfo.DEFAULT)
            .setCurrentTracks(
                new Tracks(
                    ImmutableList.of(
                        new Tracks.Group(
                            new TrackGroup(
                                new Format.Builder().setSampleMimeType(AUDIO_AAC).build()),
                            /* adaptiveSupported= */ false,
                            new int[] {C.FORMAT_EXCEEDS_CAPABILITIES},
                            /* trackSelected= */ new boolean[] {true}))))
            .build();

    PlayerInfo infoAfterBundling =
        PlayerInfo.fromBundle(
            playerInfo
                .filterByAvailableCommands(
                    new Player.Commands.Builder()
                        .addAllCommands()
                        .remove(Player.COMMAND_GET_TRACKS)
                        .build(),
                    /* excludeTimeline= */ false,
                    /* excludeTracks= */ true)
                .toBundle());

    assertThat(infoAfterBundling.currentTracks).isEqualTo(Tracks.EMPTY);
  }

  @Test
  public void toBundleFromBundle_withDefaultValues_restoresAllData() {
    PlayerInfo roundTripValue = PlayerInfo.fromBundle(PlayerInfo.DEFAULT.toBundle());

    assertThat(roundTripValue.oldPositionInfo).isEqualTo(PlayerInfo.DEFAULT.oldPositionInfo);
    assertThat(roundTripValue.newPositionInfo).isEqualTo(PlayerInfo.DEFAULT.newPositionInfo);
    assertThat(roundTripValue.sessionPositionInfo)
        .isEqualTo(PlayerInfo.DEFAULT.sessionPositionInfo);
    assertThat(roundTripValue.timeline).isEqualTo(PlayerInfo.DEFAULT.timeline);
    assertThat(roundTripValue.timelineChangeReason)
        .isEqualTo(PlayerInfo.DEFAULT.timelineChangeReason);
    assertThat(roundTripValue.mediaMetadata).isEqualTo(PlayerInfo.DEFAULT.mediaMetadata);
    assertThat(roundTripValue.playlistMetadata).isEqualTo(PlayerInfo.DEFAULT.playlistMetadata);
    assertThat(roundTripValue.volume).isEqualTo(PlayerInfo.DEFAULT.volume);
    assertThat(roundTripValue.deviceVolume).isEqualTo(PlayerInfo.DEFAULT.deviceVolume);
    assertThat(roundTripValue.deviceMuted).isEqualTo(PlayerInfo.DEFAULT.deviceMuted);
    assertThat(roundTripValue.audioAttributes).isEqualTo(PlayerInfo.DEFAULT.audioAttributes);
    assertThat(roundTripValue.cueGroup).isEqualTo(PlayerInfo.DEFAULT.cueGroup);
    assertThat(roundTripValue.currentTracks).isEqualTo(PlayerInfo.DEFAULT.currentTracks);
    assertThat(roundTripValue.deviceInfo).isEqualTo(PlayerInfo.DEFAULT.deviceInfo);
    assertThat(roundTripValue.discontinuityReason)
        .isEqualTo(PlayerInfo.DEFAULT.discontinuityReason);
    assertThat(roundTripValue.isLoading).isEqualTo(PlayerInfo.DEFAULT.isLoading);
    assertThat(roundTripValue.isPlaying).isEqualTo(PlayerInfo.DEFAULT.isPlaying);
    assertThat(roundTripValue.maxSeekToPreviousPositionMs)
        .isEqualTo(PlayerInfo.DEFAULT.maxSeekToPreviousPositionMs);
    assertThat(roundTripValue.mediaItemTransitionReason)
        .isEqualTo(PlayerInfo.DEFAULT.mediaItemTransitionReason);
    assertThat(roundTripValue.playbackParameters).isEqualTo(PlayerInfo.DEFAULT.playbackParameters);
    assertThat(roundTripValue.playbackState).isEqualTo(PlayerInfo.DEFAULT.playbackState);
    assertThat(roundTripValue.playbackSuppressionReason)
        .isEqualTo(PlayerInfo.DEFAULT.playbackSuppressionReason);
    assertThat(roundTripValue.playerError).isEqualTo(PlayerInfo.DEFAULT.playerError);
    assertThat(roundTripValue.playWhenReady).isEqualTo(PlayerInfo.DEFAULT.playWhenReady);
    assertThat(roundTripValue.playWhenReadyChangeReason)
        .isEqualTo(PlayerInfo.DEFAULT.playWhenReadyChangeReason);
    assertThat(roundTripValue.repeatMode).isEqualTo(PlayerInfo.DEFAULT.repeatMode);
    assertThat(roundTripValue.seekBackIncrementMs)
        .isEqualTo(PlayerInfo.DEFAULT.seekBackIncrementMs);
    assertThat(roundTripValue.seekForwardIncrementMs)
        .isEqualTo(PlayerInfo.DEFAULT.seekForwardIncrementMs);
    assertThat(roundTripValue.shuffleModeEnabled).isEqualTo(PlayerInfo.DEFAULT.shuffleModeEnabled);
    assertThat(roundTripValue.trackSelectionParameters)
        .isEqualTo(PlayerInfo.DEFAULT.trackSelectionParameters);
    assertThat(roundTripValue.videoSize).isEqualTo(PlayerInfo.DEFAULT.videoSize);
  }

  @Test
  public void toBundleForRemoteProcess_withDefaultValues_omitsAllData() {
    Bundle bundle =
        PlayerInfo.DEFAULT.toBundleForRemoteProcess(
            /* controllerInterfaceVersion= */ Integer.MAX_VALUE);

    assertThat(bundle.isEmpty()).isTrue();
  }

  @Test
  public void
      toBundleForRemoteProcess_withDefaultValuesForControllerInterfaceBefore3_includesPositionInfos() {
    // Controller before version 3 uses invalid default values for indices in (Session)PositionInfo.
    // The Bundle should always include these fields to avoid using the invalid defaults.
    Bundle bundle =
        PlayerInfo.DEFAULT.toBundleForRemoteProcess(/* controllerInterfaceVersion= */ 2);

    assertThat(bundle.keySet())
        .containsAtLeast(
            PlayerInfo.FIELD_SESSION_POSITION_INFO,
            PlayerInfo.FIELD_NEW_POSITION_INFO,
            PlayerInfo.FIELD_OLD_POSITION_INFO);
  }
}
