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
     * @param streamType Stream type value as defined in the PMT entry or associated descriptors.
     * @param esInfo Information associated to the elementary stream provided in the PMT.
     * @return An {@link ElementaryStreamReader} for the elementary streams carried by the provided
     *     pid. {@code null} if the stream is not supported or if it should be ignored.
     */
    ElementaryStreamReader createStreamReader(int streamType, EsInfo esInfo);

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

  /**
   * Generates track ids for initializing {@link ElementaryStreamReader}s' {@link TrackOutput}s.
   */
  public static final class TrackIdGenerator {

    private final int firstId;
    private final int idIncrement;
    private int generatedIdCount;

    public TrackIdGenerator(int firstId, int idIncrement) {
      this.firstId = firstId;
      this.idIncrement = idIncrement;
    }

    public int getNextId() {
      return firstId + idIncrement * generatedIdCount++;
    }

  }

  /**
   * Notifies the reader that a seek has occurred.
   */
  public abstract void seek();

  /**
   * Initializes the reader by providing outputs and ids for the tracks.
   *
   * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
   * @param idGenerator A {@link TrackIdGenerator} that generates unique track ids for the
   *     {@link TrackOutput}s.
   */
  public abstract void init(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator);

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
