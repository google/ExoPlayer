/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/* package */ abstract class Sample {

  public static final class UriSample extends Sample {

    public final Uri uri;
    public final String extension;
    public final boolean isLive;
    public final DrmInfo drmInfo;
    public final Uri adTagUri;
    @Nullable public final String sphericalStereoMode;
    @Nullable SubtitleInfo subtitleInfo;

    public UriSample(
        String name,
        Uri uri,
        String extension,
        boolean isLive,
        DrmInfo drmInfo,
        Uri adTagUri,
        @Nullable String sphericalStereoMode,
        @Nullable SubtitleInfo subtitleInfo) {
      super(name);
      this.uri = uri;
      this.extension = extension;
      this.isLive = isLive;
      this.drmInfo = drmInfo;
      this.adTagUri = adTagUri;
      this.sphericalStereoMode = sphericalStereoMode;
      this.subtitleInfo = subtitleInfo;
    }

    @Override
    public void addToIntent(Intent intent) {
      intent.setAction(IntentUtil.ACTION_VIEW).setData(uri);
      intent.putExtra(IntentUtil.IS_LIVE_EXTRA, isLive);
      intent.putExtra(IntentUtil.SPHERICAL_STEREO_MODE_EXTRA, sphericalStereoMode);
      addPlayerConfigToIntent(intent, /* extrasKeySuffix= */ "");
    }

    public void addToPlaylistIntent(Intent intent, String extrasKeySuffix) {
      intent.putExtra(IntentUtil.URI_EXTRA + extrasKeySuffix, uri.toString());
      intent.putExtra(IntentUtil.IS_LIVE_EXTRA + extrasKeySuffix, isLive);
      addPlayerConfigToIntent(intent, extrasKeySuffix);
    }

    private void addPlayerConfigToIntent(Intent intent, String extrasKeySuffix) {
      intent
          .putExtra(IntentUtil.EXTENSION_EXTRA + extrasKeySuffix, extension)
          .putExtra(
              IntentUtil.AD_TAG_URI_EXTRA + extrasKeySuffix,
              adTagUri != null ? adTagUri.toString() : null);
      if (drmInfo != null) {
        drmInfo.addToIntent(intent, extrasKeySuffix);
      }
      if (subtitleInfo != null) {
        subtitleInfo.addToIntent(intent, extrasKeySuffix);
      }
    }
  }

  public static final class PlaylistSample extends Sample {

    public final UriSample[] children;

    public PlaylistSample(String name, UriSample... children) {
      super(name);
      this.children = children;
    }

    @Override
    public void addToIntent(Intent intent) {
      intent.setAction(IntentUtil.ACTION_VIEW_LIST);
      for (int i = 0; i < children.length; i++) {
        children[i].addToPlaylistIntent(intent, /* extrasKeySuffix= */ "_" + i);
      }
    }
  }

  public static final class DrmInfo {

    public final UUID drmScheme;
    public final String drmLicenseUrl;
    public final String[] drmKeyRequestProperties;
    public final int[] drmSessionForClearTypes;
    public final boolean drmMultiSession;

    public DrmInfo(
        UUID drmScheme,
        String drmLicenseUrl,
        String[] drmKeyRequestProperties,
        int[] drmSessionForClearTypes,
        boolean drmMultiSession) {
      this.drmScheme = drmScheme;
      this.drmLicenseUrl = drmLicenseUrl;
      this.drmKeyRequestProperties = drmKeyRequestProperties;
      this.drmSessionForClearTypes = drmSessionForClearTypes;
      this.drmMultiSession = drmMultiSession;
    }

    public void addToIntent(Intent intent, String extrasKeySuffix) {
      Assertions.checkNotNull(intent);
      intent.putExtra(IntentUtil.DRM_SCHEME_EXTRA + extrasKeySuffix, drmScheme.toString());
      intent.putExtra(IntentUtil.DRM_LICENSE_URL_EXTRA + extrasKeySuffix, drmLicenseUrl);
      intent.putExtra(
          IntentUtil.DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix, drmKeyRequestProperties);
      ArrayList<String> typeStrings = new ArrayList<>();
      for (int type : drmSessionForClearTypes) {
        // Only audio and video are supported.
        typeStrings.add(type == C.TRACK_TYPE_AUDIO ? "audio" : "video");
      }
      intent.putExtra(
          IntentUtil.DRM_SESSION_FOR_CLEAR_TYPES_EXTRA + extrasKeySuffix,
          typeStrings.toArray(new String[0]));
      intent.putExtra(IntentUtil.DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, drmMultiSession);
    }
  }

  public static final class SubtitleInfo {

    public final Uri uri;
    public final String mimeType;
    @Nullable public final String language;

    public SubtitleInfo(Uri uri, String mimeType, @Nullable String language) {
      this.uri = Assertions.checkNotNull(uri);
      this.mimeType = Assertions.checkNotNull(mimeType);
      this.language = language;
    }

    public void addToIntent(Intent intent, String extrasKeySuffix) {
      intent.putExtra(IntentUtil.SUBTITLE_URI_EXTRA + extrasKeySuffix, uri.toString());
      intent.putExtra(IntentUtil.SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix, mimeType);
      intent.putExtra(IntentUtil.SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix, language);
    }
  }

  public static int[] toTrackTypeArray(@Nullable String[] trackTypeStringsArray) {
    if (trackTypeStringsArray == null) {
      return new int[0];
    }
    HashSet<Integer> trackTypes = new HashSet<>();
    for (String trackTypeString : trackTypeStringsArray) {
      switch (Util.toLowerInvariant(trackTypeString)) {
        case "audio":
          trackTypes.add(C.TRACK_TYPE_AUDIO);
          break;
        case "video":
          trackTypes.add(C.TRACK_TYPE_VIDEO);
          break;
        default:
          throw new IllegalArgumentException("Invalid track type: " + trackTypeString);
      }
    }
    return Util.toArray(new ArrayList<>(trackTypes));
  }

  public final String name;

  public Sample(String name) {
    this.name = name;
  }

  public abstract void addToIntent(Intent intent);
}
