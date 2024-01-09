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

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Format.CueReplacementBehavior;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;

/**
 * Parses subtitle data into timed {@linkplain CuesWithTiming} instances.
 *
 * <p>Instances are stateful, so samples can be fed in repeated calls to {@link #parse}, and one or
 * more complete {@link CuesWithTiming} instances will be returned when enough data has been
 * received. Due to this stateful-ness, {@link #reset()} must be called after a seek or similar
 * discontinuity in the source data.
 */
@UnstableApi
public interface SubtitleParser {

  /** Factory for {@link SubtitleParser} instances. */
  interface Factory {

    /** A subtitle parser factory that supports no formats. */
    public static final Factory UNSUPPORTED =
        new Factory() {
          @Override
          public boolean supportsFormat(Format format) {
            return false;
          }

          @Override
          public @CueReplacementBehavior int getCueReplacementBehavior(Format format) {
            return Format.CUE_REPLACEMENT_BEHAVIOR_MERGE;
          }

          @Override
          public SubtitleParser create(Format format) {
            throw new IllegalStateException(
                "This SubtitleParser.Factory doesn't support any formats.");
          }
        };

    /**
     * Returns whether the factory is able to instantiate a {@link SubtitleParser} for the given
     * {@link Format}.
     *
     * @param format The {@link Format}.
     * @return Whether the factory can instantiate a suitable {@link SubtitleParser}.
     */
    boolean supportsFormat(Format format);

    /**
     * Returns the {@link CueReplacementBehavior} of the {@link SubtitleParser} implementation that
     * handles {@code format}.
     *
     * @return The replacement behavior.
     * @throws IllegalArgumentException if {@code format} is {@linkplain #supportsFormat(Format) not
     *     supported} by this factory.
     */
    @CueReplacementBehavior
    int getCueReplacementBehavior(Format format);

    /**
     * Creates a {@link SubtitleParser} for the given {@link Format}.
     *
     * @return The {@link SubtitleParser} instance.
     * @throws IllegalArgumentException if {@code format} is {@linkplain #supportsFormat(Format) not
     *     supported} by this factory.
     */
    SubtitleParser create(Format format);
  }

  /**
   * Options to control the output behavior of {@link SubtitleParser} methods that emit their output
   * incrementally using a {@link Consumer} provided by the caller.
   */
  class OutputOptions {

    private static final OutputOptions ALL =
        new OutputOptions(C.TIME_UNSET, /* outputAllCues= */ false);

    /**
     * Cues after this time (inclusive) will be emitted first. Cues before this time might be
     * emitted later, depending on {@link #outputAllCues}. Can be {@link C#TIME_UNSET} to emit all
     * cues.
     */
    public final long startTimeUs;

    /**
     * Whether to eventually emit all cues, or only those after {@link #startTimeUs}. Ignored if
     * {@link #startTimeUs} is not set.
     */
    public final boolean outputAllCues;

    private OutputOptions(long startTimeUs, boolean outputAllCues) {
      this.startTimeUs = startTimeUs;
      this.outputAllCues = outputAllCues;
    }

    /** Output all {@link CuesWithTiming} instances. */
    public static OutputOptions allCues() {
      return ALL;
    }

    /**
     * Only output {@link CuesWithTiming} instances where {@link CuesWithTiming#startTimeUs} is at
     * least {@code startTimeUs}.
     *
     * <p>The order in which {@link CuesWithTiming} instances are emitted is not defined.
     */
    public static OutputOptions onlyCuesAfter(long startTimeUs) {
      return new OutputOptions(startTimeUs, /* outputAllCues= */ false);
    }

    /**
     * Output {@link CuesWithTiming} where {@link CuesWithTiming#startTimeUs} is at least {@code
     * startTimeUs}, followed by the remaining {@link CuesWithTiming} instances.
     *
     * <p>Beyond this, the order in which {@link CuesWithTiming} instances are emitted is not
     * defined.
     */
    public static OutputOptions cuesAfterThenRemainingCuesBefore(long startTimeUs) {
      return new OutputOptions(startTimeUs, /* outputAllCues= */ true);
    }
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and emits resulting {@link
   * CuesWithTiming} instances.
   *
   * <p>Equivalent to {@link #parse(byte[], int, int, OutputOptions, Consumer) parse(data, 0,
   * data.length, outputOptions, output)}.
   */
  default void parse(byte[] data, OutputOptions outputOptions, Consumer<CuesWithTiming> output) {
    parse(data, /* offset= */ 0, data.length, outputOptions, output);
  }

  /**
   * Parses {@code data} (and any data stored from previous invocations) and emits any resulting
   * complete {@link CuesWithTiming} instances via {@code output}.
   *
   * <p>Any samples not used from {@code data} will be persisted and used during subsequent calls to
   * this method.
   *
   * <p>{@link CuesWithTiming#startTimeUs} in an emitted instance is derived only from the provided
   * sample data, so has to be considered together with any relevant {@link
   * Format#subsampleOffsetUs}. If the provided sample doesn't contain any timing information then
   * at most one {@link CuesWithTiming} instance will be emitted, with {@link
   * CuesWithTiming#startTimeUs} set to {@link C#TIME_UNSET}, in which case {@link
   * Format#subsampleOffsetUs} <b>must</b> be {@link Format#OFFSET_SAMPLE_RELATIVE}.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   * @param outputOptions Options to control how instances are emitted to {@code output}.
   * @param output A consumer for {@link CuesWithTiming} instances emitted by this method. All calls
   *     will be made on the thread that called this method, and will be completed before this
   *     method returns.
   */
  void parse(
      byte[] data,
      int offset,
      int length,
      OutputOptions outputOptions,
      Consumer<CuesWithTiming> output);

  /**
   * Parses {@code data} to a legacy {@link Subtitle} instance.
   *
   * <p>This method only exists temporarily to support the transition away from {@link
   * SubtitleDecoder} and {@link Subtitle}. It will be removed in a future release.
   *
   * <p>The default implementation delegates to {@link #parse(byte[], int, int, OutputOptions,
   * Consumer)}. Implementations can override this to provide a more efficient implementation if
   * desired.
   *
   * @param data The subtitle data to parse. This must contain only complete samples. For subtitles
   *     muxed inside a media container, a sample is usually defined by the container. For subtitles
   *     read from a text file, a sample is usually the entire contents of the text file.
   * @param offset The index in {@code data} to start reading from (inclusive).
   * @param length The number of bytes to read from {@code data}.
   */
  default Subtitle parseToLegacySubtitle(byte[] data, int offset, int length) {
    ImmutableList.Builder<CuesWithTiming> cuesWithTimingList = ImmutableList.builder();
    parse(data, offset, length, OutputOptions.ALL, cuesWithTimingList::add);
    return new CuesWithTimingSubtitle(cuesWithTimingList.build());
  }

  /**
   * Clears any data stored inside this parser from previous {@link #parse} calls.
   *
   * <p>This must be called after a seek or other similar discontinuity in the source data.
   *
   * <p>The default implementation is a no-op.
   */
  default void reset() {}

  /**
   * Returns the {@link CueReplacementBehavior} for consecutive {@link CuesWithTiming} emitted by
   * this implementation.
   *
   * <p>A given instance must always return the same value from this method.
   */
  @CueReplacementBehavior
  int getCueReplacementBehavior();
}
