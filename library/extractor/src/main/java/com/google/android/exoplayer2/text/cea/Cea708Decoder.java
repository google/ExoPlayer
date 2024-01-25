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
package com.google.android.exoplayer2.text.cea;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.CuesWithTiming;
import com.google.android.exoplayer2.text.CuesWithTimingSubtitle;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleParser.OutputOptions;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * A {@link SubtitleDecoder} for CEA-708 (also known as "EIA-708").
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Cea708Decoder extends CeaDecoder {
  private final Cea708Parser cea708Parser;

  @Nullable private CuesWithTiming cues;
  private boolean isNewSubtitleDataAvailable;

  /**
   * Constructs an instance.
   *
   * @param accessibilityChannel The accessibility channel, or {@link Format#NO_VALUE} if unknown.
   * @param initializationData Optional initialization data for the decoder. If present, it must
   *     conform to the structure created by {@link
   *     CodecSpecificDataUtil#buildCea708InitializationData}.
   */
  public Cea708Decoder(int accessibilityChannel, @Nullable List<byte[]> initializationData) {
    this.cea708Parser = new Cea708Parser(accessibilityChannel, initializationData);
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
