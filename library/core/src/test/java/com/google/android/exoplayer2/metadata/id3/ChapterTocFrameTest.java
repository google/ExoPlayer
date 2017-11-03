/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Test for {@link ChapterTocFrame}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class ChapterTocFrameTest {

  @Test
  public void testParcelable() {
    String[] children = new String[] {"child0", "child1"};
    Id3Frame[] subFrames = new Id3Frame[] {
        new TextInformationFrame("TIT2", null, "title"),
        new UrlLinkFrame("WXXX", "description", "url")
    };
    ChapterTocFrame chapterTocFrameToParcel = new ChapterTocFrame("id", false, true, children,
        subFrames);

    Parcel parcel = Parcel.obtain();
    chapterTocFrameToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    ChapterTocFrame chapterTocFrameFromParcel = ChapterTocFrame.CREATOR.createFromParcel(parcel);
    assertThat(chapterTocFrameFromParcel).isEqualTo(chapterTocFrameToParcel);

    parcel.recycle();
  }

}
