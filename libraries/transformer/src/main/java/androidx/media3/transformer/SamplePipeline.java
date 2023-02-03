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

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;

/**
 * Pipeline for processing media data.
 *
 * <p>This pipeline can be used to implement transformations of audio or video samples.
 */
/* package */ abstract class SamplePipeline implements SampleConsumer, OnMediaItemChangedListener {

  private final long streamStartPositionUs;
  private final MuxerWrapper muxerWrapper;
  private final @C.TrackType int trackType;

  private boolean muxerWrapperTrackAdded;

  public SamplePipeline(
      Format firstInputFormat, long streamStartPositionUs, MuxerWrapper muxerWrapper) {
    this.streamStartPositionUs = streamStartPositionUs;
    this.muxerWrapper = muxerWrapper;
    trackType = MimeTypes.getTrackType(firstInputFormat.sampleMimeType);
  }

  protected static TransformationException createNoSupportedMimeTypeException(
      Format requestedEncoderFormat) {
    return TransformationException.createForCodec(
        new IllegalArgumentException("No MIME type is supported by both encoder and muxer."),
        TransformationException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        MimeTypes.isVideo(requestedEncoderFormat.sampleMimeType),
        /* isDecoder= */ false,
        requestedEncoderFormat);
  }

  @Override
  public boolean expectsDecodedData() {
    return true;
  }

  /**
   * Processes the input data and returns whether it may be possible to process more data by calling
   * this method again.
   */
  public final boolean processData() throws TransformationException {
    return feedMuxer() || processDataUpToMuxer();
  }

  /** Releases all resources held by the pipeline. */
  public abstract void release();

  protected boolean processDataUpToMuxer() throws TransformationException {
    return false;
  }

  @Nullable
  protected abstract Format getMuxerInputFormat() throws TransformationException;

  @Nullable
  protected abstract DecoderInputBuffer getMuxerInputBuffer() throws TransformationException;

  protected abstract void releaseMuxerInputBuffer() throws TransformationException;

  protected abstract boolean isMuxerInputEnded();

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
