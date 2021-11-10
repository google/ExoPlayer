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
package androidx.media3.test.utils;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline.Window;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link FakeMediaSourceFactory}. */
@RunWith(AndroidJUnit4.class)
public class FakeMediaSourceFactoryTest {

  @Test
  public void createMediaSource_mediaItemIsSameInstance() {
    FakeMediaSourceFactory fakeMediaSourceFactory = new FakeMediaSourceFactory();
    MediaItem mediaItem = MediaItem.fromUri("http://google.com/0");
    @Nullable AtomicReference<MediaItem> reportedMediaItem = new AtomicReference<>();

    MediaSource mediaSource = fakeMediaSourceFactory.createMediaSource(mediaItem);
    mediaSource.prepareSource(
        (source, timeline) -> {
          int firstWindowIndex = timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false);
          reportedMediaItem.set(timeline.getWindow(firstWindowIndex, new Window()).mediaItem);
        },
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);

    assertThat(reportedMediaItem.get()).isSameInstanceAs(mediaItem);
    assertThat(mediaSource.getMediaItem()).isSameInstanceAs(mediaItem);
  }
}
