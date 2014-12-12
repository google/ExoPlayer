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
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * An abstract base class for {@link Loadable} implementations that load chunks of data required
 * for the playback of HLS streams.
 */
public abstract class HlsChunk implements Loadable {

  protected final DataSource dataSource;
  protected final DataSpec dataSpec;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded. {@code dataSpec.length} must not exceed
   *     {@link Integer#MAX_VALUE}. If {@code dataSpec.length == C.LENGTH_UNBOUNDED} then
   *     the length resolved by {@code dataSource.open(dataSpec)} must not exceed
   *     {@link Integer#MAX_VALUE}.
   */
  public HlsChunk(DataSource dataSource, DataSpec dataSpec) {
    Assertions.checkState(dataSpec.length <= Integer.MAX_VALUE);
    this.dataSource = Assertions.checkNotNull(dataSource);
    this.dataSpec = Assertions.checkNotNull(dataSpec);
  }

  public abstract void consume() throws IOException;

  public abstract boolean isLoadFinished();

}
