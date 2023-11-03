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
package androidx.media3.common;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.media3.common.Player.PositionInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
            /* mediaItemIndex= */ 23,
            new MediaItem.Builder().setMediaId("1234").build(),
            /* periodUid= */ null,
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    assertThat(PositionInfo.fromBundle(positionInfo.toBundle())).isEqualTo(positionInfo);
  }

  @Test
  public void roundTripViaBundle_ofPositionInfoWithWindowUid_yieldsNullWindowUid() {
    PositionInfo positionInfo =
        new PositionInfo(
            /* windowUid= */ new Object(),
            /* mediaItemIndex= */ 23,
            MediaItem.fromUri("https://exoplayer.dev"),
            /* periodUid= */ null,
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    PositionInfo positionInfoFromBundle = PositionInfo.fromBundle(positionInfo.toBundle());
    assertThat(positionInfoFromBundle.windowUid).isNull();
  }

  @Test
  public void roundTripViaBundle_ofPositionInfoWithPeriodUid_yieldsNullPeriodUid() {
    PositionInfo positionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* mediaItemIndex= */ 23,
            MediaItem.fromUri("https://exoplayer.dev"),
            /* periodUid= */ new Object(),
            /* periodIndex= */ 11,
            /* positionMs= */ 8787L,
            /* contentPositionMs= */ 12L,
            /* adGroupIndex= */ 2,
            /* adIndexInAdGroup= */ 444);

    PositionInfo positionInfoFromBundle = PositionInfo.fromBundle(positionInfo.toBundle());
    assertThat(positionInfoFromBundle.periodUid).isNull();
  }

  @Test
  public void roundTripViaBundle_withDefaultValues_yieldsEqualInstance() {
    PositionInfo defaultPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* mediaItemIndex= */ 0,
            /* mediaItem= */ null,
            /* periodUid= */ null,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);

    PositionInfo roundTripValue = PositionInfo.fromBundle(defaultPositionInfo.toBundle());

    assertThat(roundTripValue).isEqualTo(defaultPositionInfo);
  }

  @Test
  public void toBundle_withDefaultValues_omitsAllData() {
    PositionInfo defaultPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* mediaItemIndex= */ 0,
            /* mediaItem= */ null,
            /* periodUid= */ null,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);

    Bundle bundle =
        defaultPositionInfo.toBundle(/* controllerInterfaceVersion= */ Integer.MAX_VALUE);

    assertThat(bundle.isEmpty()).isTrue();
  }

  @Test
  public void toBundle_withDefaultValuesForControllerInterfaceBefore3_includesDefaultValues() {
    // Controller before version 3 uses invalid default values for indices and the Bundle should
    // always include them to avoid using the default values in the controller code.
    PositionInfo defaultPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* mediaItemIndex= */ 0,
            /* mediaItem= */ null,
            /* periodUid= */ null,
            /* periodIndex= */ 0,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);

    Bundle bundle = defaultPositionInfo.toBundle(/* controllerInterfaceVersion= */ 2);

    assertThat(bundle.keySet())
        .containsAtLeast(
            PositionInfo.FIELD_MEDIA_ITEM_INDEX,
            PositionInfo.FIELD_CONTENT_POSITION_MS,
            PositionInfo.FIELD_PERIOD_INDEX,
            PositionInfo.FIELD_POSITION_MS);
  }
}
