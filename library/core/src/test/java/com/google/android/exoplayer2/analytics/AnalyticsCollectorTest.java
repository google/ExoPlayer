/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics;

import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallbackException;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerRunnable;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.FakeAudioRenderer;
import com.google.android.exoplayer2.testutil.FakeExoMediaDrm;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.FakeVideoRenderer;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration test for {@link AnalyticsCollector}. */
@RunWith(AndroidJUnit4.class)
public final class AnalyticsCollectorTest {

  private static final String TAG = "AnalyticsCollectorTest";

  private static final int EVENT_PLAYER_STATE_CHANGED = 0;
  private static final int EVENT_TIMELINE_CHANGED = 1;
  private static final int EVENT_POSITION_DISCONTINUITY = 2;
  private static final int EVENT_SEEK_STARTED = 3;
  private static final int EVENT_SEEK_PROCESSED = 4;
  private static final int EVENT_PLAYBACK_PARAMETERS_CHANGED = 5;
  private static final int EVENT_REPEAT_MODE_CHANGED = 6;
  private static final int EVENT_SHUFFLE_MODE_CHANGED = 7;
  private static final int EVENT_LOADING_CHANGED = 8;
  private static final int EVENT_PLAYER_ERROR = 9;
  private static final int EVENT_TRACKS_CHANGED = 10;
  private static final int EVENT_LOAD_STARTED = 11;
  private static final int EVENT_LOAD_COMPLETED = 12;
  private static final int EVENT_LOAD_CANCELED = 13;
  private static final int EVENT_LOAD_ERROR = 14;
  private static final int EVENT_DOWNSTREAM_FORMAT_CHANGED = 15;
  private static final int EVENT_UPSTREAM_DISCARDED = 16;
  private static final int EVENT_BANDWIDTH_ESTIMATE = 17;
  private static final int EVENT_SURFACE_SIZE_CHANGED = 18;
  private static final int EVENT_METADATA = 19;
  private static final int EVENT_DECODER_ENABLED = 20;
  private static final int EVENT_DECODER_INIT = 21;
  private static final int EVENT_DECODER_FORMAT_CHANGED = 22;
  private static final int EVENT_DECODER_DISABLED = 23;
  private static final int EVENT_AUDIO_ENABLED = 24;
  private static final int EVENT_AUDIO_DECODER_INIT = 25;
  private static final int EVENT_AUDIO_INPUT_FORMAT_CHANGED = 26;
  private static final int EVENT_AUDIO_DISABLED = 27;
  private static final int EVENT_AUDIO_SESSION_ID = 28;
  private static final int EVENT_AUDIO_POSITION_ADVANCING = 29;
  private static final int EVENT_AUDIO_UNDERRUN = 30;
  private static final int EVENT_VIDEO_ENABLED = 31;
  private static final int EVENT_VIDEO_DECODER_INIT = 32;
  private static final int EVENT_VIDEO_INPUT_FORMAT_CHANGED = 33;
  private static final int EVENT_DROPPED_FRAMES = 34;
  private static final int EVENT_VIDEO_DISABLED = 35;
  private static final int EVENT_RENDERED_FIRST_FRAME = 36;
  private static final int EVENT_VIDEO_FRAME_PROCESSING_OFFSET = 37;
  private static final int EVENT_VIDEO_SIZE_CHANGED = 38;
  private static final int EVENT_DRM_KEYS_LOADED = 39;
  private static final int EVENT_DRM_ERROR = 40;
  private static final int EVENT_DRM_KEYS_RESTORED = 41;
  private static final int EVENT_DRM_KEYS_REMOVED = 42;
  private static final int EVENT_DRM_SESSION_ACQUIRED = 43;
  private static final int EVENT_DRM_SESSION_RELEASED = 44;

  private static final UUID DRM_SCHEME_UUID =
      UUID.nameUUIDFromBytes(TestUtil.createByteArray(7, 8, 9));

  public static final DrmInitData DRM_DATA_1 =
      new DrmInitData(
          new DrmInitData.SchemeData(
              DRM_SCHEME_UUID,
              ExoPlayerTestRunner.VIDEO_FORMAT.sampleMimeType,
              /* data= */ TestUtil.createByteArray(1, 2, 3)));
  public static final DrmInitData DRM_DATA_2 =
      new DrmInitData(
          new DrmInitData.SchemeData(
              DRM_SCHEME_UUID,
              ExoPlayerTestRunner.VIDEO_FORMAT.sampleMimeType,
              /* data= */ TestUtil.createByteArray(4, 5, 6)));
  private static final Format VIDEO_FORMAT_DRM_1 =
      ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setDrmInitData(DRM_DATA_1).build();

  private static final int TIMEOUT_MS = 10_000;
  private static final Timeline SINGLE_PERIOD_TIMELINE = new FakeTimeline(/* windowCount= */ 1);
  private static final EventWindowAndPeriodId WINDOW_0 =
      new EventWindowAndPeriodId(/* windowIndex= */ 0, /* mediaPeriodId= */ null);
  private static final EventWindowAndPeriodId WINDOW_1 =
      new EventWindowAndPeriodId(/* windowIndex= */ 1, /* mediaPeriodId= */ null);

  private final DrmSessionManager drmSessionManager =
      new DefaultDrmSessionManager.Builder()
          .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
          .setMultiSession(true)
          .build(new EmptyDrmCallback());

  private EventWindowAndPeriodId period0;
  private EventWindowAndPeriodId period1;
  private EventWindowAndPeriodId period0Seq0;
  private EventWindowAndPeriodId period1Seq1;
  private EventWindowAndPeriodId period0Seq1;
  private EventWindowAndPeriodId period1Seq0;
  private EventWindowAndPeriodId period1Seq2;
  private EventWindowAndPeriodId window0Period1Seq0;
  private EventWindowAndPeriodId window1Period0Seq1;

  @Test
  public void emptyTimeline() throws Exception {
    FakeMediaSource mediaSource =
        new FakeMediaSource(
            Timeline.EMPTY, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady */, WINDOW_0 /* BUFFERING */, WINDOW_0 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, WINDOW_0 /* SOURCE_UPDATE */)
        .inOrder();
    listener.assertNoMoreEvents();
  }

  @Test
  public void singlePeriod() throws Exception {
    FakeMediaSource mediaSource =
        new FakeMediaSource(
            SINGLE_PERIOD_TIMELINE,
            ExoPlayerTestRunner.VIDEO_FORMAT,
            ExoPlayerTestRunner.AUDIO_FORMAT);
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady */,
            WINDOW_0 /* BUFFERING */,
            period0 /* READY */,
            period0 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, WINDOW_0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0 /* started */, period0 /* stopped */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(WINDOW_0 /* manifest */, period0 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(WINDOW_0 /* manifest */, period0 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0 /* audio */, period0 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0 /* audio */, period0 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0 /* audio */, period0 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0 /* audio */, period0 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INIT)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_SESSION_ID)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET)).containsExactly(period0);
    listener.assertNoMoreEvents();
  }

  @Test
  public void automaticPeriodTransition() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE,
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE,
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT));
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady */,
            WINDOW_0 /* BUFFERING */,
            period0 /* READY */,
            period1 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, period0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0, period0, period0, period0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(
            period0 /* audio */, period0 /* video */, period1 /* audio */, period1 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0 /* audio */, period0 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(
            period0 /* audio */, period0 /* video */, period1 /* audio */, period1 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(
            period0 /* audio */, period0 /* video */, period1 /* audio */, period1 /* video */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INIT))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_SESSION_ID)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET)).containsExactly(period1);
    listener.assertNoMoreEvents();
  }

  @Test
  public void periodTransitionWithRendererChange() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.AUDIO_FORMAT));
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady */,
            WINDOW_0 /* BUFFERING */,
            period0 /* READY */,
            period1 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, period0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0, period0, period0, period0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0 /* video */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0 /* video */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0 /* video */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0 /* video */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(period0 /* video */);
    assertThat(listener.getEvents(EVENT_AUDIO_ENABLED)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INIT)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_SESSION_ID)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET)).containsExactly(period0);
    listener.assertNoMoreEvents();
  }

  @Test
  public void seekToOtherPeriod() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE,
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.AUDIO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            // Wait until second period has fully loaded to assert loading events without flakiness.
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .seek(/* windowIndex= */ 1, /* positionMs= */ 0)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource, actionSchedule);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* BUFFERING */,
            WINDOW_0 /* setPlayWhenReady=false */,
            period0 /* READY */,
            period1 /* BUFFERING */,
            period1 /* setPlayWhenReady=true */,
            period1 /* READY */,
            period1 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, period0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(period1);
    List<EventWindowAndPeriodId> loadingEvents = listener.getEvents(EVENT_LOADING_CHANGED);
    assertThat(loadingEvents).hasSize(4);
    assertThat(loadingEvents).containsAtLeast(period0, period0).inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(period0, period1, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0 /* video */, period0 /* audio */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0 /* video */, period0 /* audio */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0 /* video */, period0 /* audio */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0 /* video */, period0 /* audio */, period1 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED))
        .containsExactly(period0 /* video */, period0 /* audio */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_ENABLED)).containsExactly(period0, period1).inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INIT))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_SESSION_ID))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME)).containsExactly(period0);
    listener.assertNoMoreEvents();
  }

  @Test
  public void seekBackAfterReadingAhead() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE,
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT));
    long periodDurationMs =
        SINGLE_PERIOD_TIMELINE.getWindow(/* windowIndex= */ 0, new Window()).getDurationMs();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* windowIndex= */ 0, periodDurationMs)
            .seekAndWait(/* positionMs= */ 0)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource, actionSchedule);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            period0 /* READY */,
            period0 /* setPlayWhenReady=true */,
            period0 /* setPlayWhenReady=false */,
            period0 /* BUFFERING */,
            period0 /* READY */,
            period0 /* setPlayWhenReady=true */,
            period1Seq2 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, period0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0, period0, period0, period0, period0, period0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1Seq1 /* media */,
            period1Seq2 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            WINDOW_1 /* manifest */,
            period0 /* media */,
            period1Seq1 /* media */,
            period1Seq2 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0, period1Seq1, period1Seq1, period1Seq2, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0, period1, period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0, period1Seq1, period1Seq1, period1Seq2, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0, period1Seq1, period1Seq1, period1Seq2, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(period0, period0);
    assertThat(listener.getEvents(EVENT_AUDIO_ENABLED))
        .containsExactly(period1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INIT))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_SESSION_ID))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0, period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(period0, period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES))
        .containsExactly(period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0, period1Seq1, period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0, period1Seq1, period0, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(period0, period1Seq2)
        .inOrder();
    listener.assertNoMoreEvents();
  }

  @Test
  public void prepareNewSource() throws Exception {
    MediaSource mediaSource1 =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT);
    MediaSource mediaSource2 =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setMediaSources(/* resetPosition= */ false, mediaSource2)
            .waitForTimelineChanged()
            // Wait until loading started to prevent flakiness caused by loading finishing too fast.
            .waitForIsLoading(true)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource1, actionSchedule);

    // Populate all event ids with last timeline (after second prepare).
    populateEventIds(listener.lastReportedTimeline);
    // Populate event id of period 0, sequence 0 with timeline of initial preparation.
    period0Seq0 =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                listener.reportedTimelines.get(1).getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 0));
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* BUFFERING */,
            WINDOW_0 /* setPlayWhenReady=false */,
            period0Seq0 /* READY */,
            WINDOW_0 /* BUFFERING */,
            period0Seq1 /* setPlayWhenReady=true */,
            period0Seq1 /* READY */,
            period0Seq1 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(
            WINDOW_0 /* PLAYLIST_CHANGE */,
            WINDOW_0 /* SOURCE_UPDATE */,
            WINDOW_0 /* PLAYLIST_CHANGE */,
            WINDOW_0 /* SOURCE_UPDATE */);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0Seq0, period0Seq0, period0Seq1, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(
            period0Seq0 /* prepared */, WINDOW_0 /* setMediaSources */, period0Seq1 /* prepared */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */,
            WINDOW_0 /* manifest */,
            period0Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */,
            WINDOW_0 /* manifest */,
            period0Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period0Seq1);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(period0Seq1);
    listener.assertNoMoreEvents();
  }

  @Test
  public void reprepareAfterError() throws Exception {
    MediaSource mediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(ExoPlaybackException.createForSource(new IOException()))
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(/* positionMs= */ 0)
            .prepare()
            // Wait until loading started to assert loading events without flakiness.
            .waitForIsLoading(true)
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource, actionSchedule);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            period0Seq0 /* READY */,
            period0Seq0 /* IDLE */,
            period0Seq0 /* BUFFERING */,
            period0Seq0 /* setPlayWhenReady=true */,
            period0Seq0 /* READY */,
            period0Seq0 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* prepared */, WINDOW_0 /* prepared */);
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0Seq0, period0Seq0, period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED)).containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */,
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */,
            WINDOW_0 /* manifest */,
            period0Seq0 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED)).containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_DECODER_INIT)).containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(period0Seq0);
    listener.assertNoMoreEvents();
  }

  @Test
  public void dynamicTimelineChange() throws Exception {
    MediaSource childMediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT);
    final ConcatenatingMediaSource concatenatedMediaSource =
        new ConcatenatingMediaSource(childMediaSource, childMediaSource);
    long periodDurationMs =
        SINGLE_PERIOD_TIMELINE.getWindow(/* windowIndex= */ 0, new Window()).getDurationMs();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Ensure second period is already being read from.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ periodDurationMs)
            .executeRunnable(
                () ->
                    concatenatedMediaSource.moveMediaSource(
                        /* currentIndex= */ 0, /* newIndex= */ 1))
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(concatenatedMediaSource, actionSchedule);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            window0Period1Seq0 /* READY */,
            window0Period1Seq0 /* setPlayWhenReady=true */,
            window0Period1Seq0 /* setPlayWhenReady=false */,
            period1Seq0 /* setPlayWhenReady=true */,
            period1Seq0 /* BUFFERING */,
            period1Seq0 /* READY */,
            period1Seq0 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(
            WINDOW_0 /* PLAYLIST_CHANGED */,
            window0Period1Seq0 /* SOURCE_UPDATE (concatenated timeline replaces placeholder) */,
            period1Seq0 /* SOURCE_UPDATE (child sources in concatenating source moved) */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(
            window0Period1Seq0, window0Period1Seq0, window0Period1Seq0, window0Period1Seq0);
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED)).containsExactly(window0Period1Seq0);
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* manifest */, window0Period1Seq0 /* media */, window1Period0Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* manifest */, window0Period1Seq0 /* media */, window1Period0Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(window0Period1Seq0, window0Period1Seq0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(window0Period1Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED))
        .containsExactly(window0Period1Seq0, window0Period1Seq0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(window0Period1Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES))
        .containsExactly(window0Period1Seq0, period1Seq0)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(window0Period1Seq0, period1Seq0)
        .inOrder();
    listener.assertNoMoreEvents();
  }

  @Test
  public void playlistOperations() throws Exception {
    MediaSource fakeMediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .addMediaSources(fakeMediaSource)
            // Wait until second period has fully loaded to assert loading events without flakiness.
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .removeMediaItem(/* index= */ 0)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(fakeMediaSource, actionSchedule);

    // Populate event ids with second to last timeline that still contained both periods.
    populateEventIds(listener.reportedTimelines.get(listener.reportedTimelines.size() - 2));
    // Expect the second period with window index 0 and increased window sequence after the removal
    // moved the period to another window index.
    period0Seq1 =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                listener.lastReportedTimeline.getUidOfPeriod(/* periodIndex= */ 0),
                /* windowSequenceNumber= */ 1));
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            period0Seq0 /* READY */,
            period0Seq1 /* BUFFERING */,
            period0Seq1 /* READY */,
            period0Seq1 /* setPlayWhenReady=true */,
            period0Seq1 /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(
            WINDOW_0 /* PLAYLIST_CHANGED */,
            WINDOW_0 /* SOURCE_UPDATE (first item) */,
            period0Seq0 /* PLAYLIST_CHANGED (add) */,
            period0Seq0 /* SOURCE_UPDATE (second item) */,
            period0Seq1 /* PLAYLIST_CHANGED (remove) */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(period0Seq0, period0Seq0, period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(period0Seq0, period0Seq1, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(WINDOW_0 /* manifest */, period0Seq0 /* media */, period1Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(WINDOW_0 /* manifest */, period0Seq0 /* media */, period1Seq1 /* media */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0Seq0, period0Seq1, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED))
        .containsExactly(period0Seq0, period0Seq1, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(period0Seq1);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0Seq0, period0Seq1);
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(period0Seq1);
    listener.assertNoMoreEvents();
  }

  @Test
  public void adPlayback() throws Exception {
    long contentDurationsUs = 11 * C.MICROS_PER_SECOND;
    long windowOffsetInFirstPeriodUs =
        TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    AtomicReference<AdPlaybackState> adPlaybackState =
        new AtomicReference<>(
            FakeTimeline.createAdPlaybackState(
                /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */
                windowOffsetInFirstPeriodUs,
                windowOffsetInFirstPeriodUs + 5 * C.MICROS_PER_SECOND,
                C.TIME_END_OF_SOURCE));
    AtomicInteger playedAdCount = new AtomicInteger(0);
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                contentDurationsUs,
                adPlaybackState.get()));
    FakeMediaSource fakeMediaSource =
        new FakeMediaSource(
            adTimeline,
            DrmSessionManager.DUMMY,
            (unusedFormat, mediaPeriodId) -> {
              if (mediaPeriodId.isAd()) {
                return ImmutableList.of(oneByteSample(/* timeUs= */ 0), END_OF_STREAM_ITEM);
              } else {
                // Provide a single sample before and after the midroll ad and another after the
                // postroll.
                return ImmutableList.of(
                    oneByteSample(windowOffsetInFirstPeriodUs + C.MICROS_PER_SECOND),
                    oneByteSample(windowOffsetInFirstPeriodUs + 6 * C.MICROS_PER_SECOND),
                    oneByteSample(windowOffsetInFirstPeriodUs + contentDurationsUs),
                    END_OF_STREAM_ITEM);
              }
            },
            ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.addListener(
                        new Player.EventListener() {
                          @Override
                          public void onPositionDiscontinuity(
                              @Player.DiscontinuityReason int reason) {
                            if (!player.isPlayingAd()
                                && reason == Player.DISCONTINUITY_REASON_AD_INSERTION) {
                              // Finished playing ad. Marked as played.
                              adPlaybackState.set(
                                  adPlaybackState
                                      .get()
                                      .withPlayedAd(
                                          /* adGroupIndex= */ playedAdCount.getAndIncrement(),
                                          /* adIndexInAdGroup= */ 0));
                              fakeMediaSource.setNewSourceInfo(
                                  new FakeTimeline(
                                      new TimelineWindowDefinition(
                                          /* periodCount= */ 1,
                                          /* id= */ 0,
                                          /* isSeekable= */ true,
                                          /* isDynamic= */ false,
                                          contentDurationsUs,
                                          adPlaybackState.get())),
                                  /* sendManifestLoadEvents= */ false);
                            }
                          }
                        });
                  }
                })
            .pause()
            // Ensure everything is preloaded.
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForPlaybackState(Player.STATE_READY)
            // Wait in each content part to ensure previously triggered events get a chance to be
            // delivered. This prevents flakiness caused by playback progressing too fast.
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 3_000)
            .waitForPendingPlayerCommands()
            .playUntilPosition(/* windowIndex= */ 0, /* positionMs= */ 8_000)
            .waitForPendingPlayerCommands()
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            // Wait for final timeline change that marks post-roll played.
            .waitForTimelineChanged()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(fakeMediaSource, actionSchedule);

    Object periodUid = listener.lastReportedTimeline.getUidOfPeriod(/* periodIndex= */ 0);
    EventWindowAndPeriodId prerollAd =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventWindowAndPeriodId midrollAd =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid,
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventWindowAndPeriodId postrollAd =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid,
                /* adGroupIndex= */ 2,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventWindowAndPeriodId contentAfterPreroll =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(periodUid, /* windowSequenceNumber= */ 0, /* nextAdGroupIndex= */ 1));
    EventWindowAndPeriodId contentAfterMidroll =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(periodUid, /* windowSequenceNumber= */ 0, /* nextAdGroupIndex= */ 2));
    EventWindowAndPeriodId contentAfterPostroll =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid, /* windowSequenceNumber= */ 0, /* nextAdGroupIndex= */ C.INDEX_UNSET));
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            prerollAd /* READY */,
            prerollAd /* setPlayWhenReady=true */,
            contentAfterPreroll /* setPlayWhenReady=false */,
            contentAfterPreroll /* setPlayWhenReady=true */,
            contentAfterMidroll /* setPlayWhenReady=false */,
            contentAfterMidroll /* setPlayWhenReady=true */,
            contentAfterPostroll /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(
            WINDOW_0 /* PLAYLIST_CHANGED */,
            WINDOW_0 /* SOURCE_UPDATE (initial) */,
            contentAfterPreroll /* SOURCE_UPDATE (played preroll) */,
            contentAfterMidroll /* SOURCE_UPDATE (played midroll) */,
            contentAfterPostroll /* SOURCE_UPDATE (played postroll) */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(
            contentAfterPreroll, midrollAd, contentAfterMidroll, postrollAd, contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(
            prerollAd, prerollAd, prerollAd, prerollAd, prerollAd, prerollAd, prerollAd, prerollAd,
            prerollAd, prerollAd, prerollAd, prerollAd)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* content manifest */,
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* content manifest */,
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED)).containsExactly(prerollAd);
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(prerollAd);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES))
        .containsExactly(contentAfterPreroll, contentAfterMidroll, contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(
            prerollAd,
            contentAfterPreroll,
            midrollAd,
            contentAfterMidroll,
            postrollAd,
            contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(contentAfterPreroll, contentAfterMidroll, contentAfterPostroll)
        .inOrder();
    listener.assertNoMoreEvents();
  }

  @Test
  public void seekAfterMidroll() throws Exception {
    long windowOffsetInFirstPeriodUs =
        TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                10 * C.MICROS_PER_SECOND,
                FakeTimeline.createAdPlaybackState(
                    /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */
                    windowOffsetInFirstPeriodUs + 5 * C.MICROS_PER_SECOND)));
    FakeMediaSource fakeMediaSource =
        new FakeMediaSource(
            adTimeline,
            DrmSessionManager.DUMMY,
            (unusedFormat, mediaPeriodId) -> {
              if (mediaPeriodId.isAd()) {
                return ImmutableList.of(oneByteSample(/* timeUs= */ 0), END_OF_STREAM_ITEM);
              } else {
                // Provide a sample before the midroll and another after the seek point below (6s).
                return ImmutableList.of(
                    oneByteSample(windowOffsetInFirstPeriodUs + C.MICROS_PER_SECOND),
                    oneByteSample(windowOffsetInFirstPeriodUs + 7 * C.MICROS_PER_SECOND),
                    END_OF_STREAM_ITEM);
              }
            },
            ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            // Ensure everything is preloaded.
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            // Seek behind the midroll.
            .seek(6 * C.MICROS_PER_SECOND)
            // Wait until loading started again to assert loading events without flakiness.
            .waitForIsLoading(true)
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(fakeMediaSource, actionSchedule);

    Object periodUid = listener.lastReportedTimeline.getUidOfPeriod(/* periodIndex= */ 0);
    EventWindowAndPeriodId midrollAd =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0));
    EventWindowAndPeriodId contentBeforeMidroll =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(periodUid, /* windowSequenceNumber= */ 0, /* nextAdGroupIndex= */ 0));
    EventWindowAndPeriodId contentAfterMidroll =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                periodUid, /* windowSequenceNumber= */ 0, /* nextAdGroupIndex= */ C.INDEX_UNSET));
    assertThat(listener.getEvents(EVENT_PLAYER_STATE_CHANGED))
        .containsExactly(
            WINDOW_0 /* setPlayWhenReady=true */,
            WINDOW_0 /* setPlayWhenReady=false */,
            WINDOW_0 /* BUFFERING */,
            contentBeforeMidroll /* READY */,
            contentAfterMidroll /* BUFFERING */,
            midrollAd /* setPlayWhenReady=true */,
            midrollAd /* READY */,
            contentAfterMidroll /* ENDED */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TIMELINE_CHANGED))
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, WINDOW_0 /* SOURCE_UPDATE */);
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(
            contentAfterMidroll /* seek */,
            midrollAd /* seek adjustment */,
            contentAfterMidroll /* ad transition */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(contentBeforeMidroll);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(contentAfterMidroll);
    assertThat(listener.getEvents(EVENT_LOADING_CHANGED))
        .containsExactly(
            contentBeforeMidroll,
            contentBeforeMidroll,
            contentBeforeMidroll,
            contentBeforeMidroll,
            contentBeforeMidroll,
            contentBeforeMidroll,
            midrollAd,
            midrollAd)
        .inOrder();
    assertThat(listener.getEvents(EVENT_TRACKS_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll);
    assertThat(listener.getEvents(EVENT_LOAD_STARTED))
        .containsExactly(
            WINDOW_0 /* content manifest */,
            contentBeforeMidroll,
            midrollAd,
            contentAfterMidroll,
            contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_LOAD_COMPLETED))
        .containsExactly(
            WINDOW_0 /* content manifest */,
            contentBeforeMidroll,
            midrollAd,
            contentAfterMidroll,
            contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(contentBeforeMidroll, midrollAd)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(contentBeforeMidroll);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED))
        .containsExactly(contentBeforeMidroll, midrollAd)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INIT))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(contentBeforeMidroll);
    assertThat(listener.getEvents(EVENT_DROPPED_FRAMES)).containsExactly(contentAfterMidroll);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .containsExactly(contentAfterMidroll);
    listener.assertNoMoreEvents();
  }

  @Test
  public void notifyExternalEvents() throws Exception {
    MediaSource mediaSource = new FakeMediaSource(SINGLE_PERIOD_TIMELINE);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(SimpleExoPlayer player) {
                    player.getAnalyticsCollector().notifySeekStarted();
                  }
                })
            .seek(/* positionMs= */ 0)
            .play()
            .build();
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource, actionSchedule);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(period0);
  }

  @Test
  public void drmEvents_singlePeriod() throws Exception {
    MediaSource mediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, drmSessionManager, VIDEO_FORMAT_DRM_1);
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED)).containsExactly(period0);
    // The release event is lost because it's posted to "ExoPlayerTest thread" after that thread
    // has been quit during clean-up.
    assertThat(listener.getEvents(EVENT_DRM_SESSION_RELEASED)).isEmpty();
  }

  @Test
  public void drmEvents_periodWithSameDrmData_keysReused() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, drmSessionManager, VIDEO_FORMAT_DRM_1),
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, drmSessionManager, VIDEO_FORMAT_DRM_1));
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED)).containsExactly(period0);
    // The period1 release event is lost because it's posted to "ExoPlayerTest thread" after that
    // thread has been quit during clean-up.
    assertThat(listener.getEvents(EVENT_DRM_SESSION_RELEASED)).containsExactly(period0);
  }

  @Test
  public void drmEvents_periodWithDifferentDrmData_keysLoadedAgain() throws Exception {
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(SINGLE_PERIOD_TIMELINE, drmSessionManager, VIDEO_FORMAT_DRM_1),
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE,
                drmSessionManager,
                VIDEO_FORMAT_DRM_1.buildUpon().setDrmInitData(DRM_DATA_2).build()));
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED))
        .containsExactly(period0, period1)
        .inOrder();
    // The period1 release event is lost because it's posted to "ExoPlayerTest thread" after that
    // thread has been quit during clean-up.
    assertThat(listener.getEvents(EVENT_DRM_SESSION_RELEASED)).containsExactly(period0);
  }

  @Test
  public void drmEvents_errorHandling() throws Exception {
    DrmSessionManager failingDrmSessionManager =
        new DefaultDrmSessionManager.Builder().build(new FailingDrmCallback());
    MediaSource mediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, failingDrmSessionManager, VIDEO_FORMAT_DRM_1);
    TestAnalyticsListener listener = runAnalyticsTest(mediaSource);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_ERROR)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period0);
  }

  @Test
  public void onPlayerError_thrownDuringRendererEnableAtPeriodTransition_isReportedForNewPeriod()
      throws Exception {
    FakeMediaSource source0 =
        new FakeMediaSource(
            new FakeTimeline(/* windowCount= */ 1), ExoPlayerTestRunner.VIDEO_FORMAT);
    FakeMediaSource source1 =
        new FakeMediaSource(
            new FakeTimeline(/* windowCount= */ 1), ExoPlayerTestRunner.AUDIO_FORMAT);
    RenderersFactory renderersFactory =
        (eventHandler, videoListener, audioListener, textOutput, metadataOutput) ->
            new Renderer[] {
              new FakeRenderer(C.TRACK_TYPE_VIDEO),
              new FakeRenderer(C.TRACK_TYPE_AUDIO) {
                @Override
                protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
                    throws ExoPlaybackException {
                  // Fail when enabling the renderer. This will happen during the period transition.
                  throw createRendererException(
                      new IllegalStateException(), ExoPlayerTestRunner.AUDIO_FORMAT);
                }
              }
            };

    TestAnalyticsListener listener =
        runAnalyticsTest(
            new ConcatenatingMediaSource(source0, source1),
            /* actionSchedule= */ null,
            renderersFactory);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period1);
  }

  @Test
  public void onPlayerError_thrownDuringRenderAtPeriodTransition_isReportedForNewPeriod()
      throws Exception {
    FakeMediaSource source0 =
        new FakeMediaSource(
            new FakeTimeline(/* windowCount= */ 1), ExoPlayerTestRunner.VIDEO_FORMAT);
    FakeMediaSource source1 =
        new FakeMediaSource(
            new FakeTimeline(/* windowCount= */ 1), ExoPlayerTestRunner.AUDIO_FORMAT);
    RenderersFactory renderersFactory =
        (eventHandler, videoListener, audioListener, textOutput, metadataOutput) ->
            new Renderer[] {
              new FakeRenderer(C.TRACK_TYPE_VIDEO),
              new FakeRenderer(C.TRACK_TYPE_AUDIO) {
                @Override
                public void render(long positionUs, long realtimeUs) throws ExoPlaybackException {
                  // Fail when rendering the audio stream. This will happen during the period
                  // transition.
                  throw createRendererException(
                      new IllegalStateException(), ExoPlayerTestRunner.AUDIO_FORMAT);
                }
              }
            };

    TestAnalyticsListener listener =
        runAnalyticsTest(
            new ConcatenatingMediaSource(source0, source1),
            /* actionSchedule= */ null,
            renderersFactory);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period1);
  }

  @Test
  public void
      onPlayerError_thrownDuringRendererReplaceStreamAtPeriodTransition_isReportedForNewPeriod()
          throws Exception {
    FakeMediaSource source =
        new FakeMediaSource(
            new FakeTimeline(/* windowCount= */ 1), ExoPlayerTestRunner.AUDIO_FORMAT);
    RenderersFactory renderersFactory =
        (eventHandler, videoListener, audioListener, textOutput, metadataOutput) ->
            new Renderer[] {
              new FakeRenderer(C.TRACK_TYPE_AUDIO) {
                private int streamChangeCount = 0;

                @Override
                protected void onStreamChanged(
                    Format[] formats, long startPositionUs, long offsetUs)
                    throws ExoPlaybackException {
                  // Fail when changing streams for the second time. This will happen during the
                  // period transition (as the first time is when enabling the stream initially).
                  if (++streamChangeCount == 2) {
                    throw createRendererException(
                        new IllegalStateException(), ExoPlayerTestRunner.AUDIO_FORMAT);
                  }
                }
              }
            };

    TestAnalyticsListener listener =
        runAnalyticsTest(
            new ConcatenatingMediaSource(source, source),
            /* actionSchedule= */ null,
            renderersFactory);

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period1);
  }

  private void populateEventIds(Timeline timeline) {
    period0 =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    period0Seq0 = period0;
    period0Seq1 =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 0,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 1));
    window1Period0Seq1 =
        new EventWindowAndPeriodId(
            /* windowIndex= */ 1,
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 1));
    if (timeline.getPeriodCount() > 1) {
      period1 =
          new EventWindowAndPeriodId(
              /* windowIndex= */ 1,
              new MediaPeriodId(
                  timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
      period1Seq1 = period1;
      period1Seq0 =
          new EventWindowAndPeriodId(
              /* windowIndex= */ 1,
              new MediaPeriodId(
                  timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0));
      period1Seq2 =
          new EventWindowAndPeriodId(
              /* windowIndex= */ 1,
              new MediaPeriodId(
                  timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 2));
      window0Period1Seq0 =
          new EventWindowAndPeriodId(
              /* windowIndex= */ 0,
              new MediaPeriodId(
                  timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0));
    }
  }

  private static TestAnalyticsListener runAnalyticsTest(MediaSource mediaSource) throws Exception {
    return runAnalyticsTest(mediaSource, /* actionSchedule= */ null);
  }

  private static TestAnalyticsListener runAnalyticsTest(
      MediaSource mediaSource, @Nullable ActionSchedule actionSchedule) throws Exception {
    RenderersFactory renderersFactory =
        (eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput) ->
            new Renderer[] {
              new FakeVideoRenderer(eventHandler, videoRendererEventListener),
              new FakeAudioRenderer(eventHandler, audioRendererEventListener)
            };
    return runAnalyticsTest(mediaSource, actionSchedule, renderersFactory);
  }

  private static TestAnalyticsListener runAnalyticsTest(
      MediaSource mediaSource,
      @Nullable ActionSchedule actionSchedule,
      RenderersFactory renderersFactory)
      throws Exception {
    TestAnalyticsListener listener = new TestAnalyticsListener();
    try {
      new ExoPlayerTestRunner.Builder(ApplicationProvider.getApplicationContext())
          .setMediaSources(mediaSource)
          .setRenderersFactory(renderersFactory)
          .setAnalyticsListener(listener)
          .setActionSchedule(actionSchedule)
          .build()
          .start()
          .blockUntilActionScheduleFinished(TIMEOUT_MS)
          .blockUntilEnded(TIMEOUT_MS);
    } catch (ExoPlaybackException e) {
      // Ignore ExoPlaybackException as these may be expected.
    }
    return listener;
  }

  private static final class EventWindowAndPeriodId {

    private final int windowIndex;
    @Nullable private final MediaPeriodId mediaPeriodId;

    public EventWindowAndPeriodId(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (!(other instanceof EventWindowAndPeriodId)) {
        return false;
      }
      EventWindowAndPeriodId event = (EventWindowAndPeriodId) other;
      return windowIndex == event.windowIndex && Util.areEqual(mediaPeriodId, event.mediaPeriodId);
    }

    @Override
    public String toString() {
      return mediaPeriodId != null
          ? "{"
              + "window="
              + windowIndex
              + ", sequence="
              + mediaPeriodId.windowSequenceNumber
              + (mediaPeriodId.adGroupIndex != C.INDEX_UNSET
                  ? ", adGroup="
                      + mediaPeriodId.adGroupIndex
                      + ", adIndexInGroup="
                      + mediaPeriodId.adIndexInAdGroup
                  : "")
              + ", period.hashCode="
              + mediaPeriodId.periodUid.hashCode()
              + (mediaPeriodId.nextAdGroupIndex != C.INDEX_UNSET
                  ? ", nextAdGroup=" + mediaPeriodId.nextAdGroupIndex
                  : "")
              + '}'
          : "{" + "window=" + windowIndex + ", period = null}";
    }

    @Override
    public int hashCode() {
      return 31 * windowIndex + (mediaPeriodId == null ? 0 : mediaPeriodId.hashCode());
    }
  }

  private static final class TestAnalyticsListener implements AnalyticsListener {

    public Timeline lastReportedTimeline;

    private final List<Timeline> reportedTimelines;
    private final ArrayList<ReportedEvent> reportedEvents;

    public TestAnalyticsListener() {
      reportedEvents = new ArrayList<>();
      reportedTimelines = new ArrayList<>();
      lastReportedTimeline = Timeline.EMPTY;
    }

    public List<EventWindowAndPeriodId> getEvents(int eventType) {
      ArrayList<EventWindowAndPeriodId> eventTimes = new ArrayList<>();
      Iterator<ReportedEvent> eventIterator = reportedEvents.iterator();
      while (eventIterator.hasNext()) {
        ReportedEvent event = eventIterator.next();
        if (event.eventType == eventType) {
          eventTimes.add(event.eventWindowAndPeriodId);
          eventIterator.remove();
        }
      }
      return eventTimes;
    }

    public void assertNoMoreEvents() {
      assertThat(reportedEvents).isEmpty();
    }

    @SuppressWarnings("deprecation") // Testing deprecated behaviour.
    @Override
    public void onPlayerStateChanged(
        EventTime eventTime, boolean playWhenReady, @Player.State int playbackState) {
      reportedEvents.add(new ReportedEvent(EVENT_PLAYER_STATE_CHANGED, eventTime));
    }

    @Override
    public void onTimelineChanged(EventTime eventTime, int reason) {
      lastReportedTimeline = eventTime.timeline;
      reportedTimelines.add(eventTime.timeline);
      reportedEvents.add(new ReportedEvent(EVENT_TIMELINE_CHANGED, eventTime));
    }

    @Override
    public void onPositionDiscontinuity(EventTime eventTime, int reason) {
      reportedEvents.add(new ReportedEvent(EVENT_POSITION_DISCONTINUITY, eventTime));
    }

    @Override
    public void onSeekStarted(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_SEEK_STARTED, eventTime));
    }

    @SuppressWarnings("deprecation") // Testing deprecated behaviour.
    @Override
    public void onSeekProcessed(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_SEEK_PROCESSED, eventTime));
    }

    @Override
    public void onPlaybackParametersChanged(
        EventTime eventTime, PlaybackParameters playbackParameters) {
      reportedEvents.add(new ReportedEvent(EVENT_PLAYBACK_PARAMETERS_CHANGED, eventTime));
    }

    @Override
    public void onRepeatModeChanged(EventTime eventTime, int repeatMode) {
      reportedEvents.add(new ReportedEvent(EVENT_REPEAT_MODE_CHANGED, eventTime));
    }

    @Override
    public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
      reportedEvents.add(new ReportedEvent(EVENT_SHUFFLE_MODE_CHANGED, eventTime));
    }

    @Override
    public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
      reportedEvents.add(new ReportedEvent(EVENT_LOADING_CHANGED, eventTime));
    }

    @Override
    public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
      reportedEvents.add(new ReportedEvent(EVENT_PLAYER_ERROR, eventTime));
    }

    @Override
    public void onTracksChanged(
        EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      reportedEvents.add(new ReportedEvent(EVENT_TRACKS_CHANGED, eventTime));
    }

    @Override
    public void onLoadStarted(
        EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      reportedEvents.add(new ReportedEvent(EVENT_LOAD_STARTED, eventTime));
    }

    @Override
    public void onLoadCompleted(
        EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      reportedEvents.add(new ReportedEvent(EVENT_LOAD_COMPLETED, eventTime));
    }

    @Override
    public void onLoadCanceled(
        EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      reportedEvents.add(new ReportedEvent(EVENT_LOAD_CANCELED, eventTime));
    }

    @Override
    public void onLoadError(
        EventTime eventTime,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      reportedEvents.add(new ReportedEvent(EVENT_LOAD_ERROR, eventTime));
    }

    @Override
    public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
      reportedEvents.add(new ReportedEvent(EVENT_DOWNSTREAM_FORMAT_CHANGED, eventTime));
    }

    @Override
    public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
      reportedEvents.add(new ReportedEvent(EVENT_UPSTREAM_DISCARDED, eventTime));
    }

    @Override
    public void onBandwidthEstimate(
        EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
      reportedEvents.add(new ReportedEvent(EVENT_BANDWIDTH_ESTIMATE, eventTime));
    }

    @Override
    public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
      reportedEvents.add(new ReportedEvent(EVENT_SURFACE_SIZE_CHANGED, eventTime));
    }

    @Override
    public void onMetadata(EventTime eventTime, Metadata metadata) {
      reportedEvents.add(new ReportedEvent(EVENT_METADATA, eventTime));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDecoderEnabled(
        EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
      reportedEvents.add(new ReportedEvent(EVENT_DECODER_ENABLED, eventTime));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDecoderInitialized(
        EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
      reportedEvents.add(new ReportedEvent(EVENT_DECODER_INIT, eventTime));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
      reportedEvents.add(new ReportedEvent(EVENT_DECODER_FORMAT_CHANGED, eventTime));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDecoderDisabled(
        EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
      reportedEvents.add(new ReportedEvent(EVENT_DECODER_DISABLED, eventTime));
    }

    @Override
    public void onAudioEnabled(EventTime eventTime, DecoderCounters counters) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_ENABLED, eventTime));
    }

    @Override
    public void onAudioDecoderInitialized(
        EventTime eventTime, String decoderName, long initializationDurationMs) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_DECODER_INIT, eventTime));
    }

    @Override
    public void onAudioInputFormatChanged(EventTime eventTime, Format format) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_INPUT_FORMAT_CHANGED, eventTime));
    }

    @Override
    public void onAudioDisabled(EventTime eventTime, DecoderCounters counters) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_DISABLED, eventTime));
    }

    @Override
    public void onAudioSessionId(EventTime eventTime, int audioSessionId) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_SESSION_ID, eventTime));
    }

    @Override
    public void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_POSITION_ADVANCING, eventTime));
    }

    @Override
    public void onAudioUnderrun(
        EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_UNDERRUN, eventTime));
    }

    @Override
    public void onVideoEnabled(EventTime eventTime, DecoderCounters counters) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_ENABLED, eventTime));
    }

    @Override
    public void onVideoDecoderInitialized(
        EventTime eventTime, String decoderName, long initializationDurationMs) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_DECODER_INIT, eventTime));
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_INPUT_FORMAT_CHANGED, eventTime));
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
      reportedEvents.add(new ReportedEvent(EVENT_DROPPED_FRAMES, eventTime));
    }

    @Override
    public void onVideoDisabled(EventTime eventTime, DecoderCounters counters) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_DISABLED, eventTime));
    }

    @Override
    public void onVideoFrameProcessingOffset(
        EventTime eventTime, long totalProcessingOffsetUs, int frameCount) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_FRAME_PROCESSING_OFFSET, eventTime));
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Surface surface) {
      reportedEvents.add(new ReportedEvent(EVENT_RENDERED_FIRST_FRAME, eventTime));
    }

    @Override
    public void onVideoSizeChanged(
        EventTime eventTime,
        int width,
        int height,
        int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_SIZE_CHANGED, eventTime));
    }

    @Override
    public void onDrmSessionAcquired(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_SESSION_ACQUIRED, eventTime));
    }

    @Override
    public void onDrmKeysLoaded(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_KEYS_LOADED, eventTime));
    }

    @Override
    public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_ERROR, eventTime));
    }

    @Override
    public void onDrmKeysRestored(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_KEYS_RESTORED, eventTime));
    }

    @Override
    public void onDrmKeysRemoved(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_KEYS_REMOVED, eventTime));
    }

    @Override
    public void onDrmSessionReleased(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_SESSION_RELEASED, eventTime));
    }

    private static final class ReportedEvent {

      public final int eventType;
      public final EventWindowAndPeriodId eventWindowAndPeriodId;

      public ReportedEvent(int eventType, EventTime eventTime) {
        this.eventType = eventType;
        this.eventWindowAndPeriodId =
            new EventWindowAndPeriodId(eventTime.windowIndex, eventTime.mediaPeriodId);
      }

      @Override
      public String toString() {
        return "{" + "type=" + eventType + ", windowAndPeriodId=" + eventWindowAndPeriodId + '}';
      }
    }
  }

  /**
   * A {@link MediaDrmCallback} that returns empty byte arrays for both {@link
   * #executeProvisionRequest(UUID, ExoMediaDrm.ProvisionRequest)} and {@link
   * #executeKeyRequest(UUID, ExoMediaDrm.KeyRequest)}.
   */
  private static final class EmptyDrmCallback implements MediaDrmCallback {
    @Override
    public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request)
        throws MediaDrmCallbackException {
      return new byte[0];
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request)
        throws MediaDrmCallbackException {
      return new byte[0];
    }
  }

  /**
   * A {@link MediaDrmCallback} that throws exceptions for both {@link
   * #executeProvisionRequest(UUID, ExoMediaDrm.ProvisionRequest)} and {@link
   * #executeKeyRequest(UUID, ExoMediaDrm.KeyRequest)}.
   */
  private static final class FailingDrmCallback implements MediaDrmCallback {
    @Override
    public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request)
        throws MediaDrmCallbackException {
      throw new RuntimeException("executeProvision failed");
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request)
        throws MediaDrmCallbackException {
      throw new RuntimeException("executeKey failed");
    }
  }
}
