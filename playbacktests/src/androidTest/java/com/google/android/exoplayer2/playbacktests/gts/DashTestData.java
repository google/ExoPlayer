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
package com.google.android.exoplayer2.playbacktests.gts;

import com.google.android.exoplayer2.util.Util;

/**
 * Test data for {@link DashTest} and {@link DashWidevineOfflineTest).
 */
public final class DashTestData {

  // Clear content manifests.
  public static final String H264_MANIFEST = "manifest-h264.mpd";
  public static final String H265_MANIFEST = "manifest-h265.mpd";
  public static final String VP9_MANIFEST = "manifest-vp9.mpd";
  public static final String H264_23_MANIFEST = "manifest-h264-23.mpd";
  public static final String H264_24_MANIFEST = "manifest-h264-24.mpd";
  public static final String H264_29_MANIFEST = "manifest-h264-29.mpd";
  // Widevine encrypted content manifests.
  public static final String WIDEVINE_H264_MANIFEST_PREFIX = "manifest-h264-enc";
  public static final String WIDEVINE_H265_MANIFEST_PREFIX = "manifest-h265-enc";
  public static final String WIDEVINE_VP9_MANIFEST_PREFIX = "manifest-vp9-enc";
  public static final String WIDEVINE_H264_23_MANIFEST_PREFIX = "manifest-h264-23-enc";
  public static final String WIDEVINE_H264_24_MANIFEST_PREFIX = "manifest-h264-24-enc";
  public static final String WIDEVINE_H264_29_MANIFEST_PREFIX = "manifest-h264-29-enc";

  public static final String AAC_AUDIO_REPRESENTATION_ID = "141";
  public static final String H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "avc-baseline-240";
  public static final String H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "avc-baseline-480";
  public static final String H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "avc-main-240";
  public static final String H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "avc-main-480";
  // The highest quality H264 format mandated by the Android CDD.
  public static final String H264_CDD_FIXED = Util.SDK_INT < 23
      ? H264_BASELINE_480P_VIDEO_REPRESENTATION_ID : H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  public static final String[] H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
      H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
      H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  public static final String H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-23";
  public static final String H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-24";
  public static final String H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID =
      "avc-baseline-480-29";

  public static final String H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "hevc-main-288";
  public static final String H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "hevc-main-360";
  // The highest quality H265 format mandated by the Android CDD.
  public static final String H265_CDD_FIXED = H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  public static final String[] H265_CDD_ADAPTIVE =
      new String[] {
          H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  public static final String VORBIS_AUDIO_REPRESENTATION_ID = "4";
  public static final String VP9_180P_VIDEO_REPRESENTATION_ID = "0";
  public static final String VP9_360P_VIDEO_REPRESENTATION_ID = "1";
  // The highest quality VP9 format mandated by the Android CDD.
  public static final String VP9_CDD_FIXED = VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  public static final String[] VP9_CDD_ADAPTIVE =
      new String[] {
          VP9_180P_VIDEO_REPRESENTATION_ID,
          VP9_360P_VIDEO_REPRESENTATION_ID};

  // Widevine encrypted content representation ids.
  public static final String WIDEVINE_AAC_AUDIO_REPRESENTATION_ID = "0";
  public static final String WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID = "1";
  public static final String WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID = "3";
  public static final String WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID = "4";
  // The highest quality H264 format mandated by the Android CDD.
  public static final String WIDEVINE_H264_CDD_FIXED = Util.SDK_INT < 23
      ? WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID
      : WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID;
  // Multiple H264 formats mandated by the Android CDD. Note: The CDD actually mandated main profile
  // support from API level 23, but we opt to test only from 24 due to known issues on API level 23
  // when switching between baseline and main profiles on certain devices.
  public static final String[] WIDEVINE_H264_CDD_ADAPTIVE = Util.SDK_INT < 24
      ? new String[] {
      WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
      WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID}
      : new String[] {
          WIDEVINE_H264_BASELINE_240P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_BASELINE_480P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_MAIN_240P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H264_MAIN_480P_VIDEO_REPRESENTATION_ID};

  public static final String WIDEVINE_H264_BASELINE_480P_23FPS_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_H264_BASELINE_480P_24FPS_VIDEO_REPRESENTATION_ID = "2";
  public static final String WIDEVINE_H264_BASELINE_480P_29FPS_VIDEO_REPRESENTATION_ID = "2";

  public static final String WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID = "1";
  public static final String WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID = "2";
  // The highest quality H265 format mandated by the Android CDD.
  public static final String WIDEVINE_H265_CDD_FIXED =
      WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID;
  // Multiple H265 formats mandated by the Android CDD.
  public static final String[] WIDEVINE_H265_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_H265_BASELINE_288P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_H265_BASELINE_360P_VIDEO_REPRESENTATION_ID};

  public static final String WIDEVINE_VORBIS_AUDIO_REPRESENTATION_ID = "0";
  public static final String WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID = "1";
  public static final String WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID = "2";
  // The highest quality VP9 format mandated by the Android CDD.
  public static final String WIDEVINE_VP9_CDD_FIXED = VP9_360P_VIDEO_REPRESENTATION_ID;
  // Multiple VP9 formats mandated by the Android CDD.
  public static final String[] WIDEVINE_VP9_CDD_ADAPTIVE =
      new String[] {
          WIDEVINE_VP9_180P_VIDEO_REPRESENTATION_ID,
          WIDEVINE_VP9_360P_VIDEO_REPRESENTATION_ID};

  private DashTestData() {
  }

}
