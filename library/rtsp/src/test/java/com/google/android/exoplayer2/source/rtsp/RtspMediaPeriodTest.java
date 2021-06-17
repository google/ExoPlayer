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

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Tests the {@link RtspMediaPeriod} using the {@link RtspServer}. */
@RunWith(AndroidJUnit4.class)
@DoNotInstrument
public final class RtspMediaPeriodTest {

  private RtspMediaPeriod mediaPeriod;
  private RtspServer rtspServer;

  @After
  public void tearDown() {
    Util.closeQuietly(rtspServer);
  }

  @Test
  public void prepareMediaPeriod_refreshesSourceInfoAndCallsOnPrepared() throws Exception {
    RtpPacketStreamDump rtpPacketStreamDump =
        RtspTestUtils.readRtpPacketStreamDump("media/rtsp/aac-dump.json");

    rtspServer =
        new RtspServer(
            new RtspServer.ResponseProvider() {
              @Override
              public RtspResponse getOptionsResponse() {
                return new RtspResponse(
                    /* status= */ 200,
                    new RtspHeaders.Builder().add(RtspHeaders.PUBLIC, "OPTIONS, DESCRIBE").build());
              }

              @Override
              public RtspResponse getDescribeResponse(Uri requestedUri) {
                return RtspTestUtils.newDescribeResponseWithSdpMessage(
                    "v=0\r\n"
                        + "o=- 1606776316530225 1 IN IP4 127.0.0.1\r\n"
                        + "s=Exoplayer test\r\n"
                        + "t=0 0\r\n"
                        // The session is 50.46s long.
                        + "a=range:npt=0-50.46\r\n",
                    ImmutableList.of(rtpPacketStreamDump),
                    requestedUri);
              }
            });

    AtomicBoolean prepareCallbackCalled = new AtomicBoolean();
    AtomicLong refreshedSourceDurationMs = new AtomicLong();

    mediaPeriod =
        new RtspMediaPeriod(
            new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
            new TransferRtpDataChannelFactory(),
            RtspTestUtils.getTestUri(rtspServer.startAndGetPortNumber()),
            /* listener= */ timing -> refreshedSourceDurationMs.set(timing.getDurationMs()),
            /* userAgent= */ "ExoPlayer:RtspPeriodTest");

    mediaPeriod.prepare(
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
    RobolectricUtil.runMainLooperUntil(prepareCallbackCalled::get);
    mediaPeriod.release();

    assertThat(refreshedSourceDurationMs.get()).isEqualTo(50_460);
  }
}
