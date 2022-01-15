package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.NonNull;
import java.nio.ByteBuffer;

public class StreamFormat extends ResidentBox {
  public static final int STRF = 's' | ('t' << 8) | ('r' << 16) | ('f' << 24);

  StreamFormat(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  @NonNull
  public VideoFormat getVideoFormat() {
    return new VideoFormat(byteBuffer);
  }

  @NonNull
  public AudioFormat getAudioFormat() {
    return new AudioFormat(byteBuffer);
  }
}
