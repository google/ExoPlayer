package com.google.android.exoplayer.hls;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
* Created by martin on 18/08/14.
*/
public class Packet {
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;
  static final ArrayList<LinkedList<Packet>> recycledSampleList;

  static {
    recycledSampleList = new ArrayList<LinkedList<Packet>>(2);
    recycledSampleList.add(new LinkedList<Packet>());
    recycledSampleList.add(new LinkedList<Packet>());
  }

  public Packet(int type) {
    this.type = type;
    this.data = ByteBuffer.allocateDirect(2 * 1024);
  }
  public long pts;
  public ByteBuffer data;
  public int type;

  static public synchronized Packet getPacket(int type) {
    LinkedList<Packet> list = recycledSampleList.get(type);
    if (list.size() > 0) {
      Packet s = list.removeFirst();
      s.data.position(0);
      s.data.limit(s.data.capacity());
      return s;
    } else {
      return new Packet(type);
    }
  }

  static public synchronized void releaseSample(Packet s) {
    LinkedList<Packet> list = recycledSampleList.get(s.type);
    list.add(s);
  }

  static public synchronized void resizeSample(Packet s, int newSize) {
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
}
