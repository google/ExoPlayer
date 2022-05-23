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
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultMediaSourceFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultMediaSourceFactoryTest {

  private static final String URI_MEDIA = "http://exoplayer.dev/video";
  private static final String URI_TEXT = "http://exoplayer.dev/text";

  @Test
  public void createMediaSource_fromMediaItem_returnsSameMediaItemInstance() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_MEDIA).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource.getMediaItem()).isSameInstanceAs(mediaItem);
  }

  @Test
  public void createMediaSource_withoutMimeType_progressiveSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_MEDIA).build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void createMediaSource_withPath_progressiveSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_MEDIA + "/file.mp3").build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void createMediaSource_withSubtitle_isMergingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    List<MediaItem.SubtitleConfiguration> subtitleConfigurations =
        Arrays.asList(
            new MediaItem.SubtitleConfiguration.Builder(Uri.parse(URI_TEXT))
                .setMimeType(MimeTypes.APPLICATION_TTML)
                .setLanguage("en")
                .build(),
            new MediaItem.SubtitleConfiguration.Builder(Uri.parse(URI_TEXT))
                .setMimeType(MimeTypes.APPLICATION_TTML)
                .setLanguage("de")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setSubtitleConfigurations(subtitleConfigurations)
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(MergingMediaSource.class);
  }

  @Test
  public void createMediaSource_withStartPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1000L).build())
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_withEndPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(1000L).build())
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_relativeToDefaultPosition_isClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setRelativeToDefaultPosition(true)
                    .build())
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ClippingMediaSource.class);
  }

  @Test
  public void createMediaSource_defaultToEnd_isNotClippingMediaSource() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(C.TIME_END_OF_SOURCE)
                    .build())
            .build();

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(ProgressiveMediaSource.class);
  }

  @Test
  public void getSupportedTypes_coreModule_onlyOther() {
    int[] supportedTypes =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext())
            .getSupportedTypes();

    assertThat(supportedTypes).asList().containsExactly(C.CONTENT_TYPE_OTHER);
  }

  @SuppressWarnings("deprecation") // Testing deprecated setters.
  @Test
  public void createMediaSource_withDeprecatedAdsConfiguration_callsAdsLoader() {
    Uri adTagUri = Uri.parse(URI_MEDIA);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setAdsConfiguration(new MediaItem.AdsConfiguration.Builder(adTagUri).build())
            .build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext())
            .setAdsLoaderProvider(ignoredAdsConfiguration -> mock(AdsLoader.class))
            .setAdViewProvider(mock(AdViewProvider.class));

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_withAdsConfiguration_callsAdsLoader() {
    Uri adTagUri = Uri.parse(URI_MEDIA);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setAdsConfiguration(new MediaItem.AdsConfiguration.Builder(adTagUri).build())
            .build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext())
            .setLocalAdInsertionComponents(
                ignoredAdsConfiguration -> mock(AdsLoader.class), mock(AdViewProvider.class));

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_withAdsConfiguration_adProvidersNotSet_playsWithoutAdNoException() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse(URI_MEDIA)).build())
            .build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());

    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    assertThat(mediaSource).isNotInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_withAdsConfigurationProvidersNull_playsWithoutAdNoException() {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA)
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse(URI_MEDIA)).build())
            .build();

    MediaSource mediaSource =
        new DefaultMediaSourceFactory(applicationContext).createMediaSource(mediaItem);

    assertThat(mediaSource).isNotInstanceOf(AdsMediaSource.class);
  }

  @Test
  public void createMediaSource_undefinedLiveProperties_livePropertiesUnset() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_MEDIA + "/file.mp4").build();
    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    MediaItem mediaItemFromSource = mediaSource.getMediaItem();

    assertThat(mediaItemFromSource.liveConfiguration.targetOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(mediaItemFromSource.liveConfiguration.minOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(mediaItemFromSource.liveConfiguration.maxOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(mediaItemFromSource.liveConfiguration.minPlaybackSpeed).isEqualTo(C.RATE_UNSET);
    assertThat(mediaItemFromSource.liveConfiguration.maxPlaybackSpeed).isEqualTo(C.RATE_UNSET);
  }

  @Test
  public void createMediaSource_withoutMediaItemProperties_usesFactoryLiveProperties() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext())
            .setLiveTargetOffsetMs(20)
            .setLiveMinOffsetMs(2222)
            .setLiveMaxOffsetMs(4444)
            .setLiveMinSpeed(.1f)
            .setLiveMaxSpeed(2.0f);
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_MEDIA + "/file.mp4").build();
    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    MediaItem mediaItemFromSource = mediaSource.getMediaItem();

    assertThat(mediaItemFromSource.liveConfiguration.targetOffsetMs).isEqualTo(20);
    assertThat(mediaItemFromSource.liveConfiguration.minOffsetMs).isEqualTo(2222);
    assertThat(mediaItemFromSource.liveConfiguration.maxOffsetMs).isEqualTo(4444);
    assertThat(mediaItemFromSource.liveConfiguration.minPlaybackSpeed).isEqualTo(.1f);
    assertThat(mediaItemFromSource.liveConfiguration.maxPlaybackSpeed).isEqualTo(2.0f);
  }

  @Test
  public void createMediaSource_withMediaItemLiveProperties_overridesFactoryLiveProperties() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext())
            .setLiveTargetOffsetMs(20)
            .setLiveMinOffsetMs(2222)
            .setLiveMinOffsetMs(4444)
            .setLiveMinSpeed(.1f)
            .setLiveMaxSpeed(2.0f);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_MEDIA + "/file.mp4")
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(10)
                    .setMinOffsetMs(1111)
                    .setMinOffsetMs(3333)
                    .setMinPlaybackSpeed(20.0f)
                    .setMaxPlaybackSpeed(20.0f)
                    .build())
            .build();
    MediaSource mediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem);

    MediaItem mediaItemFromSource = mediaSource.getMediaItem();

    assertThat(mediaItemFromSource).isEqualTo(mediaItem);
  }
}
