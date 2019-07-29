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

import com.google.android.exoplayer2.C;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import org.json.JSONException;
import org.json.JSONObject;

/** Default {@link MediaItemConverter} implementation. */
public final class DefaultMediaItemConverter implements MediaItemConverter {

  @Override
  public MediaQueueItem toMediaQueueItem(MediaItem item) {
    if (item.mimeType == null) {
      throw new IllegalArgumentException("The item must specify its mimeType");
    }
    MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    if (item.title != null) {
      movieMetadata.putString(MediaMetadata.KEY_TITLE, item.title);
    }
    MediaInfo mediaInfo =
        new MediaInfo.Builder(item.uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(item.mimeType)
            .setMetadata(movieMetadata)
            .setCustomData(getCustomData(item))
            .build();
    return new MediaQueueItem.Builder(mediaInfo).build();
  }

  private static JSONObject getCustomData(MediaItem item) {
    JSONObject customData = new JSONObject();

    MediaItem.DrmConfiguration drmConfiguration = item.drmConfiguration;
    if (drmConfiguration == null) {
      return customData;
    }

    String drmScheme;
    if (C.WIDEVINE_UUID.equals(drmConfiguration.uuid)) {
      drmScheme = "widevine";
    } else if (C.PLAYREADY_UUID.equals(drmConfiguration.uuid)) {
      drmScheme = "playready";
    } else {
      return customData;
    }

    JSONObject exoPlayerConfig = new JSONObject();
    try {
      exoPlayerConfig.put("withCredentials", false);
      exoPlayerConfig.put("protectionSystem", drmScheme);
      if (drmConfiguration.licenseUri != null) {
        exoPlayerConfig.put("licenseUrl", drmConfiguration.licenseUri);
      }
      if (!drmConfiguration.requestHeaders.isEmpty()) {
        exoPlayerConfig.put("headers", new JSONObject(drmConfiguration.requestHeaders));
      }
      customData.put("exoPlayerConfig", exoPlayerConfig);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }

    return customData;
  }
}
