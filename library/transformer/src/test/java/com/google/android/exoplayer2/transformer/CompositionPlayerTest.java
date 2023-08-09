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

import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYER_ERROR;
import static com.google.android.exoplayer2.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_RENDERED_FIRST_FRAME;
import static com.google.android.exoplayer2.Player.EVENT_VIDEO_SIZE_CHANGED;
import static com.google.android.exoplayer2.transformer.TestUtil.ASSET_URI_PREFIX;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static com.google.android.exoplayer2.transformer.TestUtil.FILE_AUDIO_VIDEO_STEREO;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link CompositionPlayer}. */
@RunWith(AndroidJUnit4.class)
public class CompositionPlayerTest {

  @Test
  public void playback_outputsSamples() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    CapturingAudioSink capturingAudioSink =
        new CapturingAudioSink(new DefaultAudioSink.Builder(applicationContext).build());
    CompositionPlayer player =
        new CompositionPlayer(applicationContext, /* looper= */ null, capturingAudioSink, clock);
    // Use an audio-only Composition because there is no way to dump the video frames.
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

  @Test
  public void addListener_callsSupportedCallbacks() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();
    Player.Listener mockListener = mock(Player.Listener.class);

    player.setComposition(composition);
    player.addListener(mockListener);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(mockListener, atLeastOnce()).onPlaybackStateChanged(anyInt());
    verify(mockListener, atLeastOnce()).onPlayWhenReadyChanged(anyBoolean(), anyInt());
    verify(mockListener, atLeastOnce()).onEvents(any(), any());
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void addListener_callsOnEventsWithSupportedEvents() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();
    Player.Listener mockListener = mock(Player.Listener.class);
    ArgumentCaptor<Player.Events> eventsCaptor = ArgumentCaptor.forClass(Player.Events.class);
    ImmutableSet<Integer> supportedEvents =
        ImmutableSet.of(
            EVENT_PLAYBACK_STATE_CHANGED,
            EVENT_PLAY_WHEN_READY_CHANGED,
            EVENT_PLAYER_ERROR,
            EVENT_VIDEO_SIZE_CHANGED,
            EVENT_RENDERED_FIRST_FRAME);

    player.setComposition(composition);
    player.addListener(mockListener);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(mockListener, atLeastOnce()).onEvents(any(), eventsCaptor.capture());
    List<Player.Events> eventsList = eventsCaptor.getAllValues();
    for (Player.Events events : eventsList) {
      assertThat(events.size()).isNotEqualTo(0);
      for (int j = 0; j < events.size(); j++) {
        assertThat(supportedEvents).contains(events.get(j));
      }
    }
  }

  private static CompositionPlayer buildCompositionPlayer() {
    return new CompositionPlayer(
        ApplicationProvider.getApplicationContext(),
        /* looper= */ null,
        /* audioSink= */ null,
        new FakeClock(/* isAutoAdvancing= */ true));
  }

  private static Composition buildComposition() {
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO)).build();
    EditedMediaItem editedMediaItem2 =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_STEREO))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence(ImmutableList.of(editedMediaItem1, editedMediaItem2));
    return new Composition.Builder(ImmutableList.of(sequence)).build();
  }
}
