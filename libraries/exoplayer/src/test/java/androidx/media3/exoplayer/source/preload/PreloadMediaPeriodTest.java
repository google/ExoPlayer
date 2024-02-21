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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
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
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeTrackSelection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

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
  public void selectTracksForPreloadingSecondTime_forSameSelections_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    // Select tracks for preloading.
    long preloadTrackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    reset(wrappedMediaPeriod);

    // Select tracks for preloading the second time based on the same track selections.
    long newTrackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);

    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(newTrackSelectionStartPositionUs).isEqualTo(preloadTrackSelectionStartPositionUs);
  }

  @Test
  public void
      selectTracksForPreloadingSecondTime_forDifferentAdaptiveVideoSelection_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 1),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selections.
    ExoTrackSelection[] newPreloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };

    // Select tracks for preloading the second time based on the new track selections. The
    // selectTracks method of the wrapped media period must not be called again.
    long newTrackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(
            newPreloadTrackSelections, /* positionUs= */ 0L);

    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(newTrackSelectionStartPositionUs).isEqualTo(0L);
  }

  @Test
  public void
      selectTracksForPreloadingSecondTime_forDifferentAdaptiveAudioSelection_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selections.
    ExoTrackSelection[] newPreloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 1),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };

    // Select tracks for preloading the second time based on the new track selections. The
    // selectTracks method of the wrapped media period must not be called again.
    long newTrackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(
            newPreloadTrackSelections, /* positionUs= */ 0L);

    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(newTrackSelectionStartPositionUs).isEqualTo(0L);
  }

  @Test
  public void
      selectTracksForPreloadingSecondTime_forDifferentAdaptiveTextSelection_callOnWrappedPeriodRetainingPreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0), //
          // An adaptive track group for text is practically not possible. The hypothetical case has
          // been created for test coverage.
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build(),
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("en")
                      .build()),
              /* selectedIndex= */ 0)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          // An adaptive track group for text is practically not possible. The hypothetical case has
          // been created for test coverage.
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build(),
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("en")
                      .build()),
              /* selectedIndex= */ 1)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(newTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenReturn(0L);

    // Select tracks for preloading the second time based on the new track selections. The
    // selectTracks method of the wrapped media period must be called again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(newTrackSelections, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(newTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, true, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
  }

  @Test
  public void
      selectTracksForPreloadingSecondTime_forSameSelectionsButAtDifferentPosition_callOnWrappedPeriod() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* selectedIndex= */ 0)
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    // Select tracks for preloading.
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(1234L)))
        .thenReturn(1234L);

    // Select tracks for preloading based on the same track selections but at a different position.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracksForPreloading(
            preloadTrackSelections, /* positionUs= */ 1234L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(1234L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(1234L);
  }

  @Test
  public void selectTracks_afterPreloadingForSameSelections_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* track= */ 0),
          new FixedTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* track= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {new EmptySampleStream(), new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = preloadedStreams[i];
                  streamResetFlags[i] = true;
                }
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
        preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    SampleStream[] streams = new SampleStream[2];
    boolean[] streamResetFlags = new boolean[2];

    // Select tracks based on the same track selections.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            preloadTrackSelections,
            new boolean[2],
            streams,
            streamResetFlags,
            /* positionUs= */ 0L);

    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(preloadTrackSelectionStartPositionUs);
    assertThat(streams).isEqualTo(preloadedStreams);
    assertThat(streamResetFlags).asList().containsExactly(true, true);
  }

  @Test
  public void
      selectTracks_afterPreloadingForDifferentSelections_callOnWrappedPeriodRetainingPreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = preloadedStreams[i];
                  streamResetFlags[i] = true;
                }
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            eq(preloadedStreams),
            any(),
            eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("es")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] newSampleStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(newTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, true, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = newSampleStreams[i];
                  streamResetFlags[i] = true;
                }
              }
              return 0L;
            });
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Select tracks based on the new track selections.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            newTrackSelections, new boolean[3], streams, streamResetFlags, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(
                new ExoTrackSelection[] {
                  preloadTrackSelections[0], preloadTrackSelections[1], newTrackSelections[2]
                }),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, true, false}),
            eq(new SampleStream[] {preloadedStreams[0], preloadedStreams[1], newSampleStreams[2]}),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    // Use newSampleStreams instead of preloadedSampleStreams for the text track.
    assertThat(streams)
        .isEqualTo(
            new SampleStream[] {preloadedStreams[0], preloadedStreams[1], newSampleStreams[2]});
    assertThat(streamResetFlags).asList().containsExactly(true, true, true);
  }

  @Test
  public void selectTracks_afterPreloadingForDifferentAdaptiveVideoSelection_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 1),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = preloadedStreams[i];
                  streamResetFlags[i] = true;
                }
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Track selection from player must not call the wrapped media period again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            newTrackSelections, new boolean[3], streams, streamResetFlags, /* positionUs= */ 0L);

    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    assertThat(streams).isEqualTo(preloadedStreams);
    assertThat(streamResetFlags).asList().containsExactly(true, true, true);
  }

  @Test
  public void selectTracks_afterPreloadingForDifferentAdaptiveAudioSelection_usePreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(),
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = preloadedStreams[i];
                  streamResetFlags[i] = true;
                }
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(),
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()),
              /* selectedIndex= */ 1),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Track selection from player must not call the wrapped media period again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            newTrackSelections, new boolean[3], streams, streamResetFlags, /* positionUs= */ 0L);

    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    assertThat(streams).isEqualTo(preloadedStreams);
    assertThat(streamResetFlags).asList().containsExactly(true, true, true);
  }

  @Test
  public void
      selectTracks_afterPreloadingForDifferentAdaptiveTextSelection_callOnWrappedPeriodRetainingPreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] trackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0), //
          // An adaptive track group for text is practically not possible. The hypothetical case has
          // been created for test coverage.
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build(),
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("en")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
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
    preloadMediaPeriod.selectTracksForPreloading(trackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(eq(trackSelections), any(), any(), any(), /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selections.
    ExoTrackSelection[] newTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build(),
                  new Format.Builder()
                      .setWidth(3840)
                      .setHeight(2160)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          // An adaptive track group for text is practically not possible. The hypothetical case has
          // been created for test coverage.
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build(),
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("en")
                      .build()),
              /* selectedIndex= */ 1)
        };
    SampleStream[] newSampleStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(newTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = newSampleStreams[i];
                  streamResetFlags[i] = true;
                }
              }
              return 0L;
            });
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Track selection from player must call the wrapped media period again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            newTrackSelections, new boolean[3], streams, streamResetFlags, /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(
                new ExoTrackSelection[] {
                  trackSelections[0], trackSelections[1], newTrackSelections[2]
                }),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, true, false}),
            /* streams= */ eq(
                new SampleStream[] {preloadedStreams[0], preloadedStreams[1], newSampleStreams[2]}),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    assertThat(streams)
        .isEqualTo(
            new SampleStream[] {preloadedStreams[0], preloadedStreams[1], newSampleStreams[2]});
    assertThat(streamResetFlags).asList().containsExactly(true, true, true);
  }

  @Test
  public void
      selectTracks_afterPreloadingWithAudioDisabled_callOnWrappedPeriodRetainingPreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            eq(new boolean[] {false, false, false}),
            any(),
            any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    // Create a new track selection.
    ExoTrackSelection[] trackSelectionWithAudioDisabled =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          null,
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] newStreams =
        new SampleStream[] {new EmptySampleStream(), null, new EmptySampleStream()};
    ExoTrackSelection[] expectedTrackSelection =
        new ExoTrackSelection[] {preloadTrackSelections[0], null, preloadTrackSelections[2]};
    when(wrappedMediaPeriod.selectTracks(
            eq(expectedTrackSelection), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = newStreams[i];
                  streamResetFlags[i] = newStreams[i] != null;
                }
              }
              return 0L;
            });
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Track selection from player must call the wrapped media period again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            trackSelectionWithAudioDisabled,
            new boolean[3],
            streams,
            streamResetFlags,
            /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(expectedTrackSelection),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, false, true}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    assertThat(streams)
        .isEqualTo(new SampleStream[] {preloadedStreams[0], null, preloadedStreams[2]});
    assertThat(streamResetFlags).asList().containsExactly(true, false, true);
  }

  @Test
  public void
      selectTracks_afterPreloadingWithAudioEnabled_callOnWrappedPeriodRetainingPreloadedStreams() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          null,
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {new EmptySampleStream(), null, new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ eq(new SampleStream[3]),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                streams[i] = preloadedStreams[i];
                streamResetFlags[i] = preloadedStreams != null;
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    Mockito.reset(wrappedMediaPeriod);
    // Create a new track selection.
    ExoTrackSelection[] trackSelectionWithAudioEnabled =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setWidth(1920)
                      .setHeight(1080)
                      .setSampleMimeType(MimeTypes.VIDEO_H264)
                      .build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(
                  new Format.Builder()
                      .setSampleMimeType(MimeTypes.TEXT_VTT)
                      .setLanguage("de")
                      .build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] newStreams =
        new SampleStream[] {
          new EmptySampleStream(), new EmptySampleStream(), new EmptySampleStream()
        };
    ExoTrackSelection[] expectedTrackSelection =
        new ExoTrackSelection[] {
          preloadTrackSelections[0], trackSelectionWithAudioEnabled[1], preloadTrackSelections[2]
        };
    when(wrappedMediaPeriod.selectTracks(
            eq(expectedTrackSelection), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = newStreams[i];
                  streamResetFlags[i] = true;
                }
              }
              return 0L;
            });
    SampleStream[] streams = new SampleStream[3];
    boolean[] streamResetFlags = new boolean[3];

    // Track selection from player must call the wrapped media period again.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            trackSelectionWithAudioEnabled,
            /* mayRetainStreamFlags= */ new boolean[3],
            streams,
            streamResetFlags,
            /* positionUs= */ 0L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(expectedTrackSelection),
            /* mayRetainStreamFlags= */ eq(new boolean[] {true, false, true}),
            /* streams= */ eq(
                new SampleStream[] {preloadedStreams[0], newStreams[1], preloadedStreams[2]}),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(0L);
    assertThat(streams)
        .isEqualTo(new SampleStream[] {preloadedStreams[0], newStreams[1], preloadedStreams[2]});
    assertThat(streamResetFlags).asList().containsExactly(true, true, true);
  }

  @Test
  public void
      selectTracks_afterPreloadingForSameSelectionsButAtDifferentPosition_callOnWrappedPeriod() {
    MediaPeriod wrappedMediaPeriod = mock(MediaPeriod.class);
    ExoTrackSelection[] preloadTrackSelections =
        new ExoTrackSelection[] {
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
              /* selectedIndex= */ 0),
          new FakeTrackSelection(
              new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
              /* selectedIndex= */ 0)
        };
    SampleStream[] preloadedStreams =
        new SampleStream[] {new EmptySampleStream(), new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections), any(), any(), any(), /* positionUs= */ eq(0L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = preloadedStreams[i];
                  streamResetFlags[i] = true;
                }
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
    preloadMediaPeriod.selectTracksForPreloading(preloadTrackSelections, /* positionUs= */ 0L);
    verify(wrappedMediaPeriod).prepare(any(), anyLong());
    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ any(),
            /* streamResetFlags= */ any(),
            /* positionUs= */ eq(0L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    reset(wrappedMediaPeriod);
    boolean[] reselectStreamResetFlags = new boolean[2];
    SampleStream[] newStreams =
        new SampleStream[] {new EmptySampleStream(), new EmptySampleStream()};
    when(wrappedMediaPeriod.selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ eq(preloadedStreams),
            /* streamResetFlags= */ eq(reselectStreamResetFlags),
            /* positionUs= */ eq(1234L)))
        .thenAnswer(
            invocation -> {
              boolean[] mayRetainStreamFlags = invocation.getArgument(1);
              SampleStream[] streams = invocation.getArgument(2);
              boolean[] streamResetFlags = invocation.getArgument(3);
              for (int i = 0; i < streams.length; i++) {
                if (!mayRetainStreamFlags[i]) {
                  streams[i] = newStreams[i];
                  streamResetFlags[i] = true;
                }
              }
              return 1234L;
            });
    SampleStream[] reselectStreams = new SampleStream[2];

    // Select tracks based on the same track selections but at a different position.
    long trackSelectionStartPositionUs =
        preloadMediaPeriod.selectTracks(
            preloadTrackSelections,
            new boolean[2],
            reselectStreams,
            reselectStreamResetFlags,
            /* positionUs= */ 1234L);

    verify(wrappedMediaPeriod)
        .selectTracks(
            eq(preloadTrackSelections),
            /* mayRetainStreamFlags= */ eq(new boolean[] {false, false}),
            /* streams= */ eq(newStreams),
            /* streamResetFlags= */ same(reselectStreamResetFlags),
            /* positionUs= */ eq(1234L));
    verifyNoMoreInteractions(wrappedMediaPeriod);
    assertThat(trackSelectionStartPositionUs).isEqualTo(1234L);
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
    preloadMediaPeriod.selectTracksForPreloading(trackSelections, /* positionUs= */ 0L);
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
