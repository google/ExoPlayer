/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.hls;

import android.net.Uri;

import java.util.List;

/**
 * Represents an HLS master playlist.
 */
public final class HlsMasterPlaylist {

  /**
   * Variant stream reference.
   */
  public static final class Variant {
    public final int bandwidth;
    public final String url;
    public final String[] codecs;
    public final int width;
    public final int height;

    public Variant(String url, int bandwidth, String[] codecs, int width, int height) {
      this.bandwidth = bandwidth;
      this.url = url;
      this.codecs = codecs;
      this.width = width;
      this.height = height;
    }
  }

  public final Uri baseUri;
  public final List<Variant> variants;

  public HlsMasterPlaylist(Uri baseUri, List<Variant> variants) {
    this.baseUri = baseUri;
    this.variants = variants;
  }

}
