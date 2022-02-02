/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
