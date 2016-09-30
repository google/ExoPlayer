/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Extracts individual samples from an elementary media stream, preserving original order.
 */
public abstract class ElementaryStreamReader {

  /**
   * Factory of {@link ElementaryStreamReader} instances.
   */
  public interface Factory {

    /**
     * Returns an {@link ElementaryStreamReader} for a given PMT entry. May return null if the
     * stream type is not supported or if the stream already has a reader assigned to it.
     *
     * @param pid The pid for the PMT entry.
     * @param streamType One of the {@link TsExtractor}{@code .TS_STREAM_TYPE_*} constants defining
     *     the type of the stream.
     * @param esInfo The descriptor information linked to the elementary stream.
     * @param output The {@link ExtractorOutput} that provides the {@link TrackOutput}s for the
     *     created readers.
     * @return An {@link ElementaryStreamReader} for the elementary streams carried by the provided
     *     pid. {@code null} if the stream is not supported or if it should be ignored.
     */
    ElementaryStreamReader onPmtEntry(int pid, int streamType, EsInfo esInfo,
        ExtractorOutput output);

  }

  /**
   * Holds descriptor information associated with an elementary stream.
   */
  public static final class EsInfo {

    public final int streamType;
    public String language;
    public byte[] descriptorBytes;

    /**
     * @param streamType The type of the stream as defined by the
     *     {@link TsExtractor}{@code .TS_STREAM_TYPE_*}.
     * @param language The language of the stream, as defined by ISO/IEC 13818-1, section 2.6.18.
     * @param descriptorBytes The descriptor bytes associated to the stream.
     */
    public EsInfo(int streamType, String language, byte[] descriptorBytes) {
      this.streamType = streamType;
      this.language = language;
      this.descriptorBytes = descriptorBytes;
    }

  }

  protected final TrackOutput output;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected ElementaryStreamReader(TrackOutput output) {
    this.output = output;
  }

  /**
   * Notifies the reader that a seek has occurred.
   */
  public abstract void seek();

  /**
   * Called when a packet starts.
   *
   * @param pesTimeUs The timestamp associated with the packet.
   * @param dataAlignmentIndicator The data alignment indicator associated with the packet.
   */
  public abstract void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator);

  /**
   * Consumes (possibly partial) data from the current packet.
   *
   * @param data The data to consume.
   */
  public abstract void consume(ParsableByteArray data);

  /**
   * Called when a packet ends.
   */
  public abstract void packetFinished();

}
