package com.google.android.exoplayer2.audio;


import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that converts audio data to {@link C#ENCODING_PCM_16BIT}.
 */
/* package */ final class FloatResamplingAudioProcessor implements AudioProcessor {

  private int sampleRateHz;
  private static final double PCM_INT32_FLOAT = 1.0 / 0x7fffffff;

  private int channelCount;
  @C.PcmEncoding
  private int sourceEncoding;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  /**
   * Creates a new audio processor that converts audio data to {@link C#ENCODING_PCM_16BIT}.
   */
  public FloatResamplingAudioProcessor() {
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    sourceEncoding = C.ENCODING_INVALID;
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
   throws AudioProcessor.UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_24BIT && encoding != C.ENCODING_PCM_32BIT) {
      throw new AudioProcessor.UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount
     && this.sourceEncoding == encoding) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    this.sourceEncoding = encoding;

    return true;
  }

  @Override
  public boolean isActive() {
    return sourceEncoding == C.ENCODING_PCM_24BIT || sourceEncoding == C.ENCODING_PCM_32BIT;
  }

  @Override
  public int getOutputChannelCount() { return channelCount; }

  @Override
  public int getOutputEncoding() { return C.ENCODING_PCM_FLOAT; }

  @Override
  public int getOutputSampleRateHz() {
    return sampleRateHz;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int offset = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - offset;

    int resampledSize;
    switch (sourceEncoding) {
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 4;
        break;
      case C.ENCODING_PCM_32BIT:
        resampledSize = size;
        break;
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    if (buffer.capacity() < resampledSize) {
      buffer = ByteBuffer.allocateDirect(resampledSize).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }

    // Samples are little endian.
    switch (sourceEncoding) {
      case C.ENCODING_PCM_24BIT:
        // 24->32 bit resampling.
        for (int i = offset; i < limit; i += 3) {
          int val = (inputBuffer.get(i) << 8) & 0x0000ff00 | (inputBuffer.get(i + 1) << 16) & 0x00ff0000 |
           (inputBuffer.get(i + 2) << 24) & 0xff000000;
          writePcm32bitFloat(val, buffer);
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32->32 bit conversion.
        for (int i = offset; i < limit; i += 4) {
          int val = inputBuffer.get(i) & 0x000000ff | (inputBuffer.get(i) << 8) & 0x0000ff00 |
           (inputBuffer.get(i + 1) << 16) & 0x00ff0000 | (inputBuffer.get(i + 2) << 24) & 0xff000000;
          writePcm32bitFloat(val, buffer);
        }
        break;
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
    outputBuffer = buffer;
  }

  @Override
  public void queueEndOfStream() {
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
  }

  @Override
  public void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    sourceEncoding = C.ENCODING_INVALID;
  }

  /**
   * Converts the provided value into 32-bit float PCM and writes to buffer.
   *
   * @param val 32-bit int value to convert to 32-bit float [-1.0, 1.0]
   * @param buffer The output buffer.
   */
  private static void writePcm32bitFloat(int val, ByteBuffer buffer) {
    float convVal = (float) (PCM_INT32_FLOAT * val);
    int bits = Float.floatToIntBits(convVal);
    if (bits == 0x7fc00000)
      bits = Float.floatToIntBits((float) 0.0);
    buffer.put((byte) (bits & 0xff));
    buffer.put((byte) ((bits >> 8) & 0xff));
    buffer.put((byte) ((bits >> 16) & 0xff));
    buffer.put((byte) ((bits >> 24) & 0xff));
  }

}