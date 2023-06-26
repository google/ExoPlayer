/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.transformer;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/** A default {@link Muxer} implementation. */
@UnstableApi
public final class DefaultMuxer implements Muxer {

  /** A {@link Muxer.Factory} for {@link DefaultMuxer}. */
  public static final class Factory implements Muxer.Factory {

    /** The default value returned by {@link #getMaxDelayBetweenSamplesMs()}. */
    public static final long DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS = 10_000;

    private final Muxer.Factory muxerFactory;

    /**
     * Creates an instance with {@link Muxer#getMaxDelayBetweenSamplesMs() maxDelayBetweenSamplesMs}
     * set to {@link #DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS}.
     */
    public Factory() {
      this(/* maxDelayBetweenSamplesMs= */ DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS);
    }

    /**
     * Creates an instance.
     *
     * @param maxDelayBetweenSamplesMs See {@link Muxer#getMaxDelayBetweenSamplesMs()}.
     */
    public Factory(long maxDelayBetweenSamplesMs) {
      this(maxDelayBetweenSamplesMs, /* videoDurationMs= */ C.TIME_UNSET);
    }

    /**
     * Creates an instance.
     *
     * @param maxDelayBetweenSamplesMs See {@link Muxer#getMaxDelayBetweenSamplesMs()}.
     * @param videoDurationMs The duration of the video track (in milliseconds) to enforce in the
     *     output, or {@link C#TIME_UNSET} to not enforce. Only applicable when a video track is
     *     {@linkplain #addTrack(Format) added}.
     */
    public Factory(long maxDelayBetweenSamplesMs, long videoDurationMs) {
      this.muxerFactory = new FrameworkMuxer.Factory(maxDelayBetweenSamplesMs, videoDurationMs);
    }

    @Override
    public Muxer create(String path) throws MuxerException {
      return new DefaultMuxer(muxerFactory.create(path));
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return muxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private final Muxer muxer;

  private DefaultMuxer(Muxer muxer) {
    this.muxer = muxer;
  }

  @Override
  public int addTrack(Format format) throws MuxerException {
    return muxer.addTrack(format);
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
      throws MuxerException {
    muxer.writeSampleData(trackIndex, data, presentationTimeUs, flags);
  }

  @Override
  public void addMetadata(Metadata metadata) {
    muxer.addMetadata(metadata);
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    muxer.release(forCancellation);
  }

  @Override
  public long getMaxDelayBetweenSamplesMs() {
    return muxer.getMaxDelayBetweenSamplesMs();
  }
}
