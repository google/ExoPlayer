package com.google.android.exoplayer.hls;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
* Created by martin on 18/08/14.
*/
public abstract class Packet {
  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;

  public long pts;
  public ByteBuffer data;
  public int type;

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
