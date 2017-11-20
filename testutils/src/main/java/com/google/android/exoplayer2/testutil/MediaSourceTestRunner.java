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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * A runner for {@link MediaSource} tests.
 */
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
    playbackHandler.post(new Runnable() {
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
    assertTrue(finishedCondition.block(TIMEOUT_MS));
    if (throwable[0] != null) {
      Util.sneakyThrow(throwable[0]);
    }
  }

  /**
   * Prepares the source on the playback thread, asserting that it provides an initial timeline.
   *
   * @return The initial {@link Timeline}.
   */
  public Timeline prepareSource() {
    runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        mediaSource.prepareSource(player, true, mediaSourceListener);
      }
    });
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
    runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        holder[0] = mediaSource.createPeriod(periodId, allocator);
      }
    });
    assertNotNull(holder[0]);
    return holder[0];
  }

  /**
   * Calls {@link MediaPeriod#prepare(MediaPeriod.Callback, long)} on the playback thread.
   *
   * @param mediaPeriod The {@link MediaPeriod} to prepare.
   * @param positionUs The position at which to prepare.
   * @return A {@link ConditionVariable} that will be opened when preparation completes.
   */
  public ConditionVariable preparePeriod(final MediaPeriod mediaPeriod, final long positionUs) {
    final ConditionVariable preparedCondition = new ConditionVariable();
    runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        mediaPeriod.prepare(new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            preparedCondition.open();
          }
          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            // Do nothing.
          }
        }, positionUs);
      }
    });
    return preparedCondition;
  }

  /**
   * Calls {@link MediaSource#releasePeriod(MediaPeriod)} on the playback thread.
   *
   * @param mediaPeriod The {@link MediaPeriod} to release.
   */
  public void releasePeriod(final MediaPeriod mediaPeriod) {
    runOnPlaybackThread(new Runnable() {
      @Override
      public void run() {
        mediaSource.releasePeriod(mediaPeriod);
      }
    });
  }

  /**
   * Calls {@link MediaSource#releaseSource()} on the playback thread.
   */
  public void releaseSource() {
    runOnPlaybackThread(new Runnable() {
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
    assertTrue(timelines.isEmpty());
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
      assertNotNull(timeline); // Null indicates the poll timed out.
      assertNoTimelineChange();
      return timeline;
    } catch (InterruptedException e) {
      // Should never happen.
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates and releases all periods (including ad periods) defined in the last timeline to be
   * returned from {@link #prepareSource()}, {@link #assertTimelineChange()} or
   * {@link #assertTimelineChangeBlocking()}.
   */
  public void assertPrepareAndReleaseAllPeriods() {
    Timeline.Period period = new Timeline.Period();
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      assertPrepareAndReleasePeriod(new MediaPeriodId(i));
      timeline.getPeriod(i, period);
      for (int adGroupIndex = 0; adGroupIndex < period.getAdGroupCount(); adGroupIndex++) {
        for (int adIndex = 0; adIndex < period.getAdCountInAdGroup(adGroupIndex); adIndex++) {
          assertPrepareAndReleasePeriod(new MediaPeriodId(i, adGroupIndex, adIndex));
        }
      }
    }
  }

  private void assertPrepareAndReleasePeriod(MediaPeriodId mediaPeriodId) {
    MediaPeriod mediaPeriod = createPeriod(mediaPeriodId);
    ConditionVariable preparedCondition = preparePeriod(mediaPeriod, 0);
    assertTrue(preparedCondition.block(TIMEOUT_MS));
    // MediaSource is supposed to support multiple calls to createPeriod with the same id without an
    // intervening call to releasePeriod.
    MediaPeriod secondMediaPeriod = createPeriod(mediaPeriodId);
    ConditionVariable secondPreparedCondition = preparePeriod(secondMediaPeriod, 0);
    assertTrue(secondPreparedCondition.block(TIMEOUT_MS));
    // Release the periods.
    releasePeriod(mediaPeriod);
    releasePeriod(secondMediaPeriod);
  }

  /**
   * Releases the runner. Should be called when the runner is no longer required.
   */
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

  private static class EventHandlingExoPlayer extends StubExoPlayer implements Handler.Callback {

    private final Handler handler;

    public EventHandlingExoPlayer(Looper looper) {
      this.handler = new Handler(looper, this);
    }

    @Override
    public void sendMessages(ExoPlayerMessage... messages) {
      handler.obtainMessage(0, messages).sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
      ExoPlayerMessage[] messages = (ExoPlayerMessage[]) msg.obj;
      for (ExoPlayerMessage message : messages) {
        try {
          message.target.handleMessage(message.messageType, message.message);
        } catch (ExoPlaybackException e) {
          fail("Unexpected ExoPlaybackException.");
        }
      }
      return true;
    }

  }

}
