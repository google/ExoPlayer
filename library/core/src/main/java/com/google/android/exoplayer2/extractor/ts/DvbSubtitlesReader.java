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
package com.google.android.exoplayer2.extractor.ts;


import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Output PES packets to a {@link TrackOutput}.
 */
public final class DvbSubtitlesReader implements ElementaryStreamReader {

  private class SubtitleTrack {
    private String language;
    private List<byte[]> initializationData;
  }

  private List<SubtitleTrack> subtitles = new ArrayList<>();

  private long sampleTimeUs;
  private int sampleBytesWritten;
  private boolean writingSample;

  private List<TrackOutput> outputTracks = new ArrayList<>();

  public DvbSubtitlesReader(TsPayloadReader.EsInfo esInfo) {
    int pos = 2;

    while (pos < esInfo.descriptorBytes.length) {
      SubtitleTrack subtitle = new SubtitleTrack();
      subtitle.language = new String(new byte[] {
              esInfo.descriptorBytes[pos],
              esInfo.descriptorBytes[pos + 1],
              esInfo.descriptorBytes[pos + 2]});

      if (((esInfo.descriptorBytes[pos + 3] & 0xF0 ) >> 4 ) == 2 ) {
        subtitle.language += " for hard of hearing";
      }

      subtitle.initializationData = Collections.singletonList(new byte[] {(byte) 0x00,
          esInfo.descriptorBytes[pos + 4], esInfo.descriptorBytes[pos + 5],
          esInfo.descriptorBytes[pos + 6], esInfo.descriptorBytes[pos + 7]});

      subtitles.add(subtitle);
      pos += 8;
    }
  }


  @Override
  public void seek() {
    writingSample = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    TrackOutput output;
    SubtitleTrack subtitle;

    for (int i = 0; i < subtitles.size(); i++) {
      subtitle = subtitles.get(i);
      idGenerator.generateNewId();
      output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
      output.format(Format.createImageSampleFormat(idGenerator.getFormatId(),
              MimeTypes.APPLICATION_DVBSUBS, null, Format.NO_VALUE,
              subtitle.initializationData, subtitle.language, null));
      outputTracks.add(output);
    }
  }


  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    if (!dataAlignmentIndicator) {
      return;
    }
    writingSample = true;
    sampleTimeUs = pesTimeUs;
    sampleBytesWritten = 0;
  }

  @Override
  public void packetFinished() {
    TrackOutput output;

    for (int i = 0; i < outputTracks.size(); i++) {
      output = outputTracks.get(i);
      output.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null);
    }
    writingSample = false;
  }

  @Override
  public void consume(ParsableByteArray data) {
    if (writingSample) {
      int bytesAvailable = data.bytesLeft();
      TrackOutput output;
      int dataPosition = data.getPosition();

      for (int i = 0; i < outputTracks.size(); i++) {
        data.setPosition(dataPosition);
        output = outputTracks.get(i);
        output.sampleData(data, bytesAvailable);
      }

      sampleBytesWritten += bytesAvailable;
    }
  }
}