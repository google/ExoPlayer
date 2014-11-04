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

import com.google.android.exoplayer.hls.HlsMasterPlaylist.Variant;
import com.google.android.exoplayer.util.ManifestParser;

import android.net.Uri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HLS Master playlists parsing logic.
 */
public final class HlsMasterPlaylistParser implements ManifestParser<HlsMasterPlaylist> {

  private static final String STREAM_INF_TAG = "#EXT-X-STREAM-INF";
  private static final String BANDWIDTH_ATTR = "BANDWIDTH";
  private static final String CODECS_ATTR = "CODECS";
  private static final String RESOLUTION_ATTR = "RESOLUTION";

  private static final Pattern BANDWIDTH_ATTR_REGEX =
      Pattern.compile(BANDWIDTH_ATTR + "=(\\d+)\\b");
  private static final Pattern CODECS_ATTR_REGEX =
      Pattern.compile(CODECS_ATTR + "=\"(.+)\"");
  private static final Pattern RESOLUTION_ATTR_REGEX =
      Pattern.compile(RESOLUTION_ATTR + "=(\\d+x\\d+)");

  @Override
  public HlsMasterPlaylist parse(InputStream inputStream, String inputEncoding,
      String contentId, Uri baseUri) throws IOException {
    return parseMasterPlaylist(inputStream, inputEncoding, baseUri);
  }

  private static HlsMasterPlaylist parseMasterPlaylist(InputStream inputStream,
      String inputEncoding, Uri baseUri) throws IOException {
    BufferedReader reader = new BufferedReader((inputEncoding == null)
        ? new InputStreamReader(inputStream) : new InputStreamReader(inputStream, inputEncoding));
    List<Variant> variants = new ArrayList<Variant>();
    int bandwidth = 0;
    String[] codecs = null;
    int width = -1;
    int height = -1;

    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      if (line.startsWith(STREAM_INF_TAG)) {
        bandwidth = HlsParserUtil.parseIntAttr(line, BANDWIDTH_ATTR_REGEX, BANDWIDTH_ATTR);
        String codecsString = HlsParserUtil.parseOptionalStringAttr(line, CODECS_ATTR_REGEX,
            CODECS_ATTR);
        if (codecsString != null) {
          codecs = codecsString.split(",");
        } else {
          codecs = null;
        }
        String resolutionString = HlsParserUtil.parseOptionalStringAttr(line, RESOLUTION_ATTR_REGEX,
            RESOLUTION_ATTR);
        if (resolutionString != null) {
          String[] widthAndHeight = resolutionString.split("x");
          width = Integer.parseInt(widthAndHeight[0]);
          height = Integer.parseInt(widthAndHeight[1]);
        } else {
          width = -1;
          height = -1;
        }
      } else if (!line.startsWith("#")) {
        variants.add(new Variant(line, bandwidth, codecs, width, height));
        bandwidth = 0;
        codecs = null;
        width = -1;
        height = -1;
      }
    }
    return new HlsMasterPlaylist(baseUri, Collections.unmodifiableList(variants));
  }

}
