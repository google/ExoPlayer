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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class TransformerVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerTranscodingVideoRenderer";

  private final Context context;
  private final DecoderInputBuffer decoderInputBuffer;

  private @MonotonicNonNull SefSlowMotionFlattener sefSlowMotionFlattener;

  public TransformerVideoRenderer(
      Context context,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      Transformation transformation) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformation);
    this.context = context;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  /** Attempts to read the input format and to initialize the {@link SamplePipeline}. */
  @Override
  protected boolean ensureConfigured() throws ExoPlaybackException {
    if (samplePipeline != null) {
      return true;
    }
    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    if ((transformation.videoMimeType != null
            && !transformation.videoMimeType.equals(inputFormat.sampleMimeType))
        || (transformation.outputHeight != Format.NO_VALUE
            && transformation.outputHeight != inputFormat.height)) {
      samplePipeline = new VideoSamplePipeline(context, inputFormat, transformation, getIndex());
    } else {
      samplePipeline = new PassthroughSamplePipeline(inputFormat);
    }
    if (transformation.flattenForSlowMotion) {
      sefSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
    return true;
  }

  /**
   * Queues the input buffer to the sample pipeline unless it should be dropped because of slow
   * motion flattening.
   */
  @Override
  @RequiresNonNull({"samplePipeline", "#1.data"})
  protected void maybeQueueSampleToPipeline(DecoderInputBuffer inputBuffer) {
    ByteBuffer data = inputBuffer.data;
    boolean shouldDropSample =
        sefSlowMotionFlattener != null && sefSlowMotionFlattener.dropOrTransformSample(inputBuffer);
    if (shouldDropSample) {
      data.clear();
    } else {
      samplePipeline.queueInputBuffer();
    }
  }
}
