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
package com.google.android.exoplayer2.transformer;

import android.os.ParcelFileDescriptor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/** A default {@link Muxer} implementation. */
public final class DefaultMuxer implements Muxer {

  /** A {@link Muxer.Factory} for {@link DefaultMuxer}. */
  public static final class Factory implements Muxer.Factory {

    /** The default value returned by {@link #getMaxDelayBetweenSamplesMs()}. */
    public static final long DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS = 3000;

    private final Muxer.Factory muxerFactory;

    /**
     * Creates an instance with {@link Muxer#getMaxDelayBetweenSamplesMs() maxDelayBetweenSamplesMs}
     * set to {@link #DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS}.
     */
    public Factory() {
      this.muxerFactory = new FrameworkMuxer.Factory(DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS);
    }

    /**
     * Creates an instance.
     *
     * @param maxDelayBetweenSamplesMs See {@link Muxer#getMaxDelayBetweenSamplesMs()}.
     */
    public Factory(long maxDelayBetweenSamplesMs) {
      this.muxerFactory = new FrameworkMuxer.Factory(maxDelayBetweenSamplesMs);
    }

    @Override
    public Muxer create(String path) throws MuxerException {
      return new DefaultMuxer(muxerFactory.create(path));
    }

    @Override
    public Muxer create(ParcelFileDescriptor parcelFileDescriptor) throws MuxerException {
      return new DefaultMuxer(muxerFactory.create(parcelFileDescriptor));
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
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException {
    muxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
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
