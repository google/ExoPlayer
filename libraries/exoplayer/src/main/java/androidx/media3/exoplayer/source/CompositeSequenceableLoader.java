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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.min;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.LoadingInfo;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;

/** A {@link SequenceableLoader} that encapsulates multiple other {@link SequenceableLoader}s. */
@UnstableApi
public final class CompositeSequenceableLoader implements SequenceableLoader {

  private final ImmutableList<SequenceableLoaderWithTrackTypes> loadersWithTrackTypes;
  private long lastAudioVideoBufferedPositionUs;

  /**
   * @deprecated Use {@link CompositeSequenceableLoader#CompositeSequenceableLoader(List, List)}
   *     instead.
   */
  @Deprecated
  public CompositeSequenceableLoader(SequenceableLoader[] loaders) {
    this(
        ImmutableList.copyOf(loaders),
        Collections.nCopies(loaders.length, ImmutableList.of(C.TRACK_TYPE_UNKNOWN)));
  }

  public CompositeSequenceableLoader(
      List<? extends SequenceableLoader> loaders,
      List<List<@C.TrackType Integer>> loaderTrackTypes) {
    ImmutableList.Builder<SequenceableLoaderWithTrackTypes> loaderAndTrackTypes =
        ImmutableList.builder();
    checkArgument(loaders.size() == loaderTrackTypes.size());
    for (int i = 0; i < loaders.size(); i++) {
      loaderAndTrackTypes.add(
          new SequenceableLoaderWithTrackTypes(loaders.get(i), loaderTrackTypes.get(i)));
    }
    this.loadersWithTrackTypes = loaderAndTrackTypes.build();
    this.lastAudioVideoBufferedPositionUs = C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    long bufferedPositionAudioVideoUs = Long.MAX_VALUE;
    for (int i = 0; i < loadersWithTrackTypes.size(); i++) {
      SequenceableLoaderWithTrackTypes loader = loadersWithTrackTypes.get(i);
      long loaderBufferedPositionUs = loader.getBufferedPositionUs();

      if (loader.getTrackTypes().contains(C.TRACK_TYPE_AUDIO)
          || loader.getTrackTypes().contains(C.TRACK_TYPE_VIDEO)
          || loader.getTrackTypes().contains(C.TRACK_TYPE_IMAGE)) {
        if (loaderBufferedPositionUs != C.TIME_END_OF_SOURCE) {
          bufferedPositionAudioVideoUs =
              min(bufferedPositionAudioVideoUs, loaderBufferedPositionUs);
        }
      }
      if (loaderBufferedPositionUs != C.TIME_END_OF_SOURCE) {
        bufferedPositionUs = min(bufferedPositionUs, loaderBufferedPositionUs);
      }
    }
    if (bufferedPositionAudioVideoUs != Long.MAX_VALUE) {
      lastAudioVideoBufferedPositionUs = bufferedPositionAudioVideoUs;
      return bufferedPositionAudioVideoUs;
    } else if (bufferedPositionUs != Long.MAX_VALUE) {
      // If lastAudioVideoBufferedPositionUs != C.TIME_UNSET, then we know there's at least one a/v
      // track (because this is the only time we end up assigning lastAudioVideoBufferedPositionUs
      // on a previous invocation).
      return lastAudioVideoBufferedPositionUs != C.TIME_UNSET
          ? lastAudioVideoBufferedPositionUs
          : bufferedPositionUs;
    } else {
      return C.TIME_END_OF_SOURCE;
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    long nextLoadPositionUs = Long.MAX_VALUE;
    for (int i = 0; i < loadersWithTrackTypes.size(); i++) {
      SequenceableLoaderWithTrackTypes loader = loadersWithTrackTypes.get(i);
      long loaderNextLoadPositionUs = loader.getNextLoadPositionUs();
      if (loaderNextLoadPositionUs != C.TIME_END_OF_SOURCE) {
        nextLoadPositionUs = min(nextLoadPositionUs, loaderNextLoadPositionUs);
      }
    }
    return nextLoadPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : nextLoadPositionUs;
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    for (int i = 0; i < loadersWithTrackTypes.size(); i++) {
      loadersWithTrackTypes.get(i).reevaluateBuffer(positionUs);
    }
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    boolean madeProgress = false;
    boolean madeProgressThisIteration;
    do {
      madeProgressThisIteration = false;
      long nextLoadPositionUs = getNextLoadPositionUs();
      if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
        break;
      }
      for (int i = 0; i < loadersWithTrackTypes.size(); i++) {
        long loaderNextLoadPositionUs = loadersWithTrackTypes.get(i).getNextLoadPositionUs();
        boolean isLoaderBehind =
            loaderNextLoadPositionUs != C.TIME_END_OF_SOURCE
                && loaderNextLoadPositionUs <= loadingInfo.playbackPositionUs;
        if (loaderNextLoadPositionUs == nextLoadPositionUs || isLoaderBehind) {
          madeProgressThisIteration |= loadersWithTrackTypes.get(i).continueLoading(loadingInfo);
        }
      }
      madeProgress |= madeProgressThisIteration;
    } while (madeProgressThisIteration);
    return madeProgress;
  }

  @Override
  public boolean isLoading() {
    for (int i = 0; i < loadersWithTrackTypes.size(); i++) {
      if (loadersWithTrackTypes.get(i).isLoading()) {
        return true;
      }
    }
    return false;
  }

  private static final class SequenceableLoaderWithTrackTypes implements SequenceableLoader {

    private final SequenceableLoader loader;
    private final ImmutableList<@C.TrackType Integer> trackTypes;

    public SequenceableLoaderWithTrackTypes(
        SequenceableLoader loader, List<@C.TrackType Integer> trackTypes) {
      this.loader = loader;
      this.trackTypes = ImmutableList.copyOf(trackTypes);
    }

    public ImmutableList<@C.TrackType Integer> getTrackTypes() {
      return trackTypes;
    }

    // SequenceableLoader implementation

    @Override
    public long getBufferedPositionUs() {
      return loader.getBufferedPositionUs();
    }

    @Override
    public long getNextLoadPositionUs() {
      return loader.getNextLoadPositionUs();
    }

    @Override
    public boolean continueLoading(LoadingInfo loadingInfo) {
      return loader.continueLoading(loadingInfo);
    }

    @Override
    public boolean isLoading() {
      return loader.isLoading();
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
      loader.reevaluateBuffer(positionUs);
    }
  }
}
