/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeRenderer;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link FilteringMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class FilteringMediaSourceTest {

  @Test
  public void playbackWithFilteredMediaSource_onlyPublishesAndPlaysAllowedTypes() throws Exception {
    Timeline timeline = new FakeTimeline();
    FakeMediaSource videoSource =
        new FakeMediaSource(
            timeline, new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build());
    FakeMediaSource audioSource =
        new FakeMediaSource(
            timeline, new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build());
    FakeMediaSource textSource =
        new FakeMediaSource(
            timeline,
            new Format.Builder()
                .setSampleMimeType(MimeTypes.TEXT_VTT)
                .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                .build());
    FakeRenderer videoRenderer = new FakeRenderer(C.TRACK_TYPE_VIDEO);
    FakeRenderer audioRenderer = new FakeRenderer(C.TRACK_TYPE_AUDIO);
    FakeRenderer textRenderer = new FakeRenderer(C.TRACK_TYPE_TEXT);
    ExoPlayer player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setRenderers(videoRenderer, audioRenderer, textRenderer)
            .build();
    FilteringMediaSource mediaSourceWithVideoAndTextOnly =
        new FilteringMediaSource(
            new MergingMediaSource(textSource, audioSource, videoSource),
            ImmutableSet.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT));
    player.setMediaSource(mediaSourceWithVideoAndTextOnly);

    player.prepare();
    runUntilPlaybackState(player, Player.STATE_READY);
    Tracks tracks = player.getCurrentTracks();
    player.play();
    runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    assertThat(tracks.getGroups()).hasSize(2);
    assertThat(tracks.containsType(C.TRACK_TYPE_AUDIO)).isFalse();
    assertThat(videoRenderer.enabledCount).isEqualTo(1);
    assertThat(textRenderer.enabledCount).isEqualTo(1);
    assertThat(audioRenderer.enabledCount).isEqualTo(0);
  }
}
