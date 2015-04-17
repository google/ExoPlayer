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

import java.util.Locale;

/**
 * Holds statically defined sample definitions.
 */
/* package */ class Samples {

  public static class Sample {

    public final String name;
    public final String contentId;
    public final String uri;
    public final int type;

    public Sample(String name, String uri, int type) {
      this(name, name.toLowerCase(Locale.US).replaceAll("\\s", ""), uri, type);
    }

    public Sample(String name, String contentId, String uri, int type) {
      this.name = name;
      this.contentId = contentId;
      this.uri = uri;
      this.type = type;
    }

  }

  public static final Sample[] YOUTUBE_DASH_MP4 = new Sample[] {
    new Sample("Google Glass",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&"
        + "ipbits=0&expire=19000000000&signature=255F6B3C07C753C88708C07EA31B7A1A10703C8D."
        + "2D6A28B21F921D0B245CDCF36F7EB54A2B5ABFC2&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("Google Play",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=7181C59D0252B285D593E1B61D985D5B7C98DE2A."
        + "5B445837F55A40E0F28AACAA047982E372D177E2&key=ik0", DemoUtil.TYPE_DASH),
  };

  public static final Sample[] YOUTUBE_DASH_WEBM = new Sample[] {
    new Sample("Google Glass",
        "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=A3EC7EE53ABE601B357F7CAB8B54AD0702CA85A7."
        + "446E9C38E47E3EDAF39E0163C390FF83A7944918&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("Google Play",
        "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
        + "as=fmp4_audio_clear,webm2_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
        + "expire=19000000000&signature=B752B262C6D7262EC4E4EB67901E5D8F7058A81D."
        + "C0358CE1E335417D9A8D88FF192F0D5D8F6DA1B6&key=ik0", DemoUtil.TYPE_DASH),
  };

  public static final Sample[] SMOOTHSTREAMING = new Sample[] {
    new Sample("Super speed",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264/SuperSpeedway_720.ism",
        DemoUtil.TYPE_SS),
    new Sample("Super speed (PlayReady)",
        "http://playready.directtaps.net/smoothstreaming/SSWSS720H264PR/SuperSpeedway_720.ism",
        DemoUtil.TYPE_SS),
  };

  public static final Sample[] WIDEVINE_GTS = new Sample[] {
    new Sample("WV: HDCP not specified", "d286538032258a1c",
        "http://www.youtube.com/api/manifest/dash/id/d286538032258a1c/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=41EA40A027A125A16292E0A5E3277A3B5FA9B938."
        + "0BB075C396FFDDC97E526E8F77DC26FF9667D0D6&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("WV: HDCP not required", "48fcc369939ac96c",
        "http://www.youtube.com/api/manifest/dash/id/48fcc369939ac96c/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=315911BDCEED0FB0C763455BDCC97449DAAFA9E8."
        + "5B41E2EB411F797097A359D6671D2CDE26272373&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("WV: HDCP required", "e06c39f1151da3df",
        "http://www.youtube.com/api/manifest/dash/id/e06c39f1151da3df/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=A47A1E13E7243BD567601A75F79B34644D0DC592."
        + "B09589A34FA23527EFC1552907754BB8033870BD&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("WV: Secure video path required", "0894c7c8719b28a0",
        "http://www.youtube.com/api/manifest/dash/id/0894c7c8719b28a0/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=2847EE498970F6B45176766CD2802FEB4D4CB7B2."
        + "A1CA51EC40A1C1039BA800C41500DD448C03EEDA&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("WV: HDCP + secure video path required", "efd045b1eb61888a",
        "http://www.youtube.com/api/manifest/dash/id/efd045b1eb61888a/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=61611F115EEEC7BADE5536827343FFFE2D83D14F."
        + "2FDF4BFA502FB5865C5C86401314BDDEA4799BD0&key=ik0", DemoUtil.TYPE_DASH),
    new Sample("WV: 30s license duration (fails at ~30s)", "f9a34cab7b05881a",
        "http://www.youtube.com/api/manifest/dash/id/f9a34cab7b05881a/source/youtube?"
        + "as=fmp4_audio_cenc,fmp4_sd_hd_cenc&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0"
        + "&expire=19000000000&signature=88DC53943385CED8CF9F37ADD9E9843E3BF621E6."
        + "22727BB612D24AA4FACE4EF62726F9461A9BF57A&key=ik0", DemoUtil.TYPE_DASH),
  };

  public static final Sample[] HLS = new Sample[] {
    new Sample("Apple master playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/"
        + "bipbop_4x3_variant.m3u8", DemoUtil.TYPE_HLS),
    new Sample("Apple master playlist advanced",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_16x9/"
        + "bipbop_16x9_variant.m3u8", DemoUtil.TYPE_HLS),
    new Sample("Apple TS media playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/"
        + "prog_index.m3u8", DemoUtil.TYPE_HLS),
    new Sample("Apple AAC media playlist",
        "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear0/"
        + "prog_index.m3u8", DemoUtil.TYPE_HLS),
    new Sample("Apple ID3 metadata", "http://devimages.apple.com/samplecode/adDemo/ad.m3u8",
        DemoUtil.TYPE_HLS),
  };

  public static final Sample[] MISC = new Sample[] {
    new Sample("Dizzy", "http://html5demos.com/assets/dizzy.mp4",
        DemoUtil.TYPE_OTHER),
    new Sample("Dizzy (https->http redirect)", "https://goo.gl/MtUDEj",
        DemoUtil.TYPE_OTHER),
    new Sample("Apple AAC 10s", "https://devimages.apple.com.edgekey.net/"
        + "streaming/examples/bipbop_4x3/gear0/fileSequence0.aac",
        DemoUtil.TYPE_AAC),
    new Sample("Big Buck Bunny (MP4 Video)",
        "http://redirector.c.youtube.com/videoplayback?id=604ed5ce52eda7ee&itag=22&source=youtube"
        + "&sparams=ip,ipbits,expire&ip=0.0.0.0&ipbits=0&expire=19000000000&signature="
        + "2E853B992F6CAB9D28CA3BEBD84A6F26709A8A55.94344B0D8BA83A7417AAD24DACC8C71A9A878ECE"
        + "&key=ik0",
        DemoUtil.TYPE_MP4),
    new Sample("Google Play (MP3 Audio)",
        "http://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
        DemoUtil.TYPE_MP3),
  };

  private Samples() {}

}
