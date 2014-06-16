/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.demo.full;

import com.google.android.exoplayer.demo.DemoUtil;
import com.google.android.exoplayer.drm.MediaDrmCallback;

import android.annotation.TargetApi;
import android.media.MediaDrm.KeyRequest;
import android.media.MediaDrm.ProvisionRequest;
import android.text.TextUtils;

import org.apache.http.client.ClientProtocolException;

import java.io.IOException;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} for Widevine test content.
 */
@TargetApi(18)
public class WidevineTestMediaDrmCallback implements MediaDrmCallback {

  private static final String WIDEVINE_GTS_DEFAULT_BASE_URI =
      "http://wv-staging-proxy.appspot.com/proxy?provider=YouTube&video_id=";

  private final String defaultUri;

  public WidevineTestMediaDrmCallback(String videoId) {
    defaultUri = WIDEVINE_GTS_DEFAULT_BASE_URI + videoId;
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request)
      throws ClientProtocolException, IOException {
    String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
    return DemoUtil.executePost(url, null, null);
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws IOException {
    String url = request.getDefaultUrl();
    if (TextUtils.isEmpty(url)) {
      url = defaultUri;
    }
    return DemoUtil.executePost(url, request.getData(), null);
  }

}
