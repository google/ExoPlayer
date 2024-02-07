/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link CompositeSequenceableLoader}. */
@RunWith(AndroidJUnit4.class)
public final class CompositeSequenceableLoaderTest {

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns minimum buffered
   * position among all sub-loaders, and is consistent with {@link
   * CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getBufferedPositionUsReturnsMinimumLoaderBufferedPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns minimum buffered
   * position that is not {@link C#TIME_END_OF_SOURCE} among all sub-loaders, and is consistent with
   * {@link CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getBufferedPositionUsReturnsMinimumNonEndOfSourceLoaderBufferedPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2, loader3),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO),
                ImmutableList.of(C.TRACK_TYPE_VIDEO),
                ImmutableList.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns {@link
   * C#TIME_END_OF_SOURCE} when all sub-loaders have buffered till end-of-source, and is consistent
   * with {@link CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getBufferedPositionUsReturnsEndOfSourceWhenAllLoaderBufferedTillEndOfSource() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ C.TIME_END_OF_SOURCE,
            /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(compositeSequenceableLoader.isLoading()).isFalse();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns the minimum
   * buffered position of loaders with audio or video tracks (if at least one loader has tracks of
   * these types).
   */
  @Test
  public void getBufferedPositionUs_prefersLoadersWithAudioAndVideoTracks() {
    FakeSequenceableLoader loaderWithTextOnly =
        new FakeSequenceableLoader(/* bufferedPositionUs= */ 999, /* nextLoadPositionUs= */ 2000);
    FakeSequenceableLoader loaderWithAudioVideoAndText =
        new FakeSequenceableLoader(/* bufferedPositionUs= */ 1000, /* nextLoadPositionUs= */ 2000);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loaderWithTextOnly, loaderWithAudioVideoAndText),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_TEXT),
                ImmutableList.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} doesn't return {@link
   * C#TIME_END_OF_SOURCE} if only the A/V tracks have finished loading and text track is still
   * loading. Instead it keeps returning the last 'real' A/V buffered position, to avoid the
   * buffered position snapping back to the text track buffered position.
   */
  @Test
  public void
      getBufferedPositionUs_prefersLoadersWithAudioAndVideoTracks_fallsBackToLastAVBufferedPositionWhenLoadingComplete() {
    FakeSequenceableLoader loaderWithTextOnly =
        new FakeSequenceableLoader(/* bufferedPositionUs= */ 500, /* nextLoadPositionUs= */ 2000);
    FakeSequenceableLoader loaderWithAudioVideoAndText =
        new FakeSequenceableLoader(
            /* bufferedPositionUs= */ 1000, /* nextLoadPositionUs= */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loaderWithTextOnly, loaderWithAudioVideoAndText),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_TEXT),
                ImmutableList.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
    loaderWithAudioVideoAndText.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(100).build());
    assertThat(loaderWithAudioVideoAndText.getBufferedPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(1000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getBufferedPositionUs()} returns the minimum
   * buffered position of all loaders if no loader has audio or video tracks.
   */
  @Test
  public void getBufferedPositionUs_considersAllTracksIfNoneAreAudioOrVideo() {
    FakeSequenceableLoader loaderWithTextOnly =
        new FakeSequenceableLoader(/* bufferedPositionUs= */ 999, /* nextLoadPositionUs= */ 2000);
    FakeSequenceableLoader loaderWithMetadataOnly =
        new FakeSequenceableLoader(/* bufferedPositionUs= */ 1000, /* nextLoadPositionUs= */ 2000);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loaderWithTextOnly, loaderWithMetadataOnly),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_TEXT), ImmutableList.of(C.TRACK_TYPE_METADATA)));
    assertThat(compositeSequenceableLoader.getBufferedPositionUs()).isEqualTo(999);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns minimum next
   * load position among all sub-loaders, and is consistent with {@link
   * CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getNextLoadPositionUsReturnMinimumLoaderNextLoadPositionUs() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2000);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(2000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns minimum next
   * load position that is not {@link C#TIME_END_OF_SOURCE} among all sub-loaders, and is consistent
   * with {@link CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getNextLoadPositionUsReturnMinimumNonEndOfSourceLoaderNextLoadPositionUs() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2, loader3),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO),
                ImmutableList.of(C.TRACK_TYPE_VIDEO),
                ImmutableList.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(2000);
    assertThat(compositeSequenceableLoader.isLoading()).isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#getNextLoadPositionUs()} returns {@link
   * C#TIME_END_OF_SOURCE} when all sub-loaders have next load position at end-of-source, and is
   * consistent with {@link CompositeSequenceableLoader#isLoading()}.
   */
  @Test
  public void getNextLoadPositionUsReturnsEndOfSourceWhenAllLoaderLoadingLastChunk() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    assertThat(compositeSequenceableLoader.getNextLoadPositionUs()).isEqualTo(C.TIME_END_OF_SOURCE);
    assertThat(compositeSequenceableLoader.isLoading()).isFalse();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(LoadingInfo)} only allows the
   * loader with minimum next load position to continue loading if next load positions are not
   * behind current playback position.
   */
  @Test
  public void continueLoadingOnlyAllowFurthestBehindLoaderToLoadIfNotBehindPlaybackPosition() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    compositeSequenceableLoader.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(100).build());

    assertThat(loader1.numInvocations).isEqualTo(1);
    assertThat(loader2.numInvocations).isEqualTo(0);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(LoadingInfo)} allows all loaders
   * with next load position behind current playback position to continue loading.
   */
  @Test
  public void continueLoadingReturnAllowAllLoadersBehindPlaybackPositionToLoad() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    FakeSequenceableLoader loader3 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1002, /* nextLoadPositionUs */ 2002);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2, loader3),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO),
                ImmutableList.of(C.TRACK_TYPE_VIDEO),
                ImmutableList.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)));
    compositeSequenceableLoader.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(3000).build());

    assertThat(loader1.numInvocations).isEqualTo(1);
    assertThat(loader2.numInvocations).isEqualTo(1);
    assertThat(loader3.numInvocations).isEqualTo(1);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(LoadingInfo)} does not allow
   * loader with next load position at end-of-source to continue loading.
   */
  @Test
  public void continueLoadingOnlyNotAllowEndOfSourceLoaderToLoad() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(
            /* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ C.TIME_END_OF_SOURCE);
    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));
    compositeSequenceableLoader.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(3000).build());

    assertThat(loader1.numInvocations).isEqualTo(0);
    assertThat(loader2.numInvocations).isEqualTo(0);
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(LoadingInfo)} returns true if the
   * loader with minimum next load position can make progress if next load positions are not behind
   * current playback position.
   */
  @Test
  public void continueLoadingReturnTrueIfFurthestBehindLoaderCanMakeProgress() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    loader1.setNextChunkDurationUs(1000);

    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));

    assertThat(
            compositeSequenceableLoader.continueLoading(
                new LoadingInfo.Builder().setPlaybackPositionUs(100).build()))
        .isTrue();
  }

  /**
   * Tests that {@link CompositeSequenceableLoader#continueLoading(LoadingInfo)} returns true if any
   * loader that are behind current playback position can make progress, even if it is not the one
   * with minimum next load position.
   */
  @Test
  public void continueLoadingReturnTrueIfLoaderBehindPlaybackPositionCanMakeProgress() {
    FakeSequenceableLoader loader1 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1000, /* nextLoadPositionUs */ 2000);
    FakeSequenceableLoader loader2 =
        new FakeSequenceableLoader(/* bufferedPositionUs */ 1001, /* nextLoadPositionUs */ 2001);
    // loader2 is not the furthest behind, but it can make progress if allowed.
    loader2.setNextChunkDurationUs(1000);

    CompositeSequenceableLoader compositeSequenceableLoader =
        new CompositeSequenceableLoader(
            ImmutableList.of(loader1, loader2),
            ImmutableList.of(
                ImmutableList.of(C.TRACK_TYPE_AUDIO), ImmutableList.of(C.TRACK_TYPE_VIDEO)));

    assertThat(
            compositeSequenceableLoader.continueLoading(
                new LoadingInfo.Builder().setPlaybackPositionUs(3000).build()))
        .isTrue();
  }

  private static class FakeSequenceableLoader implements SequenceableLoader {

    private long bufferedPositionUs;
    private long nextLoadPositionUs;
    private int numInvocations;
    private int nextChunkDurationUs;

    private FakeSequenceableLoader(long bufferedPositionUs, long nextLoadPositionUs) {
      if (bufferedPositionUs == C.TIME_END_OF_SOURCE) {
        checkArgument(nextLoadPositionUs == C.TIME_END_OF_SOURCE);
      }
      this.bufferedPositionUs = bufferedPositionUs;
      this.nextLoadPositionUs = nextLoadPositionUs;
    }

    @Override
    public long getBufferedPositionUs() {
      return bufferedPositionUs;
    }

    @Override
    public long getNextLoadPositionUs() {
      return nextLoadPositionUs;
    }

    @Override
    public boolean continueLoading(LoadingInfo loadingInfo) {
      numInvocations++;

      bufferedPositionUs = nextLoadPositionUs;
      if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
        return false;
      }

      long oldNextLoadPositionUs = nextLoadPositionUs;
      // The current chunk has been loaded, advance to next chunk.
      nextLoadPositionUs += nextChunkDurationUs;
      nextChunkDurationUs = 0;
      return oldNextLoadPositionUs != nextLoadPositionUs;
    }

    @Override
    public boolean isLoading() {
      return nextLoadPositionUs != C.TIME_END_OF_SOURCE;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
      // Do nothing.
    }

    private void setNextChunkDurationUs(int nextChunkDurationUs) {
      checkState(nextLoadPositionUs != C.TIME_END_OF_SOURCE);
      this.nextChunkDurationUs = nextChunkDurationUs;
    }
  }
}
