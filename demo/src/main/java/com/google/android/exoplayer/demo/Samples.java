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
package com.google.android.exoplayer.demo;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Util;

import java.util.UUID;

/**
 * Holds statically defined sample definitions.
 */
/* package */ final class Samples {

  public static class Sample {

    public final String name;
    public final String uri;
    public final int type;
    public final UUID drmSchemeUuid;
    public final String drmContentId;
    public final String drmProvider;
    public final boolean useExtensionDecoders;

    public static Sample newSample(String name, String uri, int type) {
      return new Sample(name, uri, type, null, null, null, false);
    }

    public static Sample newExtensionSample(String name, String uri, int type) {
      return new Sample(name, uri, type, null, null, null, true);
    }

    public static Sample newDrmProtectedSample(String name, String uri, int type, UUID drmScheme,
        String drmContentId, String drmProvider) {
      return new Sample(name, uri, type, drmScheme, drmContentId, drmProvider, false);
    }

    private Sample(String name, String uri, int type, UUID drmSchemeUuid, String drmContentId,
        String drmProvider, boolean useExtensionDecoders) {
      this.name = name;
      this.uri = uri;
      this.type = type;
      this.drmSchemeUuid = drmSchemeUuid;
      this.drmContentId = drmContentId;
      this.drmProvider = drmProvider;
      this.useExtensionDecoders = useExtensionDecoders;
    }

  }

  public static final Sample[] YOUTUBE_DASH_MP4 = new Sample[] {
    Sample.newSample("Google Glass (MP4,H264)",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7."
        + "8506521BFC350652163895D4C26DEE124209AA9E&key=ik0", Util.TYPE_DASH),
    Sample.newSample("Google Play (MP4,H264)",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29."
        + "84308FF04844498CE6FBCE4731507882B8307798&key=ik0", Util.TYPE_DASH),
  };

  public static final Sample[] YOUTUBE_DASH_WEBM = new Sample[] {
    Sample.newSample("Google Glass (WebM,VP9)",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=249B04F79E984D7F86B4D8DB48AE6FAF41C17AB3."
        + "7B9F0EC0505E1566E59B8E488E9419F253DDF413&key=ik0", Util.TYPE_DASH),
    Sample.newSample("Google Play (WebM,VP9)",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=B1C2A74783AC1CC4865EB312D7DD2D48230CC9FD."
        + "BD153B9882175F1F94BFE5141A5482313EA38E8D&key=ik0", Util.TYPE_DASH),
  };

  public static final Sample[] SMOOTHSTREAMING = new Sample[] {
    Sample.newSample("Super speed",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism",
        Util.TYPE_SS),
    Sample.newDrmProtectedSample("Super speed (PlayReady)",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264PR/SuperSpeedway_720.ism",
        Util.TYPE_SS, C.PLAYREADY_UUID, null, null),
  };

  private static final String WIDEVINE_GTS_H264_MPD =
      "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd";
  private static final String WIDEVINE_GTS_VP9_MPD =
      "https://storage.googleapis.com/wvmedia/cenc/vp9/tears/tears.mpd";
  private static final String WIDEVINE_GTS_H265_MPD =
      "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears.mpd";
  public static final Sample[] WIDEVINE_GTS = new Sample[] {
    Sample.newDrmProtectedSample("WV: HDCP not specified", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "d286538032258a1c", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP not required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "48fcc369939ac96c", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "e06c39f1151da3df", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure video path required (MP4,H264)", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "0894c7c8719b28a0", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure video path required (WebM,VP9)", WIDEVINE_GTS_VP9_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "0894c7c8719b28a0", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure video path required (MP4,H265)", WIDEVINE_GTS_H265_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "0894c7c8719b28a0", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP + secure video path required", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "efd045b1eb61888a", "widevine_test"),
    Sample.newDrmProtectedSample("WV: 30s license duration (fails at ~30s)", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "f9a34cab7b05881a", "widevine_test"),
  };

  public static final Sample[] WIDEVINE_HDCP = new Sample[] {
    Sample.newDrmProtectedSample("WV: HDCP: None (not required)", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "HDCP_None", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP: 1.0 required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "HDCP_V1", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP: 2.0 required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "HDCP_V2", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP: 2.1 required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "HDCP_V2_1", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP: 2.2 required", WIDEVINE_GTS_H264_MPD, Util.TYPE_DASH,
        C.WIDEVINE_UUID, "HDCP_V2_2", "widevine_test"),
    Sample.newDrmProtectedSample("WV: HDCP: No digital output", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "HDCP_NO_DIGTAL_OUTPUT", "widevine_test"),
  };

  public static final Sample[] WIDEVINE_H264_MP4_CLEAR = new Sample[] {
    Sample.newSample("WV: Clear SD & HD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear SD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears_sd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear HD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears_hd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear UHD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears_uhd.mpd",
        Util.TYPE_DASH),
  };

  public static final Sample[] WIDEVINE_H264_MP4_SECURE = new Sample[] {
    Sample.newDrmProtectedSample("WV: Secure SD & HD (MP4,H264)", WIDEVINE_GTS_H264_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure SD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears_sd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure HD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears_hd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure UHD (MP4,H264)",
        "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears_uhd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
  };

  public static final Sample[] WIDEVINE_VP9_WEBM_CLEAR = new Sample[] {
    Sample.newSample("WV: Clear SD & HD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/clear/vp9/tears/tears.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear SD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/clear/vp9/tears/tears_sd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear HD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/clear/vp9/tears/tears_hd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear UHD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/clear/vp9/tears/tears_uhd.mpd",
        Util.TYPE_DASH),
  };

  public static final Sample[] WIDEVINE_VP9_WEBM_SECURE = new Sample[] {
    Sample.newDrmProtectedSample("WV: Secure SD & HD (WebM,VP9)", WIDEVINE_GTS_VP9_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure SD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/cenc/vp9/tears/tears_sd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure HD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/cenc/vp9/tears/tears_hd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure UHD (WebM,VP9)",
        "https://storage.googleapis.com/wvmedia/cenc/vp9/tears/tears_uhd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
  };

  public static final Sample[] WIDEVINE_H265_MP4_CLEAR = new Sample[] {
    Sample.newSample("WV: Clear SD & HD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/clear/hevc/tears/tears.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear SD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/clear/hevc/tears/tears_sd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear HD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/clear/hevc/tears/tears_hd.mpd",
        Util.TYPE_DASH),
    Sample.newSample("WV: Clear UHD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/clear/hevc/tears/tears_uhd.mpd",
        Util.TYPE_DASH),
  };

  public static final Sample[] WIDEVINE_H265_MP4_SECURE = new Sample[] {
    Sample.newDrmProtectedSample("WV: Secure SD & HD (MP4,H265)", WIDEVINE_GTS_H265_MPD,
        Util.TYPE_DASH, C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure SD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears_sd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure HD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears_hd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
    Sample.newDrmProtectedSample("WV: Secure UHD (MP4,H265)",
        "https://storage.googleapis.com/wvmedia/cenc/hevc/tears/tears_uhd.mpd", Util.TYPE_DASH,
        C.WIDEVINE_UUID, "", "widevine_test"),
  };

  public static final Sample[] HLS = new Sample[] {
    Sample.newSample("Apple master playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/"
        + "bipbop_4x3_variant.m3u8", Util.TYPE_HLS),
    Sample.newSample("Apple master playlist advanced",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/"
        + "bipbop_16x9_variant.m3u8", Util.TYPE_HLS),
    Sample.newSample("Apple TS media playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/"
        + "prog_index.m3u8", Util.TYPE_HLS),
    Sample.newSample("Apple AAC media playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear0/"
        + "prog_index.m3u8", Util.TYPE_HLS),
    Sample.newSample("Apple ID3 metadata", "http://devimages.apple.com/samplecode/adDemo/ad.m3u8",
        Util.TYPE_HLS),
  };

  public static final Sample[] MISC = new Sample[] {
    Sample.newSample("Dizzy", "http://html5demos.com/assets/dizzy.mp4", Util.TYPE_OTHER),
    Sample.newSample("Apple AAC 10s", "https://devimages.apple.com.edgekey.net/"
        + "streaming/examples/bipbop_4x3/gear0/fileSequence0.aac", Util.TYPE_OTHER),
    Sample.newSample("Apple TS 10s", "https://devimages.apple.com.edgekey.net/streaming/examples/"
        + "bipbop_4x3/gear1/fileSequence0.ts", Util.TYPE_OTHER),
    Sample.newSample("Android screens (Matroska)", "http://storage.googleapis.com/exoplayer-test-me"
        + "dia-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv", Util.TYPE_OTHER),
    Sample.newSample("Big Buck Bunny (MP4 Video)",
        "http://redirector.c.youtube.com/videoplayback?id=604ed5ce52eda7ee&itag=22&source=youtube&"
        + "sparams=ip,ipbits,expire,source,id&ip=0.0.0.0&ipbits=0&expire=19000000000&signature="
        + "513F28C7FDCBEC60A66C86C9A393556C99DC47FB.04C88036EEE12565A1ED864A875A58F15D8B5300"
        + "&key=ik0", Util.TYPE_OTHER),
    Sample.newSample("Screens 360P (WebM,VP9,No Audio)",
        "https://storage.googleapis.com/exoplayer-test-media-1/gen-3/screens/dash-vod-single-segmen"
        + "t/video-vp9-360.webm", Util.TYPE_OTHER),
    Sample.newSample("Screens 480p (FMP4,H264,No Audio)",
        "https://storage.googleapis.com/exoplayer-test-media-1/gen-3/screens/dash-vod-single-segmen"
        + "t/video-avc-baseline-480.mp4", Util.TYPE_OTHER),
    Sample.newSample("Screens 1080p (FMP4,H264, No Audio)",
        "https://storage.googleapis.com/exoplayer-test-media-1/gen-3/screens/dash-vod-single-segmen"
        + "t/video-137.mp4", Util.TYPE_OTHER),
    Sample.newSample("Screens (FMP4,AAC Audio)",
        "https://storage.googleapis.com/exoplayer-test-media-1/gen-3/screens/dash-vod-single-segmen"
        + "t/audio-141.mp4", Util.TYPE_OTHER),
    Sample.newSample("Google Play (MP3 Audio)",
        "http://storage.googleapis.com/exoplayer-test-media-0/play.mp3", Util.TYPE_OTHER),
    Sample.newSample("Google Play (Ogg/Vorbis Audio)",
        "https://storage.googleapis.com/exoplayer-test-media-1/ogg/play.ogg", Util.TYPE_OTHER),
    Sample.newSample("Google Glass (WebM Video with Vorbis Audio)",
        "http://demos.webmproject.org/exoplayer/glass_vp9_vorbis.webm", Util.TYPE_OTHER),
    Sample.newSample("Google Glass (VP9 in MP4/ISO-BMFF)",
        "http://demos.webmproject.org/exoplayer/glass.mp4", Util.TYPE_OTHER),
    Sample.newSample("Google Glass DASH - VP9 and Opus",
        "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9_opus.mpd",
        Util.TYPE_DASH),
    Sample.newSample("Big Buck Bunny (FLV Video)",
        "http://vod.leasewebcdn.com/bbb.flv?ri=1024&rs=150&start=0", Util.TYPE_OTHER),
  };

  public static final Sample[] EXTENSION = new Sample[] {
    Sample.newExtensionSample("Google Glass DASH - VP9 Only",
        "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9.mpd",
        Util.TYPE_DASH),
    Sample.newExtensionSample("Google Glass DASH - VP9 and Vorbis",
        "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9_vorbis.mpd",
        Util.TYPE_DASH),
    Sample.newExtensionSample("Google Glass DASH - VP9 and Opus",
        "http://demos.webmproject.org/dash/201410/vp9_glass/manifest_vp9_opus.mpd",
        Util.TYPE_DASH),
  };

  private Samples() {}

}
