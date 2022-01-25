package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class VideoFormat implements IStreamFormat {

  static final int XVID = 'X' | ('V' << 8) | ('I' << 16) | ('D' << 24);

  private static final HashMap<Integer, String> STREAM_MAP = new HashMap<>();

  static {
    //Although other types are technically supported, AVI is almost exclusively MP4V and MJPEG
    final String mimeType = MimeTypes.VIDEO_MP4V;
    //final String mimeType = MimeTypes.VIDEO_H263;

    //I've never seen an Android devices that actually supports MP42
    STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('2' << 24), MimeTypes.BASE_TYPE_VIDEO+"/mp42");
    //Samsung seems to support the rare MP43.
    STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('3' << 24), MimeTypes.BASE_TYPE_VIDEO+"/mp43");
    STREAM_MAP.put('H' | ('2' << 8) | ('6' << 16) | ('4' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('a' | ('v' << 8) | ('c' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('A' | ('V' << 8) | ('C' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('3' | ('V' << 8) | ('I' << 16) | ('D' << 24), mimeType);
    STREAM_MAP.put('x' | ('v' << 8) | ('i' << 16) | ('d' << 24), mimeType);
    STREAM_MAP.put(XVID, mimeType);
    STREAM_MAP.put('D' | ('X' << 8) | ('5' << 16) | ('0' << 24), mimeType);
    STREAM_MAP.put('d' | ('i' << 8) | ('v' << 16) | ('x' << 24), mimeType);

    STREAM_MAP.put('M' | ('J' << 8) | ('P' << 16) | ('G' << 24), MimeTypes.VIDEO_MJPEG);
    STREAM_MAP.put('m' | ('j' << 8) | ('p' << 16) | ('g' << 24), MimeTypes.VIDEO_MJPEG);
  }

  private final ByteBuffer byteBuffer;

  public VideoFormat(final ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  //biSize - (uint)

  public int getWidth() {
    return byteBuffer.getInt(4);
  }
  public int getHeight() {
    return byteBuffer.getInt(8);
  }
  // 12 - biPlanes
  // 14 - biBitCount
  public int getCompression() {
    return byteBuffer.getInt(16);
  }

  public String getMimeType() {
    return STREAM_MAP.get(getCompression());
  }

  @Override
  public boolean isAllKeyFrames() {
    return MimeTypes.VIDEO_MJPEG.equals(getMimeType());
  }

  @Override
  public int getTrackType() {
    return C.TRACK_TYPE_VIDEO;
  }
}
