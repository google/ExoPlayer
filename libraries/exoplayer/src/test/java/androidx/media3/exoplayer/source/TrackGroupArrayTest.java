/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TrackGroupArray}. */
@RunWith(AndroidJUnit4.class)
public final class TrackGroupArrayTest {

  @Test
  public void roundTripViaBundle_ofTrackGroupArray_yieldsEqualInstance() {
    Format.Builder formatBuilder = new Format.Builder();
    Format format1 = formatBuilder.setSampleMimeType(MimeTypes.VIDEO_H264).build();
    Format format2 = formatBuilder.setSampleMimeType(MimeTypes.AUDIO_AAC).build();
    Format format3 = formatBuilder.setSampleMimeType(MimeTypes.VIDEO_H264).build();

    TrackGroup trackGroup1 = new TrackGroup(format1, format2);
    TrackGroup trackGroup2 = new TrackGroup(format3);

    TrackGroupArray trackGroupArrayToBundle = new TrackGroupArray(trackGroup1, trackGroup2);

    TrackGroupArray trackGroupArrayFromBundle =
        TrackGroupArray.fromBundle(trackGroupArrayToBundle.toBundle());

    assertThat(trackGroupArrayFromBundle).isEqualTo(trackGroupArrayToBundle);
  }
}
