/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.extractor.text.cea;

import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.CuesWithTimingSubtitle;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleDecoder;
import androidx.media3.extractor.text.SubtitleInputBuffer;
import androidx.media3.extractor.text.SubtitleParser.OutputOptions;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/** A {@link SubtitleDecoder} for CEA-708 (also known as "EIA-708"). */
@UnstableApi
public final class Cea708Decoder extends CeaDecoder {
  private final Cea708Parser cea708Parser;

  @Nullable private CuesWithTiming cues;
  private boolean isNewSubtitleDataAvailable;

  /**
   * Constructs an instance.
   *
   * @param parser A {@link Cea708Parser} to parse the subtitle data.
   */
  public Cea708Decoder(Cea708Parser parser) {
    this.cea708Parser = parser;
  }

  @Override
  public String getName() {
    return "Cea708Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    isNewSubtitleDataAvailable = false;
    cues = null;
    cea708Parser.reset();
  }

  @Override
  protected boolean isNewSubtitleDataAvailable() {
    return isNewSubtitleDataAvailable;
  }

  @Override
  protected Subtitle createSubtitle() {
    isNewSubtitleDataAvailable = false;
    return new CuesWithTimingSubtitle(ImmutableList.of(checkNotNull(cues)));
  }

  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    ByteBuffer subtitleData = checkNotNull(inputBuffer.data);

    cea708Parser.parse(
        subtitleData.array(),
        /* offset= */ subtitleData.arrayOffset(),
        /* length= */ subtitleData.limit(),
        OutputOptions.allCues(),
        /* output= */ cues -> {
          isNewSubtitleDataAvailable = true;
          this.cues =
              new CuesWithTiming(
                  cues.cues, /* startTimeUs= */ C.TIME_UNSET, /* durationUs= */ C.TIME_UNSET);
        });
  }
}
