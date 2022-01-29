package com.google.android.exoplayer2.extractor.avi;

import java.nio.ByteBuffer;

/**
 * AVISTREAMHEADER
 */
public class StreamHeaderBox extends ResidentBox {
  public static final int STRH = 's' | ('t' << 8) | ('r' << 16) | ('h' << 24);

  //Audio Stream
  static final int AUDS = 'a' | ('u' << 8) | ('d' << 16) | ('s' << 24);

  //Videos Stream
  static final int VIDS = 'v' | ('i' << 8) | ('d' << 16) | ('s' << 24);

  StreamHeaderBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  public boolean isAudio() {
    return getSteamType() == AUDS;
  }

  public boolean isVideo() {
    return getSteamType() == VIDS;
  }

  public float getFrameRate() {
    return getRate() / (float)getScale();
  }

  public long getDurationUs() {
    return getScale() * getLength() * 1_000_000L / getRate();
  }

  public int getSteamType() {
    return byteBuffer.getInt(0);
  }
  //4 - fourCC
  //8 - dwFlags
  //12 - wPriority
  //14 - wLanguage
  public int getInitialFrames() {
    return byteBuffer.getInt(16);
  }
  public int getScale() {
    return byteBuffer.getInt(20);
  }
  public int getRate() {
    return byteBuffer.getInt(24);
  }
  //28 - dwStart - doesn't seem to ever be set
//  public int getStart() {
//    return byteBuffer.getInt(28);
//  }
  public int getLength() {
    return byteBuffer.getInt(32);
  }

  public int getSuggestedBufferSize() {
    return byteBuffer.getInt(36);
  }
  //40 - dwQuality
  //44 - dwSampleSize
//  public int getSampleSize() {
//    return byteBuffer.getInt(44);
//  }

//  public String toString() {
//    return "scale=" + getScale() + " rate=" + getRate() + " length=" + getLength() + " us=" + getDurationUs();
//  }
}
