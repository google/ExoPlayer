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

import java.util.List;
import java.util.ArrayList;

/**
 * Represents an HLS master playlist.
 */
public final class HlsMasterPlaylist extends HlsPlaylist {

  public final List<Variant> variants;
  public final List<AlternateMedia> subtitles = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> closedCaptions = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> alternateAudio = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> alternateVideo = new ArrayList<AlternateMedia>();

  public HlsMasterPlaylist(String baseUri, List<Variant> variants, List<AlternateMedia> alternateMedias) {
    super(baseUri, HlsPlaylist.TYPE_MASTER);
    this.variants = variants;

    for (AlternateMedia a: alternateMedias) {
      if (a.type == AlternateMedia.TYPE_AUDIO) {
        alternateAudio.add(a);
        continue;
      }
      if (a.type == AlternateMedia.TYPE_VIDEO) {
        alternateVideo.add(a);
        continue;
      }
      if (a.type == AlternateMedia.TYPE_SUBTITLES) {
        subtitles.add(a);
        continue;
      }
      if (a.type == AlternateMedia.TYPE_CLOSED_CAPTIONS) {
        closedCaptions.add(a);
        continue;
      }
    }

    for (Variant v: variants) {
      for (AlternateMedia a: alternateMedias) {
        if (a.type == AlternateMedia.TYPE_AUDIO &&
            v.audioGroup != null && a.groupID != null &&
            v.audioGroup.equals(a.groupID)) {
          v.alternateAudio.add(a);
          continue;
        }

        if (a.type == AlternateMedia.TYPE_VIDEO &&
            v.videoGroup != null && a.groupID != null &&
            v.videoGroup.equals(a.groupID)) {
          v.alternateVideo.add(a);
          continue;
        }

        if (a.type == AlternateMedia.TYPE_SUBTITLES &&
            v.subtitlesGroup != null && a.groupID != null &&
            v.subtitlesGroup.equals(a.groupID)) {
          v.subtitles.add(a);
          continue;
        }

        if (a.type == AlternateMedia.TYPE_CLOSED_CAPTIONS &&
            v.closedCaptionsGroup != null && a.groupID != null &&
            v.closedCaptionsGroup.equals(a.groupID)) {
          v.closedCaptions.add(a);
          continue;
        }
      }
    }
  }
}
