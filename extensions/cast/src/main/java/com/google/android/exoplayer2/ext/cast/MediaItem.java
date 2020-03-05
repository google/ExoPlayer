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
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/** Representation of a media item. */
public final class MediaItem {

  /** A builder for {@link MediaItem} instances. */
  public static final class Builder {

    @Nullable private Uri uri;
    @Nullable private String title;
    @Nullable private String mimeType;
    @Nullable private DrmConfiguration drmConfiguration;

    /** See {@link MediaItem#uri}. */
    public Builder setUri(String uri) {
      return setUri(Uri.parse(uri));
    }

    /** See {@link MediaItem#uri}. */
    public Builder setUri(Uri uri) {
      this.uri = uri;
      return this;
    }

    /** See {@link MediaItem#title}. */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /** See {@link MediaItem#mimeType}. */
    public Builder setMimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /** See {@link MediaItem#drmConfiguration}. */
    public Builder setDrmConfiguration(DrmConfiguration drmConfiguration) {
      this.drmConfiguration = drmConfiguration;
      return this;
    }

    /** Returns a new {@link MediaItem} instance with the current builder values. */
    public MediaItem build() {
      Assertions.checkNotNull(uri);
      return new MediaItem(uri, title, mimeType, drmConfiguration);
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
    @Nullable public final Uri licenseUri;

    /** Headers that should be attached to any license requests. */
    public final Map<String, String> requestHeaders;

    /**
     * Creates an instance.
     *
     * @param uuid See {@link #uuid}.
     * @param licenseUri See {@link #licenseUri}.
     * @param requestHeaders See {@link #requestHeaders}.
     */
    public DrmConfiguration(
        UUID uuid, @Nullable Uri licenseUri, @Nullable Map<String, String> requestHeaders) {
      this.uuid = uuid;
      this.licenseUri = licenseUri;
      this.requestHeaders =
          requestHeaders == null
              ? Collections.emptyMap()
              : Collections.unmodifiableMap(requestHeaders);
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
          && requestHeaders.equals(other.requestHeaders);
    }

    @Override
    public int hashCode() {
      int result = uuid.hashCode();
      result = 31 * result + (licenseUri != null ? licenseUri.hashCode() : 0);
      result = 31 * result + requestHeaders.hashCode();
      return result;
    }
  }

  /** The media {@link Uri}. */
  public final Uri uri;

  /** The title of the item, or {@code null} if unspecified. */
  @Nullable public final String title;

  /** The mime type for the media, or {@code null} if unspecified. */
  @Nullable public final String mimeType;

  /** Optional {@link DrmConfiguration} for the media. */
  @Nullable public final DrmConfiguration drmConfiguration;

  private MediaItem(
      Uri uri,
      @Nullable String title,
      @Nullable String mimeType,
      @Nullable DrmConfiguration drmConfiguration) {
    this.uri = uri;
    this.title = title;
    this.mimeType = mimeType;
    this.drmConfiguration = drmConfiguration;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MediaItem other = (MediaItem) obj;
    return uri.equals(other.uri)
        && Util.areEqual(title, other.title)
        && Util.areEqual(mimeType, other.mimeType)
        && Util.areEqual(drmConfiguration, other.drmConfiguration);
  }

  @Override
  public int hashCode() {
    int result = uri.hashCode();
    result = 31 * result + (title == null ? 0 : title.hashCode());
    result = 31 * result + (drmConfiguration == null ? 0 : drmConfiguration.hashCode());
    result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
    return result;
  }
}
