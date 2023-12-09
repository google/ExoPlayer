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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.EmptySampleStream;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeTimeline;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PreloadMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public final class PreloadMediaPeriodTest {

  @Test
  public void preload_prepareWrappedPeriodAndInvokeCallbackOnPrepared() {
    MediaSource.MediaPeriodId mediaPeriodId =
        new MediaSource.MediaPeriodId(/* periodUid= */ new Object());
    FakeMediaPeriod wrappedMediaPeriod =
        new FakeMediaPeriod(
            TrackGroupArray.EMPTY,
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            new MediaSourceEventListener.EventDispatcher()
                .withParameters(/* windowIndex= */ 0, mediaPeriodId));
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    AtomicBoolean onPreparedCallbackCalled = new AtomicBoolean();
    AtomicReference<MediaPeriod> mediaPeriodReference = new AtomicReference<>();

    preloadMediaPeriod.preload(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            mediaPeriodReference.set(mediaPeriod);
            onPreparedCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        },
        /* positionUs= */ 0L);

    // Wrapped media period is called `prepare` for once.
    assertThat(onPreparedCallbackCalled.get()).isTrue();
    assertThat(mediaPeriodReference.get()).isSameInstanceAs(preloadMediaPeriod);
  }

  @Test
  public void prepareBeforePreload_prepareWrappedPeriodAndInvokeCallbackOnPrepared() {
    MediaSource.MediaPeriodId mediaPeriodId =
        new MediaSource.MediaPeriodId(/* periodUid= */ new Object());
    FakeMediaPeriod wrappedMediaPeriod =
        new FakeMediaPeriod(
            TrackGroupArray.EMPTY,
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            new MediaSourceEventListener.EventDispatcher()
                .withParameters(/* windowIndex= */ 0, mediaPeriodId));
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    AtomicBoolean onPreparedCallbackCalled = new AtomicBoolean();
    AtomicReference<MediaPeriod> mediaPeriodReference = new AtomicReference<>();

    preloadMediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            mediaPeriodReference.set(mediaPeriod);
            onPreparedCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        },
        /* positionUs= */ 0L);

    assertThat(onPreparedCallbackCalled.get()).isTrue();
    assertThat(mediaPeriodReference.get()).isSameInstanceAs(preloadMediaPeriod);
  }

  @Test
  public void prepareBeforeWrappedPeriodPreparedByPreloading_invokeLatestCallbackOnPrepared() {
    MediaSource.MediaPeriodId mediaPeriodId =
        new MediaSource.MediaPeriodId(/* periodUid= */ new Object());
    FakeMediaPeriod wrappedMediaPeriod =
        new FakeMediaPeriod(
            TrackGroupArray.EMPTY,
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            new MediaSourceEventListener.EventDispatcher()
                .withParameters(/* windowIndex= */ 0, mediaPeriodId),
            DrmSessionManager.DRM_UNSUPPORTED,
            new DrmSessionEventListener.EventDispatcher()
                .withParameters(/* windowIndex= */ 0, mediaPeriodId),
            /* deferOnPrepared= */ true);
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    AtomicBoolean onPreparedOfPreloadCallbackCalled = new AtomicBoolean();
    AtomicBoolean onPreparedOfPrepareCallbackCalled = new AtomicBoolean();
    AtomicReference<MediaPeriod> mediaPeriodReference = new AtomicReference<>();
    MediaPeriod.Callback preloadCallback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            onPreparedOfPreloadCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    MediaPeriod.Callback prepareCallback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            mediaPeriodReference.set(mediaPeriod);
            onPreparedOfPrepareCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };

    preloadMediaPeriod.preload(preloadCallback, /* positionUs= */ 0L);
    preloadMediaPeriod.prepare(prepareCallback, /* positionUs= */ 0L);
    wrappedMediaPeriod.setPreparationComplete();
    shadowOf(Looper.getMainLooper()).idle();

    // Should only invoke the latest callback.
    assertThat(onPreparedOfPreloadCallbackCalled.get()).isFalse();
    assertThat(onPreparedOfPrepareCallbackCalled.get()).isTrue();
    assertThat(mediaPeriodReference.get()).isSameInstanceAs(preloadMediaPeriod);
  }

  @Test
  public void prepareAfterWrappedPeriodPreparedByPreloading_immediatelyInvokeCallbackOnPrepared() {
    MediaSource.MediaPeriodId mediaPeriodId =
        new MediaSource.MediaPeriodId(/* periodUid= */ new Object());
    FakeMediaPeriod wrappedMediaPeriod =
        new FakeMediaPeriod(
            TrackGroupArray.EMPTY,
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            new MediaSourceEventListener.EventDispatcher()
                .withParameters(/* windowIndex= */ 0, mediaPeriodId));
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    AtomicBoolean onPreparedOfPreloadCallbackCalled = new AtomicBoolean();
    AtomicBoolean onPreparedOfPrepareCallbackCalled = new AtomicBoolean();
    AtomicReference<MediaPeriod> mediaPeriodReference = new AtomicReference<>();
    MediaPeriod.Callback preloadCallback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            onPreparedOfPreloadCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    MediaPeriod.Callback prepareCallback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            mediaPeriodReference.set(mediaPeriod);
            onPreparedOfPrepareCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };

    preloadMediaPeriod.preload(preloadCallback, /* positionUs= */ 0L);
    preloadMediaPeriod.prepare(prepareCallback, /* positionUs= */ 0L);

    assertThat(onPreparedOfPreloadCallbackCalled.get()).isTrue();
    assertThat(onPreparedOfPrepareCallbackCalled.get()).isTrue();
    assertThat(mediaPeriodReference.get()).isSameInstanceAs(preloadMediaPeriod);
  }

  @Test
  public void selectTracks_afterPreloadingForSameSelections_usePreloadedResults() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] trackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    TrackSelectorResult trackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[] {
              RendererConfiguration.DEFAULT, RendererConfiguration.DEFAULT
            },
            trackSelections,
            Tracks.EMPTY,
            /* info= */ null);
    SampleStream[] preloadedStreams =
        new SampleStream[] {new EmptySampleStream(), new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                streams[i] = preloadedStreams[i];
                streamResetFlags[i] = true;
              }
              return 0L;
            });
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    MediaPeriod.Callback callback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {}

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    preloadMediaPeriod.prepare(callback, /* positionUs= */ 0L);

    // Select tracks for preloading.
    long preloadTrackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(trackSelectorResult, /* positionUs= */ 0L);
    SampleStream[] streams = new SampleStream[2];
    boolean[] streamResetFlags = new boolean[2];
    // Select tracks based on the same track selections.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            trackSelections, new boolean[2], streams, streamResetFlags, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    assertThat(trackSelectionStartPositionUs).isEqualTo(preloadTrackSelectionStartPositionUs);
    assertThat(streams).isEqualTo(preloadedStreams);
    assertThat(streamResetFlags).hasLength(2);
    assertThat(streamResetFlags[0]).isTrue();
    assertThat(streamResetFlags[1]).isTrue();
  }

  @Test
  public void selectTracks_afterPreloadingButForDifferentSelections_callOnWrappedPeriod() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] trackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    TrackSelectorResult trackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[] {
              RendererConfiguration.DEFAULT, RendererConfiguration.DEFAULT
            },
            trackSelections,
            Tracks.EMPTY,
            /* info= */ null);
    SampleStream[] preloadedStreams =
        new SampleStream[] {new EmptySampleStream(), new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                streams[i] = preloadedStreams[i];
                streamResetFlags[i] = true;
              }
              return 0L;
            });
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    MediaPeriod.Callback callback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {}

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    preloadMediaPeriod.prepare(callback, /* positionUs= */ 0L);

    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(trackSelectorResult, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod)
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          null,
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    SampleStream[] newSampleStreams = new SampleStream[] {new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(newTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                streams[i] = newSampleStreams[i];
                streamResetFlags[i] = true;
              }
              return 0L;
            });
    SampleStream[] streams = new SampleStream[1];
    boolean[] streamResetFlags = new boolean[1];
    // Select tracks based on the new track selections.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            newTrackSelections, new boolean[1], streams, streamResetFlags, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(eq(newTrackSelections), any(), same(streams), same(streamResetFlags), eq(0L));
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    // Use newSampleStreams instead of preloadedSampleStreams
    assertThat(streams).isEqualTo(newSampleStreams);
    assertThat(streamResetFlags).hasLength(1);
    assertThat(streamResetFlags[0]).isTrue();
  }

  @Test
  public void
      selectTracks_afterPreloadingForSameSelectionsButAtDifferentPosition_callOnWrappedPeriod() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] trackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    TrackSelectorResult trackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[] {
              RendererConfiguration.DEFAULT, RendererConfiguration.DEFAULT
            },
            trackSelections,
            Tracks.EMPTY,
            /* info= */ null);
    when(wrappedMediaPeriod.selectTracks(eq(trackSelections), any(), any(), any(), anyLong()))
        .thenAnswer(invocation -> invocation.getArgument(4, Long.class));
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    MediaPeriod.Callback callback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {}

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    preloadMediaPeriod.prepare(callback, /* positionUs= */ 0L);

    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(trackSelectorResult, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod)
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    SampleStream[] streams = new SampleStream[2];
    boolean[] streamResetFlags = new boolean[2];
    // Select tracks based on the same track selections but at a different position.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            trackSelections, new boolean[2], streams, streamResetFlags, /* positionUs= */ 10L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(trackSelections),
            any(),
            same(streams),
            same(streamResetFlags),
            /* positionUs= */ eq(10L));
    assertThat(trackSelectionStartPositionUs).isEqualTo(10L);
  }

  @Test
  public void selectTracks_theSecondCallAfterPreloading_callOnWrappedPeriod() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] trackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    TrackSelectorResult trackSelectorResult =
        new TrackSelectorResult(
            new RendererConfiguration[] {
              RendererConfiguration.DEFAULT, RendererConfiguration.DEFAULT
            },
            trackSelections,
            Tracks.EMPTY,
            /* info= */ null);
    when(wrappedMediaPeriod.selectTracks(
            eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenReturn(0L);
    PreloadMediaPeriod preloadMediaPeriod = new PreloadMediaPeriod(wrappedMediaPeriod);
    MediaPeriod.Callback callback =
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {}

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {}
        };
    preloadMediaPeriod.prepare(callback, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), /* positionUs= */ eq(0L));

    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(trackSelectorResult, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod)
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    SampleStream[] streams = new SampleStream[2];
    boolean[] streamResetFlags = new boolean[2];
    // First `selectTracks` call based on the same track selections at the same position.
    preloadMediaPeriod.selectTracks(
        trackSelections, new boolean[2], streams, streamResetFlags, /* positionUs= */ 0L);
    // Second `selectTracks` call based on the same track selections at the same position.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            trackSelections, new boolean[2], streams, streamResetFlags, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod, times(2))
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
  }
}
