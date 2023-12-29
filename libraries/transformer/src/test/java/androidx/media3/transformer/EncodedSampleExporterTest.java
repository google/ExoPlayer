/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.EncodedSampleExporter.ALLOCATION_SIZE_TARGET_BYTES;
import static androidx.media3.transformer.EncodedSampleExporter.MAX_INPUT_BUFFER_COUNT;
import static androidx.media3.transformer.EncodedSampleExporter.MIN_INPUT_BUFFER_COUNT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit tests for {@link EncodedSampleExporter}. */
@RunWith(AndroidJUnit4.class)
public final class EncodedSampleExporterTest {

  private EncodedSampleExporter encodedSampleExporter;

  @Mock private ListenerSet.IterationFinishedEvent<Transformer.Listener> mockIterationFinishedEvent;
  @Mock private HandlerWrapper mockHandlerWrapper;

  @Before
  public void setUp() {
    Looper looper = checkNotNull(Looper.myLooper());
    FallbackListener fallbackListener =
        new FallbackListener(
            new Composition.Builder(
                    new EditedMediaItemSequence(
                        new EditedMediaItem.Builder(MediaItem.EMPTY).build()))
                .build(),
            new ListenerSet<>(looper, Clock.DEFAULT, mockIterationFinishedEvent),
            mockHandlerWrapper,
            new TransformationRequest.Builder().build());
    fallbackListener.setTrackCount(1);
    encodedSampleExporter =
        new EncodedSampleExporter(
            new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build(),
            new TransformationRequest.Builder().build(),
            new MuxerWrapper(
                /* outputPath= */ "unused",
                new InAppMuxer.Factory.Builder().build(),
                mock(MuxerWrapper.Listener.class),
                MuxerWrapper.MUXER_MODE_DEFAULT,
                /* dropSamplesBeforeFirstVideoSample= */ false),
            fallbackListener,
            /* initialTimestampOffsetUs= */ 0);
  }

  @Test
  public void queueInput_withEmptyBuffers_allocatesMaxBufferCount() {
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT; i++) {
      @Nullable DecoderInputBuffer decoderInputBuffer = encodedSampleExporter.getInputBuffer();
      assertThat(decoderInputBuffer).isNotNull();
      decoderInputBuffer.ensureSpaceForWrite(/* length= */ 0);
      encodedSampleExporter.queueInputBuffer();
    }
    assertThat(encodedSampleExporter.getInputBuffer()).isNull();
  }

  @Test
  public void queueInput_withSmallBuffers_allocatesMaxBufferCount() {
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 1))
        .isEqualTo(MAX_INPUT_BUFFER_COUNT);
  }

  @Test
  public void queueInput_withMediumBuffers_reachesBufferSizeTarget() {
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 16 * 1024))
        .isEqualTo(ALLOCATION_SIZE_TARGET_BYTES);
  }

  @Test
  public void queueInput_withLargeBuffers_allocatesMinBufferCount() {
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 1024 * 1024))
        .isEqualTo(MIN_INPUT_BUFFER_COUNT * 1024 * 1024);
  }

  @Test
  public void queueInputToLimitThenProcessOutput_queueInputSucceeds() {
    // Queue input until no more input is accepted.
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 16 * 1024))
        .isEqualTo(ALLOCATION_SIZE_TARGET_BYTES);
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 1024 * 1024)).isEqualTo(0);

    // Simulate draining to the muxer.
    while (encodedSampleExporter.getMuxerInputBuffer() != null) {
      encodedSampleExporter.releaseMuxerInputBuffer();
    }

    // It's possible to queue input again.
    assertThat(fillInputAndGetTotalInputSize(/* inputBufferSizeBytes= */ 16 * 1024))
        .isEqualTo(ALLOCATION_SIZE_TARGET_BYTES);
  }

  private long fillInputAndGetTotalInputSize(int inputBufferSizeBytes) {
    int totalAllocatedSize = 0;
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT + 1; i++) {
      @Nullable DecoderInputBuffer decoderInputBuffer = encodedSampleExporter.getInputBuffer();
      if (decoderInputBuffer == null) {
        return totalAllocatedSize;
      }
      decoderInputBuffer.ensureSpaceForWrite(inputBufferSizeBytes);
      encodedSampleExporter.queueInputBuffer();
      totalAllocatedSize += inputBufferSizeBytes;
    }
    throw new IllegalStateException("Unexpectedly allocated more than MAX_INPUT_BUFFER_COUNT");
  }
}
