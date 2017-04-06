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
package com.google.android.exoplayer2.text.dvb;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;

import java.util.List;

/**
 * A {@link SimpleSubtitleDecoder} for DVB Subtitles.
 */
public final class DvbDecoder extends SimpleSubtitleDecoder {

  private final DvbParser parser;

  public DvbDecoder(List<byte[]> initializationData) {
    super("DvbDecoder");

    int subtitleCompositionPage = 1;
    int subtitleAncillaryPage = 1;
    int flags = 0;
    byte[] tempByteArray;

    if ((tempByteArray = initializationData.get(0)) != null && tempByteArray.length == 5) {
      if (tempByteArray[0] == 0x01) {
        flags |= DvbParser.FLAG_PES_STRIPPED_DVBSUB;
      }
      subtitleCompositionPage = ((tempByteArray[1] & 0xFF) << 8) | (tempByteArray[2] & 0xFF);
      subtitleAncillaryPage = ((tempByteArray[3] & 0xFF) << 8) | (tempByteArray[4] & 0xFF);
    }

    parser = new DvbParser(subtitleCompositionPage, subtitleAncillaryPage, flags);
  }

  @Override
  protected DvbSubtitle decode(byte[] data, int length) {
    return new DvbSubtitle(parser.dvbSubsDecode(data, length));
  }
}