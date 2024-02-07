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
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.C.TrackType;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class provides functionality for generating and adding Common Media Client Data (CMCD) data
 * to adaptive streaming formats, DASH, HLS, and SmoothStreaming.
 *
 * <p>It encapsulates the necessary attributes and information relevant to media content playback,
 * following the guidelines specified in the CMCD standard document <a
 * href="https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf">CTA-5004</a>.
 */
@UnstableApi
public final class CmcdData {

  /** {@link CmcdData.Factory} for {@link CmcdData} instances. */
  public static final class Factory {

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

    /**
     * Custom key names MUST carry a hyphenated prefix to ensure that there will not be a namespace
     * collision with future revisions to this specification. Clients SHOULD use a reverse-DNS
     * syntax when defining their own prefix.
     */
    private static final Pattern CUSTOM_KEY_NAME_PATTERN = Pattern.compile(".*-.*");

    private final CmcdConfiguration cmcdConfiguration;
    private final ExoTrackSelection trackSelection;
    private final long bufferedDurationUs;
    private final float playbackRate;
    private final @CmcdData.StreamingFormat String streamingFormat;
    private final boolean isLive;
    private final boolean didRebuffer;
    private final boolean isBufferEmpty;
    private long chunkDurationUs;
    @Nullable private @CmcdData.ObjectType String objectType;
    @Nullable private String nextObjectRequest;
    @Nullable private String nextRangeRequest;

    /**
     * Creates an instance.
     *
     * @param cmcdConfiguration The {@link CmcdConfiguration} for this chunk source.
     * @param trackSelection The {@linkplain ExoTrackSelection track selection}.
     * @param bufferedDurationUs The duration of media currently buffered from the current playback
     *     position, in microseconds.
     * @param playbackRate The playback rate indicating the current speed of playback.
     * @param streamingFormat The streaming format of the media content. Must be one of the allowed
     *     streaming formats specified by the {@link CmcdData.StreamingFormat} annotation.
     * @param isLive {@code true} if the media content is being streamed live, {@code false}
     *     otherwise.
     * @param didRebuffer {@code true} if a rebuffering event happened between the previous request
     *     and this one, {@code false} otherwise.
     * @param isBufferEmpty {@code true} if the queue of buffered chunks is empty, {@code false}
     *     otherwise.
     * @throws IllegalArgumentException If {@code bufferedDurationUs} is negative or {@code
     *     playbackRate} is non-positive.
     */
    public Factory(
        CmcdConfiguration cmcdConfiguration,
        ExoTrackSelection trackSelection,
        long bufferedDurationUs,
        float playbackRate,
        @CmcdData.StreamingFormat String streamingFormat,
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
     * Retrieves the object type value from the given {@link ExoTrackSelection}.
     *
     * @param trackSelection The {@link ExoTrackSelection} from which to retrieve the object type.
     * @return The object type value as a String if {@link TrackType} can be mapped to one of the
     *     object types specified by {@link CmcdData.ObjectType} annotation, or {@code null}.
     * @throws IllegalArgumentException if the provided {@link ExoTrackSelection} is {@code null}.
     */
    @Nullable
    public static @CmcdData.ObjectType String getObjectType(ExoTrackSelection trackSelection) {
      checkArgument(trackSelection != null);
      @TrackType
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

    /**
     * Sets the duration of current media chunk being requested, in microseconds. The default value
     * is {@link C#TIME_UNSET}.
     *
     * @throws IllegalArgumentException If {@code chunkDurationUs} is negative.
     */
    @CanIgnoreReturnValue
    public Factory setChunkDurationUs(long chunkDurationUs) {
      checkArgument(chunkDurationUs >= 0);
      this.chunkDurationUs = chunkDurationUs;
      return this;
    }

    /**
     * Sets the object type of the current object being requested. Must be one of the allowed object
     * types specified by the {@link CmcdData.ObjectType} annotation.
     *
     * <p>Default is {@code null}.
     */
    @CanIgnoreReturnValue
    public Factory setObjectType(@Nullable @CmcdData.ObjectType String objectType) {
      this.objectType = objectType;
      return this;
    }

    /**
     * Sets the relative path of the next object to be requested. This can be used to trigger
     * pre-fetching by the CDN.
     *
     * <p>Default is {@code null}.
     */
    @CanIgnoreReturnValue
    public Factory setNextObjectRequest(@Nullable String nextObjectRequest) {
      this.nextObjectRequest = nextObjectRequest;
      return this;
    }

    /**
     * Sets the byte range representing the partial object request. This can be used to trigger
     * pre-fetching by the CDN.
     *
     * <p>Default is {@code null}.
     */
    @CanIgnoreReturnValue
    public Factory setNextRangeRequest(@Nullable String nextRangeRequest) {
      this.nextRangeRequest = nextRangeRequest;
      return this;
    }

    public CmcdData createCmcdData() {
      ImmutableListMultimap<@CmcdConfiguration.HeaderKey String, String> customData =
          cmcdConfiguration.requestConfig.getCustomData();
      for (String headerKey : customData.keySet()) {
        validateCustomDataListFormat(customData.get(headerKey));
      }

      int bitrateKbps = Util.ceilDivide(trackSelection.getSelectedFormat().bitrate, 1000);

      CmcdObject.Builder cmcdObject = new CmcdObject.Builder();
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
      if (customData.containsKey(CmcdConfiguration.KEY_CMCD_OBJECT)) {
        cmcdObject.setCustomDataList(customData.get(CmcdConfiguration.KEY_CMCD_OBJECT));
      }

      CmcdRequest.Builder cmcdRequest = new CmcdRequest.Builder();
      if (!getIsInitSegment() && cmcdConfiguration.isBufferLengthLoggingAllowed()) {
        cmcdRequest.setBufferLengthMs(Util.usToMs(bufferedDurationUs));
      }
      if (cmcdConfiguration.isMeasuredThroughputLoggingAllowed()
          && trackSelection.getLatestBitrateEstimate() != C.RATE_UNSET_INT) {
        cmcdRequest.setMeasuredThroughputInKbps(
            Util.ceilDivide(trackSelection.getLatestBitrateEstimate(), 1000));
      }
      if (cmcdConfiguration.isDeadlineLoggingAllowed()) {
        cmcdRequest.setDeadlineMs(Util.usToMs((long) (bufferedDurationUs / playbackRate)));
      }
      if (cmcdConfiguration.isStartupLoggingAllowed()) {
        cmcdRequest.setStartup(didRebuffer || isBufferEmpty);
      }
      if (cmcdConfiguration.isNextObjectRequestLoggingAllowed()) {
        cmcdRequest.setNextObjectRequest(nextObjectRequest);
      }
      if (cmcdConfiguration.isNextRangeRequestLoggingAllowed()) {
        cmcdRequest.setNextRangeRequest(nextRangeRequest);
      }
      if (customData.containsKey(CmcdConfiguration.KEY_CMCD_REQUEST)) {
        cmcdRequest.setCustomDataList(customData.get(CmcdConfiguration.KEY_CMCD_REQUEST));
      }

      CmcdSession.Builder cmcdSession = new CmcdSession.Builder();
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
      if (customData.containsKey(CmcdConfiguration.KEY_CMCD_SESSION)) {
        cmcdSession.setCustomDataList(customData.get(CmcdConfiguration.KEY_CMCD_SESSION));
      }

      CmcdStatus.Builder cmcdStatus = new CmcdStatus.Builder();
      if (cmcdConfiguration.isMaximumRequestThroughputLoggingAllowed()) {
        cmcdStatus.setMaximumRequestedThroughputKbps(
            cmcdConfiguration.requestConfig.getRequestedMaximumThroughputKbps(bitrateKbps));
      }
      if (cmcdConfiguration.isBufferStarvationLoggingAllowed()) {
        cmcdStatus.setBufferStarvation(didRebuffer);
      }
      if (customData.containsKey(CmcdConfiguration.KEY_CMCD_STATUS)) {
        cmcdStatus.setCustomDataList(customData.get(CmcdConfiguration.KEY_CMCD_STATUS));
      }

      return new CmcdData(
          cmcdObject.build(),
          cmcdRequest.build(),
          cmcdSession.build(),
          cmcdStatus.build(),
          cmcdConfiguration.dataTransmissionMode);
    }

    private boolean getIsInitSegment() {
      return objectType != null && objectType.equals(OBJECT_TYPE_INIT_SEGMENT);
    }

    private void validateCustomDataListFormat(List<String> customDataList) {
      for (String customData : customDataList) {
        String key = Util.split(customData, "=")[0];
        checkState(CUSTOM_KEY_NAME_PATTERN.matcher(key).matches());
      }
    }
  }

  /** Indicates the streaming format used for media content. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    Factory.STREAMING_FORMAT_DASH,
    Factory.STREAMING_FORMAT_HLS,
    Factory.STREAMING_FORMAT_SS
  })
  @Documented
  @Target(TYPE_USE)
  public @interface StreamingFormat {}

  /** Indicates the type of streaming for media content. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({Factory.STREAM_TYPE_VOD, Factory.STREAM_TYPE_LIVE})
  @Documented
  @Target(TYPE_USE)
  public @interface StreamType {}

  /** Indicates the media type of current object being requested. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    Factory.OBJECT_TYPE_INIT_SEGMENT,
    Factory.OBJECT_TYPE_AUDIO_ONLY,
    Factory.OBJECT_TYPE_VIDEO_ONLY,
    Factory.OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO
  })
  @Documented
  @Target(TYPE_USE)
  public @interface ObjectType {}

  private static final Joiner COMMA_JOINER = Joiner.on(",");

  private final CmcdObject cmcdObject;
  private final CmcdRequest cmcdRequest;
  private final CmcdSession cmcdSession;
  private final CmcdStatus cmcdStatus;
  private final @CmcdConfiguration.DataTransmissionMode int dataTransmissionMode;

  private CmcdData(
      CmcdObject cmcdObject,
      CmcdRequest cmcdRequest,
      CmcdSession cmcdSession,
      CmcdStatus cmcdStatus,
      @CmcdConfiguration.DataTransmissionMode int datatTransmissionMode) {
    this.cmcdObject = cmcdObject;
    this.cmcdRequest = cmcdRequest;
    this.cmcdSession = cmcdSession;
    this.cmcdStatus = cmcdStatus;
    this.dataTransmissionMode = datatTransmissionMode;
  }

  /**
   * Adds Common Media Client Data (CMCD) related information to the provided {@link DataSpec}
   * object.
   */
  public DataSpec addToDataSpec(DataSpec dataSpec) {
    ArrayListMultimap<String, String> cmcdDataMap = ArrayListMultimap.create();
    cmcdObject.populateCmcdDataMap(cmcdDataMap);
    cmcdRequest.populateCmcdDataMap(cmcdDataMap);
    cmcdSession.populateCmcdDataMap(cmcdDataMap);
    cmcdStatus.populateCmcdDataMap(cmcdDataMap);

    if (dataTransmissionMode == CmcdConfiguration.MODE_REQUEST_HEADER) {
      ImmutableMap.Builder<String, String> httpRequestHeaders = ImmutableMap.builder();
      for (String headerKey : cmcdDataMap.keySet()) {
        List<String> headerValues = cmcdDataMap.get(headerKey);
        Collections.sort(headerValues);
        httpRequestHeaders.put(headerKey, COMMA_JOINER.join(headerValues));
      }
      return dataSpec.withAdditionalHeaders(httpRequestHeaders.buildOrThrow());
    } else {
      List<String> keyValuePairs = new ArrayList<>();
      for (Collection<String> values : cmcdDataMap.asMap().values()) {
        keyValuePairs.addAll(values);
      }
      Collections.sort(keyValuePairs);
      Uri.Builder uriBuilder =
          dataSpec
              .uri
              .buildUpon()
              .appendQueryParameter(
                  CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY, COMMA_JOINER.join(keyValuePairs));
      return dataSpec.buildUpon().setUri(uriBuilder.build()).build();
    }
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
      private ImmutableList<String> customDataList;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bitrateKbps = C.RATE_UNSET_INT;
        this.topBitrateKbps = C.RATE_UNSET_INT;
        this.objectDurationMs = C.TIME_UNSET;
        this.customDataList = ImmutableList.of();
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

      /** Sets the {@link CmcdObject#customDataList}. The default value is an empty list. */
      @CanIgnoreReturnValue
      public Builder setCustomDataList(List<String> customDataList) {
        this.customDataList = ImmutableList.copyOf(customDataList);
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

    /** Custom data that vary based on the specific object being requested. */
    public final ImmutableList<String> customDataList;

    private CmcdObject(Builder builder) {
      this.bitrateKbps = builder.bitrateKbps;
      this.topBitrateKbps = builder.topBitrateKbps;
      this.objectDurationMs = builder.objectDurationMs;
      this.objectType = builder.objectType;
      this.customDataList = builder.customDataList;
    }

    /**
     * Populates the {@code cmcdDataMap} with {@link CmcdConfiguration#KEY_CMCD_OBJECT} values.
     *
     * @param cmcdDataMap An {@link ArrayListMultimap} to which CMCD data will be added.
     */
    public void populateCmcdDataMap(
        ArrayListMultimap<@CmcdConfiguration.HeaderKey String, String> cmcdDataMap) {
      ArrayList<String> keyValuePairs = new ArrayList<>();
      if (bitrateKbps != C.RATE_UNSET_INT) {
        keyValuePairs.add(CmcdConfiguration.KEY_BITRATE + "=" + bitrateKbps);
      }
      if (topBitrateKbps != C.RATE_UNSET_INT) {
        keyValuePairs.add(CmcdConfiguration.KEY_TOP_BITRATE + "=" + topBitrateKbps);
      }
      if (objectDurationMs != C.TIME_UNSET) {
        keyValuePairs.add(CmcdConfiguration.KEY_OBJECT_DURATION + "=" + objectDurationMs);
      }
      if (!TextUtils.isEmpty(objectType)) {
        keyValuePairs.add(CmcdConfiguration.KEY_OBJECT_TYPE + "=" + objectType);
      }
      keyValuePairs.addAll(customDataList);

      if (!keyValuePairs.isEmpty()) {
        cmcdDataMap.putAll(CmcdConfiguration.KEY_CMCD_OBJECT, keyValuePairs);
      }
    }
  }

  /**
   * Keys whose values vary with each request. Contains CMCD fields: {@code bl}, {@code mtp}, {@code
   * dl}, {@code su}, {@code nor} and {@code nrr}.
   */
  private static final class CmcdRequest {

    /** Builder for {@link CmcdRequest} instances. */
    public static final class Builder {
      private long bufferLengthMs;
      private long measuredThroughputInKbps;
      private long deadlineMs;
      private boolean startup;
      @Nullable private String nextObjectRequest;
      @Nullable private String nextRangeRequest;
      private ImmutableList<String> customDataList;

      /** Creates a new instance with default values. */
      public Builder() {
        this.bufferLengthMs = C.TIME_UNSET;
        this.measuredThroughputInKbps = C.RATE_UNSET_INT;
        this.deadlineMs = C.TIME_UNSET;
        this.customDataList = ImmutableList.of();
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
       * default value is {@link C#RATE_UNSET_INT}.
       *
       * @throws IllegalArgumentException If {@code measuredThroughputInKbps} is not equal to {@link
       *     C#RATE_UNSET_INT} and is negative.
       */
      @CanIgnoreReturnValue
      public Builder setMeasuredThroughputInKbps(long measuredThroughputInKbps) {
        checkArgument(
            measuredThroughputInKbps >= 0 || measuredThroughputInKbps == C.RATE_UNSET_INT);
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

      /**
       * Sets the {@link CmcdRequest#nextObjectRequest}. This string is URL encoded. The default
       * value is {@code null}.
       */
      @CanIgnoreReturnValue
      public Builder setNextObjectRequest(@Nullable String nextObjectRequest) {
        this.nextObjectRequest = nextObjectRequest == null ? null : Uri.encode(nextObjectRequest);
        return this;
      }

      /** Sets the {@link CmcdRequest#nextRangeRequest}. The default value is {@code null}. */
      @CanIgnoreReturnValue
      public Builder setNextRangeRequest(@Nullable String nextRangeRequest) {
        this.nextRangeRequest = nextRangeRequest;
        return this;
      }

      /** Sets the {@link CmcdRequest#customDataList}. The default value is an empty list. */
      @CanIgnoreReturnValue
      public Builder setCustomDataList(List<String> customDataList) {
        this.customDataList = ImmutableList.copyOf(customDataList);
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
     * Factory#OBJECT_TYPE_AUDIO_ONLY}, {@link Factory#OBJECT_TYPE_VIDEO_ONLY} or {@link
     * Factory#OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO}.
     *
     * <p>This value MUST be rounded to the nearest 100 ms.
     */
    public final long bufferLengthMs;

    /**
     * The throughput between client and server, as measured by the client, or {@link
     * C#RATE_UNSET_INT} if unset.
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
     * Relative path of the next object to be requested, or {@code null} if unset. This can be used
     * to trigger pre-fetching by the CDN. This MUST be a path relative to the current request.
     *
     * <p>This string MUST be URL encoded.
     *
     * <p><b>Note:</b> The client SHOULD NOT depend upon any pre-fetch action being taken - it is
     * merely a request for such a pre-fetch to take place.
     */
    @Nullable public final String nextObjectRequest;

    /**
     * The byte range representing the partial object request, or {@code null} if unset. If the
     * {@link #nextObjectRequest} field is not set, then the object is assumed to match the object
     * currently being requested.
     *
     * <p><b>Note:</b> The client SHOULD NOT depend upon any pre-fetch action being taken - it is
     * merely a request for such a pre-fetch to take place.
     */
    @Nullable public final String nextRangeRequest;

    /** Custom data that vary with each request. */
    public final ImmutableList<String> customDataList;

    private CmcdRequest(Builder builder) {
      this.bufferLengthMs = builder.bufferLengthMs;
      this.measuredThroughputInKbps = builder.measuredThroughputInKbps;
      this.deadlineMs = builder.deadlineMs;
      this.startup = builder.startup;
      this.nextObjectRequest = builder.nextObjectRequest;
      this.nextRangeRequest = builder.nextRangeRequest;
      this.customDataList = builder.customDataList;
    }

    /**
     * Populates the {@code cmcdDataMap} with {@link CmcdConfiguration#KEY_CMCD_REQUEST} values.
     *
     * @param cmcdDataMap An {@link ArrayListMultimap} to which CMCD data will be added.
     */
    public void populateCmcdDataMap(
        ArrayListMultimap<@CmcdConfiguration.HeaderKey String, String> cmcdDataMap) {
      ArrayList<String> keyValuePairs = new ArrayList<>();
      if (bufferLengthMs != C.TIME_UNSET) {
        keyValuePairs.add(CmcdConfiguration.KEY_BUFFER_LENGTH + "=" + bufferLengthMs);
      }
      if (measuredThroughputInKbps != C.RATE_UNSET_INT) {
        keyValuePairs.add(
            CmcdConfiguration.KEY_MEASURED_THROUGHPUT + "=" + measuredThroughputInKbps);
      }
      if (deadlineMs != C.TIME_UNSET) {
        keyValuePairs.add(CmcdConfiguration.KEY_DEADLINE + "=" + deadlineMs);
      }
      if (startup) {
        keyValuePairs.add(CmcdConfiguration.KEY_STARTUP);
      }
      if (!TextUtils.isEmpty(nextObjectRequest)) {
        keyValuePairs.add(
            Util.formatInvariant(
                "%s=\"%s\"", CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, nextObjectRequest));
      }
      if (!TextUtils.isEmpty(nextRangeRequest)) {
        keyValuePairs.add(
            Util.formatInvariant(
                "%s=\"%s\"", CmcdConfiguration.KEY_NEXT_RANGE_REQUEST, nextRangeRequest));
      }
      keyValuePairs.addAll(customDataList);

      if (!keyValuePairs.isEmpty()) {
        cmcdDataMap.putAll(CmcdConfiguration.KEY_CMCD_REQUEST, keyValuePairs);
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
      private ImmutableList<String> customDataList;

      /** Creates a new instance with default values. */
      public Builder() {
        this.customDataList = ImmutableList.of();
      }

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

      /** Sets the {@link CmcdSession#customDataList}. The default value is an empty list. */
      @CanIgnoreReturnValue
      public Builder setCustomDataList(List<String> customDataList) {
        this.customDataList = ImmutableList.copyOf(customDataList);
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

    /** Custom data that is expected to be invariant over the life of the session. */
    public final ImmutableList<String> customDataList;

    private CmcdSession(Builder builder) {
      this.contentId = builder.contentId;
      this.sessionId = builder.sessionId;
      this.streamingFormat = builder.streamingFormat;
      this.streamType = builder.streamType;
      this.playbackRate = builder.playbackRate;
      this.customDataList = builder.customDataList;
    }

    /**
     * Populates the {@code cmcdDataMap} with {@link CmcdConfiguration#KEY_CMCD_SESSION} values.
     *
     * @param cmcdDataMap An {@link ArrayListMultimap} to which CMCD data will be added.
     */
    public void populateCmcdDataMap(
        ArrayListMultimap<@CmcdConfiguration.HeaderKey String, String> cmcdDataMap) {
      ArrayList<String> keyValuePairs = new ArrayList<>();
      if (!TextUtils.isEmpty(this.contentId)) {
        keyValuePairs.add(
            Util.formatInvariant("%s=\"%s\"", CmcdConfiguration.KEY_CONTENT_ID, contentId));
      }
      if (!TextUtils.isEmpty(this.sessionId)) {
        keyValuePairs.add(
            Util.formatInvariant("%s=\"%s\"", CmcdConfiguration.KEY_SESSION_ID, sessionId));
      }
      if (!TextUtils.isEmpty(this.streamingFormat)) {
        keyValuePairs.add(CmcdConfiguration.KEY_STREAMING_FORMAT + "=" + streamingFormat);
      }
      if (!TextUtils.isEmpty(this.streamType)) {
        keyValuePairs.add(CmcdConfiguration.KEY_STREAM_TYPE + "=" + streamType);
      }
      if (playbackRate != C.RATE_UNSET && playbackRate != 1.0f) {
        keyValuePairs.add(
            Util.formatInvariant("%s=%.2f", CmcdConfiguration.KEY_PLAYBACK_RATE, playbackRate));
      }
      if (VERSION != 1) {
        keyValuePairs.add(CmcdConfiguration.KEY_VERSION + "=" + VERSION);
      }
      keyValuePairs.addAll(customDataList);

      if (!keyValuePairs.isEmpty()) {
        cmcdDataMap.putAll(CmcdConfiguration.KEY_CMCD_SESSION, keyValuePairs);
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
      private ImmutableList<String> customDataList;

      /** Creates a new instance with default values. */
      public Builder() {
        this.maximumRequestedThroughputKbps = C.RATE_UNSET_INT;
        this.customDataList = ImmutableList.of();
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

      /** Sets the {@link CmcdStatus#customDataList}. The default value is an empty list. */
      @CanIgnoreReturnValue
      public Builder setCustomDataList(List<String> customDataList) {
        this.customDataList = ImmutableList.copyOf(customDataList);
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

    /** Custom data that do not vary with every request or object. */
    public final ImmutableList<String> customDataList;

    private CmcdStatus(Builder builder) {
      this.maximumRequestedThroughputKbps = builder.maximumRequestedThroughputKbps;
      this.bufferStarvation = builder.bufferStarvation;
      this.customDataList = builder.customDataList;
    }

    /**
     * Populates the {@code cmcdDataMap} with {@link CmcdConfiguration#KEY_CMCD_STATUS} values.
     *
     * @param cmcdDataMap An {@link ArrayListMultimap} to which CMCD data will be added.
     */
    public void populateCmcdDataMap(
        ArrayListMultimap<@CmcdConfiguration.HeaderKey String, String> cmcdDataMap) {
      ArrayList<String> keyValuePairs = new ArrayList<>();
      if (maximumRequestedThroughputKbps != C.RATE_UNSET_INT) {
        keyValuePairs.add(
            CmcdConfiguration.KEY_MAXIMUM_REQUESTED_BITRATE + "=" + maximumRequestedThroughputKbps);
      }
      if (bufferStarvation) {
        keyValuePairs.add(CmcdConfiguration.KEY_BUFFER_STARVATION);
      }
      keyValuePairs.addAll(customDataList);

      if (!keyValuePairs.isEmpty()) {
        cmcdDataMap.putAll(CmcdConfiguration.KEY_CMCD_STATUS, keyValuePairs);
      }
    }
  }
}
