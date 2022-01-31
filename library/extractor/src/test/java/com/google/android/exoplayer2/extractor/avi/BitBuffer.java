package com.google.android.exoplayer2.extractor.avi;

import java.nio.BufferOverflowException;

public class BitBuffer {
  private long work;
  int bits;

  public void push(boolean b) {
    grow(1);
    if (b) {
      work |= 1L;
    }
  }

  void grow(int bits) {
    if (this.bits + bits > 64) {
      throw new BufferOverflowException();
    }
    this.bits += bits;
    work <<= bits;
  }

  public void push(int bits, int value) {
    int mask = (1 << bits) - 1;
    if ((value & mask) != value) {
      throw new IllegalArgumentException("Expected only " + bits + " bits, got " + value);
    }
    grow(bits);
    work |= (value & 0xffffffffL);
  }

  public void pushExpGolomb(final int i) {
    if (i == 0) {
      push(true);
    }
    int v = i + 1;
    int zeroBits = 0;
    while ((v >>>=1) > 1) {
      zeroBits++;
    }
    final int bits = zeroBits * 2 + 1;
    push(bits, i + 1);
  }

  public byte[] getBytes() {
    //Byte align
    grow(8 - bits % 8);
    final int count = bits / 8;
    final byte[] bytes = new byte[count];
    for (int i=count -1; i >= 0;i--) {
      bytes[i] = (byte)(work & 0xff);
      work >>=8;
    }
    work = 0L;
    bits = 0;
    return bytes;
  }
}
