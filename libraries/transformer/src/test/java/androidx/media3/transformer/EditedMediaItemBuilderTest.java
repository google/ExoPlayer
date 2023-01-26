/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.transformer;

import static org.junit.Assert.assertThrows;

import androidx.media3.common.MediaItem;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link EditedMediaItem.Builder}. */
@RunWith(AndroidJUnit4.class)
public final class EditedMediaItemBuilderTest {

  @Test
  public void build_removeAudioAndVideo_throws() {
    MediaItem mediaItem = MediaItem.fromUri("uri");

    assertThrows(
        IllegalStateException.class,
        () ->
            new EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .setRemoveVideo(true)
                .build());
  }

  @Test
  public void setFlattenForSlowMotion_forClippedMediaItem_throws() {
    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1000).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("Uri")
            .setClippingConfiguration(clippingConfiguration)
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> new EditedMediaItem.Builder(mediaItem).setFlattenForSlowMotion(true).build());
  }
}
