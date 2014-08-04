package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.HLSExtractor;
import com.google.android.exoplayer.parser.aac.AACExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import java.io.InputStream;

public class TSExtractorNative extends HLSExtractor {

  private DataSource dataSource;
  MediaFormat audioMediaFormat;

  static boolean loaded;

  public TSExtractorNative(DataSource dataSource) throws UnsatisfiedLinkError
  {
    if (!loaded) {
      System.loadLibrary("TSExtractorNative");
    }

    // needs to be done before the nativeInit()
    this.dataSource = dataSource;
    nativeInit();
  }

  @Override
  public Sample read() throws ParserException {

    return nativeRead();
  }

  public void release() {
    nativeRelease();
  }

  // stores a pointer to the native state
  private long nativeHandle;

  private native void nativeInit();
  private native Sample nativeRead();
  private native int nativeGetSampleRateIndex();
  private native int nativeGetChannelConfigIndex();
  private native boolean nativeIsReadFinished();
  public native void nativeRelease();
}
