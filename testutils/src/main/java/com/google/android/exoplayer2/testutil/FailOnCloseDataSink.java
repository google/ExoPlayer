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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link DataSink} that can simulate caching the bytes being written to it, and then failing to
 * persist them when {@link #close()} is called.
 */
public final class FailOnCloseDataSink implements DataSink {

  /** Factory to create a {@link FailOnCloseDataSink}. */
  public static final class Factory implements DataSink.Factory {

    private final Cache cache;
    private final AtomicBoolean failOnClose;

    /**
     * Creates an instance.
     *
     * @param cache The cache to write to when not in fail-on-close mode.
     * @param failOnClose An {@link AtomicBoolean} whose value is read in each call to {@link #open}
     *     to determine whether to enable fail-on-close for the read that's being started.
     */
    public Factory(Cache cache, AtomicBoolean failOnClose) {
      this.cache = cache;
      this.failOnClose = failOnClose;
    }

    @Override
    public DataSink createDataSink() {
      return new FailOnCloseDataSink(cache, failOnClose);
    }
  }

  private final CacheDataSink wrappedSink;
  private final AtomicBoolean failOnClose;
  private boolean currentReadFailOnClose;

  /**
   * Creates an instance.
   *
   * @param cache The cache to write to when not in fail-on-close mode.
   * @param failOnClose An {@link AtomicBoolean} whose value is read in each call to {@link #open}
   *     to determine whether to enable fail-on-close for the read that's being started.
   */
  public FailOnCloseDataSink(Cache cache, AtomicBoolean failOnClose) {
    this.wrappedSink = new CacheDataSink(cache, /* fragmentSize= */ C.LENGTH_UNSET);
    this.failOnClose = failOnClose;
  }

  @Override
  public void open(DataSpec dataSpec) throws IOException {
    currentReadFailOnClose = failOnClose.get();
    if (currentReadFailOnClose) {
      return;
    }
    wrappedSink.open(dataSpec);
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws IOException {
    if (currentReadFailOnClose) {
      return;
    }
    wrappedSink.write(buffer, offset, length);
  }

  @Override
  public void close() throws IOException {
    if (currentReadFailOnClose) {
      throw new IOException("Fail on close");
    }
    wrappedSink.close();
  }
}
