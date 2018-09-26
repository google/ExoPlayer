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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test for {@link MediaItem}. */
@RunWith(RobolectricTestRunner.class)
public class MediaItemTest {

  @Test
  public void buildMediaItem_resetsUuid() {
    MediaItem.Builder builder = new MediaItem.Builder();
    UUID uuid = new UUID(1, 1);
    MediaItem item1 = builder.setUuid(uuid).build();
    MediaItem item2 = builder.build();
    MediaItem item3 = builder.build();
    assertThat(item1.uuid).isEqualTo(uuid);
    assertThat(item2.uuid).isNotEqualTo(uuid);
    assertThat(item3.uuid).isNotEqualTo(item2.uuid);
    assertThat(item3.uuid).isNotEqualTo(uuid);
  }

  @Test
  public void buildMediaItem_doesNotChangeState() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item1 =
        builder
            .setMedia("http://example.com")
            .setTitle("title")
            .setMimeType(MimeTypes.AUDIO_MP4)
            .setStartPositionUs(3)
            .setEndPositionUs(4)
            .build();
    MediaItem item2 = builder.build();
    assertThat(item1.title).isEqualTo(item2.title);
    assertThat(item1.media.uri).isEqualTo(item2.media.uri);
    assertThat(item1.mimeType).isEqualTo(item2.mimeType);
    assertThat(item1.startPositionUs).isEqualTo(item2.startPositionUs);
    assertThat(item1.endPositionUs).isEqualTo(item2.endPositionUs);
  }

  @Test
  public void buildMediaItem_assertDefaultValues() {
    assertDefaultValues(new MediaItem.Builder().build());
  }

  @Test
  public void buildMediaItem_testClear() {
    MediaItem.Builder builder = new MediaItem.Builder();
    builder
        .setMedia("http://example.com")
        .setTitle("title")
        .setMimeType(MimeTypes.AUDIO_MP4)
        .setStartPositionUs(3)
        .setEndPositionUs(4)
        .buildAndClear();
    assertDefaultValues(builder.build());
  }

  private static void assertDefaultValues(MediaItem item) {
    assertThat(item.title).isEmpty();
    assertThat(item.description).isEmpty();
    assertThat(item.media.uri).isEqualTo(Uri.EMPTY);
    assertThat(item.attachment).isNull();
    assertThat(item.drmSchemes).isEmpty();
    assertThat(item.startPositionUs).isEqualTo(C.TIME_UNSET);
    assertThat(item.endPositionUs).isEqualTo(C.TIME_UNSET);
    assertThat(item.mimeType).isEmpty();
  }
}
