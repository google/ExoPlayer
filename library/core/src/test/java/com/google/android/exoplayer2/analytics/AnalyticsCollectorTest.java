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

import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_AUDIO_DECODER_INITIALIZED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_AUDIO_DISABLED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_AUDIO_ENABLED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_AUDIO_INPUT_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_AUDIO_POSITION_ADVANCING;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DRM_KEYS_LOADED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DRM_SESSION_ACQUIRED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DRM_SESSION_MANAGER_ERROR;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DRM_SESSION_RELEASED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DROPPED_VIDEO_FRAMES;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_IS_LOADING_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_IS_PLAYING_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_LOAD_COMPLETED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_LOAD_ERROR;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_LOAD_STARTED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_MEDIA_ITEM_TRANSITION;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYER_ERROR;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_POSITION_DISCONTINUITY;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_RENDERED_FIRST_FRAME;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TIMELINE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TRACKS_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_DECODER_INITIALIZED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_DISABLED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_ENABLED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_FRAME_PROCESSING_OFFSET;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_INPUT_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_VIDEO_SIZE_CHANGED;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
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
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.MediaDrmCallbackException;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
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
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowLooper;

/** Integration test for {@link AnalyticsCollector}. */
@RunWith(AndroidJUnit4.class)
public final class AnalyticsCollectorTest {

  private static final String TAG = "AnalyticsCollectorTest";

  // Deprecated event constants.
  private static final long EVENT_PLAYER_STATE_CHANGED = 1L << 63;
  private static final long EVENT_SEEK_STARTED = 1L << 62;
  private static final long EVENT_SEEK_PROCESSED = 1L << 61;
  private static final long EVENT_DECODER_ENABLED = 1L << 60;
  private static final long EVENT_DECODER_INIT = 1L << 59;
  private static final long EVENT_DECODER_FORMAT_CHANGED = 1L << 58;
  private static final long EVENT_DECODER_DISABLED = 1L << 57;

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
  private static final Timeline SINGLE_PERIOD_TIMELINE = new FakeTimeline();
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
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, period0 /* SOURCE_UPDATE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
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
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INITIALIZED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period0);
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
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(period0, period0)
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
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INITIALIZED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period1);
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
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(period0, period0)
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
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INITIALIZED)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING)).containsExactly(period1);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period0);
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
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(period0, period0)
        .inOrder();
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
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INITIALIZED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING))
        .containsExactly(period0, period1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED)).containsExactly(period0);
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
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(period0, period0, period0, period0)
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
    assertThat(listener.getEvents(EVENT_AUDIO_DECODER_INITIALIZED))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_POSITION_ADVANCING))
        .containsExactly(period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_AUDIO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED)).containsExactly(period0, period0);
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(period0, period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0, period1Seq1, period1Seq2)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES))
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
            period0Seq0 /* SOURCE_UPDATE */,
            WINDOW_0 /* PLAYLIST_CHANGE */,
            period0Seq1 /* SOURCE_UPDATE */);
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(WINDOW_0 /* REMOVE */);
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
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
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period0Seq1);
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
        .containsExactly(WINDOW_0 /* prepared */, period0Seq0 /* prepared */);
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
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
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period0Seq0);
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
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(window0Period1Seq0, window0Period1Seq0, period1Seq0, period1Seq0);
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
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(window0Period1Seq0, window1Period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(window0Period1Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES))
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
            period0Seq0 /* SOURCE_UPDATE (first item) */,
            period0Seq0 /* PLAYLIST_CHANGED (add) */,
            period0Seq0 /* SOURCE_UPDATE (second item) */,
            period0Seq1 /* PLAYLIST_CHANGED (remove) */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(period0Seq1 /* REMOVE */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
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
        .containsExactly(period0Seq0, period1Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_ENABLED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_INIT))
        .containsExactly(period0Seq0, period1Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period1Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_DECODER_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_VIDEO_ENABLED))
        .containsExactly(period0Seq0, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(period0Seq0, period1Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(period0Seq0, period1Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(period0Seq0);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(period0Seq1);
    assertThat(listener.getEvents(EVENT_VIDEO_SIZE_CHANGED))
        .containsExactly(period0Seq0, period1Seq1, period0Seq1)
        .inOrder();
    assertThat(listener.getEvents(EVENT_RENDERED_FIRST_FRAME))
        .containsExactly(period0Seq0, period1Seq1, period0Seq1);
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
                /* adsPerAdGroup= */ 1,
                /* adGroupTimesUs...= */ windowOffsetInFirstPeriodUs,
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
            DrmSessionManager.DRM_UNSUPPORTED,
            (unusedFormat, mediaPeriodId) -> {
              if (mediaPeriodId.isAd()) {
                return ImmutableList.of(
                    oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM);
              } else {
                // Provide a single sample before and after the midroll ad and another after the
                // postroll.
                return ImmutableList.of(
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + C.MICROS_PER_SECOND, C.BUFFER_FLAG_KEY_FRAME),
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + 6 * C.MICROS_PER_SECOND,
                        C.BUFFER_FLAG_KEY_FRAME),
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + contentDurationsUs, C.BUFFER_FLAG_KEY_FRAME),
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
                        new Player.Listener() {
                          @Override
                          public void onPositionDiscontinuity(
                              Player.PositionInfo oldPosition,
                              Player.PositionInfo newPosition,
                              @Player.DiscontinuityReason int reason) {
                            if (!player.isPlayingAd()
                                && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
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
            prerollAd /* SOURCE_UPDATE (initial) */,
            contentAfterPreroll /* SOURCE_UPDATE (played preroll) */,
            contentAfterMidroll /* SOURCE_UPDATE (played midroll) */,
            contentAfterPostroll /* SOURCE_UPDATE (played postroll) */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(
            contentAfterPreroll, midrollAd, contentAfterMidroll, postrollAd, contentAfterPostroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(prerollAd, prerollAd)
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
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
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
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES))
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
            DrmSessionManager.DRM_UNSUPPORTED,
            (unusedFormat, mediaPeriodId) -> {
              if (mediaPeriodId.isAd()) {
                return ImmutableList.of(
                    oneByteSample(/* timeUs= */ 0, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM);
              } else {
                // Provide a sample before the midroll and another after the seek point below (6s).
                return ImmutableList.of(
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + C.MICROS_PER_SECOND, C.BUFFER_FLAG_KEY_FRAME),
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + 7 * C.MICROS_PER_SECOND,
                        C.BUFFER_FLAG_KEY_FRAME),
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
        .containsExactly(WINDOW_0 /* PLAYLIST_CHANGED */, contentBeforeMidroll /* SOURCE_UPDATE */);
    assertThat(listener.getEvents(EVENT_POSITION_DISCONTINUITY))
        .containsExactly(
            contentAfterMidroll /* seek */,
            midrollAd /* seek adjustment */,
            contentAfterMidroll /* ad transition */)
        .inOrder();
    assertThat(listener.getEvents(EVENT_SEEK_STARTED)).containsExactly(contentBeforeMidroll);
    assertThat(listener.getEvents(EVENT_SEEK_PROCESSED)).containsExactly(contentAfterMidroll);
    assertThat(listener.getEvents(EVENT_IS_LOADING_CHANGED))
        .containsExactly(
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
    assertThat(listener.getEvents(EVENT_VIDEO_DECODER_INITIALIZED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .containsExactly(contentBeforeMidroll, midrollAd, contentAfterMidroll)
        .inOrder();
    assertThat(listener.getEvents(EVENT_VIDEO_DISABLED)).containsExactly(contentBeforeMidroll);
    assertThat(listener.getEvents(EVENT_DROPPED_VIDEO_FRAMES)).containsExactly(contentAfterMidroll);
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
    assertThat(listener.getEvents(EVENT_DRM_SESSION_MANAGER_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED)).containsExactly(period0);
    // The release event is lost because it's posted to "ExoPlayerTest thread" after that thread
    // has been quit during clean-up.
    assertThat(listener.getEvents(EVENT_DRM_SESSION_RELEASED)).isEmpty();
  }

  @Test
  public void drmEvents_periodsWithSameDrmData_keysReusedButLoadEventReportedTwice()
      throws Exception {
    BlockingDrmCallback mediaDrmCallback = BlockingDrmCallback.returnsEmpty();
    DrmSessionManager blockingDrmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setMultiSession(true)
            .build(mediaDrmCallback);
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE, blockingDrmSessionManager, VIDEO_FORMAT_DRM_1),
            new FakeMediaSource(
                SINGLE_PERIOD_TIMELINE, blockingDrmSessionManager, VIDEO_FORMAT_DRM_1));
    TestAnalyticsListener listener =
        runAnalyticsTest(
            mediaSource,
            // Wait for the media to be fully buffered before unblocking the DRM key request. This
            // ensures both periods report the same load event (because period1's DRM session is
            // already preacquired by the time the key load completes).
            new ActionSchedule.Builder(TAG)
                .waitForIsLoading(false)
                .waitForIsLoading(true)
                .waitForIsLoading(false)
                .executeRunnable(mediaDrmCallback.keyCondition::open)
                .build());

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_SESSION_MANAGER_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED))
        .containsExactly(period0, period1)
        .inOrder();
    // This includes both period0 and period1 because period1's DrmSession was preacquired before
    // the key load completed. There's only one key load (a second would block forever). We can't
    // assume the order these events will arrive in because it depends on the iteration order of a
    // HashSet of EventDispatchers inside DefaultDrmSession.
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED)).containsExactly(period0, period1);
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
    assertThat(listener.getEvents(EVENT_DRM_SESSION_MANAGER_ERROR)).isEmpty();
    assertThat(listener.getEvents(EVENT_DRM_SESSION_ACQUIRED))
        .containsExactly(period0, period1)
        .inOrder();
    // The pre-fetched key load for period1 might complete before the blocking key load for period0,
    // so we can't assert the order:
    assertThat(listener.getEvents(EVENT_DRM_KEYS_LOADED)).containsExactly(period0, period1);
    // The period1 release event is lost because it's posted to "ExoPlayerTest thread" after that
    // thread has been quit during clean-up.
    assertThat(listener.getEvents(EVENT_DRM_SESSION_RELEASED)).containsExactly(period0);
  }

  @Test
  public void drmEvents_errorHandling() throws Exception {
    BlockingDrmCallback mediaDrmCallback = BlockingDrmCallback.alwaysFailing();
    DrmSessionManager failingDrmSessionManager =
        new DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(DRM_SCHEME_UUID, uuid -> new FakeExoMediaDrm())
            .setMultiSession(true)
            .build(mediaDrmCallback);
    MediaSource mediaSource =
        new FakeMediaSource(SINGLE_PERIOD_TIMELINE, failingDrmSessionManager, VIDEO_FORMAT_DRM_1);
    TestAnalyticsListener listener =
        runAnalyticsTest(
            mediaSource,
            new ActionSchedule.Builder(TAG)
                .waitForIsLoading(false)
                .executeRunnable(mediaDrmCallback.keyCondition::open)
                .build());

    populateEventIds(listener.lastReportedTimeline);
    assertThat(listener.getEvents(EVENT_DRM_SESSION_MANAGER_ERROR)).containsExactly(period0);
    assertThat(listener.getEvents(EVENT_PLAYER_ERROR)).containsExactly(period0);
  }

  @Test
  public void onPlayerError_thrownDuringRendererEnableAtPeriodTransition_isReportedForNewPeriod()
      throws Exception {
    FakeMediaSource source0 =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT);
    FakeMediaSource source1 =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT);
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
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT);
    FakeMediaSource source1 =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT);
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
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT);
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

  @Test
  public void onEvents_isReportedWithCorrectEventTimes() throws Exception {
    SimpleExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    player.setVideoSurface(surface);

    AnalyticsListener listener = mock(AnalyticsListener.class);
    Format[] formats =
        new Format[] {
          new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build(),
          new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        };
    player.addAnalyticsListener(listener);

    // Trigger some simultaneous events.
    player.setMediaSource(new FakeMediaSource(new FakeTimeline(), formats));
    player.seekTo(2_000);
    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2.0f));
    ShadowLooper.runMainLooperToNextTask();

    // Move to another item and fail with a third one to trigger events with different EventTimes.
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    player.addMediaSource(new FakeMediaSource(new FakeTimeline(), formats));
    player.play();
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setMediaItem(MediaItem.fromUri("http://this-will-throw-an-exception.mp4"));
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_IDLE);
    ShadowLooper.runMainLooperToNextTask();
    player.release();
    surface.release();

    // Verify that expected individual callbacks have been called and capture EventTimes.
    ArgumentCaptor<AnalyticsListener.EventTime> individualTimelineChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onTimelineChanged(individualTimelineChangedEventTimes.capture(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualMediaItemTransitionEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onMediaItemTransition(individualMediaItemTransitionEventTimes.capture(), any(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualPositionDiscontinuityEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onPositionDiscontinuity(individualPositionDiscontinuityEventTimes.capture(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualPlaybackStateChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onPlaybackStateChanged(individualPlaybackStateChangedEventTimes.capture(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualIsLoadingChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onIsLoadingChanged(individualIsLoadingChangedEventTimes.capture(), anyBoolean());
    ArgumentCaptor<AnalyticsListener.EventTime> individualTracksChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onTracksChanged(individualTracksChangedEventTimes.capture(), any(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualPlayWhenReadyChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onPlayWhenReadyChanged(
            individualPlayWhenReadyChangedEventTimes.capture(), anyBoolean(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualIsPlayingChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onIsPlayingChanged(individualIsPlayingChangedEventTimes.capture(), anyBoolean());
    ArgumentCaptor<AnalyticsListener.EventTime> individualPlayerErrorEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce()).onPlayerError(individualPlayerErrorEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualPlaybackParametersChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onPlaybackParametersChanged(
            individualPlaybackParametersChangedEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualLoadStartedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onLoadStarted(individualLoadStartedEventTimes.capture(), any(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualLoadCompletedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onLoadCompleted(individualLoadCompletedEventTimes.capture(), any(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualLoadErrorEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onLoadError(individualLoadErrorEventTimes.capture(), any(), any(), any(), anyBoolean());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoEnabledEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoEnabled(individualVideoEnabledEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualAudioEnabledEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onAudioEnabled(individualAudioEnabledEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualDownstreamFormatChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onDownstreamFormatChanged(individualDownstreamFormatChangedEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoInputFormatChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoInputFormatChanged(
            individualVideoInputFormatChangedEventTimes.capture(), any(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualAudioInputFormatChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onAudioInputFormatChanged(
            individualAudioInputFormatChangedEventTimes.capture(), any(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoDecoderInitializedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoDecoderInitialized(
            individualVideoDecoderInitializedEventTimes.capture(), any(), anyLong(), anyLong());
    ArgumentCaptor<AnalyticsListener.EventTime> individualAudioDecoderInitializedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onAudioDecoderInitialized(
            individualAudioDecoderInitializedEventTimes.capture(), any(), anyLong(), anyLong());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoDisabledEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoDisabled(individualVideoDisabledEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualAudioDisabledEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onAudioDisabled(individualAudioDisabledEventTimes.capture(), any());
    ArgumentCaptor<AnalyticsListener.EventTime> individualRenderedFirstFrameEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onRenderedFirstFrame(individualRenderedFirstFrameEventTimes.capture(), any(), anyLong());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoSizeChangedEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoSizeChanged(individualVideoSizeChangedEventTimes.capture(), any());
    verify(listener, atLeastOnce())
        .onVideoSizeChanged(
            individualVideoSizeChangedEventTimes.capture(),
            anyInt(),
            anyInt(),
            anyInt(),
            anyFloat());
    ArgumentCaptor<AnalyticsListener.EventTime> individualAudioPositionAdvancingEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onAudioPositionAdvancing(individualAudioPositionAdvancingEventTimes.capture(), anyLong());
    ArgumentCaptor<AnalyticsListener.EventTime> individualVideoProcessingOffsetEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onVideoFrameProcessingOffset(
            individualVideoProcessingOffsetEventTimes.capture(), anyLong(), anyInt());
    ArgumentCaptor<AnalyticsListener.EventTime> individualDroppedFramesEventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(listener, atLeastOnce())
        .onDroppedVideoFrames(individualDroppedFramesEventTimes.capture(), anyInt(), anyLong());

    // Verify the EventTimes reported with onEvents are a non-empty subset of the individual
    // callback EventTimes. We can only assert they are a non-empty subset because there may be
    // multiple events of the same type arriving in the same message queue iteration.
    ArgumentCaptor<AnalyticsListener.Events> eventsCaptor =
        ArgumentCaptor.forClass(AnalyticsListener.Events.class);
    verify(listener, atLeastOnce()).onEvents(eq(player), eventsCaptor.capture());
    SparseArray<List<AnalyticsListener.EventTime>> onEventsEventTimes = new SparseArray<>();
    for (AnalyticsListener.Events events : eventsCaptor.getAllValues()) {
      for (int i = 0; i < events.size(); i++) {
        @AnalyticsListener.EventFlags int event = events.get(i);
        if (onEventsEventTimes.get(event) == null) {
          onEventsEventTimes.put(event, new ArrayList<>());
        }
        onEventsEventTimes.get(event).add(events.getEventTime(event));
      }
    }
    // SparseArray.get returns null if the key doesn't exist, thus verifying the sets are non-empty.
    assertThat(individualTimelineChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_TIMELINE_CHANGED))
        .inOrder();
    assertThat(individualMediaItemTransitionEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_MEDIA_ITEM_TRANSITION))
        .inOrder();
    assertThat(individualPositionDiscontinuityEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_POSITION_DISCONTINUITY))
        .inOrder();
    assertThat(individualPlaybackStateChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_PLAYBACK_STATE_CHANGED))
        .inOrder();
    assertThat(individualIsLoadingChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_IS_LOADING_CHANGED))
        .inOrder();
    assertThat(individualTracksChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_TRACKS_CHANGED))
        .inOrder();
    assertThat(individualPlayWhenReadyChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_PLAY_WHEN_READY_CHANGED))
        .inOrder();
    assertThat(individualIsPlayingChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_IS_PLAYING_CHANGED))
        .inOrder();
    assertThat(individualPlayerErrorEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_PLAYER_ERROR))
        .inOrder();
    assertThat(individualPlaybackParametersChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_PLAYBACK_PARAMETERS_CHANGED))
        .inOrder();
    assertThat(individualLoadStartedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_LOAD_STARTED))
        .inOrder();
    assertThat(individualLoadCompletedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_LOAD_COMPLETED))
        .inOrder();
    assertThat(individualLoadErrorEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_LOAD_ERROR))
        .inOrder();
    assertThat(individualVideoEnabledEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_ENABLED))
        .inOrder();
    assertThat(individualAudioEnabledEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_AUDIO_ENABLED))
        .inOrder();
    assertThat(individualDownstreamFormatChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_DOWNSTREAM_FORMAT_CHANGED))
        .inOrder();
    assertThat(individualVideoInputFormatChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_INPUT_FORMAT_CHANGED))
        .inOrder();
    assertThat(individualAudioInputFormatChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_AUDIO_INPUT_FORMAT_CHANGED))
        .inOrder();
    assertThat(individualVideoDecoderInitializedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_DECODER_INITIALIZED))
        .inOrder();
    assertThat(individualAudioDecoderInitializedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_AUDIO_DECODER_INITIALIZED))
        .inOrder();
    assertThat(individualVideoDisabledEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_DISABLED))
        .inOrder();
    assertThat(individualAudioDisabledEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_AUDIO_DISABLED))
        .inOrder();
    assertThat(individualRenderedFirstFrameEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_RENDERED_FIRST_FRAME))
        .inOrder();
    assertThat(individualVideoSizeChangedEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_SIZE_CHANGED))
        .inOrder();
    assertThat(individualAudioPositionAdvancingEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_AUDIO_POSITION_ADVANCING))
        .inOrder();
    assertThat(individualVideoProcessingOffsetEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_VIDEO_FRAME_PROCESSING_OFFSET))
        .inOrder();
    assertThat(individualDroppedFramesEventTimes.getAllValues())
        .containsAtLeastElementsIn(onEventsEventTimes.get(EVENT_DROPPED_VIDEO_FRAMES))
        .inOrder();
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

  @Test
  public void recursiveListenerInvocation_arrivesInCorrectOrder() {
    AnalyticsCollector analyticsCollector = new AnalyticsCollector(Clock.DEFAULT);
    analyticsCollector.setPlayer(
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build(),
        Looper.myLooper());
    AnalyticsListener listener1 = mock(AnalyticsListener.class);
    AnalyticsListener listener2 =
        spy(
            new AnalyticsListener() {
              @Override
              public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
                analyticsCollector.onSurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
              }
            });
    AnalyticsListener listener3 = mock(AnalyticsListener.class);
    analyticsCollector.addListener(listener1);
    analyticsCollector.addListener(listener2);
    analyticsCollector.addListener(listener3);

    analyticsCollector.onPlayerError(ExoPlaybackException.createForSource(new IOException()));

    InOrder inOrder = Mockito.inOrder(listener1, listener2, listener3);
    inOrder.verify(listener1).onPlayerError(any(), any());
    inOrder.verify(listener2).onPlayerError(any(), any());
    inOrder.verify(listener3).onPlayerError(any(), any());
    inOrder.verify(listener1).onSurfaceSizeChanged(any(), eq(0), eq(0));
    inOrder.verify(listener2).onSurfaceSizeChanged(any(), eq(0), eq(0));
    inOrder.verify(listener3).onSurfaceSizeChanged(any(), eq(0), eq(0));
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
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    TestAnalyticsListener listener = new TestAnalyticsListener();
    try {
      new ExoPlayerTestRunner.Builder(ApplicationProvider.getApplicationContext())
          .setMediaSources(mediaSource)
          .setRenderersFactory(renderersFactory)
          .setVideoSurface(surface)
          .setAnalyticsListener(listener)
          .setActionSchedule(actionSchedule)
          .build()
          .start()
          .blockUntilActionScheduleFinished(TIMEOUT_MS)
          .blockUntilEnded(TIMEOUT_MS);
    } catch (ExoPlaybackException e) {
      // Ignore ExoPlaybackException as these may be expected.
    } finally {
      surface.release();
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

    public List<EventWindowAndPeriodId> getEvents(long eventType) {
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
    public void onPositionDiscontinuity(
        EventTime eventTime,
        Player.PositionInfo oldPosition,
        Player.PositionInfo newPosition,
        int reason) {
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
      reportedEvents.add(new ReportedEvent(EVENT_SHUFFLE_MODE_ENABLED_CHANGED, eventTime));
    }

    @Override
    public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
      reportedEvents.add(new ReportedEvent(EVENT_IS_LOADING_CHANGED, eventTime));
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
        EventTime eventTime,
        String decoderName,
        long initializedTimestampMs,
        long initializationDurationMs) {
      reportedEvents.add(new ReportedEvent(EVENT_AUDIO_DECODER_INITIALIZED, eventTime));
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
    public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {
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
        EventTime eventTime,
        String decoderName,
        long initializedTimestampMs,
        long initializationDurationMs) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_DECODER_INITIALIZED, eventTime));
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_INPUT_FORMAT_CHANGED, eventTime));
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
      reportedEvents.add(new ReportedEvent(EVENT_DROPPED_VIDEO_FRAMES, eventTime));
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
    public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
      reportedEvents.add(new ReportedEvent(EVENT_RENDERED_FIRST_FRAME, eventTime));
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
      reportedEvents.add(new ReportedEvent(EVENT_VIDEO_SIZE_CHANGED, eventTime));
    }

    @Override
    public void onDrmSessionAcquired(EventTime eventTime, @DrmSession.State int state) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_SESSION_ACQUIRED, eventTime));
    }

    @Override
    public void onDrmKeysLoaded(EventTime eventTime) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_KEYS_LOADED, eventTime));
    }

    @Override
    public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
      reportedEvents.add(new ReportedEvent(EVENT_DRM_SESSION_MANAGER_ERROR, eventTime));
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

      public final long eventType;
      public final EventWindowAndPeriodId eventWindowAndPeriodId;

      public ReportedEvent(long eventType, EventTime eventTime) {
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
    public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request) {
      return new byte[0];
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request) {
      return new byte[0];
    }
  }

  /**
   * A {@link MediaDrmCallback} that blocks each provision and key request until the associated
   * {@link ConditionVariable} field is opened, and then returns an empty byte array. The {@link
   * ConditionVariable} must be explicitly opened for each request.
   */
  private static final class BlockingDrmCallback implements MediaDrmCallback {

    public final ConditionVariable provisionCondition;
    public final ConditionVariable keyCondition;

    private final boolean alwaysFail;

    private BlockingDrmCallback(boolean alwaysFail) {
      this.provisionCondition = RobolectricUtil.createRobolectricConditionVariable();
      this.keyCondition = RobolectricUtil.createRobolectricConditionVariable();

      this.alwaysFail = alwaysFail;
    }

    /** Returns a callback that always returns an empty byte array from its execute methods. */
    public static BlockingDrmCallback returnsEmpty() {
      return new BlockingDrmCallback(/* alwaysFail= */ false);
    }

    /** Returns a callback that always throws an exception from its execute methods. */
    public static BlockingDrmCallback alwaysFailing() {
      return new BlockingDrmCallback(/* alwaysFail= */ true);
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request)
        throws MediaDrmCallbackException {
      provisionCondition.blockUninterruptible();
      provisionCondition.close();
      if (alwaysFail) {
        throw new RuntimeException("executeProvisionRequest failed");
      } else {
        return new byte[0];
      }
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request)
        throws MediaDrmCallbackException {
      keyCondition.blockUninterruptible();
      keyCondition.close();
      if (alwaysFail) {
        throw new RuntimeException("executeKeyRequest failed");
      } else {
        return new byte[0];
      }
    }
  }
}
