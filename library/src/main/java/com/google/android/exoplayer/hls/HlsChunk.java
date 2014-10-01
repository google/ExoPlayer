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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.Allocation;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceStream;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * An abstract base class for {@link Loadable} implementations that load chunks of data required
 * for the playback of streams.
 * <p>
 * TODO: Figure out whether this should merge with the chunk package, or whether the hls
 * implementation is going to naturally diverge.
 */
public abstract class HlsChunk implements Loadable {

  /**
   * The reason for a {@link HlsChunkSource} having generated this chunk. For reporting only.
   * Possible values for this variable are defined by the specific {@link HlsChunkSource}
   * implementations.
   */
  public final int trigger;

  private final DataSource dataSource;
  private final DataSpec dataSpec;

  private DataSourceStream dataSourceStream;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded. {@code dataSpec.length} must not exceed
   *     {@link Integer#MAX_VALUE}. If {@code dataSpec.length == C.LENGTH_UNBOUNDED} then
   *     the length resolved by {@code dataSource.open(dataSpec)} must not exceed
   *     {@link Integer#MAX_VALUE}.
   * @param trigger See {@link #trigger}.
   */
  public HlsChunk(DataSource dataSource, DataSpec dataSpec, int trigger) {
    Assertions.checkState(dataSpec.length <= Integer.MAX_VALUE);
    this.dataSource = Assertions.checkNotNull(dataSource);
    this.dataSpec = Assertions.checkNotNull(dataSpec);
    this.trigger = trigger;
  }

  /**
   * Initializes the {@link HlsChunk}.
   *
   * @param allocator An {@link Allocator} from which the {@link Allocation} needed to contain the
   *     data can be obtained.
   */
  public final void init(Allocator allocator) {
    Assertions.checkState(dataSourceStream == null);
    dataSourceStream = new DataSourceStream(dataSource, dataSpec, allocator);
  }

  /**
   * Releases the {@link HlsChunk}, releasing any backing {@link Allocation}s.
   */
  public final void release() {
    if (dataSourceStream != null) {
      dataSourceStream.close();
      dataSourceStream = null;
    }
  }

  /**
   * Gets the length of the chunk in bytes.
   *
   * @return The length of the chunk in bytes, or {@link C#LENGTH_UNBOUNDED} if the length has yet
   *     to be determined.
   */
  public final long getLength() {
    return dataSourceStream.getLength();
  }

  /**
   * Whether the whole of the data has been consumed.
   *
   * @return True if the whole of the data has been consumed. False otherwise.
   */
  public final boolean isReadFinished() {
    return dataSourceStream.isEndOfStream();
  }

  /**
   * Whether the whole of the chunk has been loaded.
   *
   * @return True if the whole of the chunk has been loaded. False otherwise.
   */
  public final boolean isLoadFinished() {
    return dataSourceStream.isLoadFinished();
  }

  /**
   * Gets the number of bytes that have been loaded.
   *
   * @return The number of bytes that have been loaded.
   */
  public final long bytesLoaded() {
    return dataSourceStream.getLoadPosition();
  }

  /**
   * Causes loaded data to be consumed.
   *
   * @throws IOException If an error occurs consuming the loaded data.
   */
  public final void consume() throws IOException {
    Assertions.checkState(dataSourceStream != null);
    consumeStream(dataSourceStream);
  }

  /**
   * Invoked by {@link #consume()}. Implementations may override this method if they wish to
   * consume the loaded data at this point.
   * <p>
   * The default implementation is a no-op.
   *
   * @param stream The stream of loaded data.
   * @throws IOException If an error occurs consuming the loaded data.
   */
  protected void consumeStream(NonBlockingInputStream stream) throws IOException {
    // Do nothing.
  }

  protected final NonBlockingInputStream getNonBlockingInputStream() {
    return dataSourceStream;
  }

  protected final void resetReadPosition() {
    if (dataSourceStream != null) {
      dataSourceStream.resetReadPosition();
    } else {
      // We haven't been initialized yet, so the read position must already be 0.
    }
  }

  // Loadable implementation

  @Override
  public final void cancelLoad() {
    dataSourceStream.cancelLoad();
  }

  @Override
  public final boolean isLoadCanceled() {
    return dataSourceStream.isLoadCanceled();
  }

  @Override
  public final void load() throws IOException, InterruptedException {
    dataSourceStream.load();
  }

}
