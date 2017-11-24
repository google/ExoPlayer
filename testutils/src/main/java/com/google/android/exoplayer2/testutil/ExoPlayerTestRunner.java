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
public final class ExoPlayerTestRunner extends Player.DefaultEventListener {

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

      /**
       * Creates a new {@link SimpleExoPlayer} using the provided renderers factory, track selector,
       * and load control.
       *
       * @param renderersFactory A {@link RenderersFactory} to be used for the new player.
       * @param trackSelector A {@link MappingTrackSelector} to be used for the new player.
       * @param loadControl A {@link LoadControl} to be used for the new player.
       * @return A new {@link SimpleExoPlayer}.
       */
      SimpleExoPlayer createExoPlayer(RenderersFactory renderersFactory,
          MappingTrackSelector trackSelector, LoadControl loadControl);

    }

    /**
     * A generic video {@link Format} which can be used to set up media sources and renderers.
     */
    public static final Format VIDEO_FORMAT = Format.createVideoSampleFormat(null,
        MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
        null, null);

    /**
     * A generic audio {@link Format} which can be used to set up media sources and renderers.
     */
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

    /**
     * Sets a {@link Timeline} to be used by a {@link FakeMediaSource} in the test runner. The
     * default value is a seekable, non-dynamic {@link FakeTimeline} with a duration of
     * {@link FakeTimeline.TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US}. Setting the
     * timeline is not allowed after a call to {@link #setMediaSource(MediaSource)}.
     *
     * @param timeline A {@link Timeline} to be used by a {@link FakeMediaSource} in the test
     *     runner.
     * @return This builder.
     */
    public Builder setTimeline(Timeline timeline) {
      Assert.assertNull(mediaSource);
      this.timeline = timeline;
      return this;
    }

    /**
     * Sets a manifest to be used by a {@link FakeMediaSource} in the test runner. The default value
     * is null. Setting the manifest is not allowed after a call to
     * {@link #setMediaSource(MediaSource)}.
     *
     * @param manifest A manifest to be used by a {@link FakeMediaSource} in the test runner.
     * @return This builder.
     */
    public Builder setManifest(Object manifest) {
      Assert.assertNull(mediaSource);
      this.manifest = manifest;
      return this;
    }

    /**
     * Sets a {@link MediaSource} to be used by the test runner. The default value is a
     * {@link FakeMediaSource} with the timeline and manifest provided by
     * {@link #setTimeline(Timeline)} and {@link #setManifest(Object)}. Setting the media source is
     * not allowed after calls to {@link #setTimeline(Timeline)} and/or
     * {@link #setManifest(Object)}.
     *
     * @param mediaSource A {@link MediaSource} to be used by the test runner.
     * @return This builder.
     */
    public Builder setMediaSource(MediaSource mediaSource) {
      Assert.assertNull(timeline);
      Assert.assertNull(manifest);
      this.mediaSource = mediaSource;
      return this;
    }

    /**
     * Sets a {@link MappingTrackSelector} to be used by the test runner. The default value is a
     * {@link DefaultTrackSelector}.
     *
     * @param trackSelector A {@link MappingTrackSelector} to be used by the test runner.
     * @return This builder.
     */
    public Builder setTrackSelector(MappingTrackSelector trackSelector) {
      this.trackSelector = trackSelector;
      return this;
    }

    /**
     * Sets a {@link LoadControl} to be used by the test runner. The default value is a
     * {@link DefaultLoadControl}.
     *
     * @param loadControl A {@link LoadControl} to be used by the test runner.
     * @return This builder.
     */
    public Builder setLoadControl(LoadControl loadControl) {
      this.loadControl = loadControl;
      return this;
    }

    /**
     * Sets a list of {@link Format}s to be used by a {@link FakeMediaSource} to create media
     * periods and for setting up a {@link FakeRenderer}. The default value is a single
     * {@link #VIDEO_FORMAT}. Note that this parameter doesn't have any influence if both a media
     * source with {@link #setMediaSource(MediaSource)} and renderers with
     * {@link #setRenderers(Renderer...)} or {@link #setRenderersFactory(RenderersFactory)} are set.
     *
     * @param supportedFormats A list of supported {@link Format}s.
     * @return This builder.
     */
    public Builder setSupportedFormats(Format... supportedFormats) {
      this.supportedFormats = supportedFormats;
      return this;
    }

    /**
     * Sets the {@link Renderer}s to be used by the test runner. The default value is a single
     * {@link FakeRenderer} supporting the formats set by {@link #setSupportedFormats(Format...)}.
     * Setting the renderers is not allowed after a call to
     * {@link #setRenderersFactory(RenderersFactory)}.
     *
     * @param renderers A list of {@link Renderer}s to be used by the test runner.
     * @return This builder.
     */
    public Builder setRenderers(Renderer... renderers) {
      Assert.assertNull(renderersFactory);
      this.renderers = renderers;
      return this;
    }

    /**
     * Sets the {@link RenderersFactory} to be used by the test runner. The default factory creates
     * all renderers set by {@link #setRenderers(Renderer...)}. Setting the renderer factory is not
     * allowed after a call to {@link #setRenderers(Renderer...)}.
     *
     * @param renderersFactory A {@link RenderersFactory} to be used by the test runner.
     * @return This builder.
     */
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      Assert.assertNull(renderers);
      this.renderersFactory = renderersFactory;
      return this;
    }

    /**
     * Sets the {@link PlayerFactory} which creates the {@link SimpleExoPlayer} to be used by the
     * test runner. The default value is a {@link SimpleExoPlayer} with the renderers provided by
     * {@link #setRenderers(Renderer...)} or {@link #setRenderersFactory(RenderersFactory)}, the
     * track selector provided by {@link #setTrackSelector(MappingTrackSelector)} and the load
     * control provided by {@link #setLoadControl(LoadControl)}.
     *
     * @param playerFactory A {@link PlayerFactory} to create the player.
     * @return This builder.
     */
    public Builder setExoPlayer(PlayerFactory playerFactory) {
      this.playerFactory = playerFactory;
      return this;
    }

    /**
     * Sets an {@link ActionSchedule} to be run by the test runner. The first action will be
     * executed immediately before {@link SimpleExoPlayer#prepare(MediaSource)}.
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
     * Builds an {@link ExoPlayerTestRunner} using the provided values or their defaults.
     *
     * @return The built {@link ExoPlayerTestRunner}.
     */
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
          timeline = new FakeTimeline(1);
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
  private boolean playerWasPrepared;

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

  /**
   * Starts the test runner on its own thread. This will trigger the creation of the player, the
   * listener registration, the start of the action schedule, and the preparation of the player
   * with the provided media source.
   *
   * @return This test runner.
   */
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

  /**
   * Blocks the current thread until the test runner finishes. A test is deemed to be finished when
   * the playback state transitions to {@link Player#STATE_ENDED} or {@link Player#STATE_IDLE}, or
   * when am {@link ExoPlaybackException} is thrown.
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

  // Assertions called on the test thread after test finished.

  /**
   * Asserts that the timelines reported by
   * {@link Player.EventListener#onTimelineChanged(Timeline, Object, int)} are equal to the provided
   * timelines.
   *
   * @param timelines A list of expected {@link Timeline}s.
   */
  public void assertTimelinesEqual(Timeline... timelines) {
    Assert.assertEquals(timelines.length, this.timelines.size());
    for (Timeline timeline : timelines) {
      Assert.assertEquals(timeline, this.timelines.remove());
    }
  }

  /**
   * Asserts that the manifests reported by
   * {@link Player.EventListener#onTimelineChanged(Timeline, Object, int)} are equal to the provided
   * manifest.
   *
   * @param manifests A list of expected manifests.
   */
  public void assertManifestsEqual(Object... manifests) {
    Assert.assertEquals(manifests.length, this.manifests.size());
    for (Object manifest : manifests) {
      Assert.assertEquals(manifest, this.manifests.remove());
    }
  }

  /**
   * Asserts that the last track group array reported by
   * {@link Player.EventListener#onTracksChanged(TrackGroupArray, TrackSelectionArray)} is equal to
   * the provided track group array.
   *
   * @param trackGroupArray The expected {@link TrackGroupArray}.
   */
  public void assertTrackGroupsEqual(TrackGroupArray trackGroupArray) {
    Assert.assertEquals(trackGroupArray, this.trackGroups);
  }

  /**
   * Asserts that the number of reported discontinuities by
   * {@link Player.EventListener#onPositionDiscontinuity(int)} is equal to the provided number.
   *
   * @param expectedCount The expected number of position discontinuities.
   */
  public void assertPositionDiscontinuityCount(int expectedCount) {
    Assert.assertEquals(expectedCount, positionDiscontinuityCount);
  }

  /**
   * Asserts that the indices of played periods is equal to the provided list of periods. A period
   * is considered to be played if it was the current period after a position discontinuity or a
   * media source preparation. When the same period is repeated automatically due to enabled repeat
   * modes, it is reported twice. Seeks within the current period are not reported.
   *
   * @param periodIndices A list of expected period indices.
   */
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
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (periodIndices.isEmpty() && playbackState == Player.STATE_READY) {
      periodIndices.add(player.getCurrentPeriodIndex());
    }
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
    positionDiscontinuityCount++;
    periodIndices.add(player.getCurrentPeriodIndex());
  }

}
