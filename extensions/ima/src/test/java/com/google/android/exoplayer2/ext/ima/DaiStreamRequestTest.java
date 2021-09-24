/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ima;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DaiStreamRequest}. */
@RunWith(AndroidJUnit4.class)
public final class DaiStreamRequestTest {

  private static final String ASSET_KEY = "testAssetKey";
  private static final String API_KEY = "testApiKey";
  private static final String CONTENT_SOURCE_ID = "testContentSourceId";
  private static final String VIDEO_ID = "testVideoId";
  private static final String MANIFEST_SUFFIX = "testManifestSuffix";
  private static final String CONTENT_URL =
      "http://google.com/contentUrl?queryParamName=queryParamValue";
  private static final String AUTH_TOKEN = "testAuthToken";
  private static final String STREAM_ACTIVITY_MONITOR_ID = "testStreamActivityMonitorId";
  private static final int FORMAT_DASH = 0;
  private static final int FORMAT_HLS = 2;
  private static final Map<String, String> adTagParameters = new HashMap<>();

  static {
    adTagParameters.put("param1", "value1");
    adTagParameters.put("param2", "value2");
  }

  @Test
  public void liveRequestSerializationAndDeserialization() {
    DaiStreamRequest.Builder request = new DaiStreamRequest.Builder();
    request.setAssetKey(ASSET_KEY);
    request.setApiKey(API_KEY);
    request.setManifestSuffix(MANIFEST_SUFFIX);
    request.setContentUrl(CONTENT_URL);
    request.setAuthToken(AUTH_TOKEN);
    request.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    request.setFormat(FORMAT_HLS);
    request.setAdTagParameters(adTagParameters);

    DaiStreamRequest requestAfterConversions = DaiStreamRequest.fromUri(request.build().toUri());
    assertThat(requestAfterConversions.getStreamRequest().getAssetKey()).isEqualTo(ASSET_KEY);
    assertThat(requestAfterConversions.getStreamRequest().getApiKey()).isEqualTo(API_KEY);
    assertThat(requestAfterConversions.getStreamRequest().getManifestSuffix())
        .isEqualTo(MANIFEST_SUFFIX);
    assertThat(requestAfterConversions.getStreamRequest().getContentUrl()).isEqualTo(CONTENT_URL);
    assertThat(requestAfterConversions.getStreamRequest().getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(requestAfterConversions.getStreamRequest().getStreamActivityMonitorId())
        .isEqualTo(STREAM_ACTIVITY_MONITOR_ID);
    assertThat(requestAfterConversions.getStreamRequest().getFormat()).isEqualTo(StreamFormat.HLS);
    assertThat(requestAfterConversions.getStreamRequest().getAdTagParameters())
        .isEqualTo(adTagParameters);
  }

  @Test
  public void vodRequestSerializationAndDeserialization() {
    DaiStreamRequest.Builder request = new DaiStreamRequest.Builder();
    request.setApiKey(API_KEY);
    request.setContentSourceId(CONTENT_SOURCE_ID);
    request.setVideoId(VIDEO_ID);
    request.setManifestSuffix(MANIFEST_SUFFIX);
    request.setContentUrl(CONTENT_URL);
    request.setAuthToken(AUTH_TOKEN);
    request.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    request.setFormat(FORMAT_DASH);
    request.setAdTagParameters(adTagParameters);

    DaiStreamRequest requestAfterConversions = DaiStreamRequest.fromUri(request.build().toUri());
    assertThat(requestAfterConversions.getStreamRequest().getApiKey()).isEqualTo(API_KEY);
    assertThat(requestAfterConversions.getStreamRequest().getContentSourceId())
        .isEqualTo(CONTENT_SOURCE_ID);
    assertThat(requestAfterConversions.getStreamRequest().getVideoId()).isEqualTo(VIDEO_ID);
    assertThat(requestAfterConversions.getStreamRequest().getManifestSuffix())
        .isEqualTo(MANIFEST_SUFFIX);
    assertThat(requestAfterConversions.getStreamRequest().getContentUrl()).isEqualTo(CONTENT_URL);
    assertThat(requestAfterConversions.getStreamRequest().getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(requestAfterConversions.getStreamRequest().getStreamActivityMonitorId())
        .isEqualTo(STREAM_ACTIVITY_MONITOR_ID);
    assertThat(requestAfterConversions.getStreamRequest().getFormat()).isEqualTo(StreamFormat.DASH);
    assertThat(requestAfterConversions.getStreamRequest().getAdTagParameters())
        .isEqualTo(adTagParameters);
  }
}
