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
import java.util.LinkedList;
import java.util.List;

/**
 * A simple Text parser that supports tx3g atom for mpeg4 files.
 *
 * @see <a href="http://en.wikipedia.org/wiki/MPEG-4_Part_17">ISO/IEC 14496-17:2006</a>
 */
public class TextParser implements SubtitleParser {
  private static final String TAG = "TextParser";

  private final List<SubtitleData> mSubtitleList;

  /**
   * Equivalent to {@code TextParser()}.
   */
  public TextParser() {
    mSubtitleList = new LinkedList<SubtitleData>();
  }

  @Override
  public Subtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
  throws IOException {

    DataInputStream in  = new DataInputStream(inputStream);
    String text = in.readUTF();

    SubtitleData cue = new SubtitleData(text, startTimeUs);
    mSubtitleList.add(cue);

    Collections.sort(mSubtitleList);
    return  new TextSubtitle(mSubtitleList);
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.TEXT_TX3G.equals(mimeType);
  }
}
