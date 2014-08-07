package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.hls.HLSExtractor;
import com.google.android.exoplayer.upstream.DataSource;

public class TSExtractorNative extends HLSExtractor {

  private DataSource dataSource;
  static boolean loaded;

  public TSExtractorNative(DataSource dataSource) throws UnsatisfiedLinkError
  {
    if (!loaded) {
      System.loadLibrary("TSExtractorNative");
      loaded = true;
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

  public int getStreamType(int type) {
    return STREAM_TYPE_NONE;
  }

  // stores a pointer to the native state
  private long nativeHandle;

  private native void nativeInit();
  private native Sample nativeRead();
  public native void nativeRelease();
}
