/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.util.IntArrayQueue;
import java.util.ArrayDeque;

/** Handles the asynchronous callbacks from {@link android.media.MediaCodec.Callback}. */
@RequiresApi(21)
/* package */ final class MediaCodecAsyncCallback extends MediaCodec.Callback {
  private final IntArrayQueue availableInputBuffers;
  private final IntArrayQueue availableOutputBuffers;
  private final ArrayDeque<MediaCodec.BufferInfo> bufferInfos;
  private final ArrayDeque<MediaFormat> formats;
  @Nullable private MediaFormat currentFormat;
  @Nullable private IllegalStateException mediaCodecException;

  /** Creates a new MediaCodecAsyncCallback. */
  public MediaCodecAsyncCallback() {
    availableInputBuffers = new IntArrayQueue();
    availableOutputBuffers = new IntArrayQueue();
    bufferInfos = new ArrayDeque<>();
    formats = new ArrayDeque<>();
  }

  /**
   * Returns the next available input buffer index or {@link MediaCodec#INFO_TRY_AGAIN_LATER} if no
   * such buffer exists.
   */
  public int dequeueInputBufferIndex() {
    return availableInputBuffers.isEmpty()
        ? MediaCodec.INFO_TRY_AGAIN_LATER
        : availableInputBuffers.remove();
  }

  /**
   * Returns the next available output buffer index. If the next available output is a MediaFormat
   * change, it will return {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} and you should call {@link
   * #getOutputFormat()} to get the format. If there is no available output, this method will return
   * {@link MediaCodec#INFO_TRY_AGAIN_LATER}.
   */
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    if (availableOutputBuffers.isEmpty()) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      int bufferIndex = availableOutputBuffers.remove();
      if (bufferIndex >= 0) {
        MediaCodec.BufferInfo nextBufferInfo = bufferInfos.remove();
        bufferInfo.set(
            nextBufferInfo.offset,
            nextBufferInfo.size,
            nextBufferInfo.presentationTimeUs,
            nextBufferInfo.flags);
      } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        currentFormat = formats.remove();
      }
      return bufferIndex;
    }
  }

  /**
   * Returns the {@link MediaFormat} signalled by the underlying {@link MediaCodec}.
   *
   * <p>Call this <b>after</b> {@link #dequeueOutputBufferIndex} returned {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   *
   * @throws {@link IllegalStateException} if you call this method before before {
   * @link #dequeueOutputBufferIndex} returned {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   */
  public MediaFormat getOutputFormat() throws IllegalStateException {
    if (currentFormat == null) {
      throw new IllegalStateException();
    }

    return currentFormat;
  }

  /**
   * Checks and throws an {@link IllegalStateException} if an error was previously set on this
   * instance via {@link #onError}.
   */
  public void maybeThrowMediaCodecException() throws IllegalStateException {
    IllegalStateException exception = mediaCodecException;
    mediaCodecException = null;

    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Flushes the MediaCodecAsyncCallback. This method removes all available input and output buffers
   * and any error that was previously set.
   */
  public void flush() {
    availableInputBuffers.clear();
    availableOutputBuffers.clear();
    bufferInfos.clear();
    formats.clear();
    mediaCodecException = null;
  }

  @Override
  public void onInputBufferAvailable(MediaCodec mediaCodec, int i) {
    availableInputBuffers.add(i);
  }

  @Override
  public void onOutputBufferAvailable(
      MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {
    availableOutputBuffers.add(i);
    bufferInfos.add(bufferInfo);
  }

  @Override
  public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
    onMediaCodecError(e);
  }

  @Override
  public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
    availableOutputBuffers.add(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    formats.add(mediaFormat);
  }

  @VisibleForTesting()
  void onMediaCodecError(IllegalStateException e) {
    mediaCodecException = e;
  }
}
