/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */
package androidx.media3.common.text;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcel;
import android.text.SpannedString;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CueGroup}. */
@RunWith(AndroidJUnit4.class)
public class CueGroupTest {

  @Test
  public void bundleAndUnBundleCueGroup() {
    Cue textCue = new Cue.Builder().setText(SpannedString.valueOf("text")).build();
    Cue bitmapCue =
        new Cue.Builder().setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)).build();
    ImmutableList<Cue> cues = ImmutableList.of(textCue, bitmapCue);
    CueGroup cueGroup = new CueGroup(cues, /* presentationTimeUs= */ 1_230_000);

    Parcel parcel = Parcel.obtain();
    try {
      parcel.writeBundle(cueGroup.toBundle());
      parcel.setDataPosition(0);

      Bundle bundle = parcel.readBundle();
      CueGroup filteredCueGroup = CueGroup.fromBundle(bundle);

      assertThat(filteredCueGroup.cues).containsExactly(textCue);
    } finally {
      parcel.recycle();
    }
  }
}
