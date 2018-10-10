/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp.message;


public class InterleavedFrame {
  private final int channel;
  private final byte[] data;

  public InterleavedFrame(int channel, byte[] data) {
    this.channel = channel;
    this.data = data;
  }

  public int getChannel() { return channel; }

  public byte[] getData() { return data; }

  public byte[] getBytes() {
    byte[] bytes = new byte[4 + data.length]; // magic + channel + length (first fourth bytes)

    bytes[0] = (byte) '$';
    bytes[1] = (byte) channel;
    bytes[2] = (byte) ((data.length & 0xFF00) >> 8);
    bytes[3] = (byte) ((data.length & 0x00FF) >> 0);

    System.arraycopy(data, 0, bytes, 4, data.length);
    return bytes;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append('$').append((char)channel).append((short)data.length).append(new String(data));

    return str.toString();
  }
}
