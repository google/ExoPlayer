/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.text.ssa;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import java.util.List;

import static com.google.android.exoplayer2.mediacodec.MediaCodecInfo.TAG;

/**
 * A representation of an SSA/ASS subtitle.
 */
/* package */ public final class SsaSubtitle implements Subtitle {

  private final List<List<Cue>> cues;
  private final List<Long> cueTimesUs;
  private final SsaDialogueFormat format;
  public SsaDecoder decoder;

  /**
   * @param cues The cues in the subtitle.
   * @param cueTimesUs The cue times, in microseconds.
   */
  public SsaSubtitle(List<List<Cue>> cues, List<Long> cueTimesUs, SsaDialogueFormat format) {
    this.cues = cues;
    this.cueTimesUs = cueTimesUs;
    this.format = format;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index = Util.binarySearchCeil(cueTimesUs, timeUs, false, false);
    return index < cueTimesUs.size() ? index : C.INDEX_UNSET;
  }

  @Override
  public int getEventTimeCount() {
    return cueTimesUs.size();
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkArgument(index < cueTimesUs.size());
    return cueTimesUs.get(index);
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    int index = Util.binarySearchFloor(cueTimesUs, timeUs, true, false);
    if (index == -1) {
      // timeUs is earlier than the start of the first cue.
      return Collections.emptyList();
    } else {
      return cues.get(index);
    }
  }

  public void addSubtitles(ParsableByteArray data) {
    @Nullable String currentLine;
    while ((currentLine = data.readLine()) != null) {
      if (currentLine.startsWith("Dialogue:")) {
        if (format == null) {
          Log.w(TAG, "Skipping dialogue line before complete format: " + currentLine);
          continue;
        }
        decoder.parseDialogueLine(currentLine, format, cues, cueTimesUs);
      }
    }
  }
}
