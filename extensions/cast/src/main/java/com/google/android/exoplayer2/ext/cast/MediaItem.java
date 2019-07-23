/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Representation of an item that can be played by a media player. */
public final class MediaItem {

  /** A builder for {@link MediaItem} instances. */
  public static final class Builder {

    private String title;
    private MediaItem.UriBundle media;
    private List<MediaItem.DrmScheme> drmSchemes;
    private String mimeType;

    public Builder() {
      title = "";
      media = UriBundle.EMPTY;
      drmSchemes = Collections.emptyList();
      mimeType = "";
    }

    /** See {@link MediaItem#title}. */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /** Equivalent to {@link #setMedia(UriBundle) setMedia(new UriBundle(Uri.parse(uri)))}. */
    public Builder setMedia(String uri) {
      return setMedia(new UriBundle(Uri.parse(uri)));
    }

    /** See {@link MediaItem#media}. */
    public Builder setMedia(UriBundle media) {
      this.media = media;
      return this;
    }

    /** See {@link MediaItem#drmSchemes}. */
    public Builder setDrmSchemes(List<MediaItem.DrmScheme> drmSchemes) {
      this.drmSchemes = Collections.unmodifiableList(new ArrayList<>(drmSchemes));
      return this;
    }

    /** See {@link MediaItem#mimeType}. */
    public Builder setMimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /** Returns a new {@link MediaItem} instance with the current builder values. */
    public MediaItem build() {
      return new MediaItem(
          title,
          media,
          drmSchemes,
          mimeType);
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

  /**
   * Represents a DRM protection scheme, and optionally provides information about how to acquire
   * the license for the media.
   */
  public static final class DrmScheme {

    /** The UUID of the protection scheme. */
    public final UUID uuid;

    /**
     * Optional {@link UriBundle} for the license server. If no license server is provided, the
     * server must be provided by the media.
     */
    @Nullable public final UriBundle licenseServer;

    /**
     * Creates an instance.
     *
     * @param uuid See {@link #uuid}.
     * @param licenseServer See {@link #licenseServer}.
     */
    public DrmScheme(UUID uuid, @Nullable UriBundle licenseServer) {
      this.uuid = uuid;
      this.licenseServer = licenseServer;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      DrmScheme drmScheme = (DrmScheme) other;
      return uuid.equals(drmScheme.uuid) && Util.areEqual(licenseServer, drmScheme.licenseServer);
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + (licenseServer != null ? licenseServer.hashCode() : 0);
      return result;
    }
  }

  /** The title of the item. The default value is an empty string. */
  public final String title;

  /**
   * A {@link UriBundle} to fetch the media content. The default value is {@link UriBundle#EMPTY}.
   */
  public final UriBundle media;

  /**
   * Immutable list of {@link DrmScheme} instances sorted in decreasing order of preference. The
   * default value is an empty list.
   */
  public final List<DrmScheme> drmSchemes;

  /**
   * The mime type of this media item. The default value is an empty string.
   *
   * <p>The usage of this mime type is optional and player implementation specific.
   */
  public final String mimeType;

  // TODO: Add support for sideloaded tracks, artwork, icon, and subtitle.

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    MediaItem mediaItem = (MediaItem) other;
    return title.equals(mediaItem.title)
        && media.equals(mediaItem.media)
        && drmSchemes.equals(mediaItem.drmSchemes)
        && mimeType.equals(mediaItem.mimeType);
  }

  @Override
  public int hashCode() {
    int result = title.hashCode();
    result = 31 * result + media.hashCode();
    result = 31 * result + drmSchemes.hashCode();
    result = 31 * result + mimeType.hashCode();
    return result;
  }

  private MediaItem(
      String title,
      UriBundle media,
      List<DrmScheme> drmSchemes,
      String mimeType) {
    this.title = title;
    this.media = media;
    this.drmSchemes = drmSchemes;
    this.mimeType = mimeType;
  }
}
