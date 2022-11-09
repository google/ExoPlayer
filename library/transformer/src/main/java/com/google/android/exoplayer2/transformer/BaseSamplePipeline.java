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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ abstract class BaseSamplePipeline implements SamplePipeline {

  private final long streamOffsetUs;
  private final long streamStartPositionUs;
  private final MuxerWrapper muxerWrapper;
  private final @C.TrackType int trackType;
  private final @MonotonicNonNull SefSlowMotionFlattener sefVideoSlowMotionFlattener;

  @Nullable private DecoderInputBuffer inputBuffer;
  private boolean muxerWrapperTrackAdded;
  private boolean isEnded;

  public BaseSamplePipeline(
      Format inputFormat,
      long streamOffsetUs,
      long streamStartPositionUs,
      boolean flattenForSlowMotion,
      MuxerWrapper muxerWrapper) {
    this.streamOffsetUs = streamOffsetUs;
    this.streamStartPositionUs = streamStartPositionUs;
    this.muxerWrapper = muxerWrapper;
    trackType = MimeTypes.getTrackType(inputFormat.sampleMimeType);
    sefVideoSlowMotionFlattener =
        flattenForSlowMotion && trackType == C.TRACK_TYPE_VIDEO
            ? new SefSlowMotionFlattener(inputFormat)
            : null;
  }

  @Nullable
  @Override
  public DecoderInputBuffer dequeueInputBuffer() throws TransformationException {
    inputBuffer = dequeueInputBufferInternal();
    return inputBuffer;
  }

  @Override
  public void queueInputBuffer() throws TransformationException {
    checkNotNull(inputBuffer);
    checkNotNull(inputBuffer.data);
    if (!shouldDropInputBuffer()) {
      queueInputBufferInternal();
    }
  }

  @Override
  public boolean processData() throws TransformationException {
    return feedMuxer() || processDataUpToMuxer();
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

  @Nullable
  protected abstract DecoderInputBuffer dequeueInputBufferInternal() throws TransformationException;

  protected abstract void queueInputBufferInternal() throws TransformationException;

  protected abstract boolean processDataUpToMuxer() throws TransformationException;

  @Nullable
  protected abstract Format getMuxerInputFormat() throws TransformationException;

  @Nullable
  protected abstract DecoderInputBuffer getMuxerInputBuffer() throws TransformationException;

  protected abstract void releaseMuxerInputBuffer() throws TransformationException;

  protected abstract boolean isMuxerInputEnded();

  /**
   * Preprocesses an {@linkplain DecoderInputBuffer input buffer} queued to the pipeline and returns
   * whether it should be dropped.
   */
  @RequiresNonNull({"inputBuffer", "inputBuffer.data"})
  private boolean shouldDropInputBuffer() {
    ByteBuffer inputBytes = inputBuffer.data;

    if (sefVideoSlowMotionFlattener == null || inputBuffer.isEndOfStream()) {
      return false;
    }

    long presentationTimeUs = inputBuffer.timeUs - streamOffsetUs;
    DecoderInputBuffer inputBuffer = this.inputBuffer;
    boolean shouldDropInputBuffer =
        sefVideoSlowMotionFlattener.dropOrTransformSample(inputBytes, presentationTimeUs);
    if (shouldDropInputBuffer) {
      inputBytes.clear();
    } else {
      inputBuffer.timeUs =
          streamOffsetUs + sefVideoSlowMotionFlattener.getSamplePresentationTimeUs();
    }
    return shouldDropInputBuffer;
  }

  /**
   * Attempts to pass encoded data to the muxer, and returns whether it may be possible to pass more
   * data immediately by calling this method again.
   */
  private boolean feedMuxer() throws TransformationException {
    if (!muxerWrapperTrackAdded) {
      @Nullable Format inputFormat = getMuxerInputFormat();
      if (inputFormat == null) {
        return false;
      }
      try {
        muxerWrapper.addTrackFormat(inputFormat);
      } catch (Muxer.MuxerException e) {
        throw TransformationException.createForMuxer(
            e, TransformationException.ERROR_CODE_MUXING_FAILED);
      }
      muxerWrapperTrackAdded = true;
    }

    if (isMuxerInputEnded()) {
      muxerWrapper.endTrack(trackType);
      isEnded = true;
      return false;
    }

    @Nullable DecoderInputBuffer muxerInputBuffer = getMuxerInputBuffer();
    if (muxerInputBuffer == null) {
      return false;
    }

    long samplePresentationTimeUs = muxerInputBuffer.timeUs - streamStartPositionUs;
    // TODO(b/204892224): Consider subtracting the first sample timestamp from the sample pipeline
    //  buffer from all samples so that they are guaranteed to start from zero in the output file.
    try {
      if (!muxerWrapper.writeSample(
          trackType,
          checkStateNotNull(muxerInputBuffer.data),
          muxerInputBuffer.isKeyFrame(),
          samplePresentationTimeUs)) {
        return false;
      }
    } catch (Muxer.MuxerException e) {
      throw TransformationException.createForMuxer(
          e, TransformationException.ERROR_CODE_MUXING_FAILED);
    }

    releaseMuxerInputBuffer();
    return true;
  }
}
