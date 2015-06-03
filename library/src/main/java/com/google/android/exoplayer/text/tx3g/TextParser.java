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
package com.google.android.exoplayer.text.tx3g;

import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple Text parser that supports tx3g atom.
 *
 * Only support to parse a single text track at this version ,
 * since ExtractorSampleSource does not handle multiple audio/video tracks.
 *
 */
public class TextParser implements SubtitleParser {
  private static final String TAG = "TextParser";

  private final List<SubtitleData> subtitleList;
  private static final int MAX_SUBTITLE_COUNT = 4;
  public TextParser() {

    subtitleList = new LinkedList<SubtitleData>();
  }

  @Override
  public Subtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
  throws IOException {

    DataInputStream in  = new DataInputStream(inputStream);
    String text = in.readUTF();
    text = (text == null) ? "" : text;

    SubtitleData cue = new SubtitleData(startTimeUs, text);

    while (subtitleList.size() > MAX_SUBTITLE_COUNT) {
      subtitleList.remove(0);
    }

    subtitleList.add(cue);

    Collections.sort(subtitleList, new Comparator<SubtitleData>() {
      @Override
      public int compare(SubtitleData o1 , SubtitleData o2) {
        if (o1.startTimePosUs < o2.startTimePosUs)
          return -1;
        if (o1.startTimePosUs > o2.startTimePosUs)
          return 1;
        return 0;
      }
    });
    return new TextSubtitle(subtitleList);
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_TX3G.equals(mimeType);
  }
}
