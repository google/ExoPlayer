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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.DefaultMediaClock.PlaybackParametersListener;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.testutil.FakeMediaClockRenderer;
import com.google.android.exoplayer2.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for {@link DefaultMediaClock}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaClockTest {

  private static final long TEST_POSITION_US = 123456789012345678L;
  private static final long SLEEP_TIME_MS = 1_000;
  private static final PlaybackParameters TEST_PLAYBACK_PARAMETERS =
      new PlaybackParameters(/* speed= */ 2f);

  @Mock private PlaybackParametersListener listener;
  private FakeClock fakeClock;
  private DefaultMediaClock mediaClock;

  @Before
  public void initMediaClockWithFakeClock() {
    initMocks(this);
    fakeClock = new FakeClock(0);
    mediaClock = new DefaultMediaClock(listener, fakeClock);
  }

  @Test
  public void standaloneResetPosition_getPositionShouldReturnSameValue() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void standaloneGetAndResetPosition_shouldNotTriggerCallback() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void standaloneClock_shouldNotAutoStart() throws Exception {
    assertClockIsStopped();
  }

  @Test
  public void standaloneResetPosition_shouldNotStartClock() throws Exception {
    mediaClock.resetPosition(TEST_POSITION_US);
    assertClockIsStopped();
  }

  @Test
  public void standaloneStart_shouldStartClock() throws Exception {
    mediaClock.start();
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void standaloneStop_shouldKeepClockStopped() throws Exception {
    mediaClock.stop();
    assertClockIsStopped();
  }

  @Test
  public void standaloneStartAndStop_shouldStopClock() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    assertClockIsStopped();
  }

  @Test
  public void standaloneStartStopStart_shouldRestartClock() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    mediaClock.start();
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void standaloneStartAndStop_shouldNotTriggerCallback() throws Exception {
    mediaClock.start();
    mediaClock.stop();
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void standaloneGetPlaybackParameters_initializedWithDefaultPlaybackParameters() {
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void standaloneSetPlaybackParameters_getPlaybackParametersShouldReturnSameValue() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void standaloneSetPlaybackParameters_shouldNotTriggerCallback() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void standaloneSetPlaybackParameters_shouldApplyNewPlaybackParameters() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    mediaClock.start();
    // Asserts that clock is running with speed declared in getPlaybackParameters().
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void standaloneSetOtherPlaybackParameters_getPlaybackParametersShouldReturnSameValue() {
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    mediaClock.setPlaybackParameters(PlaybackParameters.DEFAULT);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void enableRendererMediaClock_shouldOverwriteRendererPlaybackParametersIfPossible()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void enableRendererMediaClockWithFixedPlaybackSpeed_usesRendererPlaybackSpeed()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void enableRendererMediaClockWithFixedPlaybackSpeed_shouldTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void enableRendererMediaClockWithFixedButSamePlaybackSpeed_shouldNotTriggerCallback()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            PlaybackParameters.DEFAULT, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void disableRendererMediaClock_shouldKeepPlaybackSpeed() throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(TEST_PLAYBACK_PARAMETERS, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    mediaClock.onRendererDisabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererClockSetPlaybackSpeed_getPlaybackParametersShouldReturnSameValue()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            PlaybackParameters.DEFAULT, /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererClockSetPlaybackSpeed_shouldNotTriggerCallback() throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            PlaybackParameters.DEFAULT, /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void rendererClockSetPlaybackSpeedOverwrite_getPlaybackParametersShouldReturnSameValue()
      throws ExoPlaybackException {
    FakeMediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            PlaybackParameters.DEFAULT, /* playbackParametersAreMutable= */ false);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    mediaClock.setPlaybackParameters(TEST_PLAYBACK_PARAMETERS);
    assertThat(mediaClock.getPlaybackParameters()).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void enableRendererMediaClock_usesRendererClockPosition() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
    // We're not advancing the renderer media clock. Thus, the clock should appear to be stopped.
    assertClockIsStopped();
  }

  @Test
  public void resetPositionWhileUsingRendererMediaClock_shouldHaveNoEffect()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
    mediaClock.resetPosition(0);
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void disableRendererMediaClock_standaloneShouldBeSynced() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    mediaClock.onRendererDisabled(mediaClockRenderer);
    fakeClock.advanceTime(SLEEP_TIME_MS);
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US + Util.msToUs(SLEEP_TIME_MS));
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void getPositionWithPlaybackSpeedChange_shouldTriggerCallback()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            PlaybackParameters.DEFAULT, /* playbackParametersAreMutable= */ true);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    // Silently change playback speed of renderer clock.
    mediaClockRenderer.playbackParameters = TEST_PLAYBACK_PARAMETERS;
    mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    verify(listener).onPlaybackParametersChanged(TEST_PLAYBACK_PARAMETERS);
  }

  @Test
  public void rendererNotReady_shouldStillUseRendererClock() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            /* isReady= */ false, /* isEnded= */ false, /* hasReadStreamToEnd= */ false);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    // We're not advancing the renderer media clock. Thus, the clock should appear to be stopped.
    assertClockIsStopped();
  }

  @Test
  public void rendererNotReadyAndReadStreamToEnd_shouldFallbackToStandaloneClock()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            /* isReady= */ false, /* isEnded= */ false, /* hasReadStreamToEnd= */ true);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void rendererNotReadyAndReadingAhead_shouldFallbackToStandaloneClock()
      throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            /* isReady= */ false, /* isEnded= */ false, /* hasReadStreamToEnd= */ false);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertClockIsRunning(/* isReadingAhead= */ true);
  }

  @Test
  public void rendererEnded_shouldFallbackToStandaloneClock() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer =
        new MediaClockRenderer(
            /* isReady= */ true, /* isEnded= */ true, /* hasReadStreamToEnd= */ true);
    mediaClock.start();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    assertClockIsRunning(/* isReadingAhead= */ false);
  }

  @Test
  public void staleDisableRendererClock_shouldNotThrow() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    mediaClock.onRendererDisabled(mediaClockRenderer);
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(Util.msToUs(fakeClock.elapsedRealtime()));
  }

  @Test
  public void enableSameRendererClockTwice_shouldNotThrow() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer = new MediaClockRenderer();
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClock.onRendererEnabled(mediaClockRenderer);
    mediaClockRenderer.positionUs = TEST_POSITION_US;
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
  }

  @Test
  public void enableOtherRendererClock_shouldThrow() throws ExoPlaybackException {
    MediaClockRenderer mediaClockRenderer1 = new MediaClockRenderer();
    MediaClockRenderer mediaClockRenderer2 = new MediaClockRenderer();
    mediaClockRenderer1.positionUs = TEST_POSITION_US;
    mediaClock.onRendererEnabled(mediaClockRenderer1);
    try {
      mediaClock.onRendererEnabled(mediaClockRenderer2);
      fail();
    } catch (ExoPlaybackException e) {
      // Expected.
    }
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(TEST_POSITION_US);
  }

  private void assertClockIsRunning(boolean isReadingAhead) {
    long clockStartUs = mediaClock.syncAndGetPositionUs(isReadingAhead);
    fakeClock.advanceTime(SLEEP_TIME_MS);
    int scaledUsPerMs = Math.round(mediaClock.getPlaybackParameters().speed * 1000f);
    assertThat(mediaClock.syncAndGetPositionUs(isReadingAhead))
        .isEqualTo(clockStartUs + (SLEEP_TIME_MS * scaledUsPerMs));
  }

  private void assertClockIsStopped() {
    long positionAtStartUs = mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false);
    fakeClock.advanceTime(SLEEP_TIME_MS);
    assertThat(mediaClock.syncAndGetPositionUs(/* isReadingAhead= */ false))
        .isEqualTo(positionAtStartUs);
  }

  @SuppressWarnings("HidingField")
  private static class MediaClockRenderer extends FakeMediaClockRenderer {

    private final boolean playbackParametersAreMutable;
    private final boolean isReady;
    private final boolean isEnded;

    public PlaybackParameters playbackParameters;
    public long positionUs;

    public MediaClockRenderer() throws ExoPlaybackException {
      this(
          PlaybackParameters.DEFAULT,
          /* playbackParametersAreMutable= */ false,
          /* isReady= */ true,
          /* isEnded= */ false,
          /* hasReadStreamToEnd= */ false);
    }

    public MediaClockRenderer(
        PlaybackParameters playbackParameters, boolean playbackParametersAreMutable)
        throws ExoPlaybackException {
      this(
          playbackParameters,
          playbackParametersAreMutable,
          /* isReady= */ true,
          /* isEnded= */ false,
          /* hasReadStreamToEnd= */ false);
    }

    public MediaClockRenderer(boolean isReady, boolean isEnded, boolean hasReadStreamToEnd)
        throws ExoPlaybackException {
      this(
          PlaybackParameters.DEFAULT,
          /* playbackParametersAreMutable= */ false,
          isReady,
          isEnded,
          hasReadStreamToEnd);
    }

    private MediaClockRenderer(
        PlaybackParameters playbackParameters,
        boolean playbackParametersAreMutable,
        boolean isReady,
        boolean isEnded,
        boolean hasReadStreamToEnd)
        throws ExoPlaybackException {
      super(C.TRACK_TYPE_UNKNOWN);
      this.playbackParameters = playbackParameters;
      this.playbackParametersAreMutable = playbackParametersAreMutable;
      this.isReady = isReady;
      this.isEnded = isEnded;
      this.positionUs = TEST_POSITION_US;
      if (!hasReadStreamToEnd) {
        resetPosition(0);
      }
    }

    @Override
    public long getPositionUs() {
      return positionUs;
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
      if (playbackParametersAreMutable) {
        this.playbackParameters = playbackParameters;
      }
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
      return playbackParameters;
    }

    @Override
    public boolean isReady() {
      return isReady;
    }

    @Override
    public boolean isEnded() {
      return isEnded;
    }
  }
}
