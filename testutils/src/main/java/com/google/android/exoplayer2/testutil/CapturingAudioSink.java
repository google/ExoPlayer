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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.ForwardingAudioSink;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A {@link ForwardingAudioSink} that captures configuration, discontinuity and buffer events. */
public final class CapturingAudioSink extends ForwardingAudioSink implements Dumper.Dumpable {

  private final List<Dumper.Dumpable> interceptedData;
  @Nullable private ByteBuffer currentBuffer;

  public CapturingAudioSink(AudioSink sink) {
    super(sink);
    interceptedData = new ArrayList<>();
  }

  @Override
  public void configure(Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
      throws ConfigurationException {
    interceptedData.add(
        new DumpableConfiguration(
            inputFormat.pcmEncoding, inputFormat.channelCount, inputFormat.sampleRate));
    super.configure(inputFormat, specifiedBufferSize, outputChannels);
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

  @Override
  public void dump(Dumper dumper) {
    for (int i = 0; i < interceptedData.size(); i++) {
      interceptedData.get(i).dump(dumper);
    }
  }

  private static final class DumpableConfiguration implements Dumper.Dumpable {

    private final @C.PcmEncoding int inputPcmEncoding;
    private final int inputChannelCount;
    private final int inputSampleRate;

    public DumpableConfiguration(
        @C.PcmEncoding int inputPcmEncoding, int inputChannelCount, int inputSampleRate) {
      this.inputPcmEncoding = inputPcmEncoding;
      this.inputChannelCount = inputChannelCount;
      this.inputSampleRate = inputSampleRate;
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("config")
          .add("pcmEncoding", inputPcmEncoding)
          .add("channelCount", inputChannelCount)
          .add("sampleRate", inputSampleRate)
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
