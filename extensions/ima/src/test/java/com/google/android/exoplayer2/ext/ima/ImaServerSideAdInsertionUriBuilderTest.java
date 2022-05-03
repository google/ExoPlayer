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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.android.exoplayer2.C;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ImaServerSideAdInsertionUriBuilder}. */
@RunWith(AndroidJUnit4.class)
public final class ImaServerSideAdInsertionUriBuilderTest {

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
  private static final Map<String, String> adTagParameters = new HashMap<>();

  static {
    adTagParameters.put("param1", "value1");
    adTagParameters.put("param2", "value2");
  }

  @Test
  public void build_live_correctUriParsing() {
    ImaServerSideAdInsertionUriBuilder builder = new ImaServerSideAdInsertionUriBuilder();
    builder.setAdsId(ADS_ID);
    builder.setAssetKey(ASSET_KEY);
    builder.setApiKey(API_KEY);
    builder.setManifestSuffix(MANIFEST_SUFFIX);
    builder.setContentUrl(CONTENT_URL);
    builder.setAuthToken(AUTH_TOKEN);
    builder.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    builder.setFormat(C.CONTENT_TYPE_HLS);
    builder.setAdTagParameters(adTagParameters);
    builder.setLoadVideoTimeoutMs(ADS_LOADER_TIMEOUT_MS);
    Uri uri = builder.build();

    StreamRequest streamRequest = ImaServerSideAdInsertionUriBuilder.createStreamRequest(uri);
    assertThat(streamRequest.getAssetKey()).isEqualTo(ASSET_KEY);
    assertThat(streamRequest.getApiKey()).isEqualTo(API_KEY);
    assertThat(streamRequest.getManifestSuffix()).isEqualTo(MANIFEST_SUFFIX);
    assertThat(streamRequest.getContentUrl()).isEqualTo(CONTENT_URL);
    assertThat(streamRequest.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(streamRequest.getStreamActivityMonitorId()).isEqualTo(STREAM_ACTIVITY_MONITOR_ID);
    assertThat(streamRequest.getFormat()).isEqualTo(StreamFormat.HLS);
    assertThat(streamRequest.getAdTagParameters()).isEqualTo(adTagParameters);

    boolean isLive = ImaServerSideAdInsertionUriBuilder.isLiveStream(uri);
    assertThat(isLive).isTrue();

    String adsId = ImaServerSideAdInsertionUriBuilder.getAdsId(uri);
    assertThat(adsId).isEqualTo(ADS_ID);

    int loadVideoTimeoutMs = ImaServerSideAdInsertionUriBuilder.getLoadVideoTimeoutMs(uri);
    assertThat(loadVideoTimeoutMs).isEqualTo(ADS_LOADER_TIMEOUT_MS);
  }

  @Test
  public void build_vod_correctUriParsing() {
    ImaServerSideAdInsertionUriBuilder builder = new ImaServerSideAdInsertionUriBuilder();
    builder.setAdsId(ADS_ID);
    builder.setApiKey(API_KEY);
    builder.setContentSourceId(CONTENT_SOURCE_ID);
    builder.setVideoId(VIDEO_ID);
    builder.setManifestSuffix(MANIFEST_SUFFIX);
    builder.setContentUrl(CONTENT_URL);
    builder.setAuthToken(AUTH_TOKEN);
    builder.setStreamActivityMonitorId(STREAM_ACTIVITY_MONITOR_ID);
    builder.setFormat(C.CONTENT_TYPE_DASH);
    builder.setAdTagParameters(adTagParameters);
    builder.setLoadVideoTimeoutMs(ADS_LOADER_TIMEOUT_MS);
    Uri uri = builder.build();

    StreamRequest streamRequest = ImaServerSideAdInsertionUriBuilder.createStreamRequest(uri);
    assertThat(streamRequest.getApiKey()).isEqualTo(API_KEY);
    assertThat(streamRequest.getContentSourceId()).isEqualTo(CONTENT_SOURCE_ID);
    assertThat(streamRequest.getVideoId()).isEqualTo(VIDEO_ID);
    assertThat(streamRequest.getManifestSuffix()).isEqualTo(MANIFEST_SUFFIX);
    assertThat(streamRequest.getContentUrl()).isEqualTo(CONTENT_URL);
    assertThat(streamRequest.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(streamRequest.getStreamActivityMonitorId()).isEqualTo(STREAM_ACTIVITY_MONITOR_ID);
    assertThat(streamRequest.getFormat()).isEqualTo(StreamFormat.DASH);
    assertThat(streamRequest.getAdTagParameters()).isEqualTo(adTagParameters);

    boolean isLive = ImaServerSideAdInsertionUriBuilder.isLiveStream(uri);
    assertThat(isLive).isFalse();

    String adsId = ImaServerSideAdInsertionUriBuilder.getAdsId(uri);
    assertThat(adsId).isEqualTo(ADS_ID);

    int loadVideoTimeoutMs = ImaServerSideAdInsertionUriBuilder.getLoadVideoTimeoutMs(uri);
    assertThat(loadVideoTimeoutMs).isEqualTo(ADS_LOADER_TIMEOUT_MS);
  }

  @Test
  public void build_vodWithNoAdsId_usesVideoIdAsDefault() {
    ImaServerSideAdInsertionUriBuilder builder = new ImaServerSideAdInsertionUriBuilder();
    builder.setContentSourceId(CONTENT_SOURCE_ID);
    builder.setVideoId(VIDEO_ID);
    builder.setFormat(C.CONTENT_TYPE_DASH);

    Uri streamRequest = builder.build();

    assertThat(ImaServerSideAdInsertionUriBuilder.getAdsId(streamRequest)).isEqualTo(VIDEO_ID);
    assertThat(streamRequest.getQueryParameter("adsId")).isEqualTo(VIDEO_ID);
  }

  @Test
  public void build_liveWithNoAdsId_usesAssetKeyAsDefault() {
    ImaServerSideAdInsertionUriBuilder builder = new ImaServerSideAdInsertionUriBuilder();
    builder.setAssetKey(ASSET_KEY);
    builder.setFormat(C.CONTENT_TYPE_DASH);

    Uri streamRequest = builder.build();

    assertThat(ImaServerSideAdInsertionUriBuilder.getAdsId(streamRequest)).isEqualTo(ASSET_KEY);
    assertThat(streamRequest.getQueryParameter("adsId")).isEqualTo(ASSET_KEY);
  }

  @Test
  public void build_assetKeyWithVideoId_throwsIllegalStateException() {
    ImaServerSideAdInsertionUriBuilder requestBuilder = new ImaServerSideAdInsertionUriBuilder();
    requestBuilder.setAssetKey(ASSET_KEY);
    requestBuilder.setVideoId(VIDEO_ID);

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }

  @Test
  public void build_assetKeyWithContentSource_throwsIllegalStateException() {
    ImaServerSideAdInsertionUriBuilder requestBuilder = new ImaServerSideAdInsertionUriBuilder();
    requestBuilder.setAssetKey(ASSET_KEY);
    requestBuilder.setContentSourceId(CONTENT_SOURCE_ID);

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }

  @Test
  public void build_withoutContentSourceAndVideoIdOrAssetKey_throwsIllegalStateException() {
    ImaServerSideAdInsertionUriBuilder requestBuilder = new ImaServerSideAdInsertionUriBuilder();

    Assert.assertThrows(IllegalStateException.class, requestBuilder::build);
  }

  @Test
  public void build_withoutLoadVideoTimeoutMs_usesDefaultTimeout() {
    Uri uri =
        new ImaServerSideAdInsertionUriBuilder()
            .setAssetKey(ASSET_KEY)
            .setFormat(C.CONTENT_TYPE_DASH)
            .build();

    int loadVideoTimeoutMs = ImaServerSideAdInsertionUriBuilder.getLoadVideoTimeoutMs(uri);
    assertThat(loadVideoTimeoutMs)
        .isEqualTo(ImaServerSideAdInsertionUriBuilder.DEFAULT_LOAD_VIDEO_TIMEOUT_MS);
  }
}
