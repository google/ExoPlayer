/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.testutil.AutoAdvancingFakeClock;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeVideoRenderer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link SimpleExoPlayer}. */
@RunWith(AndroidJUnit4.class)
public class SimpleExoPlayerTest {

  // TODO(b/143232359): Revert to @Config(sdk = Config.ALL_SDKS) once b/143232359 is resolved
  @Test
  @Config(minSdk = Config.OLDEST_SDK, maxSdk = Config.TARGET_SDK)
  public void builder_inBackgroundThread_doesNotThrow() throws Exception {
    Thread builderThread =
        new Thread(
            () -> new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build());
    AtomicReference<Throwable> builderThrow = new AtomicReference<>();
    builderThread.setUncaughtExceptionHandler((thread, throwable) -> builderThrow.set(throwable));

    builderThread.start();
    builderThread.join();

    assertThat(builderThrow.get()).isNull();
  }

  @Test
  public void release_triggersAllPendingEventsInAnalyticsListeners() throws Exception {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(
                ApplicationProvider.getApplicationContext(),
                (handler, videoListener, audioListener, textOutput, metadataOutput) ->
                    new Renderer[] {new FakeVideoRenderer(handler, videoListener)})
            .setClock(new AutoAdvancingFakeClock())
            .build();
    AnalyticsListener listener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(listener);
    // Do something that requires clean-up callbacks like decoder disabling.
    player.setMediaSource(
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT));
    player.prepare();
    player.play();
    runUntilPlaybackState(player, Player.STATE_READY);

    player.release();
    ShadowLooper.runMainLooperToNextTask();

    verify(listener).onVideoDisabled(any(), any());
    verify(listener).onPlayerReleased(any());
  }
}
