/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import java.nio.ByteBuffer;

/**
 * Interface for processors of buffers, for use with {@link AudioTrack}.
 */
public interface BufferProcessor {

  /**
   * Processes the data in the specified input buffer in its entirety. Populates {@code output} with
   * processed data if is not {@code null} and has sufficient capacity. Otherwise a different buffer
   * will be populated and returned.
   *
   * @param input A buffer containing the input data to process.
   * @param output A buffer into which the output should be written, if its capacity is sufficient.
   * @return The processed output. Different to {@code output} if null was passed, or if its
   *     capacity was insufficient.
   */
  ByteBuffer handleBuffer(ByteBuffer input, ByteBuffer output);

}
