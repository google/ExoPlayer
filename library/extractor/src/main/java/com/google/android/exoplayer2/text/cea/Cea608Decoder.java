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
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.text.SubtitleParser.OutputOptions;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/**
 * A {@link SubtitleDecoder} for CEA-608 (also known as "line 21 captions" and "EIA-608").
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class Cea608Decoder extends CeaDecoder {

  /**
   * The minimum value for the {@code validDataChannelTimeoutMs} constructor parameter permitted by
   * ANSI/CTA-608-E R-2014 Annex C.9.
   */
  public static final long MIN_DATA_CHANNEL_TIMEOUT_MS = Cea608Parser.MIN_DATA_CHANNEL_TIMEOUT_MS;

  private static final CuesWithTiming EMPTY_CUES =
      new CuesWithTiming(
          ImmutableList.of(), /* startTimeUs= */ C.TIME_UNSET, /* durationUs= */ C.TIME_UNSET);

  private final Cea608Parser cea608Parser;

  @Nullable private CuesWithTiming cues;
  private boolean isNewSubtitleDataAvailable;
  private long lastCueUpdateUs;

  /**
   * Constructs an instance.
   *
   * @param mimeType The MIME type of the CEA-608 data.
   * @param accessibilityChannel The Accessibility channel, or {@link Format#NO_VALUE} if unknown.
   * @param validDataChannelTimeoutMs The timeout (in milliseconds) permitted by ANSI/CTA-608-E
   *     R-2014 Annex C.9 to clear "stuck" captions where no removal control code is received. The
   *     timeout should be at least {@link #MIN_DATA_CHANNEL_TIMEOUT_MS} or {@link C#TIME_UNSET} for
   *     no timeout. This applies an upper-bound on the duration of a single caption.
   */
  public Cea608Decoder(String mimeType, int accessibilityChannel, long validDataChannelTimeoutMs) {
    this.cea608Parser = new Cea608Parser(mimeType, accessibilityChannel, validDataChannelTimeoutMs);
    lastCueUpdateUs = C.TIME_UNSET;
  }

  @Override
  public String getName() {
    return "Cea608Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    isNewSubtitleDataAvailable = false;
    cues = null;
    cea608Parser.reset();
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Nullable
  @Override
  public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException {
    SubtitleOutputBuffer outputBuffer = super.dequeueOutputBuffer();
    if (outputBuffer != null) {
      return outputBuffer;
    }
    if (shouldClearStuckCaptions()) {
      outputBuffer = getAvailableOutputBuffer();
      if (outputBuffer != null) {
        cues = EMPTY_CUES;
        lastCueUpdateUs = C.TIME_UNSET;
        Subtitle subtitle = createSubtitle();
        outputBuffer.setContent(getPositionUs(), subtitle, Format.OFFSET_SAMPLE_RELATIVE);
        return outputBuffer;
      }
    }
    return null;
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

  @SuppressWarnings("ByteBufferBackingArray")
  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    ByteBuffer subtitleData = checkNotNull(inputBuffer.data);

    cea608Parser.parse(
        subtitleData.array(),
        /* offset= */ subtitleData.arrayOffset(),
        /* length= */ subtitleData.limit(),
        OutputOptions.allCues(),
        /* output= */ cues -> {
          isNewSubtitleDataAvailable = true;
          // Remove the 'stuck captions' duration - in this class the clearing of stuck captions is
          // implemented by shouldClearStuckCaptions() below.
          this.cues =
              new CuesWithTiming(
                  cues.cues, /* startTimeUs= */ C.TIME_UNSET, /* durationUs= */ C.TIME_UNSET);
        });
  }

  /** See ANSI/CTA-608-E R-2014 Annex C.9 for Caption Erase Logic. */
  private boolean shouldClearStuckCaptions() {
    if (cea608Parser.validDataChannelTimeoutUs == C.TIME_UNSET || lastCueUpdateUs == C.TIME_UNSET) {
      return false;
    }
    long elapsedUs = getPositionUs() - lastCueUpdateUs;
    return elapsedUs >= cea608Parser.validDataChannelTimeoutUs;
  }
}
