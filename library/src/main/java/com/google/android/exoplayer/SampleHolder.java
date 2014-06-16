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
   * Whether a {@link SampleSource} is permitted to replace {@link #data} if its current value is
   * null or of insufficient size to hold the sample.
   */
  public final boolean allowDataBufferReplacement;

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

  /**
   * @param allowDataBufferReplacement See {@link #allowDataBufferReplacement}.
   */
  public SampleHolder(boolean allowDataBufferReplacement) {
    this.cryptoInfo = new CryptoInfo();
    this.allowDataBufferReplacement = allowDataBufferReplacement;
  }

}
