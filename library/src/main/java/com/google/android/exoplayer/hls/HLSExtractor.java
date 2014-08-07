package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

public abstract class HLSExtractor {
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;

  public static final int STREAM_TYPE_NONE = -1;
  public static final int STREAM_TYPE_AAC_ADTS = 0xf;
  public static final int STREAM_TYPE_H264 = 0x1b;
  public static final int STREAM_TYPE_MPEG_AUDIO = 0x3;

  public static class Sample {
    public Sample(int type) {
      this.type = type;
      this.data = ByteBuffer.allocateDirect(2*1024);
    }
    public long timeUs;
    public ByteBuffer data;
    public int type;
  }

  private static final ArrayList<LinkedList<Sample>> recycledSampleList;

  static {
    recycledSampleList = new ArrayList<LinkedList<Sample>>(2);
    recycledSampleList.add(new LinkedList<Sample>());
    recycledSampleList.add(new LinkedList<Sample>());
  }

  static public synchronized Sample getSample(int type) {
    LinkedList<Sample> list = recycledSampleList.get(type);
    if (list.size() > 0) {
      Sample s = list.removeFirst();
      s.data.position(0);
      s.data.limit(s.data.capacity());
      return s;
    } else {
      return new Sample(type);
    }
  }

  static public synchronized void releaseSample(Sample s) {
    LinkedList<Sample> list = recycledSampleList.get(s.type);
    list.add(s);
  }

  static public synchronized void resizeSample(Sample s, int newSize) {
    ByteBuffer newData = ByteBuffer.allocateDirect(newSize);
    s.data.limit(s.data.position());
    s.data.position(0);
    newData.put(s.data);
    s.data = newData;
  }

  static public class UnsignedByteArray {
    byte[] array;
    public UnsignedByteArray(int length) {
      array = new byte[length];
    }

    public UnsignedByteArray(byte[] array) {
      this.array = array;
    }

    public void resize(int newLength) {
      byte [] newArray = new byte[newLength];
      System.arraycopy(array, 0, newArray, 0, array.length);
      array = newArray;
    }

    public int length() {
      return array.length;
    }

    public int get(int index) {
      return (int)array[index] & 0xff;
    }
    public int getShort(int index) {
      return get(index) << 8 | get(index + 1);
    }
    public byte[] array() {
      return array;
    }

    public long getLong(int offset) {
      long result = 0;
      for (int i = 0; i < 8; i++) {
        result <<= 8;
        result |= get(offset + i);
      }
      return result;
    }
  }

  /*
   * return null if end of stream
   */
  abstract public Sample read()
          throws ParserException;

  abstract public int getStreamType(int type);

  public void release() {};
}