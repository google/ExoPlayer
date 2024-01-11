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
package androidx.media3.exoplayer.source.preload;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.FakeAudioRenderer;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.FakeVideoRenderer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

  private final AtomicInteger preloadControlOnSourceInfoRefreshedCalledCounter;
  private final AtomicInteger preloadControlOnPreparedCalledCounter;
  private final AtomicBoolean playbackSourceCallerOnSourceInfoRefreshedCalled;
  private final AtomicBoolean playbackPeriodCallbackOnPreparedCalled;
  private final AtomicReference<MediaPeriod> preloadMediaPeriodReference;

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
    preloadControlOnSourceInfoRefreshedCalledCounter = new AtomicInteger();
    preloadControlOnPreparedCalledCounter = new AtomicInteger();
    playbackSourceCallerOnSourceInfoRefreshedCalled = new AtomicBoolean();
    playbackPeriodCallbackOnPreparedCalled = new AtomicBoolean();
    preloadMediaPeriodReference = new AtomicReference<>();

    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            preloadControlOnSourceInfoRefreshedCalledCounter.addAndGet(1);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            preloadControlOnPreparedCalledCounter.addAndGet(1);
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
          preloadMediaPeriodReference.set(mediaPeriod);
          mediaPeriod.prepare(playbackMediaPeriodCallback, /* positionUs= */ 0L);
        };
  }

  @Test
  public void playbackWithoutPreload_reusableForPreloadAfterRelease() {
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(0);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(0);
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
    assertThat(preloadMediaPeriodReference.get()).isNotNull();

    // Reuse for preload after playback releases the source
    MediaPeriod mediaPeriod = checkNotNull(preloadMediaPeriodReference.get());
    preloadMediaSource.releasePeriod(mediaPeriod);
    preloadMediaSource.releaseSource(playbackMediaSourceCaller);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(1);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(1);
  }

  @Test
  public void playbackBeforePreload_reusableForPreloadAfterRelease() {
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(0);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(0);
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
    assertThat(preloadMediaPeriodReference.get()).isNotNull();

    // Reuse for preload after playback releases the source
    MediaPeriod mediaPeriod = checkNotNull(preloadMediaPeriodReference.get());
    preloadMediaSource.releasePeriod(mediaPeriod);
    preloadMediaSource.releaseSource(playbackMediaSourceCaller);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(1);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(1);
  }

  @Test
  public void playbackBetweenPreloadStartAndTimelineInfoRefreshed_reusableForPreloadAfterRelease() {
    wrappedMediaSource.setAllowPreparation(false);

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    wrappedMediaSource.setAllowPreparation(true);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(0);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(0);
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
    assertThat(preloadMediaPeriodReference.get()).isNotNull();

    // Reuse for preload after playback releases the source
    MediaPeriod mediaPeriod = checkNotNull(preloadMediaPeriodReference.get());
    preloadMediaSource.releasePeriod(mediaPeriod);
    preloadMediaSource.releaseSource(playbackMediaSourceCaller);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(1);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(1);
  }

  @Test
  public void
      playbackBetweenPreloadTimelineRefreshedAndPeriodPrepared_reusableForPreloadAfterRelease() {
    wrappedMediaSource.setPeriodDefersOnPreparedCallback(true);

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    FakeMediaPeriod lastCreatedActiveMediaPeriod =
        (FakeMediaPeriod) wrappedMediaSource.getLastCreatedActiveMediaPeriod();
    lastCreatedActiveMediaPeriod.setPreparationComplete();
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(1);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(0);
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
    assertThat(preloadMediaPeriodReference.get()).isNotNull();

    // Reuse for preload after playback releases the source
    MediaPeriod mediaPeriod = checkNotNull(preloadMediaPeriodReference.get());
    preloadMediaSource.releasePeriod(mediaPeriod);
    preloadMediaSource.releaseSource(playbackMediaSourceCaller);
    wrappedMediaSource.setPeriodDefersOnPreparedCallback(false);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(2);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(1);
  }

  @Test
  public void playbackWhilePreloadPeriodContinueLoading_reusableForPreloadAfterRelease() {
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();
    preloadMediaSource.prepareSource(
        playbackMediaSourceCaller, bandwidthMeter.getTransferListener(), playerId);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(1);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(1);
    assertThat(playbackSourceCallerOnSourceInfoRefreshedCalled.get()).isTrue();
    assertThat(playbackPeriodCallbackOnPreparedCalled.get()).isTrue();
    assertThat(preloadMediaPeriodReference.get()).isNotNull();

    // Reuse for preload after playback releases the source
    MediaPeriod mediaPeriod = checkNotNull(preloadMediaPeriodReference.get());
    preloadMediaSource.releasePeriod(mediaPeriod);
    preloadMediaSource.releaseSource(playbackMediaSourceCaller);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    ShadowLooper.idleMainLooper();

    assertThat(preloadControlOnSourceInfoRefreshedCalledCounter.get()).isEqualTo(2);
    assertThat(preloadControlOnPreparedCalledCounter.get()).isEqualTo(2);
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
