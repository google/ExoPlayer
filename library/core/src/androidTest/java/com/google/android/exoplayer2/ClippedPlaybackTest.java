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
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
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
 * Instrumentation tests for playback of clipped items using {@link MediaItem#clippingConfiguration}
 * or {@link ClippingMediaSource} directly.
 */
@RunWith(AndroidJUnit4.class)
public final class ClippedPlaybackTest {

  @Test
  public void subtitlesRespectClipping_singlePeriod() throws Exception {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/sample.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new SubtitleConfiguration.Builder(Uri.parse("asset:///media/webvtt/typical"))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            // Expect the clipping to affect both subtitles and video.
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(1000).build())
            .build();
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    TextCapturingPlaybackListener textCapturer = new TextCapturingPlaybackListener();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(new ExoPlayer.Builder(getInstrumentation().getContext()).build());
              player.get().addListener(textCapturer);
              player.get().setMediaItem(mediaItem);
              player.get().prepare();
              player.get().play();
            });

    textCapturer.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textCapturer.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  @Test
  public void subtitlesRespectClipping_multiplePeriods() throws Exception {
    ImmutableList<MediaItem> mediaItems =
        ImmutableList.of(
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                .setSubtitleConfigurations(
                    ImmutableList.of(
                        new SubtitleConfiguration.Builder(
                                Uri.parse("asset:///media/webvtt/typical"))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage("en")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()))
                // Expect the clipping to affect both subtitles and video.
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(1000).build())
                .build(),
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                // Not needed for correctness, just makes test run faster. Must be longer than the
                // subtitle content (3.5s).
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(4_000).build())
                .build());
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    TextCapturingPlaybackListener textCapturer = new TextCapturingPlaybackListener();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(new ExoPlayer.Builder(getInstrumentation().getContext()).build());
              player.get().addListener(textCapturer);
              player.get().setMediaItems(mediaItems);
              player.get().prepare();
              player.get().play();
            });

    textCapturer.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textCapturer.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  private static class TextCapturingPlaybackListener implements Player.Listener {

    private final ConditionVariable playbackEnded;
    private final List<List<Cue>> cues;

    private TextCapturingPlaybackListener() {
      playbackEnded = new ConditionVariable();
      cues = new ArrayList<>();
    }

    @Override
    public void onCues(CueGroup cueGroup) {
      this.cues.add(cueGroup.cues);
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED) {
        playbackEnded.open();
      }
    }

    public void block() throws InterruptedException {
      playbackEnded.block();
    }
  }
}
