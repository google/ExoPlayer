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

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

public class DataHelper {
  /* package */ static final int FPS = 24;
  /* package */ static final long VIDEO_US = 1_000_000L / FPS;
  /* package */ static final int AUDIO_PER_VIDEO = 4;
  /* package */ static final int VIDEO_SIZE = 4096;
  /* package */ static final int AUDIO_SIZE = 256;
  /* package */ static final int VIDEO_ID = 0;
  /* package */ static final int AUDIO_ID = 1;
  /* package */ static final int MOVI_OFFSET = 4096;

  public static StreamHeaderBox getStreamHeader(int type, int scale, int rate, int length) {
    final ByteBuffer byteBuffer = AviExtractor.allocate(0x40);
    byteBuffer.putInt(type);
    byteBuffer.putInt(20, scale);
    byteBuffer.putInt(24, rate);
    byteBuffer.putInt(32, length);
    byteBuffer.putInt(36, (type == StreamHeaderBox.VIDS ? 128 : 16) * 1024); //Suggested buffer size
    return new StreamHeaderBox(StreamHeaderBox.STRH, 0x40, byteBuffer);
  }

  public static StreamHeaderBox getVidsStreamHeader() {
    return getStreamHeader(StreamHeaderBox.VIDS, 1001, 24000, 9 * FPS);
  }

  public static StreamHeaderBox getAudioStreamHeader() {
    return getStreamHeader(StreamHeaderBox.AUDS, 1, 44100, 9 * FPS);
  }

  public static StreamFormatBox getAacStreamFormat() throws IOException {
    final Context context = ApplicationProvider.getApplicationContext();
    final byte[] buffer = TestUtil.getByteArray(context,"extractordumps/avi/aac_stream_format.dump");
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return new StreamFormatBox(StreamFormatBox.STRF, buffer.length, byteBuffer);
  }

  public static StreamFormatBox getVideoStreamFormat() {
    final ByteBuffer byteBuffer = AviExtractor.allocate(40);
    final VideoFormat videoFormat = new VideoFormat(byteBuffer);
    videoFormat.setWidth(720);
    videoFormat.setHeight(480);
    videoFormat.setCompression(VideoFormat.XVID);
    return new StreamFormatBox(StreamFormatBox.STRF, byteBuffer.capacity(), byteBuffer);
  }

  public static ListBox getVideoStreamList() {
    final StreamHeaderBox streamHeaderBox = getVidsStreamHeader();
    final StreamFormatBox streamFormatBox = getVideoStreamFormat();
    final ArrayList<Box> list = new ArrayList<>(2);
    list.add(streamHeaderBox);
    list.add(streamFormatBox);
    return new ListBox(streamHeaderBox.getSize() + streamFormatBox.getSize(),
        ListBox.TYPE_STRL, list);
  }

  public static ListBox getAacStreamList() throws IOException {
    final StreamHeaderBox streamHeaderBox = getAudioStreamHeader();
    final StreamFormatBox streamFormatBox = getAacStreamFormat();
    final ArrayList<Box> list = new ArrayList<>(2);
    list.add(streamHeaderBox);
    list.add(streamFormatBox);
    return new ListBox((int)(streamHeaderBox.getSize() + streamFormatBox.getSize()),
        ListBox.TYPE_STRL, list);
  }

  public static StreamNameBox getStreamNameBox(final String name) {
    byte[] bytes = name.getBytes();
    bytes = Arrays.copyOf(bytes, bytes.length + 1);
    return new StreamNameBox(StreamNameBox.STRN, bytes.length, ByteBuffer.wrap(bytes));
  }

  public static ByteBuffer appendNal(final ByteBuffer byteBuffer, byte nalType) {
    byteBuffer.put((byte)0);
    byteBuffer.put((byte)0);
    byteBuffer.put((byte) 1);
    byteBuffer.put(nalType);
    return byteBuffer;
  }

  public static ChunkHandler getVideoChunkHandler(int sec) {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    return new ChunkHandler(0, ChunkHandler.TYPE_VIDEO, fakeTrackOutput,
        new ChunkClock(sec * 1_000_000L, sec * FPS));
  }

  public static ChunkHandler getAudioChunkHandler(int sec) {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    return new ChunkHandler(AUDIO_ID, ChunkHandler.TYPE_AUDIO, fakeTrackOutput,
        new ChunkClock(sec * 1_000_000L, sec * FPS * AUDIO_PER_VIDEO));
  }

  public static AviSeekMap getAviSeekMap() {
    final int[] keyFrameOffsetsDiv2= {4, 1024};
    final UnboundedIntArray videoArray = new UnboundedIntArray();
    videoArray.add(0);
    videoArray.add(4);
    final UnboundedIntArray audioArray = new UnboundedIntArray();
    audioArray.add(0);
    audioArray.add(128);
    return new AviSeekMap(0, 100L, 8, keyFrameOffsetsDiv2,
        new UnboundedIntArray[]{videoArray, audioArray}, MOVI_OFFSET);
  }

  private static void putIndex(final ByteBuffer byteBuffer, int chunkId, int flags, int offset,
      int size) {
    byteBuffer.putInt(chunkId);
    byteBuffer.putInt(flags);
    byteBuffer.putInt(offset);
    byteBuffer.putInt(size);
  }

  /**
   *
   * @param secs Number of seconds
   * @param keyFrameRate Key frame rate 1= every frame, 2=every other, ...
   */
  public static ByteBuffer getIndex(final int secs, final int keyFrameRate) {
    return getIndex(secs, keyFrameRate, 4);
  }
    /**
     *
     * @param secs Number of seconds
     * @param keyFrameRate Key frame rate 1= every frame, 2=every other, ...
     */
  public static ByteBuffer getIndex(final int secs, final int keyFrameRate, int offset) {
    final int videoFrames = secs * FPS;
    final int videoChunkId = ChunkHandler.TYPE_VIDEO | ChunkHandler.getChunkIdLower(0);
    final int audioChunkId = ChunkHandler.TYPE_AUDIO | ChunkHandler.getChunkIdLower(1);
    final ByteBuffer byteBuffer = AviExtractor.allocate((videoFrames + videoFrames*AUDIO_PER_VIDEO) * 16);

    for (int v=0;v<videoFrames;v++) {
      putIndex(byteBuffer, videoChunkId, (v % keyFrameRate == 0) ? AviExtractor.AVIIF_KEYFRAME : 0,
          offset, VIDEO_SIZE);
      offset += VIDEO_SIZE;
      for (int a=0;a<AUDIO_PER_VIDEO;a++) {
        putIndex(byteBuffer, audioChunkId,AviExtractor.AVIIF_KEYFRAME, offset, AUDIO_SIZE);
        offset += AUDIO_SIZE;
      }
    }
    return byteBuffer;
  }

  /**
   * Get the RIFF header up to AVI Header
   */
  public static ByteBuffer getRiffHeader(int bufferSize, int headerListSize) {
    ByteBuffer byteBuffer = AviExtractor.allocate(bufferSize);
    byteBuffer.putInt(AviExtractor.RIFF);
    byteBuffer.putInt(128);
    byteBuffer.putInt(AviExtractor.AVI_);
    byteBuffer.putInt(ListBox.LIST);
    byteBuffer.putInt(headerListSize);
    byteBuffer.putInt(ListBox.TYPE_HDRL);
    byteBuffer.putInt(AviHeaderBox.AVIH);
    return byteBuffer;
  }

  public static ByteBuffer createAviHeader() {
    final ByteBuffer byteBuffer = ByteBuffer.allocate(AviHeaderBox.LEN);
    byteBuffer.putInt((int)VIDEO_US);
    byteBuffer.putLong(0); //skip 4+4
    byteBuffer.putInt(AviHeaderBox.AVIF_HASINDEX);
    byteBuffer.putInt(FPS * 5); //5 seconds
    byteBuffer.putInt(24, 2); // Number of streams
    byteBuffer.clear();
    return byteBuffer;
  }

  public static AviHeaderBox createAviHeaderBox() {
    final ByteBuffer byteBuffer = createAviHeader();
    return new AviHeaderBox(AviHeaderBox.AVIH, byteBuffer.capacity(), byteBuffer);
  }
}
