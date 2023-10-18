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

import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link VideoFrameReleaseControl}. */
@RunWith(AndroidJUnit4.class)
public class VideoFrameReleaseControlTest {
  @Test
  public void isReady_onNewInstance_returnsFalse() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ true)).isFalse();
    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ false)).isFalse();
  }

  @Test
  public void isReady_afterReleasingFrame_returnsTrue() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ true)).isTrue();
  }

  @Test
  public void isReady_withinJoiningDeadline_returnsTrue() {
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        createVideoFrameReleaseControl(/* allowedJoiningTimeMs= */ 100);
    videoFrameReleaseControl.setClock(clock);

    videoFrameReleaseControl.join();

    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ false)).isTrue();
  }

  @Test
  public void isReady_joiningDeadlineExceeded_returnsFalse() {
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        createVideoFrameReleaseControl(/* allowedJoiningTimeMs= */ 100);
    videoFrameReleaseControl.setClock(clock);

    videoFrameReleaseControl.join();
    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ false)).isTrue();

    clock.advanceTime(/* timeDiffMs= */ 101);

    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ false)).isFalse();
  }

  @Test
  public void onFrameReleasedIsFirstFrame_resetsAfterOnEnabled() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
  }

  @Test
  public void onFrameReleasedIsFirstFrame_resetsAfterOnProcessedStreamChange() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
    videoFrameReleaseControl.onProcessedStreamChange();

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
  }

  @Test
  public void onFrameReleasedIsFirstFrame_resetsAfterSetOutputSurface() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
    videoFrameReleaseControl.setOutputSurface(/* outputSurface= */ null);

    assertThat(videoFrameReleaseControl.onFrameReleasedIsFirstFrame()).isTrue();
  }

  @Test
  public void isReady_afterReset_returnsFalse() {
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();

    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ true)).isTrue();
    videoFrameReleaseControl.reset();

    assertThat(videoFrameReleaseControl.isReady(/* rendererReady= */ true)).isFalse();
  }

  @Test
  public void getFrameReleaseAction_firstFrameAllowedBeforeStart_returnsReleaseImmediately() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
  }

  @Test
  public void getFrameReleaseAction_firstFrameNotAllowedBeforeStart_returnsTryAgainLater() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ false);

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER);
  }

  @Test
  public void
      getFrameReleaseAction_firstFrameNotAllowedBeforeStartAndStarted_returnsReleaseImmediately() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ false);

    videoFrameReleaseControl.onStarted();

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
  }

  @Test
  public void getFrameReleaseAction_secondFrameAndNotStarted_returnsTryAgainLater() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();

    // Second frame
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 10_000,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER);
  }

  @Test
  public void getFrameReleaseAction_secondFrameAndStarted_returnsScheduled() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);
    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();

    // Second frame
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 10_000,
                /* positionUs= */ 1,
                /* elapsedRealtimeUs= */ 1,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_SCHEDULED);
  }

  @Test
  public void getFrameReleaseAction_secondFrameEarly_returnsTryAgainLater() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 10);

    // Second frame is 51 ms too soon.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 61_000,
                /* positionUs= */ 10_000,
                /* elapsedRealtimeUs= */ 10_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER);
    assertThat(frameReleaseInfo.getEarlyUs()).isEqualTo(51_000);
  }

  @Test
  public void getFrameReleaseAction_frameLate_returnsDrop() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl = createVideoFrameReleaseControl();
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 40);

    // Second frame is 31 ms late.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 9_000,
                /* positionUs= */ 40_000,
                /* elapsedRealtimeUs= */ 40_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_DROP);
  }

  @Test
  public void getFrameReleaseAction_frameLateWhileJoining_returnsSkip() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        createVideoFrameReleaseControl(/* allowedJoiningTimeMs= */ 1234);
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 40);

    // Start joining.
    videoFrameReleaseControl.join();

    // Second frame is 31 ms late.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 9_000,
                /* positionUs= */ 40_000,
                /* elapsedRealtimeUs= */ 40_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_SKIP);
  }

  @Test
  public void getFrameReleaseAction_frameVeryLate_returnsDropToKeyframe() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        new VideoFrameReleaseControl(
            ApplicationProvider.getApplicationContext(), /* allowedJoiningTimeMs= */ 0);
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.setFrameTimingEvaluator(
        new TestFrameTimingEvaluator(/* shouldForceRelease= */ false));
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 1_000);

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 1_000,
                /* positionUs= */ 1_000_000,
                /* elapsedRealtimeUs= */ 1_000_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_DROP_TO_KEYFRAME);
  }

  @Test
  public void getFrameReleaseAction_frameVeryLateAndJoining_returnsSkipToFrame() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        createVideoFrameReleaseControl(/* allowedJoiningTimeMs= */ 1234);
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 1_000);
    videoFrameReleaseControl.join();

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 1_000,
                /* positionUs= */ 1_000_000,
                /* elapsedRealtimeUs= */ 1_000_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_SKIP_TO_KEYFRAME);
  }

  @Test
  public void
      getFrameReleaseAction_frameVeryLateAndFrameTimeEvaluatorForcesFrameRelease_returnsReleaseImmediately() {
    VideoFrameReleaseControl.FrameReleaseInfo frameReleaseInfo =
        new VideoFrameReleaseControl.FrameReleaseInfo();
    FakeClock clock = new FakeClock(/* isAutoAdvancing= */ false);
    VideoFrameReleaseControl videoFrameReleaseControl =
        new VideoFrameReleaseControl(
            ApplicationProvider.getApplicationContext(), /* allowedJoiningTimeMs= */ 0);
    videoFrameReleaseControl.setFrameTimingEvaluator(
        /* frameTimingEvaluator= */ new TestFrameTimingEvaluator(/* shouldForceRelease= */ true));
    videoFrameReleaseControl.setClock(clock);
    videoFrameReleaseControl.onEnabled(/* releaseFirstFrameBeforeStarted= */ true);

    videoFrameReleaseControl.onStarted();

    // First frame released.
    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 0,
                /* positionUs= */ 0,
                /* elapsedRealtimeUs= */ 0,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
    videoFrameReleaseControl.onFrameReleasedIsFirstFrame();
    clock.advanceTime(/* timeDiffMs= */ 1_000);

    assertThat(
            videoFrameReleaseControl.getFrameReleaseAction(
                /* presentationTimeUs= */ 1_000,
                /* positionUs= */ 1_000_000,
                /* elapsedRealtimeUs= */ 1_000_000,
                /* outputStreamStartPositionUs= */ 0,
                /* isLastFrame= */ false,
                frameReleaseInfo))
        .isEqualTo(VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
  }

  private static VideoFrameReleaseControl createVideoFrameReleaseControl() {
    return createVideoFrameReleaseControl(/* allowedJoiningTimeMs= */ 0);
  }

  private static VideoFrameReleaseControl createVideoFrameReleaseControl(
      long allowedJoiningTimeMs) {
    return new VideoFrameReleaseControl(
        ApplicationProvider.getApplicationContext(), allowedJoiningTimeMs);
  }

  private static class TestFrameTimingEvaluator
      implements VideoFrameReleaseControl.FrameTimingEvaluator {

    private final boolean shouldForceRelease;

    public TestFrameTimingEvaluator(boolean shouldForceRelease) {
      this.shouldForceRelease = shouldForceRelease;
    }

    @Override
    public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
      return shouldForceRelease;
    }

    @Override
    public boolean shouldDropFrame(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
      return VideoFrameReleaseControl.FrameTimingEvaluator.DEFAULT.shouldDropFrame(
          earlyUs, elapsedRealtimeUs, isLastFrame);
    }

    @Override
    public boolean shouldDropFramesToKeyframe(
        long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
      return VideoFrameReleaseControl.FrameTimingEvaluator.DEFAULT.shouldDropFramesToKeyframe(
          earlyUs, elapsedRealtimeUs, isLastFrame);
    }
  }
}
