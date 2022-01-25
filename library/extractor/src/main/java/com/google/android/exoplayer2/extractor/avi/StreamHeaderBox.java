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

  /**
   * @return sample duration in us
   */
  public long getUsPerSample() {
    return getScale() * 1_000_000L / getRate();
  }

  public int getSteamType() {
    return byteBuffer.getInt(0);
  }
  /**
   * Only meaningful for video
   * @return FourCC
   */
  public int getFourCC() {
    return byteBuffer.getInt(4);
  }
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
  // 28 - dwStart
//  public int getStart() {
//    return byteBuffer.getInt(28);
//  }
  public long getLength() {
    return byteBuffer.getInt(32) & AviExtractor.UINT_MASK;
  }

  public int getSuggestedBufferSize() {
    return byteBuffer.getInt(36);
  }
  //40 - dwQuality
  //44 - dwSampleSize
//  public int getSampleSize() {
//    return byteBuffer.getInt(44);
//  }
}
