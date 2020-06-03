/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.net.Uri;
import androidx.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Filename related utility methods. */
public final class FilenameUtil {

  /**
   * File formats. One of {@link #FILE_FORMAT_UNKNOWN}, {@link #FILE_FORMAT_AC3}, {@link
   * #FILE_FORMAT_AC4}, {@link #FILE_FORMAT_ADTS}, {@link #FILE_FORMAT_AMR}, {@link
   * #FILE_FORMAT_FLAC}, {@link #FILE_FORMAT_FLV}, {@link #FILE_FORMAT_MATROSKA}, {@link
   * #FILE_FORMAT_MP3}, {@link #FILE_FORMAT_MP4}, {@link #FILE_FORMAT_OGG}, {@link #FILE_FORMAT_PS},
   * {@link #FILE_FORMAT_TS}, {@link #FILE_FORMAT_WAV} and {@link #FILE_FORMAT_WEBVTT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    FILE_FORMAT_UNKNOWN,
    FILE_FORMAT_AC3,
    FILE_FORMAT_AC4,
    FILE_FORMAT_ADTS,
    FILE_FORMAT_AMR,
    FILE_FORMAT_FLAC,
    FILE_FORMAT_FLV,
    FILE_FORMAT_MATROSKA,
    FILE_FORMAT_MP3,
    FILE_FORMAT_MP4,
    FILE_FORMAT_OGG,
    FILE_FORMAT_PS,
    FILE_FORMAT_TS,
    FILE_FORMAT_WAV,
    FILE_FORMAT_WEBVTT
  })
  public @interface FileFormat {}
  /** Unknown file format. */
  public static final int FILE_FORMAT_UNKNOWN = -1;
  /** File format for AC-3 and E-AC-3. */
  public static final int FILE_FORMAT_AC3 = 0;
  /** File format for AC-4. */
  public static final int FILE_FORMAT_AC4 = 1;
  /** File format for ADTS. */
  public static final int FILE_FORMAT_ADTS = 2;
  /** File format for AMR. */
  public static final int FILE_FORMAT_AMR = 3;
  /** File format for FLAC. */
  public static final int FILE_FORMAT_FLAC = 4;
  /** File format for FLV. */
  public static final int FILE_FORMAT_FLV = 5;
  /** File format for Matroska and WebM. */
  public static final int FILE_FORMAT_MATROSKA = 6;
  /** File format for MP3. */
  public static final int FILE_FORMAT_MP3 = 7;
  /** File format for MP4. */
  public static final int FILE_FORMAT_MP4 = 8;
  /** File format for Ogg. */
  public static final int FILE_FORMAT_OGG = 9;
  /** File format for MPEG-PS. */
  public static final int FILE_FORMAT_PS = 10;
  /** File format for MPEG-TS. */
  public static final int FILE_FORMAT_TS = 11;
  /** File format for WAV. */
  public static final int FILE_FORMAT_WAV = 12;
  /** File format for WebVTT. */
  public static final int FILE_FORMAT_WEBVTT = 13;

  private static final String FILE_EXTENSION_AC3 = ".ac3";
  private static final String FILE_EXTENSION_EC3 = ".ec3";
  private static final String FILE_EXTENSION_AC4 = ".ac4";
  private static final String FILE_EXTENSION_ADTS = ".adts";
  private static final String FILE_EXTENSION_AAC = ".aac";
  private static final String FILE_EXTENSION_AMR = ".amr";
  private static final String FILE_EXTENSION_FLAC = ".flac";
  private static final String FILE_EXTENSION_FLV = ".flv";
  private static final String FILE_EXTENSION_PREFIX_MK = ".mk";
  private static final String FILE_EXTENSION_WEBM = ".webm";
  private static final String FILE_EXTENSION_PREFIX_OG = ".og";
  private static final String FILE_EXTENSION_OPUS = ".opus";
  private static final String FILE_EXTENSION_MP3 = ".mp3";
  private static final String FILE_EXTENSION_MP4 = ".mp4";
  private static final String FILE_EXTENSION_PREFIX_M4 = ".m4";
  private static final String FILE_EXTENSION_PREFIX_MP4 = ".mp4";
  private static final String FILE_EXTENSION_PREFIX_CMF = ".cmf";
  private static final String FILE_EXTENSION_PS = ".ps";
  private static final String FILE_EXTENSION_MPEG = ".mpeg";
  private static final String FILE_EXTENSION_MPG = ".mpg";
  private static final String FILE_EXTENSION_M2P = ".m2p";
  private static final String FILE_EXTENSION_TS = ".ts";
  private static final String FILE_EXTENSION_PREFIX_TS = ".ts";
  private static final String FILE_EXTENSION_WAV = ".wav";
  private static final String FILE_EXTENSION_WAVE = ".wave";
  private static final String FILE_EXTENSION_VTT = ".vtt";
  private static final String FILE_EXTENSION_WEBVTT = ".webvtt";

  private FilenameUtil() {}

  /**
   * Returns the {@link FileFormat} corresponding to the filename extension of the provided {@link
   * Uri}. The filename is considered to be the last segment of the {@link Uri} path.
   */
  @FileFormat
  public static int getFormatFromExtension(Uri uri) {
    String filename = uri.getLastPathSegment();
    if (filename == null) {
      return FILE_FORMAT_UNKNOWN;
    } else if (filename.endsWith(FILE_EXTENSION_AC3) || filename.endsWith(FILE_EXTENSION_EC3)) {
      return FILE_FORMAT_AC3;
    } else if (filename.endsWith(FILE_EXTENSION_AC4)) {
      return FILE_FORMAT_AC4;
    } else if (filename.endsWith(FILE_EXTENSION_ADTS) || filename.endsWith(FILE_EXTENSION_AAC)) {
      return FILE_FORMAT_ADTS;
    } else if (filename.endsWith(FILE_EXTENSION_AMR)) {
      return FILE_FORMAT_AMR;
    } else if (filename.endsWith(FILE_EXTENSION_FLAC)) {
      return FILE_FORMAT_FLAC;
    } else if (filename.endsWith(FILE_EXTENSION_FLV)) {
      return FILE_FORMAT_FLV;
    } else if (filename.startsWith(
            FILE_EXTENSION_PREFIX_MK,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_MK.length() + 1))
        || filename.endsWith(FILE_EXTENSION_WEBM)) {
      return FILE_FORMAT_MATROSKA;
    } else if (filename.endsWith(FILE_EXTENSION_MP3)) {
      return FILE_FORMAT_MP3;
    } else if (filename.endsWith(FILE_EXTENSION_MP4)
        || filename.startsWith(
            FILE_EXTENSION_PREFIX_M4,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_M4.length() + 1))
        || filename.startsWith(
            FILE_EXTENSION_PREFIX_MP4,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_MP4.length() + 1))
        || filename.startsWith(
            FILE_EXTENSION_PREFIX_CMF,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_CMF.length() + 1))) {
      return FILE_FORMAT_MP4;
    } else if (filename.startsWith(
            FILE_EXTENSION_PREFIX_OG,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_OG.length() + 1))
        || filename.endsWith(FILE_EXTENSION_OPUS)) {
      return FILE_FORMAT_OGG;
    } else if (filename.endsWith(FILE_EXTENSION_PS)
        || filename.endsWith(FILE_EXTENSION_MPEG)
        || filename.endsWith(FILE_EXTENSION_MPG)
        || filename.endsWith(FILE_EXTENSION_M2P)) {
      return FILE_FORMAT_PS;
    } else if (filename.endsWith(FILE_EXTENSION_TS)
        || filename.startsWith(
            FILE_EXTENSION_PREFIX_TS,
            /* toffset= */ filename.length() - (FILE_EXTENSION_PREFIX_TS.length() + 1))) {
      return FILE_FORMAT_TS;
    } else if (filename.endsWith(FILE_EXTENSION_WAV) || filename.endsWith(FILE_EXTENSION_WAVE)) {
      return FILE_FORMAT_WAV;
    } else if (filename.endsWith(FILE_EXTENSION_VTT) || filename.endsWith(FILE_EXTENSION_WEBVTT)) {
      return FILE_FORMAT_WEBVTT;
    } else {
      return FILE_FORMAT_UNKNOWN;
    }
  }
}
