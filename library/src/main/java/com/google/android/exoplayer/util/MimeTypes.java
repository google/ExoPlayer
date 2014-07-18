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

  public static final String BASE_TYPE_VIDEO = "video";
  public static final String BASE_TYPE_AUDIO = "audio";
  public static final String BASE_TYPE_TEXT = "text";
  public static final String BASE_TYPE_APPLICATION = "application";

  public static final String VIDEO_MP4 = BASE_TYPE_VIDEO + "/mp4";
  public static final String VIDEO_WEBM = BASE_TYPE_VIDEO + "/webm";
  public static final String VIDEO_H264 = BASE_TYPE_VIDEO + "/avc";
  public static final String VIDEO_VP9 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9";

  public static final String AUDIO_MP4 = BASE_TYPE_AUDIO + "/mp4";
  public static final String AUDIO_AAC = BASE_TYPE_AUDIO + "/mp4a-latm";

  public static final String TEXT_VTT = BASE_TYPE_TEXT + "/vtt";

  public static final String APPLICATION_TTML = BASE_TYPE_APPLICATION + "/ttml+xml";

  private MimeTypes() {}

  /**
   * Returns the top-level type of {@code mimeType}.
   *
   * @param mimeType The mimeType whose top-level type is required.
   * @return The top-level type.
   */
  public static String getTopLevelType(String mimeType) {
    int indexOfSlash = mimeType.indexOf('/');
    if (indexOfSlash == -1) {
      throw new IllegalArgumentException("Invalid mime type: " + mimeType);
    }
    return mimeType.substring(0, indexOfSlash);
  }

  /**
   * Whether the top-level type of {@code mimeType} is audio.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is audio.
   */
  public static boolean isAudio(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_AUDIO);
  }

  /**
   * Whether the top-level type of {@code mimeType} is video.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is video.
   */
  public static boolean isVideo(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_VIDEO);
  }

  /**
   * Whether the top-level type of {@code mimeType} is text.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is text.
   */
  public static boolean isText(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_TEXT);
  }

  /**
   * Whether the top-level type of {@code mimeType} is application.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is application.
   */
  public static boolean isApplication(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_APPLICATION);
  }

  /**
   * Whether the mimeType is {@link #APPLICATION_TTML}.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the mimeType is {@link #APPLICATION_TTML}.
   */
  public static boolean isTtml(String mimeType) {
    return mimeType.equals(APPLICATION_TTML);
  }

}
