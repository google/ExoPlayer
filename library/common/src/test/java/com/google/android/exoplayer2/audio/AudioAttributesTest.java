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
package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AudioAttributes}. */
@RunWith(AndroidJUnit4.class)
public class AudioAttributesTest {

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder()
            .setContentType(C.CONTENT_TYPE_SONIFICATION)
            .setFlags(C.FLAG_AUDIBILITY_ENFORCED)
            .setUsage(C.USAGE_ALARM)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_SYSTEM)
            .build();

    assertThat(AudioAttributes.CREATOR.fromBundle(audioAttributes.toBundle()))
        .isEqualTo(audioAttributes);
  }
}
