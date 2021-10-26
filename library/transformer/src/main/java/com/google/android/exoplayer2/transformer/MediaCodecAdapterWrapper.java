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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter.Configuration;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.SynchronousMediaCodecAdapter;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A wrapper around {@link MediaCodecAdapter}.
 *
 * <p>Provides a layer of abstraction for callers that need to interact with {@link MediaCodec}
 * through {@link MediaCodecAdapter}. This is done by simplifying the calls needed to queue and
 * dequeue buffers, removing the need to track buffer indices and codec events.
 */
@RequiresApi(18)
/* package */ final class MediaCodecAdapterWrapper {

  // MediaCodec decoders always output 16 bit PCM, unless configured to output PCM float.
  // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers.
  private static final int MEDIA_CODEC_PCM_ENCODING = C.ENCODING_PCM_16BIT;

  private final BufferInfo outputBufferInfo;
  private final MediaCodecAdapter codec;

  private @MonotonicNonNull Format outputFormat;
  @Nullable private ByteBuffer outputBuffer;

  private int inputBufferIndex;
  private int outputBufferIndex;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;

  private static class Factory extends SynchronousMediaCodecAdapter.Factory {
    @Override
    protected MediaCodec createCodec(Configuration configuration) throws IOException {
      String sampleMimeType =
          checkNotNull(configuration.mediaFormat.getString(MediaFormat.KEY_MIME));
      boolean isDecoder = (configuration.flags & MediaCodec.CONFIGURE_FLAG_ENCODE) == 0;
      return isDecoder
          ? MediaCodec.createDecoderByType(checkNotNull(sampleMimeType))
          : MediaCodec.createEncoderByType(checkNotNull(sampleMimeType));
    }
  }

  private static MediaCodecInfo createPlaceholderMediaCodecInfo() {
    return MediaCodecInfo.newInstance(
        /* name= */ "name-placeholder",
        /* mimeType= */ "mime-type-placeholder",
        /* codecMimeType= */ "mime-type-placeholder",
        /* capabilities= */ null,
        /* hardwareAccelerated= */ false,
        /* softwareOnly= */ false,
        /* vendor= */ false,
        /* forceDisableAdaptive= */ false,
        /* forceSecure= */ false);
  }

  /**
   * Returns a {@link MediaCodecAdapterWrapper} for a configured and started {@link
   * MediaCodecAdapter} audio decoder.
   *
   * @param format The {@link Format} (of the input data) used to determine the underlying {@link
   *     MediaCodec} and its configuration values.
   * @return A configured and started decoder wrapper.
   * @throws IOException If the underlying codec cannot be created.
   */
  public static MediaCodecAdapterWrapper createForAudioDecoding(Format format) throws IOException {
    @Nullable MediaCodecAdapter adapter = null;
    try {
      MediaFormat mediaFormat =
          MediaFormat.createAudioFormat(
              checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
      MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
      adapter =
          new Factory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForAudioDecoding(
                      createPlaceholderMediaCodecInfo(), mediaFormat, format, /* crypto= */ null));
      return new MediaCodecAdapterWrapper(adapter);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
      }
      throw e;
    }
  }

  /**
   * Returns a {@link MediaCodecAdapterWrapper} for a configured and started {@link
   * MediaCodecAdapter} video decoder.
   *
   * @param format The {@link Format} (of the input data) used to determine the underlying {@link
   *     MediaCodec} and its configuration values.
   * @param surface The {@link Surface} to which the decoder output is rendered.
   * @return A configured and started decoder wrapper.
   * @throws IOException If the underlying codec cannot be created.
   */
  public static MediaCodecAdapterWrapper createForVideoDecoding(Format format, Surface surface)
      throws IOException {
    @Nullable MediaCodecAdapter adapter = null;
    try {
      MediaFormat mediaFormat =
          MediaFormat.createVideoFormat(
              checkNotNull(format.sampleMimeType), format.width, format.height);
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, format.maxInputSize);
      MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
      adapter =
          new Factory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForVideoDecoding(
                      createPlaceholderMediaCodecInfo(),
                      mediaFormat,
                      format,
                      surface,
                      /* crypto= */ null));
      return new MediaCodecAdapterWrapper(adapter);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
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
  public static MediaCodecAdapterWrapper createForAudioEncoding(Format format) throws IOException {
    @Nullable MediaCodec encoder = null;
    @Nullable MediaCodecAdapter adapter = null;
    try {
      MediaFormat mediaFormat =
          MediaFormat.createAudioFormat(
              checkNotNull(format.sampleMimeType), format.sampleRate, format.channelCount);
      mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate);
      adapter =
          new Factory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForAudioEncoding(
                      createPlaceholderMediaCodecInfo(), mediaFormat, format));
      return new MediaCodecAdapterWrapper(adapter);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
      } else if (encoder != null) {
        encoder.release();
      }
      throw e;
    }
  }

  /**
   * Returns a {@link MediaCodecAdapterWrapper} for a configured and started {@link
   * MediaCodecAdapter} video encoder.
   *
   * @param format The {@link Format} (of the output data) used to determine the underlying {@link
   *     MediaCodec} and its configuration values. {@link Format#sampleMimeType}, {@link
   *     Format#width} and {@link Format#height} must be set to those of the desired output video
   *     format.
   * @param additionalEncoderConfig A map of {@link MediaFormat}'s integer settings, where the keys
   *     are from {@code MediaFormat.KEY_*} constants. Its values will override those in {@code
   *     format}.
   * @return A configured and started encoder wrapper.
   * @throws IOException If the underlying codec cannot be created.
   */
  public static MediaCodecAdapterWrapper createForVideoEncoding(
      Format format, Map<String, Integer> additionalEncoderConfig) throws IOException {
    checkArgument(format.width != Format.NO_VALUE);
    checkArgument(format.height != Format.NO_VALUE);

    @Nullable MediaCodecAdapter adapter = null;
    try {
      MediaFormat mediaFormat =
          MediaFormat.createVideoFormat(
              checkNotNull(format.sampleMimeType), format.width, format.height);
      mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
      mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
      mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
      mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 413_000);

      for (Map.Entry<String, Integer> encoderSetting : additionalEncoderConfig.entrySet()) {
        mediaFormat.setInteger(encoderSetting.getKey(), encoderSetting.getValue());
      }

      adapter =
          new Factory()
              .createAdapter(
                  MediaCodecAdapter.Configuration.createForVideoEncoding(
                      createPlaceholderMediaCodecInfo(), mediaFormat, format));
      return new MediaCodecAdapterWrapper(adapter);
    } catch (Exception e) {
      if (adapter != null) {
        adapter.release();
      }
      throw e;
    }
  }

  private MediaCodecAdapterWrapper(MediaCodecAdapter codec) {
    this.codec = codec;
    outputBufferInfo = new BufferInfo();
    inputBufferIndex = C.INDEX_UNSET;
    outputBufferIndex = C.INDEX_UNSET;
  }

  /** Returns the input {@link Surface}, or null if the input is not a surface. */
  @Nullable
  public Surface getInputSurface() {
    return codec.getInputSurface();
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
   * Queues an input buffer to the decoder. No buffers may be queued after an {@link
   * DecoderInputBuffer#isEndOfStream() end of stream} buffer has been queued.
   */
  public void queueInputBuffer(DecoderInputBuffer inputBuffer) {
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
  }

  @RequiresApi(18)
  public void signalEndOfInputStream() {
    codec.signalEndOfInputStream();
  }

  /** Returns the current output format, if available. */
  @Nullable
  public Format getOutputFormat() {
    // The format is updated when dequeueing a 'special' buffer index, so attempt to dequeue now.
    maybeDequeueOutputBuffer();
    return outputFormat;
  }

  /** Returns the current output {@link ByteBuffer}, if available. */
  @Nullable
  public ByteBuffer getOutputBuffer() {
    return maybeDequeueAndSetOutputBuffer() ? outputBuffer : null;
  }

  /** Returns the {@link BufferInfo} associated with the current output buffer, if available. */
  @Nullable
  public BufferInfo getOutputBufferInfo() {
    return maybeDequeueOutputBuffer() ? outputBufferInfo : null;
  }

  /**
   * Releases the current output buffer.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the previous has been released.
   */
  public void releaseOutputBuffer() {
    releaseOutputBuffer(/* render= */ false);
  }

  /**
   * Releases the current output buffer. If the {@link MediaCodec} was configured with an output
   * surface, setting {@code render} to {@code true} will first send the buffer to the output
   * surface. The surface will release the buffer back to the codec once it is no longer
   * used/displayed.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the previous has been released.
   */
  public void releaseOutputBuffer(boolean render) {
    outputBuffer = null;
    codec.releaseOutputBuffer(outputBufferIndex, render);
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

  /**
   * Tries obtaining an output buffer and sets {@link #outputBuffer} to the obtained output buffer.
   *
   * @return {@code true} if a buffer is successfully obtained, {@code false} otherwise.
   */
  private boolean maybeDequeueAndSetOutputBuffer() {
    if (!maybeDequeueOutputBuffer()) {
      return false;
    }

    outputBuffer = checkNotNull(codec.getOutputBuffer(outputBufferIndex));
    outputBuffer.position(outputBufferInfo.offset);
    outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
    return true;
  }

  /**
   * Returns true if there is already an output buffer pending. Otherwise attempts to dequeue an
   * output buffer and returns whether there is a new output buffer.
   */
  private boolean maybeDequeueOutputBuffer() {
    if (outputBufferIndex >= 0) {
      return true;
    }
    if (outputStreamEnded) {
      return false;
    }

    outputBufferIndex = codec.dequeueOutputBufferIndex(outputBufferInfo);
    if (outputBufferIndex < 0) {
      if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        outputFormat = getFormat(codec.getOutputFormat());
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
    return true;
  }

  private static Format getFormat(MediaFormat mediaFormat) {
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
    String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
    Format.Builder formatBuilder =
        new Format.Builder()
            .setSampleMimeType(mediaFormat.getString(MediaFormat.KEY_MIME))
            .setInitializationData(csdBuffers.build());
    if (MimeTypes.isVideo(mimeType)) {
      formatBuilder
          .setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
          .setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
    } else if (MimeTypes.isAudio(mimeType)) {
      // TODO(internal b/178685617): Only set the PCM encoding for audio/raw, once we have a way to
      // simulate more realistic codec input/output formats in tests.
      formatBuilder
          .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
          .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
          .setPcmEncoding(MEDIA_CODEC_PCM_ENCODING);
    }
    return formatBuilder.build();
  }
}
