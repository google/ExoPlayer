package com.google.android.exoplayer.ext.libmpg123;

import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.extensions.Buffer;
import com.google.android.exoplayer.util.extensions.InputBuffer;
import com.google.android.exoplayer.util.extensions.SimpleDecoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;



/**
 * JNI wrapper for the libopus Opus decoder.
 */
/* package */ final class Mpg123Decoder extends
    SimpleDecoder<InputBuffer, Mpg123OutputBuffer, Mpg123DecoderException> {

  /**
   * Whether the underlying libopus library is available.
   */
  public static final boolean IS_AVAILABLE;
  private int skipSamples;
  static private final int NUM_CHANNELS=2;
  static {
    boolean isAvailable;
    try {
      System.loadLibrary("mpg123");
      System.loadLibrary("mpg123JNI");
      isAvailable = true;
    } catch (UnsatisfiedLinkError exception) {
      isAvailable = false;
    }
    IS_AVAILABLE = isAvailable;
  }

  private static final int OUTPUT_BUFFER_SIZE = 4608*32;
  int[] info;
  private int decoderOk;

  public Mpg123Decoder(int numInputBuffers, int numOutputBuffers) throws Mpg123DecoderException {
    super(new InputBuffer[numInputBuffers], new Mpg123OutputBuffer[numOutputBuffers]);
    info = new int[3];
    info[0] = info[1] = info[2] = 0;
    init();
    decoderOk = newDecoder();
//    try {
//      outputStream = Context.openFileOutput(filename, Context.MODE_PRIVATE);
//      outputStream.write(string.getBytes());
//      outputStream.close();
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
  }

  @Override
  public InputBuffer createInputBuffer() {
    return new InputBuffer();
  }

  @Override
  public Mpg123OutputBuffer createOutputBuffer() {
    return new Mpg123OutputBuffer(this);
  }

  @Override
  protected void releaseOutputBuffer(Mpg123OutputBuffer buffer) {
    super.releaseOutputBuffer(buffer);
  }

  @Override
  public Mpg123DecoderException decode(InputBuffer inputBuffer, Mpg123OutputBuffer outputBuffer,
                                       boolean reset) {
    if (reset) {
      outputBuffer.reset();
    }
    if (inputBuffer.getFlag(Buffer.FLAG_END_OF_STREAM)) {
      outputBuffer.setFlag(Buffer.FLAG_END_OF_STREAM);
      return null;
    }
    if (inputBuffer.getFlag(Buffer.FLAG_DECODE_ONLY)) {
      outputBuffer.setFlag(Buffer.FLAG_DECODE_ONLY);
    }
    SampleHolder sampleHolder = inputBuffer.sampleHolder;
    outputBuffer.timestampUs = sampleHolder.timeUs;
    sampleHolder.data.position(sampleHolder.data.position() - sampleHolder.size);
    int requiredOutputBufferSize = OUTPUT_BUFFER_SIZE;
    outputBuffer.init(requiredOutputBufferSize);
    int result = mpgdecode(sampleHolder.data, sampleHolder.size,
        outputBuffer.data, outputBuffer.data.capacity());
    if (result < 0) {
//      return new Mpg123DecoderException("Decode error: " + mpg123GetErrorMessage(result));
      return new Mpg123DecoderException("Decode error: mpg123decoder");
    }
    outputBuffer.data.position(0);
    outputBuffer.data.limit(result);
    return null;
  }

  @Override
   public void release() {
    close();
    exit();
  }
  private native void init();
  private native int newDecoder();
  private native int mpgdecode(ByteBuffer inBuffer, int inSize, ByteBuffer outBuffer, int outSize);
  private native int close();
  private native int exit();

}
