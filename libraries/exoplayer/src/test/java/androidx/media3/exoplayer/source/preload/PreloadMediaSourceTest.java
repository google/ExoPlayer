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

import static androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.net.Uri;
import android.os.Looper;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.FakeAudioRenderer;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeTrackSelector;
import androidx.media3.test.utils.FakeVideoRenderer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PreloadMediaSource}. */
@RunWith(AndroidJUnit4.class)
public final class PreloadMediaSourceTest {

  private static final int LOADING_CHECK_INTERVAL_BYTES = 10 * 1024;
  private static final int TARGET_PRELOAD_POSITION_US = 10000;

  private Allocator allocator;
  private BandwidthMeter bandwidthMeter;
  private RenderersFactory renderersFactory;
  private PlayerId playerId;

  @Before
  public void setUp() {
    allocator = new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
    bandwidthMeter =
        new DefaultBandwidthMeter.Builder(ApplicationProvider.getApplicationContext()).build();
    renderersFactory =
        (handler, videoListener, audioListener, textOutput, metadataOutput) ->
            new Renderer[] {
              new FakeVideoRenderer(
                  SystemClock.DEFAULT.createHandler(handler.getLooper(), /* callback= */ null),
                  videoListener),
              new FakeAudioRenderer(
                  SystemClock.DEFAULT.createHandler(handler.getLooper(), /* callback= */ null),
                  audioListener)
            };
    playerId = new PlayerId();
  }

  @Test
  public void preload_loadPeriodToTargetPreloadPosition() throws Exception {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean();
    AtomicBoolean onPreparedCalled = new AtomicBoolean();
    AtomicBoolean onContinueLoadingStopped = new AtomicBoolean();
    AtomicReference<PreloadMediaSource> preloadMediaSourceReference = new AtomicReference<>();
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            preloadMediaSourceReference.set(mediaSource);
            if (bufferedPositionUs >= TARGET_PRELOAD_POSITION_US) {
              onContinueLoadingStopped.set(true);
              return false;
            }
            return true;
          }
        };
    ProgressiveMediaSource.Factory mediaSourceFactory =
        new ProgressiveMediaSource.Factory(
            new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()));
    mediaSourceFactory.setContinueLoadingCheckIntervalBytes(LOADING_CHECK_INTERVAL_BYTES);
    TrackSelector trackSelector =
        new DefaultTrackSelector(ApplicationProvider.getApplicationContext());
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    runMainLooperUntil(onContinueLoadingStopped::get);

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    assertThat(onPreparedCalled.get()).isTrue();
    assertThat(preloadMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
  }

  @Test
  public void preload_stopWhenPeriodPreparedByPreloadControl() throws Exception {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean();
    AtomicBoolean onPreparedCalled = new AtomicBoolean();
    AtomicReference<PreloadMediaSource> preloadMediaSourceReference = new AtomicReference<>();
    AtomicBoolean onContinueLoadingRequestedCalled = new AtomicBoolean();
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            preloadMediaSourceReference.set(mediaSource);
            onPreparedCalled.set(true);
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            onContinueLoadingRequestedCalled.set(true);
            return false;
          }
        };
    ProgressiveMediaSource.Factory mediaSourceFactory =
        new ProgressiveMediaSource.Factory(
            new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()));
    mediaSourceFactory.setContinueLoadingCheckIntervalBytes(LOADING_CHECK_INTERVAL_BYTES);
    TrackSelector trackSelector =
        new DefaultTrackSelector(ApplicationProvider.getApplicationContext());
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    runMainLooperUntil(onPreparedCalled::get);

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    assertThat(preloadMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
    assertThat(onContinueLoadingRequestedCalled.get()).isFalse();
  }

  @Test
  public void preload_stopWhenTimelineRefreshedByPreloadControl() throws Exception {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean();
    AtomicReference<PreloadMediaSource> preloadMediaSourceReference = new AtomicReference<>();
    AtomicBoolean onPreparedCalled = new AtomicBoolean();
    AtomicBoolean onContinueLoadingRequestedCalled = new AtomicBoolean();
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            preloadMediaSourceReference.set(mediaSource);
            onTimelineRefreshedCalled.set(true);
            return false;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            onContinueLoadingRequestedCalled.set(true);
            return false;
          }
        };
    ProgressiveMediaSource.Factory mediaSourceFactory =
        new ProgressiveMediaSource.Factory(
            new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()));
    TrackSelector trackSelector =
        new DefaultTrackSelector(ApplicationProvider.getApplicationContext());
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    runMainLooperUntil(onTimelineRefreshedCalled::get);

    assertThat(preloadMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
    assertThat(onPreparedCalled.get()).isFalse();
    assertThat(onContinueLoadingRequestedCalled.get()).isFalse();
  }

  @Test
  public void preload_whileSourceIsAccessedByExternalCaller_notProceedWithPreloading() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    AtomicBoolean onPreparedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return true;
          }
        };
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            new FakeMediaSourceFactory(),
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    AtomicReference<MediaSource> externalCallerMediaSourceReference = new AtomicReference<>();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerMediaSourceReference.set(source);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(externalCallerMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
    assertThat(onTimelineRefreshedCalled.get()).isFalse();
    assertThat(onPreparedCalled.get()).isFalse();
  }

  @Test
  public void
      prepareSource_beforeSourceInfoRefreshedForPreloading_onlyInvokeExternalCallerOnSourceInfoRefreshed() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    AtomicBoolean onPreparedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return true;
          }
        };
    FakeMediaSourceFactory mediaSourceFactory = new FakeMediaSourceFactory();
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    FakeMediaSource wrappedMediaSource = mediaSourceFactory.getLastCreatedSource();
    wrappedMediaSource.setAllowPreparation(false);
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    AtomicReference<MediaSource> externalCallerMediaSourceReference = new AtomicReference<>();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerMediaSourceReference.set(source);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    wrappedMediaSource.setAllowPreparation(true);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(externalCallerMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
    assertThat(onTimelineRefreshedCalled.get()).isFalse();
    assertThat(onPreparedCalled.get()).isFalse();
  }

  @Test
  public void prepareSource_afterPreload_immediatelyInvokeExternalCallerOnSourceInfoRefreshed() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    AtomicBoolean onPreparedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return true;
          }
        };
    FakeMediaSourceFactory mediaSourceFactory = new FakeMediaSourceFactory();
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    AtomicReference<MediaSource> externalCallerMediaSourceReference = new AtomicReference<>();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerMediaSourceReference.set(source);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    assertThat(onPreparedCalled.get()).isTrue();
    assertThat(externalCallerMediaSourceReference.get()).isSameInstanceAs(preloadMediaSource);
  }

  @Test
  public void createPeriodWithSameMediaPeriodIdAndStartPosition_returnExistingPeriod()
      throws Exception {
    AtomicBoolean onPreparedCalled = new AtomicBoolean();
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline());
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              doAnswer(
                      createPeriodInvocation -> {
                        MediaPeriod mediaPeriod = mock(MediaPeriod.class);
                        doAnswer(
                                prepareInvocation -> {
                                  MediaPeriod.Callback callback = prepareInvocation.getArgument(0);
                                  callback.onPrepared(mediaPeriod);
                                  return null;
                                })
                            .when(mediaPeriod)
                            .prepare(any(), anyLong());
                        return mediaPeriod;
                      })
                  .when(mockMediaSource)
                  .createPeriod(any(), any(), anyLong());
              return mockMediaSource;
            });
    TrackSelector mockTrackSelector = mock(TrackSelector.class);
    when(mockTrackSelector.selectTracks(any(), any(), any(), any()))
        .thenReturn(
            new TrackSelectorResult(
                new RendererConfiguration[0],
                new ExoTrackSelection[0],
                Tracks.EMPTY,
                /* info= */ null));
    mockTrackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            mockTrackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    AtomicReference<Timeline> externalCallerSourceInfoTimelineReference = new AtomicReference<>();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerSourceInfoTimelineReference.set(timeline);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    Pair<Object, Long> periodPosition =
        externalCallerSourceInfoTimelineReference
            .get()
            .getPeriodPositionUs(
                new Timeline.Window(),
                new Timeline.Period(),
                /* windowIndex= */ 0,
                /* windowPositionUs= */ 0L);
    MediaSource.MediaPeriodId mediaPeriodId = new MediaSource.MediaPeriodId(periodPosition.first);
    preloadMediaSource.createPeriod(mediaPeriodId, allocator, periodPosition.second);

    assertThat(onPreparedCalled.get()).isTrue();
    verify(internalSourceReference.get()).createPeriod(any(), any(), anyLong());
  }

  @Test
  public void createPeriodWithSameMediaPeriodIdAndDifferentStartPosition_returnNewPeriod()
      throws Exception {
    AtomicBoolean onPreparedCalled = new AtomicBoolean();
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            onPreparedCalled.set(true);
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline());
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              doAnswer(
                      createPeriodInvocation -> {
                        MediaPeriod mediaPeriod = mock(MediaPeriod.class);
                        doAnswer(
                                prepareInvocation -> {
                                  MediaPeriod.Callback callback = prepareInvocation.getArgument(0);
                                  callback.onPrepared(mediaPeriod);
                                  return null;
                                })
                            .when(mediaPeriod)
                            .prepare(any(), anyLong());
                        return mediaPeriod;
                      })
                  .when(mockMediaSource)
                  .createPeriod(any(), any(), anyLong());
              return mockMediaSource;
            });
    TrackSelector mockTrackSelector = mock(TrackSelector.class);
    when(mockTrackSelector.selectTracks(any(), any(), any(), any()))
        .thenReturn(
            new TrackSelectorResult(
                new RendererConfiguration[0],
                new ExoTrackSelection[0],
                Tracks.EMPTY,
                /* info= */ null));
    mockTrackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            mockTrackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());

    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    AtomicReference<Timeline> externalCallerSourceInfoTimelineReference = new AtomicReference<>();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerSourceInfoTimelineReference.set(timeline);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    // Create a period from different position.
    Pair<Object, Long> periodPosition =
        externalCallerSourceInfoTimelineReference
            .get()
            .getPeriodPositionUs(
                new Timeline.Window(),
                new Timeline.Period(),
                /* windowIndex= */ 0,
                /* windowPositionUs= */ 1L);
    MediaSource.MediaPeriodId mediaPeriodId = new MediaSource.MediaPeriodId(periodPosition.first);
    preloadMediaSource.createPeriod(mediaPeriodId, allocator, periodPosition.second);

    assertThat(onPreparedCalled.get()).isTrue();
    verify(internalSourceReference.get(), times(2)).createPeriod(any(), any(), anyLong());
  }

  @Test
  public void releaseSourceByAllExternalCallers_preloadNotCalledBefore_releaseInternalSource() {
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline(1));
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              when(mockMediaSource.createPeriod(any(), any(), anyLong()))
                  .thenReturn(mock(MediaPeriod.class));
              return mockMediaSource;
            });
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    AtomicBoolean externalCallerSourceInfoRefreshedCalled = new AtomicBoolean();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerSourceInfoRefreshedCalled.set(true);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.releaseSource(externalCaller);

    assertThat(externalCallerSourceInfoRefreshedCalled.get()).isTrue();
    MediaSource internalSource = internalSourceReference.get();
    assertThat(internalSource).isNotNull();
    verify(internalSource).releaseSource(any());
  }

  @Test
  public void releaseSourceByAllExternalCallers_stillPreloading_notReleaseInternalSource() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return true;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            return true;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return true;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline(1));
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              when(mockMediaSource.createPeriod(any(), any(), anyLong()))
                  .thenReturn(mock(MediaPeriod.class));
              return mockMediaSource;
            });
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    AtomicBoolean externalCallerSourceInfoRefreshedCalled = new AtomicBoolean();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerSourceInfoRefreshedCalled.set(true);
          }
        };
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    preloadMediaSource.releaseSource(externalCaller);

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    assertThat(externalCallerSourceInfoRefreshedCalled.get()).isTrue();
    MediaSource internalSource = internalSourceReference.get();
    assertThat(internalSource).isNotNull();
    verify(internalSource, times(0)).releaseSource(any());
  }

  @Test
  public void
      releaseSourceNotByAllExternalCallers_preloadNotCalledBefore_notReleaseInternalSource() {
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline(1));
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              when(mockMediaSource.createPeriod(any(), any(), anyLong()))
                  .thenReturn(mock(MediaPeriod.class));
              return mockMediaSource;
            });
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    AtomicBoolean externalCaller1SourceInfoRefreshedCalled = new AtomicBoolean();
    AtomicBoolean externalCaller2SourceInfoRefreshedCalled = new AtomicBoolean();
    MediaSource.MediaSourceCaller externalCaller1 =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCaller1SourceInfoRefreshedCalled.set(true);
          }
        };
    MediaSource.MediaSourceCaller externalCaller2 =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCaller2SourceInfoRefreshedCalled.set(true);
          }
        };
    preloadMediaSource.prepareSource(
        externalCaller1, bandwidthMeter.getTransferListener(), playerId);
    preloadMediaSource.prepareSource(
        externalCaller2, bandwidthMeter.getTransferListener(), playerId);
    // Only releaseSource by externalCaller1.
    preloadMediaSource.releaseSource(externalCaller1);

    assertThat(externalCaller1SourceInfoRefreshedCalled.get()).isTrue();
    assertThat(externalCaller2SourceInfoRefreshedCalled.get()).isTrue();
    MediaSource internalSource = internalSourceReference.get();
    assertThat(internalSource).isNotNull();
    verify(internalSource, times(0)).releaseSource(any());
  }

  @Test
  public void releasePreloadMediaSource_notUsedByExternalCallers_releaseInternalSource() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return false;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline(1));
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              when(mockMediaSource.createPeriod(any(), any(), anyLong()))
                  .thenReturn(mock(MediaPeriod.class));
              return mockMediaSource;
            });
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.releasePreloadMediaSource();
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    MediaSource internalSource = internalSourceReference.get();
    assertThat(internalSource).isNotNull();
    verify(internalSource).releaseSource(any());
  }

  @Test
  public void releasePreloadMediaSource_stillUsedByExternalCallers_releaseInternalSource() {
    AtomicBoolean onTimelineRefreshedCalled = new AtomicBoolean(false);
    PreloadMediaSource.PreloadControl preloadControl =
        new PreloadMediaSource.PreloadControl() {
          @Override
          public boolean onTimelineRefreshed(PreloadMediaSource mediaSource) {
            onTimelineRefreshedCalled.set(true);
            return false;
          }

          @Override
          public boolean onPrepared(PreloadMediaSource mediaSource) {
            return false;
          }

          @Override
          public boolean onContinueLoadingRequested(
              PreloadMediaSource mediaSource, long bufferedPositionUs) {
            return false;
          }
        };
    AtomicReference<MediaSource> internalSourceReference = new AtomicReference<>();
    MediaSource.Factory mockMediaSourceFactory = mock(MediaSource.Factory.class);
    when(mockMediaSourceFactory.createMediaSource(any()))
        .thenAnswer(
            invocation -> {
              MediaSource mockMediaSource = mock(MediaSource.class);
              internalSourceReference.set(mockMediaSource);
              doAnswer(
                      prepareSourceInvocation -> {
                        MediaSource.MediaSourceCaller caller =
                            prepareSourceInvocation.getArgument(0);
                        caller.onSourceInfoRefreshed(mockMediaSource, new FakeTimeline(1));
                        return null;
                      })
                  .when(mockMediaSource)
                  .prepareSource(any(), any(), any());
              when(mockMediaSource.createPeriod(any(), any(), anyLong()))
                  .thenReturn(mock(MediaPeriod.class));
              return mockMediaSource;
            });
    TrackSelector trackSelector = new FakeTrackSelector();
    trackSelector.init(() -> {}, bandwidthMeter);
    PreloadMediaSource.Factory preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mockMediaSourceFactory,
            preloadControl,
            trackSelector,
            bandwidthMeter,
            getRendererCapabilities(renderersFactory),
            allocator,
            Util.getCurrentOrMainLooper());
    PreloadMediaSource preloadMediaSource =
        preloadMediaSourceFactory.createMediaSource(
            new MediaItem.Builder()
                .setUri(Uri.parse("asset://android_asset/media/mp4/sample.mp4"))
                .build());
    AtomicBoolean externalCallerSourceInfoRefreshedCalled = new AtomicBoolean();
    MediaSource.MediaSourceCaller externalCaller =
        new MediaSource.MediaSourceCaller() {
          @Override
          public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
            externalCallerSourceInfoRefreshedCalled.set(true);
          }
        };
    preloadMediaSource.preload(/* startPositionUs= */ 0L);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.prepareSource(
        externalCaller, bandwidthMeter.getTransferListener(), playerId);
    shadowOf(Looper.getMainLooper()).idle();
    preloadMediaSource.releasePreloadMediaSource();
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(onTimelineRefreshedCalled.get()).isTrue();
    assertThat(externalCallerSourceInfoRefreshedCalled.get()).isTrue();
    MediaSource internalSource = internalSourceReference.get();
    assertThat(internalSource).isNotNull();
    verify(internalSource, times(0)).releaseSource(any());
  }

  private static RendererCapabilities[] getRendererCapabilities(RenderersFactory renderersFactory) {
    Renderer[] renderers =
        renderersFactory.createRenderers(
            Util.createHandlerForCurrentLooper(),
            mock(VideoRendererEventListener.class),
            mock(AudioRendererEventListener.class),
            mock(TextOutput.class),
            mock(MetadataOutput.class));
    RendererCapabilities[] rendererCapabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      rendererCapabilities[i] = renderers[i].getCapabilities();
    }
    return rendererCapabilities;
  }
}
