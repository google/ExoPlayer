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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertFalse;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Helper class to run an ExoPlayer test. */
public final class ExoPlayerTestRunner implements Player.EventListener, ActionSchedule.Callback {

  /** A generic video {@link Format} which can be used to set up a {@link FakeMediaSource}. */
  public static final Format VIDEO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setAverageBitrate(800_000)
          .setWidth(1280)
          .setHeight(720)
          .build();

  /** A generic audio {@link Format} which can be used to set up a {@link FakeMediaSource}. */
  public static final Format AUDIO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setAverageBitrate(100_000)
          .setChannelCount(2)
          .setSampleRate(44100)
          .build();

  /**
   * Builder to set-up a {@link ExoPlayerTestRunner}. Default fake implementations will be used for
   * unset test properties.
   */
  public static final class Builder {
    private final TestExoPlayer.Builder testPlayerBuilder;
    private Timeline timeline;
    private List<MediaSource> mediaSources;
    private Format[] supportedFormats;
    private Object manifest;
    private ActionSchedule actionSchedule;
    private Player.EventListener eventListener;
    private AnalyticsListener analyticsListener;
    private Integer expectedPlayerEndedCount;
    private boolean pauseAtEndOfMediaItems;
    private int initialWindowIndex;
    private long initialPositionMs;
    private boolean skipSettingMediaSources;

    public Builder(Context context) {
      testPlayerBuilder = new TestExoPlayer.Builder(context);
      mediaSources = new ArrayList<>();
      supportedFormats = new Format[] {VIDEO_FORMAT};
      initialWindowIndex = C.INDEX_UNSET;
      initialPositionMs = C.TIME_UNSET;
    }

    /**
     * Sets a {@link Timeline} to be used by a {@link FakeMediaSource} in the test runner. The
     * default value is a seekable, non-dynamic {@link FakeTimeline} with a duration of {@link
     * FakeTimeline.TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US}. Setting the timeline is
     * not allowed after a call to {@link #setMediaSources(MediaSource...)} or {@link
     * #skipSettingMediaSources()}.
     *
     * @param timeline A {@link Timeline} to be used by a {@link FakeMediaSource} in the test
     *     runner.
     * @return This builder.
     */
    public Builder setTimeline(Timeline timeline) {
      assertThat(mediaSources).isEmpty();
      assertFalse(skipSettingMediaSources);
      this.timeline = timeline;
      return this;
    }

    /**
     * Sets a manifest to be used by a {@link FakeMediaSource} in the test runner. The default value
     * is null. Setting the manifest is not allowed after a call to {@link
     * #setMediaSources(MediaSource...)} or {@link #skipSettingMediaSources()}.
     *
     * @param manifest A manifest to be used by a {@link FakeMediaSource} in the test runner.
     * @return This builder.
     */
    public Builder setManifest(Object manifest) {
      assertThat(mediaSources).isEmpty();
      assertFalse(skipSettingMediaSources);
      this.manifest = manifest;
      return this;
    }

    /**
     * Seeks before setting the media sources and preparing the player.
     *
     * @param windowIndex The window index to seek to.
     * @param positionMs The position in milliseconds to seek to.
     * @return This builder.
     */
    public Builder initialSeek(int windowIndex, long positionMs) {
      this.initialWindowIndex = windowIndex;
      this.initialPositionMs = positionMs;
      return this;
    }

    /**
     * Sets the {@link MediaSource}s to be used by the test runner. The default value is a {@link
     * FakeMediaSource} with the timeline and manifest provided by {@link #setTimeline(Timeline)}
     * and {@link #setManifest(Object)}. Setting media sources is not allowed after calls to {@link
     * #skipSettingMediaSources()}, {@link #setTimeline(Timeline)} and/or {@link
     * #setManifest(Object)}.
     *
     * @param mediaSources The {@link MediaSource}s to be used by the test runner.
     * @return This builder.
     */
    public Builder setMediaSources(MediaSource... mediaSources) {
      assertThat(timeline).isNull();
      assertThat(manifest).isNull();
      assertFalse(skipSettingMediaSources);
      this.mediaSources = Arrays.asList(mediaSources);
      return this;
    }

    /**
     * Sets a list of {@link Format}s to be used by a {@link FakeMediaSource} to create media
     * periods. The default value is a single {@link #VIDEO_FORMAT}. Note that this parameter
     * doesn't have any influence if a media source with {@link #setMediaSources(MediaSource...)} is
     * set.
     *
     * @param supportedFormats A list of supported {@link Format}s.
     * @return This builder.
     */
    public Builder setSupportedFormats(Format... supportedFormats) {
      this.supportedFormats = supportedFormats;
      return this;
    }

    /**
     * Skips calling {@link com.google.android.exoplayer2.ExoPlayer#setMediaSources(List)} before
     * preparing. Calling this method is not allowed after calls to {@link
     * #setMediaSources(MediaSource...)}, {@link #setTimeline(Timeline)} and/or {@link
     * #setManifest(Object)}.
     *
     * @return This builder.
     */
    public Builder skipSettingMediaSources() {
      assertThat(timeline).isNull();
      assertThat(manifest).isNull();
      assertThat(mediaSources).isEmpty();
      skipSettingMediaSources = true;
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setUseLazyPreparation(boolean)
     * @return This builder.
     */
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      testPlayerBuilder.setUseLazyPreparation(useLazyPreparation);
      return this;
    }

    /**
     * Sets whether to enable pausing at the end of media items.
     *
     * @param pauseAtEndOfMediaItems Whether to pause at the end of media items.
     * @return This builder.
     */
    public Builder setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
      this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setTrackSelector(DefaultTrackSelector)
     * @return This builder.
     */
    public Builder setTrackSelector(DefaultTrackSelector trackSelector) {
      testPlayerBuilder.setTrackSelector(trackSelector);
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setLoadControl(LoadControl)
     * @return This builder.
     */
    public Builder setLoadControl(LoadControl loadControl) {
      testPlayerBuilder.setLoadControl(loadControl);
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setBandwidthMeter(BandwidthMeter)
     * @return This builder.
     */
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      this.testPlayerBuilder.setBandwidthMeter(bandwidthMeter);
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setRenderers(Renderer...)
     * @return This builder.
     */
    public Builder setRenderers(Renderer... renderers) {
      testPlayerBuilder.setRenderers(renderers);
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setRenderersFactory(RenderersFactory)
     * @return This builder.
     */
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      testPlayerBuilder.setRenderersFactory(renderersFactory);
      return this;
    }

    /**
     * @see TestExoPlayer.Builder#setClock(Clock)
     * @return This builder.
     */
    public Builder setClock(Clock clock) {
      testPlayerBuilder.setClock(clock);
      return this;
    }

    /**
     * Sets an {@link ActionSchedule} to be run by the test runner. The first action will be
     * executed immediately before {@link SimpleExoPlayer#prepare()}.
     *
     * @param actionSchedule An {@link ActionSchedule} to be used by the test runner.
     * @return This builder.
     */
    public Builder setActionSchedule(ActionSchedule actionSchedule) {
      this.actionSchedule = actionSchedule;
      return this;
    }

    /**
     * Sets an {@link Player.EventListener} to be registered to listen to player events.
     *
     * @param eventListener A {@link Player.EventListener} to be registered by the test runner to
     *     listen to player events.
     * @return This builder.
     */
    public Builder setEventListener(Player.EventListener eventListener) {
      this.eventListener = eventListener;
      return this;
    }

    /**
     * Sets an {@link AnalyticsListener} to be registered.
     *
     * @param analyticsListener An {@link AnalyticsListener} to be registered.
     * @return This builder.
     */
    public Builder setAnalyticsListener(AnalyticsListener analyticsListener) {
      this.analyticsListener = analyticsListener;
      return this;
    }

    /**
     * Sets the number of times the test runner is expected to reach the {@link Player#STATE_ENDED}
     * or {@link Player#STATE_IDLE}. The default is 1. This affects how long
     * {@link ExoPlayerTestRunner#blockUntilEnded(long)} waits.
     *
     * @param expectedPlayerEndedCount The number of times the player is expected to reach the ended
     *     or idle state.
     * @return This builder.
     */
    public Builder setExpectedPlayerEndedCount(int expectedPlayerEndedCount) {
      this.expectedPlayerEndedCount = expectedPlayerEndedCount;
      return this;
    }

    /**
     * Builds an {@link ExoPlayerTestRunner} using the provided values or their defaults.
     *
     * @return The built {@link ExoPlayerTestRunner}.
     */
    public ExoPlayerTestRunner build() {
      if (mediaSources.isEmpty() && !skipSettingMediaSources) {
        if (timeline == null) {
          timeline = new FakeTimeline(/* windowCount= */ 1, manifest);
        }
        mediaSources.add(new FakeMediaSource(timeline, supportedFormats));
      }
      if (expectedPlayerEndedCount == null) {
        expectedPlayerEndedCount = 1;
      }
      return new ExoPlayerTestRunner(
          testPlayerBuilder,
          mediaSources,
          skipSettingMediaSources,
          initialWindowIndex,
          initialPositionMs,
          actionSchedule,
          eventListener,
          analyticsListener,
          expectedPlayerEndedCount,
          pauseAtEndOfMediaItems);
    }
  }

  private final TestExoPlayer.Builder playerBuilder;
  private final List<MediaSource> mediaSources;
  private final boolean skipSettingMediaSources;
  private final int initialWindowIndex;
  private final long initialPositionMs;
  @Nullable private final ActionSchedule actionSchedule;
  @Nullable private final Player.EventListener eventListener;
  @Nullable private final AnalyticsListener analyticsListener;

  private final HandlerThread playerThread;
  private final HandlerWrapper handler;
  private final CountDownLatch endedCountDownLatch;
  private final CountDownLatch actionScheduleFinishedCountDownLatch;
  private final ArrayList<Timeline> timelines;
  private final ArrayList<Integer> timelineChangeReasons;
  private final ArrayList<Integer> periodIndices;
  private final ArrayList<Integer> discontinuityReasons;
  private final ArrayList<Integer> playbackStates;
  private final boolean pauseAtEndOfMediaItems;

  private SimpleExoPlayer player;
  private Exception exception;
  private TrackGroupArray trackGroups;
  private boolean playerWasPrepared;

  private ExoPlayerTestRunner(
      TestExoPlayer.Builder playerBuilder,
      List<MediaSource> mediaSources,
      boolean skipSettingMediaSources,
      int initialWindowIndex,
      long initialPositionMs,
      @Nullable ActionSchedule actionSchedule,
      @Nullable Player.EventListener eventListener,
      @Nullable AnalyticsListener analyticsListener,
      int expectedPlayerEndedCount,
      boolean pauseAtEndOfMediaItems) {
    this.playerBuilder = playerBuilder;
    this.mediaSources = mediaSources;
    this.skipSettingMediaSources = skipSettingMediaSources;
    this.initialWindowIndex = initialWindowIndex;
    this.initialPositionMs = initialPositionMs;
    this.actionSchedule = actionSchedule;
    this.eventListener = eventListener;
    this.analyticsListener = analyticsListener;
    timelines = new ArrayList<>();
    timelineChangeReasons = new ArrayList<>();
    periodIndices = new ArrayList<>();
    discontinuityReasons = new ArrayList<>();
    playbackStates = new ArrayList<>();
    endedCountDownLatch = new CountDownLatch(expectedPlayerEndedCount);
    actionScheduleFinishedCountDownLatch = new CountDownLatch(actionSchedule != null ? 1 : 0);
    playerThread = new HandlerThread("ExoPlayerTest thread");
    playerThread.start();
    handler =
        playerBuilder.getClock().createHandler(playerThread.getLooper(), /* callback= */ null);
    this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
  }

  // Called on the test thread to run the test.

  /**
   * Starts the test runner on its own thread. This will trigger the creation of the player, the
   * listener registration, the start of the action schedule, the initial set of media items and the
   * preparation of the player.
   *
   * @return This test runner.
   */
  public ExoPlayerTestRunner start() {
    return start(/* doPrepare= */ true);
  }

  /**
   * Starts the test runner on its own thread. This will trigger the creation of the player, the
   * listener registration, the start of the action schedule and the initial set of media items.
   *
   * @param doPrepare Whether the player should be prepared.
   * @return This test runner.
   */
  public ExoPlayerTestRunner start(boolean doPrepare) {
    handler.post(
        () -> {
          try {
            player = playerBuilder.setLooper(Looper.myLooper()).build();
            player.addListener(ExoPlayerTestRunner.this);
            if (eventListener != null) {
              player.addListener(eventListener);
            }
            if (analyticsListener != null) {
              player.addAnalyticsListener(analyticsListener);
            }
            if (pauseAtEndOfMediaItems) {
              player.setPauseAtEndOfMediaItems(true);
            }
            player.play();
            if (actionSchedule != null) {
              actionSchedule.start(
                  player,
                  playerBuilder.getTrackSelector(),
                  /* surface= */ null,
                  handler,
                  /* callback= */ ExoPlayerTestRunner.this);
            }
            if (initialWindowIndex != C.INDEX_UNSET) {
              player.seekTo(initialWindowIndex, initialPositionMs);
            }
            if (!skipSettingMediaSources) {
              player.setMediaSources(mediaSources, /* resetPosition= */ false);
            }
            if (doPrepare) {
              player.prepare();
            }
          } catch (Exception e) {
            handleException(e);
          }
        });
    return this;
  }

  /**
   * Blocks the current thread until the test runner finishes. A test is deemed to be finished when
   * the playback state transitioned to {@link Player#STATE_ENDED} or {@link Player#STATE_IDLE} for
   * the specified number of times. The test also finishes when an {@link ExoPlaybackException} is
   * thrown.
   *
   * @param timeoutMs The maximum time to wait for the test runner to finish. If this time elapsed
   *     the method will throw a {@link TimeoutException}.
   * @return This test runner.
   * @throws Exception If any exception occurred during playback, release, or due to a timeout.
   */
  public ExoPlayerTestRunner blockUntilEnded(long timeoutMs) throws Exception {
    if (!endedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      exception = new TimeoutException("Test playback timed out waiting for playback to end.");
    }
    release();
    // Throw any pending exception (from playback, timing out or releasing).
    if (exception != null) {
      throw exception;
    }
    return this;
  }

  /**
   * Blocks the current thread until the action schedule finished. This does not release the test
   * runner and the test must still call {@link #blockUntilEnded(long)}.
   *
   * @param timeoutMs The maximum time to wait for the action schedule to finish.
   * @return This test runner.
   * @throws TimeoutException If the action schedule did not finish within the specified timeout.
   * @throws InterruptedException If the test thread gets interrupted while waiting.
   */
  public ExoPlayerTestRunner blockUntilActionScheduleFinished(long timeoutMs)
      throws TimeoutException, InterruptedException {
    if (!actionScheduleFinishedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("Test playback timed out waiting for action schedule to finish.");
    }
    return this;
  }

  // Assertions called on the test thread after test finished.

  /**
   * Asserts that the timelines reported by {@link Player.EventListener#onTimelineChanged(Timeline,
   * int)} are the same to the provided timelines. This assert differs from testing equality by not
   * comparing period ids which may be different due to id mapping of child source period ids.
   *
   * @param timelines A list of expected {@link Timeline}s.
   */
  public void assertTimelinesSame(Timeline... timelines) {
    assertThat(this.timelines).hasSize(timelines.length);
    for (int i = 0; i < timelines.length; i++) {
      assertThat(new NoUidTimeline(timelines[i]))
          .isEqualTo(new NoUidTimeline(this.timelines.get(i)));
    }
  }

  /**
   * Asserts that the timeline change reasons reported by {@link
   * Player.EventListener#onTimelineChanged(Timeline, int)} are equal to the provided timeline
   * change reasons.
   */
  public void assertTimelineChangeReasonsEqual(Integer... reasons) {
    assertThat(timelineChangeReasons).containsExactlyElementsIn(Arrays.asList(reasons)).inOrder();
  }

  /**
   * Asserts that the playback states reported by {@link
   * Player.EventListener#onPlaybackStateChanged(int)} are equal to the provided playback states.
   */
  public void assertPlaybackStatesEqual(Integer... states) {
    assertThat(playbackStates).containsExactlyElementsIn(Arrays.asList(states)).inOrder();
  }

  /**
   * Asserts that the last track group array reported by {@link
   * Player.EventListener#onTracksChanged(TrackGroupArray, TrackSelectionArray)} is equal to the
   * provided track group array.
   *
   * @param trackGroupArray The expected {@link TrackGroupArray}.
   */
  public void assertTrackGroupsEqual(TrackGroupArray trackGroupArray) {
    assertThat(this.trackGroups).isEqualTo(trackGroupArray);
  }

  /**
   * Asserts that {@link Player.EventListener#onPositionDiscontinuity(int)} was not called.
   */
  public void assertNoPositionDiscontinuities() {
    assertThat(discontinuityReasons).isEmpty();
  }

  /**
   * Asserts that the discontinuity reasons reported by {@link
   * Player.EventListener#onPositionDiscontinuity(int)} are equal to the provided values.
   *
   * @param discontinuityReasons The expected discontinuity reasons.
   */
  public void assertPositionDiscontinuityReasonsEqual(Integer... discontinuityReasons) {
    assertThat(this.discontinuityReasons)
        .containsExactlyElementsIn(Arrays.asList(discontinuityReasons))
        .inOrder();
  }

  /**
   * Asserts that the indices of played periods is equal to the provided list of periods. A period
   * is considered to be played if it was the current period after a position discontinuity or a
   * media source preparation. When the same period is repeated automatically due to enabled repeat
   * modes, it is reported twice. Seeks within the current period are not reported.
   *
   * @param periodIndices A list of expected period indices.
   */
  public void assertPlayedPeriodIndices(Integer... periodIndices) {
    assertThat(this.periodIndices)
        .containsExactlyElementsIn(Arrays.asList(periodIndices))
        .inOrder();
  }

  // Private implementation details.

  private void release() throws InterruptedException {
    handler.post(
        () -> {
          try {
            if (player != null) {
              player.release();
            }
          } catch (Exception e) {
            handleException(e);
          } finally {
            playerThread.quit();
          }
        });
    playerThread.join();
  }

  private void handleException(Exception exception) {
    if (this.exception == null) {
      this.exception = exception;
    }
    while (endedCountDownLatch.getCount() > 0) {
      endedCountDownLatch.countDown();
    }
  }

  // Player.EventListener

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    timelineChangeReasons.add(reason);
    timelines.add(timeline);
    int currentIndex = player.getCurrentPeriodIndex();
    if (periodIndices.isEmpty() || periodIndices.get(periodIndices.size() - 1) != currentIndex) {
      // Ignore timeline changes that do not change the period index.
      periodIndices.add(currentIndex);
    }
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    this.trackGroups = trackGroups;
  }

  @Override
  public void onPlaybackStateChanged(@Player.State int playbackState) {
    playbackStates.add(playbackState);
    playerWasPrepared |= playbackState != Player.STATE_IDLE;
    if (playbackState == Player.STATE_ENDED
        || (playbackState == Player.STATE_IDLE && playerWasPrepared)) {
      endedCountDownLatch.countDown();
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    handleException(error);
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    discontinuityReasons.add(reason);
    int currentIndex = player.getCurrentPeriodIndex();
    if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
        || periodIndices.isEmpty()
        || periodIndices.get(periodIndices.size() - 1) != currentIndex) {
      // Ignore seek or internal discontinuities within a period.
      periodIndices.add(currentIndex);
    }
  }

  // ActionSchedule.Callback

  @Override
  public void onActionScheduleFinished() {
    actionScheduleFinishedCountDownLatch.countDown();
  }
}
