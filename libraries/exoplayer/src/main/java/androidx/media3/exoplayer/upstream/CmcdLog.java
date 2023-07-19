/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.upstream;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents the data for CMCD (Common Media Client Data) in adaptive streaming formats DASH, HLS,
 * and SmoothStreaming.
 *
 * <p>It holds various attributes related to the playback of media content according to the
 * specifications outlined in the CMCD standard document <a
 * href="https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf">CTA-5004</a>.
 */
@UnstableApi
public final class CmcdLog {

  /** Indicates the streaming format used for media content. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({STREAMING_FORMAT_DASH, STREAMING_FORMAT_HLS, STREAMING_FORMAT_SS})
  @Documented
  @Target(TYPE_USE)
  public @interface StreamingFormat {}

  /** Indicates the type of streaming for media content. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({STREAM_TYPE_VOD, STREAM_TYPE_LIVE})
  @Documented
  @Target(TYPE_USE)
  public @interface StreamType {}

  /** Represents the Dynamic Adaptive Streaming over HTTP (DASH) format. */
  public static final String STREAMING_FORMAT_DASH = "d";

  /** Represents the HTTP Live Streaming (HLS) format. */
  public static final String STREAMING_FORMAT_HLS = "h";

  /** Represents the Smooth Streaming (SS) format. */
  public static final String STREAMING_FORMAT_SS = "s";

  /** Represents the Video on Demand (VOD) stream type. */
  public static final String STREAM_TYPE_VOD = "v";

  /** Represents the Live Streaming stream type. */
  public static final String STREAM_TYPE_LIVE = "l";

  /**
   * Creates a new instance.
   *
   * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
   * @param trackSelection The {@linkplain ExoTrackSelection track selection}.
   * @param bufferedDurationUs The duration of media currently buffered from the current playback
   *     position, in microseconds.
   * @param chunkDurationUs The duration of current media chunk being requested, in microseconds. If
   *     the duration is not known, it can be set to {@link C#TIME_UNSET}.
   * @param streamingFormat The streaming format of the media content. Must be one of the allowed
   *     streaming formats specified by the {@link StreamingFormat} annotation.
   * @param isLive {@code true} if the media content is being streamed live, {@code false}
   *     otherwise.
   */
  public static CmcdLog createInstance(
      CmcdConfiguration cmcdConfiguration,
      ExoTrackSelection trackSelection,
      long bufferedDurationUs,
      long chunkDurationUs,
      @StreamingFormat String streamingFormat,
      boolean isLive) {
    ImmutableMap<@CmcdConfiguration.HeaderKey String, String> customData =
        cmcdConfiguration.requestConfig.getCustomData();
    int bitrateKbps = trackSelection.getSelectedFormat().bitrate / 1000;

    CmcdLog.CmcdObject.Builder cmcdObject =
        new CmcdLog.CmcdObject.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_OBJECT));
    if (cmcdConfiguration.isBitrateLoggingAllowed()) {
      cmcdObject.setBitrateKbps(bitrateKbps);
    }
    if (cmcdConfiguration.isTopBitrateLoggingAllowed()) {
      TrackGroup trackGroup = trackSelection.getTrackGroup();
      int topBitrate = trackSelection.getSelectedFormat().bitrate;
      for (int i = 0; i < trackGroup.length; i++) {
        topBitrate = max(topBitrate, trackGroup.getFormat(i).bitrate);
      }
      cmcdObject.setTopBitrateKbps(topBitrate / 1000);
    }
    if (cmcdConfiguration.isObjectDurationLoggingAllowed() && chunkDurationUs != C.TIME_UNSET) {
      cmcdObject.setObjectDurationMs(chunkDurationUs / 1000);
    }

    CmcdLog.CmcdRequest.Builder cmcdRequest =
        new CmcdLog.CmcdRequest.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_REQUEST));
    if (cmcdConfiguration.isBufferLengthLoggingAllowed()) {
      cmcdRequest.setBufferLengthMs(bufferedDurationUs / 1000);
    }
    if (cmcdConfiguration.isMeasuredThroughputLoggingAllowed()
        && trackSelection.getLatestBitrateEstimate() != Long.MIN_VALUE) {
      cmcdRequest.setMeasuredThroughputInKbps(trackSelection.getLatestBitrateEstimate() / 1000);
    }

    CmcdLog.CmcdSession.Builder cmcdSession =
        new CmcdLog.CmcdSession.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_SESSION));
    if (cmcdConfiguration.isContentIdLoggingAllowed()) {
      cmcdSession.setContentId(cmcdConfiguration.contentId);
    }
    if (cmcdConfiguration.isSessionIdLoggingAllowed()) {
      cmcdSession.setSessionId(cmcdConfiguration.sessionId);
    }
    if (cmcdConfiguration.isStreamingFormatLoggingAllowed()) {
      cmcdSession.setStreamingFormat(streamingFormat);
    }
    if (cmcdConfiguration.isStreamTypeLoggingAllowed()) {
      cmcdSession.setStreamType(isLive ? STREAM_TYPE_LIVE : STREAM_TYPE_VOD);
    }

    CmcdLog.CmcdStatus.Builder cmcdStatus =
        new CmcdLog.CmcdStatus.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_STATUS));
    if (cmcdConfiguration.isMaximumRequestThroughputLoggingAllowed()) {
      cmcdStatus.setMaximumRequestedThroughputKbps(
          cmcdConfiguration.requestConfig.getRequestedMaximumThroughputKbps(bitrateKbps));
    }

    return new CmcdLog(
        cmcdObject.build(), cmcdRequest.build(), cmcdSession.build(), cmcdStatus.build());
  }

  private final CmcdObject cmcdObject;
  private final CmcdRequest cmcdRequest;
  private final CmcdSession cmcdSession;
  private final CmcdStatus cmcdStatus;

  private CmcdLog(
      CmcdObject cmcdObject,
      CmcdRequest cmcdRequest,
      CmcdSession cmcdSession,
      CmcdStatus cmcdStatus) {
    this.cmcdObject = cmcdObject;
    this.cmcdRequest = cmcdRequest;
    this.cmcdSession = cmcdSession;
    this.cmcdStatus = cmcdStatus;
  }

  public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> getHttpRequestHeaders() {
    ImmutableMap.Builder<String, String> httpRequestHeaders = ImmutableMap.builder();
    this.cmcdObject.populateHttpRequestHeaders(httpRequestHeaders);
    this.cmcdRequest.populateHttpRequestHeaders(httpRequestHeaders);
    this.cmcdSession.populateHttpRequestHeaders(httpRequestHeaders);
    this.cmcdStatus.populateHttpRequestHeaders(httpRequestHeaders);
    return httpRequestHeaders.buildOrThrow();
  }

  /** Keys whose values vary with the object being requested. Contains CMCD fields: {@code br}. */
  private static final class CmcdObject {

    /** Builder for {@link CmcdObject} instances. */
    public static final class Builder {
      private int bitrateKbps;
      private int topBitrateKbps;
      private long objectDurationMs;
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bitrateKbps = C.RATE_UNSET_INT;
        this.topBitrateKbps = C.RATE_UNSET_INT;
        this.objectDurationMs = C.TIME_UNSET;
      }

      /** Sets the {@link CmcdObject#bitrateKbps}. The default value is {@link C#RATE_UNSET_INT}. */
      @CanIgnoreReturnValue
      public Builder setBitrateKbps(int bitrateKbps) {
        this.bitrateKbps = bitrateKbps;
        return this;
      }

      /**
       * Sets the {@link CmcdObject#topBitrateKbps}. The default value is {@link C#RATE_UNSET_INT}.
       */
      @CanIgnoreReturnValue
      public Builder setTopBitrateKbps(int topBitrateKbps) {
        this.topBitrateKbps = topBitrateKbps;
        return this;
      }

      /**
       * Sets the {@link CmcdObject#objectDurationMs}. The default value is {@link C#TIME_UNSET}.
       *
       * @throws IllegalArgumentException If {@code objectDurationMs} is negative.
       */
      @CanIgnoreReturnValue
      public Builder setObjectDurationMs(long objectDurationMs) {
        checkArgument(objectDurationMs >= 0);
        this.objectDurationMs = objectDurationMs;
        return this;
      }

      /** Sets the {@link CmcdObject#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setCustomData(@Nullable String customData) {
        this.customData = customData;
        return this;
      }

      public CmcdObject build() {
        return new CmcdObject(this);
      }
    }

    /**
     * The encoded bitrate in kbps of the audio or video object being requested, or {@link
     * C#RATE_UNSET_INT} if unset.
     *
     * <p>This may not be known precisely by the player; however, it MAY be estimated based upon
     * playlist/manifest declarations. If the playlist declares both peak and average bitrate
     * values, the peak value should be transmitted.
     */
    public final int bitrateKbps;

    /**
     * The highest bitrate rendition, in kbps, in the manifest or playlist that the client is
     * allowed to play, given current codec, licensing and sizing constraints. If unset, it is
     * represented by the value {@link C#RATE_UNSET_INT}.
     */
    public final int topBitrateKbps;

    /**
     * The playback duration in milliseconds of the object being requested, or {@link C#TIME_UNSET}
     * if unset. If a partial segment is being requested, then this value MUST indicate the playback
     * duration of that part and not that of its parent segment. This value can be an approximation
     * of the estimated duration if the explicit value is not known.
     */
    public final long objectDurationMs;

    /**
     * Custom data where the values of the keys vary with the object being requested, or {@code
     * null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue,key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdObject(Builder builder) {
      this.bitrateKbps = builder.bitrateKbps;
      this.topBitrateKbps = builder.topBitrateKbps;
      this.objectDurationMs = builder.objectDurationMs;
      this.customData = builder.customData;
    }

    /**
     * Populates the HTTP request headers with {@link CmcdConfiguration#KEY_CMCD_OBJECT} values.
     *
     * @param httpRequestHeaders An {@link ImmutableMap.Builder} used to build the HTTP request
     *     headers.
     */
    public void populateHttpRequestHeaders(
        ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String> httpRequestHeaders) {
      StringBuilder headerValue = new StringBuilder();
      if (bitrateKbps != C.RATE_UNSET_INT) {
        headerValue.append(
            Util.formatInvariant("%s=%d,", CmcdConfiguration.KEY_BITRATE, bitrateKbps));
      }
      if (topBitrateKbps != C.RATE_UNSET_INT) {
        headerValue.append(
            Util.formatInvariant("%s=%d,", CmcdConfiguration.KEY_TOP_BITRATE, topBitrateKbps));
      }
      if (objectDurationMs != C.TIME_UNSET) {
        headerValue.append(
            Util.formatInvariant(
                "%s=%d,", CmcdConfiguration.KEY_OBJECT_DURATION, objectDurationMs));
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValue.append(Util.formatInvariant("%s,", customData));
      }

      if (headerValue.length() == 0) {
        return;
      }
      // Remove the trailing comma as headerValue is not empty
      headerValue.setLength(headerValue.length() - 1);
      httpRequestHeaders.put(CmcdConfiguration.KEY_CMCD_OBJECT, headerValue.toString());
    }
  }

  /** Keys whose values vary with each request. Contains CMCD fields: {@code bl}. */
  private static final class CmcdRequest {

    /** Builder for {@link CmcdRequest} instances. */
    public static final class Builder {
      private long bufferLengthMs;
      private long measuredThroughputInKbps;
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bufferLengthMs = C.TIME_UNSET;
        this.measuredThroughputInKbps = Long.MIN_VALUE;
      }

      /**
       * Sets the {@link CmcdRequest#bufferLengthMs}. Rounded to nearest 100 ms. The default value
       * is {@link C#TIME_UNSET}.
       *
       * @throws IllegalArgumentException If {@code bufferLengthMs} is negative.
       */
      @CanIgnoreReturnValue
      public Builder setBufferLengthMs(long bufferLengthMs) {
        checkArgument(bufferLengthMs >= 0);
        this.bufferLengthMs = ((bufferLengthMs + 50) / 100) * 100;
        return this;
      }

      /**
       * Sets the {@link CmcdRequest#measuredThroughputInKbps}. Rounded to nearest 100 kbps. The
       * default value is {@link Long#MIN_VALUE}.
       *
       * @throws IllegalArgumentException If {@code measuredThroughputInKbps} is negative.
       */
      @CanIgnoreReturnValue
      public Builder setMeasuredThroughputInKbps(long measuredThroughputInKbps) {
        checkArgument(measuredThroughputInKbps >= 0);
        this.measuredThroughputInKbps = ((measuredThroughputInKbps + 50) / 100) * 100;

        return this;
      }

      /** Sets the {@link CmcdRequest#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setCustomData(@Nullable String customData) {
        this.customData = customData;
        return this;
      }

      public CmcdRequest build() {
        return new CmcdRequest(this);
      }
    }

    /**
     * The buffer length in milliseconds associated with the media object being requested, or {@link
     * C#TIME_UNSET} if unset.
     *
     * <p>This value MUST be rounded to the nearest 100 ms.
     */
    public final long bufferLengthMs;

    /**
     * The throughput between client and server, as measured by the client, or {@link
     * Long#MIN_VALUE} if unset.
     *
     * <p>This value MUST be rounded to the nearest 100 kbps. This value, however derived, SHOULD be
     * the value that the client is using to make its next Adaptive Bitrate switching decision. If
     * the client is connected to multiple servers concurrently, it must take care to report only
     * the throughput measured against the receiving server. If the client has multiple concurrent
     * connections to the server, then the intent is that this value communicates the aggregate
     * throughput the client sees across all those connections.
     */
    public final long measuredThroughputInKbps;

    /**
     * Custom data where the values of the keys vary with each request, or {@code null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue, key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdRequest(Builder builder) {
      this.bufferLengthMs = builder.bufferLengthMs;
      this.measuredThroughputInKbps = builder.measuredThroughputInKbps;
      this.customData = builder.customData;
    }

    /**
     * Populates the HTTP request headers with {@link CmcdConfiguration#KEY_CMCD_REQUEST} values.
     *
     * @param httpRequestHeaders An {@link ImmutableMap.Builder} used to build the HTTP request
     *     headers.
     */
    public void populateHttpRequestHeaders(
        ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String> httpRequestHeaders) {
      StringBuilder headerValue = new StringBuilder();
      if (bufferLengthMs != C.TIME_UNSET) {
        headerValue.append(
            Util.formatInvariant("%s=%d,", CmcdConfiguration.KEY_BUFFER_LENGTH, bufferLengthMs));
      }
      if (measuredThroughputInKbps != Long.MIN_VALUE) {
        headerValue.append(
            Util.formatInvariant(
                "%s=%d,", CmcdConfiguration.KEY_MEASURED_THROUGHPUT, measuredThroughputInKbps));
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValue.append(Util.formatInvariant("%s,", customData));
      }

      if (headerValue.length() == 0) {
        return;
      }
      // Remove the trailing comma as headerValue is not empty
      headerValue.setLength(headerValue.length() - 1);
      httpRequestHeaders.put(CmcdConfiguration.KEY_CMCD_REQUEST, headerValue.toString());
    }
  }

  /**
   * Keys whose values are expected to be invariant over the life of the session. Contains CMCD
   * fields: {@code cid} and {@code sid}.
   */
  private static final class CmcdSession {

    /** Builder for {@link CmcdSession} instances. */
    public static final class Builder {
      @Nullable private String contentId;
      @Nullable private String sessionId;
      @Nullable private String streamingFormat;
      @Nullable private String streamType;
      @Nullable private String customData;

      /**
       * Sets the {@link CmcdSession#contentId}. Maximum length allowed is 64 characters. The
       * default value is {@code null}.
       *
       * @throws IllegalArgumentException If {@code contentId} is null or its length exceeds {@link
       *     CmcdConfiguration#MAX_ID_LENGTH}.
       */
      @CanIgnoreReturnValue
      public Builder setContentId(@Nullable String contentId) {
        checkArgument(contentId == null || contentId.length() <= CmcdConfiguration.MAX_ID_LENGTH);
        this.contentId = contentId;
        return this;
      }

      /**
       * Sets the {@link CmcdSession#sessionId}. Maximum length allowed is 64 characters. The
       * default value is {@code null}.
       *
       * @throws IllegalArgumentException If {@code sessionId} is null or its length exceeds {@link
       *     CmcdConfiguration#MAX_ID_LENGTH}.
       */
      @CanIgnoreReturnValue
      public Builder setSessionId(@Nullable String sessionId) {
        checkArgument(sessionId == null || sessionId.length() <= CmcdConfiguration.MAX_ID_LENGTH);
        this.sessionId = sessionId;
        return this;
      }

      /** Sets the {@link CmcdSession#streamingFormat}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setStreamingFormat(@Nullable @StreamingFormat String streamingFormat) {
        this.streamingFormat = streamingFormat;
        return this;
      }

      /** Sets the {@link CmcdSession#streamType}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setStreamType(@Nullable @StreamType String streamType) {
        this.streamType = streamType;
        return this;
      }

      /** Sets the {@link CmcdSession#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public CmcdSession.Builder setCustomData(@Nullable String customData) {
        this.customData = customData;
        return this;
      }

      public CmcdSession build() {
        return new CmcdSession(this);
      }
    }

    /**
     * The version of this specification used for interpreting the defined key names and values. If
     * this key is omitted, the client and server MUST interpret the values as being defined by
     * version 1. Client SHOULD omit this field if the version is 1.
     */
    public static final int VERSION = 1;

    /**
     * A GUID identifying the current content, or {@code null} if unset.
     *
     * <p>This value is consistent across multiple different sessions and devices and is defined and
     * updated at the discretion of the service provider. Maximum length is 64 characters.
     */
    @Nullable public final String contentId;

    /**
     * A GUID identifying the current playback session, or {@code null} if unset.
     *
     * <p>A playback session typically ties together segments belonging to a single media asset.
     * Maximum length is 64 characters.
     */
    @Nullable public final String sessionId;

    /**
     * The streaming format that defines the current request. d = MPEG DASH, h = HTTP Live Streaming
     * (HLS), s = Smooth Streaming and o = other. If the streaming format being requested is
     * unknown, then this key MUST NOT be used.
     */
    @Nullable public final String streamingFormat;

    /**
     * Type of stream. v = all segments are available – e.g., VOD and l = segments become available
     * over time – e.g., LIVE.
     */
    @Nullable public final String streamType;

    /**
     * Custom data where the values of the keys are expected to be invariant over the life of the
     * session, or {@code null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue, key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdSession(Builder builder) {
      this.contentId = builder.contentId;
      this.sessionId = builder.sessionId;
      this.streamingFormat = builder.streamingFormat;
      this.streamType = builder.streamType;
      this.customData = builder.customData;
    }

    /**
     * Populates the HTTP request headers with {@link CmcdConfiguration#KEY_CMCD_SESSION} values.
     *
     * @param httpRequestHeaders An {@link ImmutableMap.Builder} used to build the HTTP request
     *     headers.
     */
    public void populateHttpRequestHeaders(
        ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String> httpRequestHeaders) {
      StringBuilder headerValue = new StringBuilder();
      if (!TextUtils.isEmpty(this.contentId)) {
        headerValue.append(
            Util.formatInvariant("%s=\"%s\",", CmcdConfiguration.KEY_CONTENT_ID, contentId));
      }
      if (!TextUtils.isEmpty(this.sessionId)) {
        headerValue.append(
            Util.formatInvariant("%s=\"%s\",", CmcdConfiguration.KEY_SESSION_ID, sessionId));
      }
      if (!TextUtils.isEmpty(this.streamingFormat)) {
        headerValue.append(
            Util.formatInvariant(
                "%s=%s,", CmcdConfiguration.KEY_STREAMING_FORMAT, streamingFormat));
      }
      if (!TextUtils.isEmpty(this.streamType)) {
        headerValue.append(
            Util.formatInvariant("%s=%s,", CmcdConfiguration.KEY_STREAM_TYPE, streamType));
      }
      if (VERSION != 1) {
        headerValue.append(Util.formatInvariant("%s=%d,", CmcdConfiguration.KEY_VERSION, VERSION));
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValue.append(Util.formatInvariant("%s,", customData));
      }

      if (headerValue.length() == 0) {
        return;
      }
      // Remove the trailing comma as headerValue is not empty
      headerValue.setLength(headerValue.length() - 1);
      httpRequestHeaders.put(CmcdConfiguration.KEY_CMCD_SESSION, headerValue.toString());
    }
  }

  /**
   * Keys whose values do not vary with every request or object. Contains CMCD fields: {@code rtp}.
   */
  private static final class CmcdStatus {

    /** Builder for {@link CmcdStatus} instances. */
    public static final class Builder {
      private int maximumRequestedThroughputKbps;
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.maximumRequestedThroughputKbps = C.RATE_UNSET_INT;
      }

      /**
       * Sets the {@link CmcdStatus#maximumRequestedThroughputKbps}. Rounded to nearest 100 kbps.
       * The default value is {@link C#RATE_UNSET_INT}.
       *
       * @throws IllegalArgumentException If {@code maximumRequestedThroughputKbps} is not equal to
       *     {@link C#RATE_UNSET_INT} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setMaximumRequestedThroughputKbps(int maximumRequestedThroughputKbps) {
        checkArgument(
            maximumRequestedThroughputKbps == C.RATE_UNSET_INT
                || maximumRequestedThroughputKbps >= 0);

        this.maximumRequestedThroughputKbps =
            maximumRequestedThroughputKbps == C.RATE_UNSET_INT
                ? maximumRequestedThroughputKbps
                : ((maximumRequestedThroughputKbps + 50) / 100) * 100;

        return this;
      }

      /** Sets the {@link CmcdStatus#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public CmcdStatus.Builder setCustomData(@Nullable String customData) {
        this.customData = customData;
        return this;
      }

      public CmcdStatus build() {
        return new CmcdStatus(this);
      }
    }

    /**
     * The requested maximum throughput in kbps that the client considers sufficient for delivery of
     * the asset, or {@link C#RATE_UNSET_INT} if unset. Values MUST be rounded to the nearest
     * 100kbps.
     */
    public final int maximumRequestedThroughputKbps;

    /**
     * Custom data where the values of the keys do not vary with every request or object, or {@code
     * null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue, key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdStatus(Builder builder) {
      this.maximumRequestedThroughputKbps = builder.maximumRequestedThroughputKbps;
      this.customData = builder.customData;
    }

    /**
     * Populates the HTTP request headers with {@link CmcdConfiguration#KEY_CMCD_STATUS} values.
     *
     * @param httpRequestHeaders An {@link ImmutableMap.Builder} used to build the HTTP request
     *     headers.
     */
    public void populateHttpRequestHeaders(
        ImmutableMap.Builder<@CmcdConfiguration.HeaderKey String, String> httpRequestHeaders) {
      StringBuilder headerValue = new StringBuilder();
      if (maximumRequestedThroughputKbps != C.RATE_UNSET_INT) {
        headerValue.append(
            Util.formatInvariant(
                "%s=%d,",
                CmcdConfiguration.KEY_MAXIMUM_REQUESTED_BITRATE, maximumRequestedThroughputKbps));
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValue.append(Util.formatInvariant("%s,", customData));
      }

      if (headerValue.length() == 0) {
        return;
      }
      // Remove the trailing comma as headerValue is not empty
      headerValue.setLength(headerValue.length() - 1);
      httpRequestHeaders.put(CmcdConfiguration.KEY_CMCD_STATUS, headerValue.toString());
    }
  }
}
