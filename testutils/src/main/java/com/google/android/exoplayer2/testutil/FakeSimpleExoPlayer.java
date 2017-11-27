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

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector.InvalidationListener;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Fake {@link SimpleExoPlayer} which runs a simplified copy of the playback loop as fast as
 * possible without waiting. It does only support single period timelines and does not support
 * updates during playback (like seek, timeline changes, repeat mode changes).
 */
public class FakeSimpleExoPlayer extends SimpleExoPlayer {

  private FakeExoPlayer player;

  public FakeSimpleExoPlayer(RenderersFactory renderersFactory, TrackSelector trackSelector,
      LoadControl loadControl, FakeClock clock) {
    super (renderersFactory, trackSelector, loadControl);
    player.setFakeClock(clock);
  }

  @Override
  protected ExoPlayer createExoPlayerImpl(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl) {
    this.player = new FakeExoPlayer(renderers, trackSelector, loadControl);
    return player;
  }

  private static class FakeExoPlayer extends StubExoPlayer implements MediaSource.Listener,
      MediaPeriod.Callback, Runnable {

    private final Renderer[] renderers;
    private final TrackSelector trackSelector;
    private final LoadControl loadControl;
    private final CopyOnWriteArraySet<Player.EventListener> eventListeners;
    private final HandlerThread playbackThread;
    private final Handler playbackHandler;
    private final Handler eventListenerHandler;

    private FakeClock clock;
    private MediaSource mediaSource;
    private Timeline timeline;
    private Object manifest;
    private MediaPeriod mediaPeriod;
    private TrackSelectorResult selectorResult;

    private boolean isStartingUp;
    private boolean isLoading;
    private int playbackState;
    private long rendererPositionUs;
    private long durationUs;
    private volatile long currentPositionMs;
    private volatile long bufferedPositionMs;

    public FakeExoPlayer(Renderer[] renderers, TrackSelector trackSelector,
        LoadControl loadControl) {
      this.renderers = renderers;
      this.trackSelector = trackSelector;
      this.loadControl = loadControl;
      this.eventListeners = new CopyOnWriteArraySet<>();
      Looper eventListenerLooper = Looper.myLooper();
      this.eventListenerHandler = new Handler(eventListenerLooper != null ? eventListenerLooper
          : Looper.getMainLooper());
      this.playbackThread = new HandlerThread("FakeExoPlayer Thread");
      playbackThread.start();
      this.playbackHandler = new Handler(playbackThread.getLooper());
      this.isStartingUp = true;
      this.isLoading = false;
      this.playbackState = Player.STATE_IDLE;
      this.durationUs = C.TIME_UNSET;
    }

    public void setFakeClock(FakeClock clock) {
      this.clock = clock;
    }

    @Override
    public void addListener(Player.EventListener listener) {
      eventListeners.add(listener);
    }

    @Override
    public void removeListener(Player.EventListener listener) {
      eventListeners.remove(listener);
    }

    @Override
    public int getPlaybackState() {
      return playbackState;
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
      if (!playWhenReady) {
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public boolean getPlayWhenReady() {
      return true;
    }

    @Override
    public int getRepeatMode() {
      return Player.REPEAT_MODE_OFF;
    }

    @Override
    public boolean getShuffleModeEnabled() {
      return false;
    }

    @Override
    public boolean isLoading() {
      return isLoading;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      return PlaybackParameters.DEFAULT;
    }

    @Override
    public void stop() {
      stop(/* quitPlaybackThread= */ false);
    }

    @Override
    @SuppressWarnings("ThreadJoinLoop")
    public void release() {
      stop(/* quitPlaybackThread= */ true);
      while (playbackThread.isAlive()) {
        try {
          playbackThread.join();
        } catch (InterruptedException e) {
          // Ignore interrupt.
        }
      }
    }

    @Override
    public int getRendererCount() {
      return renderers.length;
    }

    @Override
    public int getRendererType(int index) {
      return renderers[index].getTrackType();
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
      return selectorResult != null ? selectorResult.groups : null;
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
      return selectorResult != null ? selectorResult.selections : null;
    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
      return manifest;
    }

    @Override
    public Timeline getCurrentTimeline() {
      return timeline;
    }

    @Override
    public int getCurrentPeriodIndex() {
      return 0;
    }

    @Override
    public int getCurrentWindowIndex() {
      return 0;
    }

    @Override
    public int getNextWindowIndex() {
      return C.INDEX_UNSET;
    }

    @Override
    public int getPreviousWindowIndex() {
      return C.INDEX_UNSET;
    }

    @Override
    public long getDuration() {
      return C.usToMs(durationUs);
    }

    @Override
    public long getCurrentPosition() {
      return currentPositionMs;
    }

    @Override
    public long getBufferedPosition() {
      return bufferedPositionMs == C.TIME_END_OF_SOURCE ? getDuration() : bufferedPositionMs;
    }

    @Override
    public int getBufferedPercentage() {
      long duration = getDuration();
      return duration == C.TIME_UNSET ? 0 : (int) (getBufferedPosition() * 100 / duration);
    }

    @Override
    public boolean isCurrentWindowDynamic() {
      return false;
    }

    @Override
    public boolean isCurrentWindowSeekable() {
      return false;
    }

    @Override
    public boolean isPlayingAd() {
      return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
      return 0;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
      return 0;
    }

    @Override
    public long getContentPosition() {
      return getCurrentPosition();
    }

    @Override
    public Looper getPlaybackLooper() {
      return playbackThread.getLooper();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
      prepare(mediaSource, true, true);
    }

    @Override
    public void prepare(final MediaSource mediaSource, boolean resetPosition, boolean resetState) {
      if (!resetPosition || !resetState) {
        throw new UnsupportedOperationException();
      }
      this.mediaSource = mediaSource;
      playbackHandler.post(new Runnable() {
        @Override
        public void run() {
          mediaSource.prepareSource(FakeExoPlayer.this, true, FakeExoPlayer.this);
        }
      });
    }

    // MediaSource.Listener

    @Override
    public void onSourceInfoRefreshed(MediaSource source, final Timeline timeline,
        final @Nullable Object manifest) {
      if (this.timeline != null) {
        throw new UnsupportedOperationException();
      }
      Assertions.checkArgument(timeline.getPeriodCount() == 1);
      Assertions.checkArgument(timeline.getWindowCount() == 1);
      final ConditionVariable waitForNotification = new ConditionVariable();
      eventListenerHandler.post(new Runnable() {
        @Override
        public void run() {
          for (Player.EventListener eventListener : eventListeners) {
            FakeExoPlayer.this.durationUs = timeline.getPeriod(0, new Period()).durationUs;
            FakeExoPlayer.this.timeline = timeline;
            FakeExoPlayer.this.manifest = manifest;
            eventListener.onTimelineChanged(timeline, manifest);
            waitForNotification.open();
          }
        }
      });
      waitForNotification.block();
      this.mediaPeriod = mediaSource.createPeriod(new MediaPeriodId(0), loadControl.getAllocator());
      mediaPeriod.prepare(this, 0);
    }

    // MediaPeriod.Callback

    @Override
    public void onContinueLoadingRequested(MediaPeriod source) {
      maybeContinueLoading();
    }

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      try {
        initializePlaybackLoop();
      } catch (ExoPlaybackException e) {
        handlePlayerError(e);
      }
    }

    // Runnable (Playback loop).

    @Override
    public void run() {
      try {
        maybeContinueLoading();
        boolean allRenderersEnded = true;
        boolean allRenderersReadyOrEnded = true;
        if (playbackState == Player.STATE_READY) {
          for (Renderer renderer : renderers) {
            renderer.render(rendererPositionUs, C.msToUs(clock.elapsedRealtime()));
            if (!renderer.isEnded()) {
              allRenderersEnded = false;
            }
            if (!(renderer.isReady() || renderer.isEnded())) {
              allRenderersReadyOrEnded = false;
            }
          }
        }
        if (rendererPositionUs >= durationUs && allRenderersEnded) {
          changePlaybackState(Player.STATE_ENDED);
          return;
        }
        long bufferedPositionUs = mediaPeriod.getBufferedPositionUs();
        if (playbackState == Player.STATE_BUFFERING && allRenderersReadyOrEnded
            && haveSufficientBuffer(!isStartingUp, rendererPositionUs, bufferedPositionUs)) {
          changePlaybackState(Player.STATE_READY);
          isStartingUp = false;
        } else if (playbackState == Player.STATE_READY && !allRenderersReadyOrEnded) {
          changePlaybackState(Player.STATE_BUFFERING);
        }
        // Advance simulated time by 10ms.
        clock.advanceTime(10);
        if (playbackState == Player.STATE_READY) {
          rendererPositionUs += 10000;
        }
        this.currentPositionMs = C.usToMs(rendererPositionUs);
        this.bufferedPositionMs = C.usToMs(bufferedPositionUs);
        playbackHandler.post(this);
      } catch (ExoPlaybackException e) {
        handlePlayerError(e);
      }
    }

    // Internal logic

    private void initializePlaybackLoop() throws ExoPlaybackException {
      Assertions.checkNotNull(clock);
      trackSelector.init(new InvalidationListener() {
        @Override
        public void onTrackSelectionsInvalidated() {
          throw new IllegalStateException();
        }
      });
      RendererCapabilities[] rendererCapabilities = new RendererCapabilities[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        rendererCapabilities[i] = renderers[i].getCapabilities();
      }
      selectorResult = trackSelector.selectTracks(rendererCapabilities,
          mediaPeriod.getTrackGroups());
      SampleStream[] sampleStreams = new SampleStream[renderers.length];
      boolean[] mayRetainStreamFlags = new boolean[renderers.length];
      Arrays.fill(mayRetainStreamFlags, true);
      mediaPeriod.selectTracks(selectorResult.selections.getAll(), mayRetainStreamFlags,
          sampleStreams, new boolean[renderers.length], 0);
      eventListenerHandler.post(new Runnable() {
        @Override
        public void run() {
          for (Player.EventListener eventListener : eventListeners) {
            eventListener.onTracksChanged(selectorResult.groups, selectorResult.selections);
          }
        }
      });

      loadControl.onPrepared();
      loadControl.onTracksSelected(renderers, selectorResult.groups, selectorResult.selections);

      for (int i = 0; i < renderers.length; i++) {
        TrackSelection selection = selectorResult.selections.get(i);
        Format[] formats = new Format[selection.length()];
        for (int j = 0; j < formats.length; j++) {
          formats[j] = selection.getFormat(j);
        }
        renderers[i].enable(selectorResult.rendererConfigurations[i], formats, sampleStreams[i], 0,
            false, 0);
        renderers[i].setCurrentStreamFinal();
      }

      rendererPositionUs = 0;
      changePlaybackState(Player.STATE_BUFFERING);
      playbackHandler.post(this);
    }

    private void maybeContinueLoading() {
      boolean newIsLoading = false;
      long nextLoadPositionUs = mediaPeriod.getNextLoadPositionUs();
      if (nextLoadPositionUs != C.TIME_END_OF_SOURCE) {
        long bufferedDurationUs = nextLoadPositionUs - rendererPositionUs;
        if (loadControl.shouldContinueLoading(bufferedDurationUs)) {
          newIsLoading = true;
          mediaPeriod.continueLoading(rendererPositionUs);
        }
      }
      if (newIsLoading != isLoading) {
        isLoading = newIsLoading;
        eventListenerHandler.post(new Runnable() {
          @Override
          public void run() {
            for (Player.EventListener eventListener : eventListeners) {
              eventListener.onLoadingChanged(isLoading);
            }
          }
        });
      }
    }

    private boolean haveSufficientBuffer(boolean rebuffering, long rendererPositionUs,
        long bufferedPositionUs) {
      if (bufferedPositionUs == C.TIME_END_OF_SOURCE) {
        return true;
      }
      return loadControl.shouldStartPlayback(bufferedPositionUs - rendererPositionUs, rebuffering);
    }

    private void handlePlayerError(final ExoPlaybackException e) {
      eventListenerHandler.post(new Runnable() {
        @Override
        public void run() {
          for (Player.EventListener listener : eventListeners) {
            listener.onPlayerError(e);
          }
        }
      });
      changePlaybackState(Player.STATE_ENDED);
    }

    private void changePlaybackState(final int playbackState) {
      this.playbackState = playbackState;
      eventListenerHandler.post(new Runnable() {
        @Override
        public void run() {
          for (Player.EventListener listener : eventListeners) {
            listener.onPlayerStateChanged(true, playbackState);
          }
        }
      });
    }

    private void releaseMedia() {
      if (mediaSource != null) {
        if (mediaPeriod != null) {
          mediaSource.releasePeriod(mediaPeriod);
          mediaPeriod = null;
        }
        mediaSource.releaseSource();
        mediaSource = null;
      }
    }

    private void stop(final boolean quitPlaybackThread) {
      playbackHandler.post(new Runnable() {
        @Override
        public void run () {
          playbackHandler.removeCallbacksAndMessages(null);
          releaseMedia();
          changePlaybackState(Player.STATE_IDLE);
          if (quitPlaybackThread) {
            playbackThread.quit();
          }
        }
      });
    }

  }

}
