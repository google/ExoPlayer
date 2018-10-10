/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.net.InetAddress;

/**
 * A component to which streams of data can be written.
 */
public interface UdpDataSink {

  /**
   * A factory for {@link UdpDataSink} instances.
   */
  interface Factory {

    /**
     * Creates a {@link UdpDataSink} instance.
     */
    UdpDataSink createUdpDataSink();

  }

  /**
   * Opens the sink to write the specified data.
   * <p>
   * Note: If an {@link IOException} is thrown, callers must still call {@link #close()} to ensure
   * that any partial effects of the invocation are cleaned up.
   *
   * @param dataSpec Defines the data to be write.
   * @throws IOException If an error occurs opening the sink.
   * @return {@link C#LENGTH_UNSET}) for support compatibility with {@link DataSource#open(DataSpec)}
   */
  long open(DataSpec dataSpec) throws IOException;

  /**
   * Consumes the provided data.
   *
   * @param buffer The buffer from which data should be consumed.
   * @param offset The offset of the data to consume in {@code buffer}.
   * @param length The length of the data to consume, in bytes.
   * @throws IOException If an error occurs writing to the sink.
   */
  void write(byte[] buffer, int offset, int length) throws IOException;

  /**
   * Consumes the provided data for address/port specific.
   *
   * @param buffer The buffer from which data should be consumed.
   * @param offset The offset of the data to consume in {@code buffer}.
   * @param length The length of the data to consume, in bytes.
   * @param address The address to send the data to.
   * @param port The port to send the data to.
   * @throws IOException If an error occurs writing to the sink.
   */
  void writeTo(byte[] buffer, int offset, int length, InetAddress address, int port)
      throws IOException;


  /**
   * Get the local port.
   *
   * @return The local port of the opened sink or {@link C#PORT_UNSET} if the sink isn't opened.
   */
  int getLocalPort();

  /**
   * Closes the sink.
   * <p>
   * Note: This method must be called even if the corresponding call to {@link #open(DataSpec)}
   * threw an {@link IOException}. See {@link #open(DataSpec)} for more details.
   *
   * @throws IOException If an error occurs closing the sink.
   */
  void close() throws IOException;
}
