/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem.LiveConfiguration;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit test for {@link DefaultLivePlaybackSpeedControl}. */
@RunWith(AndroidJUnit4.class)
public class DefaultLivePlaybackSpeedControlTest {

  @Test
  public void getTargetLiveOffsetUs_returnsUnset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();

    assertThat(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs()).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void getTargetLiveOffsetUs_afterUpdateLiveConfiguration_usesMediaLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42, /* minPlaybackSpeed= */ 1f, /* maxPlaybackSpeed= */ 1f));

    assertThat(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs()).isEqualTo(42_000);
  }

  @Test
  public void getTargetLiveOffsetUs_withOverrideTargetLiveOffsetUs_usesOverride() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();

    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42, /* minPlaybackSpeed= */ 1f, /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(123_456_789);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterOverrideTargetLiveOffset_withoutMediaConfiguration_returnsUnset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterOverrideTargetLiveOffsetUsWithTimeUnset_usesMediaLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42, /* minPlaybackSpeed= */ 1f, /* maxPlaybackSpeed= */ 1f));
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(C.TIME_UNSET);

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(42_000);
  }

  @Test
  public void adjustPlaybackSpeed_liveOffsetMatchesTargetOffset_returnsUnitSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_000_000);

    assertThat(adjustedSpeed).isEqualTo(1f);
  }

  @Test
  public void adjustPlaybackSpeed_liveOffsetWithinAcceptableErrorMargin_returnsUnitSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeedJustAboveLowerErrorMargin =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_000_000
                - DefaultLivePlaybackSpeedControl.MAXIMUM_LIVE_OFFSET_ERROR_US_FOR_UNIT_SPEED
                + 1);
    float adjustedSpeedJustBelowUpperErrorMargin =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_000_000
                + DefaultLivePlaybackSpeedControl.MAXIMUM_LIVE_OFFSET_ERROR_US_FOR_UNIT_SPEED
                - 1);

    assertThat(adjustedSpeedJustAboveLowerErrorMargin).isEqualTo(1f);
    assertThat(adjustedSpeedJustBelowUpperErrorMargin).isEqualTo(1f);
  }

  @Test
  public void adjustPlaybackSpeed_withLiveOffsetGreaterThanTargetOffset_returnsAdjustedSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setProportionalControlFactor(0.01f).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_500_000);

    float expectedSpeedAccordingToDocumentation = 1f + 0.01f * (2.5f - 2f);
    assertThat(adjustedSpeed).isEqualTo(expectedSpeedAccordingToDocumentation);
    assertThat(adjustedSpeed).isGreaterThan(1f);
  }

  @Test
  public void adjustPlaybackSpeed_withLiveOffsetLowerThanTargetOffset_returnsAdjustedSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setProportionalControlFactor(0.01f).build();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(2_000_000);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 1_500_000);

    float expectedSpeedAccordingToDocumentation = 1f + 0.01f * (1.5f - 2f);
    assertThat(adjustedSpeed).isEqualTo(expectedSpeedAccordingToDocumentation);
    assertThat(adjustedSpeed).isLessThan(1f);
  }

  @Test
  public void
      adjustPlaybackSpeed_withLiveOffsetGreaterThanTargetOffset_clampedToFallbackMaximumSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setFallbackMaxPlaybackSpeed(1.5f).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 999_999_999_999L);

    assertThat(adjustedSpeed).isEqualTo(1.5f);
  }

  @Test
  public void
      adjustPlaybackSpeed_withLiveOffsetLowerThanTargetOffset_clampedToFallbackMinimumSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setFallbackMinPlaybackSpeed(0.5f).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ -999_999_999_999L);

    assertThat(adjustedSpeed).isEqualTo(0.5f);
  }

  @Test
  public void
      adjustPlaybackSpeed_andMediaProvidedMaxSpeedWithLiveOffsetGreaterThanTargetOffset_clampedToMediaMaxSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setFallbackMaxPlaybackSpeed(1.5f).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ 2f));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 999_999_999_999L);

    assertThat(adjustedSpeed).isEqualTo(2f);
  }

  @Test
  public void
      adjustPlaybackSpeed_andMediaProvidedMinSpeedWithLiveOffsetLowerThanTargetOffset_clampedToMediaMinSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setFallbackMinPlaybackSpeed(0.5f).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ 0.2f,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ -999_999_999_999L);

    assertThat(adjustedSpeed).isEqualTo(0.2f);
  }

  @Test
  public void adjustPlaybackSpeed_repeatedCallWithinMinUpdateInterval_returnsSameAdjustedSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 1_500_000);
    ShadowSystemClock.advanceBy(Duration.ofMillis(122));
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_500_000);
    ShadowSystemClock.advanceBy(Duration.ofMillis(2));
    float adjustedSpeed3 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_500_000);

    assertThat(adjustedSpeed1).isEqualTo(adjustedSpeed2);
    assertThat(adjustedSpeed3).isNotEqualTo(adjustedSpeed2);
  }

  @Test
  public void adjustPlaybackSpeed_repeatedCallAfterUpdateLiveConfiguration_updatesSpeedAgain() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 1_500_000);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_500_000);

    assertThat(adjustedSpeed1).isNotEqualTo(adjustedSpeed2);
  }

  @Test
  public void adjustPlaybackSpeed_repeatedCallAfterNewTargetLiveOffset_updatesSpeedAgain() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 1_500_000);
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(2_000_001);
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(/* liveOffsetUs= */ 2_500_000);

    assertThat(adjustedSpeed1).isNotEqualTo(adjustedSpeed2);
  }
}
