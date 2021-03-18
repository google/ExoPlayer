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
import android.media.MediaCodec.LinearBlock;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import java.nio.ByteBuffer;

/**
 * Abstracts {@link MediaCodec} operations.
 *
 * <p>{@code MediaCodecAdapter} offers a common interface to interact with a {@link MediaCodec}
 * regardless of the mode the {@link MediaCodec} is operating in.
 */
public interface MediaCodecAdapter {

  /** A factory for {@link MediaCodecAdapter} instances. */
  interface Factory {

    /** Default factory used in most cases. */
    Factory DEFAULT = new SynchronousMediaCodecAdapter.Factory();

    /** Creates an instance wrapping the provided {@link MediaCodec} instance. */
    MediaCodecAdapter createAdapter(MediaCodec codec);
  }

  /**
   * Listener to be called when an output frame has rendered on the output surface.
   *
   * @see MediaCodec.OnFrameRenderedListener
   */
  interface OnFrameRenderedListener {
    void onFrameRendered(MediaCodecAdapter codec, long presentationTimeUs, long nanoTime);
  }

  /**
   * Configures this adapter and the underlying {@link MediaCodec}. Needs to be called before {@link
   * #start()}.
   *
   * @see MediaCodec#configure(MediaFormat, Surface, MediaCrypto, int)
   */
  void configure(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags);

  /**
   * Starts this instance. Needs to be called after {@link #configure}.
   *
   * @see MediaCodec#start()
   */
  void start();

  /**
   * Returns the next available input buffer index from the underlying {@link MediaCodec} or {@link
   * MediaCodec#INFO_TRY_AGAIN_LATER} if no such buffer exists.
   *
   * @throws IllegalStateException If the underlying {@link MediaCodec} raised an error.
   */
  int dequeueInputBufferIndex();

  /**
   * Returns the next available output buffer index from the underlying {@link MediaCodec}. If the
   * next available output is a MediaFormat change, it will return {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} and you should call {@link #getOutputFormat()} to get
   * the format. If there is no available output, this method will return {@link
   * MediaCodec#INFO_TRY_AGAIN_LATER}.
   *
   * @throws IllegalStateException If the underlying {@link MediaCodec} raised an error.
   */
  int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo);

  /**
   * Gets the {@link MediaFormat} that was output from the {@link MediaCodec}.
   *
   * <p>Call this method if a previous call to {@link #dequeueOutputBufferIndex} returned {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   */
  MediaFormat getOutputFormat();

  /**
   * Returns a writable ByteBuffer object for a dequeued input buffer index.
   *
   * @see MediaCodec#getInputBuffer(int)
   */
  @Nullable
  ByteBuffer getInputBuffer(int index);

  /**
   * Returns a read-only ByteBuffer for a dequeued output buffer index.
   *
   * @see MediaCodec#getOutputBuffer(int)
   */
  @Nullable
  ByteBuffer getOutputBuffer(int index);

  /**
   * Submit an input buffer for decoding.
   *
   * <p>The {@code index} must be an input buffer index that has been obtained from a previous call
   * to {@link #dequeueInputBufferIndex()}.
   *
   * @see MediaCodec#queueInputBuffer
   */
  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);
  
  /**
   * Submit an LinearBlock for decoding.
   *
   * <p>The {@code index} must be an LinearBlock index that has been obtained from a previous call
   * to {@link #dequeueInputBufferIndex()} in {@link MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL}.
   *
   * @see MediaCodec.QueueRequest#setLinearBlock
   */
  @RequiresApi(30)
  void queueInputLinearBlockBuffer(int index, LinearBlock linearBlock, int offset, int size, long presentationTimeUs, int flags);

  /**
   * Submit an input buffer that is potentially encrypted for decoding.
   *
   * <p>The {@code index} must be an input buffer index that has been obtained from a previous call
   * to {@link #dequeueInputBufferIndex()}.
   *
   * <p>This method behaves like {@link MediaCodec#queueSecureInputBuffer}, with the difference that
   * {@code info} is of type {@link CryptoInfo} and not {@link android.media.MediaCodec.CryptoInfo}.
   *
   * @see MediaCodec#queueSecureInputBuffer
   */
  void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags);

  /**
   * Returns the buffer to the {@link MediaCodec}. If the {@link MediaCodec} was configured with an
   * output surface, setting {@code render} to {@code true} will first send the buffer to the output
   * surface. The surface will release the buffer back to the codec once it is no longer
   * used/displayed.
   *
   * @see MediaCodec#releaseOutputBuffer(int, boolean)
   */
  void releaseOutputBuffer(int index, boolean render);

  /**
   * Updates the output buffer's surface timestamp and sends it to the {@link MediaCodec} to render
   * it on the output surface. If the {@link MediaCodec} is not configured with an output surface,
   * this call will simply return the buffer to the {@link MediaCodec}.
   *
   * @see MediaCodec#releaseOutputBuffer(int, long)
   */
  @RequiresApi(21)
  void releaseOutputBuffer(int index, long renderTimeStampNs);

  /** Flushes the adapter and the underlying {@link MediaCodec}. */
  void flush();

  /** Releases the adapter and the underlying {@link MediaCodec}. */
  void release();

  /**
   * Registers a callback to be invoked when an output frame is rendered on the output surface.
   *
   * @see MediaCodec#setOnFrameRenderedListener
   */
  @RequiresApi(23)
  void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler);

  /**
   * Dynamically sets the output surface of a {@link MediaCodec}.
   *
   * @see MediaCodec#setOutputSurface(Surface)
   */
  @RequiresApi(23)
  void setOutputSurface(Surface surface);

  /**
   * Communicate additional parameter changes to the {@link MediaCodec} instance.
   *
   * @see MediaCodec#setParameters(Bundle)
   */
  @RequiresApi(19)
  void setParameters(Bundle params);

  /**
   * Specifies the scaling mode to use, if a surface has been specified in a previous call to {@link
   * #configure}.
   *
   * @see MediaCodec#setVideoScalingMode(int)
   */
  void setVideoScalingMode(@C.VideoScalingMode int scalingMode);
}
