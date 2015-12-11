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
package com.google.android.exoplayer.text.mp4webvtt;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SubtitleParser} for Webvtt embedded in a Mp4 container file.
 */
public final class Mp4WebvttParser implements SubtitleParser {

  private static final int BOX_HEADER_SIZE = 8;

  private static final int TYPE_vttc = Util.getIntegerCodeForString("vttc");
  private static final int TYPE_payl = Util.getIntegerCodeForString("payl");

  private final ParsableByteArray sampleData;
  private byte[] inputBytesBuffer;

  public Mp4WebvttParser() {
    sampleData = new ParsableByteArray();
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_MP4VTT.equals(mimeType);
  }

  @Override
  public Subtitle parse(InputStream inputStream) throws IOException {
    // Webvtt in Mp4 samples have boxes inside of them, so we have to do a traditional box parsing:
    // first 4 bytes size and then 4 bytes type.
    int inputStreamByteCount = inputStream.available();
    if (inputBytesBuffer == null || inputBytesBuffer.length < inputStreamByteCount) {
      inputBytesBuffer = new byte[inputStreamByteCount];
    }
    inputStream.read(inputBytesBuffer, 0, inputStreamByteCount);
    sampleData.reset(inputBytesBuffer, inputStreamByteCount);
    List<Cue> resultingCueList = new ArrayList<>();
    while (sampleData.bytesLeft() > 0) {
      if (sampleData.bytesLeft() < BOX_HEADER_SIZE) {
        throw new ParserException("Incomplete Mp4Webvtt Top Level box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      if (boxType == TYPE_vttc) {
        resultingCueList.add(parseVttCueBox(sampleData));
      } else {
        // Peers of the VTTCueBox are still not supported and are skipped.
        sampleData.skipBytes(boxSize - BOX_HEADER_SIZE);
      }
    }
    return new Mp4WebvttSubtitle(resultingCueList);
  }

  private static Cue parseVttCueBox(ParsableByteArray sampleData) throws IOException {
    while (sampleData.bytesLeft() > 0) {
      if (sampleData.bytesLeft() < BOX_HEADER_SIZE) {
        throw new ParserException("Incomplete vtt cue box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      if (boxType == TYPE_payl) {
        int payloadLength = boxSize - BOX_HEADER_SIZE;
        String cueText = new String(sampleData.data, sampleData.getPosition(), payloadLength);
        sampleData.skipBytes(payloadLength);
        return new Cue(cueText.trim());
      } else {
        // Other VTTCueBox children are still not supported and are skipped.
        sampleData.skipBytes(boxSize - BOX_HEADER_SIZE);
      }
    }
    throw new ParserException("VTTCueBox does not contain mandatory payload box.");
  }

}
