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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Representation of a media item. */
public final class MediaItem {

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
    @Nullable private Uri drmLicenseUri;
    private Map<String, String> drmLicenseRequestHeaders;
    @Nullable private UUID drmUuid;
    private boolean drmMultiSession;
    private boolean drmPlayClearContentWithoutKey;
    private boolean drmForceDefaultLicenseUri;
    private List<Integer> drmSessionForClearTypes;
    @Nullable private byte[] drmKeySetId;
    private List<StreamKey> streamKeys;
    @Nullable private String customCacheKey;
    private List<Subtitle> subtitles;
    @Nullable private Uri adTagUri;
    @Nullable private Object tag;
    @Nullable private MediaMetadata mediaMetadata;

    /** Creates a builder. */
    public Builder() {
      clipEndPositionMs = C.TIME_END_OF_SOURCE;
      drmSessionForClearTypes = Collections.emptyList();
      drmLicenseRequestHeaders = Collections.emptyMap();
      streamKeys = Collections.emptyList();
      subtitles = Collections.emptyList();
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
      @Nullable PlaybackProperties playbackProperties = mediaItem.playbackProperties;
      if (playbackProperties != null) {
        adTagUri = playbackProperties.adTagUri;
        customCacheKey = playbackProperties.customCacheKey;
        mimeType = playbackProperties.mimeType;
        uri = playbackProperties.uri;
        streamKeys = playbackProperties.streamKeys;
        subtitles = playbackProperties.subtitles;
        tag = playbackProperties.tag;
        @Nullable DrmConfiguration drmConfiguration = playbackProperties.drmConfiguration;
        if (drmConfiguration != null) {
          drmLicenseUri = drmConfiguration.licenseUri;
          drmLicenseRequestHeaders = drmConfiguration.requestHeaders;
          drmMultiSession = drmConfiguration.multiSession;
          drmForceDefaultLicenseUri = drmConfiguration.forceDefaultLicenseUri;
          drmPlayClearContentWithoutKey = drmConfiguration.playClearContentWithoutKey;
          drmSessionForClearTypes = drmConfiguration.sessionForClearTypes;
          drmUuid = drmConfiguration.uuid;
          drmKeySetId = drmConfiguration.getKeySetId();
        }
      }
    }

    /**
     * Sets the optional media ID which identifies the media item. If not specified, {@link #setUri}
     * must be called and the string representation of {@link PlaybackProperties#uri} is used as the
     * media ID.
     */
    public Builder setMediaId(@Nullable String mediaId) {
      this.mediaId = mediaId;
      return this;
    }

    /**
     * Sets the optional URI. If not specified, {@link #setMediaId(String)} must be called.
     *
     * <p>If {@code uri} is null or unset no {@link PlaybackProperties} object is created during
     * {@link #build()} and any other {@code Builder} methods that would populate {@link
     * MediaItem#playbackProperties} are ignored.
     */
    public Builder setUri(@Nullable String uri) {
      return setUri(uri == null ? null : Uri.parse(uri));
    }

    /**
     * Sets the optional URI. If not specified, {@link #setMediaId(String)} must be called.
     *
     * <p>If {@code uri} is null or unset no {@link PlaybackProperties} object is created during
     * {@link #build()} and any other {@code Builder} methods that would populate {@link
     * MediaItem#playbackProperties} are ignored.
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
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the MIME type is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
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
    public Builder setClipStartPositionMs(long startPositionMs) {
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

    /**
     * Sets the optional DRM license server URI. If this URI is set, the {@link
     * DrmConfiguration#uuid} needs to be specified as well.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the DRM license server URI is used to
     * create a {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setDrmLicenseUri(@Nullable Uri licenseUri) {
      drmLicenseUri = licenseUri;
      return this;
    }

    /**
     * Sets the optional DRM license server URI. If this URI is set, the {@link
     * DrmConfiguration#uuid} needs to be specified as well.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the DRM license server URI is used to
     * create a {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setDrmLicenseUri(@Nullable String licenseUri) {
      drmLicenseUri = licenseUri == null ? null : Uri.parse(licenseUri);
      return this;
    }

    /**
     * Sets the optional request headers attached to the DRM license request.
     *
     * <p>{@code null} or an empty {@link Map} can be used for a reset.
     *
     * <p>If no valid DRM configuration is specified, the DRM license request headers are ignored.
     */
    public Builder setDrmLicenseRequestHeaders(
        @Nullable Map<String, String> licenseRequestHeaders) {
      this.drmLicenseRequestHeaders =
          licenseRequestHeaders != null && !licenseRequestHeaders.isEmpty()
              ? Collections.unmodifiableMap(new HashMap<>(licenseRequestHeaders))
              : Collections.emptyMap();
      return this;
    }

    /**
     * Sets the {@link UUID} of the protection scheme. If a DRM system UUID is set, the {@link
     * DrmConfiguration#licenseUri} needs to be set as well.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the DRM system UUID is used to create
     * a {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setDrmUuid(@Nullable UUID uuid) {
      drmUuid = uuid;
      return this;
    }

    /**
     * Sets whether the DRM configuration is multi session enabled.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the DRM multi session flag is used to
     * create a {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setDrmMultiSession(boolean multiSession) {
      drmMultiSession = multiSession;
      return this;
    }

    /**
     * Sets whether to use the DRM license server URI of the media item for key requests that
     * include their own DRM license server URI.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the DRM force default license flag is
     * used to create a {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setDrmForceDefaultLicenseUri(boolean forceDefaultLicenseUri) {
      this.drmForceDefaultLicenseUri = forceDefaultLicenseUri;
      return this;
    }

    /**
     * Sets whether clear samples within protected content should be played when keys for the
     * encrypted part of the content have yet to be loaded.
     */
    public Builder setDrmPlayClearContentWithoutKey(boolean playClearContentWithoutKey) {
      this.drmPlayClearContentWithoutKey = playClearContentWithoutKey;
      return this;
    }

    /**
     * Sets whether a DRM session should be used for clear tracks of type {@link C#TRACK_TYPE_VIDEO}
     * and {@link C#TRACK_TYPE_AUDIO}.
     *
     * <p>This method overrides what has been set by previously calling {@link
     * #setDrmSessionForClearTypes(List)}.
     */
    public Builder setDrmSessionForClearPeriods(boolean sessionForClearPeriods) {
      this.setDrmSessionForClearTypes(
          sessionForClearPeriods
              ? Arrays.asList(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
              : Collections.emptyList());
      return this;
    }

    /**
     * Sets a list of {@link C}{@code .TRACK_TYPE_*} constants for which to use a DRM session even
     * when the tracks are in the clear.
     *
     * <p>For the common case of using a DRM session for {@link C#TRACK_TYPE_VIDEO} and {@link
     * C#TRACK_TYPE_AUDIO} the {@link #setDrmSessionForClearPeriods(boolean)} can be used.
     *
     * <p>This method overrides what has been set by previously calling {@link
     * #setDrmSessionForClearPeriods(boolean)}.
     *
     * <p>{@code null} or an empty {@link List} can be used for a reset.
     */
    public Builder setDrmSessionForClearTypes(@Nullable List<Integer> sessionForClearTypes) {
      this.drmSessionForClearTypes =
          sessionForClearTypes != null && !sessionForClearTypes.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(sessionForClearTypes))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the key set ID of the offline license.
     *
     * <p>The key set ID identifies an offline license. The ID is required to query, renew or
     * release an existing offline license (see {@code DefaultDrmSessionManager#setMode(int
     * mode,byte[] offlineLicenseKeySetId)}).
     *
     * <p>If no valid DRM configuration is specified, the key set ID is ignored.
     */
    public Builder setDrmKeySetId(@Nullable byte[] keySetId) {
      this.drmKeySetId = keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
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
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the custom cache key is used to
     * create a {@link PlaybackProperties} object. Otherwise it will be ignored.
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
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the subtitles are used to create a
     * {@link PlaybackProperties} object. Otherwise they will be ignored.
     */
    public Builder setSubtitles(@Nullable List<Subtitle> subtitles) {
      this.subtitles =
          subtitles != null && !subtitles.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(subtitles))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the optional ad tag URI.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the ad tag URI is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setAdTagUri(@Nullable String adTagUri) {
      this.adTagUri = adTagUri != null ? Uri.parse(adTagUri) : null;
      return this;
    }

    /**
     * Sets the optional ad tag {@link Uri}.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the ad tag URI is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     */
    public Builder setAdTagUri(@Nullable Uri adTagUri) {
      this.adTagUri = adTagUri;
      return this;
    }

    /**
     * Sets the optional tag for custom attributes. The tag for the media source which will be
     * published in the {@code com.google.android.exoplayer2.Timeline} of the source as {@code
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     *
     * <p>If {@link #setUri} is passed a non-null {@code uri}, the tag is used to create a {@link
     * PlaybackProperties} object. Otherwise it will be ignored.
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

    /**
     * Returns a new {@link MediaItem} instance with the current builder values.
     */
    public MediaItem build() {
      Assertions.checkState(drmLicenseUri == null || drmUuid != null);
      @Nullable PlaybackProperties playbackProperties = null;
      if (uri != null) {
        playbackProperties =
            new PlaybackProperties(
                uri,
                mimeType,
                drmUuid != null
                    ? new DrmConfiguration(
                        drmUuid,
                        drmLicenseUri,
                        drmLicenseRequestHeaders,
                        drmMultiSession,
                        drmForceDefaultLicenseUri,
                        drmPlayClearContentWithoutKey,
                        drmSessionForClearTypes,
                        drmKeySetId)
                    : null,
                streamKeys,
                customCacheKey,
                subtitles,
                adTagUri,
                tag);
        mediaId = mediaId != null ? mediaId : uri.toString();
      }
      return new MediaItem(
          Assertions.checkNotNull(mediaId),
          new ClippingProperties(
              clipStartPositionMs,
              clipEndPositionMs,
              clipRelativeToLiveWindow,
              clipRelativeToDefaultPosition,
              clipStartsAtKeyFrame),
          playbackProperties,
          mediaMetadata != null ? mediaMetadata : new MediaMetadata.Builder().build());
    }
  }

  /** DRM configuration for a media item. */
  public static final class DrmConfiguration {

    /** The UUID of the protection scheme. */
    public final UUID uuid;

    /**
     * Optional DRM license server {@link Uri}. If {@code null} then the DRM license server must be
     * specified by the media.
     */
    @Nullable public final Uri licenseUri;

    /** The headers to attach to the request to the DRM license server. */
    public final Map<String, String> requestHeaders;

    /** Whether the DRM configuration is multi session enabled. */
    public final boolean multiSession;

    /**
     * Whether clear samples within protected content should be played when keys for the encrypted
     * part of the content have yet to be loaded.
     */
    public final boolean playClearContentWithoutKey;

    /**
     * Sets whether to use the DRM license server URI of the media item for key requests that
     * include their own DRM license server URI.
     */
    public final boolean forceDefaultLicenseUri;

    /** The types of clear tracks for which to use a DRM session. */
    public final List<Integer> sessionForClearTypes;

    @Nullable private final byte[] keySetId;

    private DrmConfiguration(
        UUID uuid,
        @Nullable Uri licenseUri,
        Map<String, String> requestHeaders,
        boolean multiSession,
        boolean forceDefaultLicenseUri,
        boolean playClearContentWithoutKey,
        List<Integer> drmSessionForClearTypes,
        @Nullable byte[] keySetId) {
      this.uuid = uuid;
      this.licenseUri = licenseUri;
      this.requestHeaders = requestHeaders;
      this.multiSession = multiSession;
      this.forceDefaultLicenseUri = forceDefaultLicenseUri;
      this.playClearContentWithoutKey = playClearContentWithoutKey;
      this.sessionForClearTypes = drmSessionForClearTypes;
      this.keySetId = keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
    }

    /** Returns the key set ID of the offline license. */
    @Nullable
    public byte[] getKeySetId() {
      return keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
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

    /** Optional stream keys by which the manifest is filtered. */
    public final List<StreamKey> streamKeys;

    /** Optional custom cache key (only used for progressive streams). */
    @Nullable public final String customCacheKey;

    /** Optional subtitles to be sideloaded. */
    public final List<Subtitle> subtitles;

    /** Optional ad tag {@link Uri}. */
    @Nullable public final Uri adTagUri;

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
        List<StreamKey> streamKeys,
        @Nullable String customCacheKey,
        List<Subtitle> subtitles,
        @Nullable Uri adTagUri,
        @Nullable Object tag) {
      this.uri = uri;
      this.mimeType = mimeType;
      this.drmConfiguration = drmConfiguration;
      this.streamKeys = streamKeys;
      this.customCacheKey = customCacheKey;
      this.subtitles = subtitles;
      this.adTagUri = adTagUri;
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
          && streamKeys.equals(other.streamKeys)
          && Util.areEqual(customCacheKey, other.customCacheKey)
          && subtitles.equals(other.subtitles)
          && Util.areEqual(adTagUri, other.adTagUri)
          && Util.areEqual(tag, other.tag);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
      result = 31 * result + (drmConfiguration == null ? 0 : drmConfiguration.hashCode());
      result = 31 * result + streamKeys.hashCode();
      result = 31 * result + (customCacheKey == null ? 0 : customCacheKey.hashCode());
      result = 31 * result + subtitles.hashCode();
      result = 31 * result + (adTagUri == null ? 0 : adTagUri.hashCode());
      result = 31 * result + (tag == null ? 0 : tag.hashCode());
      return result;
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
     * Creates an instance with the given selection flags.
     *
     * @param uri The {@link Uri URI} to the subtitle file.
     * @param mimeType The MIME type.
     * @param language The optional language.
     * @param selectionFlags The selection flags.
     */
    public Subtitle(
        Uri uri, String mimeType, @Nullable String language, @C.SelectionFlags int selectionFlags) {
      this.uri = uri;
      this.mimeType = mimeType;
      this.language = language;
      this.selectionFlags = selectionFlags;
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
          && selectionFlags == other.selectionFlags;
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + mimeType.hashCode();
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + selectionFlags;
      return result;
    }
  }

  /** Optionally clips the media item to a custom start and end position. */
  public static final class ClippingProperties {

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
      int result = Long.valueOf(startPositionMs).hashCode();
      result = 31 * result + Long.valueOf(endPositionMs).hashCode();
      result = 31 * result + (relativeToLiveWindow ? 1 : 0);
      result = 31 * result + (relativeToDefaultPosition ? 1 : 0);
      result = 31 * result + (startsAtKeyFrame ? 1 : 0);
      return result;
    }
  }

  /** Identifies the media item. */
  public final String mediaId;

  /** Optional playback properties. Maybe be {@code null} if shared over process boundaries. */
  @Nullable public final PlaybackProperties playbackProperties;

  /** The media metadata. */
  public final MediaMetadata mediaMetadata;

  /** The clipping properties. */
  public final ClippingProperties clippingProperties;

  private MediaItem(
      String mediaId,
      ClippingProperties clippingProperties,
      @Nullable PlaybackProperties playbackProperties,
      MediaMetadata mediaMetadata) {
    this.mediaId = mediaId;
    this.playbackProperties = playbackProperties;
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
        && Util.areEqual(mediaMetadata, other.mediaMetadata);
  }

  @Override
  public int hashCode() {
    int result = mediaId.hashCode();
    result = 31 * result + (playbackProperties != null ? playbackProperties.hashCode() : 0);
    result = 31 * result + clippingProperties.hashCode();
    result = 31 * result + mediaMetadata.hashCode();
    return result;
  }
}
