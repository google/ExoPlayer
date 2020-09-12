/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.upstream.DataSource;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link HlsMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class HlsMediaSourceTest {

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_nullMediaItemTag_setsMediaItemTag() {
    Object tag = new Object();
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(tag);

    MediaItem dashMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(dashMediaItem.playbackProperties).isNotNull();
    assertThat(dashMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(dashMediaItem.playbackProperties.tag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_nonNullMediaItemTag_doesNotOverrideMediaItemTag() {
    Object factoryTag = new Object();
    Object mediaItemTag = new Object();
    MediaItem mediaItem =
        new MediaItem.Builder().setUri("http://www.google.com").setTag(mediaItemTag).build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(factoryTag);

    MediaItem dashMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(dashMediaItem.playbackProperties).isNotNull();
    assertThat(dashMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(dashMediaItem.playbackProperties.tag).isEqualTo(mediaItemTag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_setsDeprecatedMediaSourceTag() {
    Object tag = new Object();
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(tag);

    @Nullable Object mediaSourceTag = factory.createMediaSource(mediaItem).getTag();

    assertThat(mediaSourceTag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factoryCreateMediaSource_setsDeprecatedMediaSourceTag() {
    Object tag = new Object();
    MediaItem mediaItem =
        new MediaItem.Builder().setUri("http://www.google.com").setTag(tag).build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(new Object());

    @Nullable Object mediaSourceTag = factory.createMediaSource(mediaItem).getTag();

    assertThat(mediaSourceTag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetStreamKeys_emptyMediaItemStreamKeys_setsMediaItemStreamKeys() {
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    StreamKey streamKey = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 1);
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class))
            .setStreamKeys(Collections.singletonList(streamKey));

    MediaItem dashMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(dashMediaItem.playbackProperties).isNotNull();
    assertThat(dashMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(dashMediaItem.playbackProperties.streamKeys).containsExactly(streamKey);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetStreamKeys_withMediaItemStreamKeys_doesNotOverrideMediaItemStreamKeys() {
    StreamKey mediaItemStreamKey = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 1);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("http://www.google.com")
            .setStreamKeys(Collections.singletonList(mediaItemStreamKey))
            .build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class))
            .setStreamKeys(
                Collections.singletonList(new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 0)));

    MediaItem dashMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(dashMediaItem.playbackProperties).isNotNull();
    assertThat(dashMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(dashMediaItem.playbackProperties.streamKeys).containsExactly(mediaItemStreamKey);
  }
}
