/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.session;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link PlayerInfo}. */
@RunWith(AndroidJUnit4.class)
public class PlayerInfoTest {

  @Test
  public void bundlingExclusionEquals_equalInstances() {
    PlayerInfo.BundlingExclusions bundlingExclusions1 =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ false);
    PlayerInfo.BundlingExclusions bundlingExclusions2 =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ false);

    assertThat(bundlingExclusions1).isEqualTo(bundlingExclusions2);
  }

  @Test
  public void bundlingExclusionFromBundle_toBundleRoundTrip_equalInstances() {
    PlayerInfo.BundlingExclusions bundlingExclusions =
        new PlayerInfo.BundlingExclusions(
            /* isTimelineExcluded= */ true, /* areCurrentTracksExcluded= */ true);
    Bundle bundle = bundlingExclusions.toBundle();

    PlayerInfo.BundlingExclusions resultingBundlingExclusions =
        PlayerInfo.BundlingExclusions.CREATOR.fromBundle(bundle);

    assertThat(resultingBundlingExclusions).isEqualTo(bundlingExclusions);
  }
}
