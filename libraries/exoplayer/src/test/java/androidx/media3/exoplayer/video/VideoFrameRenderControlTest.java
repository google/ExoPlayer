/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests for {@link VideoFrameRenderControl}. */
@RunWith(AndroidJUnit4.class)
public class VideoFrameRenderControlTest {

  private static final int VIDEO_WIDTH = 640;
  private static final int VIDEO_HEIGHT = 480;

  @Test
  public void isReady_afterInstantiation_returnsFalse() {
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(
            mock(VideoFrameRenderControl.FrameRenderer.class), createVideoFrameReleaseControl());

    assertThat(videoFrameRenderControl.isReady()).isFalse();
  }

  @Test
  public void releaseFirstFrame() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    assertThat(videoFrameRenderControl.isReady()).isTrue();
    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(0L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));
  }

  @Test
  public void releaseFirstAndSecondFrame() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameReleaseControl.onStarted();
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 10_000);

    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);
    assertThat(videoFrameRenderControl.isReady()).isTrue();
    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    // First frame.
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(0L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));
    inOrder.verifyNoMoreInteractions();

    // 5 seconds pass
    clock.advanceTime(/* timeDiffMs= */ 5);
    videoFrameRenderControl.render(/* positionUs= */ 5_000, /* elapsedRealtimeUs= */ 5_000);

    // Second frame
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(10_000L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(false));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void renderFrames_withStreamOffsetSetChange_firstFrameAgain() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameReleaseControl.onStarted();
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onStreamOffsetChange(
        /* presentationTimeUs= */ 0, /* streamOffsetUs= */ 10_000);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    assertThat(videoFrameRenderControl.isReady()).isTrue();
    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    // First frame has the first stream offset.
    inOrder.verify(frameRenderer).renderFrame(anyLong(), eq(0L), eq(10_000L), eq(true));
    inOrder.verifyNoMoreInteractions();

    // 10 milliseconds pass
    clock.advanceTime(/* timeDiffMs= */ 10);
    videoFrameRenderControl.onStreamOffsetChange(
        /* presentationTimeUs= */ 10_000, /* streamOffsetUs= */ 20_000);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 10_000);
    videoFrameRenderControl.render(/* positionUs= */ 10_000, /* elapsedRealtimeUs= */ 0);

    // Second frame has the second stream offset and it is also a first frame.
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(10_000L),
            /* streamOffsetUs= */ eq(20_000L),
            /* isFirstFrame= */ eq(true));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void dropFrames() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        createVideoFrameReleaseControl(
            new TestFrameTimingEvaluator(
                /* shouldForceReleaseFrames= */ false,
                /* shouldDropFrames= */ true,
                /* shouldIgnoreFrames= */ false));
    videoFrameReleaseControl.setClock(clock);
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameReleaseControl.onStarted();
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 10_000);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    // First frame was rendered because the fist frame is force released.
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(0L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));
    inOrder.verifyNoMoreInteractions();

    clock.advanceTime(/* timeDiffMs= */ 100);
    videoFrameRenderControl.render(/* positionUs= */ 100_000, /* elapsedRealtimeUs= */ 100_000);

    // Second frame was dropped.
    inOrder.verify(frameRenderer).dropFrame();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void flush_removesAvailableFramesForRendering_doesNotFlushOnVideoSizeChange()
      throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameReleaseControl.onStarted();
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.flush();
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder.verifyNoMoreInteractions();

    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 10_000);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    // First frame was rendered with pending video size change.
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(10_000L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void hasReleasedFrame_noFrameReleased_returnsFalse() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(
            mock(VideoFrameRenderControl.FrameRenderer.class), videoFrameReleaseControl);

    assertThat(videoFrameRenderControl.hasReleasedFrame(/* presentationTimeUs= */ 0)).isFalse();
  }

  @Test
  public void hasReleasedFrame_frameIsReleased_returnsTrue() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(0L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));
    assertThat(videoFrameRenderControl.hasReleasedFrame(/* presentationTimeUs= */ 0)).isTrue();
  }

  @Test
  public void hasReleasedFrame_frameIsReleasedAndFlushed_returnsFalse() throws Exception {
    VideoFrameRenderControl.FrameRenderer frameRenderer =
        mock(VideoFrameRenderControl.FrameRenderer.class);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    VideoFrameRenderControl videoFrameRenderControl =
        new VideoFrameRenderControl(frameRenderer, videoFrameReleaseControl);

    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameRenderControl.onOutputSizeChanged(
        /* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT);
    videoFrameRenderControl.onOutputFrameAvailableForRendering(/* presentationTimeUs= */ 0);
    videoFrameRenderControl.render(/* positionUs= */ 0, /* elapsedRealtimeUs= */ 0);

    InOrder inOrder = Mockito.inOrder(frameRenderer);
    inOrder
        .verify(frameRenderer)
        .onVideoSizeChanged(new VideoSize(/* width= */ VIDEO_WIDTH, /* height= */ VIDEO_HEIGHT));
    inOrder
        .verify(frameRenderer)
        .renderFrame(
            /* renderTimeNs= */ anyLong(),
            /* presentationTimeUs= */ eq(0L),
            /* streamOffsetUs= */ eq(0L),
            /* isFirstFrame= */ eq(true));

    videoFrameRenderControl.flush();

    assertThat(videoFrameRenderControl.hasReleasedFrame(/* presentationTimeUs= */ 0)).isFalse();
  }

  private static VideoFrameReleaseControl createVideoFrameReleaseControl() {
    return createVideoFrameReleaseControl(
        new TestFrameTimingEvaluator(
            /* shouldForceReleaseFrames= */ false,
            /* shouldDropFrames= */ false,
            /* shouldIgnoreFrames= */ false));
  }

  private static VideoFrameReleaseControl createVideoFrameReleaseControl(
      VideoFrameReleaseControl.FrameTimingEvaluator frameTimingEvaluator) {
    return new VideoFrameReleaseControl(
        ApplicationProvider.getApplicationContext(),
        frameTimingEvaluator,
        /* allowedJoiningTimeMs= */ 0);
  }

  private static class TestFrameTimingEvaluator
      implements VideoFrameReleaseControl.FrameTimingEvaluator {
    private final boolean shouldForceReleaseFrames;
    private final boolean shouldDropFrames;
    private final boolean shouldIgnoreFrames;

    public TestFrameTimingEvaluator(
        boolean shouldForceReleaseFrames, boolean shouldDropFrames, boolean shouldIgnoreFrames) {
      this.shouldForceReleaseFrames = shouldForceReleaseFrames;
      this.shouldDropFrames = shouldDropFrames;
      this.shouldIgnoreFrames = shouldIgnoreFrames;
    }

    @Override
    public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
      return shouldForceReleaseFrames;
    }

    @Override
    public boolean shouldDropFrame(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
      return shouldDropFrames;
    }

    @Override
    public boolean shouldIgnoreFrame(
        long earlyUs,
        long positionUs,
        long elapsedRealtimeUs,
        boolean isLastFrame,
        boolean treatDroppedBuffersAsSkipped)
        throws ExoPlaybackException {
      return shouldIgnoreFrames;
    }
  }
}
