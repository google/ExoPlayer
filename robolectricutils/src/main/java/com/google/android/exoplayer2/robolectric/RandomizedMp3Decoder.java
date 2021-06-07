/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.robolectric;

import android.media.AudioFormat;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.audio.MpegAudioUtil;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.shadows.ShadowMediaCodec;

/**
 * Generates randomized, but correct amount of data on MP3 audio input.
 *
 * <p>The decoder reads the MP3 header for each input MP3 frame, determines the number of bytes the
 * input frame should inflate to, and writes randomized data of that amount to the output buffer.
 * Decoder randomness can help us identify possible errors in downstream renderers and audio
 * processors. The random bahavior is deterministic, it outputs the same bytes across multiple runs.
 *
 * <p>All the data written to the output by the decoder can be obtained by getAllOutputBytes().
 */
@RequiresApi(29)
public final class RandomizedMp3Decoder implements ShadowMediaCodec.CodecConfig.Codec {
  private final List<byte[]> decoderOutput = new ArrayList<>();
  private int frameSizeInBytes;

  @Override
  public void process(ByteBuffer in, ByteBuffer out) {
    if (in.remaining() == 0) {
      // An empty frame will be queued by the MediaCodecRenderer on END_OF_STREAM.
      return;
    }

    Assertions.checkState(
        in.remaining() >= 4, "Frame size too small, should be at least 4 to hold an MP3 header");

    // Get the desired output size for every input.
    int headerDataBigEndian = Util.getBigEndianInt(in, in.position());
    int frameCount = MpegAudioUtil.parseMpegAudioFrameSampleCount(headerDataBigEndian);

    int expectedNumBytes = frameCount * frameSizeInBytes;
    byte[] bytesToWrite = TestUtil.buildTestData(expectedNumBytes);

    out.put(bytesToWrite);
    decoderOutput.add(bytesToWrite);

    in.position(in.limit());
  }

  @Override
  public void onConfigured(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
    int pcmEncoding =
        format.getInteger(
            MediaFormat.KEY_PCM_ENCODING, /* defaultValue= */ AudioFormat.ENCODING_PCM_16BIT);
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    Assertions.checkArgument(
        format.getString(MediaFormat.KEY_MIME, MimeTypes.AUDIO_MPEG).equals(MimeTypes.AUDIO_MPEG));
    frameSizeInBytes = Util.getPcmFrameSize(pcmEncoding, channelCount);
  }

  /**
   * Returns all arrays of bytes output from the decoder.
   *
   * @return a list of byte arrays (for each MP3 frame input) that were previously output from the
   *     decoder.
   */
  public ImmutableList<byte[]> getAllOutputBytes() {
    return ImmutableList.copyOf(decoderOutput);
  }
}
