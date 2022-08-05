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
package com.google.android.exoplayer2.ext.cast;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link DefaultMediaItemConverter}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaItemConverterTest {

  @Test
  public void serialize_deserialize_minimal() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item =
        builder.setUri("http://example.com").setMimeType(MimeTypes.APPLICATION_MPD).build();

    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    MediaQueueItem queueItem = converter.toMediaQueueItem(item);
    MediaItem reconstructedItem = converter.toMediaItem(queueItem);

    assertThat(reconstructedItem).isEqualTo(item);
  }

  @Test
  public void serialize_deserialize_complete() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item =
        builder
            .setMediaId("fooBar")
            .setUri(Uri.parse("http://example.com"))
            .setMediaMetadata(MediaMetadata.EMPTY)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri("http://license.com")
                    .setLicenseRequestHeaders(ImmutableMap.of("key", "value"))
                    .build())
            .build();

    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    MediaQueueItem queueItem = converter.toMediaQueueItem(item);
    MediaItem reconstructedItem = converter.toMediaItem(queueItem);

    assertThat(reconstructedItem).isEqualTo(item);
  }

  @Test
  public void toMediaQueueItem_nonDefaultMediaId_usedAsContentId() {
    MediaItem.Builder builder = new MediaItem.Builder();
    MediaItem item =
        builder
            .setMediaId("fooBar")
            .setUri("http://example.com")
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build();

    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    MediaQueueItem queueItem = converter.toMediaQueueItem(item);

    assertThat(queueItem.getMedia().getContentId()).isEqualTo("fooBar");
  }

  @Test
  public void toMediaQueueItem_defaultMediaId_uriAsContentId() {
    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("http://example.com")
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build();

    MediaQueueItem queueItem = converter.toMediaQueueItem(mediaItem);

    assertThat(queueItem.getMedia().getContentId()).isEqualTo("http://example.com");

    MediaItem secondMediaItem =
        new MediaItem.Builder()
            .setMediaId(MediaItem.DEFAULT_MEDIA_ID)
            .setUri("http://example.com")
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build();

    MediaQueueItem secondQueueItem = converter.toMediaQueueItem(secondMediaItem);

    assertThat(secondQueueItem.getMedia().getContentId()).isEqualTo("http://example.com");
  }
}
