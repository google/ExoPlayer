/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtp.extractor;

import com.google.android.exoplayer2.C;

/*package*/ final class RtpTimestampAdjuster {
  private long firstSampleTimestamp;
  private long timestampOffset;

  private long sampleTimestampUs;

  private final int clockrate;

  public RtpTimestampAdjuster(int clockrate) {
    this.clockrate = clockrate;
    firstSampleTimestamp = C.TIME_UNSET;
  }

  public int getClockrate() { return clockrate; }

  public void adjustSampleTimestamp(long time) {
    if (firstSampleTimestamp == C.TIME_UNSET) {
      firstSampleTimestamp = time;
    } else {
      timestampOffset = time - firstSampleTimestamp;
    }

    sampleTimestampUs = (timestampOffset * C.MICROS_PER_SECOND) / clockrate;
  }

  public long getSampleTimeUs() {
    return sampleTimestampUs;
  }

}
