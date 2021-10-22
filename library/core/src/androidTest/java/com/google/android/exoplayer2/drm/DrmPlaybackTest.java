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
package com.google.android.exoplayer2.drm;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation tests for DRM playback. */
@RunWith(AndroidJUnit4.class)
public final class DrmPlaybackTest {

  /** The license response needed to play the {@code drm/sample_fragmented_clearkey.mp4} file. */
  // NOTE: Changing this response *should* make the test fail, but it seems the clearkey CDM
  // implementation is quite robust. This means an 'invalid' response means it just incorrectly
  // decrypts the content, resulting in invalid data fed to the decoder and no video shown on the
  // screen (but no error thrown that's detectable by this test).
  private static final String CLEARKEY_RESPONSE =
      "{\"keys\":"
          + "[{"
          + "\"kty\":\"oct\","
          + "\"k\":\"Y8tfcYTdS2iaXF_xHuajKA\","
          + "\"kid\":\"zX65_4jzTK6wYYWwACTkwg\""
          + "}],"
          + "\"type\":\"temporary\"}";

  @Test
  public void clearkeyPlayback() throws Exception {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(CLEARKEY_RESPONSE));
    mockWebServer.start();

    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/drm/sample_fragmented_clearkey.mp4")
            .setDrmConfiguration(
                new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setLicenseUri(mockWebServer.url("license").toString())
                    .build())
            .build();
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    ConditionVariable playbackComplete = new ConditionVariable();
    AtomicReference<PlaybackException> playbackException = new AtomicReference<>();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(new ExoPlayer.Builder(getInstrumentation().getContext()).build());
              player
                  .get()
                  .addListener(
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
              player.get().setMediaItem(mediaItem);
              player.get().prepare();
              player.get().play();
            });

    playbackComplete.block();
    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(playbackException.get()).isNull();
  }
}
