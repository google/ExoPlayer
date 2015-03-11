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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * Facilitates extraction of media samples for HLS playbacks.
 */
public interface HlsExtractor {

  /**
   * An object to which extracted data should be output.
   */
  public interface ExtractorOutput {

    /**
     * Obtains a {@link TrackOutput} to which extracted data should be output for a given track.
     *
     * @param trackId A stable track id.
     * @return The corresponding {@link TrackOutput}.
     */
    TrackOutput getTrackOutput(int trackId);

  }

  /**
   * An object to which extracted data belonging to a given track should be output.
   */
  public interface TrackOutput {

    int appendData(DataSource dataSource, int length) throws IOException;

    void appendData(ParsableByteArray data, int length);

    void startSample(long timeUs, int offset);

    void commitSample(int flags, int offset, byte[] encryptionKey);

    boolean isWritingSample();

  }

  /**
   * Initializes the extractor.
   *
   * @param output An {@link ExtractorOutput} to which extracted data should be output.
   */
  void init(ExtractorOutput output);

  /**
   * Whether the extractor is prepared.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  boolean isPrepared();

  /**
   * Gets the number of available tracks.
   * <p>
   * This method should only be called after the extractor has been prepared.
   *
   * @return The number of available tracks.
   */
  int getTrackCount();

  /**
   * Gets the format of the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track index.
   * @return The corresponding format.
   */
  MediaFormat getFormat(int track);

  /**
   * Reads up to a single TS packet.
   *
   * @param dataSource The {@link DataSource} from which to read.
   * @throws IOException If an error occurred reading from the source.
   * @return The number of bytes read from the source.
   */
  int read(DataSource dataSource) throws IOException;

}
