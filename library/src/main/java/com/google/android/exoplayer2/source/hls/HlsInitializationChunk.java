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
package com.google.android.exoplayer2.source.hls;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

/**
 * An HLS initialization chunk. Provides the extractor with information required for extracting the
 * samples.
 */
/* package */ final class HlsInitializationChunk extends Chunk {

  public final Format format;

  public final Extractor extractor;

  private int bytesLoaded;
  private volatile boolean loadCanceled;

  public HlsInitializationChunk(DataSource dataSource, DataSpec dataSpec, int trackSelectionReason,
      Object trackSelectionData, Extractor extractor, Format format) {
    super(dataSource, dataSpec, C.TRACK_TYPE_DEFAULT, null, trackSelectionReason,
        trackSelectionData, C.TIME_UNSET, C.TIME_UNSET);
    this.extractor = extractor;
    this.format = format;
  }

  /**
   * Sets the {@link HlsSampleStreamWrapper} that will receive the sample format information from
   * the initialization chunk.
   *
   * @param output The output that will receive the format information.
   */
  public void init(HlsSampleStreamWrapper output) {
    extractor.init(output);
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
  }

}
