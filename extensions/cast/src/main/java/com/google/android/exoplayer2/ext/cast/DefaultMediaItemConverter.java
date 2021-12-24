/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/** Default {@link MediaItemConverter} implementation. */
public final class DefaultMediaItemConverter implements MediaItemConverter {

  private static final String KEY_MEDIA_ITEM = "mediaItem";
  private static final String KEY_PLAYER_CONFIG = "exoPlayerConfig";
  private static final String KEY_URI = "uri";
  private static final String KEY_TITLE = "title";
  private static final String KEY_MIME_TYPE = "mimeType";
  private static final String KEY_DRM_CONFIGURATION = "drmConfiguration";
  private static final String KEY_UUID = "uuid";
  private static final String KEY_LICENSE_URI = "licenseUri";
  private static final String KEY_REQUEST_HEADERS = "requestHeaders";

  @Override
  public MediaItem toMediaItem(MediaQueueItem mediaQueueItem) {
    // `item` came from `toMediaQueueItem()` so the custom JSON data must be set.
    MediaInfo mediaInfo = mediaQueueItem.getMedia();
    Assertions.checkNotNull(mediaInfo);
    return getMediaItem(Assertions.checkNotNull(mediaInfo.getCustomData()));
  }

  @Override
  public MediaQueueItem toMediaQueueItem(MediaItem mediaItem) {
    Assertions.checkNotNull(mediaItem.localConfiguration);
    if (mediaItem.localConfiguration.mimeType == null) {
      throw new IllegalArgumentException("The item must specify its mimeType");
    }
    MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    if (mediaItem.mediaMetadata.title != null) {
      metadata.putString(MediaMetadata.KEY_TITLE, mediaItem.mediaMetadata.title.toString());
    }
    MediaInfo mediaInfo =
        new MediaInfo.Builder(mediaItem.localConfiguration.uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mediaItem.localConfiguration.mimeType)
            .setMetadata(metadata)
            .setCustomData(getCustomData(mediaItem))
            .build();
    return new MediaQueueItem.Builder(mediaInfo).build();
  }

  // Deserialization.

  private static MediaItem getMediaItem(JSONObject customData) {
    try {
      JSONObject mediaItemJson = customData.getJSONObject(KEY_MEDIA_ITEM);
      MediaItem.Builder builder = new MediaItem.Builder();
      builder.setUri(Uri.parse(mediaItemJson.getString(KEY_URI)));
      if (mediaItemJson.has(KEY_TITLE)) {
        com.google.android.exoplayer2.MediaMetadata mediaMetadata =
            new com.google.android.exoplayer2.MediaMetadata.Builder()
                .setTitle(mediaItemJson.getString(KEY_TITLE))
                .build();
        builder.setMediaMetadata(mediaMetadata);
      }
      if (mediaItemJson.has(KEY_MIME_TYPE)) {
        builder.setMimeType(mediaItemJson.getString(KEY_MIME_TYPE));
      }
      if (mediaItemJson.has(KEY_DRM_CONFIGURATION)) {
        populateDrmConfiguration(mediaItemJson.getJSONObject(KEY_DRM_CONFIGURATION), builder);
      }
      return builder.build();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private static void populateDrmConfiguration(JSONObject json, MediaItem.Builder mediaItem)
      throws JSONException {
    MediaItem.DrmConfiguration.Builder drmConfiguration =
        new MediaItem.DrmConfiguration.Builder(UUID.fromString(json.getString(KEY_UUID)))
            .setLicenseUri(json.getString(KEY_LICENSE_URI));
    JSONObject requestHeadersJson = json.getJSONObject(KEY_REQUEST_HEADERS);
    HashMap<String, String> requestHeaders = new HashMap<>();
    for (Iterator<String> iterator = requestHeadersJson.keys(); iterator.hasNext(); ) {
      String key = iterator.next();
      requestHeaders.put(key, requestHeadersJson.getString(key));
    }
    drmConfiguration.setLicenseRequestHeaders(requestHeaders);
    mediaItem.setDrmConfiguration(drmConfiguration.build());
  }

  // Serialization.

  private static JSONObject getCustomData(MediaItem mediaItem) {
    JSONObject json = new JSONObject();
    try {
      json.put(KEY_MEDIA_ITEM, getMediaItemJson(mediaItem));
      @Nullable JSONObject playerConfigJson = getPlayerConfigJson(mediaItem);
      if (playerConfigJson != null) {
        json.put(KEY_PLAYER_CONFIG, playerConfigJson);
      }
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
    return json;
  }

  private static JSONObject getMediaItemJson(MediaItem mediaItem) throws JSONException {
    Assertions.checkNotNull(mediaItem.localConfiguration);
    JSONObject json = new JSONObject();
    json.put(KEY_TITLE, mediaItem.mediaMetadata.title);
    json.put(KEY_URI, mediaItem.localConfiguration.uri.toString());
    json.put(KEY_MIME_TYPE, mediaItem.localConfiguration.mimeType);
    if (mediaItem.localConfiguration.drmConfiguration != null) {
      json.put(
          KEY_DRM_CONFIGURATION,
          getDrmConfigurationJson(mediaItem.localConfiguration.drmConfiguration));
    }
    return json;
  }

  private static JSONObject getDrmConfigurationJson(MediaItem.DrmConfiguration drmConfiguration)
      throws JSONException {
    JSONObject json = new JSONObject();
    json.put(KEY_UUID, drmConfiguration.scheme);
    json.put(KEY_LICENSE_URI, drmConfiguration.licenseUri);
    json.put(KEY_REQUEST_HEADERS, new JSONObject(drmConfiguration.licenseRequestHeaders));
    return json;
  }

  @Nullable
  private static JSONObject getPlayerConfigJson(MediaItem mediaItem) throws JSONException {
    if (mediaItem.localConfiguration == null
        || mediaItem.localConfiguration.drmConfiguration == null) {
      return null;
    }
    MediaItem.DrmConfiguration drmConfiguration = mediaItem.localConfiguration.drmConfiguration;

    String drmScheme;
    if (C.WIDEVINE_UUID.equals(drmConfiguration.scheme)) {
      drmScheme = "widevine";
    } else if (C.PLAYREADY_UUID.equals(drmConfiguration.scheme)) {
      drmScheme = "playready";
    } else {
      return null;
    }

    JSONObject playerConfigJson = new JSONObject();
    playerConfigJson.put("withCredentials", false);
    playerConfigJson.put("protectionSystem", drmScheme);
    if (drmConfiguration.licenseUri != null) {
      playerConfigJson.put("licenseUrl", drmConfiguration.licenseUri);
    }
    if (!drmConfiguration.licenseRequestHeaders.isEmpty()) {
      playerConfigJson.put("headers", new JSONObject(drmConfiguration.licenseRequestHeaders));
    }

    return playerConfigJson;
  }
}
