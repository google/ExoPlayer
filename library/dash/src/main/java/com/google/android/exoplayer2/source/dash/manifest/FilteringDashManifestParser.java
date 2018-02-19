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
package com.google.android.exoplayer2.source.dash.manifest;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * A parser of media presentation description files which includes only the representations
 * identified by the given keys.
 */
public final class FilteringDashManifestParser implements Parser<DashManifest> {

  private final DashManifestParser dashManifestParser;
  private final ArrayList<RepresentationKey> filter;

  /** @param filter The representation keys that should be retained in the parsed manifests. */
  public FilteringDashManifestParser(ArrayList<RepresentationKey> filter) {
    this.dashManifestParser = new DashManifestParser();
    this.filter = filter;
  }

  @Override
  public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
    return dashManifestParser.parse(uri, inputStream).copy(filter);
  }
}
