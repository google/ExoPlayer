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
package androidx.media3.decoder.opus;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.CapturingAudioSink;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link LibopusAudioRenderer}. */
@RunWith(AndroidJUnit4.class)
public class OpusPlaybackTest {

  private static final String BEAR_OPUS = "mka/bear-opus.mka";
  private static final String BEAR_OPUS_NEGATIVE_GAIN = "mka/bear-opus-negative-gain.mka";
  private static final String OPUS_5POINT1 = "mka/opus-5.1.mka";

  @Before
  public void setUp() {
    assertWithMessage("Opus library not available").that(OpusLibrary.isAvailable()).isTrue();
    assertWithMessage("Dump files were generated for x86_64")
        .that(System.getProperty("os.arch"))
        .isEqualTo("x86_64");
  }

  @Test
  public void playBasicOpus() throws Exception {
    playUri(BEAR_OPUS);
  }

  @Test
  public void playWithNegativeGain() throws Exception {
    playUri(BEAR_OPUS_NEGATIVE_GAIN);
  }

  @Test
  public void play5Point1() throws Exception {
    playUri(OPUS_5POINT1);
  }

  private void playUri(String fileName) throws Exception {
    CapturingAudioSink audioSink =
        new CapturingAudioSink(
            new DefaultAudioSink.Builder(ApplicationProvider.getApplicationContext()).build());

    TestPlaybackRunnable testPlaybackRunnable =
        new TestPlaybackRunnable(
            Uri.parse("asset:///media/" + fileName),
            ApplicationProvider.getApplicationContext(),
            audioSink);
    Thread thread = new Thread(testPlaybackRunnable);
    thread.start();
    thread.join();

    if (testPlaybackRunnable.playbackException != null) {
      throw testPlaybackRunnable.playbackException;
    }
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        audioSink,
        "audiosinkdumps/" + fileName + ".audiosink.dump");
  }

  private static class TestPlaybackRunnable implements Player.Listener, Runnable {

    private final Context context;
    private final Uri uri;
    private final AudioSink audioSink;

    @Nullable private ExoPlayer player;
    @Nullable private PlaybackException playbackException;

    public TestPlaybackRunnable(Uri uri, Context context, AudioSink audioSink) {
      this.uri = uri;
      this.context = context;
      this.audioSink = audioSink;
    }

    @Override
    public void run() {
      Looper.prepare();
      RenderersFactory renderersFactory =
          (eventHandler,
              videoRendererEventListener,
              audioRendererEventListener,
              textRendererOutput,
              metadataRendererOutput) ->
              new Renderer[] {
                new LibopusAudioRenderer(eventHandler, audioRendererEventListener, audioSink)
              };
      player = new ExoPlayer.Builder(context, renderersFactory).build();
      player.addListener(this);
      MediaSource mediaSource =
          new ProgressiveMediaSource.Factory(
                  new DefaultDataSource.Factory(context),
                  MatroskaExtractor.newFactory(new DefaultSubtitleParserFactory()))
              .createMediaSource(MediaItem.fromUri(uri));
      player.setMediaSource(mediaSource);
      player.prepare();
      player.play();
      Looper.loop();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED
          || (playbackState == Player.STATE_IDLE && playbackException != null)) {
        player.release();
        Looper.myLooper().quit();
      }
    }
  }
}
