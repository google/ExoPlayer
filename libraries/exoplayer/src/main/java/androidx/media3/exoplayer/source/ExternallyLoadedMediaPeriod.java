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

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.NullableType;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.ExternalLoader.LoadRequest;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaPeriod} that puts a {@link Charsets#UTF_8}-encoded {@link Uri} into the sample
 * queue as a single sample.
 */
/* package */ final class ExternallyLoadedMediaPeriod implements MediaPeriod {

  private final Uri uri;
  private final ExternalLoader externalLoader;
  private final TrackGroupArray tracks;
  private final byte[] sampleData;
  private final AtomicBoolean loadingFinished;
  private final AtomicReference<Throwable> loadingThrowable;
  private @MonotonicNonNull ListenableFuture<?> loadingFuture;

  public ExternallyLoadedMediaPeriod(Uri uri, String mimeType, ExternalLoader externalLoader) {
    this.uri = uri;
    Format format = new Format.Builder().setSampleMimeType(mimeType).build();
    this.externalLoader = externalLoader;
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleData = uri.toString().getBytes(Charsets.UTF_8);
    loadingFinished = new AtomicBoolean();
    loadingThrowable = new AtomicReference<>();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    callback.onPrepared(this);
    loadingFuture = externalLoader.load(new LoadRequest(uri));
    Futures.addCallback(
        loadingFuture,
        new FutureCallback<@NullableType Object>() {
          @Override
          public void onSuccess(@Nullable Object result) {
            loadingFinished.set(true);
          }

          @Override
          public void onFailure(Throwable t) {
            loadingThrowable.set(t);
          }
        },
        MoreExecutors.directExecutor());
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
    return loadingFinished.get() ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public long getNextLoadPositionUs() {
    return loadingFinished.get() ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    return !loadingFinished.get();
  }

  @Override
  public boolean isLoading() {
    return !loadingFinished.get();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  public void releasePeriod() {
    if (loadingFuture != null) {
      loadingFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
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
      return loadingFinished.get();
    }

    @Override
    public void maybeThrowError() throws IOException {
      @Nullable
      Throwable loadingThrowable = ExternallyLoadedMediaPeriod.this.loadingThrowable.get();
      if (loadingThrowable != null) {
        throw new IOException(loadingThrowable);
      }
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

      if (!loadingFinished.get()) {
        return C.RESULT_NOTHING_READ;
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
