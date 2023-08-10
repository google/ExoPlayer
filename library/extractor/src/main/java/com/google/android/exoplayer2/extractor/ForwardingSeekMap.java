/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

/**
 * A forwarding class for {@link SeekMap}
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class ForwardingSeekMap implements SeekMap {
  private final SeekMap seekMap;

  /**
   * Creates a instance.
   *
   * @param seekMap The original {@link SeekMap}.
   */
  public ForwardingSeekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  @Override
  public boolean isSeekable() {
    return seekMap.isSeekable();
  }

  @Override
  public long getDurationUs() {
    return seekMap.getDurationUs();
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return seekMap.getSeekPoints(timeUs);
  }
}
