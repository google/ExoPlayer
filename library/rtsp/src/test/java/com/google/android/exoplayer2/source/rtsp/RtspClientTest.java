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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.rtsp.RtspClient.SessionInfoListener;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link RtspClient} using the {@link RtspServer}. */
@RunWith(AndroidJUnit4.class)
public final class RtspClientTest {

  private @MonotonicNonNull RtspClient rtspClient;
  private @MonotonicNonNull RtspServer rtspServer;

  @Before
  public void setUp() {
    rtspServer = new RtspServer();
  }

  @After
  public void tearDown() {
    Util.closeQuietly(rtspServer);
    Util.closeQuietly(rtspClient);
  }

  @Test
  public void connectServerAndClient_withServerSupportsOnlyOptions_sessionTimelineRequestFails()
      throws Exception {
    int serverRtspPortNumber = checkNotNull(rtspServer).startAndGetPortNumber();

    AtomicBoolean sessionTimelineUpdateEventReceived = new AtomicBoolean();
    rtspClient =
        new RtspClient(
            new SessionInfoListener() {
              @Override
              public void onSessionTimelineUpdated(
                  RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {}

              @Override
              public void onSessionTimelineRequestFailed(
                  String message, @Nullable Throwable cause) {
                sessionTimelineUpdateEventReceived.set(true);
              }
            },
            /* userAgent= */ "ExoPlayer:RtspClientTest",
            /* uri= */ Uri.parse(
                Util.formatInvariant("rtsp://localhost:%d/test", serverRtspPortNumber)));
    rtspClient.start();

    RobolectricUtil.runMainLooperUntil(sessionTimelineUpdateEventReceived::get);
  }
}
