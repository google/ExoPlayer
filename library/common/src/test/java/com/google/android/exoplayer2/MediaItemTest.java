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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem.RequestMetadata;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaItem MediaItems}. */
@RunWith(AndroidJUnit4.class)
public class MediaItemTest {

  private static final String URI_STRING = "http://www.google.com";

  @Test
  public void builderWithUri_setsUri() {
    Uri uri = Uri.parse(URI_STRING);

    MediaItem mediaItem = MediaItem.fromUri(uri);

    assertThat(mediaItem.localConfiguration.uri).isEqualTo(uri);
    assertThat(mediaItem.mediaMetadata).isNotNull();
  }

  @Test
  public void builderWithUriAsString_setsUri() {
    MediaItem mediaItem = MediaItem.fromUri(URI_STRING);

    assertThat(mediaItem.localConfiguration.uri.toString()).isEqualTo(URI_STRING);
  }

  @Test
  public void builderWithoutMediaId_usesDefaultMediaId() {
    MediaItem mediaItem = MediaItem.fromUri(URI_STRING);

    assertThat(mediaItem.mediaId).isEqualTo(MediaItem.DEFAULT_MEDIA_ID);
  }

  @Test
  public void builderSetMimeType_isNullByDefault() {
    MediaItem mediaItem = MediaItem.fromUri(URI_STRING);

    assertThat(mediaItem.localConfiguration.mimeType).isNull();
  }

  @Test
  public void builderSetMimeType_setsMimeType() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setMimeType(MimeTypes.APPLICATION_MPD).build();

    assertThat(mediaItem.localConfiguration.mimeType).isEqualTo(MimeTypes.APPLICATION_MPD);
  }

  @Test
  public void builder_drmConfigIsNullByDefault() {
    // Null value by default.
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_STRING).build();
    assertThat(mediaItem.localConfiguration.drmConfiguration).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated methods and fields
  public void builderSetDrmPropertiesIndividually() {
    Uri licenseUri = Uri.parse(URI_STRING);
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("Referer", "http://www.google.com");
    byte[] keySetId = new byte[] {1, 2, 3};
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setDrmLicenseUri(licenseUri)
            .setDrmLicenseRequestHeaders(requestHeaders)
            .setDrmMultiSession(true)
            .setDrmForceDefaultLicenseUri(true)
            .setDrmPlayClearContentWithoutKey(true)
            .setDrmSessionForClearTypes(ImmutableList.of(C.TRACK_TYPE_AUDIO))
            .setDrmKeySetId(keySetId)
            .setDrmUuid(C.WIDEVINE_UUID)
            .build();

    assertThat(mediaItem.localConfiguration.drmConfiguration).isNotNull();
    assertThat(mediaItem.localConfiguration.drmConfiguration.scheme).isEqualTo(C.WIDEVINE_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.uuid).isEqualTo(C.WIDEVINE_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseUri).isEqualTo(licenseUri);
    assertThat(mediaItem.localConfiguration.drmConfiguration.requestHeaders)
        .isEqualTo(requestHeaders);
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseRequestHeaders)
        .isEqualTo(requestHeaders);
    assertThat(mediaItem.localConfiguration.drmConfiguration.multiSession).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.forceDefaultLicenseUri).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.playClearContentWithoutKey).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.sessionForClearTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO);
    assertThat(mediaItem.localConfiguration.drmConfiguration.forcedSessionTrackTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO);
    assertThat(mediaItem.localConfiguration.drmConfiguration.getKeySetId()).isEqualTo(keySetId);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated methods and fields
  public void builderSetDrmConfigurationOverwritesIndividualProperties() {
    Uri licenseUri = Uri.parse(URI_STRING);
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("Referer", "http://www.google.com");
    byte[] keySetId = new byte[] {1, 2, 3};
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setDrmLicenseUri(licenseUri)
            .setDrmLicenseRequestHeaders(requestHeaders)
            .setDrmMultiSession(true)
            .setDrmForceDefaultLicenseUri(true)
            .setDrmPlayClearContentWithoutKey(true)
            .setDrmSessionForClearTypes(Collections.singletonList(C.TRACK_TYPE_AUDIO))
            .setDrmKeySetId(keySetId)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build())
            .build();

    assertThat(mediaItem.localConfiguration.drmConfiguration).isNotNull();
    assertThat(mediaItem.localConfiguration.drmConfiguration.scheme).isEqualTo(C.CLEARKEY_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.uuid).isEqualTo(C.CLEARKEY_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseUri).isNull();
    assertThat(mediaItem.localConfiguration.drmConfiguration.requestHeaders).isEmpty();
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseRequestHeaders).isEmpty();
    assertThat(mediaItem.localConfiguration.drmConfiguration.multiSession).isFalse();
    assertThat(mediaItem.localConfiguration.drmConfiguration.forceDefaultLicenseUri).isFalse();
    assertThat(mediaItem.localConfiguration.drmConfiguration.playClearContentWithoutKey).isFalse();
    assertThat(mediaItem.localConfiguration.drmConfiguration.sessionForClearTypes).isEmpty();
    assertThat(mediaItem.localConfiguration.drmConfiguration.forcedSessionTrackTypes).isEmpty();
    assertThat(mediaItem.localConfiguration.drmConfiguration.getKeySetId()).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated field
  public void builderSetDrmConfiguration() {
    Uri licenseUri = Uri.parse(URI_STRING);
    Map<String, String> requestHeaders = new HashMap<>();
    requestHeaders.put("Referer", "http://www.google.com");
    byte[] keySetId = new byte[] {1, 2, 3};
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(licenseUri)
                    .setLicenseRequestHeaders(requestHeaders)
                    .setMultiSession(true)
                    .setForceDefaultLicenseUri(true)
                    .setPlayClearContentWithoutKey(true)
                    .setForcedSessionTrackTypes(ImmutableList.of(C.TRACK_TYPE_AUDIO))
                    .setKeySetId(keySetId)
                    .build())
            .build();

    assertThat(mediaItem.localConfiguration.drmConfiguration).isNotNull();
    assertThat(mediaItem.localConfiguration.drmConfiguration.scheme).isEqualTo(C.WIDEVINE_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.uuid).isEqualTo(C.WIDEVINE_UUID);
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseUri).isEqualTo(licenseUri);
    assertThat(mediaItem.localConfiguration.drmConfiguration.requestHeaders)
        .isEqualTo(requestHeaders);
    assertThat(mediaItem.localConfiguration.drmConfiguration.licenseRequestHeaders)
        .isEqualTo(requestHeaders);
    assertThat(mediaItem.localConfiguration.drmConfiguration.multiSession).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.forceDefaultLicenseUri).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.playClearContentWithoutKey).isTrue();
    assertThat(mediaItem.localConfiguration.drmConfiguration.sessionForClearTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO);
    assertThat(mediaItem.localConfiguration.drmConfiguration.forcedSessionTrackTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO);
    assertThat(mediaItem.localConfiguration.drmConfiguration.getKeySetId()).isEqualTo(keySetId);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated methods and field
  public void builderSetDrmSessionForClearPeriods_setsAudioAndVideoTracks() {
    Uri licenseUri = Uri.parse(URI_STRING);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(licenseUri)
            .setDrmSessionForClearTypes(Arrays.asList(C.TRACK_TYPE_AUDIO))
            .setDrmSessionForClearPeriods(true)
            .build();

    assertThat(mediaItem.localConfiguration.drmConfiguration.sessionForClearTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO);
    assertThat(mediaItem.localConfiguration.drmConfiguration.forcedSessionTrackTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated field
  public void drmConfigurationBuilderSetSessionForClearPeriods_overridesSetSessionForClearTypes() {
    Uri licenseUri = Uri.parse(URI_STRING);
    MediaItem.DrmConfiguration drmConfiguration =
        new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(licenseUri)
            .setForcedSessionTrackTypes(ImmutableList.of(C.TRACK_TYPE_AUDIO))
            .setForceSessionsForAudioAndVideoTracks(true)
            .build();

    assertThat(drmConfiguration.sessionForClearTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO);
    assertThat(drmConfiguration.forcedSessionTrackTypes)
        .containsExactly(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated methods
  public void builderSetDrmUuid_notCalled_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new MediaItem.Builder()
                .setUri(URI_STRING)
                // missing uuid
                .setDrmLicenseUri(Uri.parse(URI_STRING))
                .build());
  }

  @Test
  public void builderSetCustomCacheKey_setsCustomCacheKey() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setCustomCacheKey("key").build();

    assertThat(mediaItem.localConfiguration.customCacheKey).isEqualTo("key");
  }

  @Test
  public void builderSetStreamKeys_setsStreamKeys() {
    List<StreamKey> streamKeys = new ArrayList<>();
    streamKeys.add(new StreamKey(1, 0, 0));
    streamKeys.add(new StreamKey(0, 1, 1));

    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setStreamKeys(streamKeys).build();

    assertThat(mediaItem.localConfiguration.streamKeys).isEqualTo(streamKeys);
  }

  @Test
  @SuppressWarnings("deprecation") // Reading deprecated subtitles field
  public void builderSetSubtitleConfigurations() {
    List<MediaItem.SubtitleConfiguration> subtitleConfigurations =
        ImmutableList.of(
            new MediaItem.SubtitleConfiguration.Builder(Uri.parse(URI_STRING + "/es"))
                .setMimeType(MimeTypes.TEXT_SSA)
                .setLanguage(/* language= */ "es")
                .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                .setRoleFlags(C.ROLE_FLAG_ALTERNATE)
                .setLabel("label")
                .setId("id")
                .build());

    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setSubtitleConfigurations(subtitleConfigurations)
            .build();

    assertThat(mediaItem.localConfiguration.subtitleConfigurations)
        .isEqualTo(subtitleConfigurations);
    assertThat(mediaItem.localConfiguration.subtitles).isEqualTo(subtitleConfigurations);
  }

  @Test
  @SuppressWarnings("deprecation") // Using deprecated Subtitle type
  public void builderSetSubtitles() {
    List<MediaItem.Subtitle> subtitles =
        ImmutableList.of(
            new MediaItem.Subtitle(
                Uri.parse(URI_STRING + "/en"), MimeTypes.APPLICATION_TTML, /* language= */ "en"),
            new MediaItem.Subtitle(
                Uri.parse(URI_STRING + "/de"),
                MimeTypes.APPLICATION_TTML,
                /* language= */ null,
                C.SELECTION_FLAG_DEFAULT),
            new MediaItem.Subtitle(
                Uri.parse(URI_STRING + "/fr"),
                MimeTypes.APPLICATION_SUBRIP,
                /* language= */ "fr",
                C.SELECTION_FLAG_DEFAULT,
                C.ROLE_FLAG_ALTERNATE,
                "label"));

    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setSubtitles(subtitles).build();

    assertThat(mediaItem.localConfiguration.subtitleConfigurations).isEqualTo(subtitles);
    assertThat(mediaItem.localConfiguration.subtitles).isEqualTo(subtitles);
  }

  @Test
  public void builderSetTag_isNullByDefault() {
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_STRING).build();

    assertThat(mediaItem.localConfiguration.tag).isNull();
  }

  @Test
  public void builderSetTag_setsTag() {
    Object tag = new Object();

    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_STRING).setTag(tag).build();

    assertThat(mediaItem.localConfiguration.tag).isEqualTo(tag);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated field
  public void builderSetClippingConfiguration() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(1000L)
                    .setEndPositionMs(2000L)
                    .setRelativeToLiveWindow(true)
                    .setRelativeToDefaultPosition(true)
                    .setStartsAtKeyFrame(true)
                    .build())
            .build();

    assertThat(mediaItem.clippingConfiguration.startPositionMs).isEqualTo(1000L);
    assertThat(mediaItem.clippingConfiguration.endPositionMs).isEqualTo(2000L);
    assertThat(mediaItem.clippingConfiguration.relativeToLiveWindow).isTrue();
    assertThat(mediaItem.clippingConfiguration.relativeToDefaultPosition).isTrue();
    assertThat(mediaItem.clippingConfiguration.startsAtKeyFrame).isTrue();
    assertThat(mediaItem.clippingConfiguration).isEqualTo(mediaItem.clippingProperties);
  }

  @Test
  public void createDefaultClippingConfigurationInstance_checksDefaultValues() {
    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder().build();

    // Please refrain from altering default values since doing so would cause issues with backwards
    // compatibility.
    assertThat(clippingConfiguration.startPositionMs).isEqualTo(0L);
    assertThat(clippingConfiguration.endPositionMs).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(clippingConfiguration.relativeToLiveWindow).isFalse();
    assertThat(clippingConfiguration.relativeToDefaultPosition).isFalse();
    assertThat(clippingConfiguration.startsAtKeyFrame).isFalse();
    assertThat(clippingConfiguration).isEqualTo(MediaItem.ClippingConfiguration.UNSET);
  }

  @Test
  public void
      createDefaultClippingConfigurationInstance_toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder().build();

    Bundle clippingConfigurationBundle = clippingConfiguration.toBundle();

    // Check that default values are skipped when bundling.
    assertThat(clippingConfigurationBundle.keySet()).isEmpty();

    MediaItem.ClippingConfiguration clippingConfigurationFromBundle =
        MediaItem.ClippingConfiguration.CREATOR.fromBundle(clippingConfigurationBundle);

    assertThat(clippingConfigurationFromBundle).isEqualTo(clippingConfiguration);
  }

  @Test
  public void createClippingConfigurationInstance_roundTripViaBundle_yieldsEqualInstance() {
    // Creates instance by setting some non-default values
    MediaItem.ClippingConfiguration clippingConfiguration =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(1000L)
            .setStartsAtKeyFrame(true)
            .build();

    MediaItem.ClippingConfiguration clippingConfigurationFromBundle =
        MediaItem.ClippingConfiguration.CREATOR.fromBundle(clippingConfiguration.toBundle());

    assertThat(clippingConfigurationFromBundle).isEqualTo(clippingConfiguration);
  }

  @Test
  public void clippingConfigurationBuilder_throwsOnInvalidValues() {
    MediaItem.ClippingConfiguration.Builder clippingConfigurationBuilder =
        new MediaItem.ClippingConfiguration.Builder();
    assertThrows(
        IllegalArgumentException.class, () -> clippingConfigurationBuilder.setStartPositionMs(-1));
    assertThrows(
        IllegalArgumentException.class, () -> clippingConfigurationBuilder.setEndPositionMs(-1));

    MediaItem.ClippingConfiguration clippingConfiguration = clippingConfigurationBuilder.build();

    // Check neither of the setters succeeded in mutating the builder.
    assertThat(clippingConfiguration.startPositionMs).isEqualTo(0L);
    assertThat(clippingConfiguration.endPositionMs).isEqualTo(C.TIME_END_OF_SOURCE);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetStartPositionMs_setsStartPositionMs() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setClipStartPositionMs(1000L).build();

    assertThat(mediaItem.clippingConfiguration.startPositionMs).isEqualTo(1000L);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetStartPositionMs_negativeValue_throws() {
    MediaItem.Builder builder = new MediaItem.Builder();

    assertThrows(IllegalArgumentException.class, () -> builder.setClipStartPositionMs(-1));
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetEndPositionMs_setsEndPositionMs() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setClipEndPositionMs(1000L).build();

    assertThat(mediaItem.clippingConfiguration.endPositionMs).isEqualTo(1000L);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetEndPositionMs_timeEndOfSource_setsEndPositionMs() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setClipEndPositionMs(1000)
            .setClipEndPositionMs(C.TIME_END_OF_SOURCE)
            .build();

    assertThat(mediaItem.clippingConfiguration.endPositionMs).isEqualTo(C.TIME_END_OF_SOURCE);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetEndPositionMs_negativeValue_throws() {
    MediaItem.Builder builder = new MediaItem.Builder();

    assertThrows(IllegalArgumentException.class, () -> builder.setClipEndPositionMs(-1));
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter.
  public void builderSetClippingFlags_setsClippingFlags() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setClipRelativeToDefaultPosition(true)
            .setClipRelativeToLiveWindow(true)
            .setClipStartsAtKeyFrame(true)
            .build();

    assertThat(mediaItem.clippingConfiguration.relativeToDefaultPosition).isTrue();
    assertThat(mediaItem.clippingConfiguration.relativeToLiveWindow).isTrue();
    assertThat(mediaItem.clippingConfiguration.startsAtKeyFrame).isTrue();
  }

  @Test
  public void builderSetAdsConfiguration_justUri() {
    Uri adTagUri = Uri.parse(URI_STRING + "/ad");

    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setAdsConfiguration(new MediaItem.AdsConfiguration.Builder(adTagUri).build())
            .build();

    assertThat(mediaItem.localConfiguration.adsConfiguration.adTagUri).isEqualTo(adTagUri);
    assertThat(mediaItem.localConfiguration.adsConfiguration.adsId).isNull();
  }

  @Test
  public void builderSetAdsConfiguration_withAdsId() {
    Uri adTagUri = Uri.parse(URI_STRING + "/ad");
    Object adsId = new Object();

    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(adTagUri).setAdsId(adsId).build())
            .build();
    assertThat(mediaItem.localConfiguration.adsConfiguration.adTagUri).isEqualTo(adTagUri);
    assertThat(mediaItem.localConfiguration.adsConfiguration.adsId).isEqualTo(adsId);
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter
  public void builderSetAdTagUri_setsAdTagUri() {
    Uri adTagUri = Uri.parse(URI_STRING + "/ad");

    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_STRING).setAdTagUri(adTagUri).build();

    assertThat(mediaItem.localConfiguration.adsConfiguration.adTagUri).isEqualTo(adTagUri);
    assertThat(mediaItem.localConfiguration.adsConfiguration.adsId).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter
  public void builderSetAdTagUriAndAdsId_setsAdsConfiguration() {
    Uri adTagUri = Uri.parse(URI_STRING + "/ad");
    Object adsId = new Object();

    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setAdTagUri(adTagUri, adsId).build();

    assertThat(mediaItem.localConfiguration.adsConfiguration.adTagUri).isEqualTo(adTagUri);
    assertThat(mediaItem.localConfiguration.adsConfiguration.adsId).isEqualTo(adsId);
  }

  @Test
  public void builderSetMediaMetadata_setsMetadata() {
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();

    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setMediaMetadata(mediaMetadata).build();

    assertThat(mediaItem.mediaMetadata).isEqualTo(mediaMetadata);
  }

  @Test
  public void createDefaultLiveConfigurationInstance_checksDefaultValues() {
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().build();

    // Please refrain from altering default values since doing so would cause issues with backwards
    // compatibility.
    assertThat(liveConfiguration.targetOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(liveConfiguration.minOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(liveConfiguration.maxOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(liveConfiguration.minPlaybackSpeed).isEqualTo(C.RATE_UNSET);
    assertThat(liveConfiguration.maxPlaybackSpeed).isEqualTo(C.RATE_UNSET);
    assertThat(liveConfiguration).isEqualTo(MediaItem.LiveConfiguration.UNSET);
  }

  @Test
  public void
      createDefaultLiveConfigurationInstance_toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().build();

    Bundle liveConfigurationBundle = liveConfiguration.toBundle();

    // Check that default values are skipped when bundling.
    assertThat(liveConfigurationBundle.keySet()).isEmpty();

    MediaItem.LiveConfiguration liveConfigurationFromBundle =
        MediaItem.LiveConfiguration.CREATOR.fromBundle(liveConfigurationBundle);

    assertThat(liveConfigurationFromBundle).isEqualTo(liveConfiguration);
  }

  @Test
  public void createLiveConfigurationInstance_roundTripViaBundle_yieldsEqualInstance() {
    // Creates instance by setting some non-default values
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10_000)
            .setMaxPlaybackSpeed(2f)
            .build();

    MediaItem.LiveConfiguration liveConfigurationFromBundle =
        MediaItem.LiveConfiguration.CREATOR.fromBundle(liveConfiguration.toBundle());

    assertThat(liveConfigurationFromBundle).isEqualTo(liveConfiguration);
  }

  @Test
  public void builderSetLiveConfiguration() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(URI_STRING)
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(10_000)
                    .setMinOffsetMs(20_000)
                    .setMaxOffsetMs(30_000)
                    .setMinPlaybackSpeed(0.5f)
                    .setMaxPlaybackSpeed(2f)
                    .build())
            .build();

    assertThat(mediaItem.liveConfiguration.targetOffsetMs).isEqualTo(10_000);
    assertThat(mediaItem.liveConfiguration.minOffsetMs).isEqualTo(20_000);
    assertThat(mediaItem.liveConfiguration.maxOffsetMs).isEqualTo(30_000);
    assertThat(mediaItem.liveConfiguration.minPlaybackSpeed).isEqualTo(0.5f);
    assertThat(mediaItem.liveConfiguration.maxPlaybackSpeed).isEqualTo(2f);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated setter
  public void builderSetLiveTargetOffsetMs_setsLiveTargetOffsetMs() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setLiveTargetOffsetMs(10_000).build();

    assertThat(mediaItem.liveConfiguration.targetOffsetMs).isEqualTo(10_000);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated setter
  public void builderSetMinLivePlaybackSpeed_setsMinLivePlaybackSpeed() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setLiveMinPlaybackSpeed(.9f).build();

    assertThat(mediaItem.liveConfiguration.minPlaybackSpeed).isEqualTo(.9f);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated setter
  public void builderSetMaxLivePlaybackSpeed_setsMaxLivePlaybackSpeed() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setLiveMaxPlaybackSpeed(1.1f).build();

    assertThat(mediaItem.liveConfiguration.maxPlaybackSpeed).isEqualTo(1.1f);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated setter
  public void builderSetMinLiveOffset_setsMinLiveOffset() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setLiveMinOffsetMs(1234).build();

    assertThat(mediaItem.liveConfiguration.minOffsetMs).isEqualTo(1234);
  }

  @Test
  @SuppressWarnings("deprecation") // Tests deprecated setter
  public void builderSetMaxLiveOffset_setsMaxLiveOffset() {
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(URI_STRING).setLiveMaxOffsetMs(1234).build();

    assertThat(mediaItem.liveConfiguration.maxOffsetMs).isEqualTo(1234);
  }

  @Test
  public void builder_setRequestMetadata_setsRequestMetadata() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    RequestMetadata requestMetadata =
        new RequestMetadata.Builder()
            .setMediaUri(Uri.parse("http://test.test"))
            .setSearchQuery("Play media!")
            .setExtras(extras)
            .build();

    MediaItem mediaItem =
        new MediaItem.Builder().setMediaId("mediaID").setRequestMetadata(requestMetadata).build();

    assertThat(mediaItem.requestMetadata).isEqualTo(requestMetadata);
    assertThat(mediaItem.requestMetadata.extras.getString("key")).isEqualTo("value");
  }

  @Test
  @SuppressWarnings("deprecation") // Testing deprecated setter methods
  public void buildUpon_individualSetters_equalsToOriginal() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setAdTagUri(URI_STRING)
            .setClipEndPositionMs(1000)
            .setClipRelativeToDefaultPosition(true)
            .setClipRelativeToLiveWindow(true)
            .setClipStartPositionMs(100)
            .setClipStartsAtKeyFrame(true)
            .setCustomCacheKey("key")
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(URI_STRING + "/license")
            .setDrmLicenseRequestHeaders(
                Collections.singletonMap("Referer", "http://www.google.com"))
            .setDrmMultiSession(true)
            .setDrmForceDefaultLicenseUri(true)
            .setDrmPlayClearContentWithoutKey(true)
            .setDrmSessionForClearTypes(Collections.singletonList(C.TRACK_TYPE_AUDIO))
            .setDrmKeySetId(new byte[] {1, 2, 3})
            .setMediaId("mediaId")
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setMimeType(MimeTypes.APPLICATION_MP4)
            .setUri(URI_STRING)
            .setStreamKeys(Collections.singletonList(new StreamKey(1, 0, 0)))
            .setLiveTargetOffsetMs(20_000)
            .setLiveMinPlaybackSpeed(.9f)
            .setLiveMaxPlaybackSpeed(1.1f)
            .setLiveMinOffsetMs(2222)
            .setLiveMaxOffsetMs(4444)
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(URI_STRING + "/en"))
                        .setMimeType(MimeTypes.APPLICATION_TTML)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                        .setRoleFlags(C.ROLE_FLAG_ALTERNATE)
                        .setLabel("label")
                        .setId("id")
                        .build()))
            .setTag(new Object())
            .build();

    MediaItem copy = mediaItem.buildUpon().build();

    assertThat(copy).isEqualTo(mediaItem);
    assertThat(copy.localConfiguration).isEqualTo(mediaItem.playbackProperties);
  }

  @Test
  public void buildUpon_wholeObjectSetters_equalsToOriginal() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse(URI_STRING)).build())
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(1000)
                    .setRelativeToDefaultPosition(true)
                    .setRelativeToLiveWindow(true)
                    .setStartPositionMs(100)
                    .setStartsAtKeyFrame(true)
                    .build())
            .setCustomCacheKey("key")
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(URI_STRING + "/license")
                    .setLicenseRequestHeaders(ImmutableMap.of("Referer", "http://www.google.com"))
                    .setMultiSession(true)
                    .setForceDefaultLicenseUri(true)
                    .setPlayClearContentWithoutKey(true)
                    .setForcedSessionTrackTypes(ImmutableList.of(C.TRACK_TYPE_AUDIO))
                    .setKeySetId(new byte[] {1, 2, 3})
                    .build())
            .setMediaId("mediaId")
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setMimeType(MimeTypes.APPLICATION_MP4)
            .setUri(URI_STRING)
            .setStreamKeys(ImmutableList.of(new StreamKey(1, 0, 0)))
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(20_000)
                    .setMinPlaybackSpeed(.9f)
                    .setMaxPlaybackSpeed(1.1f)
                    .setMinOffsetMs(2222)
                    .setMaxOffsetMs(4444)
                    .build())
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(URI_STRING + "/en"))
                        .setMimeType(MimeTypes.APPLICATION_TTML)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                        .setRoleFlags(C.ROLE_FLAG_ALTERNATE)
                        .setLabel("label")
                        .setId("id")
                        .build()))
            .setRequestMetadata(
                new RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("http://test.test"))
                    .setSearchQuery("search")
                    .build())
            .setTag(new Object())
            .build();

    MediaItem copy = mediaItem.buildUpon().build();

    assertThat(copy).isEqualTo(mediaItem);
  }

  @Test
  public void roundTripViaBundle_withoutPlaybackProperties_yieldsEqualInstance() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("mediaId")
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(20_000)
                    .setMinOffsetMs(2_222)
                    .setMaxOffsetMs(4_444)
                    .setMinPlaybackSpeed(.9f)
                    .setMaxPlaybackSpeed(1.1f)
                    .build())
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
            .setClipStartPositionMs(100)
            .setClipEndPositionMs(1_000)
            .setClipRelativeToDefaultPosition(true)
            .setClipRelativeToLiveWindow(true)
            .setClipStartsAtKeyFrame(true)
            .setRequestMetadata(
                new RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("http://test.test"))
                    .setSearchQuery("search")
                    .build())
            .build();

    assertThat(mediaItem.localConfiguration).isNull();
    assertThat(MediaItem.CREATOR.fromBundle(mediaItem.toBundle())).isEqualTo(mediaItem);
  }

  @Test
  public void roundTripViaBundle_withPlaybackProperties_dropsPlaybackProperties() {
    MediaItem mediaItem = new MediaItem.Builder().setUri(URI_STRING).build();

    assertThat(mediaItem.localConfiguration).isNotNull();
    assertThat(MediaItem.CREATOR.fromBundle(mediaItem.toBundle()).localConfiguration).isNull();
  }

  @Test
  public void createDefaultMediaItemInstance_checksDefaultValues() {
    MediaItem mediaItem = new MediaItem.Builder().build();

    // Please refrain from altering default values since doing so would cause issues with backwards
    // compatibility.
    assertThat(mediaItem.mediaId).isEqualTo(MediaItem.DEFAULT_MEDIA_ID);
    assertThat(mediaItem.liveConfiguration).isEqualTo(MediaItem.LiveConfiguration.UNSET);
    assertThat(mediaItem.mediaMetadata).isEqualTo(MediaMetadata.EMPTY);
    assertThat(mediaItem.clippingConfiguration).isEqualTo(MediaItem.ClippingConfiguration.UNSET);
    assertThat(mediaItem.requestMetadata).isEqualTo(RequestMetadata.EMPTY);
    assertThat(mediaItem).isEqualTo(MediaItem.EMPTY);
  }

  @Test
  public void createDefaultMediaItemInstance_toBundleSkipsDefaultValues_fromBundleRestoresThem() {
    MediaItem mediaItem = new MediaItem.Builder().build();

    Bundle mediaItemBundle = mediaItem.toBundle();

    // Check that default values are skipped when bundling.
    assertThat(mediaItemBundle.keySet()).isEmpty();

    MediaItem mediaItemFromBundle = MediaItem.CREATOR.fromBundle(mediaItem.toBundle());

    assertThat(mediaItemFromBundle).isEqualTo(mediaItem);
  }

  @Test
  public void createMediaItemInstance_roundTripViaBundle_yieldsEqualInstance() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    // Creates instance by setting some non-default values
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setLiveConfiguration(
                new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(20_000)
                    .setMinOffsetMs(2_222)
                    .setMaxOffsetMs(4_444)
                    .setMinPlaybackSpeed(.9f)
                    .setMaxPlaybackSpeed(1.1f)
                    .build())
            .setRequestMetadata(
                new RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("http://test.test"))
                    .setSearchQuery("search")
                    .setExtras(extras)
                    .build())
            .build();

    MediaItem mediaItemFromBundle = MediaItem.CREATOR.fromBundle(mediaItem.toBundle());

    assertThat(mediaItemFromBundle).isEqualTo(mediaItem);
    assertThat(mediaItemFromBundle.requestMetadata.extras)
        .isEqualTo(mediaItem.requestMetadata.extras);
  }
}
