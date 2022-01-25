package com.google.android.exoplayer2.extractor.avi;

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

  public static StreamHeaderBox getVidsStreamHeader() throws IOException {
    final byte[] buffer = getBytes("vids_stream_header.dump");
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return new StreamHeaderBox(StreamHeaderBox.STRH, buffer.length, byteBuffer);
  }

  public static StreamHeaderBox getAudioStreamHeader() throws IOException {
    final byte[] buffer = getBytes("auds_stream_header.dump");
    final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return new StreamHeaderBox(StreamHeaderBox.STRH, buffer.length, byteBuffer);
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
  public static AviSeekMap getAviSeekMap() throws IOException {

    final FakeTrackOutput output = new FakeTrackOutput(false);
    final AviTrack videoTrack = new AviTrack(0,
        DataHelper.getVideoStreamFormat().getVideoFormat(), new LinearClock(100), output);
    final UnboundedIntArray videoArray = new UnboundedIntArray();
    videoArray.add(0);
    videoArray.add(1024);
    final UnboundedIntArray audioArray = new UnboundedIntArray();
    audioArray.add(0);
    audioArray.add(128);
    return new AviSeekMap(videoTrack,
        new UnboundedIntArray[]{videoArray, audioArray}, 24, 0L, 0L);
  }
}
