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

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Pair;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.Assert;

/**
 * Wraps a player with its own handler thread.
 */
public class ExoPlayerWrapper implements Player.EventListener {

  private final CountDownLatch sourceInfoCountDownLatch;
  private final CountDownLatch endedCountDownLatch;
  private final HandlerThread playerThread;
  private final Handler handler;
  private final LinkedList<Pair<Timeline, Object>> sourceInfos;

  public ExoPlayer player;
  public TrackGroupArray trackGroups;
  public Exception exception;

  // Written only on the main thread.
  public volatile int positionDiscontinuityCount;

  public ExoPlayerWrapper() {
    sourceInfoCountDownLatch = new CountDownLatch(1);
    endedCountDownLatch = new CountDownLatch(1);
    playerThread = new HandlerThread("ExoPlayerTest thread");
    playerThread.start();
    handler = new Handler(playerThread.getLooper());
    sourceInfos = new LinkedList<>();
  }

  // Called on the test thread.

  public void blockUntilEnded(long timeoutMs) throws Exception {
    if (!endedCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      exception = new TimeoutException("Test playback timed out waiting for playback to end.");
    }
    release();
    // Throw any pending exception (from playback, timing out or releasing).
    if (exception != null) {
      throw exception;
    }
  }

  public void blockUntilSourceInfoRefreshed(long timeoutMs) throws Exception {
    if (!sourceInfoCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("Test playback timed out waiting for source info.");
    }
  }

  public void setup(final MediaSource mediaSource, final Renderer... renderers) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          player = ExoPlayerFactory.newInstance(renderers, new DefaultTrackSelector());
          player.addListener(ExoPlayerWrapper.this);
          player.setPlayWhenReady(true);
          player.prepare(mediaSource);
        } catch (Exception e) {
          handleError(e);
        }
      }
    });
  }

  public void prepare(final MediaSource mediaSource) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          player.prepare(mediaSource);
        } catch (Exception e) {
          handleError(e);
        }
      }
    });
  }

  public void release() throws InterruptedException {
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          if (player != null) {
            player.release();
          }
        } catch (Exception e) {
          handleError(e);
        } finally {
          playerThread.quit();
        }
      }
    });
    playerThread.join();
  }

  private void handleError(Exception exception) {
    if (this.exception == null) {
      this.exception = exception;
    }
    endedCountDownLatch.countDown();
  }

  @SafeVarargs
  public final void assertSourceInfosEquals(Pair<Timeline, Object>... sourceInfos) {
    Assert.assertEquals(sourceInfos.length, this.sourceInfos.size());
    for (Pair<Timeline, Object> sourceInfo : sourceInfos) {
      Assert.assertEquals(sourceInfo, this.sourceInfos.remove());
    }
  }

  // Player.EventListener implementation.

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == Player.STATE_ENDED) {
      endedCountDownLatch.countDown();
    }
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
    // Do nothing.
  }

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    sourceInfos.add(Pair.create(timeline, manifest));
    sourceInfoCountDownLatch.countDown();
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    this.trackGroups = trackGroups;
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    handleError(exception);
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void onPositionDiscontinuity() {
    positionDiscontinuityCount++;
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

}
