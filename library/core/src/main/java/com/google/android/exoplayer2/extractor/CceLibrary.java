package com.google.android.exoplayer2.extractor;


import android.content.Context;
import android.util.Log;

import com.google.android.exoplayer2.text.ssa.SsaSubtitle;
import com.google.android.exoplayer2.util.ParsableByteArray;


public class CceLibrary {

  public static String TAG = "TS_CCELIBRARY";

  public static CceLibrary cceLib = new CceLibrary();

  static {
    try {
      System.loadLibrary("ccejni");
    }
    catch(UnsatisfiedLinkError e) {
    }
  }

  private native void jniInitCcextractor(String logFolderPath);
  private native void jniRunCcextractor(long length);
  private native void jniWriteSharedBytes(byte[] bytes, int length);
  // private native byte[] jniReadSubtitles();
  private native void jniEndCcextractor();
  private native void jniFinishedHeader();

  private SsaSubtitle subtitle;

  private String logFolderPath;

  public CceLibrary() {
    logFolderPath = null;
  }

  /** Returns whether the underlying library is available, loading it if necessary. */

  public void init() {
    jniInitCcextractor(null/*logFolderPath + "/exo_jni.log"*/);
  }

  public void write(byte[] bytes, int length) {
    jniWriteSharedBytes(bytes, length);
  }

  public void run(long inputLength) {
    jniRunCcextractor(inputLength);
  }

  public void finish() {jniEndCcextractor();}

  public void finishedHeader() {jniFinishedHeader();}

  public void setSubtitle(SsaSubtitle subtitle) {
    this.subtitle = subtitle;
  }

  public void appendSubtitle(byte[] data) {
    Log.i(TAG, "Append subtitles: " + new String(data));

    this.subtitle.addSubtitles(new ParsableByteArray(data));
  }

}
