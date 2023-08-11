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
import androidx.media3.common.C.TrackType;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

/**
 * This class serves as a factory for generating Common Media Client Data (CMCD) HTTP request
 * headers in adaptive streaming formats, DASH, HLS, and SmoothStreaming.
 *
 * <p>It encapsulates the necessary attributes and information relevant to media content playback,
 * following the guidelines specified in the CMCD standard document <a
 * href="https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf">CTA-5004</a>.
 */
@UnstableApi
public final class CmcdHeadersFactory {

  private static final Joiner COMMA_JOINER = Joiner.on(",");

  /**
   * Retrieves the object type value from the given {@link ExoTrackSelection}.
   *
   * @param trackSelection The {@link ExoTrackSelection} from which to retrieve the object type.
   * @return The object type value as a String if {@link TrackType} can be mapped to one of the
   *     object types specified by {@link ObjectType} annotation, or {@code null}.
   * @throws IllegalArgumentException if the provided {@link ExoTrackSelection} is {@code null}.
   */
  @Nullable
  public static @ObjectType String getObjectType(ExoTrackSelection trackSelection) {
    checkArgument(trackSelection != null);
    @C.TrackType
    int trackType = MimeTypes.getTrackType(trackSelection.getSelectedFormat().sampleMimeType);
    if (trackType == C.TRACK_TYPE_UNKNOWN) {
      trackType = MimeTypes.getTrackType(trackSelection.getSelectedFormat().containerMimeType);
    }

    if (trackType == C.TRACK_TYPE_AUDIO) {
      return OBJECT_TYPE_AUDIO_ONLY;
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      return OBJECT_TYPE_VIDEO_ONLY;
    } else {
      // Track type cannot be mapped to a known object type.
      return null;
    }
  }

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

  /** Indicates the media type of current object being requested. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    OBJECT_TYPE_INIT_SEGMENT,
    OBJECT_TYPE_AUDIO_ONLY,
    OBJECT_TYPE_VIDEO_ONLY,
    OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO
  })
  @Documented
  @Target(TYPE_USE)
  public @interface ObjectType {}

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

  /** Represents the object type for an initialization segment in a media container. */
  public static final String OBJECT_TYPE_INIT_SEGMENT = "i";

  /** Represents the object type for audio-only content in a media container. */
  public static final String OBJECT_TYPE_AUDIO_ONLY = "a";

  /** Represents the object type for video-only content in a media container. */
  public static final String OBJECT_TYPE_VIDEO_ONLY = "v";

  /** Represents the object type for muxed audio and video content in a media container. */
  public static final String OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO = "av";

  private final CmcdConfiguration cmcdConfiguration;
  private final ExoTrackSelection trackSelection;
  private final long bufferedDurationUs;
  private final float playbackRate;
  private final @StreamingFormat String streamingFormat;
  private final boolean isLive;
  private final boolean didRebuffer;
  private final boolean isBufferEmpty;
  private long chunkDurationUs;
  private @Nullable @ObjectType String objectType;

  /**
   * Creates an instance.
   *
   * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
   * @param trackSelection The {@linkplain ExoTrackSelection track selection}.
   * @param bufferedDurationUs The duration of media currently buffered from the current playback
   *     position, in microseconds.
   * @param playbackRate The playback rate indicating the current speed of playback.
   * @param streamingFormat The streaming format of the media content. Must be one of the allowed
   *     streaming formats specified by the {@link StreamingFormat} annotation.
   * @param isLive {@code true} if the media content is being streamed live, {@code false}
   *     otherwise.
   * @param didRebuffer {@code true} if a rebuffering event happened between the previous request
   *     and this one, {@code false} otherwise.
   * @param isBufferEmpty {@code true} if the queue of buffered chunks is empty, {@code false}
   *     otherwise.
   * @throws IllegalArgumentException If {@code bufferedDurationUs} is negative.
   */
  public CmcdHeadersFactory(
      CmcdConfiguration cmcdConfiguration,
      ExoTrackSelection trackSelection,
      long bufferedDurationUs,
      float playbackRate,
      @StreamingFormat String streamingFormat,
      boolean isLive,
      boolean didRebuffer,
      boolean isBufferEmpty) {
    checkArgument(bufferedDurationUs >= 0);
    checkArgument(playbackRate > 0);
    this.cmcdConfiguration = cmcdConfiguration;
    this.trackSelection = trackSelection;
    this.bufferedDurationUs = bufferedDurationUs;
    this.playbackRate = playbackRate;
    this.streamingFormat = streamingFormat;
    this.isLive = isLive;
    this.didRebuffer = didRebuffer;
    this.isBufferEmpty = isBufferEmpty;
    this.chunkDurationUs = C.TIME_UNSET;
  }

  /**
   * Sets the duration of current media chunk being requested, in microseconds. The default value is
   * {@link C#TIME_UNSET}.
   *
   * @throws IllegalArgumentException If {@code chunkDurationUs} is negative.
   */
  @CanIgnoreReturnValue
  public CmcdHeadersFactory setChunkDurationUs(long chunkDurationUs) {
    checkArgument(chunkDurationUs >= 0);
    this.chunkDurationUs = chunkDurationUs;
    return this;
  }

  /**
   * Sets the object type of the current object being requested. Must be one of the allowed object
   * types specified by the {@link ObjectType} annotation.
   *
   * <p>Default is {@code null}.
   */
  @CanIgnoreReturnValue
  public CmcdHeadersFactory setObjectType(@Nullable @ObjectType String objectType) {
    this.objectType = objectType;
    return this;
  }

  /** Creates and returns a new {@link ImmutableMap} containing the CMCD HTTP request headers. */
  public ImmutableMap<@CmcdConfiguration.HeaderKey String, String> createHttpRequestHeaders() {
    ImmutableMap<@CmcdConfiguration.HeaderKey String, String> customData =
        cmcdConfiguration.requestConfig.getCustomData();
    int bitrateKbps = Util.ceilDivide(trackSelection.getSelectedFormat().bitrate, 1000);

    CmcdObject.Builder cmcdObject =
        new CmcdObject.Builder().setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_OBJECT));
    if (!getIsInitSegment()) {
      if (cmcdConfiguration.isBitrateLoggingAllowed()) {
        cmcdObject.setBitrateKbps(bitrateKbps);
      }
      if (cmcdConfiguration.isTopBitrateLoggingAllowed()) {
        TrackGroup trackGroup = trackSelection.getTrackGroup();
        int topBitrate = trackSelection.getSelectedFormat().bitrate;
        for (int i = 0; i < trackGroup.length; i++) {
          topBitrate = max(topBitrate, trackGroup.getFormat(i).bitrate);
        }
        cmcdObject.setTopBitrateKbps(Util.ceilDivide(topBitrate, 1000));
      }
      if (cmcdConfiguration.isObjectDurationLoggingAllowed()) {
        cmcdObject.setObjectDurationMs(Util.usToMs(chunkDurationUs));
      }
    }

    if (cmcdConfiguration.isObjectTypeLoggingAllowed()) {
      cmcdObject.setObjectType(objectType);
    }

    CmcdRequest.Builder cmcdRequest =
        new CmcdRequest.Builder().setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_REQUEST));
    if (!getIsInitSegment() && cmcdConfiguration.isBufferLengthLoggingAllowed()) {
      cmcdRequest.setBufferLengthMs(Util.usToMs(bufferedDurationUs));
    }
    if (cmcdConfiguration.isMeasuredThroughputLoggingAllowed()
        && trackSelection.getLatestBitrateEstimate() != Long.MIN_VALUE) {
      cmcdRequest.setMeasuredThroughputInKbps(
          Util.ceilDivide(trackSelection.getLatestBitrateEstimate(), 1000));
    }
    if (cmcdConfiguration.isDeadlineLoggingAllowed()) {
      cmcdRequest.setDeadlineMs(Util.usToMs((long) (bufferedDurationUs / playbackRate)));
    }
    if (cmcdConfiguration.isStartupLoggingAllowed()) {
      cmcdRequest.setStartup(didRebuffer || isBufferEmpty);
    }

    CmcdSession.Builder cmcdSession =
        new CmcdSession.Builder().setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_SESSION));
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
    if (cmcdConfiguration.isPlaybackRateLoggingAllowed()) {
      cmcdSession.setPlaybackRate(playbackRate);
    }

    CmcdStatus.Builder cmcdStatus =
        new CmcdStatus.Builder().setCustomData(customData.get(CmcdConfiguration.KEY_CMCD_STATUS));
    if (cmcdConfiguration.isMaximumRequestThroughputLoggingAllowed()) {
      cmcdStatus.setMaximumRequestedThroughputKbps(
          cmcdConfiguration.requestConfig.getRequestedMaximumThroughputKbps(bitrateKbps));
    }
    if (cmcdConfiguration.isBufferStarvationLoggingAllowed()) {
      cmcdStatus.setBufferStarvation(didRebuffer);
    }

    ImmutableMap.Builder<String, String> httpRequestHeaders = ImmutableMap.builder();
    cmcdObject.build().populateHttpRequestHeaders(httpRequestHeaders);
    cmcdRequest.build().populateHttpRequestHeaders(httpRequestHeaders);
    cmcdSession.build().populateHttpRequestHeaders(httpRequestHeaders);
    cmcdStatus.build().populateHttpRequestHeaders(httpRequestHeaders);
    return httpRequestHeaders.buildOrThrow();
  }

  private boolean getIsInitSegment() {
    return objectType != null && objectType.equals(OBJECT_TYPE_INIT_SEGMENT);
  }

  /**
   * Keys whose values vary with the object being requested. Contains CMCD fields: {@code br},
   * {@code tb}, {@code d} and {@code ot}.
   */
  private static final class CmcdObject {

    /** Builder for {@link CmcdObject} instances. */
    public static final class Builder {
      private int bitrateKbps;
      private int topBitrateKbps;
      private long objectDurationMs;
      @Nullable private @ObjectType String objectType;
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bitrateKbps = C.RATE_UNSET_INT;
        this.topBitrateKbps = C.RATE_UNSET_INT;
        this.objectDurationMs = C.TIME_UNSET;
      }

      /**
       * Sets the {@link CmcdObject#bitrateKbps}. The default value is {@link C#RATE_UNSET_INT}.
       *
       * @throws IllegalArgumentException If {@code bitrateKbps} is not equal to {@link
       *     C#RATE_UNSET_INT} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setBitrateKbps(int bitrateKbps) {
        checkArgument(bitrateKbps >= 0 || bitrateKbps == C.RATE_UNSET_INT);
        this.bitrateKbps = bitrateKbps;
        return this;
      }

      /**
       * Sets the {@link CmcdObject#topBitrateKbps}. The default value is {@link C#RATE_UNSET_INT}.
       *
       * @throws IllegalArgumentException If {@code topBitrateKbps} is not equal to {@link
       *     C#RATE_UNSET_INT} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setTopBitrateKbps(int topBitrateKbps) {
        checkArgument(topBitrateKbps >= 0 || topBitrateKbps == C.RATE_UNSET_INT);
        this.topBitrateKbps = topBitrateKbps;
        return this;
      }

      /**
       * Sets the {@link CmcdObject#objectDurationMs}. The default value is {@link C#TIME_UNSET}.
       *
       * @throws IllegalArgumentException If {@code objectDurationMs} is not equal to {@link
       *     C#TIME_UNSET} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setObjectDurationMs(long objectDurationMs) {
        checkArgument(objectDurationMs >= 0 || objectDurationMs == C.TIME_UNSET);
        this.objectDurationMs = objectDurationMs;
        return this;
      }

      /** Sets the {@link CmcdObject#objectType}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setObjectType(@Nullable @ObjectType String objectType) {
        this.objectType = objectType;
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
     * The media type of the current object being requested , or {@code null} if unset. Must be one
     * of the allowed object types specified by the {@link ObjectType} annotation.
     *
     * <p>If the object type being requested is unknown, then this key MUST NOT be used.
     */
    @Nullable public final @ObjectType String objectType;

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
      this.objectType = builder.objectType;
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
      ArrayList<String> headerValueList = new ArrayList<>();
      if (bitrateKbps != C.RATE_UNSET_INT) {
        headerValueList.add(CmcdConfiguration.KEY_BITRATE + "=" + bitrateKbps);
      }
      if (topBitrateKbps != C.RATE_UNSET_INT) {
        headerValueList.add(CmcdConfiguration.KEY_TOP_BITRATE + "=" + topBitrateKbps);
      }
      if (objectDurationMs != C.TIME_UNSET) {
        headerValueList.add(CmcdConfiguration.KEY_OBJECT_DURATION + "=" + objectDurationMs);
      }
      if (!TextUtils.isEmpty(objectType)) {
        headerValueList.add(CmcdConfiguration.KEY_OBJECT_TYPE + "=" + objectType);
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValueList.add(customData);
      }

      if (!headerValueList.isEmpty()) {
        httpRequestHeaders.put(
            CmcdConfiguration.KEY_CMCD_OBJECT, COMMA_JOINER.join(headerValueList));
      }
    }
  }

  /**
   * Keys whose values vary with each request. Contains CMCD fields: {@code bl}, {@code mtp}, {@code
   * dl} and {@code su}.
   */
  private static final class CmcdRequest {

    /** Builder for {@link CmcdRequest} instances. */
    public static final class Builder {
      private long bufferLengthMs;
      private long measuredThroughputInKbps;
      private long deadlineMs;
      private boolean startup;
      @Nullable private String customData;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bufferLengthMs = C.TIME_UNSET;
        this.measuredThroughputInKbps = Long.MIN_VALUE;
        this.deadlineMs = C.TIME_UNSET;
      }

      /**
       * Sets the {@link CmcdRequest#bufferLengthMs}. Rounded to nearest 100 ms. The default value
       * is {@link C#TIME_UNSET}.
       *
       * @throws IllegalArgumentException If {@code bufferLengthMs} is not equal to {@link
       *     C#TIME_UNSET} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setBufferLengthMs(long bufferLengthMs) {
        checkArgument(bufferLengthMs >= 0 || bufferLengthMs == C.TIME_UNSET);
        this.bufferLengthMs = ((bufferLengthMs + 50) / 100) * 100;
        return this;
      }

      /**
       * Sets the {@link CmcdRequest#measuredThroughputInKbps}. Rounded to nearest 100 kbps. The
       * default value is {@link Long#MIN_VALUE}.
       *
       * @throws IllegalArgumentException If {@code measuredThroughputInKbps} is not equal to {@link
       *     Long#MIN_VALUE} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setMeasuredThroughputInKbps(long measuredThroughputInKbps) {
        checkArgument(measuredThroughputInKbps >= 0 || measuredThroughputInKbps == Long.MIN_VALUE);
        this.measuredThroughputInKbps = ((measuredThroughputInKbps + 50) / 100) * 100;

        return this;
      }

      /**
       * Sets the {@link CmcdRequest#deadlineMs}. Rounded to nearest 100 ms. The default value is
       * {@link C#TIME_UNSET}.
       *
       * @throws IllegalArgumentException If {@code deadlineMs} is not equal to {@link C#TIME_UNSET}
       *     and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setDeadlineMs(long deadlineMs) {
        checkArgument(deadlineMs >= 0 || deadlineMs == C.TIME_UNSET);
        this.deadlineMs = ((deadlineMs + 50) / 100) * 100;
        return this;
      }

      /** Sets the {@link CmcdRequest#startup}. The default value is {@code false}. */
      @CanIgnoreReturnValue
      public Builder setStartup(boolean startup) {
        this.startup = startup;
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
     * <p>This key SHOULD only be sent with an {@link CmcdObject#objectType} of {@link
     * #OBJECT_TYPE_AUDIO_ONLY}, {@link #OBJECT_TYPE_VIDEO_ONLY} or {@link
     * #OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO}.
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
     * Deadline in milliseconds from the request time until the first sample of this Segment/Object
     * needs to be available in order to not create a buffer underrun or any other playback
     * problems, or {@link C#TIME_UNSET} if unset.
     *
     * <p>This value MUST be rounded to the nearest 100 ms. For a playback rate of 1, this may be
     * equivalent to the player’s remaining buffer length.
     */
    public final long deadlineMs;

    /**
     * A boolean indicating whether the chunk is needed urgently due to startup, seeking or recovery
     * after a buffer-empty event, or {@code false} if unknown. The media SHOULD not be rendering
     * when this request is made.
     */
    public final boolean startup;

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
      this.deadlineMs = builder.deadlineMs;
      this.startup = builder.startup;
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
      ArrayList<String> headerValueList = new ArrayList<>();
      if (bufferLengthMs != C.TIME_UNSET) {
        headerValueList.add(CmcdConfiguration.KEY_BUFFER_LENGTH + "=" + bufferLengthMs);
      }
      if (measuredThroughputInKbps != Long.MIN_VALUE) {
        headerValueList.add(
            CmcdConfiguration.KEY_MEASURED_THROUGHPUT + "=" + measuredThroughputInKbps);
      }
      if (deadlineMs != C.TIME_UNSET) {
        headerValueList.add(CmcdConfiguration.KEY_DEADLINE + "=" + deadlineMs);
      }
      if (startup) {
        headerValueList.add(CmcdConfiguration.KEY_STARTUP);
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValueList.add(customData);
      }

      if (!headerValueList.isEmpty()) {
        httpRequestHeaders.put(
            CmcdConfiguration.KEY_CMCD_REQUEST, COMMA_JOINER.join(headerValueList));
      }
    }
  }

  /**
   * Keys whose values are expected to be invariant over the life of the session. Contains CMCD
   * fields: {@code cid}, {@code sid}, {@code sf}, {@code st}, {@code pr} and {@code v}.
   */
  private static final class CmcdSession {

    /** Builder for {@link CmcdSession} instances. */
    public static final class Builder {
      @Nullable private String contentId;
      @Nullable private String sessionId;
      @Nullable private @StreamingFormat String streamingFormat;
      @Nullable private @StreamType String streamType;
      private float playbackRate;
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

      /**
       * Sets the {@link CmcdSession#playbackRate}. The default value is {@link C#RATE_UNSET}.
       *
       * @throws IllegalArgumentException If {@code playbackRate} is not equal to {@link
       *     C#RATE_UNSET} and is non-positive.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackRate(float playbackRate) {
        checkArgument(playbackRate > 0 || playbackRate == C.RATE_UNSET);
        this.playbackRate = playbackRate;
        return this;
      }

      /** Sets the {@link CmcdSession#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setCustomData(@Nullable String customData) {
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
     * The streaming format that defines the current request, or{@code null} if unset. Must be one
     * of the allowed stream formats specified by the {@link StreamingFormat} annotation. If the
     * streaming format being requested is unknown, then this key MUST NOT be used.
     */
    @Nullable public final @StreamingFormat String streamingFormat;

    /**
     * Type of stream, or {@code null} if unset. Must be one of the allowed stream types specified
     * by the {@link StreamType} annotation.
     */
    @Nullable public final @StreamType String streamType;

    /**
     * The playback rate indicating the current rate of playback, or {@link C#RATE_UNSET} if unset.
     */
    public final float playbackRate;

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
      this.playbackRate = builder.playbackRate;
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
      ArrayList<String> headerValueList = new ArrayList<>();
      if (!TextUtils.isEmpty(this.contentId)) {
        headerValueList.add(
            Util.formatInvariant("%s=\"%s\"", CmcdConfiguration.KEY_CONTENT_ID, contentId));
      }
      if (!TextUtils.isEmpty(this.sessionId)) {
        headerValueList.add(
            Util.formatInvariant("%s=\"%s\"", CmcdConfiguration.KEY_SESSION_ID, sessionId));
      }
      if (!TextUtils.isEmpty(this.streamingFormat)) {
        headerValueList.add(CmcdConfiguration.KEY_STREAMING_FORMAT + "=" + streamingFormat);
      }
      if (!TextUtils.isEmpty(this.streamType)) {
        headerValueList.add(CmcdConfiguration.KEY_STREAM_TYPE + "=" + streamType);
      }
      if (playbackRate != C.RATE_UNSET && playbackRate != 1.0f) {
        headerValueList.add(
            Util.formatInvariant("%s=%.2f", CmcdConfiguration.KEY_PLAYBACK_RATE, playbackRate));
      }
      if (VERSION != 1) {
        headerValueList.add(CmcdConfiguration.KEY_VERSION + "=" + VERSION);
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValueList.add(customData);
      }

      if (!headerValueList.isEmpty()) {
        httpRequestHeaders.put(
            CmcdConfiguration.KEY_CMCD_SESSION, COMMA_JOINER.join(headerValueList));
      }
    }
  }

  /**
   * Keys whose values do not vary with every request or object. Contains CMCD fields: {@code rtp}
   * and {@code bs}.
   */
  private static final class CmcdStatus {

    /** Builder for {@link CmcdStatus} instances. */
    public static final class Builder {
      private int maximumRequestedThroughputKbps;
      private boolean bufferStarvation;
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
            maximumRequestedThroughputKbps >= 0
                || maximumRequestedThroughputKbps == C.RATE_UNSET_INT);

        this.maximumRequestedThroughputKbps =
            maximumRequestedThroughputKbps == C.RATE_UNSET_INT
                ? maximumRequestedThroughputKbps
                : ((maximumRequestedThroughputKbps + 50) / 100) * 100;

        return this;
      }

      /** Sets the {@link CmcdStatus#bufferStarvation}. The default value is {@code false}. */
      @CanIgnoreReturnValue
      public Builder setBufferStarvation(boolean bufferStarvation) {
        this.bufferStarvation = bufferStarvation;
        return this;
      }

      /** Sets the {@link CmcdStatus#customData}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setCustomData(@Nullable String customData) {
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
     * A boolean indicating whether the buffer was starved at some point between the prior request
     * and this chunk request, resulting in the player being in a rebuffering state and the video or
     * audio playback being stalled, or {@code false} if unknown.
     */
    public final boolean bufferStarvation;

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
      this.bufferStarvation = builder.bufferStarvation;
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
      ArrayList<String> headerValueList = new ArrayList<>();
      if (maximumRequestedThroughputKbps != C.RATE_UNSET_INT) {
        headerValueList.add(
            CmcdConfiguration.KEY_MAXIMUM_REQUESTED_BITRATE + "=" + maximumRequestedThroughputKbps);
      }
      if (bufferStarvation) {
        headerValueList.add(CmcdConfiguration.KEY_BUFFER_STARVATION);
      }
      if (!TextUtils.isEmpty(customData)) {
        headerValueList.add(customData);
      }

      if (!headerValueList.isEmpty()) {
        httpRequestHeaders.put(
            CmcdConfiguration.KEY_CMCD_STATUS, COMMA_JOINER.join(headerValueList));
      }
    }
  }
}
