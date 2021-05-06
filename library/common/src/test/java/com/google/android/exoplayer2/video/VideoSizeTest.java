/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link VideoSize}. */
@RunWith(AndroidJUnit4.class)
public final class VideoSizeTest {

  @Test
  public void roundTripViaBundle_ofVideoSizeUnknown_yieldsEqualInstance() {
    assertThat(roundTripViaBundle(VideoSize.UNKNOWN)).isEqualTo(VideoSize.UNKNOWN);
  }

  @Test
  public void roundTripViaBundle_ofArbitraryVideoSize_yieldsEqualInstance() {
    VideoSize videoSize =
        new VideoSize(
            /* width= */ 9,
            /* height= */ 8,
            /* unappliedRotationDegrees= */ 7,
            /* pixelWidthHeightRatio= */ 6);
    assertThat(roundTripViaBundle(videoSize)).isEqualTo(videoSize);
  }

  @Test
  public void fromBundle_ofEmptyBundle_yieldsVideoSizeUnknown() {
    assertThat(VideoSize.CREATOR.fromBundle(new Bundle())).isEqualTo(VideoSize.UNKNOWN);
  }

  private static VideoSize roundTripViaBundle(VideoSize videoSize) {
    return VideoSize.CREATOR.fromBundle(videoSize.toBundle());
  }
}
