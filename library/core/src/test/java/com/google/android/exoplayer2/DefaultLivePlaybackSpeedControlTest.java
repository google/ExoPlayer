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
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
  public void getTargetLiveOffsetUs_afterSetLiveConfiguration_returnsMediaLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    assertThat(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs()).isEqualTo(42_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetLiveConfigurationWithTargetGreaterThanMax_returnsMaxLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 4321,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    assertThat(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs()).isEqualTo(400_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetLiveConfigurationWithTargetLessThanMin_returnsMinLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 3,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    assertThat(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs()).isEqualTo(5_000);
  }

  @Test
  public void getTargetLiveOffsetUs_afterSetTargetLiveOffsetOverrideUs_returnsOverride() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();

    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(321_000);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(321_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetTargetLiveOffsetOverrideUsGreaterThanMax_returnsMaxLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();

    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(400_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetTargetLiveOffsetOverrideUsLessThanMin_returnsMinLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();

    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(3_141);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(5_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetTargetLiveOffsetOverrideWithoutMediaConfiguration_returnsUnset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterSetTargetLiveOffsetOverrideWithTimeUnset_returnsMediaLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(123_456_789);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(C.TIME_UNSET);

    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(42_000);
  }

  @Test
  public void getTargetLiveOffsetUs_afterNotifyRebuffer_returnsIncreasedTargetOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetBeforeUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();
    defaultLivePlaybackSpeedControl.notifyRebuffer();
    long targetLiveOffsetAfterUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetAfterUs).isGreaterThan(targetLiveOffsetBeforeUs);
    assertThat(targetLiveOffsetAfterUs - targetLiveOffsetBeforeUs).isEqualTo(3_000);
  }

  @Test
  public void getTargetLiveOffsetUs_afterRepeatedNotifyRebuffer_returnsMaxLiveOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    List<Long> targetOffsetsUs = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      targetOffsetsUs.add(defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs());
      defaultLivePlaybackSpeedControl.notifyRebuffer();
    }

    assertThat(targetOffsetsUs).isInOrder();
    assertThat(Iterables.getLast(targetOffsetsUs)).isEqualTo(400_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferWithIncrementOfZero_returnsOriginalTargetOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(0)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    defaultLivePlaybackSpeedControl.notifyRebuffer();
    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(42_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferAndSetTargetLiveOffsetOverrideUs_returnsOverride() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    defaultLivePlaybackSpeedControl.notifyRebuffer();
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(321_000);
    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(321_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferAndSetLiveConfigurationWithSameOffset_returnsIncreasedTargetOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    long targetLiveOffsetBeforeUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();
    defaultLivePlaybackSpeedControl.notifyRebuffer();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 3,
            /* maxLiveOffsetMs= */ 450,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));
    long targetLiveOffsetAfterUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetAfterUs).isGreaterThan(targetLiveOffsetBeforeUs);
    assertThat(targetLiveOffsetAfterUs - targetLiveOffsetBeforeUs).isEqualTo(3_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferAndSetLiveConfigurationWithNewOffset_returnsNewOffset() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42,
            /* minLiveOffsetMs= */ 5,
            /* maxLiveOffsetMs= */ 400,
            /* minPlaybackSpeed= */ 1f,
            /* maxPlaybackSpeed= */ 1f));

    defaultLivePlaybackSpeedControl.notifyRebuffer();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 39,
            /* minLiveOffsetMs= */ 3,
            /* maxLiveOffsetMs= */ 450,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));
    long targetLiveOffsetUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetUs).isEqualTo(39_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferAndAdjustPlaybackSpeedWithLargeBufferedDuration_returnsDecreasedOffsetToIdealTarget() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3_000)
            .setMinUpdateIntervalMs(100)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42_000,
            /* minLiveOffsetMs= */ 5_000,
            /* maxLiveOffsetMs= */ 400_000,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));

    defaultLivePlaybackSpeedControl.notifyRebuffer();
    long targetLiveOffsetAfterRebufferUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
        /* liveOffsetUs= */ 45_000_000, /* bufferedDurationUs= */ 9_000_000);
    long targetLiveOffsetAfterOneAdjustmentUs =
        defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    for (int i = 0; i < 500; i++) {
      ShadowSystemClock.advanceBy(Duration.ofMillis(100));
      defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
          /* liveOffsetUs= */ 45_000_000, /* bufferedDurationUs= */ 9_000_000);
    }
    long targetLiveOffsetAfterManyAdjustmentsUs =
        defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetAfterOneAdjustmentUs).isLessThan(targetLiveOffsetAfterRebufferUs);
    assertThat(targetLiveOffsetAfterManyAdjustmentsUs)
        .isLessThan(targetLiveOffsetAfterOneAdjustmentUs);
    assertThat(targetLiveOffsetAfterManyAdjustmentsUs).isEqualTo(42_000_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterNotifyRebufferAndAdjustPlaybackSpeedWithSmallBufferedDuration_returnsDecreasedOffsetToSafeTarget() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3_000)
            .setMinUpdateIntervalMs(100)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42_000,
            /* minLiveOffsetMs= */ 5_000,
            /* maxLiveOffsetMs= */ 400_000,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));

    defaultLivePlaybackSpeedControl.notifyRebuffer();
    long targetLiveOffsetAfterRebufferUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
        /* liveOffsetUs= */ 45_000_000, /* bufferedDurationUs= */ 1_000_000);
    long targetLiveOffsetAfterOneAdjustmentUs =
        defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    for (int i = 0; i < 500; i++) {
      ShadowSystemClock.advanceBy(Duration.ofMillis(100));
      long noiseUs = ((i % 10) - 5L) * 1_000;
      defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
          /* liveOffsetUs= */ 45_000_000, /* bufferedDurationUs= */ 1_000_000 + noiseUs);
    }
    long targetLiveOffsetAfterManyAdjustmentsUs =
        defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetAfterOneAdjustmentUs).isLessThan(targetLiveOffsetAfterRebufferUs);
    assertThat(targetLiveOffsetAfterManyAdjustmentsUs)
        .isLessThan(targetLiveOffsetAfterOneAdjustmentUs);
    // Should be at least be at the minimum buffered position.
    assertThat(targetLiveOffsetAfterManyAdjustmentsUs).isGreaterThan(44_005_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterAdjustPlaybackSpeedWithLiveOffsetAroundCurrentTarget_returnsSafeTarget() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42_000,
            /* minLiveOffsetMs= */ 5_000,
            /* maxLiveOffsetMs= */ 400_000,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));

    long targetLiveOffsetBeforeUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();
    // Pretend to have a buffered duration at around the target duration with some artificial noise.
    for (int i = 0; i < 500; i++) {
      ShadowSystemClock.advanceBy(Duration.ofMillis(100));
      long noiseUs = ((i % 10) - 5L) * 1_000;
      defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
          /* liveOffsetUs= */ 49_000_000, /* bufferedDurationUs= */ 7_000_000 + noiseUs);
    }
    ShadowSystemClock.advanceBy(Duration.ofMillis(100));
    defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
        /* liveOffsetUs= */ 49_000_000, /* bufferedDurationUs= */ 7_000_000);
    long targetLiveOffsetAfterUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetBeforeUs).isEqualTo(42_000_000);
    assertThat(targetLiveOffsetAfterUs).isGreaterThan(42_005_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterAdjustPlaybackSpeedAndSmoothingFactorOfZero_ignoresSafeTargetAndReturnsCurrentTarget() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setMinPossibleLiveOffsetSmoothingFactor(0f)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42_000,
            /* minLiveOffsetMs= */ 5_000,
            /* maxLiveOffsetMs= */ 400_000,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));

    long targetLiveOffsetBeforeUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();
    // Pretend to have a buffered duration at around the target duration with some artificial noise.
    for (int i = 0; i < 500; i++) {
      ShadowSystemClock.advanceBy(Duration.ofMillis(100));
      long noiseUs = ((i % 10) - 5L) * 1_000;
      defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
          /* liveOffsetUs= */ 49_000_000, /* bufferedDurationUs= */ 7_000_000 + noiseUs);
    }
    ShadowSystemClock.advanceBy(Duration.ofMillis(100));
    defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
        /* liveOffsetUs= */ 49_000_000, /* bufferedDurationUs= */ 7_000_000);
    long targetLiveOffsetAfterUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetBeforeUs).isEqualTo(42_000_000);
    // Despite the noise indicating it's unsafe here, we still return the target offset.
    assertThat(targetLiveOffsetAfterUs).isEqualTo(42_000_000);
  }

  @Test
  public void
      getTargetLiveOffsetUs_afterAdjustPlaybackSpeedWithLiveOffsetLessThanCurrentTarget_returnsCurrentTarget() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setTargetLiveOffsetIncrementOnRebufferMs(3_000)
            .setMinUpdateIntervalMs(100)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 42_000,
            /* minLiveOffsetMs= */ 5_000,
            /* maxLiveOffsetMs= */ 400_000,
            /* minPlaybackSpeed= */ 0.9f,
            /* maxPlaybackSpeed= */ 1.1f));

    long targetLiveOffsetBeforeUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();
    defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
        /* liveOffsetUs= */ 39_000_000, /* bufferedDurationUs= */ 1_000_000);
    long targetLiveOffsetAfterUs = defaultLivePlaybackSpeedControl.getTargetLiveOffsetUs();

    assertThat(targetLiveOffsetBeforeUs).isEqualTo(42_000_000);
    assertThat(targetLiveOffsetAfterUs).isEqualTo(42_000_000);
  }

  @Test
  public void adjustPlaybackSpeed_liveOffsetMatchesTargetOffset_returnsUnitSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_000_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed).isEqualTo(1f);
  }

  @Test
  public void adjustPlaybackSpeed_liveOffsetWithinAcceptableErrorMargin_returnsUnitSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder()
            .setMaxLiveOffsetErrorMsForUnitSpeed(5)
            .build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeedJustAboveLowerErrorMargin =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_000_000 - 5_000 + 1, /* bufferedDurationUs= */ 1_000_000);
    float adjustedSpeedJustBelowUpperErrorMargin =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_000_000 + 5_000 - 1, /* bufferedDurationUs= */ 1_000_000);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 999_999_999_999L, /* bufferedDurationUs= */ 999_999_999_999L);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ -999_999_999_999L, /* bufferedDurationUs= */ 1_000_000);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ 2f));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 999_999_999_999L, /* bufferedDurationUs= */ 999_999_999_999L);

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
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ 0.2f,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ -999_999_999_999L, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed).isEqualTo(0.2f);
  }

  @Test
  public void adjustPlaybackSpeed_repeatedCallWithinMinUpdateInterval_returnsSameAdjustedSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);
    ShadowSystemClock.advanceBy(Duration.ofMillis(122));
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);
    ShadowSystemClock.advanceBy(Duration.ofMillis(2));
    float adjustedSpeed3 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed1).isEqualTo(adjustedSpeed2);
    assertThat(adjustedSpeed3).isNotEqualTo(adjustedSpeed2);
  }

  @Test
  public void
      adjustPlaybackSpeed_repeatedCallAfterSetLiveConfigurationWithSameOffset_returnsSameAdjustedSpeed() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed1).isEqualTo(adjustedSpeed2);
  }

  @Test
  public void
      adjustPlaybackSpeed_repeatedCallAfterSetLiveConfigurationWithNewOffset_updatesSpeedAgain() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 1_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed1).isNotEqualTo(adjustedSpeed2);
  }

  @Test
  public void
      adjustPlaybackSpeed_repeatedCallAfterSetTargetLiveOffsetOverrideUs_updatesSpeedAgain() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);
    defaultLivePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(2_000_001);
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed1).isNotEqualTo(adjustedSpeed2);
  }

  @Test
  public void adjustPlaybackSpeed_repeatedCallAfterNotifyRebuffer_updatesSpeedAgain() {
    DefaultLivePlaybackSpeedControl defaultLivePlaybackSpeedControl =
        new DefaultLivePlaybackSpeedControl.Builder().setMinUpdateIntervalMs(123).build();
    defaultLivePlaybackSpeedControl.setLiveConfiguration(
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ 2_000,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET));

    float adjustedSpeed1 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 1_500_000, /* bufferedDurationUs= */ 1_000_000);
    defaultLivePlaybackSpeedControl.notifyRebuffer();
    float adjustedSpeed2 =
        defaultLivePlaybackSpeedControl.getAdjustedPlaybackSpeed(
            /* liveOffsetUs= */ 2_500_000, /* bufferedDurationUs= */ 1_000_000);

    assertThat(adjustedSpeed1).isNotEqualTo(adjustedSpeed2);
  }
}
