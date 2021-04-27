/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspTrackTiming}. */
@RunWith(AndroidJUnit4.class)
public class RtspTrackTimingTest {
  @Test
  public void parseTiming_withSeqNumberAndRtpTime() throws Exception {
    String rtpInfoString =
        "url=rtsp://video.example.com/twister/video;seq=12312232;rtptime=78712811";

    ImmutableList<RtspTrackTiming> trackTimingList =
        RtspTrackTiming.parseTrackTiming(rtpInfoString);

    assertThat(trackTimingList).hasSize(1);
    RtspTrackTiming trackTiming = trackTimingList.get(0);
    assertThat(trackTiming.uri).isEqualTo(Uri.parse("rtsp://video.example.com/twister/video"));
    assertThat(trackTiming.sequenceNumber).isEqualTo(12312232);
    assertThat(trackTiming.rtpTimestamp).isEqualTo(78712811);
  }

  @Test
  public void parseTiming_withSeqNumberOnly() throws Exception {
    String rtpInfoString =
        "url=rtsp://foo.com/bar.avi/streamid=0;seq=45102,url=rtsp://foo.com/bar.avi/streamid=1;seq=30211";

    ImmutableList<RtspTrackTiming> trackTimingList =
        RtspTrackTiming.parseTrackTiming(rtpInfoString);

    assertThat(trackTimingList).hasSize(2);
    RtspTrackTiming trackTiming = trackTimingList.get(0);
    assertThat(trackTiming.uri).isEqualTo(Uri.parse("rtsp://foo.com/bar.avi/streamid=0"));
    assertThat(trackTiming.sequenceNumber).isEqualTo(45102);
    assertThat(trackTiming.rtpTimestamp).isEqualTo(C.TIME_UNSET);
    trackTiming = trackTimingList.get(1);
    assertThat(trackTiming.uri).isEqualTo(Uri.parse("rtsp://foo.com/bar.avi/streamid=1"));
    assertThat(trackTiming.sequenceNumber).isEqualTo(30211);
    assertThat(trackTiming.rtpTimestamp).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void parseTiming_withInvalidParameter_throws() {
    String rtpInfoString = "url=rtsp://video.example.com/twister/video;seq=123abc";

    assertThrows(ParserException.class, () -> RtspTrackTiming.parseTrackTiming(rtpInfoString));
  }

  @Test
  public void parseTiming_withInvalidUrl_throws() {
    String rtpInfoString = "url=video.example.com/twister/video;seq=36192348";

    assertThrows(ParserException.class, () -> RtspTrackTiming.parseTrackTiming(rtpInfoString));
  }

  @Test
  public void parseTiming_withNoParameter_throws() {
    String rtpInfoString = "url=rtsp://video.example.com/twister/video";

    assertThrows(ParserException.class, () -> RtspTrackTiming.parseTrackTiming(rtpInfoString));
  }

  @Test
  public void parseTiming_withNoUrl_throws() {
    String rtpInfoString = "seq=35421887";

    assertThrows(ParserException.class, () -> RtspTrackTiming.parseTrackTiming(rtpInfoString));
  }
}
