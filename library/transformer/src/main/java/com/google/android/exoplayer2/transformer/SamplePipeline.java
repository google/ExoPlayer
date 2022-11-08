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

package com.google.android.exoplayer2.transformer;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/**
 * Pipeline for processing {@link DecoderInputBuffer DecoderInputBuffers}.
 *
 * <p>This pipeline can be used to implement transformations of audio or video samples.
 */
/* package */ interface SamplePipeline {

  /** Returns a buffer if the pipeline is ready to accept input, and {@code null} otherwise. */
  @Nullable
  DecoderInputBuffer dequeueInputBuffer() throws TransformationException;

  /**
   * Informs the pipeline that its input buffer contains new input.
   *
   * <p>Should be called after filling the input buffer from {@link #dequeueInputBuffer()} with new
   * input.
   */
  void queueInputBuffer() throws TransformationException;

  /**
   * Processes the input data and returns whether it may be possible to process more data by calling
   * this method again.
   */
  boolean processData() throws TransformationException;

  /** Returns whether the pipeline has ended. */
  boolean isEnded();

  /** Releases all resources held by the pipeline. */
  void release();
}
