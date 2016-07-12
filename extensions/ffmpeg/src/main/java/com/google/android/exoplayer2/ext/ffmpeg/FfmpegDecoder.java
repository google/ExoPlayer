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
package com.google.android.exoplayer2.ext.ffmpeg;

import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * JNI wrapper for FFmpeg. Only audio decoding is supported.
 */
/* package */ final class FfmpegDecoder extends
    SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, FfmpegDecoderException> {

  private static final String TAG = "FfmpegDecoder";

  /**
   * Whether the underlying FFmpeg library is available.
   */
  public static final boolean IS_AVAILABLE;
  static {
    boolean isAvailable;
    try {
      System.loadLibrary("avutil");
      System.loadLibrary("avresample");
      System.loadLibrary("avcodec");
      System.loadLibrary("ffmpeg");
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      isAvailable = false;
    }
    IS_AVAILABLE = isAvailable;
  }

  /**
   * Returns whether this decoder can decode samples in the specified MIME type.
   */
  public static boolean supportsFormat(String mimeType) {
    String codecName = getCodecName(mimeType);
    return codecName != null && nativeHasDecoder(codecName);
  }

  // Space for 64 ms of 6 channel 48 kHz 16-bit PCM audio.
  private static final int OUTPUT_BUFFER_SIZE = 1536 * 6 * 2 * 2;

  private final String codecName;
  private final byte[] extraData;

  private long nativeContext; // May be reassigned on resetting the codec.
  private boolean hasOutputFormat;
  private volatile int channelCount;
  private volatile int sampleRate;

  public FfmpegDecoder(int numInputBuffers, int numOutputBuffers, int initialInputBufferSize,
      String mimeType, List<byte[]> initializationData) throws FfmpegDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    codecName = getCodecName(mimeType);
    extraData = getExtraData(mimeType, initializationData);
    nativeContext = nativeInitialize(codecName, extraData);
    if (nativeContext == 0) {
      throw new FfmpegDecoderException("Initialization failed.");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "ffmpeg" + nativeGetFfmpegVersion() + "-" + codecName;
  }

  @Override
  public DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  public SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this);
  }

  @Override
  public FfmpegDecoderException decode(DecoderInputBuffer inputBuffer,
      SimpleOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      nativeContext = nativeReset(nativeContext, extraData);
      if (nativeContext == 0) {
        return new FfmpegDecoderException("Error resetting (see logcat).");
      }
    }
    ByteBuffer inputData = inputBuffer.data;
    int inputSize = inputData.limit();
    ByteBuffer outputData = outputBuffer.init(inputBuffer.timeUs, OUTPUT_BUFFER_SIZE);
    int result = nativeDecode(nativeContext, inputData, inputSize, outputData, OUTPUT_BUFFER_SIZE);
    if (result < 0) {
      return new FfmpegDecoderException("Error decoding (see logcat). Code: " + result);
    }
    if (!hasOutputFormat) {
      channelCount = nativeGetChannelCount(nativeContext);
      sampleRate = nativeGetSampleRate(nativeContext);
      hasOutputFormat = true;
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    return null;
  }

  @Override
  public void release() {
    super.release();
    nativeRelease(nativeContext);
    nativeContext = 0;
  }

  /**
   * Returns the channel count of output audio. May only be called after {@link #decode}.
   */
  public int getChannelCount() {
    return channelCount;
  }

  /**
   * Returns the sample rate of output audio. May only be called after {@link #decode}.
   */
  public int getSampleRate() {
    return sampleRate;
  }

  /**
   * Returns FFmpeg-compatible codec-specific initialization data ("extra data"), or {@code null} if
   * not required.
   */
  private static byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
      case MimeTypes.AUDIO_OPUS:
        return initializationData.get(0);
      case MimeTypes.AUDIO_VORBIS:
        byte[] header0 = initializationData.get(0);
        byte[] header1 = initializationData.get(1);
        byte[] extraData = new byte[header0.length + header1.length + 6];
        extraData[0] = (byte) (header0.length >> 8);
        extraData[1] = (byte) (header0.length & 0xFF);
        System.arraycopy(header0, 0, extraData, 2, header0.length);
        extraData[header0.length + 2] = 0;
        extraData[header0.length + 3] = 0;
        extraData[header0.length + 4] =  (byte) (header1.length >> 8);
        extraData[header0.length + 5] = (byte) (header1.length & 0xFF);
        System.arraycopy(header1, 0, extraData, header0.length + 6, header1.length);
        return extraData;
      default:
        // Other codecs do not require extra data.
        return null;
    }
  }

  /**
   * Returns the name of the FFmpeg decoder that could be used to decode {@code mimeType}. The codec
   * can only be used if {@link #nativeHasDecoder(String)} returns true for the returned codec name.
   */
  private static String getCodecName(String mimeType) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        return "aac";
      case MimeTypes.AUDIO_MPEG:
      case MimeTypes.AUDIO_MPEG_L1:
      case MimeTypes.AUDIO_MPEG_L2:
        return "mp3";
      case MimeTypes.AUDIO_AC3:
        return "ac3";
      case MimeTypes.AUDIO_E_AC3:
        return "eac3";
      case MimeTypes.AUDIO_TRUEHD:
        return "truehd";
      case MimeTypes.AUDIO_DTS:
      case MimeTypes.AUDIO_DTS_HD:
        return "dca";
      case MimeTypes.AUDIO_VORBIS:
        return "vorbis";
      case MimeTypes.AUDIO_OPUS:
        return "opus";
      case MimeTypes.AUDIO_AMR_NB:
        return "amrnb";
      case MimeTypes.AUDIO_AMR_WB:
        return "amrwb";
      case MimeTypes.AUDIO_FLAC:
        return "flac";
      default:
        return null;
    }
  }

  private static native String nativeGetFfmpegVersion();
  private static native boolean nativeHasDecoder(String codecName);
  private native long nativeInitialize(String codecName, byte[] extraData);
  private native int nativeDecode(long context, ByteBuffer inputData, int inputSize,
      ByteBuffer outputData, int outputSize);
  private native int nativeGetChannelCount(long context);
  private native int nativeGetSampleRate(long context);
  private native long nativeReset(long context, byte[] extraData);
  private native void nativeRelease(long context);

}
