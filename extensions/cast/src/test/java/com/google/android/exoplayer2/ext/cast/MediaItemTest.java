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
package com.google.android.exoplayer2.ext.cast;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link MediaItem}. */
@RunWith(AndroidJUnit4.class)
public class MediaItemTest {

  @Test
  public void buildMediaItem_doesNotChangeState() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item1 =
        builder
            .setUri(Uri.parse("http://example.com"))
            .setTitle("title")
            .setMimeType(MimeTypes.AUDIO_MP4)
            .build();
    MediaItem item2 = builder.build();
    assertThat(item1).isEqualTo(item2);
  }

  @Test
  public void equals_withEqualDrmSchemes_returnsTrue() {
    MediaItem.Builder builder1 = new MediaItem.Builder();
    MediaItem mediaItem1 =
        builder1
            .setUri(Uri.parse("www.google.com"))
            .setDrmConfiguration(buildDrmConfiguration(1))
            .build();
    MediaItem.Builder builder2 = new MediaItem.Builder();
    MediaItem mediaItem2 =
        builder2
            .setUri(Uri.parse("www.google.com"))
            .setDrmConfiguration(buildDrmConfiguration(1))
            .build();
    assertThat(mediaItem1).isEqualTo(mediaItem2);
  }

  @Test
  public void equals_withDifferentDrmRequestHeaders_returnsFalse() {
    MediaItem.Builder builder1 = new MediaItem.Builder();
    MediaItem mediaItem1 =
        builder1
            .setUri(Uri.parse("www.google.com"))
            .setDrmConfiguration(buildDrmConfiguration(1))
            .build();
    MediaItem.Builder builder2 = new MediaItem.Builder();
    MediaItem mediaItem2 =
        builder2
            .setUri(Uri.parse("www.google.com"))
            .setDrmConfiguration(buildDrmConfiguration(2))
            .build();
    assertThat(mediaItem1).isNotEqualTo(mediaItem2);
  }

  private static MediaItem.DrmConfiguration buildDrmConfiguration(int seed) {
    HashMap<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("key1", "value1");
    requestHeaders.put("key2", "value2" + seed);
    return new MediaItem.DrmConfiguration(
        C.WIDEVINE_UUID, Uri.parse("www.uri1.com"), requestHeaders);
  }
}
