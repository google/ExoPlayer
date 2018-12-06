/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.castdemo;

import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility methods and constants for the Cast demo application. */
/* package */ final class DemoUtil {

  /** Represents a media sample. */
  public static final class Sample {

    /** The uri of the media content. */
    public final String uri;
    /** The name of the sample. */
    public final String name;
    /** The mime type of the sample media content. */
    public final String mimeType;

    /**
     * @param uri See {@link #uri}.
     * @param name See {@link #name}.
     * @param mimeType See {@link #mimeType}.
     */
    public Sample(String uri, String name, String mimeType) {
      this.uri = uri;
      this.name = name;
      this.mimeType = mimeType;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static final String MIME_TYPE_DASH = MimeTypes.APPLICATION_MPD;
  public static final String MIME_TYPE_HLS = MimeTypes.APPLICATION_M3U8;
  public static final String MIME_TYPE_SS = MimeTypes.APPLICATION_SS;
  public static final String MIME_TYPE_VIDEO_MP4 = MimeTypes.VIDEO_MP4;

  /** The list of samples available in the cast demo app. */
  public static final List<Sample> SAMPLES;

  static {
    // App samples.
    ArrayList<Sample> samples = new ArrayList<>();

    samples.add(
        new Sample(
            "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
            "DASH (clear,MP4,H264)",
            MIME_TYPE_DASH));
    samples.add(
        new Sample(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/"
                + "hls/TearsOfSteel.m3u8",
            "Tears of Steel (HLS)",
            MIME_TYPE_HLS));
    samples.add(
        new Sample(
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3"
                + "/bipbop_4x3_variant.m3u8",
            "HLS Basic (TS)",
            MIME_TYPE_HLS));
    samples.add(
        new Sample("https://html5demos.com/assets/dizzy.mp4", "Dizzy (MP4)", MIME_TYPE_VIDEO_MP4));
    SAMPLES = Collections.unmodifiableList(samples);
  }

  private DemoUtil() {}
}
