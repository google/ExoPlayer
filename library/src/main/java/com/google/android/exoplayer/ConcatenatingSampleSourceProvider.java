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

/**
 * A {@link SampleSourceProvider} that concatenates multiple {@link SampleSourceProvider}s.
 */
public final class ConcatenatingSampleSourceProvider implements SampleSourceProvider {

  private final SampleSourceProvider[] sampleSourceProviders;

  /**
   * @param sampleSourcePoviders The {@link SampleSourceProvider}s to concatenate.
   */
  public ConcatenatingSampleSourceProvider(SampleSourceProvider... sampleSourcePoviders) {
    this.sampleSourceProviders = sampleSourcePoviders;
  }

  @Override
  public int getSourceCount() {
    int sourceCount = 0;
    for (SampleSourceProvider sampleSourceProvider : sampleSourceProviders) {
      int count = sampleSourceProvider.getSourceCount();
      if (count == SampleSourceProvider.UNKNOWN_SOURCE_COUNT) {
        return UNKNOWN_SOURCE_COUNT;
      }
      sourceCount += count;
    }
    return sourceCount;
  }

  @Override
  public SampleSource createSource(int index) {
    int sourceCount = 0;
    for (SampleSourceProvider sampleSourceProvider : sampleSourceProviders) {
      int count = sampleSourceProvider.getSourceCount();
      if (count == SampleSourceProvider.UNKNOWN_SOURCE_COUNT || index < sourceCount + count) {
        return sampleSourceProvider.createSource(index - sourceCount);
      }
      sourceCount += count;
    }
    throw new IndexOutOfBoundsException();
  }

}
