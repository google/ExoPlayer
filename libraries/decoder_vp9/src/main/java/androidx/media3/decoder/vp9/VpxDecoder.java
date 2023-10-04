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
package androidx.media3.decoder.vp9;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.CryptoException;
import androidx.media3.decoder.CryptoInfo;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;

/** Vpx decoder. */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
@UnstableApi
public final class VpxDecoder
    extends SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, VpxDecoderException> {

  // These constants should match the codes returned from vpxDecode and vpxSecureDecode functions in
  // https://github.com/google/ExoPlayer/blob/release-v2/extensions/vp9/src/main/jni/vpx_jni.cc.
  private static final int NO_ERROR = 0;
  private static final int DECODE_ERROR = -1;
  private static final int DRM_ERROR = -2;

  @Nullable private final CryptoConfig cryptoConfig;
  private final long vpxDecContext;

  @Nullable private ByteBuffer lastSupplementalData;

  private volatile @C.VideoOutputMode int outputMode;

  /**
   * Creates a VP9 decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @param cryptoConfig The {@link CryptoConfig} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @param threads Number of threads libvpx will use to decode.
   * @throws VpxDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public VpxDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      @Nullable CryptoConfig cryptoConfig,
      int threads)
      throws VpxDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!VpxLibrary.isAvailable()) {
      throw new VpxDecoderException("Failed to load decoder native libraries.");
    }
    this.cryptoConfig = cryptoConfig;
    if (cryptoConfig != null && !VpxLibrary.vpxIsSecureDecodeSupported()) {
      throw new VpxDecoderException("Vpx decoder does not support secure decode.");
    }
    vpxDecContext =
        vpxInit(/* disableLoopFilter= */ false, /* enableRowMultiThreadMode= */ false, threads);
    if (vpxDecContext == 0) {
      throw new VpxDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libvpx" + VpxLibrary.getVersion();
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected void releaseOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    // Skipped frames do not acquire a reference on the internal decoder buffer and thus do not
    // require a call to vpxReleaseFrame.
    if (outputMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !outputBuffer.shouldBeSkipped) {
      vpxReleaseFrame(vpxDecContext, outputBuffer);
    }
    super.releaseOutputBuffer(outputBuffer);
  }

  @Override
  protected VpxDecoderException createUnexpectedDecodeException(Throwable error) {
    return new VpxDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected VpxDecoderException decode(
      DecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset && lastSupplementalData != null) {
      // Don't propagate supplemental data across calls to flush the decoder.
      lastSupplementalData.clear();
    }

    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    CryptoInfo cryptoInfo = inputBuffer.cryptoInfo;
    final long result =
        inputBuffer.isEncrypted()
            ? vpxSecureDecode(
                vpxDecContext,
                inputData,
                inputSize,
                cryptoConfig,
                cryptoInfo.mode,
                Assertions.checkNotNull(cryptoInfo.key),
                Assertions.checkNotNull(cryptoInfo.iv),
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData)
            : vpxDecode(vpxDecContext, inputData, inputSize);
    if (result != NO_ERROR) {
      if (result == DRM_ERROR) {
        String message = "Drm error: " + vpxGetErrorMessage(vpxDecContext);
        CryptoException cause = new CryptoException(vpxGetErrorCode(vpxDecContext), message);
        return new VpxDecoderException(message, cause);
      } else {
        return new VpxDecoderException("Decode error: " + vpxGetErrorMessage(vpxDecContext));
      }
    }

    if (inputBuffer.hasSupplementalData()) {
      ByteBuffer supplementalData = Assertions.checkNotNull(inputBuffer.supplementalData);
      int size = supplementalData.remaining();
      if (size > 0) {
        if (lastSupplementalData == null || lastSupplementalData.capacity() < size) {
          lastSupplementalData = ByteBuffer.allocate(size);
        } else {
          lastSupplementalData.clear();
        }
        lastSupplementalData.put(supplementalData);
        lastSupplementalData.flip();
      }
    }

    if (isAtLeastOutputStartTimeUs(inputBuffer.timeUs)) {
      outputBuffer.init(inputBuffer.timeUs, outputMode, lastSupplementalData);
      int getFrameResult = vpxGetFrame(vpxDecContext, outputBuffer);
      if (getFrameResult == 1) {
        outputBuffer.shouldBeSkipped = true;
      } else if (getFrameResult == -1) {
        return new VpxDecoderException("Buffer initialization failed.");
      }
      outputBuffer.format = inputBuffer.format;
    } else {
      outputBuffer.shouldBeSkipped = true;
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    lastSupplementalData = null;
    vpxClose(vpxDecContext);
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode.
   */
  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  /** Renders the outputBuffer to the surface. Used with OUTPUT_MODE_SURFACE_YUV only. */
  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws VpxDecoderException {
    int getFrameResult = vpxRenderFrame(vpxDecContext, surface, outputBuffer);
    if (getFrameResult == -1) {
      throw new VpxDecoderException("Buffer render failed.");
    }
  }

  private native long vpxInit(
      boolean disableLoopFilter, boolean enableRowMultiThreadMode, int threads);

  private native long vpxClose(long context);

  private native long vpxDecode(long context, ByteBuffer encoded, int length);

  private native long vpxSecureDecode(
      long context,
      ByteBuffer encoded,
      int length,
      @Nullable CryptoConfig mediaCrypto,
      int inputMode,
      byte[] key,
      byte[] iv,
      int numSubSamples,
      @Nullable int[] numBytesOfClearData,
      @Nullable int[] numBytesOfEncryptedData);

  private native int vpxGetFrame(long context, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Renders the frame to the surface. Used with OUTPUT_MODE_SURFACE_YUV only. Must only be called
   * if {@link #vpxInit} was called with {@code enableBufferManager = true}.
   */
  private native int vpxRenderFrame(
      long context, Surface surface, VideoDecoderOutputBuffer outputBuffer);

  /**
   * Releases the frame. Used with OUTPUT_MODE_SURFACE_YUV only. Must only be called if {@link
   * #vpxInit} was called with {@code enableBufferManager = true}.
   */
  private native int vpxReleaseFrame(long context, VideoDecoderOutputBuffer outputBuffer);

  private native int vpxGetErrorCode(long context);

  private native String vpxGetErrorMessage(long context);
}
