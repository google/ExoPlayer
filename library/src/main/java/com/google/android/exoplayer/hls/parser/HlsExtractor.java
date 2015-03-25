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
   * An object from which source data can be read.
   */
  public interface ExtractorInput {

    /**
     * Reads up to {@code length} bytes from the input.
     * <p>
     * This method blocks until at least one byte of data can be read, the end of the input is
     * detected, or an exception is thrown.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The maximum number of bytes to read from the input.
     * @return The number of bytes read, or -1 if the input has ended.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread has been interrupted.
     */
    int read(byte[] target, int offset, int length) throws IOException, InterruptedException;

    /**
     * Like {@link #read(byte[], int, int)}, but guaranteed to read request {@code length} in full
     * unless the end of the input is detected, or an exception is thrown.
     *
     * TODO: Firm up behavior of this method if (a) zero bytes are read before EOS, (b) the read
     * is partially satisfied before EOS.
     *
     * @param target A target array into which data should be written.
     * @param offset The offset into the target array at which to write.
     * @param length The number of bytes to read from the input.
     * @return True if the read was successful. False if the end of the input was reached.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread has been interrupted.
     */
    boolean readFully(byte[] target, int offset, int length)
        throws IOException, InterruptedException;

    /**
     * Like {@link #readFully(byte[], int, int)}, except the data is skipped instead of read.
     *
     * TODO: Firm up behavior of this method if (a) zero bytes are skipped before EOS, (b) the skip
     * is partially satisfied before EOS.
     *
     * @param length The number of bytes to skip from the input.
     * @return True if the read was successful. False if the end of the input was reached.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread is interrupted.
     */
    boolean skipFully(int length) throws IOException, InterruptedException;

    /**
     * The current position in the stream.
     *
     * @return The position in the stream.
     */
    long getPosition();

    /**
     * Whether or not the input has ended.
     *
     * @return True if the input has ended. False otherwise.
     */
    boolean isEnded();

  }

  /**
   * An object to which extracted data should be output.
   */
  public interface TrackOutputBuilder {

    /**
     * Invoked to build a {@link TrackOutput} to which data should be output for a given track.
     *
     * @param trackId A stable track id.
     * @return The corresponding {@link TrackOutput}.
     */
    TrackOutput buildOutput(int trackId);

    /**
     * Invoked when all {@link TrackOutput}s have been built, meaning {@link #buildOutput(int)}
     * will not be invoked again.
     */
    void allOutputsBuilt();

  }

  /**
   * An object to which extracted data belonging to a given track should be output.
   */
  public interface TrackOutput {

    boolean hasFormat();

    void setFormat(MediaFormat format);

    boolean isWritingSample();

    int appendData(DataSource dataSource, int length) throws IOException;

    void appendData(ParsableByteArray data, int length);

    void startSample(long timeUs, int offset);

    void commitSample(int flags, int offset, byte[] encryptionKey);

  }

  /**
   * Initializes the extractor.
   *
   * @param output A {@link TrackOutputBuilder} to which extracted data should be output.
   */
  void init(TrackOutputBuilder output);

  /**
   * Reads from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  void read(ExtractorInput input) throws IOException, InterruptedException;

}
