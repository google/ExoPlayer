package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

  public static StreamFormatBox getAudioStreamFormat() throws IOException {
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
}
