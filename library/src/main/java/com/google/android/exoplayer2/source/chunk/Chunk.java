/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source.chunk;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Assertions;

/**
 * An abstract base class for {@link Loadable} implementations that load chunks of data required
 * for the playback of streams.
 */
public abstract class Chunk implements Loadable {

  /**
   * The {@link DataSpec} that defines the data to be loaded.
   */
  public final DataSpec dataSpec;
  /**
   * The type of the chunk. One of the {@code DATA_TYPE_*} constants defined in {@link C}. For
   * reporting only.
   */
  public final int type;
  /**
   * One of the {@link FormatEvaluator} {@code TRIGGER_*} constants if a format evaluation was
   * performed to determine that this chunk should be loaded.
   * {@link FormatEvaluator#TRIGGER_UNKNOWN} otherwise.
   */
  public final int formatEvaluatorTrigger;
  /**
   * Optional data set by a {@link FormatEvaluator} if a format evaluation was performed to
   * determine that this chunk should be loaded. Null otherwise.
   */
  public final Object formatEvaluatorData;
  /**
   * The format associated with the data being loaded, or null if the data being loaded is not
   * associated with a specific format.
   */
  public final Format format;
  /**
   * The start time of the media contained by the chunk, or {@link C#UNSET_TIME_US} if the data
   * being loaded does not contain media samples.
   */
  public final long startTimeUs;
  /**
   * The end time of the media contained by the chunk, or {@link C#UNSET_TIME_US} if the data being
   * loaded does not contain media samples.
   */
  public final long endTimeUs;

  protected final DataSource dataSource;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param type See {@link #type}.
   * @param format See {@link #format}.
   * @param formatEvaluatorTrigger See {@link #formatEvaluatorTrigger}.
   * @param formatEvaluatorData See {@link #formatEvaluatorData}.
   * @param startTimeUs See {@link #startTimeUs}.
   * @param endTimeUs See {@link #endTimeUs}.
   */
  public Chunk(DataSource dataSource, DataSpec dataSpec, int type, Format format,
      int formatEvaluatorTrigger, Object formatEvaluatorData, long startTimeUs, long endTimeUs) {
    this.dataSource = Assertions.checkNotNull(dataSource);
    this.dataSpec = Assertions.checkNotNull(dataSpec);
    this.type = type;
    this.format = format;
    this.formatEvaluatorTrigger = formatEvaluatorTrigger;
    this.formatEvaluatorData = formatEvaluatorData;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
  }

  /**
   * Gets the number of bytes that have been loaded.
   *
   * @return The number of bytes that have been loaded.
   */
  public abstract long bytesLoaded();

}
