/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.smoothstreaming.manifest;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * A parser of SmoothStreaming manifest which includes only the tracks identified by the given keys.
 */
public final class FilteringSsManifestParser implements Parser<SsManifest> {

  private final SsManifestParser ssManifestParser;
  private final List<TrackKey> filter;

  /** @param filter The track keys that should be retained in the parsed manifests. */
  public FilteringSsManifestParser(List<TrackKey> filter) {
    this.ssManifestParser = new SsManifestParser();
    this.filter = filter;
  }

  @Override
  public SsManifest parse(Uri uri, InputStream inputStream) throws IOException {
    return ssManifestParser.parse(uri, inputStream).copy(filter);
  }
}
