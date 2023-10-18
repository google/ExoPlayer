/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.testutil.FakeAudioRenderer;
import com.google.android.exoplayer2.testutil.FakeMediaPeriod;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeMediaSourceFactory;
import com.google.android.exoplayer2.testutil.FakeVideoRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/**
 * Tests the coordination behaviours when the player starts to prepare the {@link
 * PreloadMediaSource} while it is in the different preload stages. For example, as long as the
 * player calls {@link PreloadMediaSource#prepareSource(MediaSource.MediaSourceCaller,
 * TransferListener, PlayerId)}, the rest of the preload logic shouldn't proceed.
 */
@RunWith(AndroidJUnit4.class)
public class PreloadAndPlaybackCoordinationTest {

  private final PlayerId playerId;
  private final BandwidthMeter bandwidthMeter;
  private final PreloadMediaSource preloadMediaSource;
  private final FakeMediaSource wrappedMediaSource;
  private final MediaSource.MediaSourceCaller playbackMediaSourceCaller;

  private final AtomicBoolean preloadControlOnSourceInfoRefreshedCalled;
  private final AtomicBoolean preloadControlOnPreparedCalled;
  private final AtomicBoolean playbackSourceCallerOnSourceInfoRefreshedCalled;
  private final AtomicBoolean playbackPeriodCallbackOnPreparedCalled;

  public PreloadAndPlaybackCoordinationTest() {
    playerId = new PlayerId();
    Context context = ApplicationProvider.getApplicationContext();
    bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
    FakeMediaSourceFactory mediaSourceFactory = new FakeMediaSourceFactory();
    Allocator allocator =
        new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
    TrackSelector trackSelector = new DefaultTrackSelector(context);
    trackSelector.init(() -> {}, bandwidthMeter);
    RenderersFactory renderersFactory =
        (handler, videoListener, audioListener, textOutput, metadataOutput) ->
            new Renderer[] {
              new FakeVideoRenderer(
                  SystemClock.DEFAULT.createHandler(handler.getLooper(), /* callback= */ null),
                  videoListener),
              new FakeAudioRenderer(
                  SystemClock.DEFAULT.createHandler(handler.getLooper(), /* callback= */ null),
                  audioListener)
            };
    preloadControlOnSourceInfoRefreshedCalled = new AtomicBoolean();
    preloadControlOnPreparedCalled = new AtomicBoolean();
    playbackSourceCallerOnSourceInfoRefreshedCalled = new AtomicBoolean();
    playbackPeriodCallbackOnPreparedCalled = new AtomicBoolean();

    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            preloadControlOnSourceInfoRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            preloadControlOnPreparedCalled.set(true);
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return true;
          }
        };
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            playerId,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            /* preloadLooper= */ Util.getCurrentOrMainLooper());
    preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            MediaItem.fromUri("asset://android_asset/media/mp4/sample.mp4"));
    wrappedMediaSource = mediaSourceFactory.getLastCreatedSource();

    MediaPeriod.Callback playbackMediaPeriodCallback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            playbackPeriodCallbackOnPreparedCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    playbackMediaSourceCaller =
        (source, timeline) -> {
          playbackSourceCallerOnSourceInfoRefreshedCalled.set(true);
          Pair<Object, Long> periodPosition =
              timeline.getPeriodPositionUs(
                  new Timeline.Window(),
                  new Timeline.Period(),
                  /* windowIndex= */ 0,
                  /* windowPositionUs= */ 0L);
          MediaSource.MediaPeriodId mediaPeriodId =
              new MediaSource.MediaPeriodId(periodPosition.first);
          MediaPeriod mediaPeriod =
              source.createPeriod(mediaPeriodId, allocator, periodPosition.second);
          mediaPeriod.prepare(playbackMediaPeriodCallback, /* positionUs= */ 0L);
        };
  }

  @Test
  public void playbackPrepareSource_beforePreloadStart() {
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalled.get()).isFalse();
    assertThat(preloadControlOnPreparedCalled.get()).isFalse();
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
  }

  @Test
  public void playbackPrepareSource_betweenPreloadStartAndTimelineInfoRefreshed() {
    wrappedMediaSource.setAllowPreparation(false);

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    wrappedMediaSource.setAllowPreparation(true);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalled.get()).isFalse();
    assertThat(preloadControlOnPreparedCalled.get()).isFalse();
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
  }

  @Test
  public void playbackPrepareSource_betweenTimelineInfoRefreshedAndPeriodPrepared() {
    wrappedMediaSource.setPeriodDefersOnPreparedCallback(true);

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    FakeMediaPeriod lastCreatedActiveMediaPeriod =
        (FakeMediaPeriod) wrappedMediaSource.getLastCreatedActiveMediaPeriod();
    lastCreatedActiveMediaPeriod.setPreparationComplete();
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(preloadControlOnPreparedCalled.get()).isFalse();
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
  }

  @Test
  public void playbackPrepareSource_afterPeriodContinueLoading() {
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(preloadControlOnPreparedCalled.get()).isTrue();
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
  }

  private static RendererCapabilities[] getRendererCapabilities(RenderersFactory renderersFactory) {
    Renderer[] renderers =
        renderersFactory.createRenderers(
            Util.createHandlerForCurrentLooper(),
            new VideoRendererEventListener() {},
            new AudioRendererEventListener() {},
            cueGroup -> {},
            metadata -> {});
    RendererCapabilities[] rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    return rendererCapabilities;
  }
}
