/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.opus;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.OpusUtil;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.drm.DecryptionException;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.util.List;

/** Opus decoder. */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
public final class OpusDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, OpusDecoderException> {

  private static final int NO_ERROR = 0;
  private static final int DECODE_ERROR = -1;
  private static final int DRM_ERROR = -2;

  public final boolean outputFloat;
  public final int channelCount;

  @Nullable private final ExoMediaCrypto exoMediaCrypto;
  private final int preSkipSamples;
  private final int seekPreRollSamples;
  private final long nativeDecoderContext;

  private int skipSamples;

  /**
   * Creates an Opus decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @param initializationData Codec-specific initialization data. The first element must contain an
   *     opus header. Optionally, the list may contain two additional buffers, which must contain
   *     the encoder delay and seek pre roll values in nanoseconds, encoded as longs.
   * @param exoMediaCrypto The {@link ExoMediaCrypto} object required for decoding encrypted
   *     content. Maybe null and can be ignored if decoder does not handle encrypted content.
   * @param outputFloat Forces the decoder to output float PCM samples when set
   * @throws OpusDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public OpusDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      List<byte[]> initializationData,
      @Nullable ExoMediaCrypto exoMediaCrypto,
      boolean outputFloat)
      throws OpusDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    if (!OpusLibrary.isAvailable()) {
      throw new OpusDecoderException("Failed to load decoder native libraries");
    }
    this.exoMediaCrypto = exoMediaCrypto;
    if (exoMediaCrypto != null && !OpusLibrary.opusIsSecureDecodeSupported()) {
      throw new OpusDecoderException("Opus decoder does not support secure decode");
    }
    int initializationDataSize = initializationData.size();
    if (initializationDataSize != 1 && initializationDataSize != 3) {
      throw new OpusDecoderException("Invalid initialization data size");
    }
    if (initializationDataSize == 3
        && (initializationData.get(1).length != 8 || initializationData.get(2).length != 8)) {
      throw new OpusDecoderException("Invalid pre-skip or seek pre-roll");
    }
    preSkipSamples = OpusUtil.getPreSkipSamples(initializationData);
    seekPreRollSamples = OpusUtil.getSeekPreRollSamples(initializationData);

    byte[] headerBytes = initializationData.get(0);
    if (headerBytes.length < 19) {
      throw new OpusDecoderException("Invalid header length");
    }
    channelCount = OpusUtil.getChannelCount(headerBytes);
    if (channelCount > 8) {
      throw new OpusDecoderException("Invalid channel count: " + channelCount);
    }
    int gain = readSignedLittleEndian16(headerBytes, 16);

    byte[] streamMap = new byte[8];
    int numStreams;
    int numCoupled;
    if (headerBytes[18] == 0) { // Channel mapping
      // If there is no channel mapping, use the defaults.
      if (channelCount > 2) { // Maximum channel count with default layout.
        throw new OpusDecoderException("Invalid header, missing stream map");
      }
      numStreams = 1;
      numCoupled = (channelCount == 2) ? 1 : 0;
      streamMap[0] = 0;
      streamMap[1] = 1;
    } else {
      if (headerBytes.length < 21 + channelCount) {
        throw new OpusDecoderException("Invalid header length");
      }
      // Read the channel mapping.
      numStreams = headerBytes[19] & 0xFF;
      numCoupled = headerBytes[20] & 0xFF;
      System.arraycopy(headerBytes, 21, streamMap, 0, channelCount);
    }
    nativeDecoderContext =
        opusInit(OpusUtil.SAMPLE_RATE, channelCount, numStreams, numCoupled, gain, streamMap);
    if (nativeDecoderContext == 0) {
      throw new OpusDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);

    this.outputFloat = outputFloat;
    if (outputFloat) {
      opusSetFloatOutput();
    }
  }

  @Override
  public String getName() {
    return "libopus" + OpusLibrary.getVersion();
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected OpusDecoderException createUnexpectedDecodeException(Throwable error) {
    return new OpusDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected OpusDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      opusReset(nativeDecoderContext);
      // When seeking to 0, skip number of samples as specified in opus header. When seeking to
      // any other time, skip number of samples as specified by seek preroll.
      skipSamples = (inputBuffer.timeUs == 0) ? preSkipSamples : seekPreRollSamples;
    }
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    CryptoInfo cryptoInfo = inputBuffer.cryptoInfo;
    int result =
        inputBuffer.isEncrypted()
            ? opusSecureDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer,
                OpusUtil.SAMPLE_RATE,
                exoMediaCrypto,
                cryptoInfo.mode,
                Assertions.checkNotNull(cryptoInfo.key),
                Assertions.checkNotNull(cryptoInfo.iv),
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData)
            : opusDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer);
    if (result < 0) {
      if (result == DRM_ERROR) {
        String message = "Drm error: " + opusGetErrorMessage(nativeDecoderContext);
        DecryptionException cause =
            new DecryptionException(opusGetErrorCode(nativeDecoderContext), message);
        return new OpusDecoderException(message, cause);
      } else {
        return new OpusDecoderException("Decode error: " + opusGetErrorMessage(result));
      }
    }

    ByteBuffer outputData = Util.castNonNull(outputBuffer.data);
    outputData.position(0);
    outputData.limit(result);
    if (skipSamples > 0) {
      int bytesPerSample = channelCount * 2;
      int skipBytes = skipSamples * bytesPerSample;
      if (result <= skipBytes) {
        skipSamples -= result / bytesPerSample;
        outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
        outputData.position(result);
      } else {
        skipSamples = 0;
        outputData.position(skipBytes);
      }
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    opusClose(nativeDecoderContext);
  }

  private static int readSignedLittleEndian16(byte[] input, int offset) {
    int value = input[offset] & 0xFF;
    value |= (input[offset + 1] & 0xFF) << 8;
    return (short) value;
  }

  private native long opusInit(
      int sampleRate, int channelCount, int numStreams, int numCoupled, int gain, byte[] streamMap);

  private native int opusDecode(
      long decoder,
      long timeUs,
      ByteBuffer inputBuffer,
      int inputSize,
      SimpleOutputBuffer outputBuffer);

  private native int opusSecureDecode(
      long decoder,
      long timeUs,
      ByteBuffer inputBuffer,
      int inputSize,
      SimpleOutputBuffer outputBuffer,
      int sampleRate,
      @Nullable ExoMediaCrypto mediaCrypto,
      int inputMode,
      byte[] key,
      byte[] iv,
      int numSubSamples,
      @Nullable int[] numBytesOfClearData,
      @Nullable int[] numBytesOfEncryptedData);

  private native void opusClose(long decoder);

  private native void opusReset(long decoder);

  private native int opusGetErrorCode(long decoder);

  private native String opusGetErrorMessage(long decoder);

  private native void opusSetFloatOutput();
}
