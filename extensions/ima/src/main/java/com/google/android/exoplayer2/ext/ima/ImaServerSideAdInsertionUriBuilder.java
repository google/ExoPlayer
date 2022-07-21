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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for URI for IMA DAI streams. The resulting URI can be used to build a {@link
 * com.google.android.exoplayer2.MediaItem#fromUri(Uri) media item} that can be played by the {@link
 * ImaServerSideAdInsertionMediaSource}.
 */
public final class ImaServerSideAdInsertionUriBuilder {

  /** The default timeout for loading the video URI, in milliseconds. */
  public static final int DEFAULT_LOAD_VIDEO_TIMEOUT_MS = 10_000;

  /* package */ static final String IMA_AUTHORITY = "dai.google.com";
  private static final String ADS_ID = "adsId";
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
  private static final String LOAD_VIDEO_TIMEOUT_MS = "loadVideoTimeoutMs";

  @Nullable private String adsId;
  @Nullable private String assetKey;
  @Nullable private String apiKey;
  @Nullable private String contentSourceId;
  @Nullable private String videoId;
  @Nullable private String manifestSuffix;
  @Nullable private String contentUrl;
  @Nullable private String authToken;
  @Nullable private String streamActivityMonitorId;
  private ImmutableMap<String, String> adTagParameters;
  public @ContentType int format;
  private int loadVideoTimeoutMs;

  /** Creates a new instance. */
  public ImaServerSideAdInsertionUriBuilder() {
    adTagParameters = ImmutableMap.of();
    loadVideoTimeoutMs = DEFAULT_LOAD_VIDEO_TIMEOUT_MS;
    format = C.CONTENT_TYPE_OTHER;
  }

  /**
   * An opaque identifier for associated ad playback state, or {@code null} if the {@link
   * #setAssetKey(String) asset key} (for live) or {@link #setVideoId(String) video id} (for VOD)
   * should be used as the ads identifier.
   *
   * @param adsId The ads identifier.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setAdsId(String adsId) {
    this.adsId = adsId;
    return this;
  }

  /**
   * The stream request asset key used for live streams.
   *
   * @param assetKey Live stream asset key.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setAssetKey(@Nullable String assetKey) {
    this.assetKey = assetKey;
    return this;
  }

  /**
   * Sets the stream request authorization token. Used in place of {@link #setApiKey(String) the API
   * key} for stricter content authorization. The publisher can control individual content streams
   * authorizations based on this token.
   *
   * @param authToken Live stream authorization token.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setAuthToken(@Nullable String authToken) {
    this.authToken = authToken;
    return this;
  }

  /**
   * The stream request content source ID used for on-demand streams.
   *
   * @param contentSourceId VOD stream content source id.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setContentSourceId(@Nullable String contentSourceId) {
    this.contentSourceId = contentSourceId;
    return this;
  }

  /**
   * The stream request video ID used for on-demand streams.
   *
   * @param videoId VOD stream video id.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setVideoId(@Nullable String videoId) {
    this.videoId = videoId;
    return this;
  }

  /**
   * Sets the format of the stream request.
   *
   * @param format {@link C#TYPE_DASH} or {@link C#TYPE_HLS}.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setFormat(@ContentType int format) {
    checkArgument(format == C.CONTENT_TYPE_DASH || format == C.CONTENT_TYPE_HLS);
    this.format = format;
    return this;
  }

  /**
   * The stream request API key. This is used for content authentication. The API key is provided to
   * the publisher to unlock their content. It's a security measure used to verify the applications
   * that are attempting to access the content.
   *
   * @param apiKey Stream api key.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setApiKey(@Nullable String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  /**
   * Sets the ID to be used to debug the stream with the stream activity monitor. This is used to
   * provide a convenient way to allow publishers to find a stream log in the stream activity
   * monitor tool.
   *
   * @param streamActivityMonitorId ID for debugging the stream with the stream activity monitor.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setStreamActivityMonitorId(
      @Nullable String streamActivityMonitorId) {
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
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setAdTagParameters(
      Map<String, String> adTagParameters) {
    this.adTagParameters = ImmutableMap.copyOf(adTagParameters);
    return this;
  }

  /**
   * Sets the optional stream manifest's suffix, which will be appended to the stream manifest's
   * URL. The provided string must be URL-encoded and must not include a leading question mark.
   *
   * @param manifestSuffix Stream manifest's suffix.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setManifestSuffix(@Nullable String manifestSuffix) {
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
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setContentUrl(@Nullable String contentUrl) {
    this.contentUrl = contentUrl;
    return this;
  }

  /**
   * Sets the duration after which resolving the video URI should time out, in milliseconds.
   *
   * <p>The default is {@link #DEFAULT_LOAD_VIDEO_TIMEOUT_MS} milliseconds.
   *
   * @param loadVideoTimeoutMs The timeout after which to give up resolving the video URI.
   * @return This instance, for convenience.
   */
  @CanIgnoreReturnValue
  public ImaServerSideAdInsertionUriBuilder setLoadVideoTimeoutMs(int loadVideoTimeoutMs) {
    this.loadVideoTimeoutMs = loadVideoTimeoutMs;
    return this;
  }

  /**
   * Builds a URI with the builder's current values.
   *
   * @return The build {@link Uri}.
   * @throws IllegalStateException If the builder has missing or invalid inputs.
   */
  public Uri build() {
    checkState(
        (TextUtils.isEmpty(assetKey)
                && !TextUtils.isEmpty(contentSourceId)
                && !TextUtils.isEmpty(videoId))
            || (!TextUtils.isEmpty(assetKey)
                && TextUtils.isEmpty(contentSourceId)
                && TextUtils.isEmpty(videoId)));
    checkState(format != C.CONTENT_TYPE_OTHER);
    @Nullable String adsId = this.adsId;
    if (adsId == null) {
      adsId = assetKey != null ? assetKey : checkNotNull(videoId);
    }
    Uri.Builder dataUriBuilder = new Uri.Builder();
    dataUriBuilder.scheme(C.SSAI_SCHEME);
    dataUriBuilder.authority(IMA_AUTHORITY);
    dataUriBuilder.appendQueryParameter(ADS_ID, adsId);
    if (loadVideoTimeoutMs != DEFAULT_LOAD_VIDEO_TIMEOUT_MS) {
      dataUriBuilder.appendQueryParameter(
          LOAD_VIDEO_TIMEOUT_MS, String.valueOf(loadVideoTimeoutMs));
    }
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
    if (!adTagParameters.isEmpty()) {
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

  /** Returns whether the provided request is for a live stream or false if it is a VOD stream. */
  /* package */ static boolean isLiveStream(Uri uri) {
    return !TextUtils.isEmpty(uri.getQueryParameter(ASSET_KEY));
  }

  /** Returns the opaque adsId for this stream. */
  /* package */ static String getAdsId(Uri uri) {
    return checkNotNull(uri.getQueryParameter(ADS_ID));
  }

  /** Returns the video load timeout in milliseconds. */
  /* package */ static int getLoadVideoTimeoutMs(Uri uri) {
    @Nullable String adsLoaderTimeoutUs = uri.getQueryParameter(LOAD_VIDEO_TIMEOUT_MS);
    return TextUtils.isEmpty(adsLoaderTimeoutUs)
        ? DEFAULT_LOAD_VIDEO_TIMEOUT_MS
        : Integer.parseInt(adsLoaderTimeoutUs);
  }

  /** Returns the corresponding {@link StreamRequest}. */
  @SuppressWarnings("nullness") // Required for making nullness test pass for library_with_ima_sdk.
  /* package */ static StreamRequest createStreamRequest(Uri uri) {
    if (!C.SSAI_SCHEME.equals(uri.getScheme()) || !IMA_AUTHORITY.equals(uri.getAuthority())) {
      throw new IllegalArgumentException("Invalid URI scheme or authority.");
    }
    StreamRequest streamRequest;
    // Required params.
    @Nullable String assetKey = uri.getQueryParameter(ASSET_KEY);
    @Nullable String apiKey = uri.getQueryParameter(API_KEY);
    @Nullable String contentSourceId = uri.getQueryParameter(CONTENT_SOURCE_ID);
    @Nullable String videoId = uri.getQueryParameter(VIDEO_ID);
    if (!TextUtils.isEmpty(assetKey)) {
      streamRequest = ImaSdkFactory.getInstance().createLiveStreamRequest(assetKey, apiKey);
    } else {
      streamRequest =
          ImaSdkFactory.getInstance()
              .createVodStreamRequest(checkNotNull(contentSourceId), checkNotNull(videoId), apiKey);
    }
    int format = Integer.parseInt(uri.getQueryParameter(FORMAT));
    if (format == C.CONTENT_TYPE_DASH) {
      streamRequest.setFormat(StreamFormat.DASH);
    } else if (format == C.CONTENT_TYPE_HLS) {
      streamRequest.setFormat(StreamFormat.HLS);
    } else {
      throw new IllegalArgumentException("Unsupported stream format:" + format);
    }
    // Optional params.
    @Nullable String adTagParametersValue = uri.getQueryParameter(AD_TAG_PARAMETERS);
    if (!TextUtils.isEmpty(adTagParametersValue)) {
      Map<String, String> adTagParameters = new HashMap<>();
      Uri adTagParametersUri = Uri.parse(adTagParametersValue);
      for (String paramName : adTagParametersUri.getQueryParameterNames()) {
        String singleAdTagParameterValue = adTagParametersUri.getQueryParameter(paramName);
        if (!TextUtils.isEmpty(singleAdTagParameterValue)) {
          adTagParameters.put(paramName, singleAdTagParameterValue);
        }
      }
      streamRequest.setAdTagParameters(adTagParameters);
    }
    @Nullable String manifestSuffix = uri.getQueryParameter(MANIFEST_SUFFIX);
    if (manifestSuffix != null) {
      streamRequest.setManifestSuffix(manifestSuffix);
    }
    @Nullable String contentUrl = uri.getQueryParameter(CONTENT_URL);
    if (contentUrl != null) {
      streamRequest.setContentUrl(contentUrl);
    }
    @Nullable String authToken = uri.getQueryParameter(AUTH_TOKEN);
    if (authToken != null) {
      streamRequest.setAuthToken(authToken);
    }
    @Nullable String streamActivityMonitorId = uri.getQueryParameter(STREAM_ACTIVITY_MONITOR_ID);
    if (streamActivityMonitorId != null) {
      streamRequest.setStreamActivityMonitorId(streamActivityMonitorId);
    }
    checkState(
        streamRequest.getFormat() != StreamFormat.DASH
            || TextUtils.isEmpty(streamRequest.getAssetKey()),
        "DASH live streams are not supported yet.");
    return streamRequest;
  }
}
