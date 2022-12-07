/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.session;

import static org.junit.Assert.assertThrows;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link LibraryResult}. */
@RunWith(AndroidJUnit4.class)
public class LibraryResultTest {

  @Test
  public void constructor_mediaItemWithoutMediaId_throwsIAE() {
    MediaMetadata metadata =
        new MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(true).build();
    MediaItem item = new MediaItem.Builder().setMediaMetadata(metadata).build();
    assertThrows(
        IllegalArgumentException.class, () -> LibraryResult.ofItem(item, /* params= */ null));
  }

  @Test
  public void constructor_mediaItemWithoutIsBrowsable_throwsIAE() {
    MediaMetadata metadata = new MediaMetadata.Builder().setIsPlayable(true).build();
    MediaItem item = new MediaItem.Builder().setMediaId("id").setMediaMetadata(metadata).build();
    assertThrows(
        IllegalArgumentException.class, () -> LibraryResult.ofItem(item, /* params= */ null));
  }

  @Test
  public void constructor_mediaItemWithoutIsPlayable_throwsIAE() {
    MediaMetadata metadata = new MediaMetadata.Builder().setIsBrowsable(true).build();
    MediaItem item = new MediaItem.Builder().setMediaId("id").setMediaMetadata(metadata).build();
    assertThrows(
        IllegalArgumentException.class, () -> LibraryResult.ofItem(item, /* params= */ null));
  }
}
