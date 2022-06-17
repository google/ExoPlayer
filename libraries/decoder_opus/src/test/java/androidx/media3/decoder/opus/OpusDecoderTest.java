/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.decoder.opus;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link OpusDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class OpusDecoderTest {

  private static final LibraryLoader LOADER =
      new LibraryLoader("opusV2JNI") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  private static final byte[] HEADER =
      new byte[] {79, 112, 117, 115, 72, 101, 97, 100, 0, 2, 1, 56, 0, 0, -69, -128, 0, 0, 0};

  private static final byte[] ENCODED_DATA = new byte[] {-4};
  private static final int DECODED_DATA_SIZE = 3840;

  private static final int HEADER_PRE_SKIP_SAMPLES = 14337;

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;

  private static final int DISCARD_PADDING_NANOS = 166667;

  private static final ImmutableList<byte[]> HEADER_ONLY_INITIALIZATION_DATA =
      ImmutableList.of(HEADER);

  private static final long PRE_SKIP_NANOS = 6_500_000;
  private static final long CUSTOM_PRE_SKIP_SAMPLES = 28674;
  private static final byte[] CUSTOM_PRE_SKIP_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(CUSTOM_PRE_SKIP_SAMPLES));

  private static final long CUSTOM_SEEK_PRE_ROLL_SAMPLES = 7680;
  private static final byte[] CUSTOM_SEEK_PRE_ROLL_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(CUSTOM_SEEK_PRE_ROLL_SAMPLES));

  private static final ImmutableList<byte[]> FULL_INITIALIZATION_DATA =
      ImmutableList.of(HEADER, CUSTOM_PRE_SKIP_BYTES, CUSTOM_SEEK_PRE_ROLL_BYTES);

  @Test
  public void getChannelCount() {
    int channelCount = OpusDecoder.getChannelCount(HEADER);
    assertThat(channelCount).isEqualTo(2);
  }

  @Test
  public void getPreSkipSamples_fullInitializationData_returnsOverrideValue() {
    int preSkipSamples = OpusDecoder.getPreSkipSamples(FULL_INITIALIZATION_DATA);
    assertThat(preSkipSamples).isEqualTo(CUSTOM_PRE_SKIP_SAMPLES);
  }

  @Test
  public void getPreSkipSamples_headerOnlyInitializationData_returnsHeaderValue() {
    int preSkipSamples = OpusDecoder.getPreSkipSamples(HEADER_ONLY_INITIALIZATION_DATA);
    assertThat(preSkipSamples).isEqualTo(HEADER_PRE_SKIP_SAMPLES);
  }

  @Test
  public void getSeekPreRollSamples_fullInitializationData_returnsInitializationDataValue() {
    int seekPreRollSamples = OpusDecoder.getSeekPreRollSamples(FULL_INITIALIZATION_DATA);
    assertThat(seekPreRollSamples).isEqualTo(CUSTOM_SEEK_PRE_ROLL_SAMPLES);
  }

  @Test
  public void getSeekPreRollSamples_headerOnlyInitializationData_returnsDefaultValue() {
    int seekPreRollSamples = OpusDecoder.getSeekPreRollSamples(HEADER_ONLY_INITIALIZATION_DATA);
    assertThat(seekPreRollSamples).isEqualTo(DEFAULT_SEEK_PRE_ROLL_SAMPLES);
  }

  @Test
  public void getDiscardPaddingSamples_positiveSampleLength_returnSampleLength() {
    int discardPaddingSamples =
        OpusDecoder.getDiscardPaddingSamples(createSupplementalData(DISCARD_PADDING_NANOS));
    assertThat(discardPaddingSamples).isEqualTo(nanosecondsToSampleCount(DISCARD_PADDING_NANOS));
  }

  @Test
  public void getDiscardPaddingSamples_negativeSampleLength_returnZero() {
    int discardPaddingSamples =
        OpusDecoder.getDiscardPaddingSamples(createSupplementalData(-DISCARD_PADDING_NANOS));
    assertThat(discardPaddingSamples).isEqualTo(0);
  }

  @Test
  public void decode_removesPreSkipFromOutput() throws OpusDecoderException {
    assumeTrue(LOADER.isAvailable());
    OpusDecoder decoder =
        new OpusDecoder(
            /* numInputBuffers= */ 0,
            /* numOutputBuffers= */ 0,
            /* initialInputBufferSize= */ 0,
            createInitializationData(/* preSkipNanos= */ PRE_SKIP_NANOS),
            /* cryptoConfig= */ null,
            /* outputFloat= */ false);
    DecoderInputBuffer input =
        createInputBuffer(decoder, ENCODED_DATA, /* supplementalData= */ null);
    SimpleDecoderOutputBuffer output = decoder.createOutputBuffer();
    assertThat(decoder.decode(input, output, false)).isNull();
    assertThat(output.data.remaining())
        .isEqualTo(DECODED_DATA_SIZE - nanosecondsToBytes(PRE_SKIP_NANOS));
  }

  @Test
  public void decode_whenDiscardPaddingDisabled_returnsDiscardPadding()
      throws OpusDecoderException {
    assumeTrue(LOADER.isAvailable());
    OpusDecoder decoder =
        new OpusDecoder(
            /* numInputBuffers= */ 0,
            /* numOutputBuffers= */ 0,
            /* initialInputBufferSize= */ 0,
            createInitializationData(/* preSkipNanos= */ 0),
            /* cryptoConfig= */ null,
            /* outputFloat= */ false);
    DecoderInputBuffer input =
        createInputBuffer(
            decoder,
            ENCODED_DATA,
            /* supplementalData= */ buildNativeOrderByteArray(DISCARD_PADDING_NANOS));
    SimpleDecoderOutputBuffer output = decoder.createOutputBuffer();
    assertThat(decoder.decode(input, output, false)).isNull();
    assertThat(output.data.remaining()).isEqualTo(DECODED_DATA_SIZE);
  }

  @Test
  public void decode_whenDiscardPaddingEnabled_removesDiscardPadding() throws OpusDecoderException {
    assumeTrue(LOADER.isAvailable());
    OpusDecoder decoder =
        new OpusDecoder(
            /* numInputBuffers= */ 0,
            /* numOutputBuffers= */ 0,
            /* initialInputBufferSize= */ 0,
            createInitializationData(/* preSkipNanos= */ 0),
            /* cryptoConfig= */ null,
            /* outputFloat= */ false);
    decoder.experimentalSetDiscardPaddingEnabled(true);
    DecoderInputBuffer input =
        createInputBuffer(
            decoder,
            ENCODED_DATA,
            /* supplementalData= */ buildNativeOrderByteArray(DISCARD_PADDING_NANOS));
    SimpleDecoderOutputBuffer output = decoder.createOutputBuffer();
    assertThat(decoder.decode(input, output, false)).isNull();
    assertThat(output.data.limit())
        .isEqualTo(DECODED_DATA_SIZE - nanosecondsToBytes(DISCARD_PADDING_NANOS));
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / OpusDecoder.SAMPLE_RATE;
  }

  private static long nanosecondsToSampleCount(long nanoseconds) {
    return (nanoseconds * OpusDecoder.SAMPLE_RATE) / C.NANOS_PER_SECOND;
  }

  private static long nanosecondsToBytes(long nanoseconds) {
    return nanosecondsToSampleCount(nanoseconds) * 4;
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }

  private static ImmutableList<byte[]> createInitializationData(long preSkipNanos) {
    byte[] preSkip = buildNativeOrderByteArray(preSkipNanos);
    return ImmutableList.of(HEADER, preSkip, CUSTOM_SEEK_PRE_ROLL_BYTES);
  }

  // The cast to ByteBuffer is required for Java 8 compatibility. See
  // https://issues.apache.org/jira/browse/MRESOLVER-85
  @SuppressWarnings("UnnecessaryCast")
  private static ByteBuffer createSupplementalData(long value) {
    return (ByteBuffer)
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).rewind();
  }

  private static DecoderInputBuffer createInputBuffer(
      OpusDecoder decoder, byte[] data, @Nullable byte[] supplementalData) {
    DecoderInputBuffer input = decoder.createInputBuffer();
    input.ensureSpaceForWrite(data.length);
    input.data.put(data);
    input.data.position(0).limit(data.length);
    if (supplementalData != null) {
      input.resetSupplementalData(supplementalData.length);
      input.supplementalData.put(supplementalData).rewind();
      input.addFlag(C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA);
    }
    return input;
  }
}
