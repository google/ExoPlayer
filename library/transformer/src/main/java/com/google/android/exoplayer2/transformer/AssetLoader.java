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

import android.content.Context;
import android.os.Looper;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Clock;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Provides media data to a {@linkplain Transformer}.
 *
 * <p>The output samples can be encoded or decoded.
 *
 * <p>Only audio and video samples are supported. Both audio and video tracks can be provided by a
 * single asset loader, but outputting multiple tracks of the same type is not supported.
 */
public interface AssetLoader {

  /**
   * A factory for {@link AssetLoader} instances.
   *
   * <p>The setters in this interface will be called with the values used to build and start the
   * {@link Transformer}.
   */
  interface Factory {

    /** Sets the {@link Context}. */
    @CanIgnoreReturnValue
    Factory setContext(Context context);

    /** Sets the {@link MediaItem} to load. */
    @CanIgnoreReturnValue
    Factory setMediaItem(MediaItem mediaItem);

    /**
     * Sets whether to remove the audio samples from the output (if any).
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     */
    @CanIgnoreReturnValue
    Factory setRemoveAudio(boolean removeAudio);

    /**
     * Sets whether to remove the video samples from the output (if any).
     *
     * <p>The audio and video cannot both be removed because the output would not contain any
     * samples.
     */
    @CanIgnoreReturnValue
    Factory setRemoveVideo(boolean removeVideo);

    /**
     * Sets whether the video samples should be flattened prior to decoding for media containing
     * slow motion markers.
     *
     * <p>The audio samples are flattened after they are output by the {@link AssetLoader}, because
     * this is done on decoded samples.
     *
     * <p>For more information on slow motion flattening, see {@link
     * TransformationRequest.Builder#setFlattenForSlowMotion(boolean)}.
     */
    @CanIgnoreReturnValue
    Factory setFlattenVideoForSlowMotion(boolean flattenVideoForSlowMotion);

    /** Sets the {@link Codec.DecoderFactory} to be used to decode the samples (if necessary). */
    @CanIgnoreReturnValue
    Factory setDecoderFactory(Codec.DecoderFactory decoderFactory);

    /**
     * Sets the {@link Looper} that's used to access the {@link AssetLoader} after it's been
     * created.
     */
    @CanIgnoreReturnValue
    Factory setLooper(Looper looper);

    /** Sets the {@link Listener} on which the {@link AssetLoader} should notify of events. */
    @CanIgnoreReturnValue
    Factory setListener(AssetLoader.Listener listener);

    /**
     * The {@link Clock} to use.
     *
     * <p>Should always be {@link Clock#DEFAULT} except for testing.
     */
    @CanIgnoreReturnValue
    Factory setClock(Clock clock);

    /**
     * Creates an {@link AssetLoader} instance. All the setters in this factory must be called
     * before creating the {@link AssetLoader}.
     */
    AssetLoader createAssetLoader();
  }

  /**
   * A listener of {@link AssetLoader} events.
   *
   * <p>This listener is typically used in the following way:
   *
   * <ul>
   *   <li>{@linkplain #onDurationUs(long)} Report} the duration of the input media.
   *   <li>{@linkplain #onTrackRegistered() Register} each output track.
   *   <li>{@linkplain #onAllTracksRegistered() Signal} that all the tracks have been registered.
   *   <li>{@linkplain #onTrackAdded(Format, long, long) Add} the information for each track.
   * </ul>
   *
   * <p>This listener can be called from any thread.
   */
  interface Listener {

    /** Called when the duration of the input media is known. */
    void onDurationUs(long durationUs);

    /**
     * Called to register a single output track of sample data.
     *
     * <p>Must be called for each track that will be output.
     *
     * <p>Must be called on the same thread as {@link #onTrackAdded(Format, long, long)}.
     */
    void onTrackRegistered();

    /** Called when all the tracks have been {@linkplain #onTrackRegistered() registered}. */
    void onAllTracksRegistered();

    /**
     * Called when the information on a {@linkplain #onTrackRegistered() registered} track is known.
     *
     * <p>Must be called after the duration has been {@linkplain #onDurationUs(long) reported} and
     * all the tracks have been {@linkplain #onAllTracksRegistered registered}.
     *
     * <p>Must be called on the same thread as {@link #onTrackRegistered()}.
     *
     * @param format The {@link Format} of the input media (prior to video slow motion flattening or
     *     to decoding).
     * @param streamStartPositionUs The start position of the stream (offset by {@code
     *     streamOffsetUs}), in microseconds.
     * @param streamOffsetUs The offset that will be added to the timestamps to make sure they are
     *     non-negative, in microseconds.
     * @return The {@link SamplePipeline.Input} describing the type of sample data expected, and to
     *     which to pass this data.
     * @throws TransformationException If an error occurs configuring the {@link
     *     SamplePipeline.Input}.
     */
    SamplePipeline.Input onTrackAdded(
        Format format, long streamStartPositionUs, long streamOffsetUs)
        throws TransformationException;

    /**
     * Called if an error occurs in the asset loader. In this case, the asset loader will be
     * {@linkplain #release() released} automatically.
     */
    void onError(Exception e);
  }

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

  /** Stops loading data and releases all resources associated with the asset loader. */
  void release();
}
