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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Util to read from an intent. */
public class IntentUtil {

  // Actions.

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";

  // Activity extras.

  public static final String SPHERICAL_STEREO_MODE_EXTRA = "spherical_stereo_mode";
  public static final String SPHERICAL_STEREO_MODE_MONO = "mono";
  public static final String SPHERICAL_STEREO_MODE_TOP_BOTTOM = "top_bottom";
  public static final String SPHERICAL_STEREO_MODE_LEFT_RIGHT = "left_right";

  // Player configuration extras.

  public static final String ABR_ALGORITHM_EXTRA = "abr_algorithm";
  public static final String ABR_ALGORITHM_DEFAULT = "default";
  public static final String ABR_ALGORITHM_RANDOM = "random";

  // Media item configuration extras.

  public static final String URI_EXTRA = "uri";
  public static final String EXTENSION_EXTRA = "extension";
  public static final String IS_LIVE_EXTRA = "is_live";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_SESSION_FOR_CLEAR_TYPES_EXTRA = "drm_session_for_clear_types";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";
  public static final String SUBTITLE_URI_EXTRA = "subtitle_uri";
  public static final String SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type";
  public static final String SUBTITLE_LANGUAGE_EXTRA = "subtitle_language";
  // For backwards compatibility only.
  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";
  public static final String TUNNELING_EXTRA = "tunneling";

  /** Creates a list of {@link MediaItem media items} from an {@link Intent}. */
  public static List<MediaItem> createMediaItemsFromIntent(
      Intent intent, DownloadTracker downloadTracker) {
    List<MediaItem> mediaItems = new ArrayList<>();
    if (ACTION_VIEW_LIST.equals(intent.getAction())) {
      int index = 0;
      while (intent.hasExtra(URI_EXTRA + "_" + index)) {
        Uri uri = Uri.parse(intent.getStringExtra(URI_EXTRA + "_" + index));
        mediaItems.add(
            createMediaItemFromIntent(
                uri,
                intent,
                /* extrasKeySuffix= */ "_" + index,
                downloadTracker.getDownloadRequest(uri)));
        index++;
      }
    } else {
      Uri uri = intent.getData();
      mediaItems.add(
          createMediaItemFromIntent(
              uri, intent, /* extrasKeySuffix= */ "", downloadTracker.getDownloadRequest(uri)));
    }
    return mediaItems;
  }

  private static MediaItem createMediaItemFromIntent(
      Uri uri, Intent intent, String extrasKeySuffix, @Nullable DownloadRequest downloadRequest) {
    String extension = intent.getStringExtra(EXTENSION_EXTRA + extrasKeySuffix);
    MediaItem.Builder builder =
        new MediaItem.Builder()
            .setSourceUri(uri)
            .setStreamKeys(downloadRequest != null ? downloadRequest.streamKeys : null)
            .setCustomCacheKey(downloadRequest != null ? downloadRequest.customCacheKey : null)
            .setMimeType(inferAdaptiveStreamMimeType(uri, extension))
            .setAdTagUri(intent.getStringExtra(AD_TAG_URI_EXTRA + extrasKeySuffix))
            .setSubtitles(createSubtitlesFromIntent(intent, extrasKeySuffix));
    return populateDrmPropertiesFromIntent(builder, intent, extrasKeySuffix).build();
  }

  private static List<MediaItem.Subtitle> createSubtitlesFromIntent(
      Intent intent, String extrasKeySuffix) {
    if (!intent.hasExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(
        new MediaItem.Subtitle(
            Uri.parse(intent.getStringExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix)),
            checkNotNull(intent.getStringExtra(SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix)),
            intent.getStringExtra(SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix),
            C.SELECTION_FLAG_DEFAULT));
  }

  private static MediaItem.Builder populateDrmPropertiesFromIntent(
      MediaItem.Builder builder, Intent intent, String extrasKeySuffix) {
    String schemeKey = DRM_SCHEME_EXTRA + extrasKeySuffix;
    String schemeUuidKey = DRM_SCHEME_UUID_EXTRA + extrasKeySuffix;
    if (!intent.hasExtra(schemeKey) && !intent.hasExtra(schemeUuidKey)) {
      return builder;
    }
    String drmSchemeExtra =
        intent.hasExtra(schemeKey)
            ? intent.getStringExtra(schemeKey)
            : intent.getStringExtra(schemeUuidKey);
    String[] drmSessionForClearTypesExtra =
        intent.getStringArrayExtra(DRM_SESSION_FOR_CLEAR_TYPES_EXTRA + extrasKeySuffix);
    Map<String, String> headers = new HashMap<>();
    String[] keyRequestPropertiesArray =
        intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix);
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length; i += 2) {
        headers.put(keyRequestPropertiesArray[i], keyRequestPropertiesArray[i + 1]);
      }
    }
    builder
        .setDrmUuid(Util.getDrmUuid(Util.castNonNull(drmSchemeExtra)))
        .setDrmLicenseUri(intent.getStringExtra(DRM_LICENSE_URL_EXTRA + extrasKeySuffix))
        .setDrmSessionForClearTypes(toTrackTypeList(drmSessionForClearTypesExtra))
        .setDrmMultiSession(
            intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, false))
        .setDrmLicenseRequestHeaders(headers);
    return builder;
  }

  private static List<Integer> toTrackTypeList(@Nullable String[] trackTypeStringsArray) {
    if (trackTypeStringsArray == null) {
      return Collections.emptyList();
    }
    HashSet<Integer> trackTypes = new HashSet<>();
    for (String trackTypeString : trackTypeStringsArray) {
      switch (Util.toLowerInvariant(trackTypeString)) {
        case "audio":
          trackTypes.add(C.TRACK_TYPE_AUDIO);
          break;
        case "video":
          trackTypes.add(C.TRACK_TYPE_VIDEO);
          break;
        default:
          throw new IllegalArgumentException("Invalid track type: " + trackTypeString);
      }
    }
    return new ArrayList<>(trackTypes);
  }

  @Nullable
  private static String inferAdaptiveStreamMimeType(Uri uri, @Nullable String extension) {
    @C.ContentType int contentType = Util.inferContentType(uri, extension);
    switch (contentType) {
      case C.TYPE_DASH:
        return MimeTypes.APPLICATION_MPD;
      case C.TYPE_HLS:
        return MimeTypes.APPLICATION_M3U8;
      case C.TYPE_SS:
        return MimeTypes.APPLICATION_SS;
      case C.TYPE_OTHER:
      default:
        return null;
    }
  }
}
