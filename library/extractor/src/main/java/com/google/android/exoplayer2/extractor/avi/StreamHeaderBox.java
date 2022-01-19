package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseArray;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;

/**
 * AVISTREAMHEADER
 */
public class StreamHeaderBox extends ResidentBox {
  public static final int STRH = 's' | ('t' << 8) | ('r' << 16) | ('h' << 24);

  //Audio Stream
  private static final int AUDS = 'a' | ('u' << 8) | ('d' << 16) | ('s' << 24);

  //Videos Stream
  private static final int VIDS = 'v' | ('i' << 8) | ('d' << 16) | ('s' << 24);

  private static final SparseArray<String> STREAM_MAP = new SparseArray<>();

  static {
    //Although other types are technically supported, AVI is almost exclusively MP4V and MJPEG
    final String mimeType = MimeTypes.VIDEO_MP4V;
    //final String mimeType = MimeTypes.VIDEO_H263;

    //Doesn't seem to be supported on Android
    //STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('2' << 24), MimeTypes.VIDEO_MP4);
    STREAM_MAP.put('H' | ('2' << 8) | ('6' << 16) | ('4' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('a' | ('v' << 8) | ('c' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('A' | ('V' << 8) | ('C' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('3' | ('V' << 8) | ('I' << 16) | ('D' << 24), mimeType);
    STREAM_MAP.put('x' | ('v' << 8) | ('i' << 16) | ('d' << 24), mimeType);
    STREAM_MAP.put('X' | ('V' << 8) | ('I' << 16) | ('D' << 24), mimeType);

    STREAM_MAP.put('m' | ('j' << 8) | ('p' << 16) | ('g' << 24), MimeTypes.IMAGE_JPEG);
  }

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
   * How long each sample covers
   * @return
   */
  public long getUsPerSample() {
    return getScale() * 1_000_000L / getRate();
  }

  public String getMimeType() {
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
  public int getStart() {
    return byteBuffer.getInt(28);
  }
  public long getLength() {
    return byteBuffer.getInt(32) & AviExtractor.UINT_MASK;
  }
  //36 - dwSuggestedBufferSize
  //40 - dwQuality
  public int getSampleSize() {
    return byteBuffer.getInt(44);
  }
}
