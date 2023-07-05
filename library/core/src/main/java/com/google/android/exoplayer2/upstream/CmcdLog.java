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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Represents the data for CMCD (Common Media Client Data) in adaptive streaming formats DASH, HLS,
 * and SmoothStreaming.
 *
 * <p>It holds various attributes related to the playback of media content according to the
 * specifications outlined in the CMCD standard document <a
 * href="https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf">CTA-5004</a>.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class CmcdLog {

  /**
   * Creates a new instance.
   *
   * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
   * @param trackSelection The {@linkplain ExoTrackSelection track selection}.
   * @param playbackPositionUs The current playback position in microseconds.
   * @param loadPositionUs The current load position in microseconds.
   */
  public static CmcdLog createInstance(
      CmcdConfiguration cmcdConfiguration,
      ExoTrackSelection trackSelection,
      long playbackPositionUs,
      long loadPositionUs) {
    ImmutableMap<@CmcdConfiguration.HeaderKey String, String> customData =
        cmcdConfiguration.requestConfig.getCustomData();
    int bitrateKbps = trackSelection.getSelectedFormat().bitrate / 1000;

    CmcdLog.CmcdObject.Builder cmcdObject =
        new CmcdLog.CmcdObject.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_OBJECT));
    if (cmcdConfiguration.isBitrateLoggingAllowed()) {
      cmcdObject.setBitrateKbps(bitrateKbps);
    }

    CmcdLog.CmcdRequest.Builder cmcdRequest =
        new CmcdLog.CmcdRequest.Builder()
            .setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_REQUEST));
    if (cmcdConfiguration.isBufferLengthLoggingAllowed()) {
      cmcdRequest.setBufferLengthMs(
          loadPositionUs == C.TIME_UNSET ? 0 : (loadPositionUs - playbackPositionUs) / 1000);
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
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bitrateKbps = C.RATE_UNSET_INT;
      }

      /** Sets the {@link CmcdObject#bitrateKbps}. The default value is {@link C#RATE_UNSET_INT}. */
      @CanIgnoreReturnValue
      public Builder setBitrateKbps(int bitrateKbps) {
        this.bitrateKbps = bitrateKbps;
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
     * Custom data where the values of the keys vary with the object being requested, or {@code
     * null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue,key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdObject(Builder builder) {
      this.bitrateKbps = builder.bitrateKbps;
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
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bufferLengthMs = C.TIME_UNSET;
      }

      /**
       * Sets the {@link CmcdRequest#bufferLengthMs}. Rounded to nearest 100 ms. The default value
       * is {@link C#TIME_UNSET}.
       */
      @CanIgnoreReturnValue
      public Builder setBufferLengthMs(long bufferLengthMs) {
        checkArgument(bufferLengthMs == C.TIME_UNSET || bufferLengthMs >= 0);
        this.bufferLengthMs =
            bufferLengthMs == C.TIME_UNSET ? bufferLengthMs : ((bufferLengthMs + 50) / 100) * 100;
        return this;
      }

      /** Sets the {@link CmcdRequest#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public CmcdRequest.Builder setCustomData(@Nullable String customData) {
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
     * Custom data where the values of the keys vary with each request, or {@code null} if unset.
     *
     * <p>The String consists of key-value pairs separated by commas.<br>
     * Example: {@code key1=intValue, key2="stringValue"}.
     */
    @Nullable public final String customData;

    private CmcdRequest(Builder builder) {
      this.bufferLengthMs = builder.bufferLengthMs;
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
      @Nullable private String customData;

      /**
       * Sets the {@link CmcdSession#contentId}. Maximum length allowed is 64 characters. The
       * default value is {@code null}.
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
       */
      @CanIgnoreReturnValue
      public Builder setSessionId(@Nullable String sessionId) {
        checkArgument(sessionId == null || sessionId.length() <= CmcdConfiguration.MAX_ID_LENGTH);
        this.sessionId = sessionId;
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
