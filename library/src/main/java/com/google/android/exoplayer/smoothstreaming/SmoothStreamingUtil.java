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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;

import android.util.Base64;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SmoothStreamingUtil {

  private SmoothStreamingUtil() {}

  /**
   * Builds a {@link MediaFormat} for the specified track of the specified {@link StreamElement}.
   *
   * @param element The stream element.
   * @param track The index of the track for which to build the format.
   * @return The format.
   */
  public static MediaFormat getMediaFormat(StreamElement element, int track) {
    TrackElement trackElement = element.tracks[track];
    String mimeType = trackElement.mimeType;
    if (element.type == StreamElement.TYPE_VIDEO) {
      MediaFormat format = MediaFormat.createVideoFormat(mimeType, -1, trackElement.maxWidth,
          trackElement.maxHeight, Arrays.asList(trackElement.csd));
      format.setMaxVideoDimensions(element.maxWidth, element.maxHeight);
      return format;
    } else if (element.type == StreamElement.TYPE_AUDIO) {
      List<byte[]> csd;
      if (trackElement.csd != null) {
        csd = Arrays.asList(trackElement.csd);
      } else {
        csd = Collections.singletonList(CodecSpecificDataUtil.buildAudioSpecificConfig(
            trackElement.sampleRate, trackElement.numChannels));
      }
      MediaFormat format = MediaFormat.createAudioFormat(mimeType, -1, trackElement.numChannels,
          trackElement.sampleRate, csd);
      return format;
    }
    // TODO: Do subtitles need a format? MediaFormat supports KEY_LANGUAGE.
    return null;
  }

  public static byte[] getKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

}
