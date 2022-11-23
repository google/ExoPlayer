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
package com.google.android.exoplayer2.metadata;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeMetadataEntry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Metadata}. */
@RunWith(AndroidJUnit4.class)
public class MetadataTest {

  @Test
  public void parcelable() {
    Metadata metadataToParcel =
        new Metadata(
            /* presentationTimeUs= */ 1_230_000,
            new FakeMetadataEntry("id1"),
            new FakeMetadataEntry("id2"));

    Parcel parcel = Parcel.obtain();
    metadataToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Metadata metadataFromParcel = Metadata.CREATOR.createFromParcel(parcel);
    assertThat(metadataFromParcel).isEqualTo(metadataToParcel);

    parcel.recycle();
  }
}
