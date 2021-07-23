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
import java.util.Collections;
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
            .setUri(Uri.parse("http://example.com"))
            .setMediaMetadata(MediaMetadata.EMPTY)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri("http://license.com")
            .setDrmLicenseRequestHeaders(Collections.singletonMap("key", "value"))
            .build();

    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    MediaQueueItem queueItem = converter.toMediaQueueItem(item);
    MediaItem reconstructedItem = converter.toMediaItem(queueItem);

    assertThat(reconstructedItem).isEqualTo(item);
  }
}
