/*
 * Copyright 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.ext.media2;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.rules.ExternalResource;

/** Rule for tests that use {@link SessionPlayerConnector}. */
public class PlayerTestRule extends ExternalResource {
  private Context context;
  private ExecutorService executor;

  private SessionPlayerConnector sessionPlayerConnector;
  private SimpleExoPlayer exoPlayer;

  @Override
  protected void before() {
    context = ApplicationProvider.getApplicationContext();
    executor = Executors.newFixedThreadPool(1);

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              // Initialize AudioManager on the main thread to workaround b/78617702 that
              // audio focus listener is called on the thread where the AudioManager was
              // originally initialized.
              // Without posting this, audio focus listeners wouldn't be called because the
              // listeners would be posted to the test thread (here) where it waits until the
              // tests are finished.
              AudioManager audioManager =
                  (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

              exoPlayer = new SimpleExoPlayer.Builder(context).setLooper(Looper.myLooper()).build();
              ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource();
              TimelinePlaylistManager manager =
                  new TimelinePlaylistManager(context, concatenatingMediaSource);
              ConcatenatingMediaSourcePlaybackPreparer playbackPreparer =
                  new ConcatenatingMediaSourcePlaybackPreparer(exoPlayer, concatenatingMediaSource);
              sessionPlayerConnector =
                  new SessionPlayerConnector(exoPlayer, manager, playbackPreparer);
            });
  }

  @Override
  protected void after() {
    if (sessionPlayerConnector != null) {
      sessionPlayerConnector.close();
      sessionPlayerConnector = null;
    }
    if (exoPlayer != null) {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                exoPlayer.release();
                exoPlayer = null;
              });
    }
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }

  public ExecutorService getExecutor() {
    return executor;
  }

  public SessionPlayerConnector getSessionPlayerConnector() {
    return sessionPlayerConnector;
  }

  public SimpleExoPlayer getSimpleExoPlayer() {
    return exoPlayer;
  }
}
