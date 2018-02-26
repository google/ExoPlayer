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
import static org.junit.Assert.fail;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/** A runner for {@link MediaSource} tests. */
public class MediaSourceTestRunner {

  public static final int TIMEOUT_MS = 10000;

  private final StubExoPlayer player;
  private final MediaSource mediaSource;
  private final MediaSourceListener mediaSourceListener;
  private final HandlerThread playbackThread;
  private final Handler playbackHandler;
  private final Allocator allocator;

  private final LinkedBlockingDeque<Timeline> timelines;
  private Timeline timeline;

  /**
   * @param mediaSource The source under test.
   * @param allocator The allocator to use during the test run.
   */
  public MediaSourceTestRunner(MediaSource mediaSource, Allocator allocator) {
    this.mediaSource = mediaSource;
    this.allocator = allocator;
    playbackThread = new HandlerThread("PlaybackThread");
    playbackThread.start();
    Looper playbackLooper = playbackThread.getLooper();
    playbackHandler = new Handler(playbackLooper);
    player = new EventHandlingExoPlayer(playbackLooper);
    mediaSourceListener = new MediaSourceListener();
    timelines = new LinkedBlockingDeque<>();
  }

  /**
   * Runs the provided {@link Runnable} on the playback thread, blocking until execution completes.
   *
   * @param runnable The {@link Runnable} to run.
   */
  public void runOnPlaybackThread(final Runnable runnable) {
    final Throwable[] throwable = new Throwable[1];
    final ConditionVariable finishedCondition = new ConditionVariable();
    playbackHandler.post(
        new Runnable() {
          @Override
          public void run() {
            try {
              runnable.run();
            } catch (Throwable e) {
              throwable[0] = e;
            } finally {
              finishedCondition.open();
            }
          }
        });
    assertThat(finishedCondition.block(TIMEOUT_MS)).isTrue();
    if (throwable[0] != null) {
      Util.sneakyThrow(throwable[0]);
    }
  }

  /**
   * Prepares the source on the playback thread, asserting that it provides an initial timeline.
   *
   * @return The initial {@link Timeline}.
   */
  public Timeline prepareSource() throws IOException {
    final IOException[] prepareError = new IOException[1];
    runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            mediaSource.prepareSource(player, true, mediaSourceListener);
            try {
              // TODO: This only catches errors that are set synchronously in prepareSource. To
              // capture async errors we'll need to poll maybeThrowSourceInfoRefreshError until the
              // first call to onSourceInfoRefreshed.
              mediaSource.maybeThrowSourceInfoRefreshError();
            } catch (IOException e) {
              prepareError[0] = e;
            }
          }
        });
    if (prepareError[0] != null) {
      throw prepareError[0];
    }
    return assertTimelineChangeBlocking();
  }

  /**
   * Calls {@link MediaSource#createPeriod(MediaSource.MediaPeriodId, Allocator)} on the playback
   * thread, asserting that a non-null {@link MediaPeriod} is returned.
   *
   * @param periodId The id of the period to create.
   * @return The created {@link MediaPeriod}.
   */
  public MediaPeriod createPeriod(final MediaPeriodId periodId) {
    final MediaPeriod[] holder = new MediaPeriod[1];
    runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            holder[0] = mediaSource.createPeriod(periodId, allocator);
          }
        });
    assertThat(holder[0]).isNotNull();
    return holder[0];
  }

  /**
   * Calls {@link MediaPeriod#prepare(MediaPeriod.Callback, long)} on the playback thread and blocks
   * until the method has been called.
   *
   * @param mediaPeriod The {@link MediaPeriod} to prepare.
   * @param positionUs The position at which to prepare.
   * @return A {@link CountDownLatch} that will be counted down when preparation completes.
   */
  public CountDownLatch preparePeriod(final MediaPeriod mediaPeriod, final long positionUs) {
    final ConditionVariable prepareCalled = new ConditionVariable();
    final CountDownLatch preparedCountDown = new CountDownLatch(1);
    runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            mediaPeriod.prepare(
                new MediaPeriod.Callback() {
                  @Override
                  public void onPrepared(MediaPeriod mediaPeriod) {
                    preparedCountDown.countDown();
                  }

                  @Override
                  public void onContinueLoadingRequested(MediaPeriod source) {
                    // Do nothing.
                  }
                },
                positionUs);
            prepareCalled.open();
          }
        });
    prepareCalled.block();
    return preparedCountDown;
  }

  /**
   * Calls {@link MediaSource#releasePeriod(MediaPeriod)} on the playback thread.
   *
   * @param mediaPeriod The {@link MediaPeriod} to release.
   */
  public void releasePeriod(final MediaPeriod mediaPeriod) {
    runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            mediaSource.releasePeriod(mediaPeriod);
          }
        });
  }

  /** Calls {@link MediaSource#releaseSource()} on the playback thread. */
  public void releaseSource() {
    runOnPlaybackThread(
        new Runnable() {
          @Override
          public void run() {
            mediaSource.releaseSource();
          }
        });
  }

  /**
   * Asserts that the source has not notified its listener of a timeline change since the last call
   * to {@link #assertTimelineChangeBlocking()} or {@link #assertTimelineChange()} (or since the
   * runner was created if neither method has been called).
   */
  public void assertNoTimelineChange() {
    assertThat(timelines.isEmpty()).isTrue();
  }

  /**
   * Asserts that the source has notified its listener of a single timeline change.
   *
   * @return The new {@link Timeline}.
   */
  public Timeline assertTimelineChange() {
    timeline = timelines.removeFirst();
    assertNoTimelineChange();
    return timeline;
  }

  /**
   * Asserts that the source notifies its listener of a single timeline change. If the source has
   * not yet notified its listener, it has up to the timeout passed to the constructor to do so.
   *
   * @return The new {@link Timeline}.
   */
  public Timeline assertTimelineChangeBlocking() {
    try {
      timeline = timelines.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(timeline).isNotNull(); // Null indicates the poll timed out.
      assertNoTimelineChange();
      return timeline;
    } catch (InterruptedException e) {
      // Should never happen.
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates and releases all periods (including ad periods) defined in the last timeline to be
   * returned from {@link #prepareSource()}, {@link #assertTimelineChange()} or {@link
   * #assertTimelineChangeBlocking()}. The {@link MediaPeriodId#windowSequenceNumber} is set to the
   * index of the window.
   */
  public void assertPrepareAndReleaseAllPeriods() throws InterruptedException {
    Timeline.Period period = new Timeline.Period();
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      timeline.getPeriod(i, period);
      assertPrepareAndReleasePeriod(new MediaPeriodId(i, period.windowIndex));
      for (int adGroupIndex = 0; adGroupIndex < period.getAdGroupCount(); adGroupIndex++) {
        for (int adIndex = 0; adIndex < period.getAdCountInAdGroup(adGroupIndex); adIndex++) {
          assertPrepareAndReleasePeriod(
              new MediaPeriodId(i, adGroupIndex, adIndex, period.windowIndex));
        }
      }
    }
  }

  private void assertPrepareAndReleasePeriod(MediaPeriodId mediaPeriodId)
      throws InterruptedException {
    MediaPeriod mediaPeriod = createPeriod(mediaPeriodId);
    CountDownLatch preparedCondition = preparePeriod(mediaPeriod, 0);
    assertThat(preparedCondition.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    // MediaSource is supposed to support multiple calls to createPeriod with the same id without an
    // intervening call to releasePeriod.
    MediaPeriod secondMediaPeriod = createPeriod(mediaPeriodId);
    CountDownLatch secondPreparedCondition = preparePeriod(secondMediaPeriod, 0);
    assertThat(secondPreparedCondition.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    // Release the periods.
    releasePeriod(mediaPeriod);
    releasePeriod(secondMediaPeriod);
  }

  /** Releases the runner. Should be called when the runner is no longer required. */
  public void release() {
    playbackThread.quit();
  }

  private class MediaSourceListener implements MediaSource.Listener {

    @Override
    public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
      timelines.addLast(timeline);
    }
  }

  private static class EventHandlingExoPlayer extends StubExoPlayer
      implements Handler.Callback, PlayerMessage.Sender {

    private final Handler handler;

    public EventHandlingExoPlayer(Looper looper) {
      this.handler = new Handler(looper, this);
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
      return new PlayerMessage(
          /* sender= */ this, target, Timeline.EMPTY, /* defaultWindowIndex= */ 0, handler);
    }

    @Override
    public void sendMessage(PlayerMessage message) {
      handler.obtainMessage(0, message).sendToTarget();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
      PlayerMessage message = (PlayerMessage) msg.obj;
      try {
        message.getTarget().handleMessage(message.getType(), message.getPayload());
        message.markAsProcessed(/* isDelivered= */ true);
      } catch (ExoPlaybackException e) {
        fail("Unexpected ExoPlaybackException.");
      }
      return true;
    }
  }
}
