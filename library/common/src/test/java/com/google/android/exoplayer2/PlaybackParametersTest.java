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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link PlaybackParameters}. */
@RunWith(AndroidJUnit4.class)
public class PlaybackParametersTest {

  @Test
  public void roundTripViaBundle_ofPlaybackParameters_yieldsEqualInstance() {
    PlaybackParameters playbackParameters =
        new PlaybackParameters(/* speed= */ 2.9f, /* pitch= */ 1.2f);

    assertThat(PlaybackParameters.CREATOR.fromBundle(playbackParameters.toBundle()))
        .isEqualTo(playbackParameters);
  }
}
