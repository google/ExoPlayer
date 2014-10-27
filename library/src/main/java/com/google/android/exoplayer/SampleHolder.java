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
package com.google.android.exoplayer;

import java.nio.ByteBuffer;

/**
 * Holds sample data and corresponding metadata.
 */
public final class SampleHolder {

  /**
   * Disallows buffer replacement.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;

  /**
   * Allows buffer replacement using {@link ByteBuffer#allocate(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;

  /**
   * Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

  public final CryptoInfo cryptoInfo;

  /**
   * A buffer holding the sample data.
   */
  public ByteBuffer data;

  /**
   * The size of the sample in bytes.
   */
  public int size;

  /**
   * Flags that accompany the sample. A combination of
   * {@link android.media.MediaExtractor#SAMPLE_FLAG_SYNC} and
   * {@link android.media.MediaExtractor#SAMPLE_FLAG_ENCRYPTED}
   */
  public int flags;

  /**
   * The time at which the sample should be presented.
   */
  public long timeUs;

  /**
   * If true then the sample should be decoded, but should not be presented.
   */
  public boolean decodeOnly;

  private final int bufferReplacementMode;

  /**
   * @param bufferReplacementMode Determines the behavior of {@link #replaceBuffer(int)}. One of
   *     {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
   *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  public SampleHolder(int bufferReplacementMode) {
    this.cryptoInfo = new CryptoInfo();
    this.bufferReplacementMode = bufferReplacementMode;
  }

  /**
   * Attempts to replace {@link #data} with a {@link ByteBuffer} of the specified capacity.
   *
   * @param capacity The capacity of the replacement buffer, in bytes.
   * @return True if the buffer was replaced. False otherwise.
   */
  public boolean replaceBuffer(int capacity) {
    switch (bufferReplacementMode) {
      case BUFFER_REPLACEMENT_MODE_NORMAL:
        data = ByteBuffer.allocate(capacity);
        return true;
      case BUFFER_REPLACEMENT_MODE_DIRECT:
        data = ByteBuffer.allocateDirect(capacity);
        return true;
    }
    return false;
  }

}
