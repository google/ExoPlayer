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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An implementation of {@link Muxer} that forwards operations to another {@link Muxer}, counting
 * the number of frames as they go past.
 */
/* package */ final class FrameCountingMuxer implements Muxer {
  public static final class Factory implements Muxer.Factory {

    private final Muxer.Factory muxerFactory;
    private @MonotonicNonNull FrameCountingMuxer frameCountingMuxer;

    public Factory(Muxer.Factory muxerFactory) {
      this.muxerFactory = muxerFactory;
    }

    @Override
    public Muxer create(String path, String outputMimeType) throws IOException {
      frameCountingMuxer = new FrameCountingMuxer(muxerFactory.create(path, outputMimeType));
      return frameCountingMuxer;
    }

    @Override
    public Muxer create(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
        throws IOException {
      frameCountingMuxer =
          new FrameCountingMuxer(muxerFactory.create(parcelFileDescriptor, outputMimeType));
      return frameCountingMuxer;
    }

    @Override
    public boolean supportsOutputMimeType(String mimeType) {
      return muxerFactory.supportsOutputMimeType(mimeType);
    }

    @Override
    public boolean supportsSampleMimeType(@Nullable String sampleMimeType, String outputMimeType) {
      return muxerFactory.supportsSampleMimeType(sampleMimeType, outputMimeType);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(
        @C.TrackType int trackType, String containerMimeType) {
      return muxerFactory.getSupportedSampleMimeTypes(trackType, containerMimeType);
    }

    @Nullable
    public FrameCountingMuxer getLastFrameCountingMuxerCreated() {
      return frameCountingMuxer;
    }
  }

  private final Muxer muxer;
  private int videoTrackIndex;
  private int frameCount;

  private FrameCountingMuxer(Muxer muxer) throws IOException {
    this.muxer = muxer;
  }

  @Override
  public int addTrack(Format format) throws MuxerException {
    int trackIndex = muxer.addTrack(format);
    if (MimeTypes.isVideo(format.sampleMimeType)) {
      videoTrackIndex = trackIndex;
    }
    return trackIndex;
  }

  @Override
  public void writeSampleData(
      int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException {
    muxer.writeSampleData(trackIndex, data, isKeyFrame, presentationTimeUs);
    if (trackIndex == videoTrackIndex) {
      frameCount++;
    }
  }

  @Override
  public void release(boolean forCancellation) throws MuxerException {
    muxer.release(forCancellation);
  }

  /* Returns the number of frames written for the video track. */
  public int getFrameCount() {
    return frameCount;
  }
}
