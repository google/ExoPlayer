/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.transformer;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/**
 * Abstracts media muxing operations.
 *
 * <p>Query whether {@linkplain Factory#getSupportedSampleMimeTypes(int) sample MIME types} are
 * supported and {@linkplain #addTrack(Format) add all tracks}, then {@linkplain #writeSampleData
 * write sample data} to mux samples. Once any sample data has been written, it is not possible to
 * add tracks. After writing all sample data, {@linkplain #release(boolean) release} the instance to
 * finish writing to the output and return any resources to the system.
 */
@UnstableApi
public interface Muxer {

  /** Thrown when a muxing failure occurs. */
  final class MuxerException extends Exception {
    /**
     * Creates an instance.
     *
     * @param message See {@link #getMessage()}.
     * @param cause See {@link #getCause()}.
     */
    public MuxerException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Factory for muxers. */
  interface Factory {
    /**
     * Returns a new muxer writing to a file.
     *
     * @param path The path to the output file.
     * @throws IllegalArgumentException If the path is invalid.
     * @throws MuxerException If an error occurs opening the output file for writing.
     */
    Muxer create(String path) throws MuxerException;

    /**
     * Returns the supported sample {@linkplain MimeTypes MIME types} for the given {@link
     * C.TrackType}.
     */
    ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType);
  }

  /**
   * Adds a track with the specified format.
   *
   * @param format The {@link Format} of the track.
   * @return The index for this track, which should be passed to {@link #writeSampleData}.
   * @throws MuxerException If the muxer encounters a problem while adding the track.
   */
  int addTrack(Format format) throws MuxerException;

  /**
   * Writes the specified sample.
   *
   * @param trackIndex The index of the track, previously returned by {@link #addTrack(Format)}.
   * @param data A buffer containing the sample data to write to the container.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @param flags The {@link C.BufferFlags} associated with the data. Only {@link
   *     C#BUFFER_FLAG_KEY_FRAME} and {@link C#BUFFER_FLAG_END_OF_STREAM} are supported.
   * @throws MuxerException If the muxer fails to write the sample.
   */
  void writeSampleData(
      int trackIndex, ByteBuffer data, long presentationTimeUs, @C.BufferFlags int flags)
      throws MuxerException;

  /** Adds {@link Metadata} about the output file. */
  void addMetadata(Metadata metadata);

  /**
   * Finishes writing the output and releases any resources associated with muxing.
   *
   * <p>The muxer cannot be used anymore once this method has been called.
   *
   * @param forCancellation Whether the reason for releasing the resources is the export
   *     cancellation.
   * @throws MuxerException If the muxer fails to finish writing the output and {@code
   *     forCancellation} is false.
   */
  void release(boolean forCancellation) throws MuxerException;

  /**
   * Returns the maximum delay allowed between output samples, in milliseconds, or {@link
   * C#TIME_UNSET} if there is no maximum.
   *
   * <p>This is the maximum delay between samples of any track. They can be of the same or of
   * different track types.
   *
   * <p>This value is used to abort the export when the maximum delay is reached. Note that there is
   * no guarantee that the export will be aborted exactly at that time.
   */
  long getMaxDelayBetweenSamplesMs();
}
