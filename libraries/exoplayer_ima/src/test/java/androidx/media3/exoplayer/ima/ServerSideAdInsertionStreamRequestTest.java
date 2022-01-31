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
package androidx.media3.exoplayer.ima;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ServerSideAdInsertionStreamRequest}. */
@RunWith(AndroidJUnit4.class)
public final class ServerSideAdInsertionStreamRequestTest {

  private static final String ADS_ID = "testAdsId";
  private static final String ASSET_KEY = "testAssetKey";
  private static final String API_KEY = "testApiKey";
  private static final String CONTENT_SOURCE_ID = "testContentSourceId";
  private static final String VIDEO_ID = "testVideoId";
  private static final String MANIFEST_SUFFIX = "testManifestSuffix";
  private static final String CONTENT_URL =
      "http://google.com/contentUrl?queryParamName=queryParamValue";
  private static final String AUTH_TOKEN = "testAuthToken";
  private static final String STREAM_ACTIVITY_MONITOR_ID = "testStreamActivityMonitorId";
  private static final int ADS_LOADER_TIMEOUT_MS = 2;
  private static final int FORMAT_DASH = 0;
  private static final int FORMAT_HLS = 2;
  private static final Map<String, String> adTagParameters = new HashMap<>();

  static {
    adTagParameters.put("param1", "value1");
    adTagParameters.put("param2", "value2");
  }

  @Test
  public void build_live_correctUriAndParsing() {
    ServerSideAdInsertionStreamRequest.Builder builder =
        new ServerSideAdInsertionStreamRequest.Builder();
    builder.setAdsId(ADS_ID);
    builder.setAssetKey(ASSET_KEY);
    builder.setApiKey(API_KEY);
    builder.setManifestSuffix(MANIFEST_SUFFIX);
    builder.setContentUrl(CONTENT_URL);
    builder.setAuthToken(AUTH_TOKEN);
    builder.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    builder.setFormat(FORMAT_HLS);
    builder.setAdTagParameters(adTagParameters);
    builder.setLoadVideoTimeoutMs(ADS_LOADER_TIMEOUT_MS);
    ServerSideAdInsertionStreamRequest streamRequest = builder.build();

    ServerSideAdInsertionStreamRequest requestAfterConversions =
        ServerSideAdInsertionStreamRequest.fromUri(streamRequest.toUri());

    assertThat(streamRequest).isEqualTo(requestAfterConversions);
  }

  @Test
  public void build_vod_correctUriAndParsing() {
    ServerSideAdInsertionStreamRequest.Builder builder =
        new ServerSideAdInsertionStreamRequest.Builder();
    builder.setAdsId(ADS_ID);
    builder.setApiKey(API_KEY);
    builder.setContentSourceId(CONTENT_SOURCE_ID);
    builder.setVideoId(VIDEO_ID);
    builder.setManifestSuffix(MANIFEST_SUFFIX);
    builder.setContentUrl(CONTENT_URL);
    builder.setAuthToken(AUTH_TOKEN);
    builder.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    builder.setFormat(FORMAT_DASH);
    builder.setAdTagParameters(adTagParameters);
    builder.setLoadVideoTimeoutMs(ADS_LOADER_TIMEOUT_MS);
    ServerSideAdInsertionStreamRequest streamRequest = builder.build();

    ServerSideAdInsertionStreamRequest requestAfterConversions =
        ServerSideAdInsertionStreamRequest.fromUri(streamRequest.toUri());

    assertThat(requestAfterConversions).isEqualTo(streamRequest);
  }

  @Test
  public void build_vodWithNoAdsId_usesVideoIdAsDefault() {
    ServerSideAdInsertionStreamRequest.Builder builder =
        new ServerSideAdInsertionStreamRequest.Builder();
    builder.setContentSourceId(CONTENT_SOURCE_ID);
    builder.setVideoId(VIDEO_ID);

    ServerSideAdInsertionStreamRequest streamRequest = builder.build();

    assertThat(streamRequest.adsId).isEqualTo(VIDEO_ID);
    assertThat(streamRequest.toUri().getQueryParameter("adsId")).isEqualTo(VIDEO_ID);
  }

  @Test
  public void build_liveWithNoAdsId_usesAssetKeyAsDefault() {
    ServerSideAdInsertionStreamRequest.Builder builder =
        new ServerSideAdInsertionStreamRequest.Builder();
    builder.setAssetKey(ASSET_KEY);

    ServerSideAdInsertionStreamRequest streamRequest = builder.build();

    assertThat(streamRequest.adsId).isEqualTo(ASSET_KEY);
    assertThat(streamRequest.toUri().getQueryParameter("adsId")).isEqualTo(ASSET_KEY);
  }

  @Test
  public void build_assetKeyWithVideoId_throwsIllegalStateException() {
    ServerSideAdInsertionStreamRequest.Builder requestBuilder =
        new ServerSideAdInsertionStreamRequest.Builder();
    requestBuilder.setAssetKey(ASSET_KEY);
    requestBuilder.setVideoId(VIDEO_ID);

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }

  @Test
  public void build_assetKeyWithContentSource_throwsIllegalStateException() {
    ServerSideAdInsertionStreamRequest.Builder requestBuilder =
        new ServerSideAdInsertionStreamRequest.Builder();
    requestBuilder.setAssetKey(ASSET_KEY);
    requestBuilder.setContentSourceId(CONTENT_SOURCE_ID);

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }

  @Test
  public void build_withoutContentSourceAndVideoIdOrAssetKey_throwsIllegalStateException() {
    ServerSideAdInsertionStreamRequest.Builder requestBuilder =
        new ServerSideAdInsertionStreamRequest.Builder();

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }
}
