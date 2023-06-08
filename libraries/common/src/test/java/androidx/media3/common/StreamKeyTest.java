/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link StreamKey}. */
@RunWith(AndroidJUnit4.class)
public class StreamKeyTest {

  @Test
  public void parcelable() {
    StreamKey streamKeyToParcel =
        new StreamKey(/* periodIndex= */ 1, /* groupIndex= */ 2, /* streamIndex= */ 3);
    Parcel parcel = Parcel.obtain();
    streamKeyToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    StreamKey streamKeyFromParcel = StreamKey.CREATOR.createFromParcel(parcel);
    assertThat(streamKeyFromParcel).isEqualTo(streamKeyToParcel);

    parcel.recycle();
  }

  @Test
  public void roundTripViaBundle_withDefaultPeriodIndex_yieldsEqualInstance() {
    StreamKey originalStreamKey = new StreamKey(/* groupIndex= */ 1, /* streamIndex= */ 2);

    StreamKey streamKeyFromBundle = StreamKey.fromBundle(originalStreamKey.toBundle());

    assertThat(originalStreamKey).isEqualTo(streamKeyFromBundle);
  }

  @Test
  public void roundTripViaBundle_toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    StreamKey originalStreamKey = new StreamKey(/* groupIndex= */ 0, /* streamIndex= */ 0);

    Bundle streamKeyBundle = originalStreamKey.toBundle();

    assertThat(streamKeyBundle.keySet()).isEmpty();

    StreamKey streamKeyFromBundle = StreamKey.fromBundle(streamKeyBundle);

    assertThat(originalStreamKey).isEqualTo(streamKeyFromBundle);
  }

  @Test
  public void roundTripViaBundle_yieldsEqualInstance() {
    StreamKey originalStreamKey =
        new StreamKey(/* periodIndex= */ 10, /* groupIndex= */ 11, /* streamIndex= */ 12);

    StreamKey streamKeyFromBundle = StreamKey.fromBundle(originalStreamKey.toBundle());

    assertThat(originalStreamKey).isEqualTo(streamKeyFromBundle);
  }
}
