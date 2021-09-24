/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import java.util.HashMap;
import java.util.Map;

// TODO(gdambrauskas): move this back under IMA DAI class.
/** Defines all of IMA DAI stream request inputs. */
/* package */ final class DaiStreamRequest {

  private static final String SCHEME = "imadai";
  private static final String ASSET_KEY = "assetKey";
  private static final String API_KEY = "apiKey";
  private static final String CONTENT_SOURCE_ID = "contentSourceId";
  private static final String VIDEO_ID = "videoId";
  private static final String AD_TAG_PARAMETERS = "adTagParameters";
  private static final String MANIFEST_SUFFIX = "manifestSuffix";
  private static final String CONTENT_URL = "contentUrl";
  private static final String AUTH_TOKEN = "authToken";
  private static final String STREAM_ACTIVITY_MONITOR_ID = "streamActivityMonitorId";
  private static final String FORMAT = "format";

  @Nullable private final String assetKey;
  @Nullable private final String apiKey;
  @Nullable private final String contentSourceId;
  @Nullable private final String videoId;
  @Nullable private final Map<String, String> adTagParameters;
  @Nullable private final String manifestSuffix;
  @Nullable private final String contentUrl;
  @Nullable private final String authToken;
  @Nullable private final String streamActivityMonitorId;
  @Nullable private StreamRequest request;
  @ContentType public int format = C.TYPE_HLS;

  private DaiStreamRequest(
      @Nullable String assetKey,
      @Nullable String apiKey,
      @Nullable String contentSourceId,
      @Nullable String videoId,
      @Nullable Map<String, String> adTagParameters,
      @Nullable String manifestSuffix,
      @Nullable String contentUrl,
      @Nullable String authToken,
      @Nullable String streamActivityMonitorId,
      @ContentType int format) {
    this.assetKey = assetKey;
    this.apiKey = apiKey;
    this.contentSourceId = contentSourceId;
    this.videoId = videoId;
    this.adTagParameters = adTagParameters;
    this.manifestSuffix = manifestSuffix;
    this.contentUrl = contentUrl;
    this.authToken = authToken;
    this.streamActivityMonitorId = streamActivityMonitorId;
    this.format = format;
  }

  @SuppressWarnings("nullness")
  public StreamRequest getStreamRequest() {
    if (request != null) {
      return request;
    }
    // We don't care if multiple threads execute the code below, but we do care that the request
    // object is fully constructed before using it.
    StreamRequest streamRequest = null;
    if (!TextUtils.isEmpty(assetKey)) {
      streamRequest = ImaSdkFactory.getInstance().createLiveStreamRequest(assetKey, apiKey);
    } else if (!TextUtils.isEmpty(contentSourceId) && !TextUtils.isEmpty(videoId)) {
      streamRequest =
          ImaSdkFactory.getInstance().createVodStreamRequest(contentSourceId, videoId, apiKey);
    }
    checkNotNull(streamRequest);
    if (format == C.TYPE_DASH) {
      streamRequest.setFormat(StreamFormat.DASH);
    } else if (format == C.TYPE_HLS) {
      streamRequest.setFormat(StreamFormat.HLS);
    }
    // Optional params.
    if (adTagParameters != null) {
      streamRequest.setAdTagParameters(adTagParameters);
    }
    if (manifestSuffix != null) {
      streamRequest.setManifestSuffix(manifestSuffix);
    }
    if (contentUrl != null) {
      streamRequest.setContentUrl(contentUrl);
    }
    if (authToken != null) {
      streamRequest.setAuthToken(authToken);
    }
    if (streamActivityMonitorId != null) {
      streamRequest.setStreamActivityMonitorId(streamActivityMonitorId);
    }
    request = streamRequest;
    return request;
  }

  /**
   * Creates a {@link DaiStreamRequest} for the given URI.
   *
   * @param uri The URI.
   * @return An {@link DaiStreamRequest} for the given URI.
   * @throws IllegalStateException If uri has missing or invalid inputs.
   */
  public static DaiStreamRequest fromUri(Uri uri) {
    DaiStreamRequest.Builder request = new DaiStreamRequest.Builder();
    if (!SCHEME.equals(uri.getScheme())) {
      throw new IllegalArgumentException("Invalid scheme.");
    }
    request.setAssetKey(uri.getQueryParameter(ASSET_KEY));
    request.setApiKey(uri.getQueryParameter(API_KEY));
    request.setContentSourceId(uri.getQueryParameter(CONTENT_SOURCE_ID));
    request.setVideoId(uri.getQueryParameter(VIDEO_ID));
    request.setManifestSuffix(uri.getQueryParameter(MANIFEST_SUFFIX));
    request.setContentUrl(uri.getQueryParameter(CONTENT_URL));
    request.setAuthToken(uri.getQueryParameter(AUTH_TOKEN));
    request.setStreamActivityMonitorId(uri.getQueryParameter(STREAM_ACTIVITY_MONITOR_ID));
    String formatValue = uri.getQueryParameter(FORMAT);
    if (!TextUtils.isEmpty(formatValue)) {
      request.setFormat(Integer.parseInt(formatValue));
    }
    Map<String, String> adTagParameters;
    String adTagParametersValue;
    String singleAdTagParameterValue;
    if (uri.getQueryParameter(AD_TAG_PARAMETERS) != null) {
      adTagParameters = new HashMap<>();
      adTagParametersValue = uri.getQueryParameter(AD_TAG_PARAMETERS);
      if (!TextUtils.isEmpty(adTagParametersValue)) {
        Uri adTagParametersUri = Uri.parse(adTagParametersValue);
        for (String paramName : adTagParametersUri.getQueryParameterNames()) {
          singleAdTagParameterValue = adTagParametersUri.getQueryParameter(paramName);
          if (!TextUtils.isEmpty(singleAdTagParameterValue)) {
            adTagParameters.put(paramName, singleAdTagParameterValue);
          }
        }
      }
      request.setAdTagParameters(adTagParameters);
    }
    return request.build();
  }

  public Uri toUri() {
    Uri.Builder dataUriBuilder = new Uri.Builder();
    dataUriBuilder.scheme(SCHEME);
    if (assetKey != null) {
      dataUriBuilder.appendQueryParameter(ASSET_KEY, assetKey);
    }
    if (apiKey != null) {
      dataUriBuilder.appendQueryParameter(API_KEY, apiKey);
    }
    if (contentSourceId != null) {
      dataUriBuilder.appendQueryParameter(CONTENT_SOURCE_ID, contentSourceId);
    }
    if (videoId != null) {
      dataUriBuilder.appendQueryParameter(VIDEO_ID, videoId);
    }
    if (manifestSuffix != null) {
      dataUriBuilder.appendQueryParameter(MANIFEST_SUFFIX, manifestSuffix);
    }
    if (contentUrl != null) {
      dataUriBuilder.appendQueryParameter(CONTENT_URL, contentUrl);
    }
    if (authToken != null) {
      dataUriBuilder.appendQueryParameter(AUTH_TOKEN, authToken);
    }
    if (streamActivityMonitorId != null) {
      dataUriBuilder.appendQueryParameter(STREAM_ACTIVITY_MONITOR_ID, streamActivityMonitorId);
    }
    if (adTagParameters != null && !adTagParameters.isEmpty()) {
      Uri.Builder adTagParametersUriBuilder = new Uri.Builder();
      for (Map.Entry<String, String> entry : adTagParameters.entrySet()) {
        adTagParametersUriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
      }
      dataUriBuilder.appendQueryParameter(
          AD_TAG_PARAMETERS, adTagParametersUriBuilder.build().toString());
    }
    dataUriBuilder.appendQueryParameter(FORMAT, String.valueOf(format));
    return dataUriBuilder.build();
  }

  public static final class Builder {
    @Nullable private String assetKey;
    @Nullable private String apiKey;
    @Nullable private String contentSourceId;
    @Nullable private String videoId;
    @Nullable private Map<String, String> adTagParameters;
    @Nullable private String manifestSuffix;
    @Nullable private String contentUrl;
    @Nullable private String authToken;
    @Nullable private String streamActivityMonitorId;
    @ContentType public int format = C.TYPE_HLS;
    /*
     * <p>/** The stream request asset key used for live streams.
     *
     * @param assetKey Live stream asset key.
     * @return This instance, for convenience.
     */
    public Builder setAssetKey(@Nullable String assetKey) {
      this.assetKey = assetKey;
      return this;
    }

    /**
     * Sets the stream request authorization token. Used in place of the API key for stricter
     * content authorization. The publisher can control individual content streams authorizations
     * based on this token.
     *
     * @param authToken Live stream authorization token.
     * @return This instance, for convenience.
     */
    public Builder setAuthToken(@Nullable String authToken) {
      this.authToken = authToken;
      return this;
    }

    /**
     * The stream request content source ID used for on-demand streams.
     *
     * @param contentSourceId VOD stream content source id.
     * @return This instance, for convenience.
     */
    public Builder setContentSourceId(@Nullable String contentSourceId) {
      this.contentSourceId = contentSourceId;
      return this;
    }

    /**
     * The stream request video ID used for on-demand streams.
     *
     * @param videoId VOD stream video id.
     * @return This instance, for convenience.
     */
    public Builder setVideoId(@Nullable String videoId) {
      this.videoId = videoId;
      return this;
    }

    /**
     * Sets the format of the stream request.
     *
     * @param format VOD or live stream type.
     * @return This instance, for convenience.
     */
    public Builder setFormat(@ContentType int format) {
      checkArgument(format == C.TYPE_DASH || format == C.TYPE_HLS);
      this.format = format;
      return this;
    }

    /**
     * The stream request API key. This is used for content authentication. The API key is provided
     * to the publisher to unlock their content. It's a security measure used to verify the
     * applications that are attempting to access the content.
     *
     * @param apiKey Stream api key.
     * @return This instance, for convenience.
     */
    public Builder setApiKey(@Nullable String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /**
     * Sets the ID to be used to debug the stream with the stream activity monitor. This is used to
     * provide a convenient way to allow publishers to find a stream log in the stream activity
     * monitor tool.
     *
     * @param streamActivityMonitorId ID for debugging the stream withstream activity monitor.
     * @return This instance, for convenience.
     */
    public Builder setStreamActivityMonitorId(@Nullable String streamActivityMonitorId) {
      this.streamActivityMonitorId = streamActivityMonitorId;
      return this;
    }

    /**
     * Sets the overridable ad tag parameters on the stream request. <a
     * href="//support.google.com/dfp_premium/answer/7320899">Supply targeting parameters to your
     * stream</a> provides more information.
     *
     * <p>You can use the dai-ot and dai-ov parameters for stream variant preference. See <a
     * href="//support.google.com/dfp_premium/answer/7320898">Override Stream Variant Parameters</a>
     * for more information.
     *
     * @param adTagParameters A map of extra parameters to pass to the ad server.
     * @return This instance, for convenience.
     */
    public Builder setAdTagParameters(@Nullable Map<String, String> adTagParameters) {
      this.adTagParameters = adTagParameters;
      return this;
    }

    /**
     * Sets the optional stream manifest's suffix, which will be appended to the stream manifest's
     * URL. The provided string must be URL-encoded and must not include a leading question mark.
     *
     * @param manifestSuffix Stream manifest's suffix.
     * @return This instance, for convenience.
     */
    public Builder setManifestSuffix(@Nullable String manifestSuffix) {
      this.manifestSuffix = manifestSuffix;
      return this;
    }

    /**
     * Specifies the deep link to the content's screen. If provided, this parameter is passed to the
     * OM SDK. See <a href="//developer.android.com/training/app-links/deep-linking">Android
     * documentation</a> for more information.
     *
     * @param contentUrl Deep link to the content's screen.
     * @return This instance, for convenience.
     */
    public Builder setContentUrl(@Nullable String contentUrl) {
      this.contentUrl = contentUrl;
      return this;
    }

    /**
     * Builds a {@link DaiStreamRequest} with the builder's current values.
     *
     * @return The build {@link DaiStreamRequest}.
     * @throws IllegalStateException If request has missing or invalid inputs.
     */
    public DaiStreamRequest build() {
      if (TextUtils.isEmpty(assetKey)
          && (TextUtils.isEmpty(contentSourceId) || TextUtils.isEmpty(videoId))) {
        throw new IllegalStateException("Missing DAI stream request parameters.");
      }
      return new DaiStreamRequest(
          assetKey,
          apiKey,
          contentSourceId,
          videoId,
          adTagParameters,
          manifestSuffix,
          contentUrl,
          authToken,
          streamActivityMonitorId,
          format);
    }
  }
}
