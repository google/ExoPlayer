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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.LoadingInfo;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.NullableType;
import com.google.common.base.Charsets;

/**
 * A {@link MediaPeriod} that puts a {@link Charsets#UTF_8}-encoded {@link Uri} into the sample
 * queue as a single sample.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class ExternallyLoadedMediaPeriod implements MediaPeriod {

  private final Format format;
  private final TrackGroupArray tracks;
  private final byte[] sampleData;

  // TODO: b/303375301 - Removing this variable (replacing it with static returns in the methods
  // that
  //   use it) causes playback to hang.
  private boolean loadingFinished;

  public ExternallyLoadedMediaPeriod(Uri uri, String mimeType) {
    this.format = new Format.Builder().setSampleMimeType(mimeType).build();
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleData = uri.toString().getBytes(Charsets.UTF_8);
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        SampleStreamImpl stream = new SampleStreamImpl();
        streams[i] = stream;
        streamResetFlags[i] = true;
      }
    }
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    // Do nothing.
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long seekToUs(long positionUs) {
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    return loadingFinished ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public long getNextLoadPositionUs() {
    return loadingFinished ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    if (loadingFinished) {
      return false;
    }
    loadingFinished = true;
    return true;
  }

  @Override
  public boolean isLoading() {
    return !loadingFinished;
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  private final class SampleStreamImpl implements SampleStream {

    private static final int STREAM_STATE_SEND_FORMAT = 0;
    private static final int STREAM_STATE_SEND_SAMPLE = 1;
    private static final int STREAM_STATE_END_OF_STREAM = 2;

    private int streamState;

    public SampleStreamImpl() {
      streamState = STREAM_STATE_SEND_FORMAT;
    }

    @Override
    public boolean isReady() {
      return loadingFinished;
    }

    @Override
    public void maybeThrowError() {
      // Do nothing.

    }

    @Override
    public @ReadDataResult int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {

      if (streamState == STREAM_STATE_END_OF_STREAM) {
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      }

      if ((readFlags & FLAG_REQUIRE_FORMAT) != 0 || streamState == STREAM_STATE_SEND_FORMAT) {
        formatHolder.format = tracks.get(0).getFormat(0);
        streamState = STREAM_STATE_SEND_SAMPLE;
        return C.RESULT_FORMAT_READ;
      }

      int sampleSize = sampleData.length;
      buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      buffer.timeUs = 0;
      if ((readFlags & FLAG_OMIT_SAMPLE_DATA) == 0) {
        buffer.ensureSpaceForWrite(sampleSize);
        buffer.data.put(sampleData, /* offset= */ 0, sampleSize);
      }
      if ((readFlags & FLAG_PEEK) == 0) {
        streamState = STREAM_STATE_END_OF_STREAM;
      }
      return C.RESULT_BUFFER_READ;
    }

    @Override
    public int skipData(long positionUs) {
      // We should never skip our sample because the sample before any positive time is our only
      // sample in the stream.
      return 0;
    }
  }
}
