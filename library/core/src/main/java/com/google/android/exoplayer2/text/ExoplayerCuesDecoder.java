/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link SubtitleDecoder} that decodes subtitle samples of type {@link
 * MimeTypes#APPLICATION_MEDIA3_CUES}
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ExoplayerCuesDecoder implements SubtitleDecoder {
  @Documented
  @Target(TYPE_USE)
  @IntDef(value = {INPUT_BUFFER_AVAILABLE, INPUT_BUFFER_DEQUEUED, INPUT_BUFFER_QUEUED})
  @Retention(RetentionPolicy.SOURCE)
  private @interface InputBufferState {}

  private static final int INPUT_BUFFER_AVAILABLE = 0;
  private static final int INPUT_BUFFER_DEQUEUED = 1;
  private static final int INPUT_BUFFER_QUEUED = 2;

  private static final int OUTPUT_BUFFERS_COUNT = 2;

  private final CueDecoder cueDecoder;
  private final SubtitleInputBuffer inputBuffer;
  private final Deque<SubtitleOutputBuffer> availableOutputBuffers;

  private @InputBufferState int inputBufferState;
  private boolean released;

  public ExoplayerCuesDecoder() {
    cueDecoder = new CueDecoder();
    inputBuffer = new SubtitleInputBuffer();
    availableOutputBuffers = new ArrayDeque<>();
    for (int i = 0; i < OUTPUT_BUFFERS_COUNT; i++) {
      availableOutputBuffers.addFirst(
          new SubtitleOutputBuffer() {
            @Override
            public void release() {
              ExoplayerCuesDecoder.this.releaseOutputBuffer(this);
            }
          });
    }
    inputBufferState = INPUT_BUFFER_AVAILABLE;
  }

  @Override
  public String getName() {
    return "ExoplayerCuesDecoder";
  }

  @Override
  public void setOutputStartTimeUs(long outputStartTimeUs) {
    // Do nothing.
  }

  @Nullable
  @Override
  public SubtitleInputBuffer dequeueInputBuffer() throws SubtitleDecoderException {
    checkState(!released);
    if (inputBufferState != INPUT_BUFFER_AVAILABLE) {
      return null;
    }
    inputBufferState = INPUT_BUFFER_DEQUEUED;
    return inputBuffer;
  }

  @Override
  public void queueInputBuffer(SubtitleInputBuffer inputBuffer) throws SubtitleDecoderException {
    checkState(!released);
    checkState(inputBufferState == INPUT_BUFFER_DEQUEUED);
    checkArgument(this.inputBuffer == inputBuffer);
    inputBufferState = INPUT_BUFFER_QUEUED;
  }

  @Nullable
  @Override
  public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException {
    checkState(!released);
    if (inputBufferState != INPUT_BUFFER_QUEUED || availableOutputBuffers.isEmpty()) {
      return null;
    }
    SubtitleOutputBuffer outputBuffer = availableOutputBuffers.removeFirst();
    if (inputBuffer.isEndOfStream()) {
      outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    } else {
      Subtitle subtitle =
          new CuesWithTimingSubtitle(
              ImmutableList.of(
                  cueDecoder.decode(inputBuffer.timeUs, checkNotNull(inputBuffer.data).array())));
      outputBuffer.setContent(inputBuffer.timeUs, subtitle, /* subsampleOffsetUs= */ 0);
    }
    inputBuffer.clear();
    inputBufferState = INPUT_BUFFER_AVAILABLE;
    return outputBuffer;
  }

  @Override
  public void flush() {
    checkState(!released);
    inputBuffer.clear();
    inputBufferState = INPUT_BUFFER_AVAILABLE;
  }

  @Override
  public void release() {
    released = true;
  }

  @Override
  public void setPositionUs(long positionUs) {
    // Do nothing
  }

  private void releaseOutputBuffer(SubtitleOutputBuffer outputBuffer) {
    checkState(availableOutputBuffers.size() < OUTPUT_BUFFERS_COUNT);
    checkArgument(!availableOutputBuffers.contains(outputBuffer));
    outputBuffer.clear();
    availableOutputBuffers.addFirst(outputBuffer);
  }
}
