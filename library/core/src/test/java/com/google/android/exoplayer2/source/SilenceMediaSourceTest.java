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
package com.google.android.exoplayer2.source;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SilenceMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class SilenceMediaSourceTest {

  @Test
  public void builder_setsMediaItem() {
    SilenceMediaSource mediaSource =
        new SilenceMediaSource.Factory().setDurationUs(1_000_000).createMediaSource();

    MediaItem mediaItem = mediaSource.getMediaItem();

    assertThat(mediaItem).isNotNull();
    assertThat(mediaItem.mediaId).isEqualTo(SilenceMediaSource.MEDIA_ID);
    assertThat(mediaItem.localConfiguration.uri).isEqualTo(Uri.EMPTY);
    assertThat(mediaItem.localConfiguration.mimeType).isEqualTo(MimeTypes.AUDIO_RAW);
  }

  @Test
  public void builderSetTag_setsTagOfMediaItem() {
    Object tag = new Object();

    SilenceMediaSource mediaSource =
        new SilenceMediaSource.Factory().setTag(tag).setDurationUs(1_000_000).createMediaSource();

    assertThat(mediaSource.getMediaItem().localConfiguration.tag).isEqualTo(tag);
  }

  @Test
  public void builderSetTag_setsTagOfMediaSource() {
    Object tag = new Object();

    SilenceMediaSource mediaSource =
        new SilenceMediaSource.Factory().setTag(tag).setDurationUs(1_000_000).createMediaSource();

    assertThat(mediaSource.getMediaItem().localConfiguration.tag).isEqualTo(tag);
  }

  @Test
  public void builder_setDurationUsNotCalled_throwsIllegalStateException() {
    assertThrows(IllegalStateException.class, new SilenceMediaSource.Factory()::createMediaSource);
  }

  @Test
  public void builderSetDurationUs_nonPositiveValue_throwsIllegalStateException() {
    SilenceMediaSource.Factory factory = new SilenceMediaSource.Factory().setDurationUs(-1);

    assertThrows(IllegalStateException.class, factory::createMediaSource);
  }

  @Test
  public void newInstance_setsMediaItem() {
    SilenceMediaSource mediaSource = new SilenceMediaSource(1_000_000);

    MediaItem mediaItem = mediaSource.getMediaItem();

    assertThat(mediaItem).isNotNull();
    assertThat(mediaItem.mediaId).isEqualTo(SilenceMediaSource.MEDIA_ID);
    assertThat(mediaSource.getMediaItem().localConfiguration.uri).isEqualTo(Uri.EMPTY);
    assertThat(mediaItem.localConfiguration.mimeType).isEqualTo(MimeTypes.AUDIO_RAW);
  }
}
