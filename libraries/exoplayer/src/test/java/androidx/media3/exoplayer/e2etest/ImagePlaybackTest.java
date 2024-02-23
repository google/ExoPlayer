/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.e2etest;

import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

/** End-to-end tests using image samples. */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(value = NATIVE)
public class ImagePlaybackTest {

  @Test
  public void playImagePlaylist_withSeek_rendersExpectedImages() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    MediaItem mediaItem1 =
        new MediaItem.Builder()
            .setUri("asset:///media/png/media3test.png")
            .setImageDurationMs(3000L)
            .build();
    MediaItem mediaItem2 =
        new MediaItem.Builder()
            .setUri("asset:///media/png/non-motion-photo-shortened.png")
            .setImageDurationMs(3000L)
            .build();
    player.setMediaItems(ImmutableList.of(mediaItem1, mediaItem2));
    player.prepare();

    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 1000L);
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 2000L);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/image/image_playlist_with_seek.dump");
  }
}
