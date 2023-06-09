/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.extractor.text;

import androidx.annotation.Nullable;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

/**
 * Parses subtitle data into timed {@linkplain CueGroup cue groups}.
 *
 * <p>Instances are stateful, so samples can be fed in repeated calls to {@link #parse(byte[])}, and
 * one or more complete {@link CueGroup} instances will be returned when enough data has been
 * received. Due to this stateful-ness, {@link #reset()} must be called after a seek or similar
 * discontinuity in the source data.
 */
@UnstableApi
public interface SubtitleParser {

  /**
   * Parses {@code data} (and any data stored from previous invocations) and returns any resulting
   * complete {@link CueGroup} instances.
   *
   * <p>Equivalent to {@link #parse(byte[], int, int) parse(data, 0, data.length)}.
   */
  @Nullable
  default List<CueGroup> parse(byte[] data) {
    return parse(data, /* offset= */ 0, data.length);
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and returns any resulting
   * complete {@link CueGroup} instances.
   *
   * <p>Any samples not used from {@code data} will be persisted and used during subsequent calls to
   * this method.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   * @return The {@linkplain CueGroup cue groups} parsed from {@code data} (and possibly previous
   *     provided samples too), sorted in ascending order by {@link CueGroup#presentationTimeUs}.
   *     Otherwise null if there is insufficient data to generate a complete {@link CueGroup}.
   */
  @Nullable
  List<CueGroup> parse(byte[] data, int offset, int length);

  /**
   * Clears any data stored inside this parser from previous {@link #parse(byte[])} calls.
   *
   * <p>This must be called after a seek or other similar discontinuity in the source data.
   */
  void reset();
}
