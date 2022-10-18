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

import android.os.ParcelFileDescriptor;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Abstracts media muxing operations.
 *
 * <p>Query whether {@linkplain Factory#getSupportedSampleMimeTypes(int)} sample MIME types} are
 * supported and {@linkplain #addTrack(Format) add all tracks}, then {@linkplain
 * #writeSampleData(int, ByteBuffer, boolean, long) write sample data} to mux samples. Once any
 * sample data has been written, it is not possible to add tracks. After writing all sample data,
 * {@linkplain #release(boolean) release} the instance to finish writing to the output and return
 * any resources to the system.
 */
/* package */ interface Muxer {

  /** Thrown when a muxing failure occurs. */
  /* package */ final class MuxerException extends Exception {
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
     * @throws IOException If an error occurs opening the output file for writing.
     */
    Muxer create(String path) throws IOException;

    /**
     * Returns a new muxer writing to a file descriptor.
     *
     * @param parcelFileDescriptor A readable and writable {@link ParcelFileDescriptor} of the
     *     output. The file referenced by this ParcelFileDescriptor should not be used before the
     *     muxer is released. It is the responsibility of the caller to close the
     *     ParcelFileDescriptor. This can be done after this method returns.
     * @throws IllegalArgumentException If the file descriptor is invalid.
     * @throws IOException If an error occurs opening the output file descriptor for writing.
     */
    Muxer create(ParcelFileDescriptor parcelFileDescriptor) throws IOException;

    /**
     * Returns the supported sample {@linkplain MimeTypes MIME types} for the given {@link
     * C.TrackType}.
     */
    ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType);
  }

  /**
   * Adds a track with the specified format, and returns its index (to be passed in subsequent calls
   * to {@link #writeSampleData(int, ByteBuffer, boolean, long)}).
   *
   * @param format The {@link Format} of the track.
   * @throws MuxerException If the muxer encounters a problem while adding the track.
   */
  int addTrack(Format format) throws MuxerException;

  /**
   * Writes the specified sample.
   *
   * @param trackIndex The index of the track, previously returned by {@link #addTrack(Format)}.
   * @param data Buffer containing the sample data to write to the container.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @throws MuxerException If the muxer fails to write the sample.
   */
  void writeSampleData(int trackIndex, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException;

  /**
   * Releases any resources associated with muxing.
   *
   * @param forCancellation Whether the reason for releasing the resources is the transformation
   *     cancellation.
   * @throws MuxerException If the muxer fails to stop or release resources and {@code
   *     forCancellation} is false.
   */
  void release(boolean forCancellation) throws MuxerException;
}
