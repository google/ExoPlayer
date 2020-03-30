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

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultMediaSourceFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultMediaSourceFactoryTest {

  private static final String URI_MEDIA = "http://exoplayer.dev/video";
  private static final String URI_TEXT = "http://exoplayer.dev/text";

  @Test
  public void createMediaSource_withoutMimeType_progressiveSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void createMediaSource_withTag_tagInSource() {
    Object tag = new Object();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA).setTag(tag).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource.getTag()).isEqualTo(tag);
  }

  @Test
  public void createMediaSource_withPath_progressiveSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA + "/file.mp3").build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void createMediaSource_withNull_usesNonNullDefaults() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setSourceUri(URI_MEDIA).build();

    MediaSource mediaSource =
        defaultMediaSourceFactory
            .setDrmSessionManager(null)
            .setDrmHttpDataSourceFactory(null)
            .setLoadErrorHandlingPolicy(null)
            .createMediaSource(mediaItem);

    assertThat(mediaSource).isNotNull();
  }

  @Test
  public void createMediaSource_withSubtitle_isMergingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    List<MediaItem.Subtitle> subtitles =
        Arrays.asList(
            new MediaItem.Subtitle(Uri.parse(URI_TEXT), MimeTypes.APPLICATION_TTML, "en"),
            new MediaItem.Subtitle(
                Uri.parse(URI_TEXT), MimeTypes.APPLICATION_TTML, "de", C.SELECTION_FLAG_DEFAULT));
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setSubtitles(subtitles).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(MergingMediaSource.class);
  }

  @Test
  public void createMediaSource_withSubtitle_hasTag() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    Object tag = new Object();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setTag(tag)
            .setSourceUri(URI_MEDIA)
            .setSubtitles(
                Collections.singletonList(
                    new MediaItem.Subtitle(Uri.parse(URI_TEXT), MimeTypes.APPLICATION_TTML, "en")))
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource.getTag()).isEqualTo(tag);
  }

  @Test
  public void getSupportedTypes_coreModule_onlyOther() {
    int[] supportedTypes =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext())
            .getSupportedTypes();

    assertThat(supportedTypes).asList().containsExactly(C.TYPE_OTHER);
  }
}
