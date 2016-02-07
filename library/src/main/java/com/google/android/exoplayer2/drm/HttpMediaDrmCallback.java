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
package com.google.android.exoplayer2.drm;

import android.annotation.TargetApi;
import android.net.Uri;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} that makes requests using {@link HttpDataSource} instances.
 */
@TargetApi(18)
public final class HttpMediaDrmCallback implements MediaDrmCallback {

  private static final Map<String, String> PLAYREADY_KEY_REQUEST_PROPERTIES;
  static {
    HashMap<String, String> keyRequestProperties = new HashMap<>();
    keyRequestProperties.put("Content-Type", "text/xml");
    keyRequestProperties.put("SOAPAction",
        "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
    PLAYREADY_KEY_REQUEST_PROPERTIES = keyRequestProperties;
  }

  private final HttpDataSource.Factory dataSourceFactory;
  private final String defaultUrl;

  /**
   * @param defaultUrl The default license URL.
   * @param dataSourceFactory A factory from which to obtain {@link HttpDataSource} instances.
   */
  public HttpMediaDrmCallback(String defaultUrl, HttpDataSource.Factory dataSourceFactory) {
    this.dataSourceFactory = dataSourceFactory;
    this.defaultUrl = defaultUrl;
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
    String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
    return executePost(url, null, null);
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception {
    String url = request.getDefaultUrl();
    if (TextUtils.isEmpty(url)) {
      url = defaultUrl;
    }
    Map<String, String> keyRequestProperties = C.PLAYREADY_UUID.equals(uuid)
        ? PLAYREADY_KEY_REQUEST_PROPERTIES : null;
    return executePost(url, request.getData(), keyRequestProperties);
  }

  private byte[] executePost(String url, byte[] data, Map<String, String> requestProperties)
      throws IOException {
    HttpDataSource dataSource = dataSourceFactory.createDataSource();
    // Note: This will be overridden by a Content-Type in requestProperties, if one is set.
    dataSource.setRequestProperty("Content-Type", "application/octet-stream");
    if (requestProperties != null) {
      for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
        dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
      }
    }
    DataSpec dataSpec = new DataSpec(Uri.parse(url), data, 0, 0, C.LENGTH_UNSET, null,
        DataSpec.FLAG_ALLOW_GZIP);
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
    try {
      return Util.toByteArray(inputStream);
    } finally {
      inputStream.close();
    }
  }

}
