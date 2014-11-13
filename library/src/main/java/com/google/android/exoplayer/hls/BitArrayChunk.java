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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.BitArray;

import java.io.IOException;

/**
 * An abstract base class for {@link HlsChunk} implementations where the data should be loaded into
 * a {@link BitArray} and subsequently consumed.
 */
public abstract class BitArrayChunk extends HlsChunk {

  private static final int READ_GRANULARITY = 16 * 1024;

  private final BitArray bitArray;
  private volatile boolean loadFinished;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded. {@code dataSpec.length} must not exceed
   *     {@link Integer#MAX_VALUE}. If {@code dataSpec.length == C.LENGTH_UNBOUNDED} then
   *     the length resolved by {@code dataSource.open(dataSpec)} must not exceed
   *     {@link Integer#MAX_VALUE}.
   * @param bitArray The {@link BitArray} into which the data should be loaded.
   */
  public BitArrayChunk(DataSource dataSource, DataSpec dataSpec, BitArray bitArray) {
    super(dataSource, dataSpec);
    this.bitArray = bitArray;
  }

  @Override
  public void consume() throws IOException {
    consume(bitArray);
  }

  /**
   * Invoked by {@link #consume()}. Implementations should override this method to consume the
   * loaded data.
   *
   * @param bitArray The {@link BitArray} containing the loaded data.
   * @throws IOException If an error occurs consuming the loaded data.
   */
  protected abstract void consume(BitArray bitArray) throws IOException;

  /**
   * Whether the whole of the chunk has been loaded.
   *
   * @return True if the whole of the chunk has been loaded. False otherwise.
   */
  @Override
  public boolean isLoadFinished() {
    return loadFinished;
  }

  // Loadable implementation

  @Override
  public final void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public final boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public final void load() throws IOException, InterruptedException {
    try {
      bitArray.reset();
      dataSource.open(dataSpec);
      int bytesRead = 0;
      while (bytesRead != -1 && !loadCanceled) {
        bytesRead = bitArray.append(dataSource, READ_GRANULARITY);
      }
      loadFinished = !loadCanceled;
    } finally {
      dataSource.close();
    }
  }

}
