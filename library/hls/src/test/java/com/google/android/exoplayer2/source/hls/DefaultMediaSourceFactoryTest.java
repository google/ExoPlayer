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
package com.google.android.exoplayer2.source.hls;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for creating HLS media sources with the {@link DefaultMediaSourceFactory}. */
@RunWith(AndroidJUnit4.class)
public class DefaultMediaSourceFactoryTest {

  private static final String URI_MEDIA = "http://exoplayer.dev/video";

  @Test
  public void createMediaSource_withMimeType_hlsSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setSourceUri(URI_MEDIA)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(HlsMediaSource.class);
  }

  @Test
  public void createMediaSource_withTag_tagInSource() {
    Object tag = new Object();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setSourceUri(URI_MEDIA)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setTag(tag)
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource.getTag()).isEqualTo(tag);
  }

  @Test
  public void createMediaSource_withPath_hlsSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA + "/file.m3u8").build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(HlsMediaSource.class);
  }

  @Test
  public void createMediaSource_withNull_usesNonNullDefaults() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA + "/file.m3u8").build();

    MediaSource mediaSource =
        defaultMediaSourceFactory
            .setDrmSessionManager(null)
            .setDrmHttpDataSourceFactory(null)
            .setLoadErrorHandlingPolicy(null)
            .createMediaSource(mediaItem);

    assertThat(mediaSource).isNotNull();
  }

  @Test
  public void getSupportedTypes_hlsModule_containsTypeHls() {
    int[] supportedTypes =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext())
            .getSupportedTypes();

    assertThat(supportedTypes).asList().containsExactly(C.TYPE_OTHER, C.TYPE_HLS);
  }
}
