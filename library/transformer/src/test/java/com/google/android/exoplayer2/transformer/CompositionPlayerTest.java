/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.TestUtil.ASSET_URI_PREFIX;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.testutil.CapturingAudioSink;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.Clock;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link CompositionPlayer}. */
@RunWith(AndroidJUnit4.class)
public class CompositionPlayerTest {

  @Test
  public void playback_audioOnly_outputsSamples() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    CapturingAudioSink capturingAudioSink =
        new CapturingAudioSink(new DefaultAudioSink.Builder(applicationContext).build());
    CompositionPlayer player =
        new CompositionPlayer(applicationContext, /* looper= */ null, capturingAudioSink, clock);
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItem editedMediaItem2 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem1, editedMediaItem2));
    Composition composition = new Composition.Builder(ImmutableList.of(sequence)).build();

    player.setComposition(composition);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        capturingAudioSink,
        "audiosinkdumps/wav/sample.wav_then_sample_rf64.wav.dump");
  }
}
