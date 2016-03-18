/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.util.extensions.SimpleDecoder;

/**
 * Parses {@link Subtitle}s from {@link SubtitleInputBuffer}s.
 */
public abstract class SubtitleParser extends
    SimpleDecoder<SubtitleInputBuffer, SubtitleOutputBuffer, ParserException> {

  protected SubtitleParser() {
    super(new SubtitleInputBuffer[2], new SubtitleOutputBuffer[2]);
    setInitialInputBufferSize(1024);
  }

  @Override
  protected final SubtitleInputBuffer createInputBuffer() {
    return new SubtitleInputBuffer();
  }

  @Override
  protected final SubtitleOutputBuffer createOutputBuffer() {
    return new SubtitleOutputBuffer(this);
  }

  @Override
  protected final void releaseOutputBuffer(SubtitleOutputBuffer buffer) {
    super.releaseOutputBuffer(buffer);
  }

  @Override
  protected final ParserException decode(SubtitleInputBuffer inputBuffer,
      SubtitleOutputBuffer outputBuffer) {
    try {
      Subtitle subtitle = decode(inputBuffer.sampleHolder.data.array(),
          inputBuffer.sampleHolder.size);
      outputBuffer.setOutput(inputBuffer.sampleHolder.timeUs, subtitle,
          inputBuffer.subsampleOffsetUs);
      return null;
    } catch (ParserException e) {
      return e;
    }
  }

  protected abstract Subtitle decode(byte[] data, int size) throws ParserException;

}
