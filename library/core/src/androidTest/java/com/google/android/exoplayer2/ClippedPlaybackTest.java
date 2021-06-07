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
package com.google.android.exoplayer2;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumentation tests for playback of clipped items using {@link MediaItem#clippingProperties} or
 * {@link ClippingMediaSource} directly.
 */
@RunWith(AndroidJUnit4.class)
public final class ClippedPlaybackTest {

  @Test
  public void subtitlesRespectClipping_singlePeriod() throws Exception {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/sample.mp4")
            .setSubtitles(
                ImmutableList.of(
                    new MediaItem.Subtitle(
                        Uri.parse("asset:///media/webvtt/typical"),
                        MimeTypes.TEXT_VTT,
                        "en",
                        C.SELECTION_FLAG_DEFAULT)))
            // Expect the clipping to affect both subtitles and video.
            .setClipEndPositionMs(1000)
            .build();
    AtomicReference<SimpleExoPlayer> player = new AtomicReference<>();
    CapturingTextOutput textOutput = new CapturingTextOutput();
    ConditionVariable playbackEnded = new ConditionVariable();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(new SimpleExoPlayer.Builder(getInstrumentation().getContext()).build());
              player.get().addTextOutput(textOutput);
              player
                  .get()
                  .addListener(
                      new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(@Player.State int state) {
                          if (state == Player.STATE_ENDED) {
                            playbackEnded.open();
                          }
                        }
                      });
              player.get().setMediaItem(mediaItem);
              player.get().prepare();
              player.get().play();
            });

    playbackEnded.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textOutput.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  @Test
  public void subtitlesRespectClipping_multiplePeriods() throws Exception {
    ImmutableList<MediaItem> mediaItems =
        ImmutableList.of(
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                .setSubtitles(
                    ImmutableList.of(
                        new MediaItem.Subtitle(
                            Uri.parse("asset:///media/webvtt/typical"),
                            MimeTypes.TEXT_VTT,
                            "en",
                            C.SELECTION_FLAG_DEFAULT)))
                // Expect the clipping to affect both subtitles and video.
                .setClipEndPositionMs(1000)
                .build(),
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                // Not needed for correctness, just makes test run faster. Must be longer than the
                // subtitle content (3.5s).
                .setClipEndPositionMs(4_000)
                .build());
    AtomicReference<SimpleExoPlayer> player = new AtomicReference<>();
    CapturingTextOutput textOutput = new CapturingTextOutput();
    ConditionVariable playbackEnded = new ConditionVariable();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(new SimpleExoPlayer.Builder(getInstrumentation().getContext()).build());
              player.get().addTextOutput(textOutput);
              player
                  .get()
                  .addListener(
                      new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(@Player.State int state) {
                          if (state == Player.STATE_ENDED) {
                            playbackEnded.open();
                          }
                        }
                      });
              player.get().setMediaItems(mediaItems);
              player.get().prepare();
              player.get().play();
            });

    playbackEnded.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textOutput.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  private static class CapturingTextOutput implements TextOutput {

    private final List<List<Cue>> cues;

    private CapturingTextOutput() {
      cues = new ArrayList<>();
    }

    @Override
    public void onCues(List<Cue> cues) {
      this.cues.add(cues);
    }
  }
}
