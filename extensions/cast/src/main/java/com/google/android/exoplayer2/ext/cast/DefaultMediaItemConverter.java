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
import com.google.android.exoplayer2.ext.cast.MediaItem.DrmConfiguration;
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
  public MediaItem toMediaItem(MediaQueueItem item) {
    return getMediaItem(item.getMedia().getCustomData());
  }

  @Override
  public MediaQueueItem toMediaQueueItem(MediaItem item) {
    if (item.mimeType == null) {
      throw new IllegalArgumentException("The item must specify its mimeType");
    }
    MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    if (item.title != null) {
      metadata.putString(MediaMetadata.KEY_TITLE, item.title);
    }
    MediaInfo mediaInfo =
        new MediaInfo.Builder(item.uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(item.mimeType)
            .setMetadata(metadata)
            .setCustomData(getCustomData(item))
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
        builder.setTitle(mediaItemJson.getString(KEY_TITLE));
      }
      if (mediaItemJson.has(KEY_MIME_TYPE)) {
        builder.setMimeType(mediaItemJson.getString(KEY_MIME_TYPE));
      }
      if (mediaItemJson.has(KEY_DRM_CONFIGURATION)) {
        builder.setDrmConfiguration(
            getDrmConfiguration(mediaItemJson.getJSONObject(KEY_DRM_CONFIGURATION)));
      }
      return builder.build();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private static DrmConfiguration getDrmConfiguration(JSONObject json) throws JSONException {
    UUID uuid = UUID.fromString(json.getString(KEY_UUID));
    Uri licenseUri = Uri.parse(json.getString(KEY_LICENSE_URI));
    JSONObject requestHeadersJson = json.getJSONObject(KEY_REQUEST_HEADERS);
    HashMap<String, String> requestHeaders = new HashMap<>();
    for (Iterator<String> iterator = requestHeadersJson.keys(); iterator.hasNext(); ) {
      String key = iterator.next();
      requestHeaders.put(key, requestHeadersJson.getString(key));
    }
    return new DrmConfiguration(uuid, licenseUri, requestHeaders);
  }

  // Serialization.

  private static JSONObject getCustomData(MediaItem item) {
    JSONObject json = new JSONObject();
    try {
      json.put(KEY_MEDIA_ITEM, getMediaItemJson(item));
      JSONObject playerConfigJson = getPlayerConfigJson(item);
      if (playerConfigJson != null) {
        json.put(KEY_PLAYER_CONFIG, playerConfigJson);
      }
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
    return json;
  }

  private static JSONObject getMediaItemJson(MediaItem item) throws JSONException {
    JSONObject json = new JSONObject();
    json.put(KEY_URI, item.uri.toString());
    json.put(KEY_TITLE, item.title);
    json.put(KEY_MIME_TYPE, item.mimeType);
    if (item.drmConfiguration != null) {
      json.put(KEY_DRM_CONFIGURATION, getDrmConfigurationJson(item.drmConfiguration));
    }
    return json;
  }

  private static JSONObject getDrmConfigurationJson(DrmConfiguration drmConfiguration)
      throws JSONException {
    JSONObject json = new JSONObject();
    json.put(KEY_UUID, drmConfiguration.uuid);
    json.put(KEY_LICENSE_URI, drmConfiguration.licenseUri);
    json.put(KEY_REQUEST_HEADERS, new JSONObject(drmConfiguration.requestHeaders));
    return json;
  }

  @Nullable
  private static JSONObject getPlayerConfigJson(MediaItem item) throws JSONException {
    DrmConfiguration drmConfiguration = item.drmConfiguration;
    if (drmConfiguration == null) {
      return null;
    }

    String drmScheme;
    if (C.WIDEVINE_UUID.equals(drmConfiguration.uuid)) {
      drmScheme = "widevine";
    } else if (C.PLAYREADY_UUID.equals(drmConfiguration.uuid)) {
      drmScheme = "playready";
    } else {
      return null;
    }

    JSONObject exoPlayerConfigJson = new JSONObject();
    exoPlayerConfigJson.put("withCredentials", false);
    exoPlayerConfigJson.put("protectionSystem", drmScheme);
    if (drmConfiguration.licenseUri != null) {
      exoPlayerConfigJson.put("licenseUrl", drmConfiguration.licenseUri);
    }
    if (!drmConfiguration.requestHeaders.isEmpty()) {
      exoPlayerConfigJson.put("headers", new JSONObject(drmConfiguration.requestHeaders));
    }

    return exoPlayerConfigJson;
  }
}
