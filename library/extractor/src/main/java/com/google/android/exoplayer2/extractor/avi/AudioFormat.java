package com.google.android.exoplayer2.extractor.avi;

import android.util.SparseArray;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;

public class AudioFormat {
  public static final short WAVE_FORMAT_PCM = 1;
  private static final short WAVE_FORMAT_MPEGLAYER3 = 0x55;
  private static final short WAVE_FORMAT_AAC = 0xff;
  private static final short WAVE_FORMAT_DVM = 0x2000; //AC3
  private static final short WAVE_FORMAT_DTS2 = 0x2001; //DTS
  private static final SparseArray<String> FORMAT_MAP = new SparseArray<>();
  static {
    FORMAT_MAP.put(WAVE_FORMAT_PCM, MimeTypes.AUDIO_RAW);
    FORMAT_MAP.put(WAVE_FORMAT_MPEGLAYER3, MimeTypes.AUDIO_MPEG);
    FORMAT_MAP.put(WAVE_FORMAT_AAC, MimeTypes.AUDIO_AAC);
    FORMAT_MAP.put(WAVE_FORMAT_DVM, MimeTypes.AUDIO_AC3);
    FORMAT_MAP.put(WAVE_FORMAT_DTS2, MimeTypes.AUDIO_DTS);
  }

  private ByteBuffer byteBuffer;

  //WAVEFORMATEX
  public AudioFormat(ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  public String getMimeType() {
    return FORMAT_MAP.get(getFormatTag() & 0xffff);
  }

  public short getFormatTag() {
    return byteBuffer.getShort(0);
  }
  public short getChannels() {
    return byteBuffer.getShort(2);
  }
  public int getSamplesPerSecond() {
    return byteBuffer.getInt(4);
  }
  // 8 - nAvgBytesPerSec(uint)
  public int getBlockAlign() {
    return byteBuffer.getShort(12);
  }
  public short getBitsPerSample() {
    return byteBuffer.getShort(14);
  }
  public int getCbSize() {
    return byteBuffer.getShort(16) & 0xffff;
  }
  public byte[] getCodecData() {
    final int size = getCbSize();
    final ByteBuffer temp = byteBuffer.duplicate();
    temp.clear();
    temp.position(18);
    temp.limit(18 + size);
    final byte[] data = new byte[size];
    temp.get(data);
    return data;
  }
  //TODO: Deal with  WAVEFORMATEXTENSIBLE
}
