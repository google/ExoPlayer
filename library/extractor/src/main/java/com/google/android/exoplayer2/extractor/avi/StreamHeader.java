package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseArray;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;

public class StreamHeader extends ResidentBox {
  public static final int STRH = 's' | ('t' << 8) | ('r' << 16) | ('h' << 24);

  //Audio Stream
  private static final int AUDS = 'a' | ('u' << 8) | ('d' << 16) | ('s' << 24);

  //Videos Stream
  private static final int VIDS = 'v' | ('i' << 8) | ('d' << 16) | ('s' << 24);

  private static final SparseArray<String> STREAM_MAP = new SparseArray<>();

  static {
    STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('2' << 24), MimeTypes.VIDEO_MP4V);
    STREAM_MAP.put('3' | ('V' << 8) | ('I' << 16) | ('D' << 24), MimeTypes.VIDEO_MP4V);
    STREAM_MAP.put('x' | ('v' << 8) | ('i' << 16) | ('d' << 24), MimeTypes.VIDEO_MP4V);
    STREAM_MAP.put('X' | ('V' << 8) | ('I' << 16) | ('D' << 24), MimeTypes.VIDEO_MP4V);

    STREAM_MAP.put('m' | ('j' << 8) | ('p' << 16) | ('g' << 24), MimeTypes.IMAGE_JPEG);
  }

  StreamHeader(int type, int size, ByteBuffer byteBuffer) {
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

  public String getCodec() {
    return STREAM_MAP.get(getFourCC());
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
  //28 - dwStart
  public long getLength() {
    return byteBuffer.getInt(32) & AviUtil.UINT_MASK;
  }
}
