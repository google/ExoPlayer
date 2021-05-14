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
package com.google.android.exoplayer2.ext.vp9;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.VideoDecoderGLSurfaceView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link LibvpxVideoRenderer}. */
@RunWith(AndroidJUnit4.class)
public class VpxPlaybackTest {

  private static final String BEAR_URI = "asset:///media/vp9/bear-vp9.webm";
  private static final String BEAR_ODD_DIMENSIONS_URI =
      "asset:///media/vp9/bear-vp9-odd-dimensions.webm";
  private static final String ROADTRIP_10BIT_URI = "asset:///media/vp9/roadtrip-vp92-10bit.webm";
  private static final String INVALID_BITSTREAM_URI = "asset:///media/vp9/invalid-bitstream.webm";

  private static final String TAG = "VpxPlaybackTest";

  @Before
  public void setUp() {
    if (!VpxLibrary.isAvailable()) {
      fail("Vpx library not available.");
    }
  }

  @Test
  public void basicPlayback() throws Exception {
    playUri(BEAR_URI);
  }

  @Test
  public void oddDimensionsPlayback() throws Exception {
    playUri(BEAR_ODD_DIMENSIONS_URI);
  }

  @Test
  public void test10BitProfile2Playback() throws Exception {
    if (VpxLibrary.isHighBitDepthSupported()) {
      Log.d(TAG, "High Bit Depth supported.");
      playUri(ROADTRIP_10BIT_URI);
      return;
    }
    Log.d(TAG, "High Bit Depth not supported.");
  }

  @Test
  public void invalidBitstream() {
    try {
      playUri(INVALID_BITSTREAM_URI);
      fail();
    } catch (Exception e) {
      assertThat(e.getCause()).isNotNull();
      assertThat(e.getCause()).isInstanceOf(VpxDecoderException.class);
    }
  }

  private void playUri(String uri) throws Exception {
    TestPlaybackRunnable testPlaybackRunnable =
        new TestPlaybackRunnable(Uri.parse(uri), ApplicationProvider.getApplicationContext());
    Thread thread = new Thread(testPlaybackRunnable);
    thread.start();
    thread.join();
    if (testPlaybackRunnable.playbackException != null) {
      throw testPlaybackRunnable.playbackException;
    }
  }

  private static class TestPlaybackRunnable implements Player.Listener, Runnable {

    private final Context context;
    private final Uri uri;

    @Nullable private SimpleExoPlayer player;
    @Nullable private ExoPlaybackException playbackException;

    public TestPlaybackRunnable(Uri uri, Context context) {
      this.uri = uri;
      this.context = context;
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
                new LibvpxVideoRenderer(
                    /* allowedJoiningTimeMs= */ 0,
                    eventHandler,
                    videoRendererEventListener,
                    /* maxDroppedFramesToNotify= */ -1)
              };
      player = new SimpleExoPlayer.Builder(context, renderersFactory).build();
      player.addListener(this);
      MediaSource mediaSource =
          new ProgressiveMediaSource.Factory(
                  new DefaultDataSourceFactory(context), MatroskaExtractor.FACTORY)
              .createMediaSource(MediaItem.fromUri(uri));
      player.setVideoSurfaceView(new VideoDecoderGLSurfaceView(context));
      player.setMediaSource(mediaSource);
      player.prepare();
      player.play();
      Looper.loop();
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
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
