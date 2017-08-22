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
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner.Builder.PlayerFactory;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.Assert;

/**
 * Helper class to run an ExoPlayer test.
 */
public final class ExoPlayerTestRunner implements Player.EventListener {

  /**
   * Builder to set-up a {@link ExoPlayerTestRunner}. Default fake implementations will be used for
   * unset test properties.
   */
  public static final class Builder {

    /**
     * Factory to create an {@link SimpleExoPlayer} instance. The player will be created on its own
     * {@link HandlerThread}.
     */
    public interface PlayerFactory {

      SimpleExoPlayer createExoPlayer(RenderersFactory renderersFactory,
          MappingTrackSelector trackSelector, LoadControl loadControl);

    }

    public static final Format VIDEO_FORMAT = Format.createVideoSampleFormat(null,
        MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
        null, null);
    public static final Format AUDIO_FORMAT = Format.createAudioSampleFormat(null,
        MimeTypes.AUDIO_AAC, null, Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);

    private PlayerFactory playerFactory;
    private Timeline timeline;
    private Object manifest;
    private MediaSource mediaSource;
    private MappingTrackSelector trackSelector;
    private LoadControl loadControl;
    private Format[] supportedFormats;
    private Renderer[] renderers;
    private RenderersFactory renderersFactory;
    private ActionSchedule actionSchedule;
    private Player.EventListener eventListener;

    public Builder setTimeline(Timeline timeline) {
      Assert.assertNull(mediaSource);
      this.timeline = timeline;
      return this;
    }

    public Builder setManifest(Object manifest) {
      Assert.assertNull(mediaSource);
      this.manifest = manifest;
      return this;
    }

    /** Replaces {@link #setTimeline(Timeline)} and {@link #setManifest(Object)}. */
    public Builder setMediaSource(MediaSource mediaSource) {
      Assert.assertNull(timeline);
      Assert.assertNull(manifest);
      this.mediaSource = mediaSource;
      return this;
    }

    public Builder setTrackSelector(MappingTrackSelector trackSelector) {
      this.trackSelector = trackSelector;
      return this;
    }

    public Builder setLoadControl(LoadControl loadControl) {
      this.loadControl = loadControl;
      return this;
    }

    public Builder setSupportedFormats(Format... supportedFormats) {
      this.supportedFormats = supportedFormats;
      return this;
    }

    public Builder setRenderers(Renderer... renderers) {
      Assert.assertNull(renderersFactory);
      this.renderers = renderers;
      return this;
    }

    /** Replaces {@link #setRenderers(Renderer...)}. */
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      Assert.assertNull(renderers);
      this.renderersFactory = renderersFactory;
      return this;
    }

    public Builder setExoPlayer(PlayerFactory playerFactory) {
      this.playerFactory = playerFactory;
      return this;
    }

    public Builder setActionSchedule(ActionSchedule actionSchedule) {
      this.actionSchedule = actionSchedule;
      return this;
    }

    public Builder setEventListener(Player.EventListener eventListener) {
      this.eventListener = eventListener;
      return this;
    }

    public ExoPlayerTestRunner build() {
      if (supportedFormats == null) {
        supportedFormats = new Format[] { VIDEO_FORMAT };
      }
      if (trackSelector == null) {
        trackSelector = new DefaultTrackSelector();
      }
      if (renderersFactory == null) {
        if (renderers == null) {
          renderers = new Renderer[] { new FakeRenderer(supportedFormats) };
        }
        renderersFactory = new RenderersFactory() {
          @Override
          public Renderer[] createRenderers(Handler eventHandler,
              VideoRendererEventListener videoRendererEventListener,
              AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput,
              MetadataOutput metadataRendererOutput) {
            return renderers;
          }
        };
      }
      if (loadControl == null) {
        loadControl = new DefaultLoadControl();
      }
      if (playerFactory == null) {
        playerFactory = new PlayerFactory() {
          @Override
          public SimpleExoPlayer createExoPlayer(RenderersFactory renderersFactory,
              MappingTrackSelector trackSelector, LoadControl loadControl) {
            return ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
          }
        };
      }
      if (mediaSource == null) {
        if (timeline == null) {
          timeline = new FakeTimeline(new TimelineWindowDefinition(false, false, 0));
        }
        mediaSource = new FakeMediaSource(timeline, manifest, supportedFormats);
      }
      return new ExoPlayerTestRunner(playerFactory, mediaSource, renderersFactory, trackSelector,
          loadControl, actionSchedule, eventListener);
    }
  }

  private final PlayerFactory playerFactory;
  private final MediaSource mediaSource;
  private final RenderersFactory renderersFactory;
  private final MappingTrackSelector trackSelector;
  private final LoadControl loadControl;
  private final ActionSchedule actionSchedule;
  private final Player.EventListener eventListener;

  private final HandlerThread playerThread;
  private final Handler handler;
  private final CountDownLatch endedCountDownLatch;
  private final LinkedList<Timeline> timelines;
  private final LinkedList<Object> manifests;
  private final LinkedList<Integer> periodIndices;

  private SimpleExoPlayer player;
  private Exception exception;
  private TrackGroupArray trackGroups;
  private int positionDiscontinuityCount;

  private ExoPlayerTestRunner(PlayerFactory playerFactory, MediaSource mediaSource,
      RenderersFactory renderersFactory, MappingTrackSelector trackSelector,
      LoadControl loadControl, ActionSchedule actionSchedule, Player.EventListener eventListener) {
    this.playerFactory = playerFactory;
    this.mediaSource = mediaSource;
    this.renderersFactory = renderersFactory;
    this.trackSelector = trackSelector;
    this.loadControl = loadControl;
    this.actionSchedule = actionSchedule;
    this.eventListener = eventListener;
    this.timelines = new LinkedList<>();
    this.manifests = new LinkedList<>();
    this.periodIndices = new LinkedList<>();
    this.endedCountDownLatch = new CountDownLatch(1);
    this.playerThread = new HandlerThread("ExoPlayerTest thread");
    playerThread.start();
    this.handler = new Handler(playerThread.getLooper());
  }

  // Called on the test thread to run the test.

  public ExoPlayerTestRunner start() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          player = playerFactory.createExoPlayer(renderersFactory, trackSelector, loadControl);
          player.addListener(ExoPlayerTestRunner.this);
          if (eventListener != null) {
            player.addListener(eventListener);
          }
          player.setPlayWhenReady(true);
          if (actionSchedule != null) {
            actionSchedule.start(player, trackSelector, null, handler);
          }
          player.prepare(mediaSource);
        } catch (Exception e) {
          handleException(e);
        }
      }
    });
    return this;
  }

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

  // Assertions called on the test thread after test finished.

  public void assertTimelinesEqual(Timeline... timelines) {
    Assert.assertEquals(timelines.length, this.timelines.size());
    for (Timeline timeline : timelines) {
      Assert.assertEquals(timeline, this.timelines.remove());
    }
  }

  public void assertManifestsEqual(Object... manifests) {
    Assert.assertEquals(manifests.length, this.manifests.size());
    for (Object manifest : manifests) {
      Assert.assertEquals(manifest, this.manifests.remove());
    }
  }

  public void assertTrackGroupsEqual(TrackGroupArray trackGroupArray) {
    Assert.assertEquals(trackGroupArray, this.trackGroups);
  }

  public void assertPositionDiscontinuityCount(int expectedCount) {
    Assert.assertEquals(expectedCount, positionDiscontinuityCount);
  }

  public void assertPlayedPeriodIndices(int... periodIndices) {
    Assert.assertEquals(periodIndices.length, this.periodIndices.size());
    for (int periodIndex : periodIndices) {
      Assert.assertEquals(periodIndex, (int) this.periodIndices.remove());
    }
  }

  // Private implementation details.

  private void release() throws InterruptedException {
    handler.post(new Runnable() {
      @Override
      public void run() {
        try {
          if (player != null) {
            player.release();
          }
        } catch (Exception e) {
          handleException(e);
        } finally {
          playerThread.quit();
        }
      }
    });
    playerThread.join();
  }

  private void handleException(Exception exception) {
    if (this.exception == null) {
      this.exception = exception;
    }
    endedCountDownLatch.countDown();
  }

  // Player.EventListener

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    timelines.add(timeline);
    manifests.add(manifest);
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    this.trackGroups = trackGroups;
  }

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (periodIndices.isEmpty() && playbackState == Player.STATE_READY) {
      periodIndices.add(player.getCurrentPeriodIndex());
    }
    if (playbackState == Player.STATE_ENDED) {
      endedCountDownLatch.countDown();
    }
  }

  @Override
  public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    // Do nothing.
  }

  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    handleException(exception);
  }

  @Override
  public void onPositionDiscontinuity() {
    positionDiscontinuityCount++;
    periodIndices.add(player.getCurrentPeriodIndex());
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

}
