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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Representation of a media item. */
public final class MediaItem {

  /**
   * Creates a {@link MediaItem} for the given source uri.
   *
   * @param sourceUri The source uri.
   * @return An {@link MediaItem} for the given source uri.
   */
  public static MediaItem fromUri(String sourceUri) {
    return new MediaItem.Builder().setSourceUri(sourceUri).build();
  }

  /**
   * Creates a {@link MediaItem} for the given {@link Uri source uri}.
   *
   * @param sourceUri The {@link Uri source uri}.
   * @return An {@link MediaItem} for the given source uri.
   */
  public static MediaItem fromUri(Uri sourceUri) {
    return new MediaItem.Builder().setSourceUri(sourceUri).build();
  }

  /** A builder for {@link MediaItem} instances. */
  public static final class Builder {

    @Nullable private String mediaId;
    @Nullable private Uri sourceUri;
    @Nullable private String extension;
    @Nullable private UriBundle drmLicenseUri;
    @Nullable private UUID drmUuid;
    private boolean drmMultiSession;
    private List<StreamKey> streamKeys;
    @Nullable private Object tag;

    /** Creates a builder. */
    public Builder() {
      streamKeys = Collections.emptyList();
    }

    /**
     * Sets the optional media id which identifies the media item. If not specified, {@code
     * #setSourceUri} must be called and the string representation of {@link
     * PlaybackProperties#sourceUri} is used as the media id.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setMediaId(@Nullable String mediaId) {
      this.mediaId = mediaId;
      return this;
    }

    /**
     * Sets the optional source uri. If not specified, {@link #setMediaId(String)} must be called.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setSourceUri(@Nullable String sourceUri) {
      return setSourceUri(sourceUri == null ? null : Uri.parse(sourceUri));
    }

    /**
     * Sets the optional source {@link Uri}. If not specified, {@link #setMediaId(String)} must be
     * called.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setSourceUri(@Nullable Uri sourceUri) {
      this.sourceUri = sourceUri;
      return this;
    }

    /**
     * Sets the optional extension of the item.
     *
     * <p>The extension can be used to disambiguate media items that have a uri which does not allow
     * to infer the actual media type.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the extension is used to create a {@link
     * PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setExtension(@Nullable String extension) {
      this.extension = extension;
      return this;
    }

    /**
     * Sets the optional license server {@link UriBundle}. If a license uri is set, the {@link
     * DrmConfiguration#uuid} needs to be specified as well.
     *
     * <p>if a {@link PlaybackProperties#sourceUri} is set, the drm license uri is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDrmLicenseUri(@Nullable UriBundle licenseUri) {
      drmLicenseUri = licenseUri;
      return this;
    }

    /**
     * Sets the optional license server {@link Uri}. If a license uri is set, the {@link
     * DrmConfiguration#uuid} needs to be specified as well.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the drm license uri is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDrmLicenseUri(@Nullable Uri licenseUri) {
      drmLicenseUri = licenseUri == null ? null : new UriBundle(licenseUri);
      return this;
    }

    /**
     * Sets the optional license server uri as a {@link String}. If a license uri is set, the {@link
     * DrmConfiguration#uuid} needs to be specified as well.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the drm license uri is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDrmLicenseUri(@Nullable String licenseUri) {
      drmLicenseUri = licenseUri == null ? null : new UriBundle(Uri.parse(licenseUri));
      return this;
    }

    /**
     * Sets the {@link UUID} of the protection scheme. If a drm system uuid is set, the {@link
     * DrmConfiguration#licenseUri} needs to be set as well.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the drm system uuid is used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDrmUuid(@Nullable UUID uuid) {
      drmUuid = uuid;
      return this;
    }

    /**
     * Sets whether the drm configuration is multi session enabled.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the drm multi session flag is used to
     * create a {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setDrmMultiSession(boolean multiSession) {
      drmMultiSession = multiSession;
      return this;
    }

    /**
     * Sets the optional stream keys by which the manifest is filtered (only used for adaptive
     * streams).
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the stream keys are used to create a
     * {@link PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys =
          streamKeys != null && !streamKeys.isEmpty()
              ? Collections.unmodifiableList(new ArrayList<>(streamKeys))
              : Collections.emptyList();
      return this;
    }

    /**
     * Sets the optional tag for custom attributes. The tag for the media source which will be
     * published in the {@link com.google.android.exoplayer2.Timeline} of the source as {@link
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     *
     * <p>If a {@link PlaybackProperties#sourceUri} is set, the tag is used to create a {@link
     * PlaybackProperties} object. Otherwise it will be ignored.
     *
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    public Builder setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Returns a new {@link MediaItem} instance with the current builder values.
     *
     * @throws IllegalStateException If a required property is not set.
     */
    public MediaItem build() {
      Assertions.checkState(drmLicenseUri == null || drmUuid != null);
      @Nullable PlaybackProperties playbackProperties = null;
      if (sourceUri != null) {
        playbackProperties =
            new PlaybackProperties(
                sourceUri,
                extension,
                drmUuid != null
                    ? new DrmConfiguration(drmUuid, drmLicenseUri, drmMultiSession)
                    : null,
                streamKeys,
                tag);
        mediaId = mediaId != null ? mediaId : sourceUri.toString();
      }
      return new MediaItem(Assertions.checkNotNull(mediaId), playbackProperties);
    }
  }

  /** Bundles a resource's URI with headers to attach to any request to that URI. */
  public static final class UriBundle {

    /** An empty {@link UriBundle}. */
    public static final UriBundle EMPTY = new UriBundle(Uri.EMPTY);

    /** A URI. */
    public final Uri uri;

    /** The headers to attach to any request for the given URI. */
    public final Map<String, String> requestHeaders;

    /**
     * Creates an instance with no request headers.
     *
     * @param uri See {@link #uri}.
     */
    public UriBundle(Uri uri) {
      this(uri, Collections.emptyMap());
    }

    /**
     * Creates an instance with the given URI and request headers.
     *
     * @param uri See {@link #uri}.
     * @param requestHeaders See {@link #requestHeaders}.
     */
    public UriBundle(Uri uri, Map<String, String> requestHeaders) {
      this.uri = uri;
      this.requestHeaders = Collections.unmodifiableMap(new HashMap<>(requestHeaders));
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      UriBundle uriBundle = (UriBundle) other;
      return uri.equals(uriBundle.uri) && requestHeaders.equals(uriBundle.requestHeaders);
    }

    @Override
    public int hashCode() {
      int result = uri.hashCode();
      result = 31 * result + requestHeaders.hashCode();
      return result;
    }
  }

  /** DRM configuration for a media item. */
  public static final class DrmConfiguration {

    /** The UUID of the protection scheme. */
    public final UUID uuid;

    /**
     * Optional license server {@link Uri}. If {@code null} then the license server must be
     * specified by the media.
     */
    @Nullable public final UriBundle licenseUri;

    /** Whether the drm configuration is multi session enabled. */
    public final boolean multiSession;

    /**
     * Creates an instance.
     *
     * @param uuid See {@link #uuid}.
     * @param licenseUri See {@link #licenseUri}.
     * @param multiSession See {@link #multiSession}.
     */
    public DrmConfiguration(UUID uuid, @Nullable UriBundle licenseUri, boolean multiSession) {
      this.uuid = uuid;
      this.licenseUri = licenseUri;
      this.multiSession = multiSession;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }

      DrmConfiguration other = (DrmConfiguration) obj;
      return uuid.equals(other.uuid)
          && Util.areEqual(licenseUri, other.licenseUri)
          && multiSession == other.multiSession;
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + (licenseUri != null ? licenseUri.hashCode() : 0);
      result = 31 * result + (multiSession ? 1 : 0);
      return result;
    }
  }

  /** Properties for local playback. */
  public static final class PlaybackProperties {

    /** The source {@link Uri}. */
    public final Uri sourceUri;

    /**
     * The optional extension of the item, or {@code null} if unspecified.
     *
     * <p>The extension can be used to disambiguate media items that have a uri which does not allow
     * to infer the actual media type.
     */
    @Nullable public final String extension;

    /** Optional {@link DrmConfiguration} for the media. */
    @Nullable public final DrmConfiguration drmConfiguration;

    /** Optional stream keys by which the manifest is filtered. */
    public final List<StreamKey> streamKeys;

    /**
     * Optional tag for custom attributes. The tag for the media source which will be published in
     * the {@link com.google.android.exoplayer2.Timeline} of the source as {@link
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     */
    @Nullable public final Object tag;

    public PlaybackProperties(
        Uri sourceUri,
        @Nullable String extension,
        @Nullable DrmConfiguration drmConfiguration,
        List<StreamKey> streamKeys,
        @Nullable Object tag) {
      this.sourceUri = sourceUri;
      this.extension = extension;
      this.drmConfiguration = drmConfiguration;
      this.streamKeys = streamKeys;
      this.tag = tag;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      PlaybackProperties other = (PlaybackProperties) obj;

      return sourceUri.equals(other.sourceUri)
          && Util.areEqual(extension, other.extension)
          && Util.areEqual(drmConfiguration, other.drmConfiguration)
          && Util.areEqual(streamKeys, other.streamKeys)
          && Util.areEqual(tag, other.tag);
    }

    @Override
    public int hashCode() {
      int result = sourceUri.hashCode();
      result = 31 * result + (extension == null ? 0 : extension.hashCode());
      result = 31 * result + (drmConfiguration == null ? 0 : drmConfiguration.hashCode());
      result = 31 * result + streamKeys.hashCode();
      result = 31 * result + (tag == null ? 0 : tag.hashCode());
      return result;
    }
  }

  /** Identifies the media item. */
  public final String mediaId;

  /** Optional playback properties. */
  @Nullable public final PlaybackProperties playbackProperties;

  private MediaItem(String mediaId, @Nullable PlaybackProperties playbackProperties) {
    this.mediaId = mediaId;
    this.playbackProperties = playbackProperties;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MediaItem other = (MediaItem) o;

    return Util.areEqual(mediaId, other.mediaId)
        && Util.areEqual(playbackProperties, other.playbackProperties);
  }

  @Override
  public int hashCode() {
    int result = mediaId.hashCode();
    result = 31 * result + (playbackProperties != null ? playbackProperties.hashCode() : 0);
    return result;
  }
}
