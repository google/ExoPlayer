/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.test.utils;

import static java.lang.Math.min;

import android.os.Environment;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A sink for audio buffers that writes output audio as .ogg files with a given path prefix. When
 * new audio data is handled after flushing the audio packetizer, a counter is incremented and its
 * value is appended to the output file name.
 *
 * <p>Note: if writing to external storage it's necessary to grant the {@code
 * WRITE_EXTERNAL_STORAGE} permission.
 */
@UnstableApi
public final class OggFileAudioBufferSink extends ForwardingAudioSink {

  /** Opus streams are always 48000 Hz. */
  public static final int SAMPLE_RATE = 48_000;

  private static final String TAG = "OggFileAudioBufferSink";
  private static final int OGG_ID_HEADER_LENGTH = 47;
  private static final int OGG_COMMENT_HEADER_LENGTH = 52;

  private final byte[] scratchBuffer;
  private final ByteBuffer scratchByteBuffer;
  private final String outputFileNamePrefix;

  @Nullable private RandomAccessFile randomAccessFile;
  private int counter;

  /**
   * Creates an instance.
   *
   * @param audioSink The base audioSink calls are forwarded to.
   * @param outputFileNamePrefix The prefix for output files.
   */
  public OggFileAudioBufferSink(AudioSink audioSink, String outputFileNamePrefix) {
    super(audioSink);
    this.outputFileNamePrefix = outputFileNamePrefix;
    counter = 0;
    scratchBuffer = new byte[1024];
    scratchByteBuffer = ByteBuffer.wrap(scratchBuffer).order(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public void flush() {
    super.flush();
    try {
      resetInternal();
    } catch (IOException e) {
      Log.e(TAG, "Error resetting", e);
    }
  }

  @Override
  public void reset() {
    super.reset();
    try {
      resetInternal();
    } catch (IOException e) {
      Log.e(TAG, "Error resetting", e);
    }
  }

  @Override
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException {
    handleBuffer(buffer);
    return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount);
  }

  private void handleBuffer(ByteBuffer buffer) {
    try {
      maybePrepareFile();
      writeBuffer(buffer);
    } catch (IOException e) {
      Log.e(TAG, "Error writing data", e);
    }
  }

  private void maybePrepareFile() throws IOException {
    if (randomAccessFile != null) {
      return;
    }
    RandomAccessFile randomAccessFile = new RandomAccessFile(getNextOutputFileName(), "rw");
    scratchByteBuffer.clear();
    writeIdHeaderPacket();
    writeCommentHeaderPacket();
    randomAccessFile.write(scratchBuffer, 0, scratchByteBuffer.position());
    this.randomAccessFile = randomAccessFile;
  }

  private void writeOggPacketHeader(int pageSequenceNumber, boolean isIdHeaderPacket) {
    // Capture Pattern for Page [OggS]
    scratchByteBuffer.put((byte) 'O');
    scratchByteBuffer.put((byte) 'g');
    scratchByteBuffer.put((byte) 'g');
    scratchByteBuffer.put((byte) 'S');

    // StreamStructure Version
    scratchByteBuffer.put((byte) 0);

    // header-type
    scratchByteBuffer.put(isIdHeaderPacket ? (byte) 0x02 : (byte) 0x00);

    // granule_position
    scratchByteBuffer.putLong((long) 0);

    // bitstream_serial_number
    scratchByteBuffer.putInt(0);

    // page_sequence_number
    scratchByteBuffer.putInt(pageSequenceNumber);

    // CRC_checksum
    scratchByteBuffer.putInt(0);

    // number_page_segments
    scratchByteBuffer.put((byte) 1);
  }

  private void writeIdHeaderPacket() {
    // Id Header
    writeOggPacketHeader(/* pageSequenceNumber= */ 0, /* isIdHeaderPacket= */ true);

    // Payload Size = 19
    scratchByteBuffer.put((byte) 19);

    // OggOpus Id Header Capture Pattern 8
    scratchByteBuffer.put((byte) 'O');
    scratchByteBuffer.put((byte) 'p');
    scratchByteBuffer.put((byte) 'u');
    scratchByteBuffer.put((byte) 's');
    scratchByteBuffer.put((byte) 'H');
    scratchByteBuffer.put((byte) 'e');
    scratchByteBuffer.put((byte) 'a');
    scratchByteBuffer.put((byte) 'd');

    // version
    scratchByteBuffer.put((byte) 1);

    // output channel count
    scratchByteBuffer.put((byte) 2);

    // pre-skip
    scratchByteBuffer.putShort((short) 312);

    // input sample rate
    scratchByteBuffer.putInt(SAMPLE_RATE);

    // Output Gain
    scratchByteBuffer.putShort((short) 0);

    // channel mapping family
    scratchByteBuffer.put((byte) 0);

    int checksum =
        Util.crc32(scratchBuffer, /* start= */ 0, OGG_ID_HEADER_LENGTH, /* initialValue= */ 0);
    scratchByteBuffer.putInt(/* index= */ 22, checksum);
    scratchByteBuffer.position(OGG_ID_HEADER_LENGTH);
  }

  private void writeCommentHeaderPacket() {
    // Id Header
    writeOggPacketHeader(/* pageSequenceNumber= */ 1, /* isIdHeaderPacket= */ false);

    // Payload Size = 24
    scratchByteBuffer.put((byte) 24);

    // Comment Header Opus Capture Pattern 8
    scratchByteBuffer.put((byte) 'O');
    scratchByteBuffer.put((byte) 'p');
    scratchByteBuffer.put((byte) 'u');
    scratchByteBuffer.put((byte) 's');
    scratchByteBuffer.put((byte) 'T');
    scratchByteBuffer.put((byte) 'a');
    scratchByteBuffer.put((byte) 'g');
    scratchByteBuffer.put((byte) 's');

    // Vendor Comment String Length
    scratchByteBuffer.putInt(8);

    // Vendor Comment String
    scratchByteBuffer.put((byte) 'G');
    scratchByteBuffer.put((byte) 'o');
    scratchByteBuffer.put((byte) 'o');
    scratchByteBuffer.put((byte) 'g');
    scratchByteBuffer.put((byte) 'l');
    scratchByteBuffer.put((byte) 'e');
    scratchByteBuffer.put((byte) 'r');
    scratchByteBuffer.put((byte) 's');

    // UserCommentList Length
    scratchByteBuffer.putInt(0);

    int checksum =
        Util.crc32(
            scratchBuffer,
            OGG_ID_HEADER_LENGTH,
            OGG_ID_HEADER_LENGTH + OGG_COMMENT_HEADER_LENGTH,
            /* initialValue= */ 0);

    scratchByteBuffer.putInt(/* index= */ 69, checksum);

    scratchByteBuffer.position(OGG_ID_HEADER_LENGTH + OGG_COMMENT_HEADER_LENGTH);
  }

  private void writeBuffer(ByteBuffer buffer) throws IOException {
    RandomAccessFile randomAccessFile = Assertions.checkNotNull(this.randomAccessFile);
    while (buffer.hasRemaining()) {
      int bytesToWrite = min(buffer.remaining(), scratchBuffer.length);
      buffer.get(scratchBuffer, /* offset= */ 0, bytesToWrite);
      randomAccessFile.write(scratchBuffer, /* off= */ 0, bytesToWrite);
    }
  }

  private void resetInternal() throws IOException {
    @Nullable RandomAccessFile randomAccessFile = this.randomAccessFile;
    if (randomAccessFile == null) {
      return;
    }

    try {
      randomAccessFile.close();
    } finally {
      this.randomAccessFile = null;
    }
  }

  private String getNextOutputFileName() {
    return Util.formatInvariant(
        "%s/%s-%04d.ogg",
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            .getAbsolutePath(),
        outputFileNamePrefix,
        counter++);
  }
}
