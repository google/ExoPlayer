/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Representation of a media item. */
public final class MediaItem implements Bundleable {

  /**
   * Creates a {@link MediaItem} for the given URI.
   *
   * @param uri The URI.
   * @return An {@link MediaItem} for the given URI.
   */
  public static MediaItem fromUri(String uri) {
    return new MediaItem.Builder().setUri(uri).build();
  }

  /**
   * Creates a {@link MediaItem} for the given {@link Uri URI}.
   *
   * @param uri The {@link Uri uri}.
   * @return An {@link MediaItem} for the given URI.
   */
  public static MediaItem fromUri(Uri uri) {
    return new MediaItem.Builder().setUri(uri).build();
  }

  /** A builder for {@link MediaItem} instances. */
  public static final class Builder {

    @Nullable private String mediaId;
    @Nullable private Uri uri;
    @Nullable private String mimeType;
    private long clipStartPositionMs;
    private long clipEndPositionMs;
    private boolean clipRelativeToLiveWindow;
    private boolean clipRelativeToDefaultPosition;
    private boolean clipStartsAtKeyFrame;
    // TODO: Change this to @Nullable DrmConfiguration once all the deprecated individual setters
    // are removed.
    private DrmConfiguration.Builder drmConfiguration;
    private List<StreamKey> streamKeys;
    @Nullable private String customCacheKey;
    private List<Subtitle> subtitles;
    @Nullable private AdsConfiguration adsConfiguration;
    @Nullable private Object tag;
    @Nullable private MediaMetadata mediaMetadata;
    private long liveTargetOffsetMs;
    private long liveMinOffsetMs;
    private long liveMaxOffsetMs;
    private float liveMinPlaybackSpeed;
    private float liveMaxPlaybackSpeed;

    /** Creates a builder. */
    @SuppressWarnings("deprecation") // Temporarily uses DrmConfiguration.Builder() constructor.
    public Builder() {
      clipEndPositionMs = C.TIME_END_OF_SOURCE;
      drmConfiguration = new DrmConfiguration.Builder();
      streamKeys = Collections.emptyList();
      subtitles = Collections.emptyList();
      liveTargetOffsetMs = C.TIME_UNSET;
      liveMinOffsetMs = C.TIME_UNSET;
      liveMaxOffsetMs = C.TIME_UNSET;
      liveMinPlaybackSpeed = C.RATE_UNSET;
      liveMaxPlaybackSpeed = C.RATE_UNSET;
    }

    private Builder(MediaItem mediaItem) {
      this();
      clipEndPositionMs = mediaItem.clippingProperties.endPositionMs;
      clipRelativeToLiveWindow = mediaItem.clippingProperties.relativeToLiveWindow;
      clipRelativeToDefaultPosition = mediaItem.clippingProperties.relativeToDefaultPosition;
      clipStartPositionMs = mediaItem.clippingProperties.startPositionMs;
      clipStartsAtKeyFrame = mediaItem.clippingProperties.startsAtKeyFrame;
      mediaId = mediaItem.mediaId;
      mediaMetadata = mediaItem.mediaMetadata;
      liveTargetOffsetMs = mediaItem.liveConfiguration.targetOffsetMs;
      liveMinOffsetMs = mediaItem.liveConfiguration.minOffsetMs;
      liveMaxOffsetMs = mediaItem.liveConfiguration.maxOffsetMs;
      liveMinPlaybackSpeed = mediaItem.liveConfiguration.minPlaybackSpeed;
      liveMaxPlaybackSpeed = mediaItem.liveConfiguration.maxPlaybackSpeed;
      @Nullable PlaybackProperties playbackProperties = mediaItem.playbackProperties;
      if (playbackProperties != null) {
        customCacheKey = playbackProperties.customCacheKey;
        mimeType = playbackProperties.mimeType;
        uri = playbackProperties.uri;
        streamKeys = playbackProperties.streamKeys;
        subtitles = playbackProperties.subtitles;
        tag = playbackProperties.tag;
        drmConfiguration =
            playbackProperties.drmConfiguration != null
                ? playbackProperties.drmConfiguration.buildUpon()
                : new DrmConfiguration.Builder();
        adsConfiguration = playbackProperties.adsConfiguration;
      }
    }

    /**
     * Sets the optional media ID which identifies the media item.
     *
     * <p>By default {@link #DEFAULT_MEDIA_ID} is used.
     */
    public Builder setMediaId(String mediaId) {
      this.mediaId = checkNotNull(mediaId);
      return this;
    }

    /**
     * Sets the optional URI.
     *
     * <p>If {@code uri} is null or unset then no {@link PlaybackProperties} object is created
     * during {@link #build()} and no other {@code Builder} methods that would populate {@link
     * MediaItem#playbackProperties} should be called.
     */
    public Builder setUri(@Nullable String uri) {
      return setUri(uri == null ? null : Uri.parse(uri));
    }

    /**
     * Sets the optional URI.
     *
     * <p>If {@code uri} is null or unset then no {@link PlaybackProperties} object is created
     * during {@link #build()} and no other {@code Builder} methods that would populate {@link
     * MediaItem#playbackProperties} should be called.
     */
    public Builder setUri(@Nullable Uri uri) {
      this.uri = uri;
      return this;
    }

    /**
     * Sets the optional MIME type.
     *
     * <p>The MIME type may be used as a hint for inferring the type of the media item.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     *
     * @param mimeType The MIME type.
     */
    public Builder setMimeType(@Nullable String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /**
     * Sets the optional start position in milliseconds which must be a value larger than or equal
     * to zero (Default: 0).
     */
    public Builder setClipStartPositionMs(@IntRange(from = 0) long startPositionMs) {
      Assertions.checkArgument(startPositionMs >= 0);
      this.clipStartPositionMs = startPositionMs;
      return this;
    }

    /**
     * Sets the optional end position in milliseconds which must be a value larger than or equal to
     * zero, or {@link C#TIME_END_OF_SOURCE} to end when playback reaches the end of media (Default:
     * {@link C#TIME_END_OF_SOURCE}).
     */
    public Builder setClipEndPositionMs(long endPositionMs) {
      Assertions.checkArgument(endPositionMs == C.TIME_END_OF_SOURCE || endPositionMs >= 0);
      this.clipEndPositionMs = endPositionMs;
      return this;
    }

    /**
     * Sets whether the start/end positions should move with the live window for live streams. If
     * {@code false}, live streams end when playback reaches the end position in live window seen
     * when the media is first loaded (Default: {@code false}).
     */
    public Builder setClipRelativeToLiveWindow(boolean relativeToLiveWindow) {
      this.clipRelativeToLiveWindow = relativeToLiveWindow;
      return this;
    }

    /**
     * Sets whether the start position and the end position are relative to the default position in
     * the window (Default: {@code false}).
     */
    public Builder setClipRelativeToDefaultPosition(boolean relativeToDefaultPosition) {
      this.clipRelativeToDefaultPosition = relativeToDefaultPosition;
      return this;
    }

    /**
     * Sets whether the start point is guaranteed to be a key frame. If {@code false}, the playback
     * transition into the clip may not be seamless (Default: {@code false}).
     */
    public Builder setClipStartsAtKeyFrame(boolean startsAtKeyFrame) {
      this.clipStartsAtKeyFrame = startsAtKeyFrame;
      return this;
    }

    /** Sets the optional DRM configuration. */
    public Builder setDrmConfiguration(@Nullable DrmConfiguration drmConfiguration) {
      this.drmConfiguration =
          drmConfiguration != null ? drmConfiguration.buildUpon() : new DrmConfiguration.Builder();
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseUri(Uri)} instead.
     */
    @Deprecated
    public Builder setDrmLicenseUri(@Nullable Uri licenseUri) {
      drmConfiguration.setLicenseUri(licenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseUri(String)} instead.
     */
    @Deprecated
    public Builder setDrmLicenseUri(@Nullable String licenseUri) {
      drmConfiguration.setLicenseUri(licenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setLicenseRequestHeaders(Map)} instead.
     */
    @Deprecated
    public Builder setDrmLicenseRequestHeaders(
        @Nullable Map<String, String> licenseRequestHeaders) {
      drmConfiguration.setLicenseRequestHeaders(licenseRequestHeaders);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and pass the {@code uuid} to
     *     {@link DrmConfiguration.Builder#Builder(UUID)} instead.
     */
    @Deprecated
    public Builder setDrmUuid(@Nullable UUID uuid) {
      drmConfiguration.setNullableUuid(uuid);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setMultiSession(boolean)} instead.
     */
    @Deprecated
    public Builder setDrmMultiSession(boolean multiSession) {
      drmConfiguration.setMultiSession(multiSession);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setForceDefaultLicenseUri(boolean)} instead.
     */
    @Deprecated
    public Builder setDrmForceDefaultLicenseUri(boolean forceDefaultLicenseUri) {
      drmConfiguration.setForceDefaultLicenseUri(forceDefaultLicenseUri);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setPlayClearContentWithoutKey(boolean)} instead.
     */
    @Deprecated
    public Builder setDrmPlayClearContentWithoutKey(boolean playClearContentWithoutKey) {
      drmConfiguration.setPlayClearContentWithoutKey(playClearContentWithoutKey);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setSessionForClearPeriods(boolean)} instead.
     */
    @Deprecated
    public Builder setDrmSessionForClearPeriods(boolean sessionForClearPeriods) {
      drmConfiguration.setSessionForClearPeriods(sessionForClearPeriods);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setSessionForClearTypes(List)} instead.
     */
    @Deprecated
    public Builder setDrmSessionForClearTypes(
        @Nullable List<@C.TrackType Integer> sessionForClearTypes) {
      drmConfiguration.setSessionForClearTypes(sessionForClearTypes);
      return this;
    }

    /**
     * @deprecated Use {@link #setDrmConfiguration(DrmConfiguration)} and {@link
     *     DrmConfiguration.Builder#setKeySetId(byte[])} instead.
     */
    @Deprecated
    public Builder setDrmKeySetId(@Nullable byte[] keySetId) {
      drmConfiguration.setKeySetId(keySetId);
      return this;
    }

    /**
     * Sets the optional stream keys by which the manifest is filtered (only used for adaptive
     * streams).
     *
     * <p>{@code null} or an empty {@link List} can be used for a reset.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the stream keys are used to create a
     * {@link PlaybackProperties} object. Otherwise they will be ignored.
     */
    public Builder setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys =
          streamKeys != null && !streamKeys.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(streamKeys))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the optional custom cache key (only used for progressive streams).
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    public Builder setCustomCacheKey(@Nullable String customCacheKey) {
      this.customCacheKey = customCacheKey;
      return this;
    }

    /**
     * Sets the optional subtitles.
     *
     * <p>{@code null} or an empty {@link List} can be used for a reset.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    public Builder setSubtitles(@Nullable List<Subtitle> subtitles) {
      this.subtitles =
          subtitles != null && !subtitles.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(subtitles))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the optional {@link AdsConfiguration}.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    public Builder setAdsConfiguration(@Nullable AdsConfiguration adsConfiguration) {
      this.adsConfiguration = adsConfiguration;
      return this;
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)}, parse the {@code adTagUri}
     *     with {@link Uri#parse(String)} and pass the result to {@link
     *     AdsConfiguration.Builder#Builder(Uri)} instead.
     */
    @Deprecated
    public Builder setAdTagUri(@Nullable String adTagUri) {
      return setAdTagUri(adTagUri != null ? Uri.parse(adTagUri) : null);
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)} and pass the {@code adTagUri}
     *     to {@link AdsConfiguration.Builder#Builder(Uri)} instead.
     */
    @Deprecated
    public Builder setAdTagUri(@Nullable Uri adTagUri) {
      return setAdTagUri(adTagUri, /* adsId= */ null);
    }

    /**
     * @deprecated Use {@link #setAdsConfiguration(AdsConfiguration)}, pass the {@code adTagUri} to
     *     {@link AdsConfiguration.Builder#Builder(Uri)} and the {@code adsId} to {@link
     *     AdsConfiguration.Builder#setAdsId(Object)} instead.
     */
    @Deprecated
    public Builder setAdTagUri(@Nullable Uri adTagUri, @Nullable Object adsId) {
      this.adsConfiguration =
          adTagUri != null ? new AdsConfiguration.Builder(adTagUri).setAdsId(adsId).build() : null;
      return this;
    }

    /**
     * Sets the optional target offset from the live edge for live streams, in milliseconds.
     *
     * <p>See {@code Player#getCurrentLiveOffset()}.
     *
     * @param liveTargetOffsetMs The target offset, in milliseconds, or {@link C#TIME_UNSET} to use
     *     the media-defined default.
     */
    public Builder setLiveTargetOffsetMs(long liveTargetOffsetMs) {
      this.liveTargetOffsetMs = liveTargetOffsetMs;
      return this;
    }

    /**
     * Sets the optional minimum offset from the live edge for live streams, in milliseconds.
     *
     * <p>See {@code Player#getCurrentLiveOffset()}.
     *
     * @param liveMinOffsetMs The minimum allowed offset, in milliseconds, or {@link C#TIME_UNSET}
     *     to use the media-defined default.
     */
    public Builder setLiveMinOffsetMs(long liveMinOffsetMs) {
      this.liveMinOffsetMs = liveMinOffsetMs;
      return this;
    }

    /**
     * Sets the optional maximum offset from the live edge for live streams, in milliseconds.
     *
     * <p>See {@code Player#getCurrentLiveOffset()}.
     *
     * @param liveMaxOffsetMs The maximum allowed offset, in milliseconds, or {@link C#TIME_UNSET}
     *     to use the media-defined default.
     */
    public Builder setLiveMaxOffsetMs(long liveMaxOffsetMs) {
      this.liveMaxOffsetMs = liveMaxOffsetMs;
      return this;
    }

    /**
     * Sets the optional minimum playback speed for live stream speed adjustment.
     *
     * <p>This value is ignored for other stream types.
     *
     * @param minPlaybackSpeed The minimum factor by which playback can be sped up for live streams,
     *     or {@link C#RATE_UNSET} to use the media-defined default.
     */
    public Builder setLiveMinPlaybackSpeed(float minPlaybackSpeed) {
      this.liveMinPlaybackSpeed = minPlaybackSpeed;
      return this;
    }

    /**
     * Sets the optional maximum playback speed for live stream speed adjustment.
     *
     * <p>This value is ignored for other stream types.
     *
     * @param maxPlaybackSpeed The maximum factor by which playback can be sped up for live streams,
     *     or {@link C#RATE_UNSET} to use the media-defined default.
     */
    public Builder setLiveMaxPlaybackSpeed(float maxPlaybackSpeed) {
      this.liveMaxPlaybackSpeed = maxPlaybackSpeed;
      return this;
    }

    /**
     * Sets the optional tag for custom attributes. The tag for the media source which will be
     * published in the {@code com.google.android.exoplayer2.Timeline} of the source as {@code
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     *
     * <p>This method should only be called if {@link #setUri} is passed a non-null value.
     */
    public Builder setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /** Sets the media metadata. */
    public Builder setMediaMetadata(MediaMetadata mediaMetadata) {
      this.mediaMetadata = mediaMetadata;
      return this;
    }

    /** Returns a new {@link MediaItem} instance with the current builder values. */
    public MediaItem build() {
      // TODO: remove this check once all the deprecated individual DRM setters are removed.
      checkState(drmConfiguration.licenseUri == null || drmConfiguration.uuid != null);
      @Nullable PlaybackProperties playbackProperties = null;
      @Nullable Uri uri = this.uri;
      if (uri != null) {
        playbackProperties =
            new PlaybackProperties(
                uri,
                mimeType,
                drmConfiguration.uuid != null ? drmConfiguration.build() : null,
                adsConfiguration,
                streamKeys,
                customCacheKey,
                subtitles,
                tag);
      }
      return new MediaItem(
          mediaId != null ? mediaId : DEFAULT_MEDIA_ID,
          new ClippingProperties(
              clipStartPositionMs,
              clipEndPositionMs,
              clipRelativeToLiveWindow,
              clipRelativeToDefaultPosition,
              clipStartsAtKeyFrame),
          playbackProperties,
          new LiveConfiguration(
              liveTargetOffsetMs,
              liveMinOffsetMs,
              liveMaxOffsetMs,
              liveMinPlaybackSpeed,
              liveMaxPlaybackSpeed),
          mediaMetadata != null ? mediaMetadata : MediaMetadata.EMPTY);
    }
  }

  /** DRM configuration for a media item. */
  public static final class DrmConfiguration {

    /** Builder for {@link DrmConfiguration}. */
    public static final class Builder {

      // TODO remove @Nullable annotation when the deprecated zero-arg constructor is removed.
      @Nullable private UUID uuid;
      @Nullable private Uri licenseUri;
      private ImmutableMap<String, String> licenseRequestHeaders;
      private boolean multiSession;
      private boolean playClearContentWithoutKey;
      private boolean forceDefaultLicenseUri;
      private ImmutableList<@C.TrackType Integer> sessionForClearTypes;
      @Nullable private byte[] keySetId;

      /**
       * Constructs an instance.
       *
       * @param uuid The {@link UUID} of the protection scheme.
       */
      public Builder(UUID uuid) {
        this.uuid = uuid;
        this.licenseRequestHeaders = ImmutableMap.of();
        this.sessionForClearTypes = ImmutableList.of();
      }

      /**
       * @deprecated This only exists to support the deprecated setters for individual DRM
       *     properties on {@link MediaItem.Builder}.
       */
      @Deprecated
      private Builder() {
        this.licenseRequestHeaders = ImmutableMap.of();
        this.sessionForClearTypes = ImmutableList.of();
      }

      private Builder(DrmConfiguration drmConfiguration) {
        this.uuid = drmConfiguration.uuid;
        this.licenseUri = drmConfiguration.licenseUri;
        this.licenseRequestHeaders = drmConfiguration.requestHeaders;
        this.multiSession = drmConfiguration.multiSession;
        this.playClearContentWithoutKey = drmConfiguration.playClearContentWithoutKey;
        this.forceDefaultLicenseUri = drmConfiguration.forceDefaultLicenseUri;
        this.sessionForClearTypes = drmConfiguration.sessionForClearTypes;
        this.keySetId = drmConfiguration.keySetId;
      }

      /** Sets the {@link UUID} of the protection scheme. */
      public Builder setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
      }

      /**
       * @deprecated This only exists to support the deprecated {@link
       *     MediaItem.Builder#setDrmUuid(UUID)}.
       */
      @Deprecated
      private Builder setNullableUuid(@Nullable UUID uuid) {
        this.uuid = uuid;
        return this;
      }

      /** Sets the optional default DRM license server URI. */
      public Builder setLicenseUri(@Nullable Uri licenseUri) {
        this.licenseUri = licenseUri;
        return this;
      }

      /** Sets the optional default DRM license server URI. */
      public Builder setLicenseUri(@Nullable String licenseUri) {
        this.licenseUri = licenseUri == null ? null : Uri.parse(licenseUri);
        return this;
      }

      /** Sets the optional request headers attached to DRM license requests. */
      public Builder setLicenseRequestHeaders(@Nullable Map<String, String> licenseRequestHeaders) {
        this.licenseRequestHeaders =
            licenseRequestHeaders != null
                ? ImmutableMap.copyOf(licenseRequestHeaders)
                : ImmutableMap.of();
        return this;
      }

      /** Sets whether multi session is enabled. */
      public Builder setMultiSession(boolean multiSession) {
        this.multiSession = multiSession;
        return this;
      }

      /**
       * Sets whether to always use the default DRM license server URI even if the media specifies
       * its own DRM license server URI.
       */
      public Builder setForceDefaultLicenseUri(boolean forceDefaultLicenseUri) {
        this.forceDefaultLicenseUri = forceDefaultLicenseUri;
        return this;
      }

      /**
       * Sets whether clear samples within protected content should be played when keys for the
       * encrypted part of the content have yet to be loaded.
       */
      public Builder setPlayClearContentWithoutKey(boolean playClearContentWithoutKey) {
        this.playClearContentWithoutKey = playClearContentWithoutKey;
        return this;
      }

      /**
       * Sets whether a DRM session should be used for clear tracks of type {@link
       * C#TRACK_TYPE_VIDEO} and {@link C#TRACK_TYPE_AUDIO}.
       *
       * <p>This method overrides what has been set by previously calling {@link
       * #setSessionForClearTypes(List)}.
       */
      public Builder setSessionForClearPeriods(boolean sessionForClearPeriods) {
        this.setSessionForClearTypes(
            sessionForClearPeriods
                ? ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
                : ImmutableList.of());
        return this;
      }

      /**
       * Sets a list of {@link C.TrackType track type} constants for which to use a DRM session even
       * when the tracks are in the clear.
       *
       * <p>For the common case of using a DRM session for {@link C#TRACK_TYPE_VIDEO} and {@link
       * C#TRACK_TYPE_AUDIO}, {@link #setSessionForClearPeriods(boolean)} can be used.
       *
       * <p>This method overrides what has been set by previously calling {@link
       * #setSessionForClearPeriods(boolean)}.
       *
       * <p>{@code null} or an empty {@link List} can be used for a reset.
       */
      public Builder setSessionForClearTypes(
          @Nullable List<@C.TrackType Integer> sessionForClearTypes) {
        this.sessionForClearTypes =
            sessionForClearTypes != null
                ? ImmutableList.copyOf(sessionForClearTypes)
                : ImmutableList.of();
        return this;
      }

      /**
       * Sets the key set ID of the offline license.
       *
       * <p>The key set ID identifies an offline license. The ID is required to query, renew or
       * release an existing offline license (see {@code DefaultDrmSessionManager#setMode(int
       * mode,byte[] offlineLicenseKeySetId)}).
       */
      public Builder setKeySetId(@Nullable byte[] keySetId) {
        this.keySetId = keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
        return this;
      }

      public DrmConfiguration build() {

        return new DrmConfiguration(this);
      }
    }

    /** The UUID of the protection scheme. */
    public final UUID uuid;

    /**
     * Optional default DRM license server {@link Uri}. If {@code null} then the DRM license server
     * must be specified by the media.
     */
    @Nullable public final Uri licenseUri;

    /** The headers to attach to the request to the DRM license server. */
    public final ImmutableMap<String, String> requestHeaders;

    /** Whether the DRM configuration is multi session enabled. */
    public final boolean multiSession;

    /**
     * Whether clear samples within protected content should be played when keys for the encrypted
     * part of the content have yet to be loaded.
     */
    public final boolean playClearContentWithoutKey;

    /**
     * Whether to force use of {@link #licenseUri} even if the media specifies its own DRM license
     * server URI.
     */
    public final boolean forceDefaultLicenseUri;

    /** The types of clear tracks for which to use a DRM session. */
    public final ImmutableList<@C.TrackType Integer> sessionForClearTypes;

    @Nullable private final byte[] keySetId;

    private DrmConfiguration(Builder builder) {
      checkState(!(builder.forceDefaultLicenseUri && builder.licenseUri == null));
      this.uuid = checkNotNull(builder.uuid);
      this.licenseUri = builder.licenseUri;
      this.requestHeaders = builder.licenseRequestHeaders;
      this.multiSession = builder.multiSession;
      this.forceDefaultLicenseUri = builder.forceDefaultLicenseUri;
      this.playClearContentWithoutKey = builder.playClearContentWithoutKey;
      this.sessionForClearTypes = builder.sessionForClearTypes;
      this.keySetId =
          builder.keySetId != null
              ? Arrays.copyOf(builder.keySetId, builder.keySetId.length)
              : null;
    }

    /** Returns the key set ID of the offline license. */
    @Nullable
    public byte[] getKeySetId() {
      return keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof DrmConfiguration)) {
        return false;
      }

      DrmConfiguration other = (DrmConfiguration) obj;
      return uuid.equals(other.uuid)
          && Util.areEqual(licenseUri, other.licenseUri)
          && Util.areEqual(requestHeaders, other.requestHeaders)
          && multiSession == other.multiSession
          && forceDefaultLicenseUri == other.forceDefaultLicenseUri
          && playClearContentWithoutKey == other.playClearContentWithoutKey
          && sessionForClearTypes.equals(other.sessionForClearTypes)
          && Arrays.equals(keySetId, other.keySetId);
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + (licenseUri != null ? licenseUri.hashCode() : 0);
      result = 31 * result + requestHeaders.hashCode();
      result = 31 * result + (multiSession ? 1 : 0);
      result = 31 * result + (forceDefaultLicenseUri ? 1 : 0);
      result = 31 * result + (playClearContentWithoutKey ? 1 : 0);
      result = 31 * result + sessionForClearTypes.hashCode();
      result = 31 * result + Arrays.hashCode(keySetId);
      return result;
    }
  }

  /** Configuration for playing back linear ads with a media item. */
  public static final class AdsConfiguration {

    /** Builder for {@link AdsConfiguration} instances. */
    public static final class Builder {

      private Uri adTagUri;
      @Nullable private Object adsId;

      /**
       * Constructs a new instance.
       *
       * @param adTagUri The ad tag URI to load.
       */
      public Builder(Uri adTagUri) {
        this.adTagUri = adTagUri;
      }

      /** Sets the ad tag URI to load. */
      public Builder setAdTagUri(Uri adTagUri) {
        this.adTagUri = adTagUri;
        return this;
      }

      /**
       * Sets the ads identifier.
       *
       * <p>See details on {@link AdsConfiguration#adsId} for how the ads identifier is used and how
       * it's calculated if not explicitly set.
       */
      public Builder setAdsId(@Nullable Object adsId) {
        this.adsId = adsId;
        return this;
      }

      public AdsConfiguration build() {
        return new AdsConfiguration(this);
      }
    }

    /** The ad tag URI to load. */
    public final Uri adTagUri;

    /**
     * An opaque identifier for ad playback state associated with this item, or {@code null} if the
     * combination of the {@link MediaItem.Builder#setMediaId(String) media ID} and {@link #adTagUri
     * ad tag URI} should be used as the ads identifier.
     *
     * <p>Media items in the playlist that have the same ads identifier and ads loader share the
     * same ad playback state. To resume ad playback when recreating the playlist on returning from
     * the background, pass the same ads identifiers to the player.
     */
    @Nullable public final Object adsId;

    private AdsConfiguration(Builder builder) {
      this.adTagUri = builder.adTagUri;
      this.adsId = builder.adsId;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(adTagUri).setAdsId(adsId);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof AdsConfiguration)) {
        return false;
      }

      AdsConfiguration other = (AdsConfiguration) obj;
      return adTagUri.equals(other.adTagUri) && Util.areEqual(adsId, other.adsId);
    }

    @Override
    public int hashCode() {
      int result = adTagUri.hashCode();
      result = 31 * result + (adsId != null ? adsId.hashCode() : 0);
      return result;
    }
  }

  /** Properties for local playback. */
  public static final class PlaybackProperties {

    /** The {@link Uri}. */
    public final Uri uri;

    /**
     * The optional MIME type of the item, or {@code null} if unspecified.
     *
     * <p>The MIME type can be used to disambiguate media items that have a URI which does not allow
     * to infer the actual media type.
     */
    @Nullable public final String mimeType;

    /** Optional {@link DrmConfiguration} for the media. */
    @Nullable public final DrmConfiguration drmConfiguration;

    /** Optional ads configuration. */
    @Nullable public final AdsConfiguration adsConfiguration;

    /** Optional stream keys by which the manifest is filtered. */
    public final List<StreamKey> streamKeys;

    /** Optional custom cache key (only used for progressive streams). */
    @Nullable public final String customCacheKey;

    /** Optional subtitles to be sideloaded. */
    public final List<Subtitle> subtitles;

    /**
     * Optional tag for custom attributes. The tag for the media source which will be published in
     * the {@code com.google.android.exoplayer2.Timeline} of the source as {@code
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     */
    @Nullable public final Object tag;

    private PlaybackProperties(
        Uri uri,
        @Nullable String mimeType,
        @Nullable DrmConfiguration drmConfiguration,
        @Nullable AdsConfiguration adsConfiguration,
        List<StreamKey> streamKeys,
        @Nullable String customCacheKey,
        List<Subtitle> subtitles,
        @Nullable Object tag) {
      this.uri = uri;
      this.mimeType = mimeType;
      this.drmConfiguration = drmConfiguration;
      this.adsConfiguration = adsConfiguration;
      this.streamKeys = streamKeys;
      this.customCacheKey = customCacheKey;
      this.subtitles = subtitles;
      this.tag = tag;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof PlaybackProperties)) {
        return false;
      }
      PlaybackProperties other = (PlaybackProperties) obj;

      return uri.equals(other.uri)
          && Util.areEqual(mimeType, other.mimeType)
          && Util.areEqual(drmConfiguration, other.drmConfiguration)
          && Util.areEqual(adsConfiguration, other.adsConfiguration)
          && streamKeys.equals(other.streamKeys)
          && Util.areEqual(customCacheKey, other.customCacheKey)
          && subtitles.equals(other.subtitles)
          && Util.areEqual(tag, other.tag);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
      result = 31 * result + (drmConfiguration == null ? 0 : drmConfiguration.hashCode());
      result = 31 * result + (adsConfiguration == null ? 0 : adsConfiguration.hashCode());
      result = 31 * result + streamKeys.hashCode();
      result = 31 * result + (customCacheKey == null ? 0 : customCacheKey.hashCode());
      result = 31 * result + subtitles.hashCode();
      result = 31 * result + (tag == null ? 0 : tag.hashCode());
      return result;
    }
  }

  /** Live playback configuration. */
  public static final class LiveConfiguration implements Bundleable {

    /** A live playback configuration with unset values. */
    public static final LiveConfiguration UNSET =
        new LiveConfiguration(
            /* targetLiveOffsetMs= */ C.TIME_UNSET,
            /* minLiveOffsetMs= */ C.TIME_UNSET,
            /* maxLiveOffsetMs= */ C.TIME_UNSET,
            /* minPlaybackSpeed= */ C.RATE_UNSET,
            /* maxPlaybackSpeed= */ C.RATE_UNSET);

    /**
     * Target offset from the live edge, in milliseconds, or {@link C#TIME_UNSET} to use the
     * media-defined default.
     */
    public final long targetOffsetMs;

    /**
     * The minimum allowed offset from the live edge, in milliseconds, or {@link C#TIME_UNSET} to
     * use the media-defined default.
     */
    public final long minOffsetMs;

    /**
     * The maximum allowed offset from the live edge, in milliseconds, or {@link C#TIME_UNSET} to
     * use the media-defined default.
     */
    public final long maxOffsetMs;

    /**
     * Minimum factor by which playback can be sped up, or {@link C#RATE_UNSET} to use the
     * media-defined default.
     */
    public final float minPlaybackSpeed;

    /**
     * Maximum factor by which playback can be sped up, or {@link C#RATE_UNSET} to use the
     * media-defined default.
     */
    public final float maxPlaybackSpeed;

    /**
     * Creates a live playback configuration.
     *
     * @param targetOffsetMs Target live offset, in milliseconds, or {@link C#TIME_UNSET} to use the
     *     media-defined default.
     * @param minOffsetMs The minimum allowed live offset, in milliseconds, or {@link C#TIME_UNSET}
     *     to use the media-defined default.
     * @param maxOffsetMs The maximum allowed live offset, in milliseconds, or {@link C#TIME_UNSET}
     *     to use the media-defined default.
     * @param minPlaybackSpeed Minimum playback speed, or {@link C#RATE_UNSET} to use the
     *     media-defined default.
     * @param maxPlaybackSpeed Maximum playback speed, or {@link C#RATE_UNSET} to use the
     *     media-defined default.
     */
    public LiveConfiguration(
        long targetOffsetMs,
        long minOffsetMs,
        long maxOffsetMs,
        float minPlaybackSpeed,
        float maxPlaybackSpeed) {
      this.targetOffsetMs = targetOffsetMs;
      this.minOffsetMs = minOffsetMs;
      this.maxOffsetMs = maxOffsetMs;
      this.minPlaybackSpeed = minPlaybackSpeed;
      this.maxPlaybackSpeed = maxPlaybackSpeed;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof LiveConfiguration)) {
        return false;
      }
      LiveConfiguration other = (LiveConfiguration) obj;

      return targetOffsetMs == other.targetOffsetMs
          && minOffsetMs == other.minOffsetMs
          && maxOffsetMs == other.maxOffsetMs
          && minPlaybackSpeed == other.minPlaybackSpeed
          && maxPlaybackSpeed == other.maxPlaybackSpeed;
    }

    @Override
    public int hashCode() {
      int result = (int) (targetOffsetMs ^ (targetOffsetMs >>> 32));
      result = 31 * result + (int) (minOffsetMs ^ (minOffsetMs >>> 32));
      result = 31 * result + (int) (maxOffsetMs ^ (maxOffsetMs >>> 32));
      result = 31 * result + (minPlaybackSpeed != 0 ? Float.floatToIntBits(minPlaybackSpeed) : 0);
      result = 31 * result + (maxPlaybackSpeed != 0 ? Float.floatToIntBits(maxPlaybackSpeed) : 0);
      return result;
    }

    // Bundleable implementation.

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_TARGET_OFFSET_MS,
      FIELD_MIN_OFFSET_MS,
      FIELD_MAX_OFFSET_MS,
      FIELD_MIN_PLAYBACK_SPEED,
      FIELD_MAX_PLAYBACK_SPEED
    })
    private @interface FieldNumber {}

    private static final int FIELD_TARGET_OFFSET_MS = 0;
    private static final int FIELD_MIN_OFFSET_MS = 1;
    private static final int FIELD_MAX_OFFSET_MS = 2;
    private static final int FIELD_MIN_PLAYBACK_SPEED = 3;
    private static final int FIELD_MAX_PLAYBACK_SPEED = 4;

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putLong(keyForField(FIELD_TARGET_OFFSET_MS), targetOffsetMs);
      bundle.putLong(keyForField(FIELD_MIN_OFFSET_MS), minOffsetMs);
      bundle.putLong(keyForField(FIELD_MAX_OFFSET_MS), maxOffsetMs);
      bundle.putFloat(keyForField(FIELD_MIN_PLAYBACK_SPEED), minPlaybackSpeed);
      bundle.putFloat(keyForField(FIELD_MAX_PLAYBACK_SPEED), maxPlaybackSpeed);
      return bundle;
    }

    /** Object that can restore {@link LiveConfiguration} from a {@link Bundle}. */
    public static final Creator<LiveConfiguration> CREATOR =
        bundle ->
            new LiveConfiguration(
                bundle.getLong(
                    keyForField(FIELD_TARGET_OFFSET_MS), /* defaultValue= */ C.TIME_UNSET),
                bundle.getLong(keyForField(FIELD_MIN_OFFSET_MS), /* defaultValue= */ C.TIME_UNSET),
                bundle.getLong(keyForField(FIELD_MAX_OFFSET_MS), /* defaultValue= */ C.TIME_UNSET),
                bundle.getFloat(
                    keyForField(FIELD_MIN_PLAYBACK_SPEED), /* defaultValue= */ C.RATE_UNSET),
                bundle.getFloat(
                    keyForField(FIELD_MAX_PLAYBACK_SPEED), /* defaultValue= */ C.RATE_UNSET));

    private static String keyForField(@LiveConfiguration.FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
  }

  /** Properties for a text track. */
  public static final class Subtitle {

    /** The {@link Uri} to the subtitle file. */
    public final Uri uri;
    /** The MIME type. */
    public final String mimeType;
    /** The language. */
    @Nullable public final String language;
    /** The selection flags. */
    @C.SelectionFlags public final int selectionFlags;
    /** The role flags. */
    @C.RoleFlags public final int roleFlags;
    /** The label. */
    @Nullable public final String label;

    /**
     * Creates an instance.
     *
     * @param uri The {@link Uri URI} to the subtitle file.
     * @param mimeType The MIME type.
     * @param language The optional language.
     */
    public Subtitle(Uri uri, String mimeType, @Nullable String language) {
      this(uri, mimeType, language, /* selectionFlags= */ 0);
    }

    /**
     * Creates an instance.
     *
     * @param uri The {@link Uri URI} to the subtitle file.
     * @param mimeType The MIME type.
     * @param language The optional language.
     * @param selectionFlags The selection flags.
     */
    public Subtitle(
        Uri uri, String mimeType, @Nullable String language, @C.SelectionFlags int selectionFlags) {
      this(uri, mimeType, language, selectionFlags, /* roleFlags= */ 0, /* label= */ null);
    }

    /**
     * Creates an instance.
     *
     * @param uri The {@link Uri URI} to the subtitle file.
     * @param mimeType The MIME type.
     * @param language The optional language.
     * @param selectionFlags The selection flags.
     * @param roleFlags The role flags.
     * @param label The optional label.
     */
    public Subtitle(
        Uri uri,
        String mimeType,
        @Nullable String language,
        @C.SelectionFlags int selectionFlags,
        @C.RoleFlags int roleFlags,
        @Nullable String label) {
      this.uri = uri;
      this.mimeType = mimeType;
      this.language = language;
      this.selectionFlags = selectionFlags;
      this.roleFlags = roleFlags;
      this.label = label;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Subtitle)) {
        return false;
      }

      Subtitle other = (Subtitle) obj;

      return uri.equals(other.uri)
          && mimeType.equals(other.mimeType)
          && Util.areEqual(language, other.language)
          && selectionFlags == other.selectionFlags
          && roleFlags == other.roleFlags
          && Util.areEqual(label, other.label);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + mimeType.hashCode();
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + selectionFlags;
      result = 31 * result + roleFlags;
      result = 31 * result + (label == null ? 0 : label.hashCode());
      return result;
    }
  }

  /** Optionally clips the media item to a custom start and end position. */
  public static final class ClippingProperties implements Bundleable {

    /** The start position in milliseconds. This is a value larger than or equal to zero. */
    public final long startPositionMs;

    /**
     * The end position in milliseconds. This is a value larger than or equal to zero or {@link
     * C#TIME_END_OF_SOURCE} to play to the end of the stream.
     */
    public final long endPositionMs;

    /**
     * Whether the clipping of active media periods moves with a live window. If {@code false},
     * playback ends when it reaches {@link #endPositionMs}.
     */
    public final boolean relativeToLiveWindow;

    /**
     * Whether {@link #startPositionMs} and {@link #endPositionMs} are relative to the default
     * position.
     */
    public final boolean relativeToDefaultPosition;

    /** Sets whether the start point is guaranteed to be a key frame. */
    public final boolean startsAtKeyFrame;

    private ClippingProperties(
        long startPositionMs,
        long endPositionMs,
        boolean relativeToLiveWindow,
        boolean relativeToDefaultPosition,
        boolean startsAtKeyFrame) {
      this.startPositionMs = startPositionMs;
      this.endPositionMs = endPositionMs;
      this.relativeToLiveWindow = relativeToLiveWindow;
      this.relativeToDefaultPosition = relativeToDefaultPosition;
      this.startsAtKeyFrame = startsAtKeyFrame;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ClippingProperties)) {
        return false;
      }

      ClippingProperties other = (ClippingProperties) obj;

      return startPositionMs == other.startPositionMs
          && endPositionMs == other.endPositionMs
          && relativeToLiveWindow == other.relativeToLiveWindow
          && relativeToDefaultPosition == other.relativeToDefaultPosition
          && startsAtKeyFrame == other.startsAtKeyFrame;
    }

    @Override
    public int hashCode() {
      int result = (int) (startPositionMs ^ (startPositionMs >>> 32));
      result = 31 * result + (int) (endPositionMs ^ (endPositionMs >>> 32));
      result = 31 * result + (relativeToLiveWindow ? 1 : 0);
      result = 31 * result + (relativeToDefaultPosition ? 1 : 0);
      result = 31 * result + (startsAtKeyFrame ? 1 : 0);
      return result;
    }

    // Bundleable implementation.

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      FIELD_START_POSITION_MS,
      FIELD_END_POSITION_MS,
      FIELD_RELATIVE_TO_LIVE_WINDOW,
      FIELD_RELATIVE_TO_DEFAULT_POSITION,
      FIELD_STARTS_AT_KEY_FRAME
    })
    private @interface FieldNumber {}

    private static final int FIELD_START_POSITION_MS = 0;
    private static final int FIELD_END_POSITION_MS = 1;
    private static final int FIELD_RELATIVE_TO_LIVE_WINDOW = 2;
    private static final int FIELD_RELATIVE_TO_DEFAULT_POSITION = 3;
    private static final int FIELD_STARTS_AT_KEY_FRAME = 4;

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putLong(keyForField(FIELD_START_POSITION_MS), startPositionMs);
      bundle.putLong(keyForField(FIELD_END_POSITION_MS), endPositionMs);
      bundle.putBoolean(keyForField(FIELD_RELATIVE_TO_LIVE_WINDOW), relativeToLiveWindow);
      bundle.putBoolean(keyForField(FIELD_RELATIVE_TO_DEFAULT_POSITION), relativeToDefaultPosition);
      bundle.putBoolean(keyForField(FIELD_STARTS_AT_KEY_FRAME), startsAtKeyFrame);
      return bundle;
    }

    /** Object that can restore {@link ClippingProperties} from a {@link Bundle}. */
    public static final Creator<ClippingProperties> CREATOR =
        bundle ->
            new ClippingProperties(
                bundle.getLong(keyForField(FIELD_START_POSITION_MS), /* defaultValue= */ 0),
                bundle.getLong(
                    keyForField(FIELD_END_POSITION_MS), /* defaultValue= */ C.TIME_END_OF_SOURCE),
                bundle.getBoolean(keyForField(FIELD_RELATIVE_TO_LIVE_WINDOW), false),
                bundle.getBoolean(keyForField(FIELD_RELATIVE_TO_DEFAULT_POSITION), false),
                bundle.getBoolean(keyForField(FIELD_STARTS_AT_KEY_FRAME), false));

    private static String keyForField(@ClippingProperties.FieldNumber int field) {
      return Integer.toString(field, Character.MAX_RADIX);
    }
  }

  /**
   * The default media ID that is used if the media ID is not explicitly set by {@link
   * Builder#setMediaId(String)}.
   */
  public static final String DEFAULT_MEDIA_ID = "";

  /** Empty {@link MediaItem}. */
  public static final MediaItem EMPTY = new MediaItem.Builder().build();

  /** Identifies the media item. */
  public final String mediaId;

  /** Optional playback properties. May be {@code null} if shared over process boundaries. */
  @Nullable public final PlaybackProperties playbackProperties;

  /** The live playback configuration. */
  public final LiveConfiguration liveConfiguration;

  /** The media metadata. */
  public final MediaMetadata mediaMetadata;

  /** The clipping properties. */
  public final ClippingProperties clippingProperties;

  private MediaItem(
      String mediaId,
      ClippingProperties clippingProperties,
      @Nullable PlaybackProperties playbackProperties,
      LiveConfiguration liveConfiguration,
      MediaMetadata mediaMetadata) {
    this.mediaId = mediaId;
    this.playbackProperties = playbackProperties;
    this.liveConfiguration = liveConfiguration;
    this.mediaMetadata = mediaMetadata;
    this.clippingProperties = clippingProperties;
  }

  /** Returns a {@link Builder} initialized with the values of this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MediaItem)) {
      return false;
    }

    MediaItem other = (MediaItem) obj;

    return Util.areEqual(mediaId, other.mediaId)
        && clippingProperties.equals(other.clippingProperties)
        && Util.areEqual(playbackProperties, other.playbackProperties)
        && Util.areEqual(liveConfiguration, other.liveConfiguration)
        && Util.areEqual(mediaMetadata, other.mediaMetadata);
  }

  @Override
  public int hashCode() {
    int result = mediaId.hashCode();
    result = 31 * result + (playbackProperties != null ? playbackProperties.hashCode() : 0);
    result = 31 * result + liveConfiguration.hashCode();
    result = 31 * result + clippingProperties.hashCode();
    result = 31 * result + mediaMetadata.hashCode();
    return result;
  }

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FIELD_MEDIA_ID,
    FIELD_LIVE_CONFIGURATION,
    FIELD_MEDIA_METADATA,
    FIELD_CLIPPING_PROPERTIES
  })
  private @interface FieldNumber {}

  private static final int FIELD_MEDIA_ID = 0;
  private static final int FIELD_LIVE_CONFIGURATION = 1;
  private static final int FIELD_MEDIA_METADATA = 2;
  private static final int FIELD_CLIPPING_PROPERTIES = 3;

  /**
   * {@inheritDoc}
   *
   * <p>It omits the {@link #playbackProperties} field. The {@link #playbackProperties} of an
   * instance restored by {@link #CREATOR} will always be {@code null}.
   */
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(keyForField(FIELD_MEDIA_ID), mediaId);
    bundle.putBundle(keyForField(FIELD_LIVE_CONFIGURATION), liveConfiguration.toBundle());
    bundle.putBundle(keyForField(FIELD_MEDIA_METADATA), mediaMetadata.toBundle());
    bundle.putBundle(keyForField(FIELD_CLIPPING_PROPERTIES), clippingProperties.toBundle());
    return bundle;
  }

  /**
   * Object that can restore {@link MediaItem} from a {@link Bundle}.
   *
   * <p>The {@link #playbackProperties} of a restored instance will always be {@code null}.
   */
  public static final Creator<MediaItem> CREATOR = MediaItem::fromBundle;

  private static MediaItem fromBundle(Bundle bundle) {
    String mediaId = checkNotNull(bundle.getString(keyForField(FIELD_MEDIA_ID), DEFAULT_MEDIA_ID));
    @Nullable
    Bundle liveConfigurationBundle = bundle.getBundle(keyForField(FIELD_LIVE_CONFIGURATION));
    LiveConfiguration liveConfiguration;
    if (liveConfigurationBundle == null) {
      liveConfiguration = LiveConfiguration.UNSET;
    } else {
      liveConfiguration = LiveConfiguration.CREATOR.fromBundle(liveConfigurationBundle);
    }
    @Nullable Bundle mediaMetadataBundle = bundle.getBundle(keyForField(FIELD_MEDIA_METADATA));
    MediaMetadata mediaMetadata;
    if (mediaMetadataBundle == null) {
      mediaMetadata = MediaMetadata.EMPTY;
    } else {
      mediaMetadata = MediaMetadata.CREATOR.fromBundle(mediaMetadataBundle);
    }
    @Nullable
    Bundle clippingPropertiesBundle = bundle.getBundle(keyForField(FIELD_CLIPPING_PROPERTIES));
    ClippingProperties clippingProperties;
    if (clippingPropertiesBundle == null) {
      clippingProperties =
          new ClippingProperties(
              /* startPositionMs= */ 0,
              /* endPositionMs= */ C.TIME_END_OF_SOURCE,
              /* relativeToLiveWindow= */ false,
              /* relativeToDefaultPosition= */ false,
              /* startsAtKeyFrame= */ false);
    } else {
      clippingProperties = ClippingProperties.CREATOR.fromBundle(clippingPropertiesBundle);
    }
    return new MediaItem(
        mediaId,
        clippingProperties,
        /* playbackProperties= */ null,
        liveConfiguration,
        mediaMetadata);
  }

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
