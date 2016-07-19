/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.playbacktests.util;

import android.annotation.TargetApi;
import android.media.MediaDrm;
import android.text.TextUtils;

import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} for Widevine test content.
 */
@TargetApi(18)
public final class TestMediaDrmCallback implements MediaDrmCallback {

  private static final String WIDEVINE_BASE_URL = "https://proxy.uat.widevine.com/proxy";

  private final String defaultUrl;
  private final Map<String, String> keyRequestProperties;

  public static TestMediaDrmCallback newWidevineInstance(String contentId, String provider) {
    String defaultUrl = WIDEVINE_BASE_URL + "?video_id=" + contentId + "&provider=" + provider;
    return new TestMediaDrmCallback(defaultUrl, null);
  }

  private TestMediaDrmCallback(String defaultUrl, Map<String, String> keyRequestProperties) {
    this.defaultUrl = defaultUrl;
    this.keyRequestProperties = keyRequestProperties;
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, MediaDrm.ProvisionRequest request)
    throws IOException {
    String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData(),
        Charset.defaultCharset());
    return executePost(url, null, null);
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, MediaDrm.KeyRequest request) throws Exception {
    String url = request.getDefaultUrl();
    if (TextUtils.isEmpty(url)) {
      url = defaultUrl;
    }
    return executePost(url, request.getData(), keyRequestProperties);
  }

  private static byte[] executePost(String url, byte[] data, Map<String, String> requestProperties)
      throws IOException {
    HttpURLConnection urlConnection = null;
    try {
      urlConnection = (HttpURLConnection) new URL(url).openConnection();
      urlConnection.setRequestMethod("POST");
      urlConnection.setDoOutput(data != null);
      urlConnection.setDoInput(true);
      if (requestProperties != null) {
        for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
          urlConnection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
        }
      }
      // Write the request body, if there is one.
      if (data != null) {
        OutputStream out = urlConnection.getOutputStream();
        try {
          out.write(data);
        } finally {
          out.close();
        }
      }
      // Read and return the response body.
      InputStream inputStream = urlConnection.getInputStream();
      try {
        return Util.toByteArray(inputStream);
      } finally {
        inputStream.close();
      }
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
  }
}
