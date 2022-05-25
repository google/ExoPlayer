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
package com.google.android.exoplayer2.demo;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration;
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Util to read from and populate an intent. */
public class IntentUtil {

  // Actions.

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";

  // Activity extras.
  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";

  // Media item configuration extras.

  public static final String URI_EXTRA = "uri";
  public static final String TITLE_EXTRA = "title";
  public static final String MIME_TYPE_EXTRA = "mime_type";
  public static final String CLIP_START_POSITION_MS_EXTRA = "clip_start_position_ms";
  public static final String CLIP_END_POSITION_MS_EXTRA = "clip_end_position_ms";

  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URI_EXTRA = "drm_license_uri";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_SESSION_FOR_CLEAR_CONTENT = "drm_session_for_clear_content";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  public static final String DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA = "drm_force_default_license_uri";

  public static final String SUBTITLE_URI_EXTRA = "subtitle_uri";
  public static final String SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type";
  public static final String SUBTITLE_LANGUAGE_EXTRA = "subtitle_language";

  /** Creates a list of {@link MediaItem media items} from an {@link Intent}. */
  public static List<MediaItem> createMediaItemsFromIntent(Intent intent) {
    List<MediaItem> mediaItems = new ArrayList<>();
    if (ACTION_VIEW_LIST.equals(intent.getAction())) {
      int index = 0;
      while (intent.hasExtra(URI_EXTRA + "_" + index)) {
        Uri uri = Uri.parse(intent.getStringExtra(URI_EXTRA + "_" + index));
        mediaItems.add(createMediaItemFromIntent(uri, intent, /* extrasKeySuffix= */ "_" + index));
        index++;
      }
    } else {
      Uri uri = intent.getData();
      mediaItems.add(createMediaItemFromIntent(uri, intent, /* extrasKeySuffix= */ ""));
    }
    return mediaItems;
  }

  /** Populates the intent with the given list of {@link MediaItem media items}. */
  public static void addToIntent(List<MediaItem> mediaItems, Intent intent) {
    checkArgument(!mediaItems.isEmpty());
    if (mediaItems.size() == 1) {
      MediaItem mediaItem = mediaItems.get(0);
      MediaItem.LocalConfiguration localConfiguration = checkNotNull(mediaItem.localConfiguration);
      intent.setAction(ACTION_VIEW).setData(mediaItem.localConfiguration.uri);
      if (mediaItem.mediaMetadata.title != null) {
        intent.putExtra(TITLE_EXTRA, mediaItem.mediaMetadata.title);
      }
      addPlaybackPropertiesToIntent(localConfiguration, intent, /* extrasKeySuffix= */ "");
      addClippingConfigurationToIntent(
          mediaItem.clippingConfiguration, intent, /* extrasKeySuffix= */ "");
    } else {
      intent.setAction(ACTION_VIEW_LIST);
      for (int i = 0; i < mediaItems.size(); i++) {
        MediaItem mediaItem = mediaItems.get(i);
        MediaItem.LocalConfiguration localConfiguration =
            checkNotNull(mediaItem.localConfiguration);
        intent.putExtra(URI_EXTRA + ("_" + i), localConfiguration.uri.toString());
        addPlaybackPropertiesToIntent(localConfiguration, intent, /* extrasKeySuffix= */ "_" + i);
        addClippingConfigurationToIntent(
            mediaItem.clippingConfiguration, intent, /* extrasKeySuffix= */ "_" + i);
        if (mediaItem.mediaMetadata.title != null) {
          intent.putExtra(TITLE_EXTRA + ("_" + i), mediaItem.mediaMetadata.title);
        }
      }
    }
  }

  private static MediaItem createMediaItemFromIntent(
      Uri uri, Intent intent, String extrasKeySuffix) {
    @Nullable String mimeType = intent.getStringExtra(MIME_TYPE_EXTRA + extrasKeySuffix);
    @Nullable String title = intent.getStringExtra(TITLE_EXTRA + extrasKeySuffix);
    @Nullable String adTagUri = intent.getStringExtra(AD_TAG_URI_EXTRA + extrasKeySuffix);
    @Nullable
    SubtitleConfiguration subtitleConfiguration =
        createSubtitleConfiguration(intent, extrasKeySuffix);
    MediaItem.Builder builder =
        new MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
            .setClippingConfiguration(
                new ClippingConfiguration.Builder()
                    .setStartPositionMs(
                        intent.getLongExtra(CLIP_START_POSITION_MS_EXTRA + extrasKeySuffix, 0))
                    .setEndPositionMs(
                        intent.getLongExtra(
                            CLIP_END_POSITION_MS_EXTRA + extrasKeySuffix, C.TIME_END_OF_SOURCE))
                    .build());
    if (adTagUri != null) {
      builder.setAdsConfiguration(
          new MediaItem.AdsConfiguration.Builder(Uri.parse(adTagUri)).build());
    }
    if (subtitleConfiguration != null) {
      builder.setSubtitleConfigurations(ImmutableList.of(subtitleConfiguration));
    }

    return populateDrmPropertiesFromIntent(builder, intent, extrasKeySuffix).build();
  }

  @Nullable
  private static MediaItem.SubtitleConfiguration createSubtitleConfiguration(
      Intent intent, String extrasKeySuffix) {
    if (!intent.hasExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix)) {
      return null;
    }
    return new MediaItem.SubtitleConfiguration.Builder(
            Uri.parse(intent.getStringExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix)))
        .setMimeType(
            checkNotNull(intent.getStringExtra(SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix)))
        .setLanguage(intent.getStringExtra(SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix))
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build();
  }

  private static MediaItem.Builder populateDrmPropertiesFromIntent(
      MediaItem.Builder builder, Intent intent, String extrasKeySuffix) {
    String schemeKey = DRM_SCHEME_EXTRA + extrasKeySuffix;
    @Nullable String drmSchemeExtra = intent.getStringExtra(schemeKey);
    if (drmSchemeExtra == null) {
      return builder;
    }
    Map<String, String> headers = new HashMap<>();
    @Nullable
    String[] keyRequestPropertiesArray =
        intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix);
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length; i += 2) {
        headers.put(keyRequestPropertiesArray[i], keyRequestPropertiesArray[i + 1]);
      }
    }
    @Nullable UUID drmUuid = Util.getDrmUuid(drmSchemeExtra);
    if (drmUuid != null) {
      builder.setDrmConfiguration(
          new MediaItem.DrmConfiguration.Builder(drmUuid)
              .setLicenseUri(intent.getStringExtra(DRM_LICENSE_URI_EXTRA + extrasKeySuffix))
              .setMultiSession(
                  intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, false))
              .setForceDefaultLicenseUri(
                  intent.getBooleanExtra(
                      DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA + extrasKeySuffix, false))
              .setLicenseRequestHeaders(headers)
              .setForceSessionsForAudioAndVideoTracks(
                  intent.getBooleanExtra(DRM_SESSION_FOR_CLEAR_CONTENT + extrasKeySuffix, false))
              .build());
    }
    return builder;
  }

  private static void addPlaybackPropertiesToIntent(
      MediaItem.LocalConfiguration localConfiguration, Intent intent, String extrasKeySuffix) {
    intent
        .putExtra(MIME_TYPE_EXTRA + extrasKeySuffix, localConfiguration.mimeType)
        .putExtra(
            AD_TAG_URI_EXTRA + extrasKeySuffix,
            localConfiguration.adsConfiguration != null
                ? localConfiguration.adsConfiguration.adTagUri.toString()
                : null);
    if (localConfiguration.drmConfiguration != null) {
      addDrmConfigurationToIntent(localConfiguration.drmConfiguration, intent, extrasKeySuffix);
    }
    if (!localConfiguration.subtitleConfigurations.isEmpty()) {
      checkState(localConfiguration.subtitleConfigurations.size() == 1);
      MediaItem.SubtitleConfiguration subtitleConfiguration =
          localConfiguration.subtitleConfigurations.get(0);
      intent.putExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix, subtitleConfiguration.uri.toString());
      intent.putExtra(SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix, subtitleConfiguration.mimeType);
      intent.putExtra(SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix, subtitleConfiguration.language);
    }
  }

  private static void addDrmConfigurationToIntent(
      MediaItem.DrmConfiguration drmConfiguration, Intent intent, String extrasKeySuffix) {
    intent.putExtra(DRM_SCHEME_EXTRA + extrasKeySuffix, drmConfiguration.scheme.toString());
    intent.putExtra(
        DRM_LICENSE_URI_EXTRA + extrasKeySuffix,
        drmConfiguration.licenseUri != null ? drmConfiguration.licenseUri.toString() : null);
    intent.putExtra(DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, drmConfiguration.multiSession);
    intent.putExtra(
        DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA + extrasKeySuffix,
        drmConfiguration.forceDefaultLicenseUri);

    String[] drmKeyRequestProperties =
        new String[drmConfiguration.licenseRequestHeaders.size() * 2];
    int index = 0;
    for (Map.Entry<String, String> entry : drmConfiguration.licenseRequestHeaders.entrySet()) {
      drmKeyRequestProperties[index++] = entry.getKey();
      drmKeyRequestProperties[index++] = entry.getValue();
    }
    intent.putExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix, drmKeyRequestProperties);

    List<@C.TrackType Integer> forcedDrmSessionTrackTypes =
        drmConfiguration.forcedSessionTrackTypes;
    if (!forcedDrmSessionTrackTypes.isEmpty()) {
      // Only video and audio together are supported.
      checkState(
          forcedDrmSessionTrackTypes.size() == 2
              && forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_VIDEO)
              && forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_AUDIO));
      intent.putExtra(DRM_SESSION_FOR_CLEAR_CONTENT + extrasKeySuffix, true);
    }
  }

  private static void addClippingConfigurationToIntent(
      MediaItem.ClippingConfiguration clippingConfiguration,
      Intent intent,
      String extrasKeySuffix) {
    if (clippingConfiguration.startPositionMs != 0) {
      intent.putExtra(
          CLIP_START_POSITION_MS_EXTRA + extrasKeySuffix, clippingConfiguration.startPositionMs);
    }
    if (clippingConfiguration.endPositionMs != C.TIME_END_OF_SOURCE) {
      intent.putExtra(
          CLIP_END_POSITION_MS_EXTRA + extrasKeySuffix, clippingConfiguration.endPositionMs);
    }
  }
}
