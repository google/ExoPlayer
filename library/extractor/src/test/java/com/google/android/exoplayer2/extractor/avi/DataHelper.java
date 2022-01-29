package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

public class DataHelper {
  static final int FPS = 24;
  static final long VIDEO_US = 1_000_000L / FPS;
  static final int AUDIO_PER_VIDEO = 4;
  static final int VIDEO_SIZE = 4096;
  static final int AUDIO_SIZE = 256;
  static final int AUDIO_ID = 1;
  private static final long AUDIO_US = VIDEO_US / AUDIO_PER_VIDEO;

  //Base path "\ExoPlayer\library\extractor\."
  private static final File RELATIVE_PATH = new File("../../testdata/src/test/assets/extractordumps/avi/");
  public static FakeExtractorInput getInput(final String fileName) throws IOException {
    return new FakeExtractorInput.Builder().setData(getBytes(fileName)).build();
  }

  public static byte[] getBytes(final String fileName) throws IOException {
    final File file = new File(RELATIVE_PATH, fileName);
    try (FileInputStream in = new FileInputStream(file)) {
      final byte[] buffer = new byte[in.available()];
      in.read(buffer);
      return buffer;
    }
  }

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
    final byte[] buffer = getBytes("aac_stream_format.dump");
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return new StreamFormatBox(StreamFormatBox.STRF, buffer.length, byteBuffer);
  }

  public static StreamFormatBox getVideoStreamFormat() throws IOException {
    final byte[] buffer = getBytes("h264_stream_format.dump");
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return new StreamFormatBox(StreamFormatBox.STRF, buffer.length, byteBuffer);
  }

  public static ListBox getVideoStreamList() throws IOException {
    final StreamHeaderBox streamHeaderBox = getVidsStreamHeader();
    final StreamFormatBox streamFormatBox = getVideoStreamFormat();
    final ArrayList<Box> list = new ArrayList<>(2);
    list.add(streamHeaderBox);
    list.add(streamFormatBox);
    return new ListBox((int)(streamHeaderBox.getSize() + streamFormatBox.getSize()),
        AviExtractor.STRL, list);
  }

  public static ListBox getAacStreamList() throws IOException {
    final StreamHeaderBox streamHeaderBox = getAudioStreamHeader();
    final StreamFormatBox streamFormatBox = getAacStreamFormat();
    final ArrayList<Box> list = new ArrayList<>(2);
    list.add(streamHeaderBox);
    list.add(streamFormatBox);
    return new ListBox((int)(streamHeaderBox.getSize() + streamFormatBox.getSize()),
        AviExtractor.STRL, list);
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

  public static AviTrack getVideoAviTrack(int sec) {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    return new AviTrack(0, C.TRACK_TYPE_VIDEO,
        new LinearClock(sec * 1_000_000L, sec * FPS),
        fakeTrackOutput);
  }

  public static AviTrack getAudioAviTrack(int sec) {
    final FakeTrackOutput fakeTrackOutput = new FakeTrackOutput(false);
    return new AviTrack(AUDIO_ID, C.TRACK_TYPE_AUDIO,
        new LinearClock(sec * 1_000_000L, sec * FPS * AUDIO_PER_VIDEO),
        fakeTrackOutput);
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
        new UnboundedIntArray[]{videoArray, audioArray}, 4096);
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
    final int videoFrames = secs * FPS;
    final int videoChunkId = AviTrack.getVideoChunkId(0);
    final int audioChunkId = AviTrack.getAudioChunkId(1);
    int offset = 4;
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
   * @param bufferSize
   * @return
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
}
