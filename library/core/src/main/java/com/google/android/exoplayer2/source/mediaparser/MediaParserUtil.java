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
package com.google.android.exoplayer2.source.mediaparser;

import android.media.MediaFormat;
import android.media.MediaParser;
import com.google.android.exoplayer2.Format;

/**
 * Miscellaneous constants and utility methods related to the {@link MediaParser} integration.
 *
 * <p>For documentation on constants, please see the {@link MediaParser} documentation.
 */
public final class MediaParserUtil {

  public static final String PARAMETER_IN_BAND_CRYPTO_INFO =
      "android.media.mediaparser.inBandCryptoInfo";
  public static final String PARAMETER_INCLUDE_SUPPLEMENTAL_DATA =
      "android.media.mediaparser.includeSupplementalData";
  public static final String PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE =
      "android.media.mediaparser.eagerlyExposeTrackType";
  public static final String PARAMETER_EXPOSE_DUMMY_SEEK_MAP =
      "android.media.mediaparser.exposeDummySeekMap";
  public static final String PARAMETER_EXPOSE_CHUNK_INDEX_AS_MEDIA_FORMAT =
      "android.media.mediaParser.exposeChunkIndexAsMediaFormat";
  public static final String PARAMETER_OVERRIDE_IN_BAND_CAPTION_DECLARATIONS =
      "android.media.mediaParser.overrideInBandCaptionDeclarations";
  public static final String PARAMETER_EXPOSE_CAPTION_FORMATS =
      "android.media.mediaParser.exposeCaptionFormats";
  public static final String PARAMETER_IGNORE_TIMESTAMP_OFFSET =
      "android.media.mediaparser.ignoreTimestampOffset";

  private MediaParserUtil() {}

  /**
   * Returns a {@link MediaFormat} with equivalent {@link MediaFormat#KEY_MIME} and {@link
   * MediaFormat#KEY_CAPTION_SERVICE_NUMBER} to the given {@link Format}.
   */
  public static MediaFormat toCaptionsMediaFormat(Format format) {
    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType);
    if (format.accessibilityChannel != Format.NO_VALUE) {
      mediaFormat.setInteger(MediaFormat.KEY_CAPTION_SERVICE_NUMBER, format.accessibilityChannel);
    }
    return mediaFormat;
  }
}
