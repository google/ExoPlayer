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
package com.google.android.exoplayer.util;

/**
 * Defines common MIME types and helper methods.
 */
public class MimeTypes {

  public static final String VIDEO_MP4 = "video/mp4";
  public static final String VIDEO_WEBM = "video/webm";
  public static final String VIDEO_H264 = "video/avc";
  public static final String VIDEO_VP9 = "video/x-vnd.on2.vp9";
  public static final String AUDIO_MP4 = "audio/mp4";
  public static final String AUDIO_AAC = "audio/mp4a-latm";
  public static final String TEXT_VTT = "text/vtt";
  public static final String APPLICATION_TTML = "application/ttml+xml";

  private MimeTypes() {}

  /**
   * Whether the top-level type of {@code mimeType} is audio.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is audio.
   */
  public static boolean isAudio(String mimeType) {
    return mimeType.startsWith("audio/");
  }

  /**
   * Whether the top-level type of {@code mimeType} is video.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is video.
   */
  public static boolean isVideo(String mimeType) {
    return mimeType.startsWith("video/");
  }

  /**
   * Whether the top-level type of {@code mimeType} is text.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is text.
   */
  public static boolean isText(String mimeType) {
    return mimeType.startsWith("text/");
  }

}
