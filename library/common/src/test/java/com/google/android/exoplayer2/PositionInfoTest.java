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
import com.google.android.exoplayer2.Player.PositionInfo;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link Player.PositionInfo}. */
@RunWith(AndroidJUnit4.class)
public class PositionInfoTest {

  @Test
  public void roundTripViaBundle_ofPositionInfoWithoutObjectFields_yieldsEqualInstance() {
    PositionInfo positionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ 23,
            /* periodUid= */ null,
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    assertThat(PositionInfo.CREATOR.fromBundle(positionInfo.toBundle())).isEqualTo(positionInfo);
  }

  @Test
  public void roundTripViaBundle_ofPositionInfoWithWindowUid_yieldsNullWindowUid() {
    PositionInfo positionInfo =
        new PositionInfo(
            /* windowUid= */ new Object(),
            /* windowIndex= */ 23,
            /* periodUid= */ null,
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    PositionInfo positionInfoFromBundle = PositionInfo.CREATOR.fromBundle(positionInfo.toBundle());
    assertThat(positionInfoFromBundle.windowUid).isNull();
  }

  @Test
  public void roundTripViaBundle_ofPositionInfoWithPeriodUid_yieldsNullPeriodUid() {
    PositionInfo positionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ 23,
            /* periodUid= */ new Object(),
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    PositionInfo positionInfoFromBundle = PositionInfo.CREATOR.fromBundle(positionInfo.toBundle());
    assertThat(positionInfoFromBundle.periodUid).isNull();
  }
}
