/*
 * Copyright 2021 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaFormatUtil;
import com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A wrapper around {@link MediaCodecAdapter}.
 *
 * <p>Provides a layer of abstraction for callers that need to interact with {@link MediaCodec}
 * through {@link MediaCodecAdapter}. This is done by simplifying the calls needed to queue and
 * dequeue buffers, removing the need to track buffer indices and codec events.
 */
/* package */ final class MediaCodecAdapterWrapper {

  private final BufferInfo outputBufferInfo;
  private final MediaCodecAdapter codec;
  private final Format format;

  @Nullable private ByteBuffer outputBuffer;

  private int inputBufferIndex;
  private int outputBufferIndex;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean hasOutputFormat;

  /**
   * Returns a {@link MediaCodecAdapterWrapper} for a configured and started {@link
   * MediaCodecAdapter} audio decoder.
   *
   * @param format The {@link Format} (of the input data) used to determine the underlying {@link
   *     MediaCodec} and its configuration values.
   * @return A configured and started decoder wrapper.
   * @throws IOException If the underlying codec cannot be created.
   */
  @RequiresNonNull("#1.sampleMimeType")
  public static MediaCodecAdapterWrapper createForAudioDecoding(Format format) throws IOException {
    @Nullable MediaCodec decoder = null;
    @Nullable MediaCodecAdapter adapter = null;
    try {
      decoder = MediaCodec.createDecoderByType(format.sampleMimeType);
      MediaFormat mediaFormat =
          MediaFormat.createAudioFormat(
              format.sampleMimeType, format.sampleRate, format.channelCount);
      MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
      adapter = new SynchronousMediaCodecAdapter.Factory().createAdapter(decoder);
      adapter.configure(mediaFormat, /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
      adapter.start();
      return new MediaCodecAdapterWrapper(adapter, format);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
      } else if (decoder != null) {
        decoder.release();
      }
      throw e;
    }
  }

  /**
   * Returns a {@link MediaCodecAdapterWrapper} for a configured and started {@link
   * MediaCodecAdapter} audio encoder.
   *
   * @param format The {@link Format} (of the output data) used to determine the underlying {@link
   *     MediaCodec} and its configuration values.
   * @return A configured and started encoder wrapper.
   * @throws IOException If the underlying codec cannot be created.
   */
  @RequiresNonNull("#1.sampleMimeType")
  public static MediaCodecAdapterWrapper createForAudioEncoding(Format format) throws IOException {
    @Nullable MediaCodec encoder = null;
    @Nullable MediaCodecAdapter adapter = null;
    try {
      encoder = MediaCodec.createEncoderByType(format.sampleMimeType);
      MediaFormat mediaFormat =
          MediaFormat.createAudioFormat(
              format.sampleMimeType, format.sampleRate, format.channelCount);
      mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);
      adapter = new SynchronousMediaCodecAdapter.Factory().createAdapter(encoder);
      adapter.configure(
          mediaFormat,
          /* surface= */ null,
          /* crypto= */ null,
          /* flags= */ MediaCodec.CONFIGURE_FLAG_ENCODE);
      adapter.start();
      return new MediaCodecAdapterWrapper(adapter, format);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
      } else if (encoder != null) {
        encoder.release();
      }
      throw e;
    }
  }

  private MediaCodecAdapterWrapper(MediaCodecAdapter codec, Format format) {
    this.codec = codec;
    this.format = format;
    outputBufferInfo = new BufferInfo();
    inputBufferIndex = C.INDEX_UNSET;
    outputBufferIndex = C.INDEX_UNSET;
  }

  /**
   * Dequeues a writable input buffer, if available.
   *
   * @param inputBuffer The buffer where the dequeued buffer data is stored.
   * @return Whether an input buffer is ready to be used.
   */
  @EnsuresNonNullIf(expression = "#1.data", result = true)
  public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer) {
    if (inputStreamEnded) {
      return false;
    }
    if (inputBufferIndex < 0) {
      inputBufferIndex = codec.dequeueInputBufferIndex();
      if (inputBufferIndex < 0) {
        return false;
      }
      inputBuffer.data = codec.getInputBuffer(inputBufferIndex);
      inputBuffer.clear();
    }
    checkNotNull(inputBuffer.data);
    return true;
  }

  /**
   * Queues an input buffer.
   *
   * @param inputBuffer The buffer to be queued.
   * @return Whether more input buffers can be queued.
   */
  public boolean queueInputBuffer(DecoderInputBuffer inputBuffer) {
    checkState(
        !inputStreamEnded, "Input buffer can not be queued after the input stream has ended.");

    int offset = 0;
    int size = 0;
    if (inputBuffer.data != null && inputBuffer.data.hasRemaining()) {
      offset = inputBuffer.data.position();
      size = inputBuffer.data.remaining();
    }
    int flags = 0;
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }
    codec.queueInputBuffer(inputBufferIndex, offset, size, inputBuffer.timeUs, flags);
    inputBufferIndex = C.INDEX_UNSET;
    inputBuffer.data = null;
    return !inputStreamEnded;
  }

  /**
   * Dequeues an output buffer, if available.
   *
   * <p>Once this method returns {@code true}, call {@link #getOutputBuffer()} to access the
   * dequeued buffer.
   *
   * @return Whether an output buffer is available.
   */
  public boolean maybeDequeueOutputBuffer() {
    if (outputBufferIndex >= 0) {
      return true;
    }
    if (outputStreamEnded) {
      return false;
    }

    outputBufferIndex = codec.dequeueOutputBufferIndex(outputBufferInfo);
    if (outputBufferIndex < 0) {
      if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !hasOutputFormat) {
        hasOutputFormat = true;
      }
      return false;
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      outputStreamEnded = true;
      if (outputBufferInfo.size == 0) {
        releaseOutputBuffer();
        return false;
      }
    }

    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Encountered a CSD buffer, skip it.
      releaseOutputBuffer();
      return false;
    }

    outputBuffer = checkNotNull(codec.getOutputBuffer(outputBufferIndex));
    outputBuffer.position(outputBufferInfo.offset);
    outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);

    return true;
  }

  /**
   * Returns a {@link Format} based on the {@link MediaCodecAdapter#getOutputFormat() mediaFormat},
   * if available.
   */
  @Nullable
  public Format getOutputFormat() {
    @Nullable MediaFormat mediaFormat = hasOutputFormat ? codec.getOutputFormat() : null;
    if (mediaFormat == null) {
      return null;
    }

    ImmutableList.Builder<byte[]> csdBuffers = new ImmutableList.Builder<>();
    int csdIndex = 0;
    while (true) {
      @Nullable ByteBuffer csdByteBuffer = mediaFormat.getByteBuffer("csd-" + csdIndex);
      if (csdByteBuffer == null) {
        break;
      }
      byte[] csdBufferData = new byte[csdByteBuffer.remaining()];
      csdByteBuffer.get(csdBufferData);
      csdBuffers.add(csdBufferData);
      csdIndex++;
    }

    return new Format.Builder()
        .setSampleMimeType(mediaFormat.getString(MediaFormat.KEY_MIME))
        .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        .setInitializationData(csdBuffers.build())
        .build();
  }

  /** Returns the {@link Format} used to create and configure the underlying {@link MediaCodec}. */
  public Format getConfigFormat() {
    return format;
  }

  /** Returns the current output {@link ByteBuffer}, if available. */
  @Nullable
  public ByteBuffer getOutputBuffer() {
    return outputBuffer;
  }

  /** Returns the {@link BufferInfo} associated with the current output buffer, if available. */
  @Nullable
  public BufferInfo getOutputBufferInfo() {
    return outputBuffer == null ? null : outputBufferInfo;
  }

  /**
   * Releases the current output buffer.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the previous has been released.
   */
  public void releaseOutputBuffer() {
    outputBuffer = null;
    codec.releaseOutputBuffer(outputBufferIndex, /* render= */ false);
    outputBufferIndex = C.INDEX_UNSET;
  }

  /** Returns whether the codec output stream has ended, and no more data can be dequeued. */
  public boolean isEnded() {
    return outputStreamEnded && outputBufferIndex == C.INDEX_UNSET;
  }

  /** Releases the underlying codec. */
  public void release() {
    outputBuffer = null;
    codec.release();
  }
}
