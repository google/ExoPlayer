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
package com.google.android.exoplayer2.source;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation tests for {@link DefaultMediaSourceFactory}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultMediaSourceFactoryInstrumentationTest {

  // https://github.com/google/ExoPlayer/issues/9099
  @Test
  public void reuseMediaSourceFactoryBetweenPlayerInstances() throws Exception {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/sample.mp4")
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmSessionForClearPeriods(true)
            .build();
    AtomicReference<SimpleExoPlayer> player = new AtomicReference<>();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory(getInstrumentation().getContext());
    getInstrumentation()
        .runOnMainSync(
            () ->
                player.set(
                    new ExoPlayer.Builder(getInstrumentation().getContext())
                        .setMediaSourceFactory(defaultMediaSourceFactory)
                        .build()));
    playUntilEndAndRelease(player.get(), mediaItem);
    getInstrumentation()
        .runOnMainSync(
            () ->
                player.set(
                    new ExoPlayer.Builder(getInstrumentation().getContext())
                        .setMediaSourceFactory(defaultMediaSourceFactory)
                        .build()));
    playUntilEndAndRelease(player.get(), mediaItem);
  }

  private void playUntilEndAndRelease(Player player, MediaItem mediaItem)
      throws InterruptedException {
    ConditionVariable playbackComplete = new ConditionVariable();
    AtomicReference<PlaybackException> playbackException = new AtomicReference<>();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(@Player.State int playbackState) {
                      if (playbackState == Player.STATE_ENDED) {
                        playbackComplete.open();
                      }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                      playbackException.set(error);
                      playbackComplete.open();
                    }
                  });
              player.setMediaItem(mediaItem);
              player.prepare();
              player.play();
            });

    playbackComplete.block();
    getInstrumentation().runOnMainSync(player::release);
    getInstrumentation().waitForIdleSync();
    assertThat(playbackException.get()).isNull();
  }
}
