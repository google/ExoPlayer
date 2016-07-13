/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.text;

import com.google.android.exoplayer2.decoder.SimpleDecoder;

import java.nio.ByteBuffer;

/**
 * Base class for subtitle parsers that use their own decode thread.
 */
public abstract class SimpleSubtitleDecoder extends
    SimpleDecoder<SubtitleInputBuffer, SubtitleOutputBuffer, TextDecoderException> implements
    SubtitleDecoder {

  private final String name;

  protected SimpleSubtitleDecoder(String name) {
    super(new SubtitleInputBuffer[2], new SubtitleOutputBuffer[2]);
    this.name = name;
    setInitialInputBufferSize(1024);
  }

  @Override
  public final String getName() {
    return name;
  }

  @Override
  public void setPositionUs(long timeUs) {
    // Do nothing
  }

  @Override
  protected final SubtitleInputBuffer createInputBuffer() {
    return new SubtitleInputBuffer();
  }

  @Override
  protected final SubtitleOutputBuffer createOutputBuffer() {
    return new SimpleSubtitleOutputBuffer(this);
  }

  @Override
  protected final void releaseOutputBuffer(SubtitleOutputBuffer buffer) {
    super.releaseOutputBuffer(buffer);
  }

  @Override
  protected final TextDecoderException decode(SubtitleInputBuffer inputBuffer,
      SubtitleOutputBuffer outputBuffer, boolean reset) {
    try {
      ByteBuffer inputData = inputBuffer.data;
      Subtitle subtitle = decode(inputData.array(), inputData.limit());
      outputBuffer.setOutput(inputBuffer.timeUs, subtitle, inputBuffer.subsampleOffsetUs);
      return null;
    } catch (TextDecoderException e) {
      return e;
    }
  }

  /**
   * Decodes the data and converts it into a {@link Subtitle}.
   *
   * @param data The data to be decoded.
   * @param size The size of the data.
   * @return A {@link Subtitle} to rendered.
   * @throws TextDecoderException If a decoding error occurs.
   */
  protected abstract Subtitle decode(byte[] data, int size) throws TextDecoderException;

}
