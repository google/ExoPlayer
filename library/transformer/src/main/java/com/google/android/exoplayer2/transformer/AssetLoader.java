/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides media data to a {@linkplain Transformer}.
 *
 * <p>The output samples can be encoded or decoded.
 *
 * <p>Only audio and video samples are supported. Both audio and video tracks can be provided by a
 * single asset loader, but outputting multiple tracks of the same type is not supported.
 *
 * <p>An asset loader is responsible for {@linkplain EditedMediaItem.Builder#setRemoveAudio(boolean)
 * removing audio} or {@linkplain EditedMediaItem.Builder#setRemoveVideo(boolean) video} if
 * requested.
 *
 * <p>If {@linkplain EditedMediaItem.Builder#setFlattenForSlowMotion(boolean) slow motion
 * flattening} is requested, the asset loader should flatten the video track for media containing
 * slow motion markers. This is usually done prior to decoding. The audio samples are flattened
 * after they are output by the {@link AssetLoader}, because this is done on decoded samples.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public interface AssetLoader {

  /** A factory for {@link AssetLoader} instances. */
  interface Factory {

    /**
     * Creates an {@link AssetLoader} instance.
     *
     * @param editedMediaItem The {@link EditedMediaItem} to load.
     * @param looper The {@link Looper} that's used to access the {@link AssetLoader} after it's
     *     been created.
     * @param listener The {@link Listener} on which the {@link AssetLoader} should notify of
     *     events.
     * @return An {@link AssetLoader}.
     */
    AssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem, Looper looper, Listener listener);
  }

  /** A listener of {@link AssetLoader} events. */
  interface Listener {

    /**
     * Called when the duration of the input media is known.
     *
     * <p>Can be called from any thread.
     */
    void onDurationUs(long durationUs);

    /**
     * Called when the number of tracks being output is known.
     *
     * <p>Can be called from any thread.
     */
    void onTrackCount(@IntRange(from = 1) int trackCount);

    /**
     * Called when the information on a track is known.
     *
     * <p>Must be called after the {@linkplain #onDurationUs(long) duration} and the {@linkplain
     * #onTrackCount(int) track count} have been reported.
     *
     * <p>Must be called once per {@linkplain #onTrackCount(int) declared} track.
     *
     * <p>Must be called from the thread that will be used to call {@link #onOutputFormat(Format)}.
     *
     * @param inputFormat The {@link Format} of samples that will be input to the {@link
     *     AssetLoader} (prior to video slow motion flattening or to decoding).
     * @param supportedOutputTypes The output {@linkplain SupportedOutputTypes types} supported by
     *     this {@link AssetLoader} for the track added. At least one output type must be supported.
     * @return Whether the {@link AssetLoader} needs to provide decoded data to the {@link
     *     SampleConsumer}.
     */
    boolean onTrackAdded(Format inputFormat, @SupportedOutputTypes int supportedOutputTypes);

    /**
     * Called when the {@link Format} of samples that will be output by the {@link AssetLoader} is
     * known.
     *
     * <p>Must be called after the corresponding track has been {@link #onTrackAdded added}.
     *
     * <p>For each {@link #onTrackAdded added} track, this method must be called regularly until the
     * returned {@link SampleConsumer} is non-null.
     *
     * <p>Must be called from the thread that will be used to call the returned {@link
     * SampleConsumer}'s methods. This thread must be the same for all formats output, and is
     * generally different from the one used to access the {@link AssetLoader} methods.
     *
     * @param format The {@link Format} of samples that will be output.
     * @return The {@link SampleConsumer} of samples of the given {@link Format}, or {@code null} if
     *     it could not be retrieved yet.
     * @throws ExportException If an error occurs configuring the {@link SampleConsumer}.
     */
    @Nullable
    SampleConsumer onOutputFormat(Format format) throws ExportException;

    /**
     * Called if an error occurs in the asset loader. In this case, the asset loader will be
     * {@linkplain #release() released} automatically.
     *
     * <p>Can be called from any thread.
     */
    void onError(ExportException exportException);
  }

  /**
   * Supported output types of an asset loader. Possible flag values are {@link
   * #SUPPORTED_OUTPUT_TYPE_ENCODED} and {@link #SUPPORTED_OUTPUT_TYPE_DECODED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        SUPPORTED_OUTPUT_TYPE_ENCODED,
        SUPPORTED_OUTPUT_TYPE_DECODED,
      })
  @interface SupportedOutputTypes {}
  /** Indicates that the asset loader can output encoded samples. */
  int SUPPORTED_OUTPUT_TYPE_ENCODED = 1;
  /** Indicates that the asset loader can output decoded samples. */
  int SUPPORTED_OUTPUT_TYPE_DECODED = 1 << 1;

  /** Starts the asset loader. */
  void start();

  /**
   * Returns the current {@link Transformer.ProgressState} and updates {@code progressHolder} with
   * the current progress if it is {@link Transformer#PROGRESS_STATE_AVAILABLE available}.
   *
   * @param progressHolder A {@link ProgressHolder}, updated to hold the percentage progress if
   *     {@link Transformer#PROGRESS_STATE_AVAILABLE available}.
   * @return The {@link Transformer.ProgressState}.
   */
  @Transformer.ProgressState
  int getProgress(ProgressHolder progressHolder);

  /**
   * Return the used decoders' names.
   *
   * @return The decoders' names keyed by {@linkplain com.google.android.exoplayer2.C.TrackType
   *     track type}.
   */
  ImmutableMap<Integer, String> getDecoderNames();

  /** Stops loading data and releases all resources associated with the asset loader. */
  void release();
}
