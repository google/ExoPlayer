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

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link RtspMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public class RtspMediaPeriodTest {

  private static final RtspClient PLACEHOLDER_RTSP_CLIENT =
      new RtspClient(
          new RtspClient.SessionInfoListener() {
            @Override
            public void onSessionTimelineUpdated(
                RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}
            @Override
            public void onSessionTimelineRequestFailed(String message, @Nullable Throwable cause) {}
          },
          /* userAgent= */ null,
          Uri.EMPTY);

  @Test
  public void prepare_startsLoading() throws Exception {
    RtspMediaPeriod rtspMediaPeriod =
        new RtspMediaPeriod(
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            ImmutableList.of(
                new RtspMediaTrack(
                    new MediaDescription.Builder(
                            /* mediaType= */ MediaDescription.MEDIA_TYPE_VIDEO,
                            /* port= */ 0,
                            /* transportProtocol= */ MediaDescription.RTP_AVP_PROFILE,
                            /* payloadType= */ 96)
                        .setConnection("IN IP4 0.0.0.0")
                        .setBitrate(500_000)
                        .addAttribute(SessionDescription.ATTR_RTPMAP, "96 H264/90000")
                        .addAttribute(
                            SessionDescription.ATTR_FMTP,
                            "96 packetization-mode=1;profile-level-id=64001F;sprop-parameter-sets=Z2QAH6zZQPARabIAAAMACAAAAwGcHjBjLA==,aOvjyyLA")
                        .addAttribute(SessionDescription.ATTR_CONTROL, "track1")
                        .build(),
                    Uri.parse("rtsp://localhost/test"))),
            PLACEHOLDER_RTSP_CLIENT,
            new UdpDataSourceRtpDataChannelFactory());

    AtomicBoolean prepareCallbackCalled = new AtomicBoolean(false);
    rtspMediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            prepareCallbackCalled.set(true);
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            source.continueLoading(/* positionUs= */ 0);
          }
        },
        /* positionUs= */ 0);

    runMainLooperUntil(prepareCallbackCalled::get);
    rtspMediaPeriod.release();
  }

  @Test
  public void getBufferedPositionUs_withNoRtspMediaTracks_returnsEndOfSource() {
    RtspMediaPeriod rtspMediaPeriod =
        new RtspMediaPeriod(
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            ImmutableList.of(),
            PLACEHOLDER_RTSP_CLIENT,
            new UdpDataSourceRtpDataChannelFactory());

    assertThat(rtspMediaPeriod.getBufferedPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
  }
}
