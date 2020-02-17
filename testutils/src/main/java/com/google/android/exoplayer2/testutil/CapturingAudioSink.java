/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.ForwardingAudioSink;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A {@link ForwardingAudioSink} that captures configuration, discontinuity and buffer events. */
public final class CapturingAudioSink extends ForwardingAudioSink implements Dumper.Dumpable {

  /**
   * If true, makes {@link #assertOutput(Context, String)} method write the output to a file, rather
   * than validating that the output matches the dump file.
   *
   * <p>The output file is written to the test apk's external storage directory, which is typically:
   * {@code /sdcard/Android/data/${package-under-test}.test/files/}.
   */
  private static final boolean WRITE_DUMP = false;

  private final List<Dumper.Dumpable> interceptedData;
  @Nullable private ByteBuffer currentBuffer;

  public CapturingAudioSink(AudioSink sink) {
    super(sink);
    interceptedData = new ArrayList<>();
  }

  @Override
  public void configure(
      int inputEncoding,
      int inputChannelCount,
      int inputSampleRate,
      int specifiedBufferSize,
      @Nullable int[] outputChannels,
      int trimStartFrames,
      int trimEndFrames)
      throws ConfigurationException {
    interceptedData.add(
        new DumpableConfiguration(inputEncoding, inputChannelCount, inputSampleRate));
    super.configure(
        inputEncoding,
        inputChannelCount,
        inputSampleRate,
        specifiedBufferSize,
        outputChannels,
        trimStartFrames,
        trimEndFrames);
  }

  @Override
  public void handleDiscontinuity() {
    interceptedData.add(new DumpableDiscontinuity());
    super.handleDiscontinuity();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException, WriteException {
    // handleBuffer is called repeatedly with the same buffer until it's been fully consumed by the
    // sink. We only want to dump each buffer once, and we need to do so before the sink being
    // forwarded to has a chance to modify its position.
    if (buffer != currentBuffer) {
      interceptedData.add(new DumpableBuffer(buffer, presentationTimeUs));
      currentBuffer = buffer;
    }
    boolean fullyConsumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount);
    if (fullyConsumed) {
      currentBuffer = null;
    }
    return fullyConsumed;
  }

  @Override
  public void flush() {
    currentBuffer = null;
    super.flush();
  }

  @Override
  public void reset() {
    currentBuffer = null;
    super.reset();
  }

  /**
   * Asserts that dump of this sink is equal to expected dump which is read from {@code dumpFile}.
   *
   * <p>If assertion fails because of an intended change in the output or a new dump file needs to
   * be created, set {@link #WRITE_DUMP} flag to true and run the test again. Instead of assertion,
   * actual dump will be written to {@code dumpFile}. This new dump file needs to be copied to the
   * project, {@code library/src/androidTest/assets} folder manually.
   */
  public void assertOutput(Context context, String dumpFile) throws IOException {
    String actual = new Dumper().add(this).toString();

    if (WRITE_DUMP) {
      File directory = context.getExternalFilesDir(null);
      File file = new File(directory, dumpFile);
      Assertions.checkStateNotNull(file.getParentFile()).mkdirs();
      PrintWriter out = new PrintWriter(file);
      out.print(actual);
      out.close();
    } else {
      String expected = TestUtil.getString(context, dumpFile);
      assertWithMessage(dumpFile).that(actual).isEqualTo(expected);
    }
  }

  @Override
  public void dump(Dumper dumper) {
    for (int i = 0; i < interceptedData.size(); i++) {
      interceptedData.get(i).dump(dumper);
    }
  }

  private static final class DumpableConfiguration implements Dumper.Dumpable {

    private final int inputEncoding;
    private final int inputChannelCount;
    private final int inputSampleRate;

    public DumpableConfiguration(int inputEncoding, int inputChannelCount, int inputSampleRate) {
      this.inputEncoding = inputEncoding;
      this.inputChannelCount = inputChannelCount;
      this.inputSampleRate = inputSampleRate;
    }

    @Override
    public void dump(Dumper dumper) {
      int bitDepth = (Util.getPcmFrameSize(inputEncoding, /* channelCount= */ 1) * 8);
      dumper
          .startBlock("config")
          .add("encoding", inputEncoding + " (" + bitDepth + " bit)")
          .add("channel count", inputChannelCount)
          .add("sample rate", inputSampleRate)
          .endBlock();
    }
  }

  private static final class DumpableBuffer implements Dumper.Dumpable {

    private final long presentationTimeUs;
    private final int dataHashcode;

    public DumpableBuffer(ByteBuffer buffer, long presentationTimeUs) {
      this.presentationTimeUs = presentationTimeUs;
      // Compute a hash of the buffer data without changing its position.
      int initialPosition = buffer.position();
      byte[] data = new byte[buffer.remaining()];
      buffer.get(data);
      buffer.position(initialPosition);
      this.dataHashcode = Arrays.hashCode(data);
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("buffer")
          .add("time", presentationTimeUs)
          .add("data", dataHashcode)
          .endBlock();
    }
  }

  private static final class DumpableDiscontinuity implements Dumper.Dumpable {

    @Override
    public void dump(Dumper dumper) {
      dumper.startBlock("discontinuity").endBlock();
    }
  }
}
