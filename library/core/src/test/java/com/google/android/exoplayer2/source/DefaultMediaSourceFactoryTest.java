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
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
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
  public void createMediaSource_withStartPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setClipStartPositionMs(1000L).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_withEndPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setClipEndPositionMs(1000L).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_relativeToDefaultPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setSourceUri(URI_MEDIA)
            .setClipRelativeToDefaultPosition(true)
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_defaultToEnd_isNotClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setSourceUri(URI_MEDIA)
            .setClipEndPositionMs(C.TIME_END_OF_SOURCE)
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void getSupportedTypes_coreModule_onlyOther() {
    int[] supportedTypes =
        DefaultMediaSourceFactory.newInstance(ApplicationProvider.getApplicationContext())
            .getSupportedTypes();

    assertThat(supportedTypes).asList().containsExactly(C.TYPE_OTHER);
  }

  @Test
  public void createMediaSource_withAdTagUri_callsAdsLoader() {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    Uri adTagUri = Uri.parse(URI_MEDIA);
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setAdTagUri(adTagUri).build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory(
            applicationContext,
            new DefaultDataSourceFactory(applicationContext, "userAgent"),
            createAdSupportProvider(mock(AdsLoader.class), mock(AdsLoader.AdViewProvider.class)));

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_withAdTagUriAdsLoaderNull_playsWithoutAdNoException() {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setAdTagUri(Uri.parse(URI_MEDIA)).build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory(
            applicationContext,
            new DefaultDataSourceFactory(applicationContext, "userAgent"),
            createAdSupportProvider(/* adsLoader= */ null, mock(AdsLoader.AdViewProvider.class)));

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isNotInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_withAdTagUriProvidersNull_playsWithoutAdNoException() {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    MediaItem mediaItem =
        new MediaItem.Builder().setSourceUri(URI_MEDIA).setAdTagUri(Uri.parse(URI_MEDIA)).build();

    MediaSource mediaSource =
        DefaultMediaSourceFactory.newInstance(applicationContext).createMediaSource(mediaItem);

    assertThat(mediaSource).isNotInstanceOf(AdsMediaSource.class);
  }

  private static DefaultMediaSourceFactory.AdSupportProvider createAdSupportProvider(
      @Nullable AdsLoader adsLoader, AdsLoader.AdViewProvider adViewProvider) {
    return new DefaultMediaSourceFactory.AdSupportProvider() {
      @Nullable
      @Override
      public AdsLoader getAdsLoader(Uri adTagUri) {
        return adsLoader;
      }

      @Override
      public AdsLoader.AdViewProvider getAdViewProvider() {
        return adViewProvider;
      }
    };
  }
}
