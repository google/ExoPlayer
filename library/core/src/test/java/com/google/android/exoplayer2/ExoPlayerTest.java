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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static com.google.android.exoplayer2.Player.COMMAND_GET_AUDIO_ATTRIBUTES;
import static com.google.android.exoplayer2.Player.COMMAND_GET_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_GET_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_GET_MEDIA_ITEMS_METADATA;
import static com.google.android.exoplayer2.Player.COMMAND_GET_TEXT;
import static com.google.android.exoplayer2.Player.COMMAND_GET_TIMELINE;
import static com.google.android.exoplayer2.Player.COMMAND_GET_TRACKS;
import static com.google.android.exoplayer2.Player.COMMAND_GET_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_PLAY_PAUSE;
import static com.google.android.exoplayer2.Player.COMMAND_PREPARE;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_BACK;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_FORWARD;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SET_DEVICE_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_SET_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SET_MEDIA_ITEMS_METADATA;
import static com.google.android.exoplayer2.Player.COMMAND_SET_REPEAT_MODE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_SHUFFLE_MODE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_SPEED_AND_PITCH;
import static com.google.android.exoplayer2.Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS;
import static com.google.android.exoplayer2.Player.COMMAND_SET_VIDEO_SURFACE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_VOLUME;
import static com.google.android.exoplayer2.Player.COMMAND_STOP;
import static com.google.android.exoplayer2.Player.STATE_ENDED;
import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.playUntilPosition;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.playUntilStartOfMediaItem;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPositionDiscontinuity;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilSleepingForOffload;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilTimelineChanged;
import static com.google.android.exoplayer2.source.ads.ServerSideAdInsertionUtil.addAdGroupToAdPlaybackState;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US;
import static com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
import static com.google.android.exoplayer2.testutil.TestUtil.assertTimelinesSame;
import static com.google.android.exoplayer2.testutil.TestUtil.timelinesAreSame;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.BinaryFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MaskingMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.WrappingMediaSource;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.ServerSideAdInsertionMediaSource;
import com.google.android.exoplayer2.testutil.ActionSchedule;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerRunnable;
import com.google.android.exoplayer2.testutil.ActionSchedule.PlayerTarget;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.FakeAdaptiveDataSet;
import com.google.android.exoplayer2.testutil.FakeAdaptiveMediaSource;
import com.google.android.exoplayer2.testutil.FakeChunkSource;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeMediaSourceFactory;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.testutil.FakeSampleStream.FakeSampleStreamItem;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.FakeTrackSelection;
import com.google.android.exoplayer2.testutil.FakeTrackSelector;
import com.google.android.exoplayer2.testutil.FakeVideoRenderer;
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link ExoPlayer}. */
@RunWith(AndroidJUnit4.class)
public final class ExoPlayerTest {

  private static final String TAG = "ExoPlayerTest";

  /**
   * For tests that rely on the player transitioning to the ended state, the duration in
   * milliseconds after starting the player before the test will time out. This is to catch cases
   * where the player under test is not making progress, in which case the test should fail.
   */
  private static final int TIMEOUT_MS = 10_000;

  private Context context;
  private Timeline placeholderTimeline;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    placeholderTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(0).build());
  }

  /**
   * Tests playback of a source that exposes an empty timeline. Playback is expected to end without
   * error.
   */
  @Test
  public void playEmptyTimeline() throws Exception {
    Timeline timeline = Timeline.EMPTY;
    Timeline expectedMaskingTimeline =
        new MaskingMediaSource.PlaceholderTimeline(FakeMediaSource.FAKE_MEDIA_ITEM);
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_UNKNOWN);

    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onTimelineChanged(
            argThat(noUid(expectedMaskingTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockListener)
        .onTimelineChanged(
            argThat(noUid(timeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(renderer.getFormatsRead()).isEmpty();
    assertThat(renderer.sampleBufferReadCount).isEqualTo(0);
    assertThat(renderer.isEnded).isFalse();
  }

  /** Tests playback of a source that exposes a single period. */
  @Test
  public void playSinglePeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline();
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = Mockito.inOrder(mockListener);
    inOrder
        .verify(mockListener)
        .onTimelineChanged(
            argThat(noUid(placeholderTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockListener)
        .onTimelineChanged(
            argThat(noUid(timeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockListener)
        .onTracksChanged(
            eq(
                new Tracks(
                    ImmutableList.of(
                        new Tracks.Group(
                            new TrackGroup(ExoPlayerTestRunner.VIDEO_FORMAT),
                            /* adaptiveSupported= */ false,
                            new int[] {C.FORMAT_HANDLED},
                            /* trackSelected= */ new boolean[] {true})))));
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(anyInt());
    inOrder.verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(renderer.getFormatsRead()).containsExactly(ExoPlayerTestRunner.VIDEO_FORMAT);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(1);
    assertThat(renderer.isEnded).isTrue();
  }

  /** Tests playback of a source that exposes three periods. */
  @Test
  public void playMultiPeriodTimeline() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);

    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = Mockito.inOrder(mockPlayerListener);
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(new FakeMediaSource.InitialTimeline(timeline))),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(timeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockPlayerListener, times(2))
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(renderer.getFormatsRead())
        .containsExactly(
            ExoPlayerTestRunner.VIDEO_FORMAT,
            ExoPlayerTestRunner.VIDEO_FORMAT,
            ExoPlayerTestRunner.VIDEO_FORMAT);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(3);
    assertThat(renderer.isEnded).isTrue();
  }

  /** Tests playback of periods with very short duration. */
  @Test
  public void playShortDurationPeriods() throws Exception {
    // TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US / 100 = 1000 us per period.
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 100, /* id= */ 0));
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);

    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = inOrder(mockPlayerListener);
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(placeholderTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(timeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockPlayerListener, times(99))
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(renderer.getFormatsRead()).hasSize(100);
    assertThat(renderer.sampleBufferReadCount).isEqualTo(100);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void renderersLifecycle_renderersThatAreNeverEnabled_areNotReset() throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    final FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setRenderers(videoRenderer, audioRenderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);
    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT));
    player.prepare();

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    assertThat(audioRenderer.enabledCount).isEqualTo(1);
    assertThat(audioRenderer.resetCount).isEqualTo(1);
    assertThat(videoRenderer.enabledCount).isEqualTo(0);
    assertThat(videoRenderer.resetCount).isEqualTo(0);
  }

  @Test
  public void renderersLifecycle_setForegroundMode_resetsDisabledRenderersThatHaveBeenEnabled()
      throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    final FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    final FakeRenderer textRenderer = new FakeRenderer(C.TRACK_TYPE_TEXT);
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setRenderers(videoRenderer, audioRenderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(
                timeline, ExoPlayerTestRunner.AUDIO_FORMAT, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    player.play();
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setForegroundMode(/* foregroundMode= */ true);
    // Only the video renderer that is disabled in the second media item has been reset.
    assertThat(audioRenderer.resetCount).isEqualTo(0);
    assertThat(videoRenderer.resetCount).isEqualTo(1);

    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    // After release the audio renderer is reset as well.
    assertThat(audioRenderer.enabledCount).isEqualTo(1);
    assertThat(audioRenderer.resetCount).isEqualTo(1);
    assertThat(videoRenderer.enabledCount).isEqualTo(1);
    assertThat(videoRenderer.resetCount).isEqualTo(1);
    assertThat(textRenderer.enabledCount).isEqualTo(0);
    assertThat(textRenderer.resetCount).isEqualTo(0);
  }

  @Test
  public void renderersLifecycle_selectTextTracksWhilePlaying_textRendererEnabledAndReset()
      throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    final FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    final FakeRenderer textRenderer = new FakeRenderer(C.TRACK_TYPE_TEXT);
    Format textFormat =
        new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en").build();
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setRenderers(audioRenderer, textRenderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT, textFormat)));
    player.prepare();

    player.play();
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    // Only the audio renderer enabled so far.
    assertThat(audioRenderer.enabledCount).isEqualTo(1);
    assertThat(textRenderer.enabledCount).isEqualTo(0);
    player.setTrackSelectionParameters(
        player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("en").build());
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    assertThat(audioRenderer.enabledCount).isEqualTo(1);
    assertThat(audioRenderer.resetCount).isEqualTo(1);
    assertThat(textRenderer.enabledCount).isEqualTo(1);
    assertThat(textRenderer.resetCount).isEqualTo(1);
    assertThat(videoRenderer.enabledCount).isEqualTo(0);
    assertThat(videoRenderer.resetCount).isEqualTo(0);
  }

  @Test
  public void renderersLifecycle_seekTo_resetsDisabledRenderersIfRequired() throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    final FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    final FakeRenderer textRenderer = new FakeRenderer(C.TRACK_TYPE_TEXT);
    Format textFormat =
        new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en").build();
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setRenderers(videoRenderer, audioRenderer, textRenderer)
            .build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);
    player.setTrackSelectionParameters(
        player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("en").build());
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT, textFormat)));
    player.prepare();

    player.play();
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    // Disable text renderer by selecting a language that is not available.
    player.setTrackSelectionParameters(
        player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("de").build());
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 1000);
    runUntilPlaybackState(player, Player.STATE_READY);
    // Expect formerly enabled renderers to be reset after seek.
    assertThat(textRenderer.resetCount).isEqualTo(1);
    assertThat(audioRenderer.resetCount).isEqualTo(0);
    assertThat(videoRenderer.resetCount).isEqualTo(0);
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Verify that the text renderer has not been reset a second time.
    assertThat(audioRenderer.enabledCount).isEqualTo(2);
    assertThat(audioRenderer.resetCount).isEqualTo(1);
    assertThat(textRenderer.enabledCount).isEqualTo(1);
    assertThat(textRenderer.resetCount).isEqualTo(1);
    assertThat(videoRenderer.enabledCount).isEqualTo(0);
    assertThat(videoRenderer.resetCount).isEqualTo(0);
  }

  /**
   * Tests that the player does not unnecessarily reset renderers when playing a multi-period
   * source.
   */
  @Test
  public void readAheadToEndDoesNotResetRenderer() throws Exception {
    // Use sufficiently short periods to ensure the player attempts to read all at once.
    TimelineWindowDefinition windowDefinition0 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    TimelineWindowDefinition windowDefinition1 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    TimelineWindowDefinition windowDefinition2 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ false,
            /* isDynamic= */ false,
            /* durationUs= */ 100_000);
    Timeline timeline = new FakeTimeline(windowDefinition0, windowDefinition1, windowDefinition2);
    final FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeMediaClockRenderer audioRenderer =
        new FakeMediaClockRenderer(C.TRACK_TYPE_AUDIO) {
          @Override
          public long getPositionUs() {
            // Simulate the playback position lagging behind the reading position: the renderer
            // media clock position will be the start of the timeline until the stream is set to be
            // final, at which point it jumps to the end of the timeline allowing the playing period
            // to advance.
            return isCurrentStreamFinal() ? 30 : 0;
          }

          @Override
          public void setPlaybackParameters(PlaybackParameters playbackParameters) {}

          @Override
          public PlaybackParameters getPlaybackParameters() {
            return PlaybackParameters.DEFAULT;
          }

          @Override
          public boolean isEnded() {
            return videoRenderer.isEnded();
          }
        };
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setRenderers(videoRenderer, audioRenderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);

    player.setMediaSource(
        new FakeMediaSource(
            timeline, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = inOrder(mockPlayerListener);
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(new FakeMediaSource.InitialTimeline(timeline))),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(timeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockPlayerListener, times(2))
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(audioRenderer.positionResetCount).isEqualTo(1);
    assertThat(videoRenderer.isEnded).isTrue();
    assertThat(audioRenderer.isEnded).isTrue();
  }

  @Test
  public void resettingMediaSourcesGivesFreshSourceInfo() throws Exception {
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    Timeline firstTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 1_000_000_000));
    MediaSource firstSource = new FakeMediaSource(firstTimeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    AtomicBoolean secondSourcePrepared = new AtomicBoolean();
    MediaSource secondSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          public synchronized void prepareSourceInternal(
              @Nullable TransferListener mediaTransferListener) {
            super.prepareSourceInternal(mediaTransferListener);
            secondSourcePrepared.set(true);
          }
        };
    Timeline thirdTimeline = new FakeTimeline();
    MediaSource thirdSource = new FakeMediaSource(thirdTimeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    Player.Listener mockPlayerListener = mock(Player.Listener.class);
    player.addListener(mockPlayerListener);

    player.setMediaSource(firstSource);
    player.prepare();
    player.play();
    runUntilTimelineChanged(player);
    player.setMediaSource(secondSource);
    runMainLooperUntil(secondSourcePrepared::get);
    player.setMediaSource(thirdSource);
    runUntilPlaybackState(player, Player.STATE_ENDED);

    // The first source's preparation completed with a real timeline. When the second source was
    // prepared, it immediately exposed a placeholder timeline, but the source info refresh from the
    // second source was suppressed as we replace it with the third source before the update
    // arrives.
    InOrder inOrder = inOrder(mockPlayerListener);
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(placeholderTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(firstTimeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(placeholderTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockPlayerListener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(placeholderTimeline)),
            eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(mockPlayerListener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(mockPlayerListener)
        .onTimelineChanged(
            argThat(noUid(thirdTimeline)), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    inOrder
        .verify(mockPlayerListener)
        .onTracksChanged(
            eq(
                new Tracks(
                    ImmutableList.of(
                        new Tracks.Group(
                            new TrackGroup(ExoPlayerTestRunner.VIDEO_FORMAT),
                            /* adaptiveSupported= */ false,
                            new int[] {C.FORMAT_HANDLED},
                            /* trackSelected= */ new boolean[] {true})))));
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void repeatModeChanges() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    AnalyticsListener mockAnalyticsListener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(mockAnalyticsListener);

    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    runUntilTimelineChanged(player);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 1);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 1);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 2);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 2);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 0);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 0);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 0);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 1);
    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 2);
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<AnalyticsListener.EventTime> eventTimes =
        ArgumentCaptor.forClass(AnalyticsListener.EventTime.class);
    verify(mockAnalyticsListener, times(10))
        .onMediaItemTransition(eventTimes.capture(), any(), anyInt());
    assertThat(
            eventTimes.getAllValues().stream()
                .map(eventTime -> eventTime.currentWindowIndex)
                .collect(Collectors.toList()))
        .containsExactly(0, 1, 1, 2, 2, 0, 0, 0, 1, 2)
        .inOrder();
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void shuffleModeEnabledChanges() throws Exception {
    Timeline fakeTimeline = new FakeTimeline();
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT)
    };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(false, new FakeShuffleOrder(3), fakeMediaSources);
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .playUntilStartOfMediaItem(/* mediaItemIndex= */ 1)
            .setShuffleModeEnabled(true)
            .playUntilStartOfMediaItem(/* mediaItemIndex= */ 1)
            .setShuffleModeEnabled(false)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setRenderers(renderer)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0, 2, 1, 2);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void adGroupWithLoadError_noFurtherAdGroup_isSkipped() throws Exception {
    AdPlaybackState initialAdPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */
            TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + 5 * C.MICROS_PER_SECOND);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 0,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                initialAdPlaybackState));
    AdPlaybackState errorAdPlaybackState =
        initialAdPlaybackState.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    final Timeline adErrorTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 0,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                errorAdPlaybackState));
    final FakeMediaSource fakeMediaSource =
        new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.setMediaSource(fakeMediaSource);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    fakeMediaSource.setNewSourceInfo(adErrorTimeline);
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Content to content transition is ignored.
    verify(mockListener, never()).onPositionDiscontinuity(any(), any(), anyInt());
  }

  @Test
  public void adGroupWithLoadError_withFurtherAdGroup_isSkipped() throws Exception {
    AdPlaybackState initialAdPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */
            TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + 5 * C.MICROS_PER_SECOND,
            TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + 8 * C.MICROS_PER_SECOND);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 0,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                initialAdPlaybackState));
    AdPlaybackState errorAdPlaybackState =
        initialAdPlaybackState.withAdLoadError(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    final Timeline adErrorTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 0,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                errorAdPlaybackState));
    final FakeMediaSource fakeMediaSource =
        new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.setMediaSource(fakeMediaSource);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    fakeMediaSource.setNewSourceInfo(adErrorTimeline);
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    Timeline.Window window =
        player.getCurrentTimeline().getWindow(/* windowIndex= */ 0, new Timeline.Window());
    Timeline.Period period =
        player
            .getCurrentTimeline()
            .getPeriod(/* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true);
    player.release();

    // There content to content discontinuity after the failed ad is suppressed.
    PositionInfo positionInfoContentAtSuccessfulAd =
        new PositionInfo(
            window.uid,
            /* mediaItemIndex= */ 0,
            window.mediaItem,
            period.uid,
            /* periodIndex= */ 0,
            /* positionMs= */ 8_000,
            /* contentPositionMs= */ 8_000,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    PositionInfo positionInfoSuccessfulAdStart =
        new PositionInfo(
            window.uid,
            /* mediaItemIndex= */ 0,
            window.mediaItem,
            period.uid,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 8_000,
            /* adGroupIndex= */ 1,
            /* adIndexInAdGroup= */ 0);
    PositionInfo positionInfoSuccessfulAdEnd =
        new PositionInfo(
            window.uid,
            /* mediaItemIndex= */ 0,
            window.mediaItem,
            period.uid,
            /* periodIndex= */ 0,
            /* positionMs= */ Util.usToMs(
                period.getAdDurationUs(/* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0)),
            /* contentPositionMs= */ 8_000,
            /* adGroupIndex= */ 1,
            /* adIndexInAdGroup= */ 0);
    verify(mockListener)
        .onPositionDiscontinuity(
            positionInfoContentAtSuccessfulAd,
            positionInfoSuccessfulAdStart,
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    verify(mockListener)
        .onPositionDiscontinuity(
            positionInfoSuccessfulAdEnd,
            positionInfoContentAtSuccessfulAd,
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
  }

  @Test
  public void periodHoldersReleasedAfterSeekWithRepeatModeAll() throws Exception {
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .waitForPositionDiscontinuity()
            .seek(0) // Seek with repeat mode set to Player.REPEAT_MODE_ALL.
            .waitForPositionDiscontinuity()
            .setRepeatMode(Player.REPEAT_MODE_OFF) // Turn off repeat so that playback can finish.
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setRenderers(renderer)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(renderer.isEnded).isTrue();
  }

  @Test
  public void seekTo_indexLargerThanPlaylist_isIgnored() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaItem(MediaItem.fromUri("http://test"));

    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 1000);

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(0);
    player.release();
  }

  @Test
  public void addMediaItems_indexLargerThanPlaylist_addsToEndOfPlaylist() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaItem(MediaItem.fromUri("http://test"));
    ImmutableList<MediaItem> addedItems =
        ImmutableList.of(MediaItem.fromUri("http://new1"), MediaItem.fromUri("http://new2"));

    player.addMediaItems(/* index= */ 5000, addedItems);

    assertThat(player.getMediaItemCount()).isEqualTo(3);
    assertThat(player.getMediaItemAt(1)).isEqualTo(addedItems.get(0));
    assertThat(player.getMediaItemAt(2)).isEqualTo(addedItems.get(1));
    player.release();
  }

  @Test
  public void removeMediaItems_fromIndexLargerThanPlaylist_isIgnored() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaItems(
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2")));

    player.removeMediaItems(/* fromIndex= */ 5000, /* toIndex= */ 6000);

    assertThat(player.getMediaItemCount()).isEqualTo(2);
    player.release();
  }

  @Test
  public void removeMediaItems_toIndexLargerThanPlaylist_removesUpToEndOfPlaylist()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaItems(
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2")));

    player.removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 6000);

    assertThat(player.getMediaItemCount()).isEqualTo(1);
    assertThat(player.getMediaItemAt(0).localConfiguration.uri.toString())
        .isEqualTo("http://item1");
    player.release();
  }

  @Test
  public void moveMediaItems_fromIndexLargerThanPlaylist_isIgnored() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    ImmutableList<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    player.setMediaItems(items);

    player.moveMediaItems(/* fromIndex= */ 5000, /* toIndex= */ 6000, /* newIndex= */ 0);

    assertThat(player.getMediaItemAt(0)).isEqualTo(items.get(0));
    assertThat(player.getMediaItemAt(1)).isEqualTo(items.get(1));
    player.release();
  }

  @Test
  public void moveMediaItems_toIndexLargerThanPlaylist_movesItemsUpToEndOfPlaylist()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    ImmutableList<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    player.setMediaItems(items);

    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 6000, /* newIndex= */ 0);

    assertThat(player.getMediaItemAt(0)).isEqualTo(items.get(1));
    assertThat(player.getMediaItemAt(1)).isEqualTo(items.get(0));
    player.release();
  }

  @Test
  public void moveMediaItems_newIndexLargerThanPlaylist_movesItemsUpToEndOfPlaylist()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    ImmutableList<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    player.setMediaItems(items);

    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1, /* newIndex= */ 5000);

    assertThat(player.getMediaItemAt(0)).isEqualTo(items.get(1));
    assertThat(player.getMediaItemAt(1)).isEqualTo(items.get(0));
    player.release();
  }

  @Test
  public void seekDiscontinuity() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    ActionSchedule actionSchedule = new ActionSchedule.Builder(TAG).seek(10).build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);
  }

  @Test
  public void seekDiscontinuityWithAdjustment() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod =
                new FakeMediaPeriod(
                    trackGroupArray,
                    allocator,
                    TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                    mediaSourceEventDispatcher,
                    drmSessionManager,
                    drmEventDispatcher,
                    /* deferOnPrepared= */ false);
            mediaPeriod.setSeekToUsOffset(10);
            return mediaPeriod;
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(10)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_SEEK, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
  }

  @Test
  public void internalDiscontinuityAtNewPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline(1);
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod =
                new FakeMediaPeriod(
                    trackGroupArray,
                    allocator,
                    TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                    mediaSourceEventDispatcher);
            mediaPeriod.setDiscontinuityPositionUs(10);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Test
  public void internalDiscontinuityAtInitialPosition() throws Exception {
    FakeTimeline timeline = new FakeTimeline();
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            FakeMediaPeriod mediaPeriod =
                new FakeMediaPeriod(
                    trackGroupArray,
                    allocator,
                    TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                    mediaSourceEventDispatcher);
            // Set a discontinuity at the position this period is supposed to start at anyway.
            mediaPeriod.setDiscontinuityPositionUs(
                timeline.getWindow(/* windowIndex= */ 0, new Window()).positionInFirstPeriodUs);
            return mediaPeriod;
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    // If the position is unchanged we do not expect the discontinuity to be reported externally.
    testRunner.assertNoPositionDiscontinuities();
  }

  @Test
  public void allActivatedTrackSelectionAreReleasedForSinglePeriod() throws Exception {
    MediaSource mediaSource =
        new FakeMediaSource(
            new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made once (1 period).
    assertThat(createdTrackSelections).hasSize(2);
    assertThat(numSelectionsEnabled).isEqualTo(2);
  }

  @Test
  public void allActivatedTrackSelectionAreReleasedForMultiPeriods() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource mediaSource =
        new FakeMediaSource(
            timeline, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    FakeTrackSelector trackSelector = new FakeTrackSelector();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice (2 periods).
    assertThat(createdTrackSelections).hasSize(4);
    assertThat(numSelectionsEnabled).isEqualTo(4);
  }

  @Test
  public void allActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreRemade() throws Exception {
    MediaSource mediaSource =
        new FakeMediaSource(
            new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    final FakeTrackSelector trackSelector = new FakeTrackSelector();
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .disableRenderer(0)
            .play()
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice. The second time one renderer is
    // disabled, so only one out of the two track selections is enabled.
    assertThat(createdTrackSelections).hasSize(3);
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void allActivatedTrackSelectionAreReleasedWhenTrackSelectionsAreReused() throws Exception {
    MediaSource mediaSource =
        new FakeMediaSource(
            new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    final FakeTrackSelector trackSelector =
        new FakeTrackSelector(/* mayReuseTrackSelection= */ true);
    ActionSchedule disableTrackAction =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .disableRenderer(0)
            .play()
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setRenderers(videoRenderer, audioRenderer)
        .setTrackSelector(trackSelector)
        .setActionSchedule(disableTrackAction)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    List<FakeTrackSelection> createdTrackSelections = trackSelector.getAllTrackSelections();
    int numSelectionsEnabled = 0;
    // Assert that all tracks selection are disabled at the end of the playback.
    for (FakeTrackSelection trackSelection : createdTrackSelections) {
      assertThat(trackSelection.isEnabled).isFalse();
      numSelectionsEnabled += trackSelection.enableCount;
    }
    // There are 2 renderers, and track selections are made twice. The second time one renderer is
    // disabled, and the selector re-uses the previous selection for the enabled renderer. So we
    // expect two track selections, one of which will have been enabled twice.
    assertThat(createdTrackSelections).hasSize(2);
    assertThat(numSelectionsEnabled).isEqualTo(3);
  }

  @Test
  public void dynamicTimelineChangeReason() throws Exception {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 100000));
    final Timeline timeline2 = new FakeTimeline(new TimelineWindowDefinition(false, false, 20000));
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline2))
            .waitForTimelineChanged(
                timeline2, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(placeholderTimeline, timeline, timeline2);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void resetMediaSourcesWithPositionResetAndShufflingUsesFirstPeriod() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 100000));
    ConcatenatingMediaSource firstMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    ConcatenatingMediaSource secondMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for first preparation and enable shuffling. Plays period 0.
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .setShuffleModeEnabled(true)
            // Set the second media source (with position reset).
            // Plays period 1 and 0 because of the reversed fake shuffle order.
            .setMediaSources(/* resetPosition= */ true, secondMediaSource)
            .play()
            .waitForPositionDiscontinuity()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlayedPeriodIndices(0, 1, 0);
  }

  @Test
  public void setPlaybackSpeedBeforePreparationCompletesSucceeds() throws Exception {
    // Test that no exception is thrown when playback parameters are updated between creating a
    // period and preparation of the period completing.
    final CountDownLatch createPeriodCalledCountDownLatch = new CountDownLatch(1);
    final FakeMediaPeriod[] fakeMediaPeriodHolder = new FakeMediaPeriod[1];
    MediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            // Defer completing preparation of the period until playback parameters have been set.
            fakeMediaPeriodHolder[0] =
                new FakeMediaPeriod(
                    trackGroupArray,
                    allocator,
                    TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                    mediaSourceEventDispatcher,
                    drmSessionManager,
                    drmEventDispatcher,
                    /* deferOnPrepared= */ true);
            createPeriodCalledCountDownLatch.countDown();
            return fakeMediaPeriodHolder[0];
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Block until createPeriod has been called on the fake media source.
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    try {
                      player.getClock().onThreadBlocked();
                      createPeriodCalledCountDownLatch.await();
                    } catch (InterruptedException e) {
                      throw new IllegalStateException(e);
                    }
                  }
                })
            // Set playback speed (while the fake media period is not yet prepared).
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f))
            // Complete preparation of the fake media period.
            .executeRunnable(() -> fakeMediaPeriodHolder[0].setPreparationComplete())
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void seekBeforePreparationCompletes_seeksToCorrectPosition() throws Exception {
    CountDownLatch createPeriodCalledCountDownLatch = new CountDownLatch(1);
    FakeMediaPeriod[] fakeMediaPeriodHolder = new FakeMediaPeriod[1];
    FakeMediaSource mediaSource =
        new FakeMediaSource(/* timeline= */ null, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            // Defer completing preparation of the period until seek has been sent.
            fakeMediaPeriodHolder[0] =
                new FakeMediaPeriod(
                    trackGroupArray,
                    allocator,
                    TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                    mediaSourceEventDispatcher,
                    drmSessionManager,
                    drmEventDispatcher,
                    /* deferOnPrepared= */ true);
            createPeriodCalledCountDownLatch.countDown();
            return fakeMediaPeriodHolder[0];
          }
        };
    AtomicLong positionWhenReady = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Ensure we use the MaskingMediaPeriod by delaying the initial timeline update.
            .delay(1)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(new FakeTimeline()))
            .waitForTimelineChanged()
            // Block until createPeriod has been called on the fake media source.
            .executeRunnable(
                () -> {
                  try {
                    createPeriodCalledCountDownLatch.await();
                  } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                  }
                })
            // Seek before preparation completes.
            .seek(5000)
            // Complete preparation of the fake media period.
            .executeRunnable(() -> fakeMediaPeriodHolder[0].setPreparationComplete())
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionWhenReady.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 0, /* positionMs= */ 2000)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionWhenReady.get()).isAtLeast(5000);
  }

  @Test
  public void stop_withoutReset_doesNotResetPosition_correctMasking() throws Exception {
    int[] currentMediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    long[] currentPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] totalBufferedDuration = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final FakeMediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 1000)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    currentPosition[0] = player.getCurrentPosition();
                    bufferedPosition[0] = player.getBufferedPosition();
                    totalBufferedDuration[0] = player.getTotalBufferedDuration();
                    player.stop(/* reset= */ false);
                    currentMediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    currentPosition[1] = player.getCurrentPosition();
                    bufferedPosition[1] = player.getBufferedPosition();
                    totalBufferedDuration[1] = player.getTotalBufferedDuration();
                  }
                })
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[2] = player.getCurrentMediaItemIndex();
                    currentPosition[2] = player.getCurrentPosition();
                    bufferedPosition[2] = player.getBufferedPosition();
                    totalBufferedDuration[2] = player.getTotalBufferedDuration();
                  }
                })
            .build();

    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource, mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPositionDiscontinuityReasonsEqual(Player.DISCONTINUITY_REASON_SEEK);

    assertThat(currentMediaItemIndex[0]).isEqualTo(1);
    assertThat(currentPosition[0]).isEqualTo(1000);
    assertThat(bufferedPosition[0]).isEqualTo(10000);
    assertThat(totalBufferedDuration[0]).isEqualTo(9000);

    assertThat(currentMediaItemIndex[1]).isEqualTo(1);
    assertThat(currentPosition[1]).isEqualTo(1000);
    assertThat(bufferedPosition[1]).isEqualTo(1000);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);

    assertThat(currentMediaItemIndex[2]).isEqualTo(1);
    assertThat(currentPosition[2]).isEqualTo(1000);
    assertThat(bufferedPosition[2]).isEqualTo(1000);
    assertThat(totalBufferedDuration[2]).isEqualTo(0);
  }

  @Test
  public void stop_withoutReset_releasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ false)
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    mediaSource.assertReleased();
  }

  @Test
  public void stop_withReset_doesResetPosition_correctMasking() throws Exception {
    int[] currentMediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    long[] currentPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] totalBufferedDuration = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final FakeMediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 1000)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    currentPosition[0] = player.getCurrentPosition();
                    bufferedPosition[0] = player.getBufferedPosition();
                    totalBufferedDuration[0] = player.getTotalBufferedDuration();
                    player.stop(/* reset= */ true);
                    currentMediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    currentPosition[1] = player.getCurrentPosition();
                    bufferedPosition[1] = player.getBufferedPosition();
                    totalBufferedDuration[1] = player.getTotalBufferedDuration();
                  }
                })
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[2] = player.getCurrentMediaItemIndex();
                    currentPosition[2] = player.getCurrentPosition();
                    bufferedPosition[2] = player.getBufferedPosition();
                    totalBufferedDuration[2] = player.getTotalBufferedDuration();
                  }
                })
            .build();

    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource, mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS);

    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_SEEK, Player.DISCONTINUITY_REASON_REMOVE);

    assertThat(currentMediaItemIndex[0]).isEqualTo(1);
    assertThat(currentPosition[0]).isGreaterThan(0);
    assertThat(bufferedPosition[0]).isEqualTo(10000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10000 - currentPosition[0]);

    assertThat(currentMediaItemIndex[1]).isEqualTo(0);
    assertThat(currentPosition[1]).isEqualTo(0);
    assertThat(bufferedPosition[1]).isEqualTo(0);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);

    assertThat(currentMediaItemIndex[2]).isEqualTo(0);
    assertThat(currentPosition[2]).isEqualTo(0);
    assertThat(bufferedPosition[2]).isEqualTo(0);
    assertThat(totalBufferedDuration[2]).isEqualTo(0);
  }

  @Test
  public void stop_withReset_releasesMediaSource() throws Exception {
    Timeline timeline = new FakeTimeline();
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    mediaSource.assertReleased();
  }

  @Test
  public void release_correctMasking() throws Exception {
    int[] currentMediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    long[] currentPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPosition = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] totalBufferedDuration = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final FakeMediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 1000)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    currentPosition[0] = player.getCurrentPosition();
                    bufferedPosition[0] = player.getBufferedPosition();
                    totalBufferedDuration[0] = player.getTotalBufferedDuration();
                    player.release();
                    currentMediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    currentPosition[1] = player.getCurrentPosition();
                    bufferedPosition[1] = player.getBufferedPosition();
                    totalBufferedDuration[1] = player.getTotalBufferedDuration();
                  }
                })
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndex[2] = player.getCurrentMediaItemIndex();
                    currentPosition[2] = player.getCurrentPosition();
                    bufferedPosition[2] = player.getBufferedPosition();
                    totalBufferedDuration[2] = player.getTotalBufferedDuration();
                  }
                })
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource, mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(currentMediaItemIndex[0]).isEqualTo(1);
    assertThat(currentPosition[0]).isGreaterThan(0);
    assertThat(bufferedPosition[0]).isEqualTo(10000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10000 - currentPosition[0]);

    assertThat(currentMediaItemIndex[1]).isEqualTo(1);
    assertThat(currentPosition[1]).isEqualTo(currentPosition[0]);
    assertThat(bufferedPosition[1]).isEqualTo(1000);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);

    assertThat(currentMediaItemIndex[2]).isEqualTo(1);
    assertThat(currentPosition[2]).isEqualTo(currentPosition[0]);
    assertThat(bufferedPosition[2]).isEqualTo(1000);
    assertThat(totalBufferedDuration[2]).isEqualTo(0);
  }

  @Test
  public void settingNewStartPositionPossibleAfterStopWithReset() throws Exception {
    Timeline timeline = new FakeTimeline();
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondSource =
        new FakeMediaSource(secondTimeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    AtomicInteger mediaItemIndexAfterStop = new AtomicInteger();
    AtomicLong positionAfterStop = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 1000)
            .setMediaSources(secondSource)
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndexAfterStop.set(player.getCurrentMediaItemIndex());
                    positionAfterStop.set(player.getCurrentPosition());
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .setExpectedPlayerEndedCount(2)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_IDLE,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    testRunner.assertTimelinesSame(
        placeholderTimeline,
        timeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // stop(true)
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(mediaItemIndexAfterStop.get()).isEqualTo(1);
    assertThat(positionAfterStop.get()).isAtLeast(1000L);
    testRunner.assertPlayedPeriodIndices(0, 1);
  }

  @Test
  public void resetPlaylistWithPreviousPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedMaskingTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(firstWindowId).build());
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedMaskingTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(secondWindowId).build());
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(/* mediaItemIndex= */ 0, /* positionMs= */ 2000, secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedMaskingTimeline, timeline, secondExpectedMaskingTimeline, secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isAtLeast(2000L);
  }

  @Test
  public void resetPlaylistStartsFromDefaultPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedPlaceholderTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(firstWindowId).build());
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedPlaceholderTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(secondWindowId).build());
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(/* resetPosition= */ true, secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedPlaceholderTimeline,
        timeline,
        secondExpectedPlaceholderTimeline,
        secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isEqualTo(0L);
  }

  @Test
  public void resetPlaylistWithoutResettingPositionStartsFromOldPosition() throws Exception {
    Object firstWindowId = new Object();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ firstWindowId));
    Timeline firstExpectedPlaceholderTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(firstWindowId).build());
    Object secondWindowId = new Object();
    Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ secondWindowId));
    Timeline secondExpectedPlaceholderTimeline =
        new MaskingMediaSource.PlaceholderTimeline(
            FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(secondWindowId).build());
    MediaSource secondSource = new FakeMediaSource(secondTimeline);
    AtomicLong positionAfterReprepare = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 2000)
            .setMediaSources(secondSource)
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionAfterReprepare.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelinesSame(
        firstExpectedPlaceholderTimeline,
        timeline,
        secondExpectedPlaceholderTimeline,
        secondTimeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertThat(positionAfterReprepare.get()).isAtLeast(2000L);
  }

  @Test
  public void stopDuringPreparationOverwritesPreparation() throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .stop(true)
            .waitForPendingPlayerCommands()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(new FakeTimeline())
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(placeholderTimeline, Timeline.EMPTY);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void stopAndSeekAfterStopDoesNotResetTimeline() throws Exception {
    Timeline timeline = new FakeTimeline();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .stop(false)
            .stop(false)
            // Wait until the player fully processed the second stop to see that no further
            // callbacks are triggered.
            .waitForPendingPlayerCommands()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelinesSame(placeholderTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void reprepareAfterPlaybackError() throws Exception {
    Timeline timeline = new FakeTimeline();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesSame(placeholderTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void seekAndReprepareAfterPlaybackError_keepsSeekPositionAndTimeline() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    FakeMediaSource fakeMediaSource = new FakeMediaSource();
    player.setMediaSource(fakeMediaSource);

    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    player
        .createMessage(
            (type, payload) -> {
              throw ExoPlaybackException.createForSource(
                  new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            })
        .send();
    TestPlayerRunHelper.runUntilError(player);
    player.seekTo(/* positionMs= */ 50);
    runUntilPendingCommandsAreFullyHandled(player);
    long positionAfterSeekHandled = player.getCurrentPosition();
    // Delay re-preparation to force player to use its masking mechanisms.
    fakeMediaSource.setAllowPreparation(false);
    player.prepare();
    runUntilPendingCommandsAreFullyHandled(player);
    long positionAfterReprepareHandled = player.getCurrentPosition();
    fakeMediaSource.setAllowPreparation(true);
    runUntilPlaybackState(player, Player.STATE_READY);
    long positionWhenFullyReadyAfterReprepare = player.getCurrentPosition();
    player.release();

    // Ensure we don't receive further timeline updates when repreparing.
    verify(mockListener)
        .onTimelineChanged(any(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    verify(mockListener).onTimelineChanged(any(), eq(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE));
    verify(mockListener, times(2)).onTimelineChanged(any(), anyInt());

    assertThat(positionAfterSeekHandled).isEqualTo(50);
    assertThat(positionAfterReprepareHandled).isEqualTo(50);
    assertThat(positionWhenFullyReadyAfterReprepare).isEqualTo(50);
  }

  @Test
  public void restartAfterEmptyTimelineWithShuffleModeEnabledUsesCorrectFirstPeriod()
      throws Exception {
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false, new FakeShuffleOrder(0));
    AtomicInteger mediaItemIndexAfterAddingSources = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .setShuffleModeEnabled(true)
            // Preparing with an empty media source will transition to ended state.
            .waitForPlaybackState(Player.STATE_ENDED)
            // Add two sources at once such that the default start position in the shuffled order
            // will be the second source.
            .executeRunnable(
                () ->
                    concatenatingMediaSource.addMediaSources(
                        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource())))
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndexAfterAddingSources.set(player.getCurrentMediaItemIndex());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatingMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(mediaItemIndexAfterAddingSources.get()).isEqualTo(1);
  }

  @Test
  public void playbackErrorAndReprepareDoesNotResetPosition() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    final long[] positionHolder = new long[3];
    final int[] mediaItemIndexHolder = new int[3];
    final FakeMediaSource firstMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 500)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position while in error state
                    positionHolder[0] = player.getCurrentPosition();
                    mediaItemIndexHolder[0] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position while repreparing.
                    positionHolder[1] = player.getCurrentPosition();
                    mediaItemIndexHolder[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position after repreparation finished.
                    positionHolder[2] = player.getCurrentPosition();
                    mediaItemIndexHolder[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(positionHolder[0]).isAtLeast(500L);
    assertThat(positionHolder[1]).isEqualTo(positionHolder[0]);
    assertThat(positionHolder[2]).isEqualTo(positionHolder[0]);
    assertThat(mediaItemIndexHolder[0]).isEqualTo(1);
    assertThat(mediaItemIndexHolder[1]).isEqualTo(1);
    assertThat(mediaItemIndexHolder[2]).isEqualTo(1);
  }

  @Test
  public void seekAfterPlaybackError() throws Exception {
    final Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    final long[] positionHolder = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final int[] mediaItemIndexHolder = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    final FakeMediaSource firstMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .playUntilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 500)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForPlaybackState(Player.STATE_IDLE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position while in error state
                    positionHolder[0] = player.getCurrentPosition();
                    mediaItemIndexHolder[0] = player.getCurrentMediaItemIndex();
                  }
                })
            .seek(/* mediaItemIndex= */ 0, /* positionMs= */ C.TIME_UNSET)
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position while in error state
                    positionHolder[1] = player.getCurrentPosition();
                    mediaItemIndexHolder[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Position after prepare.
                    positionHolder[2] = player.getCurrentPosition();
                    mediaItemIndexHolder[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build();

    assertThrows(
        ExoPlaybackException.class,
        () ->
            testRunner
                .start()
                .blockUntilActionScheduleFinished(TIMEOUT_MS)
                .blockUntilEnded(TIMEOUT_MS));
    assertThat(positionHolder[0]).isAtLeast(500L);
    assertThat(positionHolder[1]).isEqualTo(0L);
    assertThat(positionHolder[2]).isEqualTo(0L);
    assertThat(mediaItemIndexHolder[0]).isEqualTo(1);
    assertThat(mediaItemIndexHolder[1]).isEqualTo(0);
    assertThat(mediaItemIndexHolder[2]).isEqualTo(0);
  }

  @Test
  public void playbackErrorAndReprepareWithPositionResetKeepsWindowSequenceNumber()
      throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForPlaybackState(Player.STATE_IDLE)
            .seek(0, C.TIME_UNSET)
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .build();
    HashSet<Long> reportedWindowSequenceNumbers = new HashSet<>();
    AnalyticsListener listener =
        new AnalyticsListener() {
          @Override
          public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
            if (eventTime.mediaPeriodId != null) {
              reportedWindowSequenceNumbers.add(eventTime.mediaPeriodId.windowSequenceNumber);
            }
          }

          @Override
          public void onPlayWhenReadyChanged(
              EventTime eventTime,
              boolean playWhenReady,
              @Player.PlayWhenReadyChangeReason int reason) {
            if (eventTime.mediaPeriodId != null) {
              reportedWindowSequenceNumbers.add(eventTime.mediaPeriodId.windowSequenceNumber);
            }
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setActionSchedule(actionSchedule)
            .setAnalyticsListener(listener)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    assertThat(reportedWindowSequenceNumbers).hasSize(1);
  }

  @Test
  public void playbackErrorTwiceStillKeepsTimeline() throws Exception {
    final Timeline timeline = new FakeTimeline();
    final FakeMediaSource mediaSource2 = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForPlaybackState(Player.STATE_IDLE)
            .setMediaSources(/* resetPosition= */ false, mediaSource2)
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .throwPlaybackException(
                ExoPlaybackException.createForSource(
                    new IOException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_IDLE)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build();
    try {
      testRunner.start().blockUntilActionScheduleFinished(TIMEOUT_MS).blockUntilEnded(TIMEOUT_MS);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected exception.
    }
    testRunner.assertTimelinesSame(placeholderTimeline, timeline, placeholderTimeline, timeline);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void sendMessagesDuringPreparation() throws Exception {
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline();
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void multipleSendMessages() throws Exception {
    PositionGrabbingMessageTarget target50 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target80 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target80, /* positionMs= */ 80)
            .sendMessage(target50, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target50.positionMs).isAtLeast(50L);
    assertThat(target80.positionMs).isAtLeast(80L);
    assertThat(target80.positionMs).isAtLeast(target50.positionMs);
  }

  @Test
  public void sendMessagesFromStartPositionOnlyOnce() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged()
            .pause()
            .sendMessage(
                (messageType, payload) -> counter.getAndIncrement(),
                /* mediaItemIndex= */ 0,
                /* positionMs= */ 2000,
                /* deleteAfterDelivery= */ false)
            .seek(/* positionMs= */ 2000)
            .delay(/* delayMs= */ 2000)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Test
  public void multipleSendMessagesAtSameTime() throws Exception {
    PositionGrabbingMessageTarget target1 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target2 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target1, /* positionMs= */ 50)
            .sendMessage(target2, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.positionMs).isAtLeast(50L);
    assertThat(target2.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesMultiPeriodResolution() throws Exception {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 10, /* id= */ 0));
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesAtStartAndEndOfPeriod() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 2);
    PositionGrabbingMessageTarget targetStartFirstPeriod = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndMiddlePeriodResolved =
        new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndMiddlePeriodUnresolved =
        new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetStartMiddlePeriod = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndLastPeriodResolved = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget targetEndLastPeriodUnresolved =
        new PositionGrabbingMessageTarget();
    long duration1Ms = timeline.getWindow(0, new Window()).getDurationMs();
    long duration2Ms = timeline.getWindow(1, new Window()).getDurationMs();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .sendMessage(targetStartFirstPeriod, /* mediaItemIndex= */ 0, /* positionMs= */ 0)
            .sendMessage(
                targetEndMiddlePeriodResolved,
                /* mediaItemIndex= */ 0,
                /* positionMs= */ duration1Ms - 1)
            .sendMessage(
                targetEndMiddlePeriodUnresolved,
                /* mediaItemIndex= */ 0,
                /* positionMs= */ C.TIME_END_OF_SOURCE)
            .sendMessage(targetStartMiddlePeriod, /* mediaItemIndex= */ 1, /* positionMs= */ 0)
            .sendMessage(
                targetEndLastPeriodResolved,
                /* mediaItemIndex= */ 1,
                /* positionMs= */ duration2Ms - 1)
            .sendMessage(
                targetEndLastPeriodUnresolved,
                /* mediaItemIndex= */ 1,
                /* positionMs= */ C.TIME_END_OF_SOURCE)
            .waitForMessage(targetEndLastPeriodUnresolved)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(targetStartFirstPeriod.mediaItemIndex).isEqualTo(0);
    assertThat(targetStartFirstPeriod.positionMs).isAtLeast(0L);
    assertThat(targetEndMiddlePeriodResolved.mediaItemIndex).isEqualTo(0);
    assertThat(targetEndMiddlePeriodResolved.positionMs).isAtLeast(duration1Ms - 1);
    assertThat(targetEndMiddlePeriodUnresolved.mediaItemIndex).isEqualTo(0);
    assertThat(targetEndMiddlePeriodUnresolved.positionMs).isAtLeast(duration1Ms - 1);
    assertThat(targetEndMiddlePeriodResolved.positionMs)
        .isEqualTo(targetEndMiddlePeriodUnresolved.positionMs);
    assertThat(targetStartMiddlePeriod.mediaItemIndex).isEqualTo(1);
    assertThat(targetStartMiddlePeriod.positionMs).isAtLeast(0L);
    assertThat(targetEndLastPeriodResolved.mediaItemIndex).isEqualTo(1);
    assertThat(targetEndLastPeriodResolved.positionMs).isAtLeast(duration2Ms - 1);
    assertThat(targetEndLastPeriodUnresolved.mediaItemIndex).isEqualTo(1);
    assertThat(targetEndLastPeriodUnresolved.positionMs).isAtLeast(duration2Ms - 1);
    assertThat(targetEndLastPeriodResolved.positionMs)
        .isEqualTo(targetEndLastPeriodUnresolved.positionMs);
  }

  @Test
  public void sendMessagesSeekOnDeliveryTimeDuringPreparation() throws Exception {
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .seek(/* positionMs= */ 50)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesSeekOnDeliveryTimeAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline();
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* positionMs= */ 50)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesSeekAfterDeliveryTimeDuringPreparation() throws Exception {
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .seek(/* positionMs= */ 51)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void sendMessagesSeekAfterDeliveryTimeAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline();
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .sendMessage(target, /* positionMs= */ 50)
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* positionMs= */ 51)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void sendMessagesRepeatDoesNotRepost() throws Exception {
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(target, /* positionMs= */ 50)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .play()
            .waitForPositionDiscontinuity()
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(1);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesRepeatWithoutDeletingDoesRepost() throws Exception {
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .sendMessage(
                target,
                /* mediaItemIndex= */ 0,
                /* positionMs= */ 50,
                /* deleteAfterDelivery= */ false)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 1)
            .playUntilStartOfMediaItem(/* mediaItemIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.messageCount).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesMoveCurrentMediaItemIndex() throws Exception {
    Timeline timeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* positionMs= */ 50)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(secondTimeline))
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
    assertThat(target.mediaItemIndex).isEqualTo(1);
  }

  @Test
  public void sendMessagesMultiWindowDuringPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* mediaItemIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.mediaItemIndex).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesMultiWindowAfterPreparation() throws Exception {
    Timeline timeline = new FakeTimeline(/* windowCount= */ 3);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* mediaItemIndex = */ 2, /* positionMs= */ 50)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.mediaItemIndex).isEqualTo(2);
    assertThat(target.positionMs).isAtLeast(50L);
  }

  @Test
  public void sendMessagesMoveMediaItemIndex() throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1));
    final Timeline secondTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0));
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT);
    PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForTimelineChanged(
                timeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .sendMessage(target, /* mediaItemIndex = */ 1, /* positionMs= */ 50)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(secondTimeline))
            .waitForTimelineChanged(
                secondTimeline, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .seek(/* mediaItemIndex= */ 0, /* positionMs= */ 0)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target.positionMs).isAtLeast(50L);
    assertThat(target.mediaItemIndex).isEqualTo(0);
  }

  @Test
  public void sendMessagesNonLinearPeriodOrder() throws Exception {
    Timeline fakeTimeline = new FakeTimeline();
    MediaSource[] fakeMediaSources = {
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
      new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT)
    };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(false, new FakeShuffleOrder(3), fakeMediaSources);
    PositionGrabbingMessageTarget target1 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target2 = new PositionGrabbingMessageTarget();
    PositionGrabbingMessageTarget target3 = new PositionGrabbingMessageTarget();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .sendMessage(target1, /* mediaItemIndex = */ 0, /* positionMs= */ 50)
            .sendMessage(target2, /* mediaItemIndex = */ 1, /* positionMs= */ 50)
            .sendMessage(target3, /* mediaItemIndex = */ 2, /* positionMs= */ 50)
            .setShuffleModeEnabled(true)
            .seek(/* mediaItemIndex= */ 2, /* positionMs= */ 0)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(target1.mediaItemIndex).isEqualTo(0);
    assertThat(target2.mediaItemIndex).isEqualTo(1);
    assertThat(target3.mediaItemIndex).isEqualTo(2);
  }

  @Test
  public void cancelMessageBeforeDelivery() throws Exception {
    final PositionGrabbingMessageTarget target = new PositionGrabbingMessageTarget();
    final AtomicReference<PlayerMessage> message = new AtomicReference<>();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    message.set(
                        player.createMessage(target).setPosition(/* positionMs= */ 50).send());
                  }
                })
            // Play a bit to ensure message arrived in internal player.
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 30)
            .executeRunnable(() -> message.get().cancel())
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(message.get().isCanceled()).isTrue();
    assertThat(target.messageCount).isEqualTo(0);
  }

  @Test
  public void cancelRepeatedMessageAfterDelivery() throws Exception {
    final CountingMessageTarget target = new CountingMessageTarget();
    final AtomicReference<PlayerMessage> message = new AtomicReference<>();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    message.set(
                        player
                            .createMessage(target)
                            .setPosition(/* positionMs= */ 50)
                            .setDeleteAfterDelivery(/* deleteAfterDelivery= */ false)
                            .send());
                  }
                })
            // Play until the message has been delivered.
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 51)
            // Seek back, cancel the message, and play past the same position again.
            .seek(/* positionMs= */ 0)
            .executeRunnable(() -> message.get().cancel())
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(message.get().isCanceled()).isTrue();
    assertThat(target.messageCount).isEqualTo(1);
  }

  @Test
  public void sendMessages_withMediaRemoval_triggersCorrectMessagesAndDoesNotThrow()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));
    player
        .createMessage((messageType, payload) -> {})
        .setPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 0)
        .setDeleteAfterDelivery(false)
        .send();
    PlayerMessage.Target secondMediaItemTarget = mock(PlayerMessage.Target.class);
    player
        .createMessage(secondMediaItemTarget)
        .setPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 0)
        .setDeleteAfterDelivery(false)
        .send();

    // Play through media once to trigger all messages. This ensures any internally saved message
    // indices are non-zero.
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    verify(secondMediaItemTarget).handleMessage(anyInt(), any());

    // Remove first item and play second item again to check if message is triggered again.
    // After removal, any internally saved message indices are invalid and will throw
    // IndexOutOfBoundsException if used without updating.
    // See https://github.com/google/ExoPlayer/issues/7278.
    player.removeMediaItem(/* index= */ 0);
    player.seekTo(/* positionMs= */ 0);
    runUntilPlaybackState(player, Player.STATE_ENDED);

    assertThat(player.getPlayerError()).isNull();
    verify(secondMediaItemTarget, times(2)).handleMessage(anyInt(), any());
  }

  @Test
  public void setAndSwitchSurface() throws Exception {
    final List<Integer> rendererMessages = new ArrayList<>();
    Renderer videoRenderer =
        new FakeRenderer(C.TRACK_TYPE_VIDEO) {
          @Override
          public void handleMessage(@MessageType int messageType, @Nullable Object message)
              throws ExoPlaybackException {
            super.handleMessage(messageType, message);
            rendererMessages.add(messageType);
          }
        };
    ActionSchedule actionSchedule = addSurfaceSwitch(new ActionSchedule.Builder(TAG)).build();
    new ExoPlayerTestRunner.Builder(context)
        .setRenderers(videoRenderer)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(Collections.frequency(rendererMessages, Renderer.MSG_SET_VIDEO_OUTPUT)).isEqualTo(2);
  }

  @Test
  public void switchSurfaceOnEndedState() throws Exception {
    ActionSchedule.Builder scheduleBuilder =
        new ActionSchedule.Builder(TAG).waitForPlaybackState(Player.STATE_ENDED);
    ActionSchedule waitForEndedAndSwitchSchedule = addSurfaceSwitch(scheduleBuilder).build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(Timeline.EMPTY)
        .setActionSchedule(waitForEndedAndSwitchSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void timelineUpdateDropsPrebufferedPeriods() throws Exception {
    Timeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 2));
    final Timeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 3));
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline1, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Ensure next period is pre-buffered by playing until end of first period.
            .playUntilPosition(
                /* mediaItemIndex= */ 0,
                /* positionMs= */ Util.usToMs(TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US))
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline2))
            .waitForTimelineChanged(
                timeline2, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);
    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPlayedPeriodIndices(0, 1);
    // Assert that the second period was re-created from the new timeline.
    assertThat(mediaSource.getCreatedMediaPeriods()).hasSize(3);
    assertThat(mediaSource.getCreatedMediaPeriods().get(0).periodUid)
        .isEqualTo(timeline1.getUidOfPeriod(/* periodIndex= */ 0));
    assertThat(mediaSource.getCreatedMediaPeriods().get(1).periodUid)
        .isEqualTo(timeline1.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaSource.getCreatedMediaPeriods().get(2).periodUid)
        .isEqualTo(timeline2.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaSource.getCreatedMediaPeriods().get(1).windowSequenceNumber)
        .isGreaterThan(mediaSource.getCreatedMediaPeriods().get(0).windowSequenceNumber);
    assertThat(mediaSource.getCreatedMediaPeriods().get(2).windowSequenceNumber)
        .isGreaterThan(mediaSource.getCreatedMediaPeriods().get(1).windowSequenceNumber);
  }

  @Test
  public void timelineUpdateWithNewMidrollAdCuePoint_dropsPrebufferedPeriod() throws Exception {
    Timeline timeline1 = new FakeTimeline(TimelineWindowDefinition.createPlaceholder(/* tag= */ 0));
    AdPlaybackState adPlaybackStateWithMidroll =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1,
            /* adGroupTimesUs...= */ TimelineWindowDefinition
                    .DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + 5 * C.MICROS_PER_SECOND);
    Timeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000,
                adPlaybackStateWithMidroll));
    FakeMediaSource mediaSource = new FakeMediaSource(timeline1, ExoPlayerTestRunner.VIDEO_FORMAT);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline2))
            .waitForTimelineChanged(
                timeline2, /* expectedReason= */ Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    testRunner.assertPlayedPeriodIndices(0);
    testRunner.assertPositionDiscontinuityReasonsEqual(
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(mediaSource.getCreatedMediaPeriods()).hasSize(4);
    assertThat(mediaSource.getCreatedMediaPeriods().get(0).nextAdGroupIndex)
        .isEqualTo(C.INDEX_UNSET);
    assertThat(mediaSource.getCreatedMediaPeriods().get(1).nextAdGroupIndex).isEqualTo(0);
    assertThat(mediaSource.getCreatedMediaPeriods().get(2).adGroupIndex).isEqualTo(0);
    assertThat(mediaSource.getCreatedMediaPeriods().get(3).adGroupIndex).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void seekPastBufferingMidroll_playsAdAndThenContentFromSeekPosition() throws Exception {
    long adGroupWindowTimeMs = 1_000;
    long seekPositionMs = 95_000;
    long contentDurationMs = 100_000;
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1,
            /* adGroupTimesUs...= */ TimelineWindowDefinition
                    .DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + Util.msToUs(adGroupWindowTimeMs));
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(contentDurationMs),
                adPlaybackState));
    AtomicBoolean hasCreatedAdMediaPeriod = new AtomicBoolean();
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline) {
          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            if (id.adGroupIndex == 0) {
              hasCreatedAdMediaPeriod.set(true);
            }
            return super.createPeriod(id, allocator, startPositionUs);
          }
        };
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaSource(mediaSource);
    // Throw on the playback thread if the player position reaches a value that is just less than
    // seek position. This ensures that playback stops and the assertion on the player position
    // below fails, even if a long time passes between detecting the discontinuity and asserting.
    player
        .createMessage(
            (messageType, payload) -> {
              throw new IllegalStateException();
            })
        .setPosition(seekPositionMs - 1)
        .send();
    player.pause();
    player.prepare();

    // Block until the midroll has started buffering, then seek after the midroll before playing.
    runMainLooperUntil(hasCreatedAdMediaPeriod::get);
    player.seekTo(seekPositionMs);
    player.play();

    // When the ad finishes, the player position should be at or after the requested seek position.
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(player.getCurrentPosition()).isAtLeast(seekPositionMs);
  }

  @Test
  public void repeatedSeeksToUnpreparedPeriodInSameWindowKeepsWindowSequenceNumber()
      throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 2,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10 * C.MICROS_PER_SECOND));
    FakeMediaSource mediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* mediaItemIndex= */ 0, /* positionMs= */ 9999)
            // Wait after each seek until the internal player has updated its state.
            .waitForPendingPlayerCommands()
            .seek(/* mediaItemIndex= */ 0, /* positionMs= */ 1)
            .waitForPendingPlayerCommands()
            .seek(/* mediaItemIndex= */ 0, /* positionMs= */ 9999)
            .waitForPendingPlayerCommands()
            .play()
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilEnded(TIMEOUT_MS);

    testRunner.assertPlayedPeriodIndices(0, 1, 0, 1);
    assertThat(mediaSource.getCreatedMediaPeriods())
        .containsAtLeast(
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0),
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 0));
    assertThat(mediaSource.getCreatedMediaPeriods())
        .doesNotContain(
            new MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 1), /* windowSequenceNumber= */ 1));
  }

  @Test
  public void invalidSeekFallsBackToSubsequentPeriodOfTheRemovedPeriod() throws Exception {
    Timeline timeline = new FakeTimeline();
    CountDownLatch sourceReleasedCountDownLatch = new CountDownLatch(/* count= */ 1);
    MediaSource mediaSourceToRemove =
        new FakeMediaSource(timeline) {
          @Override
          protected void releaseSourceInternal() {
            super.releaseSourceInternal();
            sourceReleasedCountDownLatch.countDown();
          }
        };
    ConcatenatingMediaSource mediaSource =
        new ConcatenatingMediaSource(mediaSourceToRemove, new FakeMediaSource(timeline));
    final int[] windowCount = {C.INDEX_UNSET};
    final long[] position = {C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaSource.removeMediaSource(/* index= */ 0);
                    try {
                      // Wait until the source to be removed is released on the playback thread. So
                      // the timeline in EPII has one window only, but the update here in EPI is
                      // stuck until we finished our executable here. So when seeking below, we will
                      // seek in the timeline which still has two windows in EPI, but when the seek
                      // arrives in EPII the actual timeline has one window only. Hence it tries to
                      // find the subsequent period of the removed period and finds it.
                      player.getClock().onThreadBlocked();
                      sourceReleasedCountDownLatch.await();
                    } catch (InterruptedException e) {
                      throw new IllegalStateException(e);
                    }
                    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 1000L);
                  }
                })
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    windowCount[0] = player.getCurrentTimeline().getWindowCount();
                    position[0] = player.getCurrentPosition();
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    // Expect the first window to be the current.
    assertThat(windowCount[0]).isEqualTo(1);
    // Expect the position to be in the default position.
    assertThat(position[0]).isEqualTo(0L);
  }

  @Test
  public void onPlayerErrorChanged_isNotifiedForNullError() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSource(
        new FakeMediaSource(/* timeline= */ null) {
          @Override
          public void maybeThrowSourceInfoRefreshError() throws IOException {
            throw new IOException();
          }
        });
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.prepare();
    player.play();
    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);
    // The media source fails preparation, so we expect both methods to be called.
    verify(mockListener).onPlayerErrorChanged(error);
    verify(mockListener).onPlayerError(error);

    reset(mockListener);

    player.setMediaSource(new FakeMediaSource());
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, STATE_ENDED);
    // Now the player, which had a playback error, was re-prepared causing the error to be cleared.
    // We expect the change to null to be notified, but not onPlayerError.
    verify(mockListener).onPlayerErrorChanged(ArgumentMatchers.isNull());
    verify(mockListener, never()).onPlayerError(any());
  }

  @Test
  public void recursivePlayerChangesReportConsistentValuesForAllListeners() throws Exception {
    // We add two listeners to the player. The first stops the player as soon as it's ready and both
    // record the state change events they receive.
    final AtomicReference<Player> playerReference = new AtomicReference<>();
    final List<Integer> playerListener1States = new ArrayList<>();
    final List<Integer> playerListener2States = new ArrayList<>();
    final Player.Listener playerListener1 =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playerListener1States.add(playbackState);
            if (playbackState == Player.STATE_READY) {
              playerReference.get().stop(/* reset= */ true);
            }
          }
        };
    final Player.Listener playerListener2 =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playerListener2States.add(playbackState);
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener1);
                    player.addListener(playerListener2);
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(playerListener1States)
        .containsExactly(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE)
        .inOrder();
    assertThat(playerListener2States)
        .containsExactly(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE)
        .inOrder();
  }

  @Test
  public void recursivePlayerChangesAreReportedInCorrectOrder() throws Exception {
    // The listener stops the player as soon as it's ready (which should report a timeline and state
    // change) and sets playWhenReady to false when the timeline callback is received.
    final AtomicReference<Player> playerReference = new AtomicReference<>();
    final List<Boolean> playerListenerPlayWhenReady = new ArrayList<>();
    final List<Integer> playerListenerStates = new ArrayList<>();
    List<Integer> sequence = new ArrayList<>();
    final Player.Listener playerListener =
        new Player.Listener() {

          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playerListenerStates.add(playbackState);
            if (playbackState == Player.STATE_READY) {
              playerReference.get().stop(/* reset= */ true);
              sequence.add(0);
            }
          }

          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            if (timeline.isEmpty()) {
              playerReference.get().pause();
              sequence.add(1);
            }
          }

          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playerListenerPlayWhenReady.add(playWhenReady);
            sequence.add(2);
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(playerListenerStates)
        .containsExactly(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_IDLE)
        .inOrder();
    assertThat(playerListenerPlayWhenReady).containsExactly(false).inOrder();
    assertThat(sequence).containsExactly(0, 1, 2).inOrder();
  }

  @Test
  public void recursiveTimelineChangeInStopAreReportedInCorrectOrder() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 2);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 3);
    final AtomicReference<ExoPlayer> playerReference = new AtomicReference<>();
    FakeMediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_IDLE) {
              playerReference.get().setMediaSource(secondMediaSource);
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            // Ensure there are no further pending callbacks.
            .delay(1)
            .stop(/* reset= */ true)
            .waitForPlaybackState(Player.STATE_IDLE)
            .prepare()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setActionSchedule(actionSchedule)
            .setTimeline(firstTimeline)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertTimelinesSame(
        new FakeMediaSource.InitialTimeline(firstTimeline),
        firstTimeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void clippedLoopedPeriodsArePlayedFully() throws Exception {
    long startPositionUs = 300_000;
    long expectedDurationUs = 700_000;
    MediaSource mediaSource =
        new ClippingMediaSource(
            new FakeMediaSource(), startPositionUs, startPositionUs + expectedDurationUs);
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong positionAtDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    AtomicLong clockAtStartMs = new AtomicLong(C.TIME_UNSET);
    AtomicLong clockAtDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_READY && clockAtStartMs.get() == C.TIME_UNSET) {
              clockAtStartMs.set(clock.elapsedRealtime());
            }
          }

          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              positionAtDiscontinuityMs.set(playerReference.get().getCurrentPosition());
              clockAtDiscontinuityMs.set(clock.elapsedRealtime());
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            .pause()
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .waitForPlaybackState(Player.STATE_READY)
            // Play until the media repeats once.
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 1)
            .playUntilStartOfMediaItem(/* mediaItemIndex= */ 0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setClock(clock)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionAtDiscontinuityMs.get()).isAtLeast(0L);
    assertThat(clockAtDiscontinuityMs.get() - clockAtStartMs.get())
        .isAtLeast(Util.usToMs(expectedDurationUs));
  }

  @Test
  public void updateTrackSelectorThenSeekToUnpreparedPeriod_returnsEmptyTrackGroups()
      throws Exception {
    // Use unset duration to prevent pre-loading of the second window.
    Timeline timelineUnsetDuration =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ C.TIME_UNSET));
    Timeline timelineSetDuration = new FakeTimeline();
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(timelineUnsetDuration, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(timelineSetDuration, ExoPlayerTestRunner.AUDIO_FORMAT));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 0)
            .waitForPendingPlayerCommands()
            .play()
            .build();
    List<Tracks> tracksList = new ArrayList<>();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setSupportedFormats(ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT)
        .setActionSchedule(actionSchedule)
        .setPlayerListener(
            new Player.Listener() {
              @Override
              public void onTracksChanged(Tracks tracks) {
                tracksList.add(tracks);
              }
            })
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
    assertThat(tracksList).hasSize(3);
    // First track groups of the 1st period are reported.
    // Then the seek to an unprepared period will result in empty track groups being returned.
    // Then the track groups of the 2nd period are reported.
    assertThat(tracksList.get(0).getGroups().get(0).getTrackFormat(0))
        .isEqualTo(ExoPlayerTestRunner.VIDEO_FORMAT);
    assertThat(tracksList.get(1)).isEqualTo(Tracks.EMPTY);
    assertThat(tracksList.get(2).getGroups().get(0).getTrackFormat(0))
        .isEqualTo(ExoPlayerTestRunner.AUDIO_FORMAT);
  }

  @Test
  public void removingLoopingLastPeriodFromPlaylistDoesNotThrow() throws Exception {
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 100_000));
    MediaSource mediaSource = new FakeMediaSource(timeline);
    ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource(mediaSource);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            // Play almost to end to ensure the current period is fully buffered.
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 90)
            // Enable repeat mode to trigger the creation of new media periods.
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            // Remove the media source.
            .executeRunnable(concatenatingMediaSource::clear)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatingMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void seekToUnpreparedWindowWithNonZeroOffsetInConcatenationStartsAtCorrectPosition()
      throws Exception {
    FakeMediaSource mediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSource clippedMediaSource =
        new ClippingMediaSource(
            mediaSource,
            /* startPositionUs= */ 3 * C.MICROS_PER_SECOND,
            /* endPositionUs= */ C.TIME_END_OF_SOURCE);
    MediaSource concatenatedMediaSource = new ConcatenatingMediaSource(clippedMediaSource);
    AtomicLong positionWhenReady = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Seek while unprepared and wait until the player handled all updates.
            .seek(/* positionMs= */ 10)
            .waitForPendingPlayerCommands()
            // Finish preparation.
            .executeRunnable(() -> mediaSource.setNewSourceInfo(new FakeTimeline()))
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionWhenReady.set(player.getContentPosition());
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatedMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionWhenReady.get()).isEqualTo(10);
  }

  @Test
  public void seekToUnpreparedWindowWithMultiplePeriodsInConcatenationStartsAtCorrectPeriod()
      throws Exception {
    long periodDurationMs = 5000;
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 2,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 2 * periodDurationMs * 1000));
    FakeMediaSource mediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSource concatenatedMediaSource = new ConcatenatingMediaSource(mediaSource);
    AtomicInteger periodIndexWhenReady = new AtomicInteger();
    AtomicLong positionWhenReady = new AtomicLong();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Seek 10ms into the second period.
            .seek(/* positionMs= */ periodDurationMs + 10)
            .executeRunnable(() -> mediaSource.setNewSourceInfo(timeline))
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    periodIndexWhenReady.set(player.getCurrentPeriodIndex());
                    positionWhenReady.set(player.getContentPosition());
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatedMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(periodIndexWhenReady.get()).isEqualTo(1);
    assertThat(positionWhenReady.get()).isEqualTo(periodDurationMs + 10);
  }

  @Test
  public void periodTransitionReportsCorrectBufferedPosition() throws Exception {
    int periodCount = 3;
    long periodDurationUs = 5 * C.MICROS_PER_SECOND;
    long windowDurationUs = periodCount * periodDurationUs;
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                periodCount,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                windowDurationUs));
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong bufferedPositionAtFirstDiscontinuityMs = new AtomicLong(C.TIME_UNSET);
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              if (bufferedPositionAtFirstDiscontinuityMs.get() == C.TIME_UNSET) {
                bufferedPositionAtFirstDiscontinuityMs.set(
                    playerReference.get().getBufferedPosition());
              }
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            .pause()
            // Wait until all periods are fully buffered.
            .waitForIsLoading(/* targetIsLoading= */ true)
            .waitForIsLoading(/* targetIsLoading= */ false)
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setTimeline(timeline)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(bufferedPositionAtFirstDiscontinuityMs.get())
        .isEqualTo(Util.usToMs(windowDurationUs));
  }

  @Test
  public void contentWithInitialSeekPositionAfterPrerollAdStartsAtSeekPosition() throws Exception {
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(/* adsPerAdGroup= */ 3, /* adGroupTimesUs...= */ 0);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000,
                adPlaybackState));
    FakeMediaSource fakeMediaSource = new FakeMediaSource(/* timeline= */ null);
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong contentStartPositionMs = new AtomicLong(C.TIME_UNSET);
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              contentStartPositionMs.set(playerReference.get().getContentPosition());
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            .seek(/* positionMs= */ 5_000)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(() -> fakeMediaSource.setNewSourceInfo(fakeTimeline))
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(fakeMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(contentStartPositionMs.get()).isAtLeast(5_000L);
  }

  @Test
  public void contentWithoutInitialSeekStartsAtDefaultPositionAfterPrerollAd() throws Exception {
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(/* adsPerAdGroup= */ 3, /* adGroupTimesUs...= */ 0);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000,
                /* defaultPositionUs= */ 5_000_000,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                adPlaybackState));
    FakeMediaSource fakeMediaSource = new FakeMediaSource(/* timeline= */ null);
    AtomicReference<Player> playerReference = new AtomicReference<>();
    AtomicLong contentStartPositionMs = new AtomicLong(C.TIME_UNSET);
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(@DiscontinuityReason int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              contentStartPositionMs.set(playerReference.get().getContentPosition());
            }
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerReference.set(player);
                    player.addListener(playerListener);
                  }
                })
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(() -> fakeMediaSource.setNewSourceInfo(fakeTimeline))
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(fakeMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(contentStartPositionMs.get()).isAtLeast(5_000L);
  }

  @Test
  public void adInMovingLiveWindow_keepsContentPosition() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */ 42_000_004_000_000L);
    Timeline liveTimeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000,
                /* defaultPositionUs= */ 3_000_000,
                /* windowOffsetInFirstPeriodUs= */ 42_000_000_000_000L,
                adPlaybackState));
    Timeline liveTimeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000,
                /* defaultPositionUs= */ 3_000_000,
                /* windowOffsetInFirstPeriodUs= */ 42_000_002_000_000L,
                adPlaybackState));
    FakeMediaSource fakeMediaSource = new FakeMediaSource(liveTimeline1);

    player.setMediaSource(fakeMediaSource);
    player.prepare();
    player.play();
    // Wait until the ad is playing.
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    long contentPositionBeforeLiveWindowUpdateMs = player.getContentPosition();
    fakeMediaSource.setNewSourceInfo(liveTimeline2);
    runUntilTimelineChanged(player);
    long contentPositionAfterLiveWindowUpdateMs = player.getContentPosition();
    player.release();

    assertThat(contentPositionBeforeLiveWindowUpdateMs).isEqualTo(4000);
    assertThat(contentPositionAfterLiveWindowUpdateMs).isEqualTo(2000);
  }

  @Test
  public void setPlaybackSpeedConsecutivelyNotifiesListenerForEveryChangeOnceAndIsMasked()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    List<Float> maskedPlaybackSpeeds = new ArrayList<>();
    List<Float> reportedPlaybackSpeeds = new ArrayList<>();
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            reportedPlaybackSpeeds.add(playbackParameters.speed);
          }
        });
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.setPlaybackSpeed(1.1f);
    maskedPlaybackSpeeds.add(player.getPlaybackParameters().speed);
    player.setPlaybackSpeed(1.2f);
    maskedPlaybackSpeeds.add(player.getPlaybackParameters().speed);
    player.setPlaybackSpeed(1.3f);
    maskedPlaybackSpeeds.add(player.getPlaybackParameters().speed);
    runUntilPendingCommandsAreFullyHandled(player);
    player.release();

    assertThat(reportedPlaybackSpeeds).containsExactly(1.1f, 1.2f, 1.3f).inOrder();
    assertThat(maskedPlaybackSpeeds).isEqualTo(reportedPlaybackSpeeds);
  }

  @Test
  public void
      setUnsupportedPlaybackSpeedConsecutivelyNotifiesListenerForEveryChangeOnceAndResetsOnceHandled()
          throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setRenderers(new AudioClockRendererWithoutSpeedChangeSupport())
            .build();
    List<PlaybackParameters> reportedPlaybackParameters = new ArrayList<>();
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            reportedPlaybackParameters.add(playbackParameters);
          }
        });
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.setPlaybackSpeed(1.1f);
    player.setPlaybackSpeed(1.2f);
    player.setPlaybackSpeed(1.3f);
    runUntilPendingCommandsAreFullyHandled(player);
    player.release();

    assertThat(reportedPlaybackParameters)
        .containsExactly(
            new PlaybackParameters(/* speed= */ 1.1f),
            new PlaybackParameters(/* speed= */ 1.2f),
            new PlaybackParameters(/* speed= */ 1.3f),
            PlaybackParameters.DEFAULT)
        .inOrder();
  }

  @Test
  public void
      setUnsupportedPlaybackSpeedDirectlyFollowedByDisablingTheRendererAndSupportedPlaybackSpeed_keepsCorrectFinalSpeedAndInformsListenersCorrectly()
          throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setRenderers(new AudioClockRendererWithoutSpeedChangeSupport())
            .build();
    List<PlaybackParameters> reportedPlaybackParameters = new ArrayList<>();
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            reportedPlaybackParameters.add(playbackParameters);
          }
        });
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.setPlaybackSpeed(2f);
    // We need to do something that reliably triggers a position sync with the renderer, but no
    // further playback progress as we want to test what happens if the parameter reset is still
    // pending when we disable the audio renderer below. Calling play and pause will achieve this.
    player.play();
    player.pause();
    // Disabling the audio renderer and setting a new speed should work, and should not be affected
    // by the still pending parameter reset from above.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, /* disabled= */ true)
            .build());
    player.setPlaybackSpeed(5f);
    runUntilPendingCommandsAreFullyHandled(player);
    player.release();

    assertThat(reportedPlaybackParameters)
        .containsExactly(
            new PlaybackParameters(/* speed= */ 2f), new PlaybackParameters(/* speed= */ 5f))
        .inOrder();
  }

  @Test
  public void simplePlaybackHasNoPlaybackSuppression() throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .pause()
            .play()
            .build();
    AtomicBoolean seenPlaybackSuppression = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(
              @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            seenPlaybackSuppression.set(true);
          }
        };
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .setPlayerListener(listener)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(seenPlaybackSuppression.get()).isFalse();
  }

  @Test
  public void audioFocusDenied() throws Exception {
    ShadowAudioManager shadowAudioManager = shadowOf(context.getSystemService(AudioManager.class));
    shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);

    AtomicBoolean playWhenReady = new AtomicBoolean();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playWhenReady.set(player.getPlayWhenReady());
                  }
                })
            .build();
    AtomicBoolean seenPlaybackSuppression = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(
              @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            seenPlaybackSuppression.set(true);
          }
        };
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .setPlayerListener(listener)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(playWhenReady.get()).isFalse();
    assertThat(seenPlaybackSuppression.get()).isFalse();
  }

  @Test
  public void delegatingMediaSourceApproach() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000_000));
    final ConcatenatingMediaSource underlyingSource = new ConcatenatingMediaSource();
    WrappingMediaSource delegatingMediaSource =
        new WrappingMediaSource(underlyingSource) {
          @Override
          public void prepareSourceInternal() {
            underlyingSource.addMediaSource(
                new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT));
            underlyingSource.addMediaSource(
                new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT));
            super.prepareSourceInternal();
          }

          @Override
          public boolean isSingleWindow() {
            return false;
          }

          @Override
          @Nullable
          public Timeline getInitialTimeline() {
            return Timeline.EMPTY;
          }
        };
    int[] currentMediaItemIndices = new int[1];
    long[] currentPlaybackPositions = new long[1];
    long[] windowCounts = new long[1];
    int seekToMediaItemIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 5000)
            .waitForTimelineChanged(
                /* expectedTimeline= */ null, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(delegatingMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
    assertArrayEquals(new long[] {2}, windowCounts);
    assertArrayEquals(new int[] {seekToMediaItemIndex}, currentMediaItemIndices);
    assertArrayEquals(new long[] {5_000}, currentPlaybackPositions);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void seekTo_mediaItemIndexIsReset_deprecated() throws Exception {
    FakeTimeline fakeTimeline = new FakeTimeline();
    FakeMediaSource mediaSource = new FakeMediaSource(fakeTimeline);
    final int[] mediaItemIndex = {C.INDEX_UNSET};
    final long[] positionMs = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .playUntilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 3000)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionMs[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    //noinspection deprecation
                    player.prepare(mediaSource);
                    player.seekTo(/* positionMs= */ 7000);
                    positionMs[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    positionMs[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource, mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(3000L);
    assertThat(positionMs[1]).isEqualTo(7000L);
    assertThat(positionMs[2]).isEqualTo(7000L);
    assertThat(bufferedPositions[0]).isAtLeast(3000L);
    assertThat(bufferedPositions[1]).isEqualTo(7000L);
    assertThat(bufferedPositions[2])
        .isEqualTo(fakeTimeline.getWindow(0, new Window()).getDurationMs());
  }

  @Test
  public void seekTo_mediaItemIndexIsReset() throws Exception {
    FakeTimeline fakeTimeline = new FakeTimeline();
    FakeMediaSource mediaSource = new FakeMediaSource(fakeTimeline);
    final int[] mediaItemIndex = {C.INDEX_UNSET};
    final long[] positionMs = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .playUntilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 3000)
            .pause()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionMs[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    player.setMediaSource(mediaSource, /* startPositionMs= */ 7000);
                    player.prepare();
                    positionMs[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    positionMs[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource, mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS);

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(3000);
    assertThat(positionMs[1]).isEqualTo(7000);
    assertThat(positionMs[2]).isEqualTo(7000);
    assertThat(bufferedPositions[0]).isAtLeast(3000);
    assertThat(bufferedPositions[1]).isEqualTo(7000);
    assertThat(bufferedPositions[2])
        .isEqualTo(fakeTimeline.getWindow(0, new Window()).getDurationMs());
  }

  @Test
  public void seekTo_singlePeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(9000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 9200));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(9000);
    assertThat(bufferedPositions[0]).isEqualTo(9200);
    assertThat(totalBufferedDuration[0]).isEqualTo(200);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(9200);
    assertThat(totalBufferedDuration[1]).isEqualTo(200);
  }

  @Test
  public void seekTo_singlePeriod_beyondBufferedData_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(9200);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 9200));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(9200);
    assertThat(bufferedPositions[0]).isEqualTo(9200);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(9200);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);
  }

  @Test
  public void seekTo_backwardsSinglePeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(1000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 9200));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(1000);
    assertThat(bufferedPositions[0]).isEqualTo(1000);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);
  }

  @Test
  public void seekTo_backwardsMultiplePeriods_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(0, 1000);
          }
        },
        /* pauseMediaItemIndex= */ 1,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 9200));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(1000);
    assertThat(bufferedPositions[0]).isEqualTo(1000);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);
  }

  @Test
  public void seekTo_toUnbufferedPeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(2, 1000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 0));

    assertThat(mediaItemIndex[0]).isEqualTo(2);
    assertThat(positionMs[0]).isEqualTo(1000);
    assertThat(bufferedPositions[0]).isEqualTo(1000);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);

    assertThat(mediaItemIndex[1]).isEqualTo(2);
    assertThat(positionMs[1]).isEqualTo(1000);
    assertThat(bufferedPositions[1]).isEqualTo(1000);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);
  }

  @Test
  public void seekTo_toLoadingPeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(1, 1000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource());

    assertThat(mediaItemIndex[0]).isEqualTo(1);
    assertThat(positionMs[0]).isEqualTo(1000);
    // TODO(b/160450903): Verify masking of buffering properties when behaviour in EPII is fully
    // covered.
    //    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    //    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void seekTo_toLoadingPeriod_withinPartiallyBufferedData_correctMaskingPosition()
      throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(1, 1000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(1);
    assertThat(positionMs[0]).isEqualTo(1000);
    // TODO(b/160450903): Verify masking of buffering properties when behaviour in EPII is fully
    // covered.
    //    assertThat(bufferedPositions[0]).isEqualTo(1000);
    //    assertThat(totalBufferedDuration[0]).isEqualTo(0);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(4000);
    assertThat(totalBufferedDuration[1]).isEqualTo(3000);
  }

  @Test
  public void seekTo_toLoadingPeriod_beyondBufferedData_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(1, 5000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(1);
    assertThat(positionMs[0]).isEqualTo(5000);
    assertThat(bufferedPositions[0]).isEqualTo(5000);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);

    assertThat(mediaItemIndex[1]).isEqualTo(1);
    assertThat(positionMs[1]).isEqualTo(5000);
    assertThat(bufferedPositions[1]).isEqualTo(5000);
    assertThat(totalBufferedDuration[1]).isEqualTo(0);
  }

  @Test
  public void seekTo_toInnerFullyBufferedPeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.seekTo(1, 5000);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(1);
    assertThat(positionMs[0]).isEqualTo(5000);
    // TODO(b/160450903): Verify masking of buffering properties when behaviour in EPII is fully
    // covered.
    //    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    //    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void addMediaSource_withinBufferedPeriods_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.addMediaSource(
                /* index= */ 1, createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 0));
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(8000);
    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void moveMediaItem_behindLoadingPeriod_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 2);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(8000);
    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void moveMediaItem_undloadedBehindPlaying_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.moveMediaItem(/* currentIndex= */ 3, /* newIndex= */ 1);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 0));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(8000);
    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void removeMediaItem_removePlayingWindow_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.removeMediaItem(/* index= */ 0);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(0);
    // TODO(b/160450903): Verify masking of buffering properties when behaviour in EPII is fully
    // covered.
    //    assertThat(bufferedPositions[0]).isEqualTo(4000);
    //    assertThat(totalBufferedDuration[0]).isEqualTo(4000);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(4000);
    assertThat(totalBufferedDuration[1]).isEqualTo(4000);
  }

  @Test
  public void removeMediaItem_removeLoadingWindow_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.removeMediaItem(/* index= */ 2);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isAtLeast(8000);
    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[1]);
  }

  @Test
  public void removeMediaItem_removeInnerFullyBufferedWindow_correctMaskingPosition()
      throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.removeMediaItem(/* index= */ 1);
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(8000);
    assertThat(bufferedPositions[0]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[0]).isEqualTo(10_000 - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(0);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(10_000);
    assertThat(totalBufferedDuration[1]).isEqualTo(10_000 - positionMs[0]);
  }

  @Test
  public void clearMediaItems_correctMaskingPosition() throws Exception {
    final int[] mediaItemIndex = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] positionMs = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] bufferedPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] totalBufferedDuration = {C.INDEX_UNSET, C.INDEX_UNSET};

    runPositionMaskingCapturingActionSchedule(
        new PlayerRunnable() {
          @Override
          public void run(ExoPlayer player) {
            player.clearMediaItems();
          }
        },
        /* pauseMediaItemIndex= */ 0,
        mediaItemIndex,
        positionMs,
        bufferedPositions,
        totalBufferedDuration,
        new FakeMediaSource(),
        new FakeMediaSource(),
        createPartiallyBufferedMediaSource(/* maxBufferedPositionMs= */ 4000));

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(positionMs[0]).isEqualTo(0);
    assertThat(bufferedPositions[0]).isEqualTo(0);
    assertThat(totalBufferedDuration[0]).isEqualTo(0);

    assertThat(mediaItemIndex[1]).isEqualTo(mediaItemIndex[0]);
    assertThat(positionMs[1]).isEqualTo(positionMs[0]);
    assertThat(bufferedPositions[1]).isEqualTo(bufferedPositions[0]);
    assertThat(totalBufferedDuration[1]).isEqualTo(totalBufferedDuration[0]);
  }

  private void runPositionMaskingCapturingActionSchedule(
      PlayerRunnable actionRunnable,
      int pauseMediaItemIndex,
      int[] mediaItemIndex,
      long[] positionMs,
      long[] bufferedPosition,
      long[] totalBufferedDuration,
      MediaSource... mediaSources)
      throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .playUntilPosition(pauseMediaItemIndex, /* positionMs= */ 8000)
            .executeRunnable(actionRunnable)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    positionMs[0] = player.getCurrentPosition();
                    bufferedPosition[0] = player.getBufferedPosition();
                    totalBufferedDuration[0] = player.getTotalBufferedDuration();
                  }
                })
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    positionMs[1] = player.getCurrentPosition();
                    bufferedPosition[1] = player.getBufferedPosition();
                    totalBufferedDuration[1] = player.getTotalBufferedDuration();
                  }
                })
            .stop()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSources)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
  }

  private static FakeMediaSource createPartiallyBufferedMediaSource(long maxBufferedPositionMs) {
    int windowOffsetInFirstPeriodUs = 1_000_000;
    FakeTimeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 1,
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ 10_000_000L,
                /* defaultPositionUs= */ 0,
                windowOffsetInFirstPeriodUs,
                AdPlaybackState.NONE));
    return new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
      @Override
      protected MediaPeriod createMediaPeriod(
          MediaPeriodId id,
          TrackGroupArray trackGroupArray,
          Allocator allocator,
          MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
          DrmSessionManager drmSessionManager,
          DrmSessionEventListener.EventDispatcher drmEventDispatcher,
          @Nullable TransferListener transferListener) {
        return new FakeMediaPeriod(
            trackGroupArray,
            allocator,
            /* trackDataFactory= */ (format, mediaPeriodId) ->
                ImmutableList.of(
                    oneByteSample(windowOffsetInFirstPeriodUs, C.BUFFER_FLAG_KEY_FRAME),
                    oneByteSample(
                        windowOffsetInFirstPeriodUs + Util.msToUs(maxBufferedPositionMs),
                        C.BUFFER_FLAG_KEY_FRAME)),
            mediaSourceEventDispatcher,
            drmSessionManager,
            drmEventDispatcher,
            /* deferOnPrepared= */ false);
      }
    };
  }

  @Test
  public void addMediaSource_whilePlayingAd_correctMasking() throws Exception {
    long contentDurationMs = 10_000;
    long adDurationMs = 100_000;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0);
    adPlaybackState = adPlaybackState.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    adPlaybackState =
        adPlaybackState.withAvailableAdUri(
            /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, Uri.parse("https://google.com/ad"));
    long[][] durationsUs = new long[1][];
    durationsUs[0] = new long[] {Util.msToUs(adDurationMs)};
    adPlaybackState = adPlaybackState.withAdDurationsUs(durationsUs);
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(contentDurationMs),
                adPlaybackState));
    FakeMediaSource adsMediaSource = new FakeMediaSource(adTimeline);
    int[] mediaItemIndex = new int[] {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    long[] positionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.INDEX_UNSET};
    long[] bufferedPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.INDEX_UNSET};
    long[] totalBufferedDurationMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.INDEX_UNSET};
    boolean[] isPlayingAd = new boolean[3];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.addMediaSource(/* index= */ 1, new FakeMediaSource());
                    mediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    isPlayingAd[0] = player.isPlayingAd();
                    positionMs[0] = player.getCurrentPosition();
                    bufferedPositionMs[0] = player.getBufferedPosition();
                    totalBufferedDurationMs[0] = player.getTotalBufferedDuration();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    isPlayingAd[1] = player.isPlayingAd();
                    positionMs[1] = player.getCurrentPosition();
                    bufferedPositionMs[1] = player.getBufferedPosition();
                    totalBufferedDurationMs[1] = player.getTotalBufferedDuration();
                  }
                })
            .playUntilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 8000)
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.addMediaSource(new FakeMediaSource());
                    mediaItemIndex[2] = player.getCurrentMediaItemIndex();
                    isPlayingAd[2] = player.isPlayingAd();
                    positionMs[2] = player.getCurrentPosition();
                    bufferedPositionMs[2] = player.getBufferedPosition();
                    totalBufferedDurationMs[2] = player.getTotalBufferedDuration();
                  }
                })
            .play()
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(adsMediaSource, new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(isPlayingAd[0]).isTrue();
    assertThat(positionMs[0]).isAtMost(adDurationMs);
    assertThat(bufferedPositionMs[0]).isEqualTo(adDurationMs);
    assertThat(totalBufferedDurationMs[0]).isAtLeast(adDurationMs - positionMs[0]);

    assertThat(mediaItemIndex[1]).isEqualTo(0);
    assertThat(isPlayingAd[1]).isTrue();
    assertThat(positionMs[1]).isAtMost(adDurationMs);
    assertThat(bufferedPositionMs[1]).isEqualTo(adDurationMs);
    assertThat(totalBufferedDurationMs[1]).isAtLeast(adDurationMs - positionMs[1]);

    assertThat(mediaItemIndex[2]).isEqualTo(0);
    assertThat(isPlayingAd[2]).isFalse();
    assertThat(positionMs[2]).isEqualTo(8000);
    assertThat(bufferedPositionMs[2]).isEqualTo(contentDurationMs);
    assertThat(totalBufferedDurationMs[2]).isAtLeast(contentDurationMs - positionMs[2]);
  }

  @Test
  public void seekTo_whilePlayingAd_correctMasking() throws Exception {
    long contentDurationMs = 10_000;
    long adDurationMs = 4_000;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0);
    adPlaybackState = adPlaybackState.withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1);
    adPlaybackState =
        adPlaybackState.withAvailableAdUri(
            /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, Uri.parse("https://google.com/ad"));
    long[][] durationsUs = new long[1][];
    durationsUs[0] = new long[] {Util.msToUs(adDurationMs)};
    adPlaybackState = adPlaybackState.withAdDurationsUs(durationsUs);
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(contentDurationMs),
                adPlaybackState));
    FakeMediaSource adsMediaSource = new FakeMediaSource(adTimeline);
    int[] mediaItemIndex = new int[] {C.INDEX_UNSET, C.INDEX_UNSET};
    long[] positionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET};
    long[] totalBufferedDurationMs = new long[] {C.TIME_UNSET, C.TIME_UNSET};
    boolean[] isPlayingAd = new boolean[2];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 8000);
                    mediaItemIndex[0] = player.getCurrentMediaItemIndex();
                    isPlayingAd[0] = player.isPlayingAd();
                    positionMs[0] = player.getCurrentPosition();
                    bufferedPositionMs[0] = player.getBufferedPosition();
                    totalBufferedDurationMs[0] = player.getTotalBufferedDuration();
                  }
                })
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndex[1] = player.getCurrentMediaItemIndex();
                    isPlayingAd[1] = player.isPlayingAd();
                    positionMs[1] = player.getCurrentPosition();
                    bufferedPositionMs[1] = player.getBufferedPosition();
                    totalBufferedDurationMs[1] = player.getTotalBufferedDuration();
                  }
                })
            .stop()
            .build();

    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(adsMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(mediaItemIndex[0]).isEqualTo(0);
    assertThat(isPlayingAd[0]).isTrue();
    assertThat(positionMs[0]).isEqualTo(0);
    assertThat(bufferedPositionMs[0]).isEqualTo(adDurationMs);
    assertThat(totalBufferedDurationMs[0]).isEqualTo(adDurationMs);

    assertThat(mediaItemIndex[1]).isEqualTo(0);
    assertThat(isPlayingAd[1]).isTrue();
    assertThat(positionMs[1]).isEqualTo(0);
    assertThat(bufferedPositionMs[1]).isEqualTo(adDurationMs);
    assertThat(totalBufferedDurationMs[1]).isEqualTo(adDurationMs);
  }

  // https://github.com/google/ExoPlayer/issues/8349
  @Test
  public void seekTo_whilePlayingAd_doesntBlockFutureUpdates() throws Exception {
    long contentDurationMs = 10_000;
    long adDurationMs = 4_000;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAvailableAdUri(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                Uri.parse("https://google.com/ad"));
    long[][] durationsUs = new long[1][];
    durationsUs[0] = new long[] {Util.msToUs(adDurationMs)};
    adPlaybackState = adPlaybackState.withAdDurationsUs(durationsUs);
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(contentDurationMs),
                adPlaybackState));
    FakeMediaSource adsMediaSource = new FakeMediaSource(adTimeline);

    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaSource(adsMediaSource);
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.seekTo(0, 8000);
    player.play();

    // This times out if playback info updates after the seek are blocked.
    runUntilPlaybackState(player, Player.STATE_ENDED);
  }

  @Test
  public void seekTo_beyondSSAIMidRolls_seekAdjustedAndRequestedContentPositionKept()
      throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            false);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.seekTo(/* positionMs= */ 4000);
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(listener, times(6))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(1, 2, 0, 0, 0, 0).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // seek discontinuities
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).periodIndex).isEqualTo(3);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).positionMs).isEqualTo(4000);
    // seek adjustment
    assertThat(oldPositions.get(1).periodIndex).isEqualTo(3);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(4000);
    assertThat(newPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(1).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(4000);
    // auto transition from ad to end of period
    assertThat(oldPositions.get(2).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(2).adGroupIndex).isEqualTo(0);
    assertThat(oldPositions.get(2).adIndexInAdGroup).isEqualTo(0);
    assertThat(oldPositions.get(2).positionMs).isEqualTo(2500);
    assertThat(oldPositions.get(2).contentPositionMs).isEqualTo(4000);
    assertThat(newPositions.get(2).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(2).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(2).positionMs).isEqualTo(2500);
    // auto transition to next ad period
    assertThat(oldPositions.get(3).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(3).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(3).periodIndex).isEqualTo(2);
    assertThat(newPositions.get(3).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(3).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(3).contentPositionMs).isEqualTo(4000);
    // auto transition from ad to end of period
    assertThat(oldPositions.get(4).periodIndex).isEqualTo(2);
    assertThat(oldPositions.get(4).adGroupIndex).isEqualTo(0);
    assertThat(oldPositions.get(4).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(4).periodIndex).isEqualTo(2);
    assertThat(newPositions.get(4).adGroupIndex).isEqualTo(-1);
    // auto transition to final content period with seek position
    assertThat(oldPositions.get(5).periodIndex).isEqualTo(2);
    assertThat(oldPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(5).periodIndex).isEqualTo(3);
    assertThat(newPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(5).contentPositionMs).isEqualTo(4000);
  }

  @Test
  public void seekTo_beyondSSAIMidRollsConsecutiveContentPeriods_seekAdjusted() throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            true,
            false,
            false);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.seekTo(/* positionMs= */ 7000);
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(listener, times(5))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(1, 2, 0, 0, 0).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // seek
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).periodIndex).isEqualTo(3);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).positionMs).isEqualTo(7000);
    // seek adjustment
    assertThat(oldPositions.get(1).periodIndex).isEqualTo(3);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(7000);
    assertThat(newPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
  }

  @Test
  public void seekTo_beforeSSAIMidRolls_requestedContentPositionNotPropagatedIntoAds()
      throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            false);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    player.play();

    player.seekTo(1600);
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(listener, times(6))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(1, 0, 0, 0, 0, 0).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // seek discontinuity
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(newPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(1600);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(1600);
    // auto discontinuities through ads has correct content position that is not the seek position.
    assertThat(newPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(1).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(2500);
    assertThat(newPositions.get(2).contentPositionMs).isEqualTo(2500);
    assertThat(newPositions.get(3).contentPositionMs).isEqualTo(2500);
    assertThat(newPositions.get(4).contentPositionMs).isEqualTo(2500);
    // Content resumes at expected position that is not the seek position.
    assertThat(newPositions.get(5).periodIndex).isEqualTo(3);
    assertThat(newPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(5).positionMs).isEqualTo(2500);
    assertThat(newPositions.get(5).contentPositionMs).isEqualTo(2500);
  }

  @Test
  public void seekTo_toSAIMidRolls_playsMidRolls() throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            false);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    player.seekTo(2500);
    player.play();

    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(listener, times(6))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(1, 2, 0, 0, 0, 0).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // seek discontinuity
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
    // seek adjustment discontinuity
    assertThat(oldPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(0);
    // auto transition to last frame of first ad period
    assertThat(oldPositions.get(2).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(2).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(2).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(2).adGroupIndex).isEqualTo(-1);
    // auto transition to second ad period
    assertThat(oldPositions.get(3).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(3).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(3).periodIndex).isEqualTo(2);
    assertThat(newPositions.get(3).adGroupIndex).isEqualTo(0);
    // auto transition to last frame of second ad period
    assertThat(oldPositions.get(4).periodIndex).isEqualTo(2);
    assertThat(oldPositions.get(4).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(4).periodIndex).isEqualTo(2);
    assertThat(newPositions.get(4).adGroupIndex).isEqualTo(-1);
    // auto transition to the final content period
    assertThat(oldPositions.get(5).periodIndex).isEqualTo(2);
    assertThat(oldPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(5).periodIndex).isEqualTo(3);
    assertThat(newPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(5).positionMs).isEqualTo(2500);
    assertThat(newPositions.get(5).contentPositionMs).isEqualTo(2500);
  }

  @Test
  public void seekTo_toPlayedSAIMidRolls_requestedContentPositionNotPropagatedIntoAds()
      throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ 2,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            false);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.pause();
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    player.seekTo(2500);
    player.play();

    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(listener, times(1))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(1).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // seek discontinuity
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    // TODO(bachinger): Incorrect masking. Skipped played prerolls not taken into account by masking
    assertThat(newPositions.get(0).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
  }

  @Test
  public void play_playedSSAIPreMidPostRollsMultiPeriodWindow_contentPeriodTransitionsOnly()
      throws Exception {
    ArgumentCaptor<PositionInfo> oldPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    ArgumentCaptor<Integer> reasonArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    FakeTimeline adTimeline =
        FakeTimeline.createMultiPeriodAdTimeline(
            "windowId",
            /* numberOfPlayedAds= */ Integer.MAX_VALUE,
            /* isAdPeriodFlags...= */ true,
            false,
            true,
            true,
            false,
            true,
            true,
            true);
    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline, ExoPlayerTestRunner.AUDIO_FORMAT),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.prepare();

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    ArgumentCaptor<Integer> playbackStateCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(listener, times(3)).onPlaybackStateChanged(playbackStateCaptor.capture());
    assertThat(playbackStateCaptor.getAllValues()).containsExactly(2, 3, 4).inOrder();
    verify(listener, times(3))
        .onPositionDiscontinuity(
            oldPositionArgumentCaptor.capture(),
            newPositionArgumentCaptor.capture(),
            reasonArgumentCaptor.capture());
    assertThat(reasonArgumentCaptor.getAllValues()).containsExactly(0, 0, 0).inOrder();
    List<PositionInfo> oldPositions = oldPositionArgumentCaptor.getAllValues();
    List<PositionInfo> newPositions = newPositionArgumentCaptor.getAllValues();
    // Auto discontinuity from the empty pre-roll period to the first content period.
    assertThat(oldPositions.get(0).periodIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).periodIndex).isEqualTo(1);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    // Auto discontinuity from the first content to the second content period.
    assertThat(oldPositions.get(1).periodIndex).isEqualTo(1);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(1).periodIndex).isEqualTo(4);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(1).positionMs).isEqualTo(1250);
    // Auto discontinuity from the second content period to the last frame of the last ad period.
    assertThat(oldPositions.get(2).periodIndex).isEqualTo(4);
    assertThat(oldPositions.get(2).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(2).periodIndex).isEqualTo(7);
    assertThat(newPositions.get(2).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(2).positionMs).isEqualTo(2500);
  }

  @Test
  public void play_playedSSAIPreMidPostRollsSinglePeriodWindow_noDiscontinuities()
      throws Exception {
    AdPlaybackState adPlaybackState =
        addAdGroupToAdPlaybackState(
            new AdPlaybackState("adsId"),
            /* fromPositionUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ C.MICROS_PER_SECOND);
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + (3 * C.MICROS_PER_SECOND),
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ C.MICROS_PER_SECOND);
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + (5 * C.MICROS_PER_SECOND),
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ C.MICROS_PER_SECOND);
    adPlaybackState =
        addAdGroupToAdPlaybackState(
            adPlaybackState,
            /* fromPositionUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US
                + (9 * C.MICROS_PER_SECOND),
            /* contentResumeOffsetUs= */ 0,
            /* adDurationsUs...= */ C.MICROS_PER_SECOND);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup+ */ 0);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 1, /* adIndexInAdGroup+ */ 0);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 2, /* adIndexInAdGroup+ */ 0);
    adPlaybackState =
        adPlaybackState.withPlayedAd(/* adGroupIndex= */ 3, /* adIndexInAdGroup+ */ 0);
    FakeTimeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                "windowId",
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ DEFAULT_WINDOW_DURATION_US,
                /* defaultPositionUs= */ 0,
                /* windowOffsetInFirstPeriodUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                /* adPlaybackStates= */ ImmutableList.of(adPlaybackState),
                MediaItem.EMPTY));

    Listener listener = mock(Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(listener);
    AtomicReference<ServerSideAdInsertionMediaSource> sourceReference = new AtomicReference<>();
    sourceReference.set(
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(adTimeline, ExoPlayerTestRunner.AUDIO_FORMAT),
            contentTimeline -> {
              sourceReference
                  .get()
                  .setAdPlaybackStates(adTimeline.getAdPlaybackStates(/* windowIndex= */ 0));
              return true;
            }));
    player.setMediaSource(sourceReference.get());
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    long finalPositionMs = player.getCurrentPosition();
    player.release();

    assertThat(finalPositionMs).isEqualTo(6000);
    verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    ArgumentCaptor<Integer> playbackStateCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(listener, times(3)).onPlaybackStateChanged(playbackStateCaptor.capture());
    assertThat(playbackStateCaptor.getAllValues()).containsExactly(2, 3, 4).inOrder();
  }

  @Test
  public void becomingNoisyIgnoredIfBecomingNoisyHandlingIsDisabled() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.play();

    player.setHandleAudioBecomingNoisy(false);
    deliverBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    runUntilPendingCommandsAreFullyHandled(player);
    boolean playWhenReadyAfterBroadcast = player.getPlayWhenReady();
    player.release();

    assertThat(playWhenReadyAfterBroadcast).isTrue();
  }

  @Test
  public void pausesWhenBecomingNoisyIfBecomingNoisyHandlingIsEnabled() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.play();

    player.setHandleAudioBecomingNoisy(true);
    deliverBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    runUntilPendingCommandsAreFullyHandled(player);
    boolean playWhenReadyAfterBroadcast = player.getPlayWhenReady();
    player.release();

    assertThat(playWhenReadyAfterBroadcast).isFalse();
  }

  @Test
  public void loadControlNeverWantsToLoad_throwsIllegalStateException() {
    LoadControl neverLoadingLoadControl =
        new DefaultLoadControl() {
          @Override
          public boolean shouldContinueLoading(
              long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
            return false;
          }

          @Override
          public boolean shouldStartPlayback(
              long bufferedDurationUs,
              float playbackSpeed,
              boolean rebuffering,
              long targetLiveOffsetUs) {
            return true;
          }
        };

    // Use chunked data to ensure the player actually needs to continue loading and playing.
    FakeAdaptiveDataSet.Factory dataSetFactory =
        new FakeAdaptiveDataSet.Factory(
            /* chunkDurationUs= */ 500_000, /* bitratePercentStdDev= */ 10.0, new Random(0));
    MediaSource chunkedMediaSource =
        new FakeAdaptiveMediaSource(
            new FakeTimeline(),
            new TrackGroupArray(new TrackGroup(ExoPlayerTestRunner.VIDEO_FORMAT)),
            new FakeChunkSource.Factory(dataSetFactory, new FakeDataSource.Factory()));

    ExoPlaybackException exception =
        assertThrows(
            ExoPlaybackException.class,
            () ->
                new ExoPlayerTestRunner.Builder(context)
                    .setLoadControl(neverLoadingLoadControl)
                    .setMediaSources(chunkedMediaSource)
                    .build()
                    .start()
                    .blockUntilEnded(TIMEOUT_MS));
    assertThat(exception.type).isEqualTo(ExoPlaybackException.TYPE_UNEXPECTED);
    assertThat(exception.getUnexpectedException()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void
      nextLoadPositionExceedingLoadControlMaxBuffer_whileCurrentLoadInProgress_doesNotThrowException()
          throws Exception {
    long maxBufferUs = 2 * C.MICROS_PER_SECOND;
    LoadControl loadControlWithMaxBufferUs =
        new DefaultLoadControl() {
          @Override
          public boolean shouldContinueLoading(
              long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
            return bufferedDurationUs < maxBufferUs;
          }

          @Override
          public boolean shouldStartPlayback(
              long bufferedDurationUs,
              float playbackSpeed,
              boolean rebuffering,
              long targetLiveOffsetUs) {
            return true;
          }
        };
    MediaSource mediaSourceWithLoadInProgress =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                mediaSourceEventDispatcher) {
              @Override
              public long getBufferedPositionUs() {
                // Pretend not to have buffered data yet.
                return 0;
              }

              @Override
              public long getNextLoadPositionUs() {
                // Set next load position beyond the maxBufferUs configured in the LoadControl.
                return Long.MAX_VALUE;
              }

              @Override
              public boolean isLoading() {
                return true;
              }
            };
          }
        };
    FakeRenderer rendererWaitingForData =
        new FakeRenderer(C.TRACK_TYPE_VIDEO) {
          @Override
          public boolean isReady() {
            return false;
          }
        };

    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setRenderers(rendererWaitingForData)
            .setLoadControl(loadControlWithMaxBufferUs)
            .build();
    player.setMediaSource(mediaSourceWithLoadInProgress);
    player.prepare();

    // Wait until the MediaSource is prepared, i.e. returned its timeline, and at least one
    // iteration of doSomeWork after this was run.
    TestPlayerRunHelper.runUntilTimelineChanged(player);
    runUntilPendingCommandsAreFullyHandled(player);

    assertThat(player.getPlayerError()).isNull();
  }

  @Test
  public void loadControlNeverWantsToPlay_playbackDoesNotGetStuck() throws Exception {
    LoadControl neverLoadingOrPlayingLoadControl =
        new DefaultLoadControl() {
          @Override
          public boolean shouldContinueLoading(
              long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
            return true;
          }

          @Override
          public boolean shouldStartPlayback(
              long bufferedDurationUs,
              float playbackSpeed,
              boolean rebuffering,
              long targetLiveOffsetUs) {
            return false;
          }
        };

    // Use chunked data to ensure the player actually needs to continue loading and playing.
    FakeAdaptiveDataSet.Factory dataSetFactory =
        new FakeAdaptiveDataSet.Factory(
            /* chunkDurationUs= */ 500_000, /* bitratePercentStdDev= */ 10.0, new Random(0));
    MediaSource chunkedMediaSource =
        new FakeAdaptiveMediaSource(
            new FakeTimeline(),
            new TrackGroupArray(new TrackGroup(ExoPlayerTestRunner.VIDEO_FORMAT)),
            new FakeChunkSource.Factory(dataSetFactory, new FakeDataSource.Factory()));

    new ExoPlayerTestRunner.Builder(context)
        .setLoadControl(neverLoadingOrPlayingLoadControl)
        .setMediaSources(chunkedMediaSource)
        .build()
        .start()
        // This throws if playback doesn't finish within timeout.
        .blockUntilEnded(TIMEOUT_MS);
  }

  @Test
  public void shortAdFollowedByUnpreparedAd_playbackDoesNotGetStuck() throws Exception {
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(/* adsPerAdGroup= */ 2, /* adGroupTimesUs...= */ 0);
    long shortAdDurationMs = 1_000;
    adPlaybackState =
        adPlaybackState.withAdDurationsUs(new long[][] {{shortAdDurationMs, shortAdDurationMs}});
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(10000),
                adPlaybackState));
    // Simulate the second ad not being prepared.
    FakeMediaSource mediaSource =
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                FakeMediaPeriod.TrackDataFactory.singleSampleWithTimeUs(0),
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                /* deferOnPrepared= */ id.adIndexInAdGroup == 1);
          }
        };
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaSource(mediaSource);
    player.prepare();
    player.play();

    // The player is not stuck in the buffering state.
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
  }

  @Test
  public void moveMediaItem() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    Timeline expectedPlaceholderTimeline =
        new FakeTimeline(
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 1),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 2));
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged(
                /* expectedTimeline= */ null, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .moveMediaItem(/* currentIndex= */ 0, /* newIndex= */ 1)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource1, mediaSource2)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedRealTimeline = new FakeTimeline(firstWindowDefinition, secondWindowDefinition);
    Timeline expectedRealTimelineAfterMove =
        new FakeTimeline(secondWindowDefinition, firstWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedPlaceholderTimeline, expectedRealTimeline, expectedRealTimelineAfterMove);
  }

  @Test
  public void removeMediaItem() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    TimelineWindowDefinition thirdWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 3,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    Timeline timeline3 = new FakeTimeline(thirdWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    MediaSource mediaSource3 = new FakeMediaSource(timeline3);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .removeMediaItem(/* index= */ 0)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource1, mediaSource2, mediaSource3)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedPlaceholderTimeline =
        new FakeTimeline(
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 1),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 2),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 3));
    Timeline expectedRealTimeline =
        new FakeTimeline(firstWindowDefinition, secondWindowDefinition, thirdWindowDefinition);
    Timeline expectedRealTimelineAfterRemove =
        new FakeTimeline(secondWindowDefinition, thirdWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedPlaceholderTimeline, expectedRealTimeline, expectedRealTimelineAfterRemove);
  }

  @Test
  public void removeMediaItems() throws Exception {
    TimelineWindowDefinition firstWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    TimelineWindowDefinition secondWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    TimelineWindowDefinition thirdWindowDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 3,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ Util.msToUs(10000));
    Timeline timeline1 = new FakeTimeline(firstWindowDefinition);
    Timeline timeline2 = new FakeTimeline(secondWindowDefinition);
    Timeline timeline3 = new FakeTimeline(thirdWindowDefinition);
    MediaSource mediaSource1 = new FakeMediaSource(timeline1);
    MediaSource mediaSource2 = new FakeMediaSource(timeline2);
    MediaSource mediaSource3 = new FakeMediaSource(timeline3);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource1, mediaSource2, mediaSource3)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    Timeline expectedPlaceholderTimeline =
        new FakeTimeline(
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 1),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 2),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 3));
    Timeline expectedRealTimeline =
        new FakeTimeline(firstWindowDefinition, secondWindowDefinition, thirdWindowDefinition);
    Timeline expectedRealTimelineAfterRemove = new FakeTimeline(firstWindowDefinition);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    exoPlayerTestRunner.assertTimelinesSame(
        expectedPlaceholderTimeline, expectedRealTimeline, expectedRealTimelineAfterRemove);
  }

  @Test
  public void clearMediaItems() throws Exception {
    Timeline timeline = new FakeTimeline();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(placeholderTimeline, timeline, Timeline.EMPTY);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */);
  }

  @Test
  public void multipleModificationWithRecursiveListenerInvocations() throws Exception {
    Timeline timeline = new FakeTimeline();
    MediaSource mediaSource = new FakeMediaSource(timeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .clearMediaItems()
            .setMediaSources(secondMediaSource)
            .waitForTimelineChanged()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(mediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertTimelinesSame(
        placeholderTimeline,
        timeline,
        Timeline.EMPTY,
        new FakeMediaSource.InitialTimeline(secondTimeline),
        secondTimeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void modifyPlaylistUnprepared_remainsInIdle_needsPrepareForBuffering() throws Exception {
    int[] playbackStates = new int[4];
    int[] timelineWindowCounts = new int[4];
    int[] maskingPlaybackState = {C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged(
                placeholderTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 0, playbackStates, timelineWindowCounts))
            .clearMediaItems()
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 1, playbackStates, timelineWindowCounts))
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.setMediaSource(new FakeMediaSource(), /* startPositionMs= */ 1000);
                    maskingPlaybackState[0] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 2, playbackStates, timelineWindowCounts))
            .addMediaSources(new FakeMediaSource())
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 3, playbackStates, timelineWindowCounts))
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ 2000)
            .prepare()
            // The first expected buffering state arrives after prepare but not before.
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(new FakeMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE},
        playbackStates);
    assertArrayEquals(new int[] {1, 0, 1, 2}, timelineWindowCounts);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING /* first buffering state after prepare */,
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* initial setMediaSources */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* clear */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* set media items */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* add media items */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source update after prepare */);
    Timeline expectedSecondPlaceholderTimeline =
        new FakeTimeline(
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 0),
            TimelineWindowDefinition.createPlaceholder(/* tag= */ 0));
    Timeline expectedSecondRealTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ 10_000_000));
    exoPlayerTestRunner.assertTimelinesSame(
        placeholderTimeline,
        Timeline.EMPTY,
        placeholderTimeline,
        expectedSecondPlaceholderTimeline,
        expectedSecondRealTimeline);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackState);
  }

  @Test
  public void modifyPlaylistPrepared_remainsInEnded_needsSeekForBuffering() throws Exception {
    Timeline timeline = new FakeTimeline();
    FakeMediaSource secondMediaSource = new FakeMediaSource(timeline);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .addMediaSources(secondMediaSource) // add must not transition to buffering
            .waitForTimelineChanged()
            .clearMediaItems() // clear must remain in ended
            .addMediaSources(secondMediaSource) // add again to be able to test the seek
            .waitForTimelineChanged()
            .seek(/* positionMs= */ 2_000) // seek must transition to buffering
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 2)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, // first buffering
        Player.STATE_READY,
        Player.STATE_ENDED, // clear playlist
        Player.STATE_BUFFERING, // second buffering after seek
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(
        placeholderTimeline,
        timeline,
        Timeline.EMPTY,
        placeholderTimeline,
        timeline,
        Timeline.EMPTY,
        placeholderTimeline,
        timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media items added */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* playlist cleared */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media items added */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void stopWithNoReset_modifyingPlaylistRemainsInIdleState_needsPrepareForBuffering()
      throws Exception {
    Timeline timeline = new FakeTimeline();
    FakeMediaSource secondMediaSource = new FakeMediaSource(timeline);
    int[] playbackStateHolder = new int[3];
    int[] windowCountHolder = new int[3];
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .stop(/* reset= */ false)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 0, playbackStateHolder, windowCountHolder))
            .clearMediaItems()
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 1, playbackStateHolder, windowCountHolder))
            .addMediaSources(secondMediaSource)
            .executeRunnable(
                new PlaybackStateCollector(/* index= */ 2, playbackStateHolder, windowCountHolder))
            .prepare()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE}, playbackStateHolder);
    assertArrayEquals(new int[] {1, 0, 1}, windowCountHolder);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, // first buffering
        Player.STATE_READY,
        Player.STATE_IDLE, // stop
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(
        placeholderTimeline, timeline, Timeline.EMPTY, placeholderTimeline, timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, /* source prepared */
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* clear media items */,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item add (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void prepareWithInvalidInitialSeek_expectEndedImmediately() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .skipSettingMediaSources()
            .initialSeek(/* mediaItemIndex= */ 1, C.TIME_UNSET)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame();
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual();
    assertArrayEquals(new int[] {1}, currentMediaItemIndices);
  }

  @Test
  public void prepareWhenAlreadyPreparedIsANoop() throws Exception {
    Timeline timeline = new FakeTimeline();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG).waitForPlaybackState(Player.STATE_READY).prepare().build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setTimeline(timeline)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);

    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    exoPlayerTestRunner.assertTimelinesSame(placeholderTimeline, timeline);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED /* media item set (masked timeline) */,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE /* source prepared */);
  }

  @Test
  public void seekToIndexLargerThanNumberOfPlaylistItems() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(
            /* isAtomic= */ false,
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    int[] currentMediaItemIndices = new int[1];
    long[] currentPlaybackPositions = new long[1];
    int seekToMediaItemIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatingMediaSource)
        .initialSeek(seekToMediaItemIndex, 5000)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {5_000}, currentPlaybackPositions);
    assertArrayEquals(new int[] {seekToMediaItemIndex}, currentMediaItemIndices);
  }

  @Test
  public void seekToIndexWithEmptyMultiWindowMediaSource() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    int[] currentMediaItemIndices = new int[2];
    long[] currentPlaybackPositions = new long[2];
    long[] windowCounts = new long[2];
    int seekToMediaItemIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .executeRunnable(
                () ->
                    concatenatingMediaSource.addMediaSources(
                        Arrays.asList(
                            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
                            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT))))
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[1] = player.getCurrentPosition();
                    windowCounts[1] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatingMediaSource)
        .initialSeek(seekToMediaItemIndex, 5000)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {0, 2}, windowCounts);
    assertArrayEquals(
        new int[] {seekToMediaItemIndex, seekToMediaItemIndex}, currentMediaItemIndices);
    assertArrayEquals(new long[] {5_000, 5_000}, currentPlaybackPositions);
  }

  @Test
  public void emptyMultiWindowMediaSource_doesNotEnterBufferState() throws Exception {
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG).waitForPlaybackState(Player.STATE_ENDED).build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(concatenatingMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_ENDED);
  }

  @Test
  public void seekToIndexWithEmptyMultiWindowMediaSource_usesLazyPreparation() throws Exception {
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ false, /* durationUs= */ 10_000_000));
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(/* isAtomic= */ false);
    int[] currentMediaItemIndices = new int[2];
    long[] currentPlaybackPositions = new long[2];
    long[] windowCounts = new long[2];
    int seekToMediaItemIndex = 1;
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[0] = player.getCurrentPosition();
                    windowCounts[0] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .executeRunnable(
                () ->
                    concatenatingMediaSource.addMediaSources(
                        Arrays.asList(
                            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT),
                            new FakeMediaSource(fakeTimeline, ExoPlayerTestRunner.VIDEO_FORMAT))))
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPlaybackPositions[1] = player.getCurrentPosition();
                    windowCounts[1] = player.getCurrentTimeline().getWindowCount();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(concatenatingMediaSource)
        .setUseLazyPreparation(/* useLazyPreparation= */ true)
        .initialSeek(seekToMediaItemIndex, 5000)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {0, 2}, windowCounts);
    assertArrayEquals(
        new int[] {seekToMediaItemIndex, seekToMediaItemIndex}, currentMediaItemIndices);
    assertArrayEquals(new long[] {5_000, 5_000}, currentPlaybackPositions);
  }

  @Test
  public void
      timelineUpdateInMultiWindowMediaSource_removingPeriod_withUnpreparedMaskingMediaPeriod_doesNotThrow()
          throws Exception {
    TimelineWindowDefinition window1 =
        new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1);
    TimelineWindowDefinition window2 =
        new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 2);
    FakeMediaSource mediaSource = new FakeMediaSource(/* timeline= */ null);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            // Wait so that the player can create its unprepared MaskingMediaPeriod.
            .waitForPendingPlayerCommands()
            // Let the player assign the unprepared period to window1.
            .executeRunnable(() -> mediaSource.setNewSourceInfo(new FakeTimeline(window1, window2)))
            .waitForTimelineChanged()
            // Remove window1 and assume the update is handled without throwing.
            .executeRunnable(() -> mediaSource.setNewSourceInfo(new FakeTimeline(window2)))
            .waitForTimelineChanged()
            .stop()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    // Assertion is to not throw while running the action schedule above.
  }

  @Test
  public void setPlayWhenReady_keepsCurrentPosition() throws Exception {
    AtomicLong positionAfterSetPlayWhenReady = new AtomicLong(C.TIME_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .playUntilPosition(0, 5000)
            .play()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionAfterSetPlayWhenReady.set(player.getCurrentPosition());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionAfterSetPlayWhenReady.get()).isAtLeast(5000);
  }

  @Test
  public void setPlayWhenReady_correctPositionMasking() throws Exception {
    long[] currentPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .playUntilPosition(0, 5000)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentPositionMs[0] = player.getCurrentPosition();
                    bufferedPositionMs[0] = player.getBufferedPosition();
                    player.setPlayWhenReady(true);
                    currentPositionMs[1] = player.getCurrentPosition();
                    bufferedPositionMs[1] = player.getBufferedPosition();
                    player.setPlayWhenReady(false);
                    currentPositionMs[2] = player.getCurrentPosition();
                    bufferedPositionMs[2] = player.getBufferedPosition();
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(currentPositionMs[0]).isAtLeast(5000);
    assertThat(currentPositionMs[1]).isEqualTo(currentPositionMs[0]);
    assertThat(currentPositionMs[2]).isEqualTo(currentPositionMs[0]);
    assertThat(bufferedPositionMs[0]).isGreaterThan(currentPositionMs[0]);
    assertThat(bufferedPositionMs[1]).isEqualTo(bufferedPositionMs[0]);
    assertThat(bufferedPositionMs[2]).isEqualTo(bufferedPositionMs[0]);
  }

  @Test
  public void setShuffleMode_correctPositionMasking() throws Exception {
    long[] currentPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    long[] bufferedPositionMs = new long[] {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .playUntilPosition(0, 5000)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentPositionMs[0] = player.getCurrentPosition();
                    bufferedPositionMs[0] = player.getBufferedPosition();
                    player.setShuffleModeEnabled(true);
                    currentPositionMs[1] = player.getCurrentPosition();
                    bufferedPositionMs[1] = player.getBufferedPosition();
                    player.setShuffleModeEnabled(false);
                    currentPositionMs[2] = player.getCurrentPosition();
                    bufferedPositionMs[2] = player.getBufferedPosition();
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(currentPositionMs[0]).isAtLeast(5000);
    assertThat(currentPositionMs[1]).isEqualTo(currentPositionMs[0]);
    assertThat(currentPositionMs[2]).isEqualTo(currentPositionMs[0]);
    assertThat(bufferedPositionMs[0]).isGreaterThan(currentPositionMs[0]);
    assertThat(bufferedPositionMs[1]).isEqualTo(bufferedPositionMs[0]);
    assertThat(bufferedPositionMs[2]).isEqualTo(bufferedPositionMs[0]);
  }

  @Test
  public void setShuffleOrder_keepsCurrentPosition() throws Exception {
    AtomicLong positionAfterSetShuffleOrder = new AtomicLong(C.TIME_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .playUntilPosition(0, 5000)
            .setShuffleOrder(new FakeShuffleOrder(/* length= */ 1))
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    positionAfterSetShuffleOrder.set(player.getCurrentPosition());
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(positionAfterSetShuffleOrder.get()).isAtLeast(5000);
  }

  @Test
  public void setShuffleOrder_notifiesTimelineChanged() throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    // No callback expected for this call, because the (empty) timeline doesn't change. We start
    // with a deterministic shuffle order, to ensure when we call setShuffleOrder again below the
    // order is definitely different (otherwise the test is flaky when the existing shuffle order
    // matches the shuffle order passed in below).
    player.setShuffleOrder(new FakeShuffleOrder(0));
    player.setMediaSources(
        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource(), new FakeMediaSource()));
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.prepare();
    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 5000);
    player.play();
    ShuffleOrder.DefaultShuffleOrder newShuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(player.getMediaItemCount(), /* randomSeed= */ 5);
    player.setShuffleOrder(newShuffleOrder);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    ArgumentCaptor<Timeline> timelineCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(mockListener)
        .onTimelineChanged(
            timelineCaptor.capture(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));

    Timeline capturedTimeline = Iterables.getOnlyElement(timelineCaptor.getAllValues());
    List<Integer> newShuffleOrderIndexes = new ArrayList<>(newShuffleOrder.getLength());
    for (int i = newShuffleOrder.getFirstIndex();
        i != C.INDEX_UNSET;
        i = newShuffleOrder.getNextIndex(i)) {
      newShuffleOrderIndexes.add(i);
    }
    List<Integer> capturedTimelineShuffleIndexes = new ArrayList<>(newShuffleOrder.getLength());
    for (int i = capturedTimeline.getFirstWindowIndex(/* shuffleModeEnabled= */ true);
        i != C.INDEX_UNSET;
        i =
            capturedTimeline.getNextWindowIndex(
                i, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true)) {
      capturedTimelineShuffleIndexes.add(i);
    }
    assertThat(capturedTimelineShuffleIndexes).isEqualTo(newShuffleOrderIndexes);
  }

  @Test
  public void setMediaSources_empty_whenEmpty_correctMaskingMediaItemIndex() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    List<MediaSource> listOfTwo =
                        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource());
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 0, 0}, currentMediaItemIndices);
  }

  @Test
  public void setMediaItems_resetPosition_resetsPosition() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] currentPositions = {C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 1000);
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    List<MediaItem> listOfTwo =
                        ImmutableList.of(
                            MediaItem.fromUri(Uri.EMPTY), MediaItem.fromUri(Uri.EMPTY));
                    player.setMediaItems(listOfTwo, /* resetPosition= */ true);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPositions[1] = player.getCurrentPosition();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0}, currentMediaItemIndices);
    assertArrayEquals(new long[] {1000, 0}, currentPositions);
  }

  @Test
  public void setMediaSources_empty_whenEmpty_validInitialSeek_correctMaskingMediaItemIndex()
      throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    List<MediaSource> listOfTwo =
                        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource());
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, C.TIME_UNSET)
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 1}, currentMediaItemIndices);
  }

  @Test
  public void setMediaSources_empty_whenEmpty_invalidInitialSeek_correctMaskingMediaItemIndex()
      throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    List<MediaSource> listOfTwo =
                        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource());
                    player.addMediaSources(/* index= */ 0, listOfTwo);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 4, C.TIME_UNSET)
        .setMediaSources(new ConcatenatingMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {4, 0, 0}, currentMediaItemIndices);
  }

  @Test
  public void setMediaSources_whenEmpty_correctMaskingMediaItemIndex() throws Exception {
    final int[] currentMediaItemIndices = {
      C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET
    };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Increase current media item index.
                    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Current media item index is unchanged.
                    player.addMediaSource(/* index= */ 2, new FakeMediaSource());
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    MediaSource mediaSource = new FakeMediaSource();
                    ConcatenatingMediaSource concatenatingMediaSource =
                        new ConcatenatingMediaSource(mediaSource, mediaSource, mediaSource);
                    // Increase current media item with multi media item source.
                    player.addMediaSource(/* index= */ 0, concatenatingMediaSource);
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    ConcatenatingMediaSource concatenatingMediaSource =
                        new ConcatenatingMediaSource();
                    // Current media item index is unchanged when adding empty source.
                    player.addMediaSource(/* index= */ 0, concatenatingMediaSource);
                    currentMediaItemIndices[3] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 4, 4}, currentMediaItemIndices);
  }

  @Test
  public void setMediaSources_whenEmpty_validInitialSeek_correctMasking() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 2);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, new Object());
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] currentPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    // Increase current media item index.
                    player.addMediaSource(/* index= */ 0, secondMediaSource);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPositions[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                    currentPositions[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, 2000)
        .setMediaSources(firstMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 2, 2}, currentMediaItemIndices);
    assertArrayEquals(new long[] {2000, 2000, 2000}, currentPositions);
    assertArrayEquals(new long[] {2000, 2000, 2000}, bufferedPositions);
  }

  @Test
  public void setMediaSources_whenEmpty_invalidInitialSeek_correctMasking() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] currentPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPositions = {C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    // Increase current media item index.
                    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPositions[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                    currentPositions[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, 2000)
        .setMediaSources(new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentMediaItemIndices);
    assertArrayEquals(new long[] {0, 0, 0}, currentPositions);
    assertArrayEquals(new long[] {0, 0, 0}, bufferedPositions);
  }

  @Test
  public void setMediaSources_correctMaskingMediaItemIndex() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    // Increase current media item index.
                    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentMediaItemIndices);
  }

  @Test
  public void setMediaSources_whenIdle_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(new FakeMediaSource());
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(
                        new FakeMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .prepare()
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_IDLE},
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void setMediaSources_whenIdle_invalidSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set a media item with an implicit seek to the current position which is
                    // invalid in the new timeline.
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1, 1L)));
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenIdle_noSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with no seek.
                    player.setMediaSource(new FakeMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenIdle_noSeekEmpty_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set an empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .setMediaSources(new FakeMediaSource())
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .skipSettingMediaSources()
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_IDLE}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenEnded_correctMaskingPlaybackState() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with an implicit seek to the current position.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(firstMediaSource);
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(secondMediaSource, /* startPositionMs= */ C.TIME_UNSET);
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 3)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_ENDED,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED,
        Player.STATE_BUFFERING,
        Player.STATE_READY,
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, Player.STATE_ENDED, Player.STATE_BUFFERING, Player.STATE_BUFFERING
        },
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenEnded_invalidSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an invalid implicit seek to the current position.
                    player.setMediaSource(
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 1, 1L)),
                        /* resetPosition= */ false);
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenEnded_noSeek_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with no seek (keep current position).
                    player.setMediaSource(new FakeMediaSource(), /* resetPosition= */ false);
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void setMediaSources_whenEnded_noSeekEmpty_correctMaskingPlaybackState() throws Exception {
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .clearMediaItems()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set an empty media item with no seek.
                    player.setMediaSource(new ConcatenatingMediaSource());
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void setMediaSources_whenPrepared_correctMaskingPlaybackState() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with an implicit seek to current position.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* resetPosition= */ false);
                    // Expect masking state is ended,
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .setMediaSources(
                /* mediaItemIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set empty media item with an explicit seek.
                    player.setMediaSource(
                        new ConcatenatingMediaSource(), /* startPositionMs= */ C.TIME_UNSET);
                    // Expect masking state is ended,
                    maskingPlaybackStates[1] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .setMediaSources(
                /* mediaItemIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an explicit seek.
                    player.setMediaSource(secondMediaSource, /* startPositionMs= */ C.TIME_UNSET);
                    // Expect masking state is buffering,
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .setMediaSources(
                /* mediaItemIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Set media item with an implicit seek to the current position.
                    player.setMediaSource(secondMediaSource, /* resetPosition= */ false);
                    // Expect masking state is buffering,
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .play()
            .waitForPlaybackState(Player.STATE_READY)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 3)
            .setMediaSources(firstMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after initial prepare.
        Player.STATE_ENDED, // Ended after setting empty source without seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_ENDED, // Ended after setting empty source with seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after setting media item with seek.
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready again after re-setting source.
        Player.STATE_BUFFERING, // Play.
        Player.STATE_READY, // Ready after setting media item without seek.
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, Player.STATE_ENDED, Player.STATE_BUFFERING, Player.STATE_BUFFERING
        },
        maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Initial source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // Empty source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, // Empty source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Set source with seek.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE, // Reset source.
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE); // Set source without seek.
  }

  @Test
  public void setMediaSources_whenPrepared_invalidSeek_correctMaskingPlaybackState()
      throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    final int[] maskingPlaybackStates = new int[1];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // An implicit, invalid seek picking up the position set by the initial seek.
                    player.setMediaSource(firstMediaSource, /* resetPosition= */ false);
                    // Expect masking state is ended,
                    maskingPlaybackStates[0] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .setMediaSources(
                /* mediaItemIndex= */ 0, /* positionMs= */ C.TIME_UNSET, firstMediaSource)
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setExpectedPlayerEndedCount(/* expectedPlayerEndedCount= */ 2)
            .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .setMediaSources(new ConcatenatingMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_ENDED, // Empty source has been prepared.
        Player.STATE_BUFFERING, // After setting another source.
        Player.STATE_READY,
        Player.STATE_ENDED);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackStates);
    exoPlayerTestRunner.assertTimelineChangeReasonsEqual(
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
        Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
        Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
  }

  @Test
  public void addMediaSources_whenEmptyInitialSeek_correctPeriodMasking() throws Exception {
    final long[] positions = new long[2];
    Arrays.fill(positions, C.TIME_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
                    positions[0] = player.getCurrentPosition();
                    positions[1] = player.getBufferedPosition();
                  }
                })
            .prepare()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .skipSettingMediaSources()
        .initialSeek(/* mediaItemIndex= */ 0, /* positionMs= */ 2000)
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new long[] {2000, 2000}, positions);
  }

  @Test
  public void addMediaSources_skipSettingMediaItems_validInitialSeek_correctMasking()
      throws Exception {
    final int[] currentMediaItemIndices = new int[5];
    Arrays.fill(currentMediaItemIndices, C.INDEX_UNSET);
    final long[] currentPositions = new long[3];
    Arrays.fill(currentPositions, C.TIME_UNSET);
    final long[] bufferedPositions = new long[3];
    Arrays.fill(bufferedPositions, C.TIME_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    // If the timeline is empty masking variables are used.
                    currentPositions[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    player.addMediaSource(/* index= */ 0, new ConcatenatingMediaSource());
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    player.addMediaSource(
                        /* index= */ 0,
                        new FakeMediaSource(new FakeTimeline(/* windowCount= */ 2)));
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
                    currentMediaItemIndices[3] = player.getCurrentMediaItemIndex();
                    // With a non-empty timeline, we mask the periodId in the playback info.
                    currentPositions[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .prepare()
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[4] = player.getCurrentMediaItemIndex();
                    // Finally original playbackInfo coming from EPII is used.
                    currentPositions[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .skipSettingMediaSources()
        .initialSeek(/* mediaItemIndex= */ 1, 2000)
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1, 1, 2, 2}, currentMediaItemIndices);
    assertThat(currentPositions[0]).isEqualTo(2000);
    assertThat(currentPositions[1]).isEqualTo(2000);
    assertThat(currentPositions[2]).isAtLeast(2000);
    assertThat(bufferedPositions[0]).isEqualTo(2000);
    assertThat(bufferedPositions[1]).isEqualTo(2000);
    assertThat(bufferedPositions[2]).isAtLeast(2000);
  }

  @Test
  public void
      testAddMediaSources_skipSettingMediaItems_invalidInitialSeek_correctMaskingMediaItemIndex()
          throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    player.addMediaSource(new FakeMediaSource());
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .skipSettingMediaSources()
        .initialSeek(/* mediaItemIndex= */ 1, C.TIME_UNSET)
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0, 0}, currentMediaItemIndices);
  }

  @Test
  public void moveMediaItems_correctMaskingMediaItemIndex() throws Exception {
    Timeline timeline = new FakeTimeline();
    MediaSource firstMediaSource = new FakeMediaSource(timeline);
    MediaSource secondMediaSource = new FakeMediaSource(timeline);
    MediaSource thirdMediaSource = new FakeMediaSource(timeline);
    final int[] currentMediaItemIndices = {
      C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET
    };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move the current item down in the playlist.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move the current item up in the playlist.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 0);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .seek(/* mediaItemIndex= */ 2, C.TIME_UNSET)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move items from before to behind the current item.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move items from behind to before the current item.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, /* newIndex= */ 0);
                    currentMediaItemIndices[3] = player.getCurrentMediaItemIndex();
                  }
                })
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move items from before to before the current item.
                    // No change in currentMediaItemIndex.
                    player.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1, /* newIndex= */ 1);
                    currentMediaItemIndices[4] = player.getCurrentMediaItemIndex();
                  }
                })
            .seek(/* mediaItemIndex= */ 0, C.TIME_UNSET)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Move items from behind to behind the current item.
                    // No change in currentMediaItemIndex.
                    player.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 2, /* newIndex= */ 2);
                    currentMediaItemIndices[5] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(firstMediaSource, secondMediaSource, thirdMediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0, 0, 2, 2, 0}, currentMediaItemIndices);
  }

  @Test
  public void moveMediaItems_unprepared_correctMaskingMediaItemIndex() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Increase current media item index.
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    player.moveMediaItem(/* currentIndex= */ 0, /* newIndex= */ 1);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .prepare()
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(new FakeMediaSource(), new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {0, 1, 1}, currentMediaItemIndices);
  }

  @Test
  public void removeMediaItems_correctMaskingMediaItemIndex() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Decrease current media item index.
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    player.removeMediaItem(/* index= */ 0);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(new FakeMediaSource(), new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0}, currentMediaItemIndices);
  }

  @Test
  public void removeMediaItems_currentItemRemoved_correctMasking() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final long[] currentPositions = {C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPositions = {C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Remove the current item.
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    player.removeMediaItem(/* index= */ 1);
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPositions[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ 5000)
        .setMediaSources(new FakeMediaSource(), new FakeMediaSource(), new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 1}, currentMediaItemIndices);
    assertThat(currentPositions[0]).isAtLeast(5000L);
    assertThat(bufferedPositions[0]).isAtLeast(5000L);
    assertThat(currentPositions[1]).isEqualTo(0);
    assertThat(bufferedPositions[1]).isAtLeast(0);
  }

  @Test
  public void removeMediaItems_currentItemRemovedThatIsTheLast_correctMasking() throws Exception {
    Timeline firstTimeline = new FakeTimeline(/* windowCount= */ 1, 1L);
    MediaSource firstMediaSource = new FakeMediaSource(firstTimeline);
    Timeline secondTimeline = new FakeTimeline(/* windowCount= */ 1, 2L);
    MediaSource secondMediaSource = new FakeMediaSource(secondTimeline);
    Timeline thirdTimeline = new FakeTimeline(/* windowCount= */ 1, 3L);
    MediaSource thirdMediaSource = new FakeMediaSource(thirdTimeline);
    Timeline fourthTimeline = new FakeTimeline(/* windowCount= */ 1, 3L);
    MediaSource fourthMediaSource = new FakeMediaSource(fourthTimeline);
    final int[] currentMediaItemIndices = new int[9];
    Arrays.fill(currentMediaItemIndices, C.INDEX_UNSET);
    final int[] maskingPlaybackStates = new int[4];
    Arrays.fill(maskingPlaybackStates, C.INDEX_UNSET);
    final long[] currentPositions = new long[3];
    Arrays.fill(currentPositions, C.TIME_UNSET);
    final long[] bufferedPositions = new long[3];
    Arrays.fill(bufferedPositions, C.TIME_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Expect the current media item index to be 2 after seek.
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPositions[0] = player.getCurrentPosition();
                    bufferedPositions[0] = player.getBufferedPosition();
                    player.removeMediaItem(/* index= */ 2);
                    // Expect the current media item index to be 0
                    // (default position of timeline after not finding subsequent period).
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    // Transition to ENDED.
                    maskingPlaybackStates[0] = player.getPlaybackState();
                    currentPositions[1] = player.getCurrentPosition();
                    bufferedPositions[1] = player.getBufferedPosition();
                  }
                })
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Expects the current media item index still on 0.
                    currentMediaItemIndices[2] = player.getCurrentMediaItemIndex();
                    // Insert an item at begin when the playlist is not empty.
                    player.addMediaSource(/* index= */ 0, thirdMediaSource);
                    // Expects the current media item index to be (0 + 1) after insertion at begin.
                    currentMediaItemIndices[3] = player.getCurrentMediaItemIndex();
                    // Remains in ENDED.
                    maskingPlaybackStates[1] = player.getPlaybackState();
                    currentPositions[2] = player.getCurrentPosition();
                    bufferedPositions[2] = player.getBufferedPosition();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[4] = player.getCurrentMediaItemIndex();
                    // Implicit seek to the current media item index, which is out of bounds in new
                    // timeline.
                    player.setMediaSource(fourthMediaSource, /* resetPosition= */ false);
                    // 0 after reset.
                    currentMediaItemIndices[5] = player.getCurrentMediaItemIndex();
                    // Invalid seek, so we remain in ENDED.
                    maskingPlaybackStates[2] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[6] = player.getCurrentMediaItemIndex();
                    // Explicit seek to (0, C.TIME_UNSET). Player transitions to BUFFERING.
                    player.setMediaSource(fourthMediaSource, /* startPositionMs= */ 5000);
                    // 0 after explicit seek.
                    currentMediaItemIndices[7] = player.getCurrentMediaItemIndex();
                    // Transitions from ENDED to BUFFERING after explicit seek.
                    maskingPlaybackStates[3] = player.getPlaybackState();
                  }
                })
            .waitForTimelineChanged()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Check whether actual media item index is equal masking index from above.
                    currentMediaItemIndices[8] = player.getCurrentMediaItemIndex();
                  }
                })
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .initialSeek(/* mediaItemIndex= */ 2, /* positionMs= */ C.TIME_UNSET)
            .setExpectedPlayerEndedCount(2)
            .setMediaSources(firstMediaSource, secondMediaSource, thirdMediaSource)
            .setActionSchedule(actionSchedule)
            .build()
            .start()
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    // Expect reset of masking to first media item.
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING,
        Player.STATE_READY, // Ready after initial prepare.
        Player.STATE_ENDED, // ended after removing current media item index
        Player.STATE_BUFFERING, // buffers after set items with seek
        Player.STATE_READY,
        Player.STATE_ENDED);
    assertArrayEquals(
        new int[] {
          Player.STATE_ENDED, // ended after removing current media item index
          Player.STATE_ENDED, // adding items does not change state
          Player.STATE_ENDED, // set items with seek to current position.
          Player.STATE_BUFFERING
        }, // buffers after set items with seek
        maskingPlaybackStates);
    assertArrayEquals(new int[] {2, 0, 0, 1, 1, 0, 0, 0, 0}, currentMediaItemIndices);
    assertThat(currentPositions[0]).isEqualTo(0);
    assertThat(currentPositions[1]).isEqualTo(0);
    assertThat(currentPositions[2]).isEqualTo(0);
    assertThat(bufferedPositions[0]).isGreaterThan(0);
    assertThat(bufferedPositions[1]).isEqualTo(0);
    assertThat(bufferedPositions[2]).isEqualTo(0);
  }

  @Test
  public void removeMediaItems_removeTailWithCurrentWindow_whenIdle_finishesPlayback()
      throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .seek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
            .waitForPendingPlayerCommands()
            .removeMediaItem(/* index= */ 1)
            .prepare()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    ExoPlayerTestRunner exoPlayerTestRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(new FakeMediaSource(), new FakeMediaSource())
            .setActionSchedule(actionSchedule)
            .build()
            .start(/* doPrepare= */ false)
            .blockUntilActionScheduleFinished(TIMEOUT_MS)
            .blockUntilEnded(TIMEOUT_MS);
    exoPlayerTestRunner.assertPlaybackStatesEqual(
        Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED);
  }

  @Test
  public void clearMediaItems_correctMasking() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final int[] maskingPlaybackState = {C.INDEX_UNSET};
    final long[] currentPosition = {C.TIME_UNSET, C.TIME_UNSET};
    final long[] bufferedPosition = {C.TIME_UNSET, C.TIME_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .waitForPlaybackState(Player.STATE_BUFFERING)
            .playUntilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 150)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentPosition[0] = player.getCurrentPosition();
                    bufferedPosition[0] = player.getBufferedPosition();
                    player.clearMediaItems();
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentPosition[1] = player.getCurrentPosition();
                    bufferedPosition[1] = player.getBufferedPosition();
                    maskingPlaybackState[0] = player.getPlaybackState();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(new FakeMediaSource(), new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(new int[] {1, 0}, currentMediaItemIndices);
    assertThat(currentPosition[0]).isAtLeast(150);
    assertThat(currentPosition[1]).isEqualTo(0);
    assertThat(bufferedPosition[0]).isAtLeast(150);
    assertThat(bufferedPosition[1]).isEqualTo(0);
    assertArrayEquals(new int[] {1, 0}, currentMediaItemIndices);
    assertArrayEquals(new int[] {Player.STATE_ENDED}, maskingPlaybackState);
  }

  @Test
  public void clearMediaItems_unprepared_correctMaskingMediaItemIndex_notEnded() throws Exception {
    final int[] currentMediaItemIndices = {C.INDEX_UNSET, C.INDEX_UNSET};
    final int[] currentStates = {C.INDEX_UNSET, C.INDEX_UNSET, C.INDEX_UNSET};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            // Wait for initial seek to be fully handled by internal player.
            .waitForPositionDiscontinuity()
            .waitForPendingPlayerCommands()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    currentMediaItemIndices[0] = player.getCurrentMediaItemIndex();
                    currentStates[0] = player.getPlaybackState();
                    player.clearMediaItems();
                    currentMediaItemIndices[1] = player.getCurrentMediaItemIndex();
                    currentStates[1] = player.getPlaybackState();
                  }
                })
            .prepare()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    // Transitions to ended when prepared with zero media items.
                    currentStates[2] = player.getPlaybackState();
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .initialSeek(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET)
        .setMediaSources(new FakeMediaSource(), new FakeMediaSource())
        .setActionSchedule(actionSchedule)
        .build()
        .start(/* doPrepare= */ false)
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);
    assertArrayEquals(
        new int[] {Player.STATE_IDLE, Player.STATE_IDLE, Player.STATE_ENDED}, currentStates);
    assertArrayEquals(new int[] {1, 0}, currentMediaItemIndices);
  }

  @Test
  public void errorThrownDuringPlaylistUpdate_keepsConsistentPlayerState() {
    FakeMediaSource source1 =
        new FakeMediaSource(
            new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT);
    FakeMediaSource source2 =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT);
    AtomicInteger audioRendererEnableCount = new AtomicInteger(0);
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer =
        new FakeRenderer(C.TRACK_TYPE_AUDIO) {
          @Override
          protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
              throws ExoPlaybackException {
            if (audioRendererEnableCount.incrementAndGet() == 2) {
              // Fail when enabling the renderer for the second time during the playlist update.
              throw createRendererException(
                  new IllegalStateException(),
                  ExoPlayerTestRunner.AUDIO_FORMAT,
                  PlaybackException.ERROR_CODE_UNSPECIFIED);
            }
          }
        };
    AtomicReference<Timeline> timelineAfterError = new AtomicReference<>();
    AtomicReference<Tracks> trackInfosAfterError = new AtomicReference<>();
    AtomicInteger mediaItemIndexAfterError = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.addAnalyticsListener(
                        new AnalyticsListener() {
                          @Override
                          public void onPlayerError(EventTime eventTime, PlaybackException error) {
                            timelineAfterError.set(player.getCurrentTimeline());
                            trackInfosAfterError.set(player.getCurrentTracks());
                            mediaItemIndexAfterError.set(player.getCurrentMediaItemIndex());
                          }
                        });
                  }
                })
            .pause()
            // Wait until fully buffered so that the new renderer can be enabled immediately.
            .waitForIsLoading(true)
            .waitForIsLoading(false)
            .removeMediaItem(0)
            .build();
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(source1, source2)
            .setActionSchedule(actionSchedule)
            .setRenderers(videoRenderer, audioRenderer)
            .build();

    assertThrows(
        ExoPlaybackException.class,
        () ->
            testRunner
                .start(/* doPrepare= */ true)
                .blockUntilActionScheduleFinished(TIMEOUT_MS)
                .blockUntilEnded(TIMEOUT_MS));

    assertThat(timelineAfterError.get().getWindowCount()).isEqualTo(1);
    assertThat(mediaItemIndexAfterError.get()).isEqualTo(0);
    assertThat(trackInfosAfterError.get().getGroups()).hasSize(1);
    assertThat(trackInfosAfterError.get().getGroups().get(0).getTrackFormat(0))
        .isEqualTo(ExoPlayerTestRunner.AUDIO_FORMAT);
    assertThat(trackInfosAfterError.get().isTypeSelected(C.TRACK_TYPE_VIDEO)).isFalse();
    assertThat(trackInfosAfterError.get().isTypeSelected(C.TRACK_TYPE_AUDIO)).isTrue();
  }

  @Test
  public void seekToCurrentPosition_inEndedState_switchesToBufferingStateAndContinuesPlayback()
      throws Exception {
    MediaSource mediaSource = new FakeMediaSource(new FakeTimeline(/* windowCount = */ 1));
    AtomicInteger mediaItemIndexAfterFinalEndedState = new AtomicInteger();
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_ENDED)
            .addMediaSources(mediaSource)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.seekTo(player.getCurrentPosition());
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .waitForPlaybackState(Player.STATE_ENDED)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    mediaItemIndexAfterFinalEndedState.set(player.getCurrentMediaItemIndex());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(mediaItemIndexAfterFinalEndedState.get()).isEqualTo(1);
  }

  @Test
  public void pauseAtEndOfMediaItems_pausesPlaybackBeforeTransitioningToTheNextItem()
      throws Exception {
    TimelineWindowDefinition timelineWindowDefinition =
        new TimelineWindowDefinition(
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ 10 * C.MICROS_PER_SECOND);
    MediaSource mediaSource = new FakeMediaSource(new FakeTimeline(timelineWindowDefinition));
    AtomicInteger playbackStateAfterPause = new AtomicInteger(C.INDEX_UNSET);
    AtomicLong positionAfterPause = new AtomicLong(C.TIME_UNSET);
    AtomicInteger mediaItemIndexAfterPause = new AtomicInteger(C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlayWhenReady(true)
            .waitForPlayWhenReady(false)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playbackStateAfterPause.set(player.getPlaybackState());
                    mediaItemIndexAfterPause.set(player.getCurrentMediaItemIndex());
                    positionAfterPause.set(player.getContentPosition());
                  }
                })
            .play()
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setPauseAtEndOfMediaItems(true)
        .setMediaSources(mediaSource, mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(playbackStateAfterPause.get()).isEqualTo(Player.STATE_READY);
    assertThat(mediaItemIndexAfterPause.get()).isEqualTo(0);
    assertThat(positionAfterPause.get()).isEqualTo(10_000);
  }

  @Test
  public void pauseAtEndOfMediaItems_pausesPlaybackWhenEnded() throws Exception {
    TimelineWindowDefinition timelineWindowDefinition =
        new TimelineWindowDefinition(
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* durationUs= */ 10 * C.MICROS_PER_SECOND);
    MediaSource mediaSource = new FakeMediaSource(new FakeTimeline(timelineWindowDefinition));
    AtomicInteger playbackStateAfterPause = new AtomicInteger(C.INDEX_UNSET);
    AtomicLong positionAfterPause = new AtomicLong(C.TIME_UNSET);
    AtomicInteger mediaItemIndexAfterPause = new AtomicInteger(C.INDEX_UNSET);
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlayWhenReady(true)
            .waitForPlayWhenReady(false)
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playbackStateAfterPause.set(player.getPlaybackState());
                    mediaItemIndexAfterPause.set(player.getCurrentMediaItemIndex());
                    positionAfterPause.set(player.getContentPosition());
                  }
                })
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setPauseAtEndOfMediaItems(true)
        .setMediaSources(mediaSource)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(playbackStateAfterPause.get()).isEqualTo(Player.STATE_ENDED);
    assertThat(mediaItemIndexAfterPause.get()).isEqualTo(0);
    assertThat(positionAfterPause.get()).isEqualTo(10_000);
  }

  @Test
  public void
      infiniteLoading_withSmallAllocations_oomIsPreventedByLoadControl_andThrowsStuckBufferingIllegalStateException() {
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setTargetBufferBytes(10 * C.DEFAULT_BUFFER_SEGMENT_SIZE)
            .build();
    // Return no end of stream signal to prevent playback from ending.
    FakeMediaPeriod.TrackDataFactory trackDataWithoutEos = (format, periodId) -> ImmutableList.of();
    MediaSource continuouslyAllocatingMediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                trackDataWithoutEos,
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                /* deferOnPrepared= */ false) {

              private final List<Allocation> allocations = new ArrayList<>();

              private Callback callback;

              @Override
              public synchronized void prepare(Callback callback, long positionUs) {
                this.callback = callback;
                super.prepare(callback, positionUs);
              }

              @Override
              public long getBufferedPositionUs() {
                // Pretend not to make loading progress, so that continueLoading keeps being called.
                return 0;
              }

              @Override
              public long getNextLoadPositionUs() {
                // Pretend not to make loading progress, so that continueLoading keeps being called.
                return 0;
              }

              @Override
              public boolean continueLoading(long positionUs) {
                allocations.add(allocator.allocate());
                callback.onContinueLoadingRequested(this);
                return true;
              }
            };
          }
        };
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(continuouslyAllocatingMediaSource)
            .setLoadControl(loadControl)
            .build();

    ExoPlaybackException exception =
        assertThrows(
            ExoPlaybackException.class, () -> testRunner.start().blockUntilEnded(TIMEOUT_MS));
    assertThat(exception.type).isEqualTo(ExoPlaybackException.TYPE_UNEXPECTED);
    assertThat(exception.getUnexpectedException()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void loading_withLargeAllocationCausingOom_playsRemainingMediaAndThenThrows() {
    Loader.Loadable loadable =
        new Loader.Loadable() {
          @SuppressWarnings("UnusedVariable")
          @Override
          public void load() throws IOException {
            @SuppressWarnings("unused") // This test needs the allocation to cause an OOM.
            byte[] largeBuffer = new byte[Integer.MAX_VALUE];
          }

          @Override
          public void cancelLoad() {}
        };
    // Create 3 samples without end of stream signal to test that all 3 samples are
    // still played before the sample stream exception is thrown.
    FakeSampleStreamItem sample =
        oneByteSample(
            TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            C.BUFFER_FLAG_KEY_FRAME);
    FakeMediaPeriod.TrackDataFactory threeSamplesWithoutEos =
        (format, mediaPeriodId) -> ImmutableList.of(sample, sample, sample);
    MediaSource largeBufferAllocatingMediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                threeSamplesWithoutEos,
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                /* deferOnPrepared= */ false) {
              private final Loader loader = new Loader("ExoPlayerTest");

              @Override
              public boolean continueLoading(long positionUs) {
                super.continueLoading(positionUs);
                if (!loader.isLoading()) {
                  loader.startLoading(
                      loadable, new FakeLoaderCallback(), /* defaultMinRetryCount= */ 1);
                }
                return true;
              }

              @Override
              protected FakeSampleStream createSampleStream(
                  Allocator allocator,
                  @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
                  DrmSessionManager drmSessionManager,
                  DrmSessionEventListener.EventDispatcher drmEventDispatcher,
                  Format initialFormat,
                  List<FakeSampleStreamItem> fakeSampleStreamItems) {
                return new FakeSampleStream(
                    allocator,
                    mediaSourceEventDispatcher,
                    drmSessionManager,
                    drmEventDispatcher,
                    initialFormat,
                    fakeSampleStreamItems) {
                  @Override
                  public void maybeThrowError() throws IOException {
                    loader.maybeThrowError();
                  }
                };
              }
            };
          }
        };
    FakeRenderer renderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    ExoPlayerTestRunner testRunner =
        new ExoPlayerTestRunner.Builder(context)
            .setMediaSources(largeBufferAllocatingMediaSource)
            .setRenderers(renderer)
            .build();

    ExoPlaybackException exception =
        assertThrows(
            ExoPlaybackException.class, () -> testRunner.start().blockUntilEnded(TIMEOUT_MS));
    assertThat(exception.type).isEqualTo(ExoPlaybackException.TYPE_SOURCE);
    assertThat(exception.getSourceException()).isInstanceOf(Loader.UnexpectedLoaderException.class);
    assertThat(exception.getSourceException().getCause()).isInstanceOf(OutOfMemoryError.class);

    assertThat(renderer.sampleBufferReadCount).isEqualTo(3);
  }

  @Test
  public void seekTo_whileReady_callsOnIsPlayingChanged() throws Exception {
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* positionMs= */ 0)
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    List<Boolean> onIsPlayingChanges = new ArrayList<>();
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            onIsPlayingChanges.add(isPlaying);
          }
        };
    new ExoPlayerTestRunner.Builder(context)
        .setPlayerListener(playerListener)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(onIsPlayingChanges).containsExactly(true, false, true, false).inOrder();
  }

  @Test
  public void multipleListenersAndMultipleCallbacks_callbacksAreOrderedByType() throws Exception {
    String playWhenReadyChange1 = "playWhenReadyChange1";
    String playWhenReadyChange2 = "playWhenReadyChange2";
    String isPlayingChange1 = "isPlayingChange1";
    String isPlayingChange2 = "isPlayingChange2";
    ArrayList<String> events = new ArrayList<>();
    Player.Listener playerListener1 =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            events.add(playWhenReadyChange1);
          }

          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            events.add(isPlayingChange1);
          }
        };
    Player.Listener playerListener2 =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            events.add(playWhenReadyChange2);
          }

          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            events.add(isPlayingChange2);
          }
        };
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    player.addListener(playerListener1);
                    player.addListener(playerListener2);
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .play()
            .waitForPlaybackState(Player.STATE_ENDED)
            .build();
    new ExoPlayerTestRunner.Builder(context)
        .setActionSchedule(actionSchedule)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(events)
        .containsExactly(
            playWhenReadyChange1,
            playWhenReadyChange2,
            isPlayingChange1,
            isPlayingChange2,
            isPlayingChange1,
            isPlayingChange2)
        .inOrder();
  }

  /**
   * This tests that renderer offsets and buffer times in the renderer are set correctly even when
   * the sources have a window-to-period offset and a non-zero default start position. The start
   * offset of the first source is also updated during preparation to make sure the player adapts
   * everything accordingly.
   */
  @Test
  public void
      playlistWithMediaWithStartOffsets_andStartOffsetChangesDuringPreparation_appliesCorrectRenderingOffsetToAllPeriods()
          throws Exception {
    List<Long> rendererStreamOffsetsUs = new ArrayList<>();
    List<Long> firstBufferTimesUsWithOffset = new ArrayList<>();
    FakeRenderer renderer =
        new FakeRenderer(C.TRACK_TYPE_VIDEO) {
          boolean pendingFirstBufferTime = false;

          @Override
          protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
            rendererStreamOffsetsUs.add(offsetUs);
            pendingFirstBufferTime = true;
          }

          @Override
          protected boolean shouldProcessBuffer(long bufferTimeUs, long playbackPositionUs) {
            if (pendingFirstBufferTime) {
              firstBufferTimesUsWithOffset.add(bufferTimeUs);
              pendingFirstBufferTime = false;
            }
            return super.shouldProcessBuffer(bufferTimeUs, playbackPositionUs);
          }
        };
    Timeline timelineWithOffsets =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ new Object(),
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US,
                /* defaultPositionUs= */ 4_567_890,
                /* windowOffsetInFirstPeriodUs= */ 1_234_567,
                AdPlaybackState.NONE));
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(renderer).build();
    long firstSampleTimeUs = 4_567_890 + 1_234_567;
    FakeMediaSource firstMediaSource =
        new FakeMediaSource(
            /* timeline= */ null,
            DrmSessionManager.DRM_UNSUPPORTED,
            (unusedFormat, unusedMediaPeriodId) ->
                ImmutableList.of(
                    oneByteSample(firstSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM),
            ExoPlayerTestRunner.VIDEO_FORMAT);
    FakeMediaSource secondMediaSource =
        new FakeMediaSource(
            timelineWithOffsets,
            DrmSessionManager.DRM_UNSUPPORTED,
            (unusedFormat, unusedMediaPeriodId) ->
                ImmutableList.of(
                    oneByteSample(firstSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM),
            ExoPlayerTestRunner.VIDEO_FORMAT);
    player.setMediaSources(ImmutableList.of(firstMediaSource, secondMediaSource));

    // Start playback and wait until player is idly waiting for an update of the first source.
    player.prepare();
    player.play();
    runUntilPendingCommandsAreFullyHandled(player);
    // Update media with a non-zero default start position and window offset.
    firstMediaSource.setNewSourceInfo(timelineWithOffsets);
    // Wait until player transitions to second source (which also has non-zero offsets).
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
    player.release();

    assertThat(rendererStreamOffsetsUs).hasSize(2);
    assertThat(firstBufferTimesUsWithOffset).hasSize(2);
    // Assert that the offsets and buffer times match the expected sample time.
    assertThat(firstBufferTimesUsWithOffset.get(0))
        .isEqualTo(rendererStreamOffsetsUs.get(0) + firstSampleTimeUs);
    assertThat(firstBufferTimesUsWithOffset.get(1))
        .isEqualTo(rendererStreamOffsetsUs.get(1) + firstSampleTimeUs);
    // Assert that the second source continues rendering seamlessly at the point where the first one
    // ended.
    long periodDurationUs =
        timelineWithOffsets.getPeriod(/* periodIndex= */ 0, new Timeline.Period()).durationUs;
    assertThat(firstBufferTimesUsWithOffset.get(1))
        .isEqualTo(rendererStreamOffsetsUs.get(0) + periodDurationUs);
  }

  @Test
  public void mediaItemOfSources_correctInTimelineWindows() throws Exception {
    TimelineWindowDefinition window1 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs = */ 100_000,
            /* defaultPositionUs = */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            ImmutableList.of(AdPlaybackState.NONE),
            MediaItem.fromUri("http://foo.bar/fake1"));
    FakeMediaSource fakeMediaSource1 = new FakeMediaSource(new FakeTimeline(window1));
    TimelineWindowDefinition window2 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 2,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs = */ 100_000,
            /* defaultPositionUs = */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            ImmutableList.of(AdPlaybackState.NONE),
            MediaItem.fromUri("http://foo.bar/fake2"));
    FakeMediaSource fakeMediaSource2 = new FakeMediaSource(new FakeTimeline(window2));
    TimelineWindowDefinition window3 =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 3,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs = */ 100_000,
            /* defaultPositionUs = */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            ImmutableList.of(AdPlaybackState.NONE),
            MediaItem.fromUri("http://foo.bar/fake3"));
    FakeMediaSource fakeMediaSource3 = new FakeMediaSource(new FakeTimeline(window3));
    final Player[] playerHolder = {null};
    ActionSchedule actionSchedule =
        new ActionSchedule.Builder(TAG)
            .pause()
            .executeRunnable(
                new PlayerRunnable() {
                  @Override
                  public void run(ExoPlayer player) {
                    playerHolder[0] = player;
                  }
                })
            .waitForPlaybackState(Player.STATE_READY)
            .seek(/* positionMs= */ 0)
            .play()
            .build();
    List<MediaItem> currentMediaItems = new ArrayList<>();
    List<MediaItem> mediaItemsInTimeline = new ArrayList<>();
    Player.Listener playerListener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
              return;
            }
            Window window = new Window();
            for (int i = 0; i < timeline.getWindowCount(); i++) {
              mediaItemsInTimeline.add(timeline.getWindow(i, window).mediaItem);
            }
          }

          @Override
          public void onPositionDiscontinuity(int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK
                || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              currentMediaItems.add(playerHolder[0].getCurrentMediaItem());
            }
          }
        };
    new ExoPlayerTestRunner.Builder(context)
        .setPlayerListener(playerListener)
        .setActionSchedule(actionSchedule)
        .setMediaSources(fakeMediaSource1, fakeMediaSource2, fakeMediaSource3)
        .build()
        .start()
        .blockUntilActionScheduleFinished(TIMEOUT_MS)
        .blockUntilEnded(TIMEOUT_MS);

    assertThat(currentMediaItems.get(0).localConfiguration.uri.toString())
        .isEqualTo("http://foo.bar/fake1");
    assertThat(currentMediaItems.get(1).localConfiguration.uri.toString())
        .isEqualTo("http://foo.bar/fake2");
    assertThat(currentMediaItems.get(2).localConfiguration.uri.toString())
        .isEqualTo("http://foo.bar/fake3");
    assertThat(mediaItemsInTimeline).containsExactlyElementsIn(currentMediaItems);
  }

  @Test
  public void setMediaSource_notifiesMediaItemTransition() {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });

    player.setMediaSource(mediaSource);

    assertThat(reportedMediaItems).containsExactly(mediaSource.getMediaItem());
    assertThat(reportedTransitionReasons)
        .containsExactly(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    player.release();
  }

  @Test
  public void setMediaSource_replaceWithSameMediaItem_notifiesMediaItemTransition()
      throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });

    player.setMediaSource(mediaSource);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    player.setMediaSource(mediaSource);

    assertThat(reportedMediaItems)
        .containsExactly(mediaSource.getMediaItem(), mediaSource.getMediaItem());
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    player.release();
  }

  @Test
  public void automaticWindowTransition_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    assertThat(reportedMediaItems)
        .containsExactly(mediaSource1.getMediaItem(), mediaSource2.getMediaItem())
        .inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        .inOrder();
    player.release();
  }

  @Test
  public void clearMediaItems_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();
    player.play();
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);

    player.clearMediaItems();

    assertThat(reportedMediaItems)
        .containsExactly(mediaSource1.getMediaItem(), mediaSource2.getMediaItem(), null)
        .inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        .inOrder();
    player.release();
  }

  @Test
  public void seekTo_otherWindow_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 2000);

    assertThat(reportedMediaItems)
        .containsExactly(mediaSource1.getMediaItem(), mediaSource2.getMediaItem())
        .inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        .inOrder();
    player.release();
  }

  @Test
  public void seekTo_sameWindow_doesNotNotifyMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 2000);

    assertThat(reportedMediaItems).containsExactly(mediaSource1.getMediaItem()).inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        .inOrder();
    player.release();
  }

  @Test
  public void seekToNextPrevious_singleItemRepeat_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSource(mediaSource);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.seekToNextMediaItem();
    player.seekToPreviousMediaItem();
    player.release();

    MediaItem expectedMediaItem = mediaSource.getMediaItem();
    assertThat(reportedMediaItems)
        .containsExactly(expectedMediaItem, expectedMediaItem, expectedMediaItem);
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
  }

  @Test
  public void repeat_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();

    player.play();
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    runUntilPositionDiscontinuity(player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    runUntilPlaybackState(player, Player.STATE_ENDED);

    assertThat(reportedMediaItems)
        .containsExactly(
            mediaSource1.getMediaItem(),
            mediaSource1.getMediaItem(),
            mediaSource1.getMediaItem(),
            mediaSource2.getMediaItem())
        .inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        .inOrder();
    player.release();
  }

  // Tests deprecated stop(boolean reset)
  @SuppressWarnings("deprecation")
  @Test
  public void stop_withReset_notifiesMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.stop(/* reset= */ true);

    assertThat(reportedMediaItems).containsExactly(mediaSource1.getMediaItem(), null).inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        .inOrder();
    player.release();
  }

  @Test
  public void stop_withoutReset_doesNotNotifyMediaItemTransition() throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    MediaSource mediaSource1 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    MediaSource mediaSource2 = FakeMediaSource.createWithWindowId(/* windowId= */ new Object());
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSources(ImmutableList.of(mediaSource1, mediaSource2));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.stop();

    assertThat(reportedMediaItems).containsExactly(mediaSource1.getMediaItem()).inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        .inOrder();
    player.release();
  }

  @Test
  public void timelineRefresh_withModifiedMediaItem_doesNotNotifyMediaItemTransition()
      throws Exception {
    List<MediaItem> reportedMediaItems = new ArrayList<>();
    List<Integer> reportedTransitionReasons = new ArrayList<>();
    List<Timeline> reportedTimelines = new ArrayList<>();
    MediaItem initialMediaItem = FakeTimeline.FAKE_MEDIA_ITEM.buildUpon().setTag(0).build();
    TimelineWindowDefinition initialWindow =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 10_000_000,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            ImmutableList.of(AdPlaybackState.NONE),
            initialMediaItem);
    TimelineWindowDefinition secondWindow =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 10_000_000,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            ImmutableList.of(AdPlaybackState.NONE),
            initialMediaItem.buildUpon().setTag(1).build());
    FakeTimeline timeline = new FakeTimeline(initialWindow);
    FakeTimeline newTimeline = new FakeTimeline(secondWindow);
    FakeMediaSource mediaSource = new FakeMediaSource(timeline);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(
        new Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            reportedTimelines.add(timeline);
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            reportedMediaItems.add(mediaItem);
            reportedTransitionReasons.add(reason);
          }
        });
    player.setMediaSource(mediaSource);
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    mediaSource.setNewSourceInfo(newTimeline);
    runUntilPlaybackState(player, Player.STATE_ENDED);

    assertTimelinesSame(
        reportedTimelines, ImmutableList.of(placeholderTimeline, timeline, newTimeline));
    assertThat(reportedMediaItems).containsExactly(initialMediaItem).inOrder();
    assertThat(reportedTransitionReasons)
        .containsExactly(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        .inOrder();
    player.release();
  }

  @Test
  public void isCommandAvailable_isTrueForAvailableCommands() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));

    assertThat(player.isCommandAvailable(COMMAND_PLAY_PAUSE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_PREPARE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_STOP)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_DEFAULT_POSITION)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_BACK)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_FORWARD)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_SHUFFLE_MODE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_REPEAT_MODE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_TIMELINE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_MEDIA_ITEMS_METADATA)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_MEDIA_ITEMS_METADATA)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_MEDIA_ITEM)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_AUDIO_ATTRIBUTES)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_VOLUME)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_DEVICE_VOLUME)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_VOLUME)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_DEVICE_VOLUME)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_ADJUST_DEVICE_VOLUME)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_VIDEO_SURFACE)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_TEXT)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_SET_TRACK_SELECTION_PARAMETERS)).isTrue();
    assertThat(player.isCommandAvailable(COMMAND_GET_TRACKS)).isTrue();
  }

  @Test
  public void isCommandAvailable_duringAd_isFalseForSeekCommands() throws Exception {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAvailableAdUri(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                Uri.parse("https://google.com/ad"))
            .withAdDurationsUs(/* adDurationUs= */ new long[][] {{Util.msToUs(4_000)}});
    Timeline adTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(10_000),
                adPlaybackState));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSources(
        ImmutableList.of(
            new FakeMediaSource(), new FakeMediaSource(adTimeline), new FakeMediaSource()));
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 0);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_BACK)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_FORWARD)).isFalse();
  }

  @Test
  public void isCommandAvailable_duringUnseekableItem_isFalseForSeekInCurrentCommands()
      throws Exception {
    Timeline timelineWithUnseekableWindow =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* durationUs= */ Util.msToUs(10_000)));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSource(new FakeMediaSource(timelineWithUnseekableWindow));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_BACK)).isFalse();
    assertThat(player.isCommandAvailable(COMMAND_SEEK_FORWARD)).isFalse();
  }

  @Test
  public void isCommandAvailable_duringUnseekableLiveItem_isFalseForSeekToPrevious()
      throws Exception {
    Timeline timelineWithUnseekableLiveWindow =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ C.TIME_UNSET,
                /* defaultPositionUs= */ 10_000_000,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                AdPlaybackState.NONE));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSource(new FakeMediaSource(timelineWithUnseekableLiveWindow));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS)).isFalse();
  }

  @Test
  public void
      isCommandAvailable_duringUnseekableLiveItemWithPreviousWindow_isTrueForSeekToPrevious()
          throws Exception {
    Timeline timelineWithUnseekableLiveWindow =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 0),
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 1,
                /* isSeekable= */ false,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ C.TIME_UNSET,
                /* defaultPositionUs= */ 10_000_000,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                AdPlaybackState.NONE));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSource(new FakeMediaSource(timelineWithUnseekableLiveWindow));
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 0);
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS)).isTrue();
  }

  @Test
  public void isCommandAvailable_duringLiveItem_isTrueForSeekToNext() throws Exception {
    Timeline timelineWithLiveWindow =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ C.TIME_UNSET,
                /* defaultPositionUs= */ 10_000_000,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                AdPlaybackState.NONE));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();

    player.addMediaSource(new FakeMediaSource(timelineWithLiveWindow));
    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)).isTrue();
  }

  @Test
  public void seekTo_nextWindow_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekToPreviousWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Commands commandsWithSeekToPreviousAndNextWindow =
        createWithDefaultCommands(
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSources(
        ImmutableList.of(
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousAndNextWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 0);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 3, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousWindow);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void seekTo_previousWindow_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekToPreviousWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Commands commandsWithSeekToPreviousAndNextWindow =
        createWithDefaultCommands(
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.seekTo(/* mediaItemIndex= */ 3, /* positionMs= */ 0);
    player.addMediaSources(
        ImmutableList.of(
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousAndNextWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 0);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 0);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void seekTo_sameWindow_doesNotNotifyAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSources(ImmutableList.of(new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);

    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 200);
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 100);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());
  }

  @Test
  public void automaticWindowTransition_notifiesAvailableCommandsChanged() throws Exception {
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Commands commandsWithSeekInCurrentAndToNextWindow =
        createWithDefaultCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD);
    Player.Commands commandsWithSeekInCurrentAndToPreviousWindow =
        createWithDefaultCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD);
    Player.Commands commandsWithSeekAnywhere =
        createWithDefaultCommands(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSources(
        ImmutableList.of(
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource(),
            new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToNextWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 1);
    runUntilPendingCommandsAreFullyHandled(player);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekAnywhere);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());

    playUntilStartOfMediaItem(player, /* mediaItemIndex= */ 2);
    runUntilPendingCommandsAreFullyHandled(player);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());

    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekInCurrentAndToPreviousWindow);
    verify(mockListener, times(4)).onAvailableCommandsChanged(any());
  }

  @Test
  public void addMediaSource_atTheEnd_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void addMediaSource_atTheStart_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToPreviousWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.addMediaSource(/* index= */ 0, new FakeMediaSource());
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_atTheEnd_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Commands emptyTimelineCommands = createWithDefaultCommands(/* isTimelineEmpty= */ true);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSources(
        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource(), new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 2);
    verify(mockListener).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 1);
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 0);
    verify(mockListener).onAvailableCommandsChanged(emptyTimelineCommands);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_atTheStart_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToPreviousWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands emptyTimelineCommands = createWithDefaultCommands(/* isTimelineEmpty= */ true);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 0);
    player.addMediaSources(
        ImmutableList.of(new FakeMediaSource(), new FakeMediaSource(), new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 0);
    verify(mockListener).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 0);
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 0);
    verify(mockListener).onAvailableCommandsChanged(emptyTimelineCommands);
    verify(mockListener, times(3)).onAvailableCommandsChanged(any());
  }

  @Test
  public void removeMediaItem_current_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.removeMediaItem(/* index= */ 0);
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void setRepeatMode_all_notifiesAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Commands commandsWithSeekToPreviousAndNextWindow =
        createWithDefaultCommands(
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousAndNextWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void setRepeatMode_one_doesNotNotifyAvailableCommandsChanged() {
    Player.Commands defaultCommands = createWithDefaultCommands();
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);

    player.addMediaSource(new FakeMediaSource());
    verify(mockListener).onAvailableCommandsChanged(defaultCommands);

    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    verify(mockListener).onAvailableCommandsChanged(any());
  }

  @Test
  public void setShuffleModeEnabled_notifiesAvailableCommandsChanged() {
    Player.Commands commandsWithSeekToPreviousWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    Player.Commands commandsWithSeekToNextWindow =
        createWithDefaultCommands(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT);
    Player.Listener mockListener = mock(Player.Listener.class);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addListener(mockListener);
    MediaSource mediaSource =
        new ConcatenatingMediaSource(
            false,
            new FakeShuffleOrder(/* length= */ 2),
            new FakeMediaSource(),
            new FakeMediaSource());

    player.addMediaSource(mediaSource);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToNextWindow);
    // Check that there were no other calls to onAvailableCommandsChanged.
    verify(mockListener).onAvailableCommandsChanged(any());

    player.setShuffleModeEnabled(true);
    verify(mockListener).onAvailableCommandsChanged(commandsWithSeekToPreviousWindow);
    verify(mockListener, times(2)).onAvailableCommandsChanged(any());
  }

  @Test
  public void
      mediaSourceMaybeThrowSourceInfoRefreshError_isNotThrownUntilPlaybackReachedFailingItem()
          throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSource(new FakeMediaSource());
    player.addMediaSource(
        new FakeMediaSource(/* timeline= */ null) {
          @Override
          public void maybeThrowSourceInfoRefreshError() throws IOException {
            throw new IOException();
          }
        });

    player.prepare();
    player.play();
    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);

    Object period1Uid =
        player
            .getCurrentTimeline()
            .getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true)
            .uid;
    assertThat(error.mediaPeriodId.periodUid).isEqualTo(period1Uid);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
  }

  @Test
  public void mediaPeriodMaybeThrowPrepareError_isNotThrownUntilPlaybackReachedFailingItem()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Timeline timeline = new FakeTimeline();
    player.addMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.addMediaSource(
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                /* singleSampleTimeUs= */ 0,
                mediaSourceEventDispatcher,
                DrmSessionManager.DRM_UNSUPPORTED,
                drmEventDispatcher,
                /* deferOnPrepared= */ true) {
              @Override
              public void maybeThrowPrepareError() throws IOException {
                throw new IOException();
              }
            };
          }
        });

    player.prepare();
    player.play();
    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);

    Object period1Uid =
        player
            .getCurrentTimeline()
            .getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true)
            .uid;
    assertThat(error.mediaPeriodId.periodUid).isEqualTo(period1Uid);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
  }

  @Test
  public void sampleStreamMaybeThrowError_isNotThrownUntilPlaybackReachedFailingItem()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Timeline timeline = new FakeTimeline();
    player.addMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT));
    player.addMediaSource(
        new FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT) {
          @Override
          protected MediaPeriod createMediaPeriod(
              MediaPeriodId id,
              TrackGroupArray trackGroupArray,
              Allocator allocator,
              MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
              DrmSessionManager drmSessionManager,
              DrmSessionEventListener.EventDispatcher drmEventDispatcher,
              @Nullable TransferListener transferListener) {
            return new FakeMediaPeriod(
                trackGroupArray,
                allocator,
                /* trackDataFactory= */ (format, mediaPeriodId) -> ImmutableList.of(),
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                /* deferOnPrepared= */ false) {
              @Override
              protected FakeSampleStream createSampleStream(
                  Allocator allocator,
                  @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
                  DrmSessionManager drmSessionManager,
                  DrmSessionEventListener.EventDispatcher drmEventDispatcher,
                  Format initialFormat,
                  List<FakeSampleStreamItem> fakeSampleStreamItems) {
                return new FakeSampleStream(
                    allocator,
                    mediaSourceEventDispatcher,
                    drmSessionManager,
                    drmEventDispatcher,
                    initialFormat,
                    fakeSampleStreamItems) {
                  @Override
                  public void maybeThrowError() throws IOException {
                    throw new IOException();
                  }
                };
              }
            };
          }
        });

    player.prepare();
    player.play();
    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);

    Object period1Uid =
        player
            .getCurrentTimeline()
            .getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true)
            .uid;
    assertThat(error.mediaPeriodId.periodUid).isEqualTo(period1Uid);
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
  }

  @Test
  public void rendererError_isReportedWithReadingMediaPeriodId() throws Exception {
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
                  // Fail when enabling the renderer. This will happen during the period
                  // transition while the reading and playing period are different.
                  throw createRendererException(
                      new IllegalStateException(),
                      ExoPlayerTestRunner.AUDIO_FORMAT,
                      PlaybackException.ERROR_CODE_UNSPECIFIED);
                }
              }
            };
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setRenderersFactory(renderersFactory).build();
    player.setMediaSources(ImmutableList.of(source0, source1));
    player.prepare();
    player.play();

    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);

    Object period1Uid =
        player
            .getCurrentTimeline()
            .getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true)
            .uid;
    assertThat(error.mediaPeriodId.periodUid).isEqualTo(period1Uid);
    // Verify test setup by checking that playing period was indeed different.
    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(0);
  }

  @Test
  public void enableOffloadScheduling_isReported() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    ExoPlayer.AudioOffloadListener mockListener = mock(ExoPlayer.AudioOffloadListener.class);
    player.addAudioOffloadListener(mockListener);

    player.experimentalSetOffloadSchedulingEnabled(true);
    verify(mockListener).onExperimentalOffloadSchedulingEnabledChanged(true);

    player.experimentalSetOffloadSchedulingEnabled(false);
    verify(mockListener).onExperimentalOffloadSchedulingEnabledChanged(false);
  }

  @Test
  public void enableOffloadScheduling_isEnable_playerSleeps() throws Exception {
    FakeSleepRenderer sleepRenderer = new FakeSleepRenderer(C.TRACK_TYPE_AUDIO);
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(sleepRenderer).build();
    Timeline timeline = new FakeTimeline();
    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT));
    player.experimentalSetOffloadSchedulingEnabled(true);
    player.prepare();
    player.play();

    sleepRenderer.sleepOnNextRender();

    runUntilSleepingForOffload(player, /* expectedSleepForOffload= */ true);
    assertThat(player.experimentalIsSleepingForOffload()).isTrue();
  }

  @Test
  public void
      experimentalEnableOffloadSchedulingWhileSleepingForOffload_isDisabled_renderingResumes()
          throws Exception {
    FakeSleepRenderer sleepRenderer = new FakeSleepRenderer(C.TRACK_TYPE_AUDIO).sleepOnNextRender();
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(sleepRenderer).build();
    Timeline timeline = new FakeTimeline();
    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT));
    player.experimentalSetOffloadSchedulingEnabled(true);
    player.prepare();
    player.play();
    runUntilSleepingForOffload(player, /* expectedSleepForOffload= */ true);

    player.experimentalSetOffloadSchedulingEnabled(false); // Force the player to exit offload sleep

    runUntilSleepingForOffload(player, /* expectedSleepForOffload= */ false);
    assertThat(player.experimentalIsSleepingForOffload()).isFalse();
    runUntilPlaybackState(player, Player.STATE_ENDED);
  }

  @Test
  public void wakeupListenerWhileSleepingForOffload_isWokenUp_renderingResumes() throws Exception {
    FakeSleepRenderer sleepRenderer = new FakeSleepRenderer(C.TRACK_TYPE_AUDIO).sleepOnNextRender();
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(sleepRenderer).build();
    Timeline timeline = new FakeTimeline();
    player.setMediaSource(new FakeMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT));
    player.experimentalSetOffloadSchedulingEnabled(true);
    player.prepare();
    player.play();
    runUntilSleepingForOffload(player, /* expectedSleepForOffload= */ true);

    sleepRenderer.wakeup();

    runUntilSleepingForOffload(player, /* expectedSleepForOffload= */ false);
    assertThat(player.experimentalIsSleepingForOffload()).isFalse();
    runUntilPlaybackState(player, Player.STATE_ENDED);
  }

  @Test
  public void targetLiveOffsetInMedia_adjustsLiveOffsetToTargetOffset() throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.pause();
    player.setMediaSource(new FakeMediaSource(timeline));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Verify test setup (now = 20 seconds in live window, default start position = 8 seconds).
    assertThat(liveOffsetAtStart).isIn(Range.closed(11_900L, 12_100L));

    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that player adjusted live offset to the media value.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(8_900L, 9_100L));
    // Assert that none of these playback speed changes were reported.
    verify(mockListener, never()).onPlaybackParametersChanged(any());
  }

  @Test
  public void targetLiveOffsetInMedia_withInitialSeek_adjustsLiveOffsetToInitialSeek()
      throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    player.pause();

    player.seekTo(18_000);
    player.setMediaSource(new FakeMediaSource(timeline), /* resetPosition= */ false);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Target should have been permanently adjusted to 2 seconds.
    // (initial now = 20 seconds in live window, initial seek to 18 seconds)
    assertThat(liveOffsetAtStart).isIn(Range.closed(1_900L, 2_100L));
    assertThat(liveOffsetAtEnd).isIn(Range.closed(1_900L, 2_100L));
  }

  @Test
  public void targetLiveOffsetInMedia_withUserSeek_adjustsLiveOffsetToSeek() throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    player.pause();
    player.setMediaSource(new FakeMediaSource(timeline));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Verify test setup (now = 20 seconds in live window, default start position = 8 seconds).
    assertThat(liveOffsetAtStart).isIn(Range.closed(11_900L, 12_100L));

    // Seek to a live offset of 2 seconds.
    player.seekTo(18_000);
    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert the live offset adjustment was permanent.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(1_900L, 2_100L));
  }

  @Test
  public void targetLiveOffsetInMedia_withTimelineUpdate_adjustsLiveOffsetToLatestTimeline()
      throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline initialTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    Timeline updatedTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs + 50_000),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
                    .build()));
    FakeMediaSource fakeMediaSource = new FakeMediaSource(initialTimeline);
    player.pause();
    player.setMediaSource(fakeMediaSource);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Verify test setup (now = 20 seconds in live window, default start position = 8 seconds).
    assertThat(liveOffsetAtStart).isIn(Range.closed(11_900L, 12_100L));

    // Play a bit and update configuration.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 55_000);
    fakeMediaSource.setNewSourceInfo(updatedTimeline);

    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that adjustment uses target offset from the updated timeline.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(3_900L, 4_100L));
  }

  @Test
  public void playerIdle_withSetPlaybackSpeed_usesPlaybackParameterSpeedWithPitchUnchanged() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 1, /* pitch= */ 2));
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.prepare();

    player.setPlaybackSpeed(2);

    verify(mockListener)
        .onPlaybackParametersChanged(new PlaybackParameters(/* speed= */ 2, /* pitch= */ 2));
  }

  @Test
  public void setPlaybackSpeed_withAdPlayback_onlyAppliesToContent() throws Exception {
    // Create renderer with media clock to listen to playback parameter changes and reported speed
    // changes.
    ArrayList<PlaybackParameters> playbackParameters = new ArrayList<>();
    ArrayList<Pair<Float, Float>> speedChanges = new ArrayList<>();
    FakeMediaClockRenderer audioRenderer =
        new FakeMediaClockRenderer(C.TRACK_TYPE_AUDIO) {
          private long positionUs;

          @Override
          protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
            this.positionUs = offsetUs;
          }

          @Override
          public long getPositionUs() {
            // Continuously increase position to let playback progress.
            positionUs += 10_000;
            return positionUs;
          }

          @Override
          public void setPlaybackParameters(PlaybackParameters parameters) {
            playbackParameters.add(parameters);
          }

          @Override
          public PlaybackParameters getPlaybackParameters() {
            return playbackParameters.isEmpty()
                ? PlaybackParameters.DEFAULT
                : Iterables.getLast(playbackParameters);
          }

          @Override
          public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
              throws ExoPlaybackException {
            speedChanges.add(Pair.create(currentPlaybackSpeed, targetPlaybackSpeed));
          }
        };
    ExoPlayer player = new TestExoPlayerBuilder(context).setRenderers(audioRenderer).build();
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1,
            /* adGroupTimesUs...= */ 0,
            7 * C.MICROS_PER_SECOND,
            C.TIME_END_OF_SOURCE);
    TimelineWindowDefinition adTimelineDefinition =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 10 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            adPlaybackState);
    player.setMediaSource(
        new FakeMediaSource(
            new FakeTimeline(adTimelineDefinition), ExoPlayerTestRunner.AUDIO_FORMAT));
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);

    player.setPlaybackSpeed(5f);
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Assert that the media clock received the playback parameters at each ad/content boundary.
    assertThat(playbackParameters)
        .containsExactly(
            /* preroll ad */ new PlaybackParameters(1f),
            /* content after preroll */ new PlaybackParameters(5f),
            /* midroll ad */ new PlaybackParameters(1f),
            /* content after midroll */ new PlaybackParameters(5f),
            /* postroll ad */ new PlaybackParameters(1f),
            /* content after postroll */ new PlaybackParameters(5f))
        .inOrder();

    // Assert that the renderer received the speed changes at each ad/content boundary.
    assertThat(speedChanges)
        .containsExactly(
            /* initial setup */ Pair.create(5f, 5f),
            /* preroll ad */ Pair.create(1f, 5f),
            /* content after preroll */ Pair.create(5f, 5f),
            /* midroll ad */ Pair.create(1f, 5f),
            /* content after midroll */ Pair.create(5f, 5f),
            /* postroll ad */ Pair.create(1f, 5f),
            /* content after postroll */ Pair.create(5f, 5f))
        .inOrder();

    // Assert that user-set speed was reported, but none of the ad overrides.
    verify(mockListener).onPlaybackParametersChanged(any());
    verify(mockListener).onPlaybackParametersChanged(new PlaybackParameters(5.0f));
  }

  @Test
  public void targetLiveOffsetInMedia_withSetPlaybackParameters_usesPlaybackParameterSpeed()
      throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 20 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.pause();
    player.setMediaSource(new FakeMediaSource(timeline));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Verify test setup (now = 20 seconds in live window, default start position = 20 seconds).
    assertThat(liveOffsetAtStart).isIn(Range.closed(-100L, 100L));

    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2.0f));
    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that the player didn't adjust the live offset to the media value (9 seconds) and
    // instead played the media with double speed (resulting in a negative live offset).
    assertThat(liveOffsetAtEnd).isLessThan(0);
    // Assert that user-set speed was reported
    verify(mockListener).onPlaybackParametersChanged(new PlaybackParameters(2.0f));
  }

  @Test
  public void
      targetLiveOffsetInMedia_afterAutomaticPeriodTransition_adjustsLiveOffsetToTargetOffset()
          throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 10_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline nonLiveTimeline = new FakeTimeline();
    Timeline liveTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    player.pause();
    player.addMediaSource(new FakeMediaSource(nonLiveTimeline));
    player.addMediaSource(new FakeMediaSource(liveTimeline));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that player adjusted live offset to the media value.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(8_900L, 9_100L));
  }

  @Test
  public void
      targetLiveOffsetInMedia_afterSeekToDefaultPositionInOtherStream_adjustsLiveOffsetToMediaOffset()
          throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline liveTimeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    Timeline liveTimeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
                    .build()));
    player.pause();
    player.addMediaSource(new FakeMediaSource(liveTimeline1));
    player.addMediaSource(new FakeMediaSource(liveTimeline2));
    // Ensure we override the target live offset to a seek position in the first live stream.
    player.seekTo(10_000);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    // Seek to default position in second stream.
    player.seekToNextMediaItem();
    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that player adjusted live offset to the media value.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(3_900L, 4_100L));
  }

  @Test
  public void
      targetLiveOffsetInMedia_afterSeekToSpecificPositionInOtherStream_adjustsLiveOffsetToSeekPosition()
          throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline liveTimeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(9_000).build())
                    .build()));
    Timeline liveTimeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder()
                    .setUri(Uri.EMPTY)
                    .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
                    .build()));
    player.pause();
    player.addMediaSource(new FakeMediaSource(liveTimeline1));
    player.addMediaSource(new FakeMediaSource(liveTimeline2));
    // Ensure we override the target live offset to a seek position in the first live stream.
    player.seekTo(10_000);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    // Seek to specific position in second stream (at 2 seconds live offset).
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 18_000);
    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that player adjusted live offset to the seek.
    assertThat(liveOffsetAtEnd).isIn(Range.closed(1_900L, 2_100L));
  }

  @Test
  public void targetLiveOffsetInMedia_unknownWindowStartTime_doesNotAdjustLiveOffset()
      throws Exception {
    FakeClock fakeClock =
        new FakeClock(/* initialTimeMs= */ 987_654_321L, /* isAutoAdvancing= */ true);
    ExoPlayer player = new TestExoPlayerBuilder(context).setClock(fakeClock).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.EMPTY)
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
            .build();
    Timeline liveTimeline =
        new SinglePeriodTimeline(
            /* presentationStartTimeMs= */ C.TIME_UNSET,
            /* windowStartTimeMs= */ C.TIME_UNSET,
            /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
            /* periodDurationUs= */ 1000 * C.MICROS_PER_SECOND,
            /* windowDurationUs= */ 1000 * C.MICROS_PER_SECOND,
            /* windowPositionInPeriodUs= */ 0,
            /* windowDefaultStartPositionUs= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ true,
            /* suppressPositionProjection= */ false,
            /* manifest= */ null,
            mediaItem,
            mediaItem.liveConfiguration);
    player.pause();
    player.setMediaSource(new FakeMediaSource(liveTimeline));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    long playbackStartTimeMs = fakeClock.elapsedRealtime();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long playbackEndTimeMs = fakeClock.elapsedRealtime();
    player.release();

    // Assert that the time it took to play 999 seconds of media is 999 seconds (asserting that no
    // playback speed adjustment was used).
    assertThat(playbackEndTimeMs - playbackStartTimeMs).isEqualTo(999_000);
  }

  @Test
  public void noTargetLiveOffsetInMedia_doesNotAdjustLiveOffset() throws Exception {
    long windowStartUnixTimeMs = 987_654_321_000L;
    long nowUnixTimeMs = windowStartUnixTimeMs + 20_000;
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(
                new FakeClock(/* initialTimeMs= */ nowUnixTimeMs, /* isAutoAdvancing= */ true))
            .build();
    Timeline liveTimelineWithoutTargetLiveOffset =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1000 * C.MICROS_PER_SECOND,
                /* defaultPositionUs= */ 8 * C.MICROS_PER_SECOND,
                /* windowOffsetInFirstPeriodUs= */ Util.msToUs(windowStartUnixTimeMs),
                ImmutableList.of(AdPlaybackState.NONE),
                new MediaItem.Builder().setUri(Uri.EMPTY).build()));
    player.pause();
    player.setMediaSource(new FakeMediaSource(liveTimelineWithoutTargetLiveOffset));
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long liveOffsetAtStart = player.getCurrentLiveOffset();
    // Verify test setup (now = 20 seconds in live window, default start position = 8 seconds).
    assertThat(liveOffsetAtStart).isIn(Range.closed(11_900L, 12_100L));

    // Play until close to the end of the available live window.
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 999_000);
    long liveOffsetAtEnd = player.getCurrentLiveOffset();
    player.release();

    // Assert that live offset is still the same (i.e. unadjusted).
    assertThat(liveOffsetAtEnd).isIn(Range.closed(11_900L, 12_100L));
  }

  @Test
  public void onEvents_correspondToListenerCalls() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    Format formatWithStaticMetadata =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setMetadata(
                new Metadata(
                    new BinaryFrame(/* id= */ "", /* data= */ new byte[0]),
                    new TextInformationFrame(
                        /* id= */ "TT2",
                        /* description= */ null,
                        /* values= */ ImmutableList.of("title"))))
            .build();

    // Set multiple values together.
    player.setMediaSource(new FakeMediaSource(new FakeTimeline(), formatWithStaticMetadata));
    player.seekTo(2_000);
    player.setPlaybackParameters(new PlaybackParameters(/* speed= */ 2.0f));
    runUntilPendingCommandsAreFullyHandled(player);

    verify(listener).onTimelineChanged(any(), anyInt());
    verify(listener).onMediaItemTransition(any(), anyInt());
    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    verify(listener).onPlaybackParametersChanged(any());
    ArgumentCaptor<Player.Events> eventCaptor = ArgumentCaptor.forClass(Player.Events.class);
    verify(listener).onEvents(eq(player), eventCaptor.capture());
    Player.Events events = eventCaptor.getValue();
    assertThat(events.contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(events.contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)).isTrue();

    // Set values recursively.
    player.addListener(
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            player.setShuffleModeEnabled(true);
          }
        });
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    runUntilPendingCommandsAreFullyHandled(player);

    verify(listener).onRepeatModeChanged(anyInt());
    verify(listener).onShuffleModeEnabledChanged(anyBoolean());
    verify(listener, times(2)).onEvents(eq(player), eventCaptor.capture());
    events = Iterables.getLast(eventCaptor.getAllValues());
    assertThat(events.contains(Player.EVENT_REPEAT_MODE_CHANGED)).isTrue();
    assertThat(events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)).isTrue();

    // Ensure all other events are called (even though we can't control how exactly they are
    // combined together in onEvents calls).
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    player.play();
    player.setMediaItem(MediaItem.fromUri("http://this-will-throw-an-exception.mp4"));
    TestPlayerRunHelper.runUntilError(player);
    runUntilPendingCommandsAreFullyHandled(player);
    player.release();

    // Verify that all callbacks have been called at least once.
    verify(listener, atLeastOnce()).onTimelineChanged(any(), anyInt());
    verify(listener, atLeastOnce()).onMediaItemTransition(any(), anyInt());
    verify(listener, atLeastOnce()).onPositionDiscontinuity(any(), any(), anyInt());
    verify(listener, atLeastOnce()).onPlaybackParametersChanged(any());
    verify(listener, atLeastOnce()).onRepeatModeChanged(anyInt());
    verify(listener, atLeastOnce()).onShuffleModeEnabledChanged(anyBoolean());
    verify(listener, atLeastOnce()).onPlaybackStateChanged(anyInt());
    verify(listener, atLeastOnce()).onIsLoadingChanged(anyBoolean());
    verify(listener, atLeastOnce()).onTracksChanged(any());
    verify(listener, atLeastOnce()).onMediaMetadataChanged(any());
    verify(listener, atLeastOnce()).onPlayWhenReadyChanged(anyBoolean(), anyInt());
    verify(listener, atLeastOnce()).onIsPlayingChanged(anyBoolean());
    verify(listener, atLeastOnce()).onPlayerErrorChanged(any());
    verify(listener, atLeastOnce()).onPlayerError(any());

    // Verify all the same events have been recorded with onEvents.
    verify(listener, atLeastOnce()).onEvents(eq(player), eventCaptor.capture());
    List<Player.Events> allEvents = eventCaptor.getAllValues();
    assertThat(containsEvent(allEvents, Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_REPEAT_MODE_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_PLAYBACK_STATE_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_IS_LOADING_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_TRACKS_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_MEDIA_METADATA_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_PLAY_WHEN_READY_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_IS_PLAYING_CHANGED)).isTrue();
    assertThat(containsEvent(allEvents, Player.EVENT_PLAYER_ERROR)).isTrue();
  }

  @Test
  public void repeatMode_windowTransition_callsOnPositionDiscontinuityAndOnMediaItemTransition()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    FakeMediaSource secondMediaSource =
        new FakeMediaSource(
            new FakeTimeline(
                new TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    /* id= */ 2,
                    /* isSeekable= */ true,
                    /* isDynamic= */ false,
                    /* durationUs= */ 20 * C.MICROS_PER_SECOND)));
    player.addListener(listener);
    player.setMediaSource(
        new FakeMediaSource(
            new FakeTimeline(
                new TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    /* id= */ 1,
                    /* isSeekable= */ true,
                    /* isDynamic= */ false,
                    /* durationUs= */ 10 * C.MICROS_PER_SECOND))));
    player.setRepeatMode(Player.REPEAT_MODE_ONE);

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.play();
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.addMediaSource(secondMediaSource);
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ C.TIME_UNSET);
    player.play();
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    // Expect media item transition for repeat mode ONE to be attributed to
    // DISCONTINUITY_REASON_REPEAT.
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    Player.PositionInfo oldPositionInfo = oldPosition.getValue();
    Player.PositionInfo newPositionInfo = newPosition.getValue();
    assertThat(oldPositionInfo.periodUid).isEqualTo(newPositionInfo.periodUid);
    assertThat(oldPositionInfo.periodIndex).isEqualTo(newPositionInfo.periodIndex);
    assertThat(oldPositionInfo.mediaItemIndex).isEqualTo(newPositionInfo.mediaItemIndex);
    assertThat(oldPositionInfo.mediaItem.localConfiguration.tag).isEqualTo(1);
    assertThat(oldPositionInfo.windowUid).isEqualTo(newPositionInfo.windowUid);
    assertThat(oldPositionInfo.positionMs).isEqualTo(10_000);
    assertThat(oldPositionInfo.contentPositionMs).isEqualTo(10_000);
    assertThat(newPositionInfo.positionMs).isEqualTo(0);
    assertThat(newPositionInfo.contentPositionMs).isEqualTo(0);
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    // Expect media item transition for repeat mode ALL with a single item to be attributed to
    // DISCONTINUITY_REASON_REPEAT.
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    oldPositionInfo = oldPosition.getValue();
    newPositionInfo = newPosition.getValue();
    assertThat(oldPositionInfo.periodUid).isEqualTo(newPositionInfo.periodUid);
    assertThat(oldPositionInfo.periodIndex).isEqualTo(newPositionInfo.periodIndex);
    assertThat(oldPositionInfo.mediaItemIndex).isEqualTo(newPositionInfo.mediaItemIndex);
    assertThat(oldPositionInfo.mediaItem.localConfiguration.tag).isEqualTo(1);
    assertThat(oldPositionInfo.windowUid).isEqualTo(newPositionInfo.windowUid);
    assertThat(oldPositionInfo.positionMs).isEqualTo(10_000);
    assertThat(oldPositionInfo.contentPositionMs).isEqualTo(10_000);
    assertThat(newPositionInfo.positionMs).isEqualTo(0);
    assertThat(newPositionInfo.contentPositionMs).isEqualTo(0);
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    // Expect media item transition for repeat mode ALL with more than one item which is attributed
    // to DISCONTINUITY_REASON_AUTO_TRANSITION not DISCONTINUITY_REASON_REPEAT.
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    oldPositionInfo = oldPosition.getValue();
    newPositionInfo = newPosition.getValue();
    assertThat(oldPositionInfo.mediaItemIndex).isEqualTo(1);
    assertThat(oldPositionInfo.mediaItem.localConfiguration.tag).isEqualTo(2);
    assertThat(oldPositionInfo.windowUid).isNotEqualTo(newPositionInfo.windowUid);
    assertThat(oldPositionInfo.positionMs).isEqualTo(20_000);
    assertThat(oldPositionInfo.contentPositionMs).isEqualTo(20_000);
    assertThat(newPositionInfo.positionMs).isEqualTo(0);
    assertThat(newPositionInfo.mediaItemIndex).isEqualTo(0);
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    // Last auto transition from media item 0 to media item 1 not caused by repeat mode.
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    // No more callbacks called.
    inOrder
        .verify(listener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder.verify(listener, never()).onMediaItemTransition(any(), anyInt());
    player.release();
  }

  @Test
  public void play_withPreMidAndPostRollAd_callsOnDiscontinuityCorrectly() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 2,
            /* adGroupTimesUs...= */ 0,
            7 * C.MICROS_PER_SECOND,
            C.TIME_END_OF_SOURCE);
    TimelineWindowDefinition adTimeline =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 10 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            adPlaybackState);
    player.setMediaSource(new FakeMediaSource(new FakeTimeline(adTimeline)));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(
            any(), any(), not(eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)));
    verify(listener, times(8))
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));

    // first ad group (pre-roll)
    // starts with ad to ad transition
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(0).adIndexInAdGroup).isEqualTo(1);
    // ad to content transition
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(0);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).adIndexInAdGroup).isEqualTo(1);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(0);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(1).adIndexInAdGroup).isEqualTo(-1);

    // second add group (mid-roll)
    assertThat(oldPositions.get(2).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(2).positionMs).isEqualTo(7000);
    assertThat(oldPositions.get(2).contentPositionMs).isEqualTo(7000);
    assertThat(oldPositions.get(2).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(2).adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPositions.get(2).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(2).positionMs).isEqualTo(0);
    assertThat(newPositions.get(2).contentPositionMs).isEqualTo(7000);
    assertThat(newPositions.get(2).adGroupIndex).isEqualTo(1);
    assertThat(newPositions.get(2).adIndexInAdGroup).isEqualTo(0);
    // ad to ad transition
    assertThat(oldPositions.get(3).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(3).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(3).contentPositionMs).isEqualTo(7000);
    assertThat(oldPositions.get(3).adGroupIndex).isEqualTo(1);
    assertThat(oldPositions.get(3).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(3).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(3).positionMs).isEqualTo(0);
    assertThat(newPositions.get(3).contentPositionMs).isEqualTo(7000);
    assertThat(newPositions.get(3).adGroupIndex).isEqualTo(1);
    assertThat(newPositions.get(3).adIndexInAdGroup).isEqualTo(1);
    // ad to content transition
    assertThat(oldPositions.get(4).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(4).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(4).contentPositionMs).isEqualTo(7000);
    assertThat(oldPositions.get(4).adGroupIndex).isEqualTo(1);
    assertThat(oldPositions.get(4).adIndexInAdGroup).isEqualTo(1);
    assertThat(newPositions.get(4).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(4).positionMs).isEqualTo(7000);
    assertThat(newPositions.get(4).contentPositionMs).isEqualTo(7000);
    assertThat(newPositions.get(4).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(4).adIndexInAdGroup).isEqualTo(-1);

    // third add group (post-roll)
    assertThat(oldPositions.get(5).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(5).positionMs).isEqualTo(10000);
    assertThat(oldPositions.get(5).contentPositionMs).isEqualTo(10000);
    assertThat(oldPositions.get(5).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(5).adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPositions.get(5).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(5).positionMs).isEqualTo(0);
    assertThat(newPositions.get(5).contentPositionMs).isEqualTo(10000);
    assertThat(newPositions.get(5).adGroupIndex).isEqualTo(2);
    assertThat(newPositions.get(5).adIndexInAdGroup).isEqualTo(0);
    // ad to ad transition
    assertThat(oldPositions.get(6).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(6).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(6).contentPositionMs).isEqualTo(10000);
    assertThat(oldPositions.get(6).adGroupIndex).isEqualTo(2);
    assertThat(oldPositions.get(6).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(6).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(6).positionMs).isEqualTo(0);
    assertThat(newPositions.get(6).contentPositionMs).isEqualTo(10000);
    assertThat(newPositions.get(6).adGroupIndex).isEqualTo(2);
    assertThat(newPositions.get(6).adIndexInAdGroup).isEqualTo(1);
    // post roll ad to end of content transition
    assertThat(oldPositions.get(7).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(7).positionMs).isEqualTo(5000);
    assertThat(oldPositions.get(7).contentPositionMs).isEqualTo(10000);
    assertThat(oldPositions.get(7).adGroupIndex).isEqualTo(2);
    assertThat(oldPositions.get(7).adIndexInAdGroup).isEqualTo(1);
    assertThat(newPositions.get(7).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(7).positionMs).isEqualTo(9999);
    assertThat(newPositions.get(7).contentPositionMs).isEqualTo(9999);
    assertThat(newPositions.get(7).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(7).adIndexInAdGroup).isEqualTo(-1);
    player.release();
  }

  @Test
  public void seekTo_seekOverMidRoll_callsOnDiscontinuityCorrectly() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    AdPlaybackState adPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */ 2 * C.MICROS_PER_SECOND);
    TimelineWindowDefinition adTimeline =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 0,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 10 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            adPlaybackState);
    player.setMediaSource(new FakeMediaSource(new FakeTimeline(adTimeline)));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 1000);
    player.seekTo(/* positionMs= */ 8_000);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT));
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SKIP));

    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    // SEEK behind mid roll
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(980L, 1_000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(980L, 1_000L));
    assertThat(oldPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(0).adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(8_000);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(8_000);
    assertThat(newPositions.get(0).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(0).adIndexInAdGroup).isEqualTo(-1);
    // SEEK_ADJUSTMENT back to ad
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(8_000);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(8_000);
    assertThat(oldPositions.get(1).adGroupIndex).isEqualTo(-1);
    assertThat(oldPositions.get(1).adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(8000);
    assertThat(newPositions.get(1).adGroupIndex).isEqualTo(0);
    assertThat(newPositions.get(1).adIndexInAdGroup).isEqualTo(0);
    // AUTO_TRANSITION back to content
    assertThat(oldPositions.get(2).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(2).positionMs).isEqualTo(5_000);
    assertThat(oldPositions.get(2).contentPositionMs).isEqualTo(8_000);
    assertThat(oldPositions.get(2).adGroupIndex).isEqualTo(0);
    assertThat(oldPositions.get(2).adIndexInAdGroup).isEqualTo(0);
    assertThat(newPositions.get(2).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(2).positionMs).isEqualTo(8_000);
    assertThat(newPositions.get(2).contentPositionMs).isEqualTo(8_000);
    assertThat(newPositions.get(2).adGroupIndex).isEqualTo(-1);
    assertThat(newPositions.get(2).adIndexInAdGroup).isEqualTo(-1);

    player.release();
  }

  @Test
  public void play_multiItemPlaylistWidthAds_callsOnDiscontinuityCorrectly() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    AdPlaybackState postRollAdPlaybackState =
        FakeTimeline.createAdPlaybackState(
            /* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE);
    TimelineWindowDefinition postRollWindow =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ "id-2",
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 20 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            postRollAdPlaybackState);
    AdPlaybackState preRollAdPlaybackState =
        FakeTimeline.createAdPlaybackState(/* adsPerAdGroup= */ 1, /* adGroupTimesUs...= */ 0);
    TimelineWindowDefinition preRollWindow =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ "id-3",
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 25 * C.MICROS_PER_SECOND,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ 0,
            preRollAdPlaybackState);
    player.setMediaSources(
        ImmutableList.of(
            createFakeMediaSource(/* id= */ "id-0"),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ "id-1",
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 15 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(new FakeTimeline(postRollWindow)),
            new FakeMediaSource(new FakeTimeline(preRollWindow))));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    Window window = new Window();
    InOrder inOrder = Mockito.inOrder(listener);
    // from first to second media item
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    assertThat(oldPosition.getValue().windowUid)
        .isEqualTo(player.getCurrentTimeline().getWindow(0, window).uid);
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(0);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-0");
    assertThat(oldPosition.getValue().positionMs).isEqualTo(10_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(10_000);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPosition.getValue().windowUid)
        .isEqualTo(player.getCurrentTimeline().getWindow(1, window).uid);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(1);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-1");
    assertThat(newPosition.getValue().positionMs).isEqualTo(0);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(0);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    // from second media item to third
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    assertThat(oldPosition.getValue().windowUid)
        .isEqualTo(player.getCurrentTimeline().getWindow(1, window).uid);
    assertThat(newPosition.getValue().windowUid)
        .isEqualTo(player.getCurrentTimeline().getWindow(2, window).uid);
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(1);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-1");
    assertThat(oldPosition.getValue().positionMs).isEqualTo(15_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(15_000);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(newPosition.getValue().positionMs).isEqualTo(0);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(0);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    // from third media item content to post roll ad
    @Nullable Object lastNewWindowUid = newPosition.getValue().windowUid;
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(oldPosition.getValue().windowUid).isEqualTo(lastNewWindowUid);
    assertThat(oldPosition.getValue().positionMs).isEqualTo(20_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(20_000);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(newPosition.getValue().positionMs).isEqualTo(0);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(20_000);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(0);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(0);
    // from third media item post roll to third media item content end
    lastNewWindowUid = newPosition.getValue().windowUid;
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(oldPosition.getValue().windowUid).isEqualTo(lastNewWindowUid);
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(oldPosition.getValue().positionMs).isEqualTo(5_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(20_000);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(0);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(0);
    assertThat(newPosition.getValue().windowUid).isEqualTo(oldPosition.getValue().windowUid);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(newPosition.getValue().positionMs).isEqualTo(19_999);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(19_999);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    // from third media item content end to fourth media item pre roll ad
    lastNewWindowUid = newPosition.getValue().windowUid;
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    assertThat(oldPosition.getValue().windowUid).isEqualTo(lastNewWindowUid);
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(2);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-2");
    assertThat(oldPosition.getValue().positionMs).isEqualTo(20_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(20_000);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    assertThat(newPosition.getValue().windowUid).isNotEqualTo(oldPosition.getValue().windowUid);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(3);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-3");
    assertThat(newPosition.getValue().positionMs).isEqualTo(0);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(0);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(0);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(0);
    // from fourth media item pre roll ad to fourth media item content
    lastNewWindowUid = newPosition.getValue().windowUid;
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    assertThat(oldPosition.getValue().windowUid).isEqualTo(lastNewWindowUid);
    assertThat(oldPosition.getValue().mediaItemIndex).isEqualTo(3);
    assertThat(oldPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-3");
    assertThat(oldPosition.getValue().positionMs).isEqualTo(5_000);
    assertThat(oldPosition.getValue().contentPositionMs).isEqualTo(0);
    assertThat(oldPosition.getValue().adGroupIndex).isEqualTo(0);
    assertThat(oldPosition.getValue().adIndexInAdGroup).isEqualTo(0);
    assertThat(newPosition.getValue().windowUid).isEqualTo(oldPosition.getValue().windowUid);
    assertThat(newPosition.getValue().mediaItemIndex).isEqualTo(3);
    assertThat(newPosition.getValue().mediaItem.localConfiguration.tag).isEqualTo("id-3");
    assertThat(newPosition.getValue().positionMs).isEqualTo(0);
    assertThat(newPosition.getValue().contentPositionMs).isEqualTo(0);
    assertThat(newPosition.getValue().adGroupIndex).isEqualTo(-1);
    assertThat(newPosition.getValue().adIndexInAdGroup).isEqualTo(-1);
    inOrder
        .verify(listener, never())
        .onPositionDiscontinuity(
            any(), any(), not(eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)));
    inOrder
        .verify(listener, never())
        .onMediaItemTransition(any(), not(eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)));
    player.release();
  }

  @Test
  public void setMediaSources_removesPlayingPeriod_callsOnPositionDiscontinuity() throws Exception {
    FakeMediaSource secondMediaSource =
        new FakeMediaSource(
            new FakeTimeline(
                new TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    /* id= */ 2,
                    /* isSeekable= */ true,
                    /* isDynamic= */ false,
                    /* durationUs= */ 15 * C.MICROS_PER_SECOND)));
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(
        new FakeMediaSource(
            new FakeTimeline(
                new TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    /* id= */ 1,
                    /* isSeekable= */ true,
                    /* isDynamic= */ false,
                    /* durationUs= */ 10 * C.MICROS_PER_SECOND))));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.setMediaSources(ImmutableList.of(secondMediaSource, secondMediaSource));
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(0);
    player.release();
  }

  @Test
  public void onPositionDiscontinuity_recursiveStateChange_mediaItemMaskingCorrect()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    MediaItem[] currentMediaItems = new MediaItem[2];
    int[] mediaItemCount = new int[2];
    player.addListener(
        new Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              mediaItemCount[0] = player.getMediaItemCount();
              currentMediaItems[0] = player.getCurrentMediaItem();
              // This is called before the second listener is called.
              player.removeMediaItem(/* index= */ 1);
            }
          }
        });
    player.addListener(
        new Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
              mediaItemCount[1] = player.getMediaItemCount();
              currentMediaItems[1] = player.getCurrentMediaItem();
            }
          }
        });
    player.addListener(listener);
    player.setMediaSources(
        ImmutableList.of(
            createFakeMediaSource(/* id= */ "id-0"), createFakeMediaSource(/* id= */ "id-1")));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<PositionInfo> newPositionArgumentCaptor =
        ArgumentCaptor.forClass(PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            any(),
            newPositionArgumentCaptor.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            any(), newPositionArgumentCaptor.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    // The state at auto-transition event time.
    assertThat(mediaItemCount[0]).isEqualTo(2);
    assertThat(currentMediaItems[0].localConfiguration.tag).isEqualTo("id-1");
    // The masked state after id-1 has been removed.
    assertThat(mediaItemCount[1]).isEqualTo(1);
    assertThat(currentMediaItems[1].localConfiguration.tag).isEqualTo("id-0");
    // PositionInfo reports the media item at event time.
    assertThat(newPositionArgumentCaptor.getAllValues().get(0).mediaItem.localConfiguration.tag)
        .isEqualTo("id-1");
    assertThat(newPositionArgumentCaptor.getAllValues().get(1).mediaItem.localConfiguration.tag)
        .isEqualTo("id-0");
    player.release();
  }

  @Test
  public void removeMediaItems_removesPlayingPeriod_callsOnPositionDiscontinuity()
      throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 10 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 8 * C.MICROS_PER_SECOND)))));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.removeMediaItem(/* index= */ 1);
    player.seekTo(/* positionMs= */ 0);
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 2 * C.MILLIS_PER_SECOND);
    // Removing the last item resets the position to 0 with an empty timeline.
    player.removeMediaItem(0);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onTimelineChanged(any(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(listener)
        .onTimelineChanged(any(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).positionMs).isIn(Range.closed(1980L, 2000L));
    assertThat(oldPositions.get(1).contentPositionMs).isIn(Range.closed(1980L, 2000L));
    assertThat(newPositions.get(1).windowUid).isNull();
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(0);
    player.release();
  }

  @Test
  public void
      concatenatingMediaSourceRemoveMediaSource_removesPlayingPeriod_callsOnPositionDiscontinuity()
          throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 10 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 8 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 6 * C.MICROS_PER_SECOND))));
    player.addMediaSource(concatenatingMediaSource);

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    concatenatingMediaSource.removeMediaSource(1);
    TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player);
    concatenatingMediaSource.removeMediaSource(1);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener, times(2))
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(0);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(0);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(0);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(0);
    player.release();
  }

  @Test
  public void
      concatenatingMediaSourceRemoveMediaSourceWithSeek_overridesRemoval_callsOnPositionDiscontinuity()
          throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    ConcatenatingMediaSource concatenatingMediaSource =
        new ConcatenatingMediaSource(
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 1,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 10 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 8 * C.MICROS_PER_SECOND))),
            new FakeMediaSource(
                new FakeTimeline(
                    new TimelineWindowDefinition(
                        /* periodCount= */ 1,
                        /* id= */ 2,
                        /* isSeekable= */ true,
                        /* isDynamic= */ false,
                        /* durationUs= */ 6 * C.MICROS_PER_SECOND))));
    player.addMediaSource(concatenatingMediaSource);

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 1, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    concatenatingMediaSource.removeMediaSource(1);
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 1234);
    TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player);
    concatenatingMediaSource.removeMediaSource(0);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    // SEEK overrides concatenating media source modification.
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    // This fails once out of a hundred test runs due to a race condition whether the seek or the
    // removal arrives first in EPI.
    // inOrder.verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(1234);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(1234);
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(1234);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(1234);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(1234);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(1234);
    player.release();
  }

  @Test
  public void setMediaItems_callsListenersWithSameInstanceOfMediaItem() throws Exception {
    ArgumentCaptor<Timeline> timeline = ArgumentCaptor.forClass(Timeline.class);
    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<MediaItem> currentMediaItem = ArgumentCaptor.forClass(MediaItem.class);
    Window window = new Timeline.Window();
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setMediaSourceFactory(new FakeMediaSourceFactory())
            .build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    List<MediaItem> playlist =
        ImmutableList.of(
            MediaItem.fromUri("http://item-0.com/"), MediaItem.fromUri("http://item-1.com/"));
    player.setMediaItems(playlist);

    player.prepare();
    player.seekTo(/* positionMs= */ 2000);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onTimelineChanged(timeline.capture(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(listener)
        .onMediaItemTransition(
            currentMediaItem.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(),
            newPosition.capture(),
            eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener)
        .onMediaItemTransition(
            currentMediaItem.capture(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO));
    inOrder.verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    inOrder.verify(listener, never()).onMediaItemTransition(any(), anyInt());
    assertThat(timeline.getValue().getWindow(0, window).mediaItem)
        .isSameInstanceAs(playlist.get(0));
    assertThat(timeline.getValue().getWindow(1, window).mediaItem)
        .isSameInstanceAs(playlist.get(1));
    assertThat(oldPosition.getAllValues().get(0).mediaItem).isSameInstanceAs(playlist.get(0));
    assertThat(newPosition.getAllValues().get(0).mediaItem).isSameInstanceAs(playlist.get(0));
    assertThat(oldPosition.getAllValues().get(1).mediaItem).isSameInstanceAs(playlist.get(0));
    assertThat(newPosition.getAllValues().get(1).mediaItem).isSameInstanceAs(playlist.get(1));
    assertThat(currentMediaItem.getAllValues().get(0)).isSameInstanceAs(playlist.get(0));
    assertThat(currentMediaItem.getAllValues().get(1)).isSameInstanceAs(playlist.get(1));
    player.release();
  }

  @Test
  public void seekTo_callsOnPositionDiscontinuity() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSources(
        ImmutableList.of(
            createFakeMediaSource(/* id= */ "id-0"), createFakeMediaSource(/* id= */ "id-1")));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.seekTo(/* positionMs= */ 7 * C.MILLIS_PER_SECOND);
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ C.MILLIS_PER_SECOND);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), not(eq(Player.DISCONTINUITY_REASON_SEEK)));
    verify(listener, times(2))
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).windowUid).isEqualTo(newPositions.get(0).windowUid);
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).mediaItem.localConfiguration.tag).isEqualTo("id-0");
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).mediaItem.localConfiguration.tag).isEqualTo("id-0");
    assertThat(newPositions.get(0).positionMs).isEqualTo(7_000);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(7_000);
    assertThat(oldPositions.get(1).windowUid).isNotEqualTo(newPositions.get(1).windowUid);
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).mediaItem.localConfiguration.tag).isEqualTo("id-0");
    assertThat(oldPositions.get(1).positionMs).isEqualTo(7_000);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(7_000);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(1);
    assertThat(newPositions.get(1).mediaItem.localConfiguration.tag).isEqualTo("id-1");
    assertThat(newPositions.get(1).positionMs).isEqualTo(1_000);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(1_000);
    player.release();
  }

  @Test
  public void seekTo_whenTimelineEmpty_callsOnPositionDiscontinuity() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.seekTo(/* positionMs= */ 7 * C.MILLIS_PER_SECOND);
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ C.MILLIS_PER_SECOND);
    player.seekTo(/* positionMs= */ 5 * C.MILLIS_PER_SECOND);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), not(eq(Player.DISCONTINUITY_REASON_SEEK)));
    verify(listener, times(3))
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    // a seek from initial state to masked seek position
    assertThat(oldPositions.get(0).windowUid).isNull();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).mediaItem).isNull();
    assertThat(oldPositions.get(0).positionMs).isEqualTo(0);
    assertThat(oldPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).windowUid).isNull();
    assertThat(newPositions.get(0).positionMs).isEqualTo(7_000);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(7_000);
    // a seek from masked seek position to another masked position across windows
    assertThat(oldPositions.get(1).windowUid).isNull();
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(1).mediaItem).isNull();
    assertThat(oldPositions.get(1).positionMs).isEqualTo(7_000);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(7_000);
    assertThat(newPositions.get(1).windowUid).isNull();
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(1);
    assertThat(newPositions.get(1).positionMs).isEqualTo(1_000);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(1_000);
    // a seek from masked seek position to another masked position within media item
    assertThat(oldPositions.get(2).windowUid).isNull();
    assertThat(oldPositions.get(2).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(2).mediaItem).isNull();
    assertThat(oldPositions.get(2).positionMs).isEqualTo(1_000);
    assertThat(oldPositions.get(2).contentPositionMs).isEqualTo(1_000);
    assertThat(newPositions.get(2).windowUid).isNull();
    assertThat(newPositions.get(2).mediaItemIndex).isEqualTo(1);
    assertThat(newPositions.get(2).positionMs).isEqualTo(5_000);
    assertThat(newPositions.get(2).contentPositionMs).isEqualTo(5_000);
    player.release();
  }

  @Test
  public void seekBack_callsOnPositionDiscontinuity() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ Util.msToUs(3 * C.DEFAULT_SEEK_BACK_INCREMENT_MS)));
    player.setMediaSource(new FakeMediaSource(fakeTimeline));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 2 * C.DEFAULT_SEEK_BACK_INCREMENT_MS);
    player.seekBack();

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), not(eq(Player.DISCONTINUITY_REASON_SEEK)));
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).positionMs)
        .isIn(
            Range.closed(
                2 * C.DEFAULT_SEEK_BACK_INCREMENT_MS - 20, 2 * C.DEFAULT_SEEK_BACK_INCREMENT_MS));
    assertThat(oldPositions.get(0).contentPositionMs)
        .isIn(
            Range.closed(
                2 * C.DEFAULT_SEEK_BACK_INCREMENT_MS - 20, 2 * C.DEFAULT_SEEK_BACK_INCREMENT_MS));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs)
        .isIn(
            Range.closed(C.DEFAULT_SEEK_BACK_INCREMENT_MS - 20, C.DEFAULT_SEEK_BACK_INCREMENT_MS));
    assertThat(newPositions.get(0).contentPositionMs)
        .isIn(
            Range.closed(C.DEFAULT_SEEK_BACK_INCREMENT_MS - 20, C.DEFAULT_SEEK_BACK_INCREMENT_MS));

    player.release();
  }

  @Test
  public void seekBack_pastZero_seeksToZero() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ Util.msToUs(C.DEFAULT_SEEK_BACK_INCREMENT_MS)));
    player.setMediaSource(new FakeMediaSource(fakeTimeline));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ C.DEFAULT_SEEK_BACK_INCREMENT_MS / 2);
    player.seekBack();

    assertThat(player.getCurrentPosition()).isEqualTo(0);

    player.release();
  }

  @Test
  public void seekForward_callsOnPositionDiscontinuity() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ Util.msToUs(2 * C.DEFAULT_SEEK_FORWARD_INCREMENT_MS)));
    player.setMediaSource(new FakeMediaSource(fakeTimeline));

    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    player.seekForward();

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), not(eq(Player.DISCONTINUITY_REASON_SEEK)));
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).positionMs).isEqualTo(0);
    assertThat(oldPositions.get(0).contentPositionMs).isEqualTo(0);
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).positionMs).isEqualTo(C.DEFAULT_SEEK_FORWARD_INCREMENT_MS);
    assertThat(newPositions.get(0).contentPositionMs)
        .isEqualTo(C.DEFAULT_SEEK_FORWARD_INCREMENT_MS);

    player.release();
  }

  @Test
  public void seekForward_pastDuration_seeksToDuration() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Timeline fakeTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ Util.msToUs(C.DEFAULT_SEEK_FORWARD_INCREMENT_MS / 2)));
    player.setMediaSource(new FakeMediaSource(fakeTimeline));

    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    player.seekForward();

    assertThat(player.getCurrentPosition()).isEqualTo(C.DEFAULT_SEEK_FORWARD_INCREMENT_MS / 2 - 1);

    player.release();
  }

  @Test
  public void seekToPrevious_withPreviousWindowAndCloseToStart_seeksToPreviousWindow() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));
    player.seekTo(/* mediaItemIndex= */ 1, C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS);

    player.seekToPrevious();

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(0);
    assertThat(player.getCurrentPosition()).isEqualTo(0);

    player.release();
  }

  @Test
  public void seekToPrevious_notCloseToStart_seeksToZero() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));
    player.seekTo(/* mediaItemIndex= */ 1, C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS + 1);

    player.seekToPrevious();

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
    assertThat(player.getCurrentPosition()).isEqualTo(0);

    player.release();
  }

  @Test
  public void seekToNext_withNextWindow_seeksToNextWindow() {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.addMediaSources(ImmutableList.of(new FakeMediaSource(), new FakeMediaSource()));

    player.seekToNext();

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
    assertThat(player.getCurrentPosition()).isEqualTo(0);

    player.release();
  }

  @Test
  public void seekToNext_liveWindowWithoutNextWindow_seeksToLiveEdge() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Timeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* isLive= */ true,
                /* isPlaceholder= */ false,
                /* durationUs= */ 1_000_000,
                /* defaultPositionUs= */ 500_000,
                TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                AdPlaybackState.NONE));
    MediaSource mediaSource = new FakeMediaSource(timeline);
    player.setMediaSource(mediaSource);

    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    player.seekTo(/* positionMs = */ 0);
    player.seekToNext();

    assertThat(player.getCurrentMediaItemIndex()).isEqualTo(0);
    assertThat(player.getCurrentPosition()).isEqualTo(500);

    player.release();
  }

  @Test
  public void stop_doesNotCallOnPositionDiscontinuity() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(new FakeMediaSource());

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.stop();

    verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    player.release();
  }

  // Tests deprecated stop(boolean reset)
  @SuppressWarnings("deprecation")
  @Test
  public void stop_withResetRemovesPlayingPeriod_callsOnPositionDiscontinuity() throws Exception {
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(createFakeMediaSource(/* id= */ 123));

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.stop(/* reset= */ true);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    verify(listener, never())
        .onPositionDiscontinuity(any(), any(), not(eq(Player.DISCONTINUITY_REASON_REMOVE)));
    verify(listener)
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_REMOVE));
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(oldPositions.get(0).mediaItem.localConfiguration.tag).isEqualTo(123);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(4980L, 5000L));
    assertThat(newPositions.get(0).windowUid).isNull();
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(0).mediaItem).isNull();
    assertThat(newPositions.get(0).positionMs).isEqualTo(0);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(0);
    player.release();
  }

  @Test
  public void seekTo_cancelsSourceDiscontinuity_callsOnPositionDiscontinuity() throws Exception {
    Timeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 2));
    final Timeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1),
            new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 3));
    final FakeMediaSource mediaSource =
        new FakeMediaSource(timeline1, ExoPlayerTestRunner.VIDEO_FORMAT);
    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(mediaSource);

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 1, /* positionMs= */ 2000);
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 2122);
    // This causes a DISCONTINUITY_REASON_REMOVE between pending operations that needs to be
    // cancelled by the seek below.
    mediaSource.setNewSourceInfo(timeline2);
    player.play();
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 2222);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    ArgumentCaptor<Player.PositionInfo> oldPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    ArgumentCaptor<Player.PositionInfo> newPosition =
        ArgumentCaptor.forClass(Player.PositionInfo.class);
    InOrder inOrder = inOrder(listener);
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder
        .verify(listener, times(2))
        .onPositionDiscontinuity(
            oldPosition.capture(), newPosition.capture(), eq(Player.DISCONTINUITY_REASON_SEEK));
    inOrder
        .verify(listener)
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_AUTO_TRANSITION));
    inOrder.verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    List<Player.PositionInfo> oldPositions = oldPosition.getAllValues();
    List<Player.PositionInfo> newPositions = newPosition.getAllValues();
    // First seek
    assertThat(oldPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(0).positionMs).isIn(Range.closed(1980L, 2000L));
    assertThat(oldPositions.get(0).contentPositionMs).isIn(Range.closed(1980L, 2000L));
    assertThat(newPositions.get(0).mediaItemIndex).isEqualTo(1);
    assertThat(newPositions.get(0).positionMs).isEqualTo(2122);
    assertThat(newPositions.get(0).contentPositionMs).isEqualTo(2122);
    // Second seek.
    assertThat(oldPositions.get(1).mediaItemIndex).isEqualTo(1);
    assertThat(oldPositions.get(1).positionMs).isEqualTo(2122);
    assertThat(oldPositions.get(1).contentPositionMs).isEqualTo(2122);
    assertThat(newPositions.get(1).mediaItemIndex).isEqualTo(0);
    assertThat(newPositions.get(1).positionMs).isEqualTo(2222);
    assertThat(newPositions.get(1).contentPositionMs).isEqualTo(2222);
    player.release();
  }

  @Test
  public void newServerSideInsertedAdAtPlaybackPosition_keepsRenderersEnabled() throws Exception {
    // Injecting renderer to count number of renderer resets.
    AtomicReference<FakeVideoRenderer> videoRenderer = new AtomicReference<>();
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setRenderersFactory(
                (handler, videoListener, audioListener, textOutput, metadataOutput) -> {
                  videoRenderer.set(
                      new FakeVideoRenderer(
                          SystemClock.DEFAULT.createHandler(
                              handler.getLooper(), /* callback= */ null),
                          videoListener));
                  return new Renderer[] {videoRenderer.get()};
                })
            .build();
    // Live stream timeline with unassigned next ad group.
    AdPlaybackState initialAdPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(), /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(new long[][] {new long[] {10 * C.MICROS_PER_SECOND}});
    // Updated timeline with ad group at 18 seconds.
    long firstSampleTimeUs = TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
    Timeline initialTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ C.TIME_UNSET,
                initialAdPlaybackState));
    AdPlaybackState updatedAdPlaybackState =
        initialAdPlaybackState.withAdGroupTimeUs(
            /* adGroupIndex= */ 0,
            /* adGroupTimeUs= */ firstSampleTimeUs + 18 * C.MICROS_PER_SECOND);
    Timeline updatedTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ 0,
                /* isSeekable= */ true,
                /* isDynamic= */ true,
                /* durationUs= */ C.TIME_UNSET,
                updatedAdPlaybackState));
    // Add samples to allow player to load and start playing (but no EOS as this is a live stream).
    FakeMediaSource mediaSource =
        new FakeMediaSource(
            initialTimeline,
            DrmSessionManager.DRM_UNSUPPORTED,
            (format, mediaPeriodId) ->
                ImmutableList.of(
                    oneByteSample(firstSampleTimeUs, C.BUFFER_FLAG_KEY_FRAME),
                    oneByteSample(firstSampleTimeUs + 40 * C.MICROS_PER_SECOND)),
            ExoPlayerTestRunner.VIDEO_FORMAT);

    // Set updated ad group once we reach 20 seconds, and then continue playing until 40 seconds.
    player
        .createMessage((message, payload) -> mediaSource.setNewSourceInfo(updatedTimeline))
        .setPosition(20_000)
        .send();
    player.setMediaSource(mediaSource);
    player.prepare();
    playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 40_000);
    player.release();

    // Assert that the renderer hasn't been reset despite the inserted ad group.
    assertThat(videoRenderer.get().positionResetCount).isEqualTo(1);
  }

  @Test
  public void setMediaItem_withMediaMetadata_updatesMediaMetadata() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("the title").build();

    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    player.setMediaItem(
        new MediaItem.Builder()
            .setMediaId("id")
            .setUri(Uri.EMPTY)
            .setMediaMetadata(mediaMetadata)
            .build());

    assertThat(player.getMediaMetadata()).isEqualTo(mediaMetadata);
  }

  @Test
  public void playingMedia_withNoMetadata_doesNotUpdateMediaMetadata() throws Exception {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("the title").build();

    ExoPlayer player = new TestExoPlayerBuilder(context).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("id")
            .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
            .setMediaMetadata(mediaMetadata)
            .build();
    player.setMediaItem(mediaItem);

    assertThat(player.getMediaMetadata()).isEqualTo(mediaMetadata);

    player.prepare();
    TestPlayerRunHelper.playUntilPosition(
        player, /* mediaItemIndex= */ 0, /* positionMs= */ 5 * C.MILLIS_PER_SECOND);
    player.stop();

    shadowOf(Looper.getMainLooper()).idle();

    assertThat(player.getMediaMetadata()).isEqualTo(mediaMetadata);
  }

  @Test
  @Config(sdk = Config.ALL_SDKS)
  public void builder_inBackgroundThreadWithAllowedAnyThreadMethods_doesNotThrow()
      throws Exception {
    Thread builderThread =
        new Thread(
            () -> {
              ExoPlayer player =
                  new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
              player.addListener(new Listener() {});
              player.addAnalyticsListener(new AnalyticsListener() {});
              player.addAudioOffloadListener(new ExoPlayer.AudioOffloadListener() {});
              player.getClock();
              player.getApplicationLooper();
              player.getPlaybackLooper();
            });
    AtomicReference<Throwable> builderThrow = new AtomicReference<>();
    builderThread.setUncaughtExceptionHandler((thread, throwable) -> builderThrow.set(throwable));

    builderThread.start();
    builderThread.join();

    assertThat(builderThrow.get()).isNull();
  }

  @Test
  public void onPlaylistMetadataChanged_calledWhenPlaylistMetadataSet() {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener playerListener = mock(Player.Listener.class);
    player.addListener(playerListener);
    AnalyticsListener analyticsListener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(analyticsListener);

    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("test").build();
    player.setPlaylistMetadata(mediaMetadata);

    verify(playerListener).onPlaylistMetadataChanged(mediaMetadata);
    verify(analyticsListener).onPlaylistMetadataChanged(any(), eq(mediaMetadata));
  }

  @Test
  public void release_triggersAllPendingEventsInAnalyticsListeners() throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setRenderersFactory(
                (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                    new Renderer[] {
                      new FakeVideoRenderer(
                          SystemClock.DEFAULT.createHandler(
                              handler.getLooper(), /* callback= */ null),
                          videoListener)
                    })
            .build();
    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    // Do something that requires clean-up callbacks like decoder disabling.
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener).onVideoDisabled(any(), any());
    verify(listener).onPlayerReleased(any());
  }

  @Test
  public void releaseAfterRendererEvents_triggersPendingVideoEventsInListener() throws Exception {
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setRenderersFactory(
                (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                    new Renderer[] {
                      new FakeVideoRenderer(
                          SystemClock.DEFAULT.createHandler(
                              handler.getLooper(), /* callback= */ null),
                          videoListener)
                    })
            .build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT));
    player.setVideoSurface(surface);
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.release();
    surface.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener, atLeastOnce()).onEvents(any(), any()); // EventListener
    verify(listener).onRenderedFirstFrame(); // VideoListener
  }

  @Test
  public void releaseAfterVolumeChanges_triggerPendingVolumeEventInListener() throws Exception {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.setVolume(0F);
    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener).onVolumeChanged(anyFloat());
  }

  @Test
  public void releaseAfterVolumeChanges_triggerPendingDeviceVolumeEventsInListener() {
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    int deviceVolume = player.getDeviceVolume();
    try {
      player.setDeviceVolume(deviceVolume + 1); // No-op if at max volume.
      player.setDeviceVolume(deviceVolume - 1); // No-op if at min volume.
    } finally {
      player.setDeviceVolume(deviceVolume); // Restore original volume.
    }

    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener, atLeast(2)).onDeviceVolumeChanged(anyInt(), anyBoolean());
  }

  @Test
  public void loadControlBackBuffer_withInsufficientMemoryLimits_stillContinuesPlayback()
      throws Exception {
    DefaultLoadControl loadControl =
        new DefaultLoadControl.Builder()
            .setTargetBufferBytes(500_000)
            .setBackBuffer(
                /* backBufferDurationMs= */ 1_000_000, /* retainBackBufferFromKeyframe= */ true)
            .build();

    ExoPlayer player = new TestExoPlayerBuilder(context).setLoadControl(loadControl).build();
    player.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);

    // Assert that playing works without getting stuck due to the memory used by the back buffer.
  }

  // Internal methods.

  private static ActionSchedule.Builder addSurfaceSwitch(ActionSchedule.Builder builder) {
    final Surface surface1 = new Surface(new SurfaceTexture(/* texName= */ 0));
    final Surface surface2 = new Surface(new SurfaceTexture(/* texName= */ 1));
    return builder
        .executeRunnable(
            new PlayerRunnable() {
              @Override
              public void run(ExoPlayer player) {
                player.setVideoSurface(surface1);
              }
            })
        .executeRunnable(
            new PlayerRunnable() {
              @Override
              public void run(ExoPlayer player) {
                player.setVideoSurface(surface2);
              }
            });
  }

  private static FakeMediaSource createFakeMediaSource(Object id) {
    return new FakeMediaSource(
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, id)));
  }

  private static void deliverBroadcast(Intent intent) {
    ApplicationProvider.getApplicationContext().sendBroadcast(intent);
    shadowOf(Looper.getMainLooper()).idle();
  }

  private static boolean containsEvent(List<Player.Events> eventsList, @Player.Event int event) {
    for (Player.Events events : eventsList) {
      if (events.contains(event)) {
        return true;
      }
    }
    return false;
  }

  private static Player.Commands createWithDefaultCommands(
      boolean isTimelineEmpty, @Player.Command int... additionalCommands) {
    Player.Commands.Builder builder = new Player.Commands.Builder();
    builder.addAll(
        COMMAND_PLAY_PAUSE,
        COMMAND_PREPARE,
        COMMAND_STOP,
        COMMAND_SEEK_TO_DEFAULT_POSITION,
        COMMAND_SEEK_TO_MEDIA_ITEM,
        COMMAND_SET_SPEED_AND_PITCH,
        COMMAND_SET_SHUFFLE_MODE,
        COMMAND_SET_REPEAT_MODE,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_MEDIA_ITEMS_METADATA,
        COMMAND_SET_MEDIA_ITEMS_METADATA,
        COMMAND_CHANGE_MEDIA_ITEMS,
        COMMAND_SET_MEDIA_ITEM,
        COMMAND_GET_AUDIO_ATTRIBUTES,
        COMMAND_GET_VOLUME,
        COMMAND_GET_DEVICE_VOLUME,
        COMMAND_SET_VOLUME,
        COMMAND_SET_DEVICE_VOLUME,
        COMMAND_ADJUST_DEVICE_VOLUME,
        COMMAND_SET_VIDEO_SURFACE,
        COMMAND_GET_TEXT,
        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        COMMAND_GET_TRACKS);
    if (!isTimelineEmpty) {
      builder.add(COMMAND_SEEK_TO_PREVIOUS);
    }
    builder.addAll(additionalCommands);
    return builder.build();
  }

  private static Player.Commands createWithDefaultCommands(
      @Player.Command int... additionalCommands) {
    return createWithDefaultCommands(/* isTimelineEmpty= */ false, additionalCommands);
  }

  // Internal classes.

  /** {@link FakeRenderer} that can sleep and be woken-up. */
  private static class FakeSleepRenderer extends FakeRenderer {
    private final AtomicBoolean sleepOnNextRender;
    private final AtomicReference<Renderer.WakeupListener> wakeupListenerReceiver;

    public FakeSleepRenderer(int trackType) {
      super(trackType);
      sleepOnNextRender = new AtomicBoolean(false);
      wakeupListenerReceiver = new AtomicReference<>();
    }

    public void wakeup() {
      wakeupListenerReceiver.get().onWakeup();
    }

    /** Call {@link Renderer.WakeupListener#onSleep()} on the next {@link #render(long, long)} */
    @CanIgnoreReturnValue
    public FakeSleepRenderer sleepOnNextRender() {
      sleepOnNextRender.set(true);
      return this;
    }

    @Override
    public void handleMessage(@MessageType int messageType, @Nullable Object message)
        throws ExoPlaybackException {
      if (messageType == MSG_SET_WAKEUP_LISTENER) {
        assertThat(message).isNotNull();
        wakeupListenerReceiver.set((WakeupListener) message);
      }
      super.handleMessage(messageType, message);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
      super.render(positionUs, elapsedRealtimeUs);
      if (sleepOnNextRender.compareAndSet(/* expectedValue= */ true, /* newValue= */ false)) {
        wakeupListenerReceiver.get().onSleep();
      }
    }
  }

  private static final class CountingMessageTarget implements PlayerMessage.Target {

    public int messageCount;

    @Override
    public void handleMessage(@Renderer.MessageType int messageType, @Nullable Object message) {
      messageCount++;
    }
  }

  private static final class PositionGrabbingMessageTarget extends PlayerTarget {

    public int mediaItemIndex;
    public long positionMs;
    public int messageCount;

    public PositionGrabbingMessageTarget() {
      mediaItemIndex = C.INDEX_UNSET;
      positionMs = C.TIME_UNSET;
    }

    @Override
    public void handleMessage(ExoPlayer player, int messageType, @Nullable Object message) {
      mediaItemIndex = player.getCurrentMediaItemIndex();
      positionMs = player.getCurrentPosition();
      messageCount++;
    }
  }

  /**
   * Provides a wrapper for a {@link Runnable} which does collect playback states and window counts.
   * Can be used with {@link ActionSchedule.Builder#executeRunnable(Runnable)} to verify that a
   * playback state did not change and hence no observable callback is called.
   *
   * <p>This is specifically useful in cases when the test may end before a given state arrives or
   * when an action of the action schedule might execute before a callback is called.
   */
  public static class PlaybackStateCollector extends PlayerRunnable {

    private final int[] playbackStates;
    private final int[] timelineWindowCount;
    private final int index;

    /**
     * Creates the collector.
     *
     * @param index The index to populate.
     * @param playbackStates An array of playback states to populate.
     * @param timelineWindowCount An array of window counts to populate.
     */
    public PlaybackStateCollector(int index, int[] playbackStates, int[] timelineWindowCount) {
      Assertions.checkArgument(playbackStates.length > index && timelineWindowCount.length > index);
      this.playbackStates = playbackStates;
      this.timelineWindowCount = timelineWindowCount;
      this.index = index;
    }

    @Override
    public void run(ExoPlayer player) {
      playbackStates[index] = player.getPlaybackState();
      timelineWindowCount[index] = player.getCurrentTimeline().getWindowCount();
    }
  }

  private static final class FakeLoaderCallback implements Loader.Callback<Loader.Loadable> {
    @Override
    public void onLoadCompleted(
        Loader.Loadable loadable, long elapsedRealtimeMs, long loadDurationMs) {}

    @Override
    public void onLoadCanceled(
        Loader.Loadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {}

    @Override
    public Loader.LoadErrorAction onLoadError(
        Loader.Loadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return Loader.RETRY;
    }
  }

  private static final class AudioClockRendererWithoutSpeedChangeSupport
      extends FakeMediaClockRenderer {

    private PlaybackParameters playbackParameters;
    private boolean delayingPlaybackParameterReset;
    private long positionUs;

    public AudioClockRendererWithoutSpeedChangeSupport() {
      super(C.TRACK_TYPE_AUDIO);
      playbackParameters = PlaybackParameters.DEFAULT;
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      super.onPositionReset(positionUs, joining);
      this.positionUs = positionUs;
    }

    @Override
    public long getPositionUs() {
      return positionUs;
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
      this.playbackParameters = playbackParameters;
      // Similar to a real renderer, the missing speed support is only detected with a delay.
      delayingPlaybackParameterReset = true;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      if (delayingPlaybackParameterReset) {
        delayingPlaybackParameterReset = false;
        return playbackParameters;
      }
      return PlaybackParameters.DEFAULT;
    }
  }

  /**
   * Returns an argument matcher for {@link Timeline} instances that ignores period and window uids.
   */
  private static ArgumentMatcher<Timeline> noUid(Timeline timeline) {
    return argument -> timelinesAreSame(argument, timeline);
  }
}
